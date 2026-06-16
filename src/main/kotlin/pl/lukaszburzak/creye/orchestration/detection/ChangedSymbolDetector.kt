package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import pl.lukaszburzak.creye.domain.change.ChangeComparison
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedFile
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.change.ContextualDeclaration
import pl.lukaszburzak.creye.domain.change.FileChangeState
import pl.lukaszburzak.creye.domain.change.FileMove
import pl.lukaszburzak.creye.domain.change.Hunk
import pl.lukaszburzak.creye.domain.change.SourceRange
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.diagnostics.SourceLocation
import pl.lukaszburzak.creye.domain.identity.NodePath

/**
 * ADR-004 changed symbol detection: maps ADR-003 changed-file records to changed
 * declarations through the own-range rule, symmetrically over baseline and current
 * PSI built from raw record content. Never reads git or the changed-file set itself.
 *
 * Callers must hold a read action; PSI is created and traversed here.
 */
class ChangedSymbolDetector(
    private val project: Project,
    private val fileSegments: (path: String) -> FileSegmentContext,
) {

    fun detect(comparison: ChangeComparison): ChangedSymbols {
        val changed = LinkedHashMap<NodePath, ChangedDeclaration>()
        val contextual = LinkedHashMap<NodePath, ContextualDeclaration>()
        val moved = mutableListOf<FileMove>()
        val diagnostics = mutableListOf<Diagnostic>()
        for (file in comparison.files) {
            if (!file.isKotlin) continue
            detectFile(file, changed, contextual, moved, diagnostics)
        }
        // A declaration is contextual only while not itself changed (ADR-004 constraint 4).
        changed.keys.forEach(contextual::remove)
        return ChangedSymbols(changed.values.toList(), contextual.values.toList(), moved, diagnostics)
    }

    private fun detectFile(
        file: ChangedFile,
        changed: MutableMap<NodePath, ChangedDeclaration>,
        contextual: MutableMap<NodePath, ContextualDeclaration>,
        moved: MutableList<FileMove>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        when (file.state) {
            FileChangeState.ADDED -> wholeFile(file, file.currentContent, ChangeKind.ADDED, changed, diagnostics)
            FileChangeState.DELETED -> wholeFile(file, file.baselineContent, ChangeKind.DELETED, changed, diagnostics)
            FileChangeState.RENAMED -> {
                moved += FileMove(previousPath = file.previousPath ?: file.path, path = file.path)
                if (file.hunks.isNotEmpty()) modified(file, changed, contextual, diagnostics)
            }
            FileChangeState.MODIFIED -> modified(file, changed, contextual, diagnostics)
        }
    }

    /** Added/deleted file: every declaration plus the file node carries [kind]. */
    private fun wholeFile(
        file: ChangedFile,
        content: String?,
        kind: ChangeKind,
        changed: MutableMap<NodePath, ChangedDeclaration>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        when (val result = parse(file, content, diagnostics)) {
            is ParseResult.Parsed -> result.tree.allNodes().forEach { node ->
                changed.merge(node, kind, file.path, sideFor(kind), content.orEmpty())
            }
            is ParseResult.Malformed -> changed[result.filePath] =
                wholeFileDeclaration(result.filePath, kind, file.path, result.fileName, content.orEmpty())
            ParseResult.NoContent -> Unit
        }
    }

    private fun modified(
        file: ChangedFile,
        changed: MutableMap<NodePath, ChangedDeclaration>,
        contextual: MutableMap<NodePath, ContextualDeclaration>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val currentResult = parse(file, file.currentContent, diagnostics)
        val baselineResult = parse(file, file.baselineContent, diagnostics)
        if (currentResult !is ParseResult.Parsed || baselineResult !is ParseResult.Parsed) {
            // Rung 2: either side malformed degrades the whole file to a file-level change.
            val fallback = (currentResult as? ParseResult.Malformed)
                ?: (baselineResult as? ParseResult.Malformed)
                ?: return
            changed[fallback.filePath] =
                ChangedDeclaration(
                    fallback.filePath,
                    ChangeKind.MODIFIED,
                    file.path,
                    fallback.fileName,
                    currentRange = file.currentContent?.let { sourceRange(it, 0 until it.length) },
                    baselineRange = file.baselineContent?.let { sourceRange(it, 0 until it.length) },
                    currentText = file.currentContent,
                    baselineText = file.baselineContent,
                )
            return
        }
        val currentTree = currentResult.tree
        val baselineTree = baselineResult.tree
        val currentIds = currentTree.allNodes().mapTo(mutableSetOf()) { it.path }
        val baselineIds = baselineTree.allNodes().mapTo(mutableSetOf()) { it.path }
        val currentOffsets = LineOffsets(file.currentContent.orEmpty())
        val baselineOffsets = LineOffsets(file.baselineContent.orEmpty())

        for (hunk in file.hunks) {
            var mappedAnywhere = false
            // Current side: additions and modifications against current PSI (ADR-004 symmetry).
            currentOffsets.toOffsets(hunk.current)?.let { span ->
                mappedAnywhere = mapSide(
                    currentTree, span, file,
                    side = DiffSide.CURRENT,
                    content = file.currentContent.orEmpty(),
                    kindFor = { path -> if (path in baselineIds) ChangeKind.MODIFIED else ChangeKind.ADDED },
                    changed, contextual,
                ) || mappedAnywhere
            }
            // Baseline side: deletions (and removals inside surviving declarations).
            baselineOffsets.toOffsets(hunk.baseline)?.let { span ->
                mappedAnywhere = mapSide(
                    baselineTree, span, file,
                    side = DiffSide.BASELINE,
                    content = file.baselineContent.orEmpty(),
                    kindFor = { path -> if (path in currentIds) ChangeKind.MODIFIED else ChangeKind.DELETED },
                    changed, contextual,
                ) || mappedAnywhere
            }
            if (!mappedAnywhere && !isPointHunk(hunk)) {
                diagnostics += Diagnostic(
                    DiagnosticSource.CHANGED_SYMBOL_DETECTION, Severity.WARNING,
                    "Hunk -${hunk.baseline.start},${hunk.baseline.length} " +
                        "+${hunk.current.start},${hunk.current.length} maps to no declaration range",
                    SourceLocation(file.path, hunk.current.start),
                )
            }
        }
    }

    private fun isPointHunk(hunk: Hunk) = hunk.baseline.length == 0 && hunk.current.length == 0

    /** Own-range rule over one PSI side; returns whether the span hit any node. */
    private fun mapSide(
        tree: DeclTree,
        span: IntRange,
        file: ChangedFile,
        side: DiffSide,
        content: String,
        kindFor: (NodePath) -> ChangeKind,
        changed: MutableMap<NodePath, ChangedDeclaration>,
        contextual: MutableMap<NodePath, ContextualDeclaration>,
    ): Boolean {
        var mapped = false
        for (node in tree.allNodes()) {
            if (!OwnRanges.intersects(node.fullRange, span)) continue
            if (OwnRanges.intersects(node.ownRanges, span)) {
                mapped = true
                val kind = kindFor(node.path)
                changed.merge(node, kind, file.path, side, content)
            } else {
                mapped = true
                contextual.putIfAbsent(node.path, ContextualDeclaration(node.path, file.path))
            }
        }
        return mapped
    }

    private fun parse(
        file: ChangedFile,
        content: String?,
        diagnostics: MutableList<Diagnostic>,
    ): ParseResult {
        if (content == null) return ParseResult.NoContent
        val fileName = file.path.substringAfterLast('/')
        val ktFile = KtPsiFactory(project, markGenerated = false).createFile(fileName, content)
        val context = fileSegments(file.path)
        diagnostics.addDistinct(context.diagnostics)
        val filePath = NodePathFactory.filePath(ktFile, context, fileName)
        if (PsiTreeUtil.hasErrorElements(ktFile)) {
            diagnostics += Diagnostic(
                DiagnosticSource.CHANGED_SYMBOL_DETECTION, Severity.WARNING,
                "Source is malformed; falling back to a file-level change", SourceLocation(file.path),
            )
            return ParseResult.Malformed(filePath, fileName)
        }
        return ParseResult.Parsed(DeclTree.build(ktFile, filePath, fileName))
    }
}

private enum class DiffSide { CURRENT, BASELINE }

private fun sideFor(kind: ChangeKind): DiffSide =
    if (kind == ChangeKind.DELETED) DiffSide.BASELINE else DiffSide.CURRENT

private fun MutableMap<NodePath, ChangedDeclaration>.merge(
    node: DeclNode,
    kind: ChangeKind,
    filePath: String,
    side: DiffSide,
    content: String,
) {
    val existing = this[node.path]
    val range = sourceRange(content, node.fullRange)
    val text = content.sliceRange(node.fullRange)
    this[node.path] = ChangedDeclaration(
        identity = node.path,
        kind = mergeKind(existing?.kind, kind),
        filePath = existing?.filePath ?: filePath,
        displayName = existing?.displayName ?: node.displayName,
        currentRange = if (side == DiffSide.CURRENT) range else existing?.currentRange,
        baselineRange = if (side == DiffSide.BASELINE) range else existing?.baselineRange,
        currentText = if (side == DiffSide.CURRENT) text else existing?.currentText,
        baselineText = if (side == DiffSide.BASELINE) text else existing?.baselineText,
    )
}

private fun wholeFileDeclaration(
    path: NodePath,
    kind: ChangeKind,
    filePath: String,
    displayName: String,
    content: String,
): ChangedDeclaration =
    ChangedDeclaration(
        identity = path,
        kind = kind,
        filePath = filePath,
        displayName = displayName,
        currentRange = if (kind != ChangeKind.DELETED) sourceRange(content, 0 until content.length) else null,
        baselineRange = if (kind == ChangeKind.DELETED) sourceRange(content, 0 until content.length) else null,
        currentText = if (kind != ChangeKind.DELETED) content else null,
        baselineText = if (kind == ChangeKind.DELETED) content else null,
    )

private fun mergeKind(existing: ChangeKind?, incoming: ChangeKind): ChangeKind =
    when {
        existing == null -> incoming
        existing == incoming -> existing
        existing == ChangeKind.MODIFIED -> incoming
        incoming == ChangeKind.MODIFIED -> existing
        else -> incoming
    }

private fun sourceRange(content: String, range: IntRange): SourceRange {
    val start = range.first.coerceIn(0, content.length)
    val end = (range.last + 1).coerceIn(start, content.length)
    return SourceRange(
        startOffset = start,
        endOffset = end,
        startLine = lineNumber(content, start),
        endLine = lineNumber(content, (end - 1).coerceAtLeast(start)),
    )
}

private fun String.sliceRange(range: IntRange): String {
    val start = range.first.coerceIn(0, length)
    val end = (range.last + 1).coerceIn(start, length)
    return substring(start, end)
}

private fun lineNumber(content: String, offset: Int): Int {
    if (content.isEmpty()) return 1
    val bounded = offset.coerceIn(0, content.lastIndex)
    var line = 1
    for (index in 0 until bounded) {
        if (content[index] == '\n') line++
    }
    return line
}

private fun MutableList<Diagnostic>.addDistinct(newDiagnostics: List<Diagnostic>) {
    for (diagnostic in newDiagnostics) {
        if (diagnostic !in this) add(diagnostic)
    }
}

private sealed interface ParseResult {
    class Parsed(val tree: DeclTree) : ParseResult
    class Malformed(val filePath: NodePath, val fileName: String) : ParseResult
    data object NoContent : ParseResult
}

/** One declaration (or the file root) with its ADR-004 own ranges and ADR-005 path. */
private class DeclNode(
    val path: NodePath,
    val displayName: String,
    val fullRange: IntRange,
    val ownRanges: List<IntRange>,
    val children: List<DeclNode>,
)

private class DeclTree(val root: DeclNode) {
    fun allNodes(): Sequence<DeclNode> = sequence {
        suspend fun SequenceScope<DeclNode>.walk(node: DeclNode) {
            yield(node)
            node.children.forEach { walk(it) }
        }
        walk(root)
    }

    companion object {
        fun build(ktFile: KtFile, filePath: NodePath, fileName: String): DeclTree {
            fun declNode(declaration: KtDeclaration, parentPath: NodePath): DeclNode {
                val path = NodePathFactory.declarationPath(parentPath, declaration)
                val childDecls = directChildren(declaration)
                val children = childDecls.map { declNode(it, path) }
                val full = declaration.textRange.startOffset until declaration.textRange.endOffset
                return DeclNode(
                    path,
                    declaration.name ?: "<anonymous>",
                    full,
                    OwnRanges.subtract(full, children.map { it.fullRange }),
                    children,
                )
            }

            val topLevel = ktFile.declarations.filter { isTracked(it) }
            val children = topLevel.map { declNode(it, filePath) }
            val full = 0 until maxOf(ktFile.textLength, 1)
            val root = DeclNode(
                filePath,
                fileName,
                full,
                OwnRanges.subtract(full, children.map { it.fullRange }),
                children,
            )
            return DeclTree(root)
        }

        /** ADR-004 granularity: classes/objects, named functions, properties. */
        private fun isTracked(declaration: KtDeclaration): Boolean =
            declaration is KtClassOrObject || declaration is KtNamedFunction || declaration is KtProperty

        private fun directChildren(declaration: KtDeclaration): List<KtDeclaration> =
            when (declaration) {
                is KtClassOrObject -> declaration.declarations.filter { isTracked(it) }
                else -> emptyList() // locals fold into the enclosing declaration's own range
            }
    }
}

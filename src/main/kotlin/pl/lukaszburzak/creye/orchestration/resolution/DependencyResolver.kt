package pl.lukaszburzak.creye.orchestration.resolution

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KotlinReferenceProvidersService
import pl.lukaszburzak.creye.domain.change.ChangeDetection
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedFile
import pl.lukaszburzak.creye.domain.change.FileChangeState
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.diagnostics.SourceLocation
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.ExternalSymbolId
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.orchestration.detection.FileSegmentContext
import pl.lukaszburzak.creye.orchestration.detection.LineOffsets
import pl.lukaszburzak.creye.orchestration.detection.NodePathFactory
import pl.lukaszburzak.creye.orchestration.detection.OwnRanges

/**
 * ADR-006 dependency resolution. Callers must hold a read action; every Analysis
 * API symbol is converted to a domain graph identity before leaving analyze {}.
 */
class DependencyResolver(
    private val project: Project,
    private val repositoryRootPath: String,
    private val fileSegments: (path: String) -> FileSegmentContext,
) {

    fun resolve(detection: ChangeDetection): ResolvedDependencies {
        val changedByPath = detection.symbols.changed
            .filter { it.kind != ChangeKind.DELETED }
            .associateBy { it.identity }
        if (changedByPath.isEmpty()) return ResolvedDependencies(emptyList(), emptyList())

        val changedFiles = detection.comparison.files
            .filter { it.isKotlin && it.currentContent != null && it.state != FileChangeState.DELETED }
            .associateBy { it.path }

        val edges = mutableListOf<DependencyEdge>()
        val diagnostics = mutableListOf<Diagnostic>()
        for ((path, file) in changedFiles) {
            val ktFile = findKtFile(path) ?: continue
            val context = fileSegmentContext(path, diagnostics)
            val declarations = DeclIndex.build(ktFile, context, path)
            val changedInFile = changedByPath.values.filter { it.filePath == path }
            if (changedInFile.isEmpty()) continue
            val hunkRanges = changedRanges(file)
            analyze(ktFile) {
                for (changed in changedInFile) {
                    val node = declarations[changed.identity] ?: continue
                    val windows = intersect(node.ownRanges, hunkRanges)
                    if (windows.isEmpty()) continue
                    collectDeferredDiagnostics(changed, node.element, windows, path, diagnostics)
                    val resolve: (KtReference) -> KaSymbol? = { reference -> reference.resolveToSymbol() }
                    collectCallEdges(changed, node.element, windows, path, changedByPath.keys, resolve, edges, diagnostics)
                    collectTypeEdges(changed, node.element, windows, path, changedByPath.keys, resolve, edges, diagnostics)
                    collectPropertyEdges(changed, node.element, windows, path, changedByPath.keys, resolve, edges, diagnostics)
                }
            }
        }
        return ResolvedDependencies(edges.distinct(), diagnostics)
    }

    private fun findKtFile(path: String): KtFile? {
        val file = findVirtualFile(path) ?: return null
        return PsiManager.getInstance(project).findFile(file) as? KtFile
    }

    private fun findVirtualFile(path: String): VirtualFile? {
        val root = LocalFileSystem.getInstance().findFileByPath(repositoryRootPath)
        val local = LocalFileSystem.getInstance().findFileByPath("$repositoryRootPath/$path")
        if (root != null && local != null && VfsUtilCore.isAncestor(root, local, false)) return local
        return FilenameIndex.getVirtualFilesByName(path.substringAfterLast('/'), GlobalSearchScope.projectScope(project))
            .firstOrNull { it.path.endsWith(path) }
    }

    private fun changedRanges(file: ChangedFile): List<IntRange> {
        if (file.state == FileChangeState.ADDED) return listOf(0 until file.currentContent.orEmpty().length.coerceAtLeast(1))
        val offsets = LineOffsets(file.currentContent.orEmpty())
        return file.hunks.mapNotNull { offsets.toOffsets(it.current) }
    }

    private fun collectDeferredDiagnostics(
        changed: ChangedDeclaration,
        element: PsiElement,
        windows: List<IntRange>,
        path: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        PsiTreeUtil.collectElementsOfType(element, KtAnnotationEntry::class.java)
            .filter { it.textRange.intersects(windows) }
            .forEach {
                diagnostics += Diagnostic(
                    DiagnosticSource.DEPENDENCY_RESOLUTION,
                    Severity.INFO,
                    "Annotation dependency is deferred: ${it.shortName?.asString() ?: it.text}",
                    location(path, it),
                    DiagnosticAttachment.Node(GraphNodeId.Structural(changed.identity)),
                )
            }
    }

    private fun collectCallEdges(
        changed: ChangedDeclaration,
        element: PsiElement,
        windows: List<IntRange>,
        path: String,
        changedTargets: Set<NodePath>,
        resolve: (KtReference) -> KaSymbol?,
        edges: MutableList<DependencyEdge>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        PsiTreeUtil.collectElementsOfType(element, KtCallExpression::class.java)
            .filter { (it.calleeExpression ?: it).textRange.intersects(windows) }
            .forEach { call ->
                val callee = call.calleeExpression ?: call
                val reference = ktReference(callee)
                if (reference == null) {
                    unresolved(changed, path, callee, diagnostics)
                    return@forEach
                }
                val symbol = resolve(reference)
                if (symbol == null) {
                    unresolved(changed, path, callee, diagnostics)
                    return@forEach
                }
                if (isDeferredImplicitInvoke(symbol)) {
                    deferred(changed, path, callee, "Implicit invoke dependency is deferred: ${callee.text}", diagnostics)
                    return@forEach
                }
                val target = classify(symbol, DependencyKind.CALL, changedTargets, diagnostics, changed, path, callee)
                if (target != null) edges += edge(changed.identity, target, DependencyKind.CALL)
            }
    }

    private fun collectTypeEdges(
        changed: ChangedDeclaration,
        element: PsiElement,
        windows: List<IntRange>,
        path: String,
        changedTargets: Set<NodePath>,
        resolve: (KtReference) -> KaSymbol?,
        edges: MutableList<DependencyEdge>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        PsiTreeUtil.collectElementsOfType(element, KtTypeReference::class.java)
            .filter { it.textRange.intersects(windows) && PsiTreeUtil.getParentOfType(it, KtAnnotationEntry::class.java) == null }
            .forEach { typeReference ->
                PsiTreeUtil.collectElementsOfType(typeReference, KtNameReferenceExpression::class.java)
                    .forEach { name ->
                        val reference = ktReference(name)
                        if (reference == null) {
                            unresolved(changed, path, name, diagnostics)
                            return@forEach
                        }
                        val symbol = resolve(reference)
                        if (symbol == null) {
                            unresolved(changed, path, name, diagnostics)
                            return@forEach
                        }
                        val target = classify(symbol, DependencyKind.TYPE_REFERENCE, changedTargets, diagnostics, changed, path, name)
                        if (target != null) edges += edge(changed.identity, target, DependencyKind.TYPE_REFERENCE)
                    }
            }
    }

    private fun collectPropertyEdges(
        changed: ChangedDeclaration,
        element: PsiElement,
        windows: List<IntRange>,
        path: String,
        changedTargets: Set<NodePath>,
        resolve: (KtReference) -> KaSymbol?,
        edges: MutableList<DependencyEdge>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        PsiTreeUtil.collectElementsOfType(element, KtNameReferenceExpression::class.java)
            .filter { it.textRange.intersects(windows) }
            .filterNot { PsiTreeUtil.getParentOfType(it, KtTypeReference::class.java) != null }
            .filterNot { PsiTreeUtil.getParentOfType(it, KtAnnotationEntry::class.java) != null }
            .filterNot { PsiTreeUtil.getParentOfType(it, KtImportDirective::class.java) != null }
            .filterNot { PsiTreeUtil.getParentOfType(it, KtPackageDirective::class.java) != null }
            .filterNot { (it.parent as? KtCallExpression)?.calleeExpression == it }
            .forEach { name ->
                val reference = ktReference(name) ?: return@forEach
                val symbol = resolve(reference) ?: return@forEach
                if (symbol !is KaCallableSymbol) return@forEach
                if (!name.hasExplicitReceiver() && sourceDeclaration(symbol) is KtProperty) {
                    deferred(changed, path, name, "Implicit receiver dependency is deferred: ${name.text}", diagnostics)
                    return@forEach
                }
                val target = classify(symbol, DependencyKind.PROPERTY_ACCESS, changedTargets, diagnostics, changed, path, name)
                if (target != null) edges += edge(changed.identity, target, DependencyKind.PROPERTY_ACCESS)
            }
    }

    private fun isDeferredImplicitInvoke(symbol: KaSymbol): Boolean =
        symbol is KaCallableSymbol && symbol !is KaFunctionSymbol && symbol !is KaConstructorSymbol

    private fun classify(
        symbol: KaSymbol,
        kind: DependencyKind,
        changedTargets: Set<NodePath>,
        diagnostics: MutableList<Diagnostic>,
        changed: ChangedDeclaration,
        path: String,
        referenceElement: PsiElement,
    ): Target? {
        val psi = sourceDeclaration(symbol)
        if (psi != null) {
                val targetPath = structuralPath(psi, diagnostics)
            if (targetPath != null) {
                val classification = if (targetPath in changedTargets) {
                    DependencyClassification.COHESION
                } else {
                    val virtualFile = psi.containingFile?.virtualFile
                    when {
                        virtualFile != null && ProjectFileIndex.getInstance(project).isInSourceContent(virtualFile) ->
                            DependencyClassification.INTERNAL
                        virtualFile != null && ProjectFileIndex.getInstance(project).isInLibrary(virtualFile) ->
                            DependencyClassification.EXTERNAL
                        else -> null
                    }
                }
                return when (classification) {
                    DependencyClassification.COHESION,
                    DependencyClassification.INTERNAL,
                    -> Target(GraphNodeId.Structural(targetPath), classification)
                    DependencyClassification.EXTERNAL -> Target(externalNode(symbol), classification)
                    null -> {
                        unresolved(changed, path, referenceElement, diagnostics)
                        null
                    }
                }
            }
        }
        return Target(externalNode(symbol), DependencyClassification.EXTERNAL)
    }

    private fun sourceDeclaration(symbol: KaSymbol): KtDeclaration? {
        val psi = symbol.psi
        return when (symbol) {
            is KaConstructorSymbol -> PsiTreeUtil.getParentOfType(psi, KtClassOrObject::class.java, false)
            else -> psi as? KtDeclaration
        }
    }

    private fun structuralPath(declaration: KtDeclaration, diagnostics: MutableList<Diagnostic>): NodePath? {
        val ktFile = declaration.containingKtFile
        val virtualFile = ktFile.virtualFile ?: return null
        val relativePath = relativePath(virtualFile) ?: return null
        val filePath = NodePathFactory.filePath(ktFile, fileSegmentContext(relativePath, diagnostics), virtualFile.name)
        val chain = generateSequence(declaration) {
            PsiTreeUtil.getParentOfType(it, KtDeclaration::class.java, true)
        }.filter { it is KtClassOrObject || it is KtNamedFunction || it is KtProperty }
            .toList()
            .asReversed()
        return chain.fold(filePath) { parent, current -> NodePathFactory.declarationPath(parent, current) }
    }

    private fun relativePath(virtualFile: VirtualFile): String? {
        val root = LocalFileSystem.getInstance().findFileByPath(repositoryRootPath)
        if (root != null) {
            VfsUtilCore.getRelativePath(virtualFile, root, '/')?.let { return it }
        }
        return virtualFile.path.substringAfter("/src/", missingDelimiterValue = virtualFile.path)
            .takeIf { it != virtualFile.path || virtualFile.path.contains("/src/") }
    }

    private fun externalNode(symbol: KaSymbol): GraphNodeId.External {
        val id = when (symbol) {
            is KaConstructorSymbol -> symbol.containingClassId?.toString()
            is KaClassLikeSymbol -> symbol.classId?.toString()
            is KaCallableSymbol -> symbol.callableId?.toString()
            else -> null
        } ?: symbol.psi?.text?.take(80) ?: symbol.toString()
        val displayName = (symbol as? KaNamedSymbol)?.name?.asString()
            ?: id.substringAfterLast('/').substringAfterLast('.')
        return GraphNodeId.External(ExternalSymbolId(id, displayName))
    }

    private fun ktReference(element: PsiElement): KtReference? =
        KotlinReferenceProvidersService.getInstance(project)
            .getReferences(element)
            .filterIsInstance<KtReference>()
            .firstOrNull()

    private fun KtNameReferenceExpression.hasExplicitReceiver(): Boolean =
        (parent as? KtQualifiedExpression)?.selectorExpression == this

    private fun fileSegmentContext(path: String, diagnostics: MutableList<Diagnostic>): FileSegmentContext {
        val context = fileSegments(path)
        diagnostics.addDistinct(context.diagnostics)
        return context
    }

    private fun edge(source: NodePath, target: Target, kind: DependencyKind) =
        DependencyEdge(source, target.id, target.classification, kind)

    private fun unresolved(
        changed: ChangedDeclaration,
        path: String,
        element: PsiElement,
        diagnostics: MutableList<Diagnostic>,
    ) {
        diagnostics += Diagnostic(
            DiagnosticSource.DEPENDENCY_RESOLUTION,
            Severity.WARNING,
            "Unresolved reference skipped: ${element.text}",
            location(path, element),
            DiagnosticAttachment.Node(GraphNodeId.Structural(changed.identity)),
        )
    }

    private fun deferred(
        changed: ChangedDeclaration,
        path: String,
        element: PsiElement,
        message: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        diagnostics += Diagnostic(
            DiagnosticSource.DEPENDENCY_RESOLUTION,
            Severity.INFO,
            message,
            location(path, element),
            DiagnosticAttachment.Node(GraphNodeId.Structural(changed.identity)),
        )
    }

    private fun location(path: String, element: PsiElement): SourceLocation {
        val virtualFile = element.containingFile?.virtualFile
        val line = virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
            ?.getLineNumber(element.textRange.startOffset)
            ?.plus(1)
        return SourceLocation(path, line)
    }

    private fun intersect(a: List<IntRange>, b: List<IntRange>): List<IntRange> =
        a.flatMap { left ->
            b.mapNotNull { right ->
                val start = maxOf(left.first, right.first)
                val end = minOf(left.last, right.last)
                if (start <= end) start..end else null
            }
        }

    private fun com.intellij.openapi.util.TextRange.intersects(windows: List<IntRange>): Boolean =
        windows.any { startOffset <= it.last && endOffset > it.first }

    private data class Target(val id: GraphNodeId, val classification: DependencyClassification)
}

private fun MutableList<Diagnostic>.addDistinct(newDiagnostics: List<Diagnostic>) {
    for (diagnostic in newDiagnostics) {
        if (diagnostic !in this) add(diagnostic)
    }
}

private class DeclNode(
    val path: NodePath,
    val element: PsiElement,
    val ownRanges: List<IntRange>,
)

private class DeclIndex(private val byPath: Map<NodePath, DeclNode>) {
    operator fun get(path: NodePath): DeclNode? = byPath[path]

    companion object {
        fun build(ktFile: KtFile, context: FileSegmentContext, filePath: String): DeclIndex {
            val rootPath = NodePathFactory.filePath(ktFile, context, filePath.substringAfterLast('/'))
            val entries = LinkedHashMap<NodePath, DeclNode>()

            fun trackedChildren(element: PsiElement): List<KtDeclaration> = when (element) {
                is KtFile -> element.declarations.filter { it.isTracked() }
                is KtClassOrObject -> element.declarations.filter { it.isTracked() }
                else -> emptyList()
            }

            fun visit(element: PsiElement, path: NodePath) {
                val children = trackedChildren(element)
                val childRanges = children.map { it.textRange.startOffset until it.textRange.endOffset }
                val full = when (element) {
                    is KtFile -> 0 until maxOf(element.textLength, 1)
                    else -> element.textRange.startOffset until element.textRange.endOffset
                }
                entries[path] = DeclNode(path, element, OwnRanges.subtract(full, childRanges))
                for (child in children) {
                    visit(child, NodePathFactory.declarationPath(path, child))
                }
            }

            visit(ktFile, rootPath)
            return DeclIndex(entries)
        }

        private fun KtDeclaration.isTracked(): Boolean =
            this is KtClassOrObject || this is KtNamedFunction || this is KtProperty
    }
}

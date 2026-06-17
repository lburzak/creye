package pl.lukaszburzak.creye.ide

import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedBlockId
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.diff.tools.combined.CombinedDiffModelListener
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Installs ADR-019 approval affordances into the plugin-owned combined diff only.
 *
 * Combined diff block contents are loaded lazily, so the decorator listens to the owned
 * processor's model and attaches editor-local highlighters whenever a block viewer appears.
 */
internal object CombinedDiffApprovalDecorator {
    fun install(
        processor: CombinedDiffComponentProcessor,
        decorations: List<ApprovalDiffDecoration>,
        onToggleApproval: (NodePath) -> Unit,
    ): ApprovalDiffDecorationHandle? {
        if (decorations.isEmpty()) return null
        val model = processor.combinedModel() ?: return null
        val disposable = Disposer.newDisposable("Creye approval diff decorations")
        val installer = ApprovalDecorationInstaller(processor, decorations, onToggleApproval)
        Disposer.register(processor.disposable, disposable)
        Disposer.register(disposable, Disposable { installer.clearAll() })
        model.addListener(installer, disposable)

        processor.blocks.forEach { block ->
            if (model.getLoadedRequest(block.id) != null) {
                installer.installBlock(block.id)
            }
        }
        return ApprovalDiffDecorationHandle(installer::update)
    }
}

internal class ApprovalDiffDecorationHandle(private val updateDecorations: (List<ApprovalDiffDecoration>) -> Unit) {
    fun update(decorations: List<ApprovalDiffDecoration>) {
        updateDecorations(decorations)
    }
}

private class ApprovalDecorationInstaller(
    private val processor: CombinedDiffComponentProcessor,
    decorations: List<ApprovalDiffDecoration>,
    private val onToggleApproval: (NodePath) -> Unit,
) : CombinedDiffModelListener {
    private var decorations = decorations.sortedForInstallation()
    private val installed = linkedMapOf<CombinedBlockId, MutableList<InstalledHighlighter>>()

    override fun onModelReset() {
        clearAll()
    }

    override fun onRequestsLoaded(blockId: CombinedBlockId, request: DiffRequest) {
        clearBlock(blockId)
        installBlock(blockId)
    }

    override fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>) {
        requests.keys.forEach(::clearBlock)
    }

    fun installBlock(blockId: CombinedBlockId) {
        val viewer = processor.diffViewerFor(blockId) ?: return
        val matchingDecorations = decorationsFor(blockId)
        if (matchingDecorations.isEmpty()) return

        val blockHighlighters = mutableListOf<InstalledHighlighter>()
        matchingDecorations.forEach { decoration ->
            when (decoration) {
                is ApprovalDiffDecoration.Gutter -> installGutter(viewer, decoration)
                is ApprovalDiffDecoration.Highlight -> installHighlight(viewer, decoration)
            }?.let(blockHighlighters::add)
        }
        if (blockHighlighters.isNotEmpty()) {
            installed[blockId] = blockHighlighters
        }
    }

    fun clearAll() {
        installed.keys.toList().forEach(::clearBlock)
    }

    fun update(decorations: List<ApprovalDiffDecoration>) {
        this.decorations = decorations.sortedForInstallation()
        clearAll()
        val model = processor.combinedModel() ?: return
        processor.blocks.forEach { block ->
            if (model.getLoadedRequest(block.id) != null) {
                installBlock(block.id)
            }
        }
    }

    private fun clearBlock(blockId: CombinedBlockId) {
        installed.remove(blockId).orEmpty().forEach { highlighter ->
            runCatching {
                if (highlighter.rangeHighlighter.isValid) {
                    highlighter.editor.markupModel.removeHighlighter(highlighter.rangeHighlighter)
                }
            }
        }
    }

    private fun decorationsFor(blockId: CombinedBlockId): List<ApprovalDiffDecoration> {
        val blockPath = (blockId as? CombinedPathBlockId)?.path?.path ?: return emptyList()
        return decorations.filter { pathMatches(blockPath, it.filePath) }
    }

    private fun installGutter(
        viewer: FrameDiffTool.DiffViewer,
        decoration: ApprovalDiffDecoration.Gutter,
    ): InstalledHighlighter? {
        val line = viewer.editorLineRange(decoration.line, decoration.line) ?: return null
        val documentLine = line.validStartLine() ?: return null
        val highlighter = line.editor.markupModel.addLineHighlighter(
            documentLine,
            HighlighterLayer.SELECTION + 2,
            TextAttributes(),
        )
        highlighter.gutterIconRenderer = ApprovalGutterIconRenderer(
            target = decoration.target,
            approved = decoration.approved,
            onToggleApproval = onToggleApproval,
        )
        return InstalledHighlighter(line.editor, highlighter)
    }

    private fun installHighlight(
        viewer: FrameDiffTool.DiffViewer,
        decoration: ApprovalDiffDecoration.Highlight,
    ): InstalledHighlighter? {
        val line = viewer.editorLineRange(decoration.startLine, decoration.endLine) ?: return null
        val offsets = line.offsetRange() ?: return null
        val highlighter = line.editor.markupModel.addRangeHighlighter(
            offsets.startOffset,
            offsets.endOffset,
            HighlighterLayer.SELECTION + 1,
            APPROVED_RANGE_ATTRIBUTES,
            HighlighterTargetArea.LINES_IN_RANGE,
        )
        return InstalledHighlighter(line.editor, highlighter)
    }
}

private data class InstalledHighlighter(
    val editor: Editor,
    val rangeHighlighter: RangeHighlighter,
)

private data class EditorLineRange(
    val editor: Editor,
    val startLine: Int,
    val endLine: Int,
) {
    fun validStartLine(): Int? =
        startLine.takeIf { it in 0 until editor.document.lineCount }

    fun offsetRange(): EditorOffsetRange? {
        val document = editor.document
        if (document.lineCount == 0) return null
        val start = startLine.takeIf { it in 0 until document.lineCount } ?: return null
        val end = endLine.coerceIn(start, document.lineCount - 1)
        return EditorOffsetRange(
            startOffset = document.getLineStartOffset(start),
            endOffset = document.getLineEndOffset(end),
        )
    }
}

private data class EditorOffsetRange(val startOffset: Int, val endOffset: Int)

private fun FrameDiffTool.DiffViewer.editorLineRange(startLine: Int, endLine: Int): EditorLineRange? {
    val startIndex = startLine.toEditorLineIndex() ?: return null
    val endIndex = endLine.toEditorLineIndex()?.coerceAtLeast(startIndex) ?: startIndex
    return when (this) {
        is UnifiedDiffViewer -> {
            val start = transferRightLine(startIndex) ?: return null
            val end = transferRightLine(endIndex)?.coerceAtLeast(start) ?: start
            EditorLineRange(editor, start, end)
        }
        is TwosideTextDiffViewer -> EditorLineRange(getEditor(Side.RIGHT), startIndex, endIndex)
        is EditorDiffViewer -> editors.singleOrNull()?.let { EditorLineRange(it, startIndex, endIndex) }
        else -> null
    }
}

private fun UnifiedDiffViewer.transferRightLine(line: Int): Int? =
    runCatching { transferLineFromOneside(Side.RIGHT, line) }
        .getOrNull()
        ?.takeIf { it >= 0 }

private fun Int.toEditorLineIndex(): Int? =
    takeIf { it >= 1 }?.minus(1)

/**
 * Resolves the changed declaration whose current range contains the caret in the focused
 * combined-diff block editor (REQUIREMENTS: Combined Diff toggle-approval action). Returns
 * null when no block editor has focus or the caret is outside every changed range.
 */
internal fun CombinedDiffComponentProcessor.changedSymbolAtCaret(symbols: ChangedSymbols): NodePath? {
    val model = combinedModel() ?: return null
    for (block in blocks) {
        if (model.getLoadedRequest(block.id) == null) continue
        val viewer = diffViewerFor(block.id) ?: continue
        val caretLine = viewer.focusedRightCaretLine() ?: continue
        val blockPath = (block.id as? CombinedPathBlockId)?.path?.path ?: continue
        val match = symbols.changed.firstOrNull { declaration ->
            pathMatches(blockPath, declaration.filePath) &&
                declaration.currentRange?.let { caretLine in it.startLine..it.endLine } == true
        }
        if (match != null) return match.identity
    }
    return null
}

/** 1-based right-side source line under the caret, or null when this viewer is not focused. */
private fun FrameDiffTool.DiffViewer.focusedRightCaretLine(): Int? = when (this) {
    is UnifiedDiffViewer -> {
        val editor = this.editor
        if (!editor.contentComponent.isFocusOwner) {
            null
        } else {
            runCatching { transferLineFromOneside(Side.RIGHT, editor.caretModel.logicalPosition.line) }
                .getOrNull()?.takeIf { it >= 0 }?.plus(1)
        }
    }
    is TwosideTextDiffViewer -> {
        val editor = getEditor(Side.RIGHT)
        if (editor.contentComponent.isFocusOwner) editor.caretModel.logicalPosition.line + 1 else null
    }
    is EditorDiffViewer -> editors.singleOrNull()
        ?.takeIf { it.contentComponent.isFocusOwner }
        ?.let { it.caretModel.logicalPosition.line + 1 }
    else -> null
}

private fun CombinedDiffComponentProcessor.combinedModel(): CombinedDiffModel? =
    runCatching {
        javaClass.methods
            .firstOrNull { it.name == "getModel" && it.parameterCount == 0 }
            ?.invoke(this) as? CombinedDiffModel
    }.getOrNull()

private fun CombinedDiffComponentProcessor.diffViewerFor(blockId: CombinedBlockId): FrameDiffTool.DiffViewer? =
    runCatching {
        val viewerField = javaClass.getDeclaredField("combinedViewer")
        viewerField.isAccessible = true
        val viewer = viewerField.get(this) as? CombinedDiffViewer ?: return null
        val method = CombinedDiffViewer::class.java.getDeclaredMethod(
            "getDiffViewerForId\$intellij_platform_diff_impl",
            CombinedBlockId::class.java,
        )
        method.invoke(viewer, blockId) as? FrameDiffTool.DiffViewer
    }.getOrNull()

private fun pathMatches(blockPath: String, decorationPath: String): Boolean {
    val normalizedBlock = blockPath.replace('\\', '/')
    val normalizedDecoration = decorationPath.replace('\\', '/')
    return normalizedBlock == normalizedDecoration || normalizedBlock.endsWith("/$normalizedDecoration")
}

private val ApprovalDiffDecoration.anchorLine: Int
    get() = when (this) {
        is ApprovalDiffDecoration.Gutter -> line
        is ApprovalDiffDecoration.Highlight -> startLine
    }

private val ApprovalDiffDecoration.kindOrder: Int
    get() = when (this) {
        is ApprovalDiffDecoration.Gutter -> 0
        is ApprovalDiffDecoration.Highlight -> 1
    }

private fun List<ApprovalDiffDecoration>.sortedForInstallation(): List<ApprovalDiffDecoration> =
    sortedWith(
        compareBy<ApprovalDiffDecoration> { it.filePath }
            .thenBy { it.anchorLine }
            .thenBy { it.kindOrder }
            .thenBy { it.target.displayName() },
    )

private class ApprovalGutterIconRenderer(
    private val target: NodePath,
    private val approved: Boolean,
    onToggleApproval: (NodePath) -> Unit,
) : GutterIconRenderer() {
    private val action = ToggleApprovalAction(target, onToggleApproval)

    override fun getIcon(): Icon = if (approved) APPROVED_ICON else UNAPPROVED_ICON

    override fun getTooltipText(): String =
        if (approved) {
            "Revoke approval for ${target.displayName()}"
        } else {
            "Approve ${target.displayName()}"
        }

    override fun getAccessibleName(): String = tooltipText
    override fun getClickAction(): AnAction = action
    override fun isNavigateAction(): Boolean = false
    override fun getAlignment(): Alignment = Alignment.CENTER

    override fun equals(other: Any?): Boolean =
        other is ApprovalGutterIconRenderer &&
            other.target == target &&
            other.approved == approved

    override fun hashCode(): Int = 31 * target.hashCode() + approved.hashCode()
}

private class ToggleApprovalAction(
    private val target: NodePath,
    private val onToggleApproval: (NodePath) -> Unit,
) : AnAction("Toggle Approval") {
    override fun actionPerformed(e: AnActionEvent) {
        onToggleApproval(target)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class ApprovalCircleIcon(private val filled: Boolean) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(14)
    override fun getIconHeight(): Int = JBUI.scale(14)

    override fun paintIcon(component: java.awt.Component?, graphics: Graphics, x: Int, y: Int) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = APPROVAL_COLOR
            g.stroke = BasicStroke(2.0f)
            val inset = JBUI.scale(1)
            val diameter = iconWidth - inset * 2 - 1
            if (filled) {
                g.fillOval(x + inset, y + inset, diameter, diameter)
            } else {
                g.drawOval(x + inset, y + inset, diameter, diameter)
            }
        } finally {
            g.dispose()
        }
    }
}

private val APPROVAL_COLOR = Color(0x2E7D32)
private val APPROVED_ICON = ApprovalCircleIcon(filled = true)
private val UNAPPROVED_ICON = ApprovalCircleIcon(filled = false)
private val APPROVED_RANGE_ATTRIBUTES = TextAttributes(
    null,
    Color(46, 125, 50, 32),
    Color(46, 125, 50),
    EffectType.BOXED,
    Font.PLAIN,
)

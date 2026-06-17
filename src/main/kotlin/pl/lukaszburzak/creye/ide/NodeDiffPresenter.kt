package pl.lukaszburzak.creye.ide

import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffManager
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.fileSegment
import pl.lukaszburzak.creye.domain.identity.isDescendantOf
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Builds a combined diff (all files stacked in one scroll, like a merge-request review) for
 * the files owned by a node and its descendants, comparing the working directory against the
 * merge base of HEAD and the selected branch — the same baseline the graph (ADR-003) is
 * computed from. Lives in the ide layer so the rendering surface stays git-free (ADR-011).
 *
 * The resulting panel is embedded side-by-side with the graph (REQUIREMENTS: Combined Diff
 * View) rather than opened as a separate window.
 */
class NodeDiffPresenter(private val project: Project) {

    /** Resolution outcome; rendering-free so the controller decides how to surface it. */
    sealed interface Result {
        /** Changes ordered with the clicked node's own file first. */
        data class Ready(val changes: List<Change>) : Result
        data class Empty(val branch: String) : Result
        data class Failed(val message: String) : Result
    }

    /** Off-EDT: runs git and resolves the changes for [node] and its descendants. */
    fun resolve(node: NodePath, graph: DependencyGraph, branch: String): Result {
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
            ?: return Result.Failed("Project has no git repository")
        val branchRef = repository.branches.findBranchByName(branch)
            ?: return Result.Failed("Branch '$branch' does not exist in the repository")
        val root = repository.root
        val mergeBase = GitHistoryUtils.getMergeBase(project, root, "HEAD", branchRef.fullName)
            ?: return Result.Failed("No merge base between HEAD and '$branch'")

        // Files owned by the clicked node plus everything contained beneath it.
        val subtreeFiles = graph.structuralNodes.asSequence()
            .map { it.path }
            .filter { it == node || it.isDescendantOf(node) }
            .mapNotNull { it.fileSegment()?.moduleRelativePath }
            .toSet()
        if (subtreeFiles.isEmpty()) return Result.Empty(branch)

        val rootPath = root.path
        val absoluteTargets = subtreeFiles.mapTo(mutableSetOf()) { "$rootPath/$it" }
        val primaryAbsolute = node.fileSegment()?.let { "$rootPath/${it.moduleRelativePath}" }

        val changes = try {
            GitChangeUtils.getDiffWithWorkingDir(project, root, mergeBase.rev, null, false)
        } catch (e: Exception) {
            return Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        val matched = changes.orEmpty()
            .filter { ChangesUtil.getFilePath(it).path in absoluteTargets }
            .sortedBy { if (ChangesUtil.getFilePath(it).path == primaryAbsolute) 0 else 1 }
        if (matched.isEmpty()) return Result.Empty(branch)

        return Result.Ready(matched)
    }

    /** An embeddable combined-diff view paired with the disposable that releases its diff processor. */
    class Panel(
        val component: JComponent,
        val disposable: Disposable,
        private val updateApprovals: (ApprovalState) -> Unit,
    ) {
        fun updateApprovals(approvals: ApprovalState) {
            updateApprovals.invoke(approvals)
        }
    }

    /**
     * EDT: builds a combined-diff component showing every change at once, stacked vertically,
     * topped by a header with the title and a close button. Returns null when nothing renders;
     * the caller mounts [Panel.component] beside the graph and disposes [Panel.disposable] when
     * the view is replaced or the editor closes.
     */
    fun createPanel(
        changes: List<Change>,
        title: String,
        symbols: ChangedSymbols,
        approvals: ApprovalState,
        onToggleApproval: (NodePath) -> Unit,
        onClose: () -> Unit,
    ): Panel? {
        val processor = CombinedDiffManager.getInstance(project).createProcessor()
        val blockPairs = changes.mapNotNull { change ->
            val producer = ChangeDiffRequestProducer.create(project, change) ?: return@mapNotNull null
            val path = ChangesUtil.getFilePath(change)
            CombinedBlockProducer(CombinedPathBlockId(path, change.fileStatus), producer) to change
        }
        if (blockPairs.isEmpty()) {
            Disposer.dispose(processor.disposable)
            return null
        }
        val fileTargets = fileApprovalTargets(changes, symbols)
        val decorationHandle = CombinedDiffApprovalDecorator.install(
            processor = processor,
            decorations = approvalDecorations(symbols, approvals, fileTargets.map { it.first }),
            onToggleApproval = onToggleApproval,
        )

        // "Collapse approved" hides fully-approved files; recomputed on toggle and on approval change.
        var latestApprovals = approvals
        var collapseApproved = false
        fun applyBlocks() {
            val visible = if (!collapseApproved) {
                blockPairs
            } else {
                blockPairs.filterNot { (_, change) -> isFullyApprovedFile(change, symbols, latestApprovals) }
            }
            processor.setBlocks(visible.map { it.first })
        }
        applyBlocks()

        val toggleAtCaret = Runnable {
            processor.changedSymbolAtCaret(symbols)?.let(onToggleApproval)
        }
        // Expose the caret-toggle through the data context so the registered IDE action can find
        // it whenever this panel (or a descendant diff editor) holds focus.
        val container = object : JPanel(BorderLayout()), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) {
                sink[TOGGLE_APPROVAL_AT_CARET] = toggleAtCaret
            }
        }
        container.add(
            buildHeader(
                title = title,
                onClose = onClose,
                onCollapseApprovedChange = { checked -> collapseApproved = checked; applyBlocks() },
            ),
            BorderLayout.NORTH,
        )
        container.add(processor.component, BorderLayout.CENTER)
        return Panel(container, processor.disposable) { nextApprovals ->
            latestApprovals = nextApprovals
            if (collapseApproved) applyBlocks()
            decorationHandle?.update(approvalDecorations(symbols, nextApprovals, fileTargets.map { it.first }))
        }
    }

    private fun buildHeader(
        title: String,
        onClose: () -> Unit,
        onCollapseApprovedChange: (Boolean) -> Unit,
    ): JComponent {
        val header = JPanel(BorderLayout())
        header.border = JBUI.Borders.empty(4, 8)
        header.add(JBLabel(title), BorderLayout.WEST)

        val east = JPanel()
        east.isOpaque = false
        val collapseApproved = javax.swing.JCheckBox("Collapse approved")
        collapseApproved.isOpaque = false
        collapseApproved.addActionListener { onCollapseApprovedChange(collapseApproved.isSelected) }
        east.add(collapseApproved)

        val closeAction = object : AnAction("Close Diff", "Hide the combined diff view", AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) = onClose()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }
        // Toggle-at-caret stays a keymap/Find-Action action only — no toolbar button.
        val group = DefaultActionGroup()
        group.add(closeAction)
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true)
        toolbar.targetComponent = header
        east.add(toolbar.component)
        header.add(east, BorderLayout.EAST)
        return header
    }

    companion object {
        fun approvalDecorations(
            symbols: ChangedSymbols,
            approvals: ApprovalState,
            fileTargets: List<NodePath> = emptyList(),
        ): List<ApprovalDiffDecoration> =
            buildList {
                fileTargets.forEach { target ->
                    val filePath = target.fileSegment()?.moduleRelativePath ?: return@forEach
                    add(
                        ApprovalDiffDecoration.Gutter(
                            target = target,
                            filePath = filePath,
                            line = 1,
                            approved = approvals.summary(target, symbols)?.isFullyApproved == true,
                        ),
                    )
                }
                symbols.changed.forEach { declaration ->
                    val approved = approvals.isApproved(declaration)
                    declaration.currentRange?.let { range ->
                        add(
                            ApprovalDiffDecoration.Gutter(
                                target = declaration.identity,
                                filePath = declaration.filePath,
                                line = range.startLine,
                                approved = approved,
                            ),
                        )
                        if (approved) {
                            add(
                                ApprovalDiffDecoration.Highlight(
                                    target = declaration.identity,
                                    filePath = declaration.filePath,
                                    startLine = range.startLine,
                                    endLine = range.endLine,
                                ),
                            )
                        }
                    }
                }
            }
    }
}

sealed interface ApprovalDiffDecoration {
    val target: NodePath
    val filePath: String

    data class Gutter(
        override val target: NodePath,
        override val filePath: String,
        val line: Int,
        val approved: Boolean,
    ) : ApprovalDiffDecoration

    data class Highlight(
        override val target: NodePath,
        override val filePath: String,
        val startLine: Int,
        val endLine: Int,
    ) : ApprovalDiffDecoration
}

/** True when every changed declaration in [change]'s file is approved (a "fully approved file"). */
private fun isFullyApprovedFile(change: Change, symbols: ChangedSymbols, approvals: ApprovalState): Boolean {
    val changePath = ChangesUtil.getFilePath(change).path
    val fileNode = symbols.changed.asSequence()
        .filter { changePath == it.filePath || changePath.endsWith("/${it.filePath}") }
        .mapNotNull { it.fileNode() }
        .firstOrNull() ?: return false
    return approvals.summary(fileNode, symbols)?.isFullyApproved == true
}

private fun fileApprovalTargets(changes: List<Change>, symbols: ChangedSymbols): List<Pair<NodePath, String>> {
    val changedFilePaths = changes.mapTo(linkedSetOf()) { ChangesUtil.getFilePath(it).path }
    return symbols.changed
        .asSequence()
        .filter { declaration ->
            changedFilePaths.any { path -> path == declaration.filePath || path.endsWith("/${declaration.filePath}") }
        }
        .mapNotNull { declaration -> declaration.fileNode()?.let { it to declaration.filePath.substringAfterLast('/') } }
        .distinct()
        .toList()
}

private fun ChangedDeclaration.fileNode(): NodePath? {
    val index = identity.segments.indexOfFirst { it is pl.lukaszburzak.creye.domain.identity.NodeSegment.File }
    if (index < 0) return null
    return NodePath(identity.segments.subList(0, index + 1))
}

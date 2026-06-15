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
    class Panel(val component: JComponent, val disposable: Disposable)

    /**
     * EDT: builds a combined-diff component showing every change at once, stacked vertically,
     * topped by a header with the title and a close button. Returns null when nothing renders;
     * the caller mounts [Panel.component] beside the graph and disposes [Panel.disposable] when
     * the view is replaced or the editor closes.
     */
    fun createPanel(changes: List<Change>, title: String, onClose: () -> Unit): Panel? {
        val processor = CombinedDiffManager.getInstance(project).createProcessor()
        val blocks = changes.mapNotNull { change ->
            val producer = ChangeDiffRequestProducer.create(project, change) ?: return@mapNotNull null
            val path = ChangesUtil.getFilePath(change)
            CombinedBlockProducer(CombinedPathBlockId(path, change.fileStatus), producer)
        }
        if (blocks.isEmpty()) {
            Disposer.dispose(processor.disposable)
            return null
        }
        processor.setBlocks(blocks)

        val container = JPanel(BorderLayout())
        container.add(buildHeader(title, onClose), BorderLayout.NORTH)
        container.add(processor.component, BorderLayout.CENTER)
        return Panel(container, processor.disposable)
    }

    private fun buildHeader(title: String, onClose: () -> Unit): JComponent {
        val header = JPanel(BorderLayout())
        header.border = JBUI.Borders.empty(4, 8)
        header.add(JBLabel(title), BorderLayout.WEST)

        val closeAction = object : AnAction("Close Diff", "Hide the combined diff view", AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) = onClose()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.UNKNOWN, DefaultActionGroup(closeAction), true)
        toolbar.targetComponent = header
        header.add(toolbar.component, BorderLayout.EAST)
        return header
    }
}

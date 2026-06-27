package pl.lukaszburzak.creye.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Toggles approval of the node selected in the dependency graph (REQUIREMENTS: IntelliJ Actions).
 * A registered IDE action so it is discoverable via Find Action and rebindable in the keymap;
 * the selection lives on the project-scoped [GraphPanelController].
 */
class ApproveSelectedNodeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GraphPanelController.getInstance(project).approveSelectedNode()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled =
            e.project?.let { GraphPanelController.getInstance(it).selectedNode() } != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Isolates the graph to the selected node and its dependencies (REQUIREMENTS: IntelliJ Actions).
 * The filter is shown in the graph control row with a clear button.
 */
class ScopeToSelectedNodeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GraphPanelController.getInstance(project).scopeToSelectedNode()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled =
            e.project?.let { GraphPanelController.getInstance(it).selectedNode() } != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

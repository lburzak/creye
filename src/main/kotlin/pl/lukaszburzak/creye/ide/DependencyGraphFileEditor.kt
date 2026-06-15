package pl.lukaszburzak.creye.ide

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.jewel.bridge.JewelComposePanel
import pl.lukaszburzak.creye.rendering.GraphSurface
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Editor panel hosting the graph (ADR-001). Embeds the rendering layer through a
 * Jewel-themed Compose panel (ADR-009) so the surface inherits the active IDE theme.
 */
class DependencyGraphFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val controller = GraphPanelController.getInstance(project).also { it.activate() }

    private val panel: JComponent = JewelComposePanel {
        val state by controller.state.collectAsState()
        GraphSurface(
            state = state,
            onBranchSelected = controller::selectBranch,
            onRefresh = controller::refresh,
            onShowDiff = controller::showNodeDiff,
        )
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Dependency Graph"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
}

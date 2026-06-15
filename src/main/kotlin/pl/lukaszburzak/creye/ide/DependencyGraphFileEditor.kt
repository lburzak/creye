package pl.lukaszburzak.creye.ide

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.JewelComposePanel
import pl.lukaszburzak.creye.rendering.GraphSurface
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Editor panel hosting the graph (ADR-001). The graph renders in a Jewel-themed Compose panel
 * (ADR-009) so the surface inherits the active IDE theme; when a node diff is requested the
 * combined diff view mounts side-by-side in the splitter's second slot (REQUIREMENTS: Combined
 * Diff View).
 */
class DependencyGraphFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val controller = GraphPanelController.getInstance(project).also { it.activate() }
    private val diffPresenter = NodeDiffPresenter(project)
    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

    private val graphPanel: JComponent = JewelComposePanel {
        val state by controller.state.collectAsState()
        GraphSurface(
            state = state,
            onBranchSelected = controller::selectBranch,
            onRefresh = controller::refresh,
            onShowDiff = controller::showNodeDiff,
            forceSettings = controller.forceSettings(),
            onForceSettingsChange = controller::updateForceSettings,
        )
    }

    /** Holds the live diff view's disposable so it is released before a replacement mounts. */
    private var diffDisposable: com.intellij.openapi.Disposable? = null

    private val splitter = OnePixelSplitter(false, 0.6f).apply {
        firstComponent = graphPanel
    }

    init {
        editorScope.launch {
            controller.diffRequest.collectLatest { request ->
                disposeDiff()
                splitter.secondComponent = when (request) {
                    null -> null
                    else -> diffPresenter.createPanel(request.changes, request.title, controller::closeDiff)
                        ?.also { diffDisposable = it.disposable }
                        ?.component
                }
            }
        }
    }

    private fun disposeDiff() {
        diffDisposable?.let { Disposer.dispose(it) }
        diffDisposable = null
    }

    override fun getComponent(): JComponent = splitter
    override fun getPreferredFocusedComponent(): JComponent = graphPanel
    override fun getName(): String = "Dependency Graph"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() {
        disposeDiff()
        editorScope.cancel()
    }
}

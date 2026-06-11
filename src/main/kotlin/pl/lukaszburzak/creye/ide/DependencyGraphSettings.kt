package pl.lukaszburzak.creye.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Per-project persisted comparison branch (ADR-011). The dropdown selection is the
 * source of truth; this component only carries it across sessions.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CreyeDependencyGraphBranch",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class DependencyGraphSettings : PersistentStateComponent<DependencyGraphSettings.State> {

    class State {
        var branch: String? = null
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var branch: String?
        get() = state.branch
        set(value) {
            state.branch = value
        }

    companion object {
        fun getInstance(project: Project): DependencyGraphSettings =
            project.getService(DependencyGraphSettings::class.java)
    }
}

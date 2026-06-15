package pl.lukaszburzak.creye.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import pl.lukaszburzak.creye.rendering.ForceSettings

/**
 * Per-project persisted comparison branch (ADR-011) and force-simulation slider values
 * (ADR-013). The UI controls are the source of truth; this component only carries them
 * across sessions.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CreyeDependencyGraphBranch",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class DependencyGraphSettings : PersistentStateComponent<DependencyGraphSettings.State> {

    class State {
        var branch: String? = null
        var forceGravity: Float = ForceSettings.DEFAULT_GRAVITY
        var forceAttraction: Float = ForceSettings.DEFAULT_ATTRACTION
        var forceRepulsion: Float = ForceSettings.DEFAULT_REPULSION
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

    var forceSettings: ForceSettings
        get() = ForceSettings(state.forceGravity, state.forceAttraction, state.forceRepulsion)
        set(value) {
            state.forceGravity = value.gravity
            state.forceAttraction = value.attraction
            state.forceRepulsion = value.repulsion
        }

    companion object {
        fun getInstance(project: Project): DependencyGraphSettings =
            project.getService(DependencyGraphSettings::class.java)
    }
}

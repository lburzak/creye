package pl.lukaszburzak.creye.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import pl.lukaszburzak.creye.domain.approval.APPROVAL_FINGERPRINT_SCHEME_VERSION
import pl.lukaszburzak.creye.domain.approval.ApprovalEntry
import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.identity.NodePath

@Service(Service.Level.PROJECT)
@State(
    name = "CreyeApprovals",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class ApprovalPersistence : PersistentStateComponent<ApprovalPersistence.State> {

    class State {
        var entries: MutableList<Entry> = mutableListOf()
    }

    class Entry {
        var key: String = ""
        var fingerprint: String = ""
        var schemeVersion: Int = APPROVAL_FINGERPRINT_SCHEME_VERSION
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun snapshot(): ApprovalState =
        ApprovalState(
            state.entries
                .mapNotNull { entry ->
                    entry.takeIf { it.key.isNotBlank() && it.fingerprint.isNotBlank() }
                        ?.let { ApprovalEntry(it.key, it.fingerprint, it.schemeVersion) }
                }
                .associateBy { it.key },
        )

    fun replace(approvals: ApprovalState) {
        state.entries = approvals.entries.values
            .sortedBy { it.key }
            .mapTo(mutableListOf()) { entry ->
                Entry().also {
                    it.key = entry.key
                    it.fingerprint = entry.fingerprint
                    it.schemeVersion = entry.schemeVersion
                }
            }
    }

    fun pruneTo(symbols: ChangedSymbols): ApprovalState {
        val next = snapshot().pruneTo(symbols)
        replace(next)
        return next
    }

    fun toggle(target: NodePath, symbols: ChangedSymbols): ApprovalState {
        val next = snapshot().pruneTo(symbols).toggle(target, symbols)
        replace(next)
        return next
    }

    companion object {
        fun getInstance(project: Project): ApprovalPersistence =
            project.getService(ApprovalPersistence::class.java)
    }
}

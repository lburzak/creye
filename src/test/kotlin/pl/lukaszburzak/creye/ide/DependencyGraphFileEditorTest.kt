package pl.lukaszburzak.creye.ide

import com.intellij.openapi.vcs.changes.Change
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.approval.ApprovalEntry
import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.ChangedSymbols

class DependencyGraphFileEditorTest {

    @Test
    fun `diff view identity ignores approval-only updates`() {
        val changes = mutableListOf<Change>()
        val symbols = symbols()
        val request = GraphPanelController.DiffRequest(
            changes = changes,
            title = "Diff",
            symbols = symbols,
            approvals = ApprovalState(),
        )
        val approvalUpdate = request.copy(
            approvals = ApprovalState(entries = mapOf("node" to ApprovalEntry("node", "fingerprint"))),
        )

        assertTrue(request.isSameDiffViewAs(approvalUpdate))
    }

    @Test
    fun `diff view identity changes when diff payload changes`() {
        val request = GraphPanelController.DiffRequest(
            changes = mutableListOf(),
            title = "Diff",
            symbols = symbols(),
            approvals = ApprovalState(),
        )

        assertFalse(request.isSameDiffViewAs(request.copy(changes = mutableListOf())))
        assertFalse(request.isSameDiffViewAs(request.copy(symbols = symbols())))
        assertFalse(request.isSameDiffViewAs(request.copy(title = "Other diff")))
    }

    private fun symbols() =
        ChangedSymbols(
            changed = emptyList(),
            contextual = emptyList(),
            movedFiles = emptyList(),
            diagnostics = emptyList(),
        )
}

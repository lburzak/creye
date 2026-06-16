package pl.lukaszburzak.creye.rendering

import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.GraphAnalysisResult
import pl.lukaszburzak.creye.domain.graph.DependencyGraph

/** Analysis lifecycle as seen by the render surface. */
sealed interface AnalysisPhase {
    data object Idle : AnalysisPhase
    data object Running : AnalysisPhase
    data class Ready(
        val result: GraphAnalysisResult,
        val approvals: ApprovalState,
    ) : AnalysisPhase {
        val graph: DependencyGraph get() = result.graph
    }
    data class Failed(val message: String) : AnalysisPhase
}

/**
 * Render-facing panel state (ADR-011): plain values produced by the ide layer; the
 * rendering layer never touches git or persistence itself.
 */
data class GraphPanelState(
    val branches: List<String> = emptyList(),
    val selectedBranch: String? = null,
    /** ADR-011: persisted branch missing from the branch list; selection stays empty. */
    val configurationDiagnostic: String? = null,
    val phase: AnalysisPhase = AnalysisPhase.Idle,
)

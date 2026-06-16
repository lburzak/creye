package pl.lukaszburzak.creye.domain.change

/** Aggregate result of the change-detection pipeline (ADR-003 + ADR-004 stages). */
data class ChangeDetection(
    val comparison: ChangeComparison,
    val symbols: ChangedSymbols,
)

/** Analysis output that keeps the render graph paired with the change set that produced it. */
data class GraphAnalysisResult(
    val graph: pl.lukaszburzak.creye.domain.graph.DependencyGraph,
    val detection: ChangeDetection,
)

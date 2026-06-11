package pl.lukaszburzak.creye.orchestration.resolution

import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.graph.DependencyEdge

data class ResolvedDependencies(
    val edges: List<DependencyEdge>,
    val diagnostics: List<Diagnostic>,
)

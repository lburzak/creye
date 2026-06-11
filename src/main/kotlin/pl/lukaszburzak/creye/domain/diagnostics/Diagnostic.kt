package pl.lukaszburzak.creye.domain.diagnostics

import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.GraphNodeId

/** Producing pipeline stage (ADR-010): closed enumeration mapped 1:1 to stage ADRs. */
enum class DiagnosticSource {
    GIT,
    PROJECT_MODEL,
    CHANGED_SYMBOL_DETECTION,
    DEPENDENCY_RESOLUTION,
    GRAPH_CONSTRUCTION,
    RENDERING,
}

enum class Severity { ERROR, WARNING, INFO }

data class SourceLocation(val filePath: String, val line: Int? = null)

sealed interface DiagnosticAttachment {
    data object Graph : DiagnosticAttachment
    data class Node(val id: GraphNodeId) : DiagnosticAttachment
    data class Edge(val key: DependencyEdge) : DiagnosticAttachment
}

/**
 * Cross-cutting diagnostic value (ADR-010). Severity never controls pipeline flow.
 * The ADR-007 graph attachment key resolves diagnostics to graph elements when available.
 */
data class Diagnostic(
    val source: DiagnosticSource,
    val severity: Severity,
    val message: String,
    val location: SourceLocation? = null,
    val attachment: DiagnosticAttachment? = null,
)

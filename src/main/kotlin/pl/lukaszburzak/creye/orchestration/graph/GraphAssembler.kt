package pl.lukaszburzak.creye.orchestration.graph

import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.orchestration.resolution.ResolvedDependencies

object GraphAssembler {

    fun assemble(
        symbols: ChangedSymbols,
        resolved: ResolvedDependencies,
        upstreamDiagnostics: List<Diagnostic>,
    ): DependencyGraph {
        val changedByPath = symbols.changed.associateBy { it.identity }
        val structuralPaths = LinkedHashSet<NodePath>()
        changedByPath.keys.forEach { structuralPaths += it.withAncestors() }

        val edges = resolved.edges.distinct()
        for (edge in edges) {
            structuralPaths += edge.source.withAncestors()
            val target = edge.target
            if (target is GraphNodeId.Structural) {
                structuralPaths += target.path.withAncestors()
            }
        }

        val structuralNodes = structuralPaths.map { path ->
            StructuralNode(
                path = path,
                displayName = changedByPath[path]?.displayName ?: path.displayName(),
                change = changedByPath[path]?.kind,
            )
        }
        val externalNodes = edges.mapNotNull { (it.target as? GraphNodeId.External)?.id }
            .distinct()
            .map(::ExternalNode)

        val diagnostics = sanitizeDiagnostics(
            diagnostics = upstreamDiagnostics + symbols.diagnostics + resolved.diagnostics,
            structuralPaths = structuralPaths,
            externalIds = externalNodes.mapTo(mutableSetOf()) { it.id },
            edges = edges.toSet(),
        )

        return DependencyGraph(structuralNodes, externalNodes, edges, diagnostics)
    }

    private fun NodePath.withAncestors(): List<NodePath> =
        segments.indices.map { index -> NodePath(segments.take(index + 1)) }

    private fun sanitizeDiagnostics(
        diagnostics: List<Diagnostic>,
        structuralPaths: Set<NodePath>,
        externalIds: Set<pl.lukaszburzak.creye.domain.graph.ExternalSymbolId>,
        edges: Set<DependencyEdge>,
    ): List<Diagnostic> {
        val result = mutableListOf<Diagnostic>()
        for (diagnostic in diagnostics) {
            when (val attachment = diagnostic.attachment) {
                null,
                DiagnosticAttachment.Graph,
                -> result += diagnostic
                is DiagnosticAttachment.Node -> {
                    val exists = when (val id = attachment.id) {
                        is GraphNodeId.Structural -> id.path in structuralPaths
                        is GraphNodeId.External -> id.id in externalIds
                    }
                    if (exists) {
                        result += diagnostic
                    } else {
                        result += graphAttachmentWarning(diagnostic)
                    }
                }
                is DiagnosticAttachment.Edge -> {
                    if (attachment.key in edges) {
                        result += diagnostic
                    } else {
                        result += graphAttachmentWarning(diagnostic)
                    }
                }
            }
        }
        return result
    }

    private fun graphAttachmentWarning(diagnostic: Diagnostic) = Diagnostic(
        source = DiagnosticSource.GRAPH_CONSTRUCTION,
        severity = Severity.WARNING,
        message = "Diagnostic attachment did not resolve to a materialized graph element: ${diagnostic.message}",
        location = diagnostic.location,
        attachment = DiagnosticAttachment.Graph,
    )
}

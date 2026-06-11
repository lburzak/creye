package pl.lukaszburzak.creye.rendering.projection

import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.identity.NodePath

/**
 * Render-facing structural node: [internalizedEdges] is the ADR-008 intrinsic
 * internal-dependency state, rendered as a badge rather than a self-loop edge.
 */
data class VisibleNode(
    val node: StructuralNode,
    val isCollapsed: Boolean,
    val internalizedEdges: Set<DependencyEdge>,
)

/**
 * Aggregated visible edge (ADR-008): deduplicated by `(source, target)` so opposing
 * directions stay distinct; carries the classification set across hidden edges and
 * the underlying domain edges for drill-down and collapsed-edge diagnostics.
 */
data class VisibleEdge(
    val source: GraphNodeId,
    val target: GraphNodeId,
    val classifications: Set<DependencyClassification>,
    val underlying: Set<DependencyEdge>,
)

data class VisibleGraph(
    val structuralNodes: List<VisibleNode>,
    val externalNodes: List<ExternalNode>,
    val edges: List<VisibleEdge>,
)

/**
 * ADR-008 collapse aggregation: a pure projection from the ADR-007 domain graph and
 * the collapse state to the visible graph. Holds no state of its own; recomputed by
 * recomposition whenever [collapsed] changes (ADR-009).
 */
fun projectVisibleGraph(graph: DependencyGraph, collapsed: Set<NodePath>): VisibleGraph {
    // ADR-007 guarantees full ancestor chains, so every lift target is materialized.
    val lifted = graph.edges.groupBy { edge ->
        edge.source.liftTo(collapsed) to edge.target.liftTo(collapsed)
    }

    val internalized = mutableMapOf<NodePath, MutableSet<DependencyEdge>>()
    val edges = mutableListOf<VisibleEdge>()
    for ((endpoints, group) in lifted) {
        val (source, target) = endpoints
        if (source == target) {
            internalized.getOrPut(source.path) { mutableSetOf() }.addAll(group)
        } else {
            edges += VisibleEdge(
                source = source,
                target = target,
                classifications = group.mapTo(mutableSetOf()) { it.classification },
                underlying = group.toSet(),
            )
        }
    }

    val structuralNodes = graph.structuralNodes
        .filter { it.path.isVisible(collapsed) }
        .map { node ->
            VisibleNode(
                node = node,
                isCollapsed = node.path in collapsed,
                internalizedEdges = internalized[node.path].orEmpty(),
            )
        }

    return VisibleGraph(
        structuralNodes = structuralNodes,
        externalNodes = graph.externalNodes,
        edges = edges,
    )
}

/** Visible iff no proper ancestor is collapsed; a collapsed node itself stays visible. */
private fun NodePath.isVisible(collapsed: Set<NodePath>): Boolean =
    properAncestors().none { it in collapsed }

/**
 * The nearest visible representative: the outermost collapsed proper ancestor if any,
 * else the path itself. Outermost wins because deeper collapsed ancestors are hidden.
 */
private fun NodePath.liftTo(collapsed: Set<NodePath>): GraphNodeId.Structural =
    GraphNodeId.Structural(properAncestors().firstOrNull { it in collapsed } ?: this)

private fun GraphNodeId.liftTo(collapsed: Set<NodePath>): GraphNodeId = when (this) {
    is GraphNodeId.Structural -> path.liftTo(collapsed)
    is GraphNodeId.External -> this
}

/** Proper ancestors ordered outermost first (module → … → parent). */
private fun NodePath.properAncestors(): Sequence<NodePath> =
    (1 until segments.size).asSequence().map { NodePath(segments.subList(0, it)) }

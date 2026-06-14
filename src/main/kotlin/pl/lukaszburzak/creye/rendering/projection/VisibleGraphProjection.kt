package pl.lukaszburzak.creye.rendering.projection

import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.identity.NodePath

/**
 * Render-facing structural node: [isCollapsed] means the visible node has hidden
 * children and can be expanded; [internalizedEdges] is the ADR-008 intrinsic
 * internal-dependency state, rendered as a badge rather than a self-loop edge.
 */
data class VisibleNode(
    val node: StructuralNode,
    val isCollapsed: Boolean,
    val internalizedEdges: Set<DependencyEdge>,
    val hasDescendantChange: Boolean,
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

/**
 * Render-derived parent-child connection between two visible structural nodes.
 * This is hierarchy evidence only; dependency classification and underlying-edge
 * traceability stay exclusively on [VisibleEdge].
 */
data class VisibleHierarchyEdge(
    val parent: GraphNodeId.Structural,
    val child: GraphNodeId.Structural,
)

data class VisibleGraph(
    val structuralNodes: List<VisibleNode>,
    val externalNodes: List<ExternalNode>,
    val edges: List<VisibleEdge>,
    val hierarchyEdges: List<VisibleHierarchyEdge> = emptyList(),
)

/**
 * ADR-008 collapse aggregation: a pure projection from the ADR-007 domain graph and
 * the expansion frontier to the visible graph. Holds no state of its own; recomputed
 * by recomposition whenever [expanded] changes (ADR-009).
 */
fun projectVisibleGraph(graph: DependencyGraph, expanded: Set<NodePath>): VisibleGraph {
    val structuralPaths = graph.structuralNodes.mapTo(linkedSetOf()) { it.path }
    val childrenByParent = structuralPaths.groupBy { it.parent() }
    val visiblePaths = structuralPaths
        .filterTo(linkedSetOf()) { path -> path.isVisible(expanded) }

    val lifted = graph.edges.groupBy { edge ->
        edge.source.liftTo(visiblePaths) to edge.target.liftTo(visiblePaths)
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

    val allChangedPaths = graph.structuralNodes
        .filter { it.change != null }
        .mapTo(mutableSetOf()) { it.path }

    val structuralNodes = graph.structuralNodes
        .filter { it.path in visiblePaths }
        .map { node ->
            VisibleNode(
                node = node,
                isCollapsed = node.path !in expanded && childrenByParent[node.path].orEmpty().isNotEmpty(),
                internalizedEdges = internalized[node.path].orEmpty(),
                hasDescendantChange = allChangedPaths.any { changed ->
                    changed.segments.size > node.path.segments.size &&
                        changed.segments.take(node.path.segments.size) == node.path.segments
                },
            )
        }
    val hierarchyEdges = structuralNodes.mapNotNull { node ->
        val parent = node.node.path.parent() ?: return@mapNotNull null
        if (parent !in visiblePaths) return@mapNotNull null
        VisibleHierarchyEdge(
            parent = GraphNodeId.Structural(parent),
            child = GraphNodeId.Structural(node.node.path),
        )
    }

    return VisibleGraph(
        structuralNodes = structuralNodes,
        externalNodes = graph.externalNodes,
        edges = edges,
        hierarchyEdges = hierarchyEdges,
    )
}

/**
 * Visible iff all proper ancestors are expanded. Expanding a node keeps it visible
 * while revealing its direct children as the next frontier.
 */
private fun NodePath.isVisible(expanded: Set<NodePath>): Boolean =
    properAncestors().all { it in expanded }

/**
 * The nearest visible representative is the deepest materialized visible ancestor.
 * ADR-007 guarantees full ancestor chains for dependency endpoints, so child edges
 * can be conflated onto the current visible ancestor frontier.
 */
private fun NodePath.liftTo(visiblePaths: Set<NodePath>): GraphNodeId.Structural =
    GraphNodeId.Structural(
        ancestorsAndSelf()
            .lastOrNull { it in visiblePaths }
            ?: this,
    )

private fun GraphNodeId.liftTo(visiblePaths: Set<NodePath>): GraphNodeId = when (this) {
    is GraphNodeId.Structural -> path.liftTo(visiblePaths)
    is GraphNodeId.External -> this
}

/** Proper ancestors ordered outermost first (module → … → parent). */
private fun NodePath.properAncestors(): Sequence<NodePath> =
    (1 until segments.size).asSequence().map { NodePath(segments.subList(0, it)) }

/** Ancestors ordered outermost first and including this path. */
private fun NodePath.ancestorsAndSelf(): Sequence<NodePath> =
    (1..segments.size).asSequence().map { NodePath(segments.subList(0, it)) }

private fun NodePath.parent(): NodePath? =
    if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))

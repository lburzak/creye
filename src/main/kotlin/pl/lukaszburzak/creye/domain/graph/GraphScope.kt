package pl.lukaszburzak.creye.domain.graph

import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.isDescendantOf

/**
 * Isolation filter (REQUIREMENTS: IntelliJ Actions — Scope to selected node): restricts the
 * graph to [root], everything it contains, and the transitive dependency closure reachable
 * from it. The structural ancestor chains of every kept node are retained so the projection
 * can still materialize and lift edges onto visible ancestors (ADR-008). Diagnostics describe
 * the whole graph and are dropped from the scoped view.
 */
fun DependencyGraph.scopedTo(root: NodePath): DependencyGraph {
    val edgesBySource = edges.groupBy { it.source }

    fun containedPaths(ancestor: NodePath): List<NodePath> =
        structuralNodes.asSequence()
            .map { it.path }
            .filter { it == ancestor || it.isDescendantOf(ancestor) }
            .toList()

    val inScope = LinkedHashSet<NodePath>()
    val externalInScope = LinkedHashSet<ExternalSymbolId>()
    val queue = ArrayDeque<NodePath>()
    containedPaths(root).forEach { if (inScope.add(it)) queue.add(it) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (edge in edgesBySource[current].orEmpty()) {
            when (val target = edge.target) {
                is GraphNodeId.Structural ->
                    containedPaths(target.path).forEach { if (inScope.add(it)) queue.add(it) }
                is GraphNodeId.External -> externalInScope.add(target.id)
            }
        }
    }

    val materialized = inScope.flatMapTo(LinkedHashSet()) { it.ancestorsAndSelf() }
    return copy(
        structuralNodes = structuralNodes.filter { it.path in materialized },
        externalNodes = externalNodes.filter { it.id in externalInScope },
        edges = edges.filter { edge ->
            edge.source in inScope &&
                when (val target = edge.target) {
                    is GraphNodeId.Structural -> target.path in materialized
                    is GraphNodeId.External -> target.id in externalInScope
                }
        },
        diagnostics = emptyList(),
    )
}

private fun NodePath.ancestorsAndSelf(): List<NodePath> =
    segments.indices.map { NodePath(segments.take(it + 1)) }

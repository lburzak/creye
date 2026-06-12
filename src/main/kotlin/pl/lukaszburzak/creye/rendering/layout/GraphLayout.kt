package pl.lukaszburzak.creye.rendering.layout

import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import org.gephi.graph.api.Node
import org.gephi.graph.impl.GraphModelImpl
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LayoutPoint(val x: Float, val y: Float) {
    operator fun plus(other: LayoutPoint): LayoutPoint = LayoutPoint(x + other.x, y + other.y)
    operator fun minus(other: LayoutPoint): LayoutPoint = LayoutPoint(x - other.x, y - other.y)
}

data class LayoutRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val center: LayoutPoint get() = LayoutPoint(x + width / 2f, y + height / 2f)

    fun contains(other: LayoutRect): Boolean =
        other.x >= x && other.y >= y && other.right <= right && other.bottom <= bottom

    fun overlaps(other: LayoutRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

data class GraphLayout(
    val bounds: Map<GraphNodeId, LayoutRect>,
    val width: Float,
    val height: Float,
) {
    fun centerOf(id: GraphNodeId): LayoutPoint? = bounds[id]?.center

    fun centers(): Map<GraphNodeId, LayoutPoint> = bounds.mapValues { (_, rect) -> rect.center }

    fun withCenters(overrides: Map<GraphNodeId, LayoutPoint>): GraphLayout {
        if (overrides.isEmpty()) return this
        val changedBounds = bounds.toMutableMap()
        for ((id, center) in overrides) {
            if (id !in changedBounds) continue
            changedBounds[id] = LayoutRect(
                x = center.x - LayoutMetrics.NODE_RADIUS,
                y = center.y - LayoutMetrics.NODE_RADIUS,
                width = LayoutMetrics.NODE_DIAMETER,
                height = LayoutMetrics.NODE_DIAMETER,
            )
        }
        return GraphLayout(
            bounds = changedBounds,
            width = changedBounds.values.maxOfOrNull { it.right } ?: 0f,
            height = changedBounds.values.maxOfOrNull { it.bottom } ?: 0f,
        )
    }
}

data class LayoutValidation(
    val messages: List<String>,
) {
    val isValid: Boolean get() = messages.isEmpty()
}

object LayoutMetrics {
    const val NODE_RADIUS = 18f
    const val NODE_DIAMETER = NODE_RADIUS * 2f
    const val EDGE_LENGTH = 118f
    const val STRUCTURAL_EDGE_LENGTH = 82f
    const val CLUSTER_EDGE_LENGTH = 58f
    const val MARGIN = 44f
}

private data class ForceEdge(
    val source: GraphNodeId,
    val target: GraphNodeId,
    val length: Float,
    val weight: Float,
)

private data class MutablePoint(var x: Float, var y: Float) {
    fun immutable(): LayoutPoint = LayoutPoint(x, y)
}

/**
 * Bounded force-directed layout over the ADR-008 visible graph projection.
 *
 * Dependency edges are attraction springs, all nodes repel each other, and visible
 * structural parent-child pairs add weaker hierarchy springs. The simulation is
 * deterministic and iteration-bounded so Compose renders completed coordinates only.
 */
fun layoutVisibleGraph(
    visible: VisibleGraph,
    seeds: Map<GraphNodeId, LayoutPoint> = emptyMap(),
): GraphLayout {
    val ids = visible.nodeIds()
    if (ids.isEmpty()) return GraphLayout(emptyMap(), width = 0f, height = 0f)

    val positions = initialPositions(ids, seeds, visible)
    val edges = forceEdges(visible)
    val centers = runCatching {
        gephiForceAtlas2(ids, positions, edges)
    }.getOrElse {
        localForceLayout(ids, positions, edges)
    }
    return graphLayoutFromCenters(centers, normalize = true)
}

fun seedVisibleGraphLayout(
    visible: VisibleGraph,
    seeds: Map<GraphNodeId, LayoutPoint> = emptyMap(),
): GraphLayout {
    val ids = visible.nodeIds()
    if (ids.isEmpty()) return GraphLayout(emptyMap(), width = 0f, height = 0f)
    val centers = initialPositions(ids, seeds, visible).mapValues { it.value.immutable() }
    return graphLayoutFromCenters(centers, normalize = true)
}

fun validateLayout(visible: VisibleGraph, layout: GraphLayout): LayoutValidation {
    val expected = visible.nodeIds().toSet()
    if (expected.isEmpty()) return LayoutValidation(emptyList())

    val messages = mutableListOf<String>()
    if (layout.bounds.isEmpty()) {
        messages += "Layout has no node bounds for ${expected.size} visible nodes."
    }

    val missing = expected - layout.bounds.keys
    if (missing.isNotEmpty()) {
        messages += "Layout is missing ${missing.size} visible node bounds."
    }

    if (!layout.width.isFinite() || !layout.height.isFinite() || layout.width <= 0f || layout.height <= 0f) {
        messages += "Layout dimensions are invalid: ${layout.width} x ${layout.height}."
    }

    val invalidBounds = layout.bounds.count { (_, rect) ->
        !rect.x.isFinite() ||
            !rect.y.isFinite() ||
            !rect.width.isFinite() ||
            !rect.height.isFinite() ||
            rect.width <= 0f ||
            rect.height <= 0f
    }
    if (invalidBounds > 0) {
        messages += "Layout contains $invalidBounds invalid node bounds."
    }

    return LayoutValidation(messages)
}

private fun localForceLayout(
    ids: List<GraphNodeId>,
    positions: MutableMap<GraphNodeId, MutablePoint>,
    edges: List<ForceEdge>,
): Map<GraphNodeId, LayoutPoint> {
    repeat(iterationsFor(ids.size)) {
        stepForceSimulation(ids, positions, edges)
    }
    return positions.mapValues { it.value.immutable() }
}

private fun gephiForceAtlas2(
    ids: List<GraphNodeId>,
    positions: Map<GraphNodeId, MutablePoint>,
    edges: List<ForceEdge>,
): Map<GraphNodeId, LayoutPoint> {
    val graphModel = GraphModelImpl()
    val graph = graphModel.directedGraph
    val factory = graphModel.factory()
    val nodes = ids.associateWith { id ->
        factory.newNode(id.stableId()).also { node ->
            val position = positions.getValue(id)
            node.setPosition(position.x, position.y)
            node.setSize(LayoutMetrics.NODE_DIAMETER)
            graph.addNode(node)
        }
    }

    edges.forEachIndexed { index, edge ->
        val source = nodes[edge.source] ?: return@forEachIndexed
        val target = nodes[edge.target] ?: return@forEachIndexed
        if (source == target) return@forEachIndexed
        graph.addEdge(
            factory.newEdge(
                "edge-$index",
                source,
                target,
                0,
                edge.weight.toDouble(),
                true,
            ),
        )
    }

    val forceAtlas = ForceAtlas2Builder().buildLayout()
    forceAtlas.setGraphModel(graphModel)
    forceAtlas.setAdjustSizes(true)
    forceAtlas.setBarnesHutOptimize(ids.size >= BARNES_HUT_THRESHOLD)
    forceAtlas.setEdgeWeightInfluence(1.0)
    forceAtlas.setGravity(1.0)
    forceAtlas.setScalingRatio(if (ids.size >= 100) 2.0 else 10.0)
    forceAtlas.setThreadsCount(1)
    forceAtlas.initAlgo()
    try {
        repeat(iterationsFor(ids.size)) {
            if (forceAtlas.canAlgo()) forceAtlas.goAlgo()
        }
    } finally {
        forceAtlas.endAlgo()
    }

    return nodes.mapValues { (_, node) -> node.position() }
}

fun graphLayoutFromCenters(
    centers: Map<GraphNodeId, LayoutPoint>,
    normalize: Boolean = false,
): GraphLayout {
    if (centers.isEmpty()) return GraphLayout(emptyMap(), width = 0f, height = 0f)
    val unstacked = centers.withoutStacking()
    val shifted = if (normalize) unstacked.normalized() else unstacked
    val bounds = shifted.mapValues { (_, center) ->
        LayoutRect(
            x = center.x - LayoutMetrics.NODE_RADIUS,
            y = center.y - LayoutMetrics.NODE_RADIUS,
            width = LayoutMetrics.NODE_DIAMETER,
            height = LayoutMetrics.NODE_DIAMETER,
        )
    }
    return GraphLayout(
        bounds = bounds,
        width = bounds.values.maxOf { it.right },
        height = bounds.values.maxOf { it.bottom },
    )
}

private fun VisibleGraph.nodeIds(): List<GraphNodeId> {
    val structural = structuralNodes
        .map { GraphNodeId.Structural(it.node.path) }
        .sortedBy { it.path.sortKey() }
    val external = externalNodes
        .map { GraphNodeId.External(it.id) }
        .sortedBy { it.id.id }
    return structural + external
}

private fun initialPositions(
    ids: List<GraphNodeId>,
    seeds: Map<GraphNodeId, LayoutPoint>,
    visible: VisibleGraph,
): MutableMap<GraphNodeId, MutablePoint> {
    val result = linkedMapOf<GraphNodeId, MutablePoint>()
    val hierarchyParentByChild = visible.hierarchyEdges.associate { it.child.path to it.parent.path }
    val clusterGroups = visible.hierarchyEdges.groupBy({ it.parent.path }, { it.child.path })
    ids.forEachIndexed { index, id ->
        val seed = seeds[id]
        if (seed != null) {
            result[id] = MutablePoint(seed.x, seed.y)
        } else {
            val clusterSeed = (id as? GraphNodeId.Structural)
                ?.path
                ?.let { path ->
                    val parent = hierarchyParentByChild[path] ?: return@let null
                    val parentSeed = seeds[GraphNodeId.Structural(parent)] ?: return@let null
                    val siblings = clusterGroups[parent].orEmpty().sortedBy { it.sortKey() }
                    val siblingIndex = siblings.indexOf(path).takeIf { it >= 0 } ?: index
                    val angle = siblingIndex * GOLDEN_ANGLE
                    val radius = CLUSTER_SEED_RADIUS + sqrt(siblingIndex + 1f) * 8f
                    MutablePoint(
                        parentSeed.x + radius * cos(angle),
                        parentSeed.y + radius * sin(angle),
                    )
                }
            result[id] = clusterSeed ?: run {
                val angle = index * GOLDEN_ANGLE
                val radius = 36f + sqrt(index + 1f) * 52f
                MutablePoint(radius * cos(angle), radius * sin(angle))
            }
        }
    }
    return result
}

private fun forceEdges(visible: VisibleGraph): List<ForceEdge> {
    val dependencyEdges = visible.edges.map {
        ForceEdge(
            source = it.source,
            target = it.target,
            length = LayoutMetrics.EDGE_LENGTH,
            weight = max(1f, sqrt(it.underlying.size.toFloat())),
        )
    }
    val structuralEdges = visible.hierarchyEdges.map { edge ->
        ForceEdge(
            source = edge.parent,
            target = edge.child,
            length = LayoutMetrics.STRUCTURAL_EDGE_LENGTH,
            weight = 0.35f,
        )
    }
    val clusterEdges = childClusterEdges(visible)
    return dependencyEdges + structuralEdges + clusterEdges
}

private fun childClusterEdges(visible: VisibleGraph): List<ForceEdge> {
    val edges = mutableListOf<ForceEdge>()
    val groups = visible.hierarchyEdges.groupBy({ it.parent.path }, { it.child.path })
    for (children in groups.values) {
        if (children.size < 2) continue
        val sortedChildren = children.sortedBy { it.sortKey() }
        for (i in sortedChildren.indices) {
            for (j in i + 1 until sortedChildren.size) {
                edges += ForceEdge(
                    source = GraphNodeId.Structural(sortedChildren[i]),
                    target = GraphNodeId.Structural(sortedChildren[j]),
                    length = LayoutMetrics.CLUSTER_EDGE_LENGTH,
                    weight = 0.55f,
                )
            }
        }
    }
    return edges
}

private fun stepForceSimulation(
    ids: List<GraphNodeId>,
    positions: MutableMap<GraphNodeId, MutablePoint>,
    edges: List<ForceEdge>,
) {
    val movement = ids.associateWithTo(linkedMapOf()) { MutablePoint(0f, 0f) }

    for (i in ids.indices) {
        for (j in i + 1 until ids.size) {
            val a = positions.getValue(ids[i])
            val b = positions.getValue(ids[j])
            val vector = resolvedVector(a.x - b.x, a.y - b.y, i + j + 1)
            val distanceSquared = max(1f, vector.x * vector.x + vector.y * vector.y)
            val distance = sqrt(distanceSquared)
            val force = REPULSION / distanceSquared
            val fx = vector.x / distance * force
            val fy = vector.y / distance * force
            movement.getValue(ids[i]).x += fx
            movement.getValue(ids[i]).y += fy
            movement.getValue(ids[j]).x -= fx
            movement.getValue(ids[j]).y -= fy
        }
    }

    for (edge in edges) {
        val source = positions[edge.source] ?: continue
        val target = positions[edge.target] ?: continue
        val vector = resolvedVector(target.x - source.x, target.y - source.y, edge.source.hashCode() xor edge.target.hashCode())
        val distance = max(1f, sqrt(vector.x * vector.x + vector.y * vector.y))
        val force = (distance - edge.length) * SPRING * edge.weight
        val fx = vector.x / distance * force
        val fy = vector.y / distance * force
        movement.getValue(edge.source).x += fx
        movement.getValue(edge.source).y += fy
        movement.getValue(edge.target).x -= fx
        movement.getValue(edge.target).y -= fy
    }

    for (id in ids) {
        val position = positions.getValue(id)
        val delta = movement.getValue(id)
        delta.x -= position.x * GRAVITY
        delta.y -= position.y * GRAVITY
        val length = sqrt(delta.x * delta.x + delta.y * delta.y)
        val scale = min(MAX_STEP, length) / max(1f, length)
        position.x += delta.x * scale
        position.y += delta.y * scale
    }
}

private fun Map<GraphNodeId, LayoutPoint>.normalized(): Map<GraphNodeId, LayoutPoint> {
    val minX = values.minOf { it.x } - LayoutMetrics.NODE_RADIUS
    val minY = values.minOf { it.y } - LayoutMetrics.NODE_RADIUS
    val dx = LayoutMetrics.MARGIN - minX
    val dy = LayoutMetrics.MARGIN - minY
    return mapValues { (_, center) -> LayoutPoint(center.x + dx, center.y + dy) }
}

private fun Map<GraphNodeId, LayoutPoint>.withoutStacking(): Map<GraphNodeId, LayoutPoint> {
    if (size < 2) return this
    val ids = keys.sortedBy { it.stableId() }
    val points = mapValuesTo(linkedMapOf()) { (_, center) -> MutablePoint(center.x, center.y) }
    var iteration = 0
    while (iteration < OVERLAP_RESOLUTION_ITERATIONS) {
        var changed = false
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val a = points.getValue(ids[i])
                val b = points.getValue(ids[j])
                val vector = resolvedVector(a.x - b.x, a.y - b.y, i * 31 + j)
                val distance = max(0.001f, sqrt(vector.x * vector.x + vector.y * vector.y))
                if (distance >= MIN_NODE_DISTANCE) continue
                val push = (MIN_NODE_DISTANCE - distance) / 2f
                val dx = vector.x / distance * push
                val dy = vector.y / distance * push
                a.x += dx
                a.y += dy
                b.x -= dx
                b.y -= dy
                changed = true
            }
        }
        if (!changed) break
        iteration++
    }
    return points.mapValues { it.value.immutable() }
}

private fun resolvedVector(x: Float, y: Float, salt: Int): LayoutPoint {
    if (x != 0f || y != 0f) return LayoutPoint(x, y)
    val angle = (salt and 0xFFFF) * GOLDEN_ANGLE
    return LayoutPoint(0.01f * cos(angle), 0.01f * sin(angle))
}

private fun iterationsFor(nodeCount: Int): Int = when {
    nodeCount < 16 -> 180
    nodeCount < 64 -> 240
    else -> 320
}

private fun NodePath.sortKey(): String = segments.joinToString("/") { it.toString() }

private fun GraphNodeId.stableId(): String = when (this) {
    is GraphNodeId.Structural -> "s:${path.sortKey()}"
    is GraphNodeId.External -> "e:${id.id}"
}

private fun Node.position(): LayoutPoint = LayoutPoint(x(), y())

private const val REPULSION = 4_800f
private const val SPRING = 0.026f
private const val GRAVITY = 0.006f
private const val MAX_STEP = 9f
private const val CLUSTER_SEED_RADIUS = 34f
private const val MIN_NODE_DISTANCE = LayoutMetrics.NODE_DIAMETER + 8f
private const val OVERLAP_RESOLUTION_ITERATIONS = 96
private const val BARNES_HUT_THRESHOLD = 64
private const val GOLDEN_ANGLE = (PI * (3.0 - 2.23606797749979)).toFloat()

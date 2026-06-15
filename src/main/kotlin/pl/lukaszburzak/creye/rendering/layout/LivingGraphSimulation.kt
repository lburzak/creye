package pl.lukaszburzak.creye.rendering.layout

import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphSimulationViewport(val width: Float, val height: Float) {
    val center: LayoutPoint get() = LayoutPoint(width / 2f, height / 2f)
}

data class LivingGraphSimulationConfig(
    val nodeRepulsion: Float = 3_200f,
    val springStrength: Float = 0.028f,
    val damping: Float = 0.88f,
    val gravity: Float = 0.010f,
    // Off by default: a perpetual drift force never decays, so the graph would never
    // settle and nodes stay hard to click. Opt in per-config for a "living" effect.
    val driftStrength: Float = 0f,
    val driftFrequency: Float = 0.025f,
    val collisionStrength: Float = 0.42f,
    val maxVelocity: Float = 18f,
    val dragVelocityTransfer: Float = 0.75f,
    val propagationTransfer: Float = 0.18f,
    val viewportPadding: Float = LayoutMetrics.MARGIN,
    /** Containment region size as a multiple of the viewport, centered on it. >1 lets the graph spread off-screen (reachable by pan/zoom) while staying bounded. */
    val boundaryScale: Float = 2.5f,
    val minNodeDistance: Float = LayoutMetrics.NODE_DIAMETER + 8f,
)

/**
 * Render-facing continuous force simulation.
 *
 * ForceAtlas2 remains the bounded initial layout engine; this class owns the live
 * interaction dynamics over already projected visible nodes and render coordinates.
 */
class LivingGraphSimulation(
    visible: VisibleGraph,
    initialCenters: Map<GraphNodeId, LayoutPoint>,
    private val config: LivingGraphSimulationConfig = LivingGraphSimulationConfig(),
) {
    private val ids = visible.nodeIds()
    private val edges = visible.layoutForceEdges()
    private val bodies = linkedMapOf<GraphNodeId, SimulationBody>()
    private val neighbors = edges
        .flatMap { listOf(it.source to (it.target to it.weight), it.target to (it.source to it.weight)) }
        .groupBy({ it.first }, { it.second })
    private var viewport: GraphSimulationViewport? = null
    private var dragged: GraphNodeId? = null
    private var tick = 0

    init {
        ids.forEachIndexed { index, id ->
            val center = initialCenters[id] ?: fallbackCenter(index)
            bodies[id] = SimulationBody(MutablePoint(center.x, center.y), MutablePoint(0f, 0f))
        }
    }

    fun setViewport(width: Float, height: Float) {
        if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
            viewport = GraphSimulationViewport(width, height)
        }
    }

    fun centers(): Map<GraphNodeId, LayoutPoint> =
        bodies.mapValues { (_, body) -> body.position.immutable() }

    fun layout(): GraphLayout =
        graphLayoutFromCenters(centers(), normalize = false, resolveOverlap = false)

    fun startDrag(id: GraphNodeId) {
        if (id in bodies) dragged = id
    }

    fun drag(id: GraphNodeId, center: LayoutPoint, delta: LayoutPoint) {
        val body = bodies[id] ?: return
        dragged = id
        body.position.x = center.x
        body.position.y = center.y
        body.velocity.x = delta.x * config.dragVelocityTransfer
        body.velocity.y = delta.y * config.dragVelocityTransfer
        disturbNeighbors(id, delta.x * config.propagationTransfer, delta.y * config.propagationTransfer)
    }

    fun endDrag(id: GraphNodeId?) {
        if (id == null || dragged == id) dragged = null
    }

    fun disturb(id: GraphNodeId, impulse: LayoutPoint) {
        val body = bodies[id] ?: return
        body.velocity.x += impulse.x
        body.velocity.y += impulse.y
        disturbNeighbors(id, impulse.x * config.propagationTransfer, impulse.y * config.propagationTransfer)
    }

    fun step(deltaTime: Float = 1f) {
        if (bodies.isEmpty()) return
        val dt = deltaTime.coerceIn(0.1f, 2.0f)
        val forces = ids.associateWithTo(linkedMapOf()) { MutablePoint(0f, 0f) }

        applyRepulsionAndCollision(forces)
        applySpringForces(forces)
        applyGravity(forces)
        applyDrift(forces)
        applyForces(forces, dt)
        tick++
    }

    fun kineticEnergy(): Float =
        bodies.values.sumOf { body ->
            val speedSquared = body.velocity.x * body.velocity.x + body.velocity.y * body.velocity.y
            speedSquared.toDouble()
        }.toFloat()

    private fun applyRepulsionAndCollision(forces: MutableMap<GraphNodeId, MutablePoint>) {
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val a = bodies.getValue(ids[i]).position
                val b = bodies.getValue(ids[j]).position
                val vector = resolvedVector(a.x - b.x, a.y - b.y, i * 31 + j)
                val distanceSquared = max(1f, vector.x * vector.x + vector.y * vector.y)
                val distance = sqrt(distanceSquared)
                val repulsion = config.nodeRepulsion / distanceSquared
                val collision = if (distance < config.minNodeDistance) {
                    (config.minNodeDistance - distance) * config.collisionStrength
                } else {
                    0f
                }
                val force = repulsion + collision
                val fx = vector.x / distance * force
                val fy = vector.y / distance * force
                forces.getValue(ids[i]).x += fx
                forces.getValue(ids[i]).y += fy
                forces.getValue(ids[j]).x -= fx
                forces.getValue(ids[j]).y -= fy
            }
        }
    }

    private fun applySpringForces(forces: MutableMap<GraphNodeId, MutablePoint>) {
        for (edge in edges) {
            val source = bodies[edge.source]?.position ?: continue
            val target = bodies[edge.target]?.position ?: continue
            val vector = resolvedVector(
                target.x - source.x,
                target.y - source.y,
                edge.source.hashCode() xor edge.target.hashCode(),
            )
            val distance = max(1f, sqrt(vector.x * vector.x + vector.y * vector.y))
            val force = (distance - edge.length) * config.springStrength * edge.weight
            val fx = vector.x / distance * force
            val fy = vector.y / distance * force
            forces.getValue(edge.source).x += fx
            forces.getValue(edge.source).y += fy
            forces.getValue(edge.target).x -= fx
            forces.getValue(edge.target).y -= fy
        }
    }

    private fun applyGravity(forces: MutableMap<GraphNodeId, MutablePoint>) {
        if (config.gravity == 0f) return
        val anchor = viewport?.center ?: currentCenter()
        for (id in ids) {
            val position = bodies.getValue(id).position
            forces.getValue(id).x += (anchor.x - position.x) * config.gravity
            forces.getValue(id).y += (anchor.y - position.y) * config.gravity
        }
    }

    private fun applyDrift(forces: MutableMap<GraphNodeId, MutablePoint>) {
        if (config.driftStrength == 0f) return
        val phase = tick * config.driftFrequency
        ids.forEachIndexed { index, id ->
            val angle = phase + index * GOLDEN_ANGLE
            forces.getValue(id).x += cos(angle) * config.driftStrength
            forces.getValue(id).y += sin(angle * 0.73f) * config.driftStrength
        }
    }

    private fun applyForces(forces: Map<GraphNodeId, MutablePoint>, deltaTime: Float) {
        val decay = config.damping.coerceIn(0f, 1f).pow(deltaTime)
        for (id in ids) {
            val body = bodies.getValue(id)
            if (dragged == id) {
                body.velocity.x *= decay
                body.velocity.y *= decay
                continue
            }
            val force = forces.getValue(id)
            body.velocity.x = (body.velocity.x + force.x * deltaTime) * decay
            body.velocity.y = (body.velocity.y + force.y * deltaTime) * decay
            body.velocity.clampLength(config.maxVelocity)
            body.position.x += body.velocity.x * deltaTime
            body.position.y += body.velocity.y * deltaTime
            contain(body)
        }
    }

    private fun contain(body: SimulationBody) {
        val view = viewport ?: return
        val centerX = view.width / 2f
        val centerY = view.height / 2f
        val halfWidth = max(
            LayoutMetrics.NODE_RADIUS,
            view.width / 2f * config.boundaryScale - LayoutMetrics.NODE_RADIUS - config.viewportPadding,
        )
        val halfHeight = max(
            LayoutMetrics.NODE_RADIUS,
            view.height / 2f * config.boundaryScale - LayoutMetrics.NODE_RADIUS - config.viewportPadding,
        )
        body.position.x = containAxis(body.position.x, centerX - halfWidth, centerX + halfWidth) { body.velocity.x *= -0.18f }
        body.position.y = containAxis(body.position.y, centerY - halfHeight, centerY + halfHeight) { body.velocity.y *= -0.18f }
    }

    private fun containAxis(value: Float, minValue: Float, maxValue: Float, onBounce: () -> Unit): Float = when {
        value < minValue -> {
            onBounce()
            minValue
        }
        value > maxValue -> {
            onBounce()
            maxValue
        }
        else -> value
    }

    private fun currentCenter(): LayoutPoint {
        val centers = bodies.values.map { it.position }
        return LayoutPoint(
            centers.sumOf { it.x.toDouble() }.toFloat() / centers.size,
            centers.sumOf { it.y.toDouble() }.toFloat() / centers.size,
        )
    }

    private fun disturbNeighbors(id: GraphNodeId, impulseX: Float, impulseY: Float) {
        val adjacent = neighbors[id].orEmpty()
        if (adjacent.isEmpty()) return
        val totalWeight = adjacent.sumOf { (_, weight) -> weight.toDouble() }.toFloat().coerceAtLeast(1f)
        for ((neighbor, weight) in adjacent) {
            val body = bodies[neighbor] ?: continue
            val scale = weight / totalWeight
            body.velocity.x += impulseX * scale
            body.velocity.y += impulseY * scale
        }
    }

    private fun fallbackCenter(index: Int): LayoutPoint {
        val angle = index * GOLDEN_ANGLE
        val radius = 80f + sqrt(index + 1f) * 48f
        return LayoutPoint(radius * cos(angle), radius * sin(angle))
    }

    private fun MutablePoint.clampLength(maxLength: Float) {
        val length = sqrt(x * x + y * y)
        if (length <= maxLength || length == 0f) return
        val scale = maxLength / length
        x *= scale
        y *= scale
    }

    private fun resolvedVector(x: Float, y: Float, salt: Int): LayoutPoint {
        if (x != 0f || y != 0f) return LayoutPoint(x, y)
        val angle = (salt and 0xFFFF) * GOLDEN_ANGLE
        return LayoutPoint(0.01f * cos(angle), 0.01f * sin(angle))
    }
}

private data class SimulationBody(
    val position: MutablePoint,
    val velocity: MutablePoint,
)

private const val GOLDEN_ANGLE = (PI * (3.0 - 2.23606797749979)).toFloat()

package pl.lukaszburzak.creye.rendering.layout

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.projection.VisibleEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.VisibleNode

class LivingGraphSimulationTest {

    private val module = NodeSegment.Module("app")
    private val pkg = NodeSegment.Package("com.a")
    private val file = NodeSegment.File("A.kt", "src/A.kt")
    private val symbolA = path(NodeSegment.Symbol("a", null))
    private val symbolB = path(NodeSegment.Symbol("b", null))
    private val symbolC = path(NodeSegment.Symbol("c", null))
    private val a = GraphNodeId.Structural(symbolA)
    private val b = GraphNodeId.Structural(symbolB)
    private val c = GraphNodeId.Structural(symbolC)

    @Test
    fun `dragging a node propagates movement through connected neighbors`() {
        val simulation = LivingGraphSimulation(
            visible = visibleGraph(
                paths = listOf(symbolA, symbolB, symbolC),
                edges = listOf(edge(a, b)),
            ),
            initialCenters = mapOf(
                a to LayoutPoint(100f, 100f),
                b to LayoutPoint(220f, 100f),
                c to LayoutPoint(360f, 100f),
            ),
            config = quietConfig(springStrength = 0.05f, damping = 0.96f),
        )

        simulation.drag(a, LayoutPoint(170f, 100f), LayoutPoint(70f, 0f))
        repeat(8) { simulation.step() }

        assertTrue(simulation.centerOf(b).x > 220f)
        assertEquals(360f, simulation.centerOf(c).x, 0.001f)
    }

    @Test
    fun `damping fades a disturbance when no new force is applied`() {
        val simulation = LivingGraphSimulation(
            visible = visibleGraph(listOf(symbolA)),
            initialCenters = mapOf(a to LayoutPoint(100f, 100f)),
            config = quietConfig(damping = 0.5f),
        )

        simulation.disturb(a, LayoutPoint(10f, 0f))
        val before = simulation.kineticEnergy()
        simulation.step()

        assertTrue(simulation.kineticEnergy() < before)
    }

    @Test
    fun `drift strength is configurable`() {
        val still = LivingGraphSimulation(
            visible = visibleGraph(listOf(symbolA)),
            initialCenters = mapOf(a to LayoutPoint(100f, 100f)),
            config = quietConfig(driftStrength = 0f, damping = 1f),
        )
        val drifting = LivingGraphSimulation(
            visible = visibleGraph(listOf(symbolA)),
            initialCenters = mapOf(a to LayoutPoint(100f, 100f)),
            config = quietConfig(driftStrength = 0.5f, damping = 1f),
        )

        repeat(5) {
            still.step()
            drifting.step()
        }

        assertEquals(LayoutPoint(100f, 100f), still.centerOf(a))
        assertTrue(drifting.centerOf(a).x > 100f)
    }

    @Test
    fun `dragged node stays pinned until released then keeps motion`() {
        val simulation = LivingGraphSimulation(
            visible = visibleGraph(listOf(symbolA)),
            initialCenters = mapOf(a to LayoutPoint(100f, 100f)),
            config = quietConfig(damping = 1f),
        )

        simulation.startDrag(a)
        simulation.drag(a, LayoutPoint(150f, 100f), LayoutPoint(30f, 0f))
        simulation.step()

        assertEquals(150f, simulation.centerOf(a).x, 0.001f)

        simulation.endDrag(a)
        simulation.step()

        assertTrue(simulation.centerOf(a).x > 150f)
    }

    @Test
    fun `viewport containment prevents runaway escape`() {
        val simulation = LivingGraphSimulation(
            visible = visibleGraph(listOf(symbolA)),
            initialCenters = mapOf(a to LayoutPoint(185f, 100f)),
            config = quietConfig(damping = 1f, viewportPadding = 0f, maxVelocity = 100f),
        )
        simulation.setViewport(width = 200f, height = 200f)

        simulation.disturb(a, LayoutPoint(80f, 0f))
        repeat(4) { simulation.step() }

        assertTrue(simulation.centerOf(a).x <= 200f - LayoutMetrics.NODE_RADIUS)
    }

    private fun path(symbol: NodeSegment.Symbol): NodePath =
        NodePath(listOf(module, pkg, file, symbol))

    private fun visibleGraph(
        paths: List<NodePath>,
        edges: List<VisibleEdge> = emptyList(),
    ): VisibleGraph =
        VisibleGraph(
            structuralNodes = paths.map {
                VisibleNode(StructuralNode(it, it.displayName(), change = null), isCollapsed = false, internalizedEdges = emptySet())
            },
            externalNodes = emptyList(),
            edges = edges,
        )

    private fun edge(source: GraphNodeId.Structural, target: GraphNodeId.Structural): VisibleEdge {
        val dependency = DependencyEdge(source.path, target, DependencyClassification.INTERNAL, DependencyKind.CALL)
        return VisibleEdge(source, target, setOf(DependencyClassification.INTERNAL), setOf(dependency))
    }

    private fun LivingGraphSimulation.centerOf(id: GraphNodeId): LayoutPoint =
        centers().getValue(id)

    private fun quietConfig(
        springStrength: Float = 0f,
        damping: Float = 0.88f,
        driftStrength: Float = 0f,
        viewportPadding: Float = LayoutMetrics.MARGIN,
        maxVelocity: Float = 18f,
    ): LivingGraphSimulationConfig =
        LivingGraphSimulationConfig(
            nodeRepulsion = 0f,
            springStrength = springStrength,
            damping = damping,
            gravity = 0f,
            driftStrength = driftStrength,
            collisionStrength = 0f,
            maxVelocity = maxVelocity,
            viewportPadding = viewportPadding,
        )
}


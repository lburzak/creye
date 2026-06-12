package pl.lukaszburzak.creye.rendering.layout

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotSame
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.ExternalSymbolId
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.projection.VisibleEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.VisibleHierarchyEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleNode
import kotlin.math.hypot

class GraphLayoutTest {

    private fun path(vararg segments: NodeSegment) = NodePath(segments.toList())

    private val module = NodeSegment.Module("app")
    private val pkg = NodeSegment.Package("com.a")
    private val file = path(module, pkg, NodeSegment.File("A.kt", "src/A.kt"))

    private fun visibleGraph(
        paths: List<NodePath>,
        externals: List<ExternalNode> = emptyList(),
        edges: List<VisibleEdge> = emptyList(),
    ): VisibleGraph {
        val structuralNodes = paths.map {
            VisibleNode(StructuralNode(it, it.displayName(), change = null), isCollapsed = false, internalizedEdges = emptySet())
        }
        val visiblePaths = paths.toSet()
        val hierarchyEdges = paths.mapNotNull { path ->
            val parent = path.parent() ?: return@mapNotNull null
            if (parent !in visiblePaths) return@mapNotNull null
            VisibleHierarchyEdge(GraphNodeId.Structural(parent), GraphNodeId.Structural(path))
        }
        return VisibleGraph(
            structuralNodes = structuralNodes,
            externalNodes = externals,
            edges = edges,
            hierarchyEdges = hierarchyEdges,
        )
    }

    private fun manySymbols(count: Int): List<NodePath> =
        listOf(path(module), path(module, pkg), file) +
            (1..count).map { NodePath(file.segments + NodeSegment.Symbol("symbol$it", null)) }

    @Test
    fun `every visible node gets constant circle bounds`() {
        val paths = manySymbols(12)
        val externals = listOf(ExternalNode(ExternalSymbolId("kotlin/io/println", "println")))

        val layout = layoutVisibleGraph(visibleGraph(paths, externals))

        assertEquals(paths.size + externals.size, layout.bounds.size)
        assertTrue(layout.bounds.values.all { it.width == LayoutMetrics.NODE_DIAMETER })
        assertTrue(layout.bounds.values.all { it.height == LayoutMetrics.NODE_DIAMETER })
    }

    @Test
    fun `dependency edges influence force layout`() {
        val a = NodePath(file.segments + NodeSegment.Symbol("a", null))
        val b = NodePath(file.segments + NodeSegment.Symbol("b", null))
        val c = NodePath(file.segments + NodeSegment.Symbol("c", null))
        val source = GraphNodeId.Structural(a)
        val target = GraphNodeId.Structural(b)
        val edge = DependencyEdge(a, target, DependencyClassification.INTERNAL, DependencyKind.CALL)
        val connected = visibleGraph(
            paths = listOf(path(module), path(module, pkg), file, a, b, c),
            edges = listOf(VisibleEdge(source, target, setOf(DependencyClassification.INTERNAL), setOf(edge))),
        )
        val disconnected = visibleGraph(paths = listOf(path(module), path(module, pkg), file, a, b, c))

        val connectedDistance = layoutVisibleGraph(connected).distance(source, target)
        val disconnectedDistance = layoutVisibleGraph(disconnected).distance(source, target)

        assertTrue("connected=$connectedDistance disconnected=$disconnectedDistance", connectedDistance < disconnectedDistance)
    }

    @Test
    fun `nodes seeded at the same point are separated to avoid stacking`() {
        val paths = manySymbols(5)
        val seeds: Map<GraphNodeId, LayoutPoint> =
            paths.associate { GraphNodeId.Structural(it) as GraphNodeId to LayoutPoint(0f, 0f) }

        val layout = layoutVisibleGraph(visibleGraph(paths), seeds)
        val rects = layout.bounds.values.toList()

        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertFalse("rects[$i] overlaps rects[$j]", rects[i].overlaps(rects[j]))
            }
        }
    }

    @Test
    fun `children of a visible expanded parent are seeded as a cluster`() {
        val packageA = path(module, NodeSegment.Package("com.a"))
        val packageB = path(module, NodeSegment.Package("com.b"))
        val parent = path(module)
        val graph = visibleGraph(listOf(parent, packageA, packageB))

        val layout = seedVisibleGraphLayout(
            graph,
            seeds = mapOf(GraphNodeId.Structural(parent) to LayoutPoint(400f, 400f)),
        )

        assertTrue(layout.distance(GraphNodeId.Structural(packageA), GraphNodeId.Structural(packageB)) < 90f)
        assertTrue(layout.distance(GraphNodeId.Structural(parent), GraphNodeId.Structural(packageA)) < 80f)
        assertTrue(layout.distance(GraphNodeId.Structural(parent), GraphNodeId.Structural(packageB)) < 80f)
    }

    @Test
    fun `layout uses seed positions for stable recomputation`() {
        val paths = manySymbols(3)
        val id = GraphNodeId.Structural(paths.last())
        val unseeded = layoutVisibleGraph(visibleGraph(paths))
        val seeded = layoutVisibleGraph(
            visibleGraph(paths),
            seeds = mapOf(id to LayoutPoint(600f, 600f)),
        )

        assertFalse(unseeded.centerOf(id) == seeded.centerOf(id))
    }

    @Test
    fun `manual center override reroutes only render coordinates`() {
        val layout = layoutVisibleGraph(visibleGraph(manySymbols(3)))
        val id = layout.bounds.keys.first()
        val moved = layout.withCenters(mapOf(id to LayoutPoint(-40f, 25f)))

        assertEquals(LayoutPoint(-40f, 25f), moved.centerOf(id))
        assertNotSame(layout, moved)
    }

    @Test
    fun `layout is deterministic`() {
        val graph = visibleGraph(manySymbols(15), listOf(ExternalNode(ExternalSymbolId("x", "x"))))

        assertEquals(layoutVisibleGraph(graph), layoutVisibleGraph(graph))
    }

    @Test
    fun `empty graph yields empty layout`() {
        val layout = layoutVisibleGraph(visibleGraph(emptyList()))

        assertTrue(layout.bounds.isEmpty())
        assertEquals(0f, layout.width)
        assertEquals(0f, layout.height)
    }

    @Test
    fun `seed layout covers visible nodes without force simulation`() {
        val paths = manySymbols(5)
        val layout = seedVisibleGraphLayout(visibleGraph(paths))

        assertEquals(paths.size, layout.bounds.size)
        assertTrue(validateLayout(visibleGraph(paths), layout).isValid)
    }

    @Test
    fun `validation reports missing visible node bounds`() {
        val graph = visibleGraph(manySymbols(2))
        val layout = GraphLayout(bounds = emptyMap(), width = 0f, height = 0f)

        val validation = validateLayout(graph, layout)

        assertFalse(validation.isValid)
        assertTrue(validation.messages.any { it.contains("no node bounds") })
    }

    @Test
    fun `validation reports invalid coordinates`() {
        val graph = visibleGraph(manySymbols(1))
        val id = GraphNodeId.Structural(graph.structuralNodes.first().node.path)
        val layout = GraphLayout(
            bounds = mapOf(id to LayoutRect(Float.NaN, 0f, LayoutMetrics.NODE_DIAMETER, LayoutMetrics.NODE_DIAMETER)),
            width = Float.NaN,
            height = LayoutMetrics.NODE_DIAMETER,
        )

        val validation = validateLayout(graph, layout)

        assertFalse(validation.isValid)
        assertTrue(validation.messages.any { it.contains("invalid") })
    }

    private fun GraphLayout.distance(source: GraphNodeId, target: GraphNodeId): Float {
        val a = centerOf(source)!!
        val b = centerOf(target)!!
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
    }

    private fun NodePath.parent(): NodePath? =
        if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))
}

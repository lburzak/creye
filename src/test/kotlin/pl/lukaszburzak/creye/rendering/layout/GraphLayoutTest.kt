package pl.lukaszburzak.creye.rendering.layout

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.ExternalSymbolId
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.VisibleNode

class GraphLayoutTest {

    private fun path(vararg segments: NodeSegment) = NodePath(segments.toList())

    private val module = NodeSegment.Module("app")
    private val pkg = NodeSegment.Package("com.a")

    private fun visibleGraph(paths: List<NodePath>, externals: List<ExternalNode> = emptyList()) =
        VisibleGraph(
            structuralNodes = paths.map {
                VisibleNode(StructuralNode(it, it.displayName(), change = null), isCollapsed = false, internalizedEdges = emptySet())
            },
            externalNodes = externals,
            edges = emptyList(),
        )

    private fun manySymbols(count: Int): List<NodePath> {
        val file = path(module, pkg, NodeSegment.File("A.kt", "src/A.kt"))
        return listOf(path(module), path(module, pkg), file) +
            (1..count).map { NodePath(file.segments + NodeSegment.Symbol("symbol$it", null)) }
    }

    @Test
    fun `every visible node gets bounds`() {
        val paths = manySymbols(12)
        val externals = listOf(ExternalNode(ExternalSymbolId("kotlin/io/println", "println")))

        val layout = layoutVisibleGraph(visibleGraph(paths, externals))

        assertEquals(paths.size + externals.size, layout.bounds.size)
    }

    @Test
    fun `children lie within parent bounds below header band`() {
        val layout = layoutVisibleGraph(visibleGraph(manySymbols(12)))

        for ((id, rect) in layout.bounds) {
            val structural = id as GraphNodeId.Structural
            if (structural.path.segments.size == 1) continue
            val parent = NodePath(structural.path.segments.dropLast(1))
            val parentRect = layout.bounds.getValue(GraphNodeId.Structural(parent))
            assertTrue("$structural outside parent", parentRect.contains(rect))
            assertTrue("$structural overlaps header", rect.y >= parentRect.y + LayoutMetrics.HEADER_HEIGHT)
        }
    }

    @Test
    fun `siblings never overlap`() {
        val layout = layoutVisibleGraph(visibleGraph(manySymbols(20)))

        val byParent = layout.bounds.keys
            .filterIsInstance<GraphNodeId.Structural>()
            .filter { it.path.segments.size > 1 }
            .groupBy { NodePath(it.path.segments.dropLast(1)) }
        for ((_, siblings) in byParent) {
            for (i in siblings.indices) for (j in i + 1 until siblings.size) {
                val a = layout.bounds.getValue(siblings[i])
                val b = layout.bounds.getValue(siblings[j])
                assertFalse("${siblings[i]} overlaps ${siblings[j]}", a.overlaps(b))
            }
        }
    }

    @Test
    fun `root modules never overlap`() {
        val paths = listOf(
            path(NodeSegment.Module("app")),
            path(NodeSegment.Module("lib")),
            path(NodeSegment.Module("core")),
        )

        val layout = layoutVisibleGraph(visibleGraph(paths))

        val rects = layout.bounds.values.toList()
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse(rects[i].overlaps(rects[j]))
        }
    }

    @Test
    fun `external nodes sit right of all structural bounds`() {
        val externals = listOf(
            ExternalNode(ExternalSymbolId("kotlin/io/println", "println")),
            ExternalNode(ExternalSymbolId("java/util/List", "List")),
        )

        val layout = layoutVisibleGraph(visibleGraph(manySymbols(6), externals))

        val structuralRight = layout.bounds
            .filterKeys { it is GraphNodeId.Structural }
            .values.maxOf { it.right }
        val externalRects = layout.bounds.filterKeys { it is GraphNodeId.External }.values
        assertEquals(2, externalRects.size)
        assertTrue(externalRects.all { it.x >= structuralRight })
        val sorted = externalRects.sortedBy { it.y }
        assertFalse(sorted[0].overlaps(sorted[1]))
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
}

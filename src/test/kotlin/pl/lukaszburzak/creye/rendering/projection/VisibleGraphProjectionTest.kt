package pl.lukaszburzak.creye.rendering.projection

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.ExternalNode
import pl.lukaszburzak.creye.domain.graph.ExternalSymbolId
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

class VisibleGraphProjectionTest {

    private val moduleA = NodePath(listOf(NodeSegment.Module("app")))
    private val packageA = moduleA.child(NodeSegment.Package("com.a"))
    private val fileA = packageA.child(NodeSegment.File("A.kt", "src/main/kotlin/com/a/A.kt"))
    private val symbolA = fileA.child(NodeSegment.Symbol("a", null))
    private val symbolA2 = fileA.child(NodeSegment.Symbol("a2", null))

    private val moduleB = NodePath(listOf(NodeSegment.Module("lib")))
    private val packageB = moduleB.child(NodeSegment.Package("com.b"))
    private val fileB = packageB.child(NodeSegment.File("B.kt", "src/main/kotlin/com/b/B.kt"))
    private val symbolB = fileB.child(NodeSegment.Symbol("b", null))

    private val external = ExternalSymbolId("kotlin/io/println", "println")

    private fun NodePath.child(segment: NodeSegment) = NodePath(segments + segment)

    private fun nodes(vararg paths: NodePath) =
        paths.map { StructuralNode(it, it.displayName(), change = null) }

    private fun edge(
        source: NodePath,
        target: GraphNodeId,
        classification: DependencyClassification = DependencyClassification.INTERNAL,
        kind: DependencyKind = DependencyKind.CALL,
    ) = DependencyEdge(source, target, classification, kind)

    private fun ancestorsAndSelf(path: NodePath) =
        (1..path.segments.size).map { NodePath(path.segments.subList(0, it)) }

    private fun graphOf(edges: List<DependencyEdge>, externalNodes: List<ExternalNode> = emptyList()): DependencyGraph {
        val paths = edges.flatMap { e ->
            ancestorsAndSelf(e.source) + ((e.target as? GraphNodeId.Structural)?.let { ancestorsAndSelf(it.path) } ?: emptyList())
        }.distinct()
        return DependencyGraph(
            structuralNodes = nodes(*paths.toTypedArray()),
            externalNodes = externalNodes,
            edges = edges,
        )
    }

    @Test
    fun `fully expanded projection reproduces domain edges unchanged`() {
        val domainEdges = listOf(
            edge(symbolA, GraphNodeId.Structural(symbolB)),
            edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL),
        )
        val graph = graphOf(domainEdges, listOf(ExternalNode(external)))

        val visible = projectVisibleGraph(graph, collapsed = emptySet())

        assertEquals(graph.structuralNodes.size, visible.structuralNodes.size)
        assertTrue(visible.structuralNodes.none { it.isCollapsed })
        assertTrue(visible.structuralNodes.all { it.internalizedEdges.isEmpty() })
        assertEquals(
            domainEdges.map { Triple(GraphNodeId.Structural(it.source), it.target, setOf(it)) }.toSet(),
            visible.edges.map { Triple(it.source, it.target, it.underlying) }.toSet(),
        )
    }

    @Test
    fun `endpoints lift to nearest visible ancestor on both sides`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, collapsed = setOf(fileA, moduleB))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(fileA), edge.source)
        assertEquals(GraphNodeId.Structural(moduleB), edge.target)
    }

    @Test
    fun `outermost collapsed ancestor wins over nested collapsed ancestor`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, collapsed = setOf(packageA, fileA))

        assertEquals(GraphNodeId.Structural(packageA), visible.edges.single().source)
    }

    @Test
    fun `hidden descendants of collapsed node are not visible`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, collapsed = setOf(fileA))

        val visiblePaths = visible.structuralNodes.map { it.node.path }
        assertTrue(fileA in visiblePaths)
        assertFalse(symbolA in visiblePaths)
        assertTrue(visible.structuralNodes.single { it.node.path == fileA }.isCollapsed)
    }

    @Test
    fun `same node lift becomes badge not edge`() {
        val internalEdge = edge(symbolA, GraphNodeId.Structural(symbolA2), DependencyClassification.COHESION)
        val graph = graphOf(listOf(internalEdge))

        val visible = projectVisibleGraph(graph, collapsed = setOf(fileA))

        assertTrue(visible.edges.isEmpty())
        val badge = visible.structuralNodes.single { it.node.path == fileA }.internalizedEdges
        assertEquals(setOf(internalEdge), badge)
    }

    @Test
    fun `opposing directions stay distinct after dedup`() {
        val aToB = edge(symbolA, GraphNodeId.Structural(symbolB))
        val bToA = edge(symbolB, GraphNodeId.Structural(symbolA))
        val graph = graphOf(listOf(aToB, bToA))

        val visible = projectVisibleGraph(graph, collapsed = setOf(fileA, fileB))

        assertEquals(2, visible.edges.size)
        val pairs = visible.edges.map { it.source to it.target }.toSet()
        assertTrue(GraphNodeId.Structural(fileA) to GraphNodeId.Structural(fileB) in pairs)
        assertTrue(GraphNodeId.Structural(fileB) to GraphNodeId.Structural(fileA) in pairs)
    }

    @Test
    fun `merged edge carries classification set and all underlying edges`() {
        val cohesion = edge(symbolA, GraphNodeId.Structural(symbolB), DependencyClassification.COHESION)
        val internal = edge(symbolA2, GraphNodeId.Structural(symbolB), DependencyClassification.INTERNAL, DependencyKind.TYPE_REFERENCE)
        val graph = graphOf(listOf(cohesion, internal))

        val visible = projectVisibleGraph(graph, collapsed = setOf(fileA))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(fileA), edge.source)
        assertEquals(GraphNodeId.Structural(symbolB), edge.target)
        assertEquals(setOf(DependencyClassification.COHESION, DependencyClassification.INTERNAL), edge.classifications)
        assertEquals(setOf(cohesion, internal), edge.underlying)
    }

    @Test
    fun `external endpoint is never lifted and external nodes survive projection`() {
        val toExternal = edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL)
        val graph = graphOf(listOf(toExternal), listOf(ExternalNode(external)))

        val visible = projectVisibleGraph(graph, collapsed = setOf(moduleA))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(moduleA), edge.source)
        assertEquals(GraphNodeId.External(external), edge.target)
        assertEquals(listOf(ExternalNode(external)), visible.externalNodes)
    }

    @Test
    fun `changed state passes through on visible nodes`() {
        val changed = StructuralNode(symbolA, "a", ChangeKind.MODIFIED)
        val graph = DependencyGraph(
            structuralNodes = nodes(moduleA, packageA, fileA) + changed,
            edges = emptyList(),
        )

        val visible = projectVisibleGraph(graph, collapsed = emptySet())

        assertEquals(ChangeKind.MODIFIED, visible.structuralNodes.single { it.node.path == symbolA }.node.change)
    }
}

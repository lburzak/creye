package pl.lukaszburzak.creye.domain.graph

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

class GraphScopeTest {

    private val source = path("app", "A.kt", "A", "a")
    private val dependency = path("lib", "B.kt", "B", "b")
    private val unrelated = path("app", "C.kt", "C", "c")
    private val external = ExternalSymbolId("kotlin/String", "String")

    private val edgeToDependency = DependencyEdge(
        source = source,
        target = GraphNodeId.Structural(dependency),
        classification = DependencyClassification.INTERNAL,
        kind = DependencyKind.CALL,
    )
    private val edgeToExternal = DependencyEdge(
        source = source,
        target = GraphNodeId.External(external),
        classification = DependencyClassification.EXTERNAL,
        kind = DependencyKind.TYPE_REFERENCE,
    )
    private val unrelatedEdge = DependencyEdge(
        source = unrelated,
        target = GraphNodeId.Structural(dependency),
        classification = DependencyClassification.INTERNAL,
        kind = DependencyKind.CALL,
    )

    private val graph = DependencyGraph(
        structuralNodes = (source.ancestorsAndSelf() + dependency.ancestorsAndSelf() + unrelated.ancestorsAndSelf())
            .distinct()
            .map { StructuralNode(it, it.displayName(), change = null) },
        externalNodes = listOf(ExternalNode(external)),
        edges = listOf(edgeToDependency, edgeToExternal, unrelatedEdge),
    )

    @Test
    fun `scope keeps the node, its dependency closure, and ancestors`() {
        val scoped = graph.scopedTo(source)
        val paths = scoped.structuralNodes.map { it.path }.toSet()

        assertTrue(paths.containsAll(source.ancestorsAndSelf()))
        assertTrue(paths.containsAll(dependency.ancestorsAndSelf()))
        assertEquals(ExternalNode(external), scoped.externalNodes.single())
        assertTrue(scoped.edges.contains(edgeToDependency))
        assertTrue(scoped.edges.contains(edgeToExternal))
    }

    @Test
    fun `scope drops unrelated nodes and their edges`() {
        val scoped = graph.scopedTo(source)
        val paths = scoped.structuralNodes.map { it.path }.toSet()

        assertFalse(paths.contains(unrelated))
        assertFalse(scoped.edges.contains(unrelatedEdge))
    }

    private fun path(module: String, fileName: String, className: String, symbol: String) = NodePath(
        listOf(
            NodeSegment.Module(module),
            NodeSegment.Package(NodeSegment.DEFAULT_PACKAGE),
            NodeSegment.File(fileName, "src/$fileName"),
            NodeSegment.Class(className),
            NodeSegment.Symbol(symbol, null),
        ),
    )

    private fun NodePath.ancestorsAndSelf(): List<NodePath> =
        segments.indices.map { NodePath(segments.take(it + 1)) }
}

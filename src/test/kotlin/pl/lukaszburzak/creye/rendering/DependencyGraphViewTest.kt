package pl.lukaszburzak.creye.rendering

import junit.framework.TestCase.assertEquals
import org.junit.Test
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.VisibleNode

class DependencyGraphViewTest {

    private val module = NodePath(listOf(NodeSegment.Module("app")))
    private val pkg = module.child(NodeSegment.Package("com.a"))
    private val fileA = pkg.child(NodeSegment.File("A.kt", "src/A.kt"))
    private val symbolA = fileA.child(NodeSegment.Symbol("a", null))
    private val otherPackage = module.child(NodeSegment.Package("com.b"))

    @Test
    fun `collapse self and siblings removes parent expansion and descendant expansions`() {
        val expanded = setOf(module, pkg, fileA, symbolA, otherPackage)

        assertEquals(setOf(module, otherPackage), collapseSelfAndSiblings(expanded, fileA))
    }

    @Test
    fun `collapse visible expanded parent hides it and sibling frontier`() {
        val expanded = setOf(module, pkg, fileA, otherPackage)

        assertEquals(emptySet<NodePath>(), collapseSelfAndSiblings(expanded, pkg))
    }

    @Test
    fun `collapse root siblings clears root expansions`() {
        val lib = NodePath(listOf(NodeSegment.Module("lib")))
        val expanded = setOf(module, pkg, lib)

        assertEquals(emptySet<NodePath>(), collapseSelfAndSiblings(expanded, module))
    }

    @Test
    fun `caret emphasis resolves to deepest visible ancestor`() {
        val visible = visibleGraph(module, pkg, fileA)

        assertEquals(GraphNodeId.Structural(fileA), closestVisibleNodeToCaret(visible, symbolA))
    }

    @Test
    fun `caret emphasis is absent when no ancestor is visible`() {
        val visible = visibleGraph(otherPackage)

        assertEquals(null, closestVisibleNodeToCaret(visible, symbolA))
    }

    private fun visibleGraph(vararg paths: NodePath) =
        VisibleGraph(
            structuralNodes = paths.map { path ->
                VisibleNode(
                    node = StructuralNode(path, path.segments.last().toString(), change = null),
                    isCollapsed = false,
                    internalizedEdges = emptySet(),
                    hasDescendantChange = false,
                )
            },
            externalNodes = emptyList(),
            edges = emptyList(),
        )

    private fun NodePath.child(segment: NodeSegment): NodePath = NodePath(segments + segment)
}

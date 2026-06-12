package pl.lukaszburzak.creye.rendering

import junit.framework.TestCase.assertEquals
import org.junit.Test
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.StructuralNode
import pl.lukaszburzak.creye.domain.graph.displayName
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

class DependencyGraphViewTest {

    private val module = NodePath(listOf(NodeSegment.Module("app")))
    private val pkg = module.child(NodeSegment.Package("com.a"))
    private val fileA = pkg.child(NodeSegment.File("A.kt", "src/A.kt"))
    private val fileB = pkg.child(NodeSegment.File("B.kt", "src/B.kt"))
    private val symbolA = fileA.child(NodeSegment.Symbol("a", null))
    private val otherPackage = module.child(NodeSegment.Package("com.b"))

    @Test
    fun `collapse self and siblings returns paths with the same parent`() {
        val graph = graphOf(module, pkg, fileA, fileB, symbolA, otherPackage)

        assertEquals(setOf(fileA, fileB), collapseSelfAndSiblings(graph, fileA))
    }

    @Test
    fun `collapse self and siblings handles root nodes`() {
        val lib = NodePath(listOf(NodeSegment.Module("lib")))
        val graph = graphOf(module, lib, pkg)

        assertEquals(setOf(module, lib), collapseSelfAndSiblings(graph, module))
    }

    private fun graphOf(vararg paths: NodePath) = DependencyGraph(
        structuralNodes = paths.map { StructuralNode(it, it.displayName(), change = null) },
    )

    private fun NodePath.child(segment: NodeSegment): NodePath = NodePath(segments + segment)
}

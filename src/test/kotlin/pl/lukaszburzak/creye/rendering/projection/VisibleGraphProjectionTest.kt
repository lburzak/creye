package pl.lukaszburzak.creye.rendering.projection

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.approval.ApprovalCompleteness
import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
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
    fun `collapsed projection shows roots and conflates child edges onto ancestors`() {
        val domainEdges = listOf(
            edge(symbolA, GraphNodeId.Structural(symbolB)),
            edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL),
        )
        val graph = graphOf(domainEdges, listOf(ExternalNode(external)))

        val visible = projectVisibleGraph(graph, expanded = emptySet())

        assertEquals(setOf(moduleA, moduleB), visible.structuralNodes.map { it.node.path }.toSet())
        assertTrue(visible.structuralNodes.all { it.isCollapsed })
        assertEquals(
            setOf(
                Triple(GraphNodeId.Structural(moduleA), GraphNodeId.Structural(moduleB), setOf(domainEdges[0])),
                Triple(GraphNodeId.Structural(moduleA), GraphNodeId.External(external), setOf(domainEdges[1])),
            ),
            visible.edges.map { Triple(it.source, it.target, it.underlying) }.toSet(),
        )
    }

    @Test
    fun `expanded node remains visible and its direct children become visible`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA))

        val visiblePaths = visible.structuralNodes.map { it.node.path }
        assertTrue(moduleA in visiblePaths)
        assertTrue(packageA in visiblePaths)
        assertFalse(fileA in visiblePaths)
        assertFalse(visible.structuralNodes.single { it.node.path == moduleA }.isCollapsed)
        assertTrue(visible.structuralNodes.single { it.node.path == packageA }.isCollapsed)
    }

    @Test
    fun `visible hierarchy edges connect visible direct parents to children`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA))

        assertEquals(
            listOf(VisibleHierarchyEdge(GraphNodeId.Structural(moduleA), GraphNodeId.Structural(packageA))),
            visible.hierarchyEdges,
        )
        assertTrue(visible.hierarchyEdges.none { hierarchy ->
            visible.edges.any { dependency ->
                dependency.source == hierarchy.parent && dependency.target == hierarchy.child
            }
        })
    }

    @Test
    fun `endpoints lift to deepest visible ancestor on both sides`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA, packageA, moduleB, packageB))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(fileA), edge.source)
        assertEquals(GraphNodeId.Structural(fileB), edge.target)
    }

    @Test
    fun `nested expanded ancestor reveals next frontier`() {
        val graph = graphOf(listOf(edge(symbolA, GraphNodeId.Structural(symbolB))))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA, packageA))

        assertEquals(GraphNodeId.Structural(fileA), visible.edges.single().source)
        assertTrue(moduleA in visible.structuralNodes.map { it.node.path })
        assertTrue(packageA in visible.structuralNodes.map { it.node.path })
        assertTrue(fileA in visible.structuralNodes.map { it.node.path })
        assertFalse(symbolA in visible.structuralNodes.map { it.node.path })
        assertEquals(
            setOf(
                VisibleHierarchyEdge(GraphNodeId.Structural(moduleA), GraphNodeId.Structural(packageA)),
                VisibleHierarchyEdge(GraphNodeId.Structural(packageA), GraphNodeId.Structural(fileA)),
            ),
            visible.hierarchyEdges.toSet(),
        )
    }

    @Test
    fun `same visible ancestor lift becomes badge not edge`() {
        val internalEdge = edge(symbolA, GraphNodeId.Structural(symbolA2), DependencyClassification.COHESION)
        val graph = graphOf(listOf(internalEdge))

        val visible = projectVisibleGraph(graph, expanded = emptySet())

        assertTrue(visible.edges.isEmpty())
        val badge = visible.structuralNodes.single { it.node.path == moduleA }.internalizedEdges
        assertEquals(setOf(internalEdge), badge)
    }

    @Test
    fun `opposing directions stay distinct after dedup`() {
        val aToB = edge(symbolA, GraphNodeId.Structural(symbolB))
        val bToA = edge(symbolB, GraphNodeId.Structural(symbolA))
        val graph = graphOf(listOf(aToB, bToA))

        val visible = projectVisibleGraph(graph, expanded = emptySet())

        assertEquals(2, visible.edges.size)
        val pairs = visible.edges.map { it.source to it.target }.toSet()
        assertTrue(GraphNodeId.Structural(moduleA) to GraphNodeId.Structural(moduleB) in pairs)
        assertTrue(GraphNodeId.Structural(moduleB) to GraphNodeId.Structural(moduleA) in pairs)
    }

    @Test
    fun `merged edge carries classification set and all underlying edges`() {
        val cohesion = edge(symbolA, GraphNodeId.Structural(symbolB), DependencyClassification.COHESION)
        val internal = edge(symbolA2, GraphNodeId.Structural(symbolB), DependencyClassification.INTERNAL, DependencyKind.TYPE_REFERENCE)
        val graph = graphOf(listOf(cohesion, internal))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(packageA), edge.source)
        assertEquals(GraphNodeId.Structural(moduleB), edge.target)
        assertEquals(setOf(DependencyClassification.COHESION, DependencyClassification.INTERNAL), edge.classifications)
        assertEquals(setOf(cohesion, internal), edge.underlying)
    }

    @Test
    fun `external endpoint is never lifted and external nodes survive projection`() {
        val toExternal = edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL)
        val graph = graphOf(listOf(toExternal), listOf(ExternalNode(external)))

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA))

        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(packageA), edge.source)
        assertEquals(GraphNodeId.External(external), edge.target)
        assertEquals(listOf(ExternalNode(external)), visible.externalNodes)
    }

    @Test
    fun `external nodes and edges are hidden when showExternal is off`() {
        val toExternal = edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL)
        val toInternal = edge(symbolA, GraphNodeId.Structural(symbolB))
        val graph = graphOf(listOf(toExternal, toInternal), listOf(ExternalNode(external)))

        val visible = projectVisibleGraph(graph, expanded = emptySet(), showExternal = false)

        assertTrue(visible.externalNodes.isEmpty())
        assertTrue(visible.edges.none { it.target is GraphNodeId.External })
        val edge = visible.edges.single()
        assertEquals(GraphNodeId.Structural(moduleA), edge.source)
        assertEquals(GraphNodeId.Structural(moduleB), edge.target)
    }

    @Test
    fun `changed state passes through on visible nodes`() {
        val changed = StructuralNode(symbolA, "a", ChangeKind.MODIFIED)
        val graph = DependencyGraph(
            structuralNodes = nodes(moduleA, packageA, fileA) + changed,
            edges = emptyList(),
        )

        val visible = projectVisibleGraph(graph, expanded = setOf(moduleA, packageA, fileA))

        assertEquals(ChangeKind.MODIFIED, visible.structuralNodes.single { it.node.path == symbolA }.node.change)
    }

    @Test
    fun `approval projection marks a changed leaf as full when approved`() {
        val symbols = changedSymbols(symbolA)
        val approvals = ApprovalState().toggle(symbolA, symbols)
        val graph = graphWithChanged(symbolA)

        val visible = projectVisibleGraph(
            graph,
            expanded = setOf(moduleA, packageA, fileA),
            changedSymbols = symbols,
            approvals = approvals,
        )

        val node = visible.structuralNodes.single { it.node.path == symbolA }
        assertEquals(ApprovalMarker.LEAF, node.approvalMarker)
        assertEquals(ApprovalCompleteness.FULL, node.approval?.completeness)
    }

    @Test
    fun `approval projection marks containers as full partial or none`() {
        val symbols = changedSymbols(symbolA, symbolA2)
        val graph = graphWithChanged(symbolA, symbolA2)

        val none = projectVisibleGraph(graph, expanded = emptySet(), changedSymbols = symbols, approvals = ApprovalState())
            .structuralNodes.single { it.node.path == moduleA }
        assertEquals(ApprovalMarker.CONTAINER, none.approvalMarker)
        assertEquals(ApprovalCompleteness.NONE, none.approval?.completeness)

        val partialApprovals = ApprovalState().toggle(symbolA, symbols)
        val partial = projectVisibleGraph(graph, expanded = emptySet(), changedSymbols = symbols, approvals = partialApprovals)
            .structuralNodes.single { it.node.path == moduleA }
        assertEquals(ApprovalCompleteness.PARTIAL, partial.approval?.completeness)

        val fullApprovals = partialApprovals.toggle(moduleA, symbols)
        val full = projectVisibleGraph(graph, expanded = emptySet(), changedSymbols = symbols, approvals = fullApprovals)
            .structuralNodes.single { it.node.path == moduleA }
        assertEquals(ApprovalCompleteness.FULL, full.approval?.completeness)
    }

    @Test
    fun `approval projection leaves containers with no changed leaves unmarked`() {
        val graph = DependencyGraph(structuralNodes = nodes(moduleA, packageA), edges = emptyList())

        val visible = projectVisibleGraph(graph, expanded = emptySet(), changedSymbols = changedSymbols(), approvals = ApprovalState())

        val node = visible.structuralNodes.single { it.node.path == moduleA }
        assertNull(node.approval)
        assertNull(node.approvalMarker)
    }

    @Test
    fun `approval projection never marks external nodes`() {
        val toExternal = edge(symbolA, GraphNodeId.External(external), DependencyClassification.EXTERNAL)
        val symbols = changedSymbols(symbolA)
        val graph = graphWithChanged(symbolA).copy(
            externalNodes = listOf(ExternalNode(external)),
            edges = listOf(toExternal),
        )

        val visible = projectVisibleGraph(graph, expanded = emptySet(), changedSymbols = symbols, approvals = ApprovalState())

        assertEquals(listOf(ExternalNode(external)), visible.externalNodes)
        assertTrue(visible.structuralNodes.none { it.node.displayName == external.displayName })
    }

    private fun graphWithChanged(vararg changed: NodePath): DependencyGraph {
        val paths = changed.flatMap { ancestorsAndSelf(it) }.distinct()
        return DependencyGraph(
            structuralNodes = paths.map { path ->
                StructuralNode(path, path.displayName(), change = if (path in changed) ChangeKind.MODIFIED else null)
            },
            edges = emptyList(),
        )
    }

    private fun changedSymbols(vararg paths: NodePath) =
        ChangedSymbols(
            changed = paths.map { path ->
                ChangedDeclaration(
                    identity = path,
                    kind = ChangeKind.MODIFIED,
                    filePath = path.filePath(),
                    displayName = path.displayName(),
                    currentText = "${path.displayName()} after",
                    baselineText = "${path.displayName()} before",
                )
            },
            contextual = emptyList(),
            movedFiles = emptyList(),
            diagnostics = emptyList(),
        )

    private fun NodePath.filePath(): String =
        segments.filterIsInstance<NodeSegment.File>().firstOrNull()?.moduleRelativePath ?: "unknown.kt"
}

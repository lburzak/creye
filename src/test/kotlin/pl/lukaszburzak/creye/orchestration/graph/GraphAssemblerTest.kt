package pl.lukaszburzak.creye.orchestration.graph

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyEdge
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.ExternalSymbolId
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.CallableDiscriminator
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.orchestration.resolution.ResolvedDependencies

class GraphAssemblerTest {

    private val source = path("app", "demo", "src/Source.kt", "Source.kt", "Source", symbol("changed"))
    private val target = path("lib", "demo", "src/Target.kt", "Target.kt", "Target", symbol("target"))
    private val sibling = path("app", "demo", "src/Sibling.kt", "Sibling.kt", "Sibling", symbol("other"))
    private val external = ExternalSymbolId("kotlin/String", "String")

    @Test
    fun `assembles changed internal neighborhood with full ancestors and no siblings`() {
        val edge = DependencyEdge(
            source = source,
            target = GraphNodeId.Structural(target),
            classification = DependencyClassification.INTERNAL,
            kind = DependencyKind.CALL,
        )

        val graph = GraphAssembler.assemble(
            symbols = changedSymbols(source),
            resolved = ResolvedDependencies(listOf(edge, edge), emptyList()),
            upstreamDiagnostics = emptyList(),
        )

        val paths = graph.structuralNodes.map { it.path }.toSet()
        assertTrue(paths.containsAll(source.ancestors()))
        assertTrue(paths.containsAll(target.ancestors()))
        assertFalse(paths.contains(sibling))
        assertEquals(listOf(edge), graph.edges)
        assertEquals(ChangeKind.MODIFIED, graph.structuralNodes.single { it.path == source }.change)
        assertNull(graph.structuralNodes.single { it.path == target }.change)
    }

    @Test
    fun `materializes external nodes outside structural containment`() {
        val edge = DependencyEdge(
            source = source,
            target = GraphNodeId.External(external),
            classification = DependencyClassification.EXTERNAL,
            kind = DependencyKind.TYPE_REFERENCE,
        )

        val graph = GraphAssembler.assemble(
            symbols = changedSymbols(source),
            resolved = ResolvedDependencies(listOf(edge), emptyList()),
            upstreamDiagnostics = emptyList(),
        )

        assertEquals(listOf(external), graph.externalNodes.map { it.id })
        assertFalse(graph.structuralNodes.any { it.displayName == external.displayName })
    }

    @Test
    fun `preserves graph node and edge diagnostic attachments when targets exist`() {
        val edge = DependencyEdge(source, GraphNodeId.External(external), DependencyClassification.EXTERNAL, DependencyKind.CALL)
        val diagnostics = listOf(
            Diagnostic(
                DiagnosticSource.DEPENDENCY_RESOLUTION,
                Severity.WARNING,
                "graph level",
                attachment = DiagnosticAttachment.Graph,
            ),
            Diagnostic(
                DiagnosticSource.DEPENDENCY_RESOLUTION,
                Severity.WARNING,
                "node level",
                attachment = DiagnosticAttachment.Node(GraphNodeId.Structural(source)),
            ),
            Diagnostic(
                DiagnosticSource.DEPENDENCY_RESOLUTION,
                Severity.WARNING,
                "edge level",
                attachment = DiagnosticAttachment.Edge(edge),
            ),
        )

        val graph = GraphAssembler.assemble(
            symbols = changedSymbols(source),
            resolved = ResolvedDependencies(listOf(edge), diagnostics),
            upstreamDiagnostics = emptyList(),
        )

        assertEquals(diagnostics.map { it.attachment }, graph.diagnostics.map { it.attachment })
    }

    @Test
    fun `invalid diagnostic attachment becomes graph construction diagnostic`() {
        val diagnostic = Diagnostic(
            DiagnosticSource.DEPENDENCY_RESOLUTION,
            Severity.WARNING,
            "missing",
            attachment = DiagnosticAttachment.Node(GraphNodeId.Structural(target)),
        )

        val graph = GraphAssembler.assemble(
            symbols = changedSymbols(source),
            resolved = ResolvedDependencies(emptyList(), listOf(diagnostic)),
            upstreamDiagnostics = emptyList(),
        )

        assertEquals(DiagnosticSource.GRAPH_CONSTRUCTION, graph.diagnostics.single().source)
        assertEquals(DiagnosticAttachment.Graph, graph.diagnostics.single().attachment)
    }

    private fun changedSymbols(path: NodePath) = ChangedSymbols(
        changed = listOf(ChangedDeclaration(path, ChangeKind.MODIFIED, "src/Source.kt", "changed")),
        contextual = emptyList(),
        movedFiles = emptyList(),
        diagnostics = emptyList(),
    )

    private fun path(
        module: String,
        pkg: String,
        moduleRelativePath: String,
        fileName: String,
        className: String,
        symbol: NodeSegment.Symbol,
    ) = NodePath(
        listOf(
            NodeSegment.Module(module),
            NodeSegment.Package(pkg),
            NodeSegment.File(fileName, moduleRelativePath),
            NodeSegment.Class(className),
            symbol,
        ),
    )

    private fun symbol(name: String) = NodeSegment.Symbol(
        name,
        CallableDiscriminator(arity = 0, parameterTypeTexts = emptyList(), receiverTypeText = null),
    )

    private fun NodePath.ancestors(): Set<NodePath> =
        segments.indices.mapTo(mutableSetOf()) { index -> NodePath(segments.take(index + 1)) }
}

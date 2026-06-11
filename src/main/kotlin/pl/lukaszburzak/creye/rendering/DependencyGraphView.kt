package pl.lukaszburzak.creye.rendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.canvas.GraphCanvas
import pl.lukaszburzak.creye.rendering.layout.layoutVisibleGraph
import pl.lukaszburzak.creye.rendering.projection.projectVisibleGraph

/**
 * State owner for one rendered graph (ADR-009): collapse state and selection live here
 * at the render surface; the visible projection (ADR-008) and layout are derived by
 * recomposition, never by re-running analysis.
 */
@Composable
fun DependencyGraphView(graph: DependencyGraph, modifier: Modifier = Modifier) {
    var collapsed by remember(graph) { mutableStateOf(emptySet<NodePath>()) }
    var selected by remember(graph) { mutableStateOf<GraphNodeId?>(null) }

    val visible = remember(graph, collapsed) { projectVisibleGraph(graph, collapsed) }
    val layout = remember(visible) { layoutVisibleGraph(visible) }

    // Only nodes with descendants can collapse; leaves have nothing to hide.
    val collapsible = remember(graph) {
        graph.structuralNodes
            .map { it.path }
            .flatMapTo(mutableSetOf()) { path ->
                (1 until path.segments.size).map { NodePath(path.segments.subList(0, it)) }
            }
    }
    val diagnosticNodes = remember(graph) {
        graph.diagnostics
            .mapNotNullTo(mutableSetOf()) { (it.attachment as? DiagnosticAttachment.Node)?.id }
    }

    GraphCanvas(
        visible = visible,
        layout = layout,
        selected = selected,
        diagnosticNodes = diagnosticNodes,
        onSelect = { selected = it },
        onToggleCollapse = { id ->
            if (id.path in collapsible) {
                collapsed = if (id.path in collapsed) collapsed - id.path else collapsed + id.path
            }
        },
        modifier = modifier,
    )
}

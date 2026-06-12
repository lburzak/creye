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
import pl.lukaszburzak.creye.rendering.layout.LayoutPoint
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
    var movedCenters by remember(graph) { mutableStateOf(emptyMap<GraphNodeId, LayoutPoint>()) }

    val visible = remember(graph, collapsed) { projectVisibleGraph(graph, collapsed) }
    val baseLayout = remember(visible) { layoutVisibleGraph(visible, movedCenters) }
    val layout = remember(baseLayout, movedCenters) { baseLayout.withCenters(movedCenters) }

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
        onExpand = { id -> collapsed = collapsed - id.path },
        onCollapseSelfAndSiblings = { id -> collapsed = collapsed + collapseSelfAndSiblings(graph, id.path) },
        onMoveNode = { id, center -> movedCenters = movedCenters + (id to center) },
        modifier = modifier,
    )
}

internal fun collapseSelfAndSiblings(graph: DependencyGraph, path: NodePath): Set<NodePath> {
    val parent = path.parent()
    return graph.structuralNodes
        .map { it.path }
        .filterTo(mutableSetOf()) { it.parent() == parent }
}

private fun NodePath.parent(): NodePath? =
    if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))

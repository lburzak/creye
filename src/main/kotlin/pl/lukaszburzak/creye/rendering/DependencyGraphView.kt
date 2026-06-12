package pl.lukaszburzak.creye.rendering

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.canvas.GraphCanvas
import pl.lukaszburzak.creye.rendering.layout.GraphLayout
import pl.lukaszburzak.creye.rendering.layout.LayoutPoint
import pl.lukaszburzak.creye.rendering.layout.layoutVisibleGraph
import pl.lukaszburzak.creye.rendering.layout.seedVisibleGraphLayout
import pl.lukaszburzak.creye.rendering.layout.validateLayout
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.projectVisibleGraph

private val layoutWarningColor = Color(0xFFFFB300)

/**
 * State owner for one rendered graph (ADR-009): collapse state and selection live here
 * at the render surface; the visible projection (ADR-008) is derived locally, and
 * force-directed layout is computed off the UI thread without re-running analysis.
 */
@Composable
fun DependencyGraphView(graph: DependencyGraph, modifier: Modifier = Modifier) {
    var collapsed by remember(graph) { mutableStateOf(emptySet<NodePath>()) }
    var selected by remember(graph) { mutableStateOf<GraphNodeId?>(null) }
    var movedCenters by remember(graph) { mutableStateOf(emptyMap<GraphNodeId, LayoutPoint>()) }
    var layoutState by remember(graph) { mutableStateOf<GraphLayoutState?>(null) }

    val visible = remember(graph, collapsed) { projectVisibleGraph(graph, collapsed) }
    val seedLayout = remember(visible) { seedVisibleGraphLayout(visible, movedCenters) }
    val activeLayoutState = layoutState?.takeIf { it.visible == visible }
        ?: GraphLayoutState(visible, seedLayout, isComputing = true, messages = validateLayout(visible, seedLayout).messages)
    val layout = remember(activeLayoutState.layout, movedCenters) { activeLayoutState.layout.withCenters(movedCenters) }

    val diagnosticNodes = remember(graph) {
        graph.diagnostics
            .mapNotNullTo(mutableSetOf()) { (it.attachment as? DiagnosticAttachment.Node)?.id }
    }

    LaunchedEffect(visible) {
        val previousLayout = layoutState?.layout
        val seeds = previousLayout?.centers().orEmpty() + movedCenters
        val immediateLayout = previousLayout
            ?.takeIf { validateLayout(visible, it).isValid }
            ?: seedVisibleGraphLayout(visible, seeds)
        layoutState = GraphLayoutState(
            visible = visible,
            layout = immediateLayout,
            isComputing = true,
            messages = validateLayout(visible, immediateLayout).messages,
        )

        val computed = runCatching {
            withContext(Dispatchers.Default) { layoutVisibleGraph(visible, seeds) }
        }
        val finalLayout = computed.getOrNull()
        val messages = when {
            computed.isFailure -> listOf("Layout failed: ${computed.exceptionOrNull()?.message ?: "unknown error"}")
            finalLayout == null -> listOf("Layout failed without a result.")
            else -> validateLayout(visible, finalLayout).messages
        }
        layoutState = GraphLayoutState(
            visible = visible,
            layout = if (messages.isEmpty() && finalLayout != null) finalLayout else immediateLayout,
            isComputing = false,
            messages = messages,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GraphCanvas(
            visible = visible,
            layout = layout,
            selected = selected,
            diagnosticNodes = diagnosticNodes,
            onSelect = { selected = it },
            onExpand = { id -> collapsed = collapsed - id.path },
            onCollapseSelfAndSiblings = { id -> collapsed = collapsed + collapseSelfAndSiblings(graph, id.path) },
            onMoveNode = { id, center -> movedCenters = movedCenters + (id to center) },
            modifier = Modifier.weight(1f),
        )
        if (activeLayoutState.messages.isNotEmpty()) {
            Text(
                activeLayoutState.messages.joinToString(separator = " "),
                color = layoutWarningColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun collapseSelfAndSiblings(graph: DependencyGraph, path: NodePath): Set<NodePath> {
    val parent = path.parent()
    return graph.structuralNodes
        .map { it.path }
        .filterTo(mutableSetOf()) { it.parent() == parent }
}

private fun NodePath.parent(): NodePath? =
    if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))

private data class GraphLayoutState(
    val visible: VisibleGraph,
    val layout: GraphLayout,
    val isComputing: Boolean,
    val messages: List<String>,
)

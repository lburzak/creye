package pl.lukaszburzak.creye.rendering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.canvas.GraphCanvas
import pl.lukaszburzak.creye.rendering.layout.GraphLayout
import pl.lukaszburzak.creye.rendering.layout.GraphSimulationViewport
import pl.lukaszburzak.creye.rendering.layout.LivingGraphSimulation
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
    var expanded by remember(graph) { mutableStateOf(emptySet<NodePath>()) }
    var selected by remember(graph) { mutableStateOf<GraphNodeId?>(null) }
    var layoutState by remember(graph) { mutableStateOf<GraphLayoutState?>(null) }
    var simulation by remember(graph) { mutableStateOf<LivingGraphSimulation?>(null) }
    var liveLayout by remember(graph) { mutableStateOf<GraphLayout?>(null) }
    var liveVisible by remember(graph) { mutableStateOf<VisibleGraph?>(null) }
    var viewport by remember(graph) { mutableStateOf<GraphSimulationViewport?>(null) }
    var paused by remember(graph) { mutableStateOf(false) }

    val visible = remember(graph, expanded) { projectVisibleGraph(graph, expanded) }
    val seedLayout = remember(visible, layoutState) {
        seedVisibleGraphLayout(visible, layoutState?.layout?.centers().orEmpty() + liveLayout?.centers().orEmpty())
    }
    val activeLayoutState = layoutState?.takeIf { it.visible == visible }
        ?: GraphLayoutState(visible, seedLayout, isComputing = true, messages = validateLayout(visible, seedLayout).messages)
    val layout = liveLayout?.takeIf { liveVisible == visible } ?: activeLayoutState.layout

    val diagnosticNodes = remember(graph) {
        graph.diagnostics
            .mapNotNullTo(mutableSetOf()) { (it.attachment as? DiagnosticAttachment.Node)?.id }
    }

    LaunchedEffect(visible) {
        val previousLayout = layoutState?.layout
        val seeds = previousLayout?.centers().orEmpty() + liveLayout?.centers().orEmpty()
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

    LaunchedEffect(visible, activeLayoutState.layout, activeLayoutState.isComputing) {
        val nextSimulation = LivingGraphSimulation(visible, activeLayoutState.layout.centers())
        viewport?.let { nextSimulation.setViewport(it.width, it.height) }
        simulation = nextSimulation
        liveVisible = visible
        liveLayout = nextSimulation.layout()
    }

    LaunchedEffect(visible) {
        var lastFrameNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaTime = if (lastFrameNanos == 0L) {
                1f
            } else {
                ((frameNanos - lastFrameNanos).toFloat() / FRAME_NANOS).coerceIn(0.25f, 2f)
            }
            lastFrameNanos = frameNanos
            val currentSimulation = simulation
            if (!paused && currentSimulation != null && liveVisible == visible) {
                currentSimulation.step(deltaTime)
                liveLayout = currentSimulation.layout()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GraphCanvas(
            visible = visible,
            layout = layout,
            selected = selected,
            diagnosticNodes = diagnosticNodes,
            onSelect = { selected = it },
            onExpand = { id -> expanded = expanded + id.path },
            onCollapseSelfAndSiblings = { id -> expanded = collapseSelfAndSiblings(expanded, id.path) },
            onNodeDragStart = { id -> simulation?.startDrag(id) },
            onNodeDragged = { id, center, delta ->
                simulation?.let {
                    it.drag(id, center, delta)
                    liveLayout = it.layout()
                }
            },
            onNodeDragEnd = { id -> simulation?.endDrag(id) },
            onViewportChanged = { width, height ->
                viewport = GraphSimulationViewport(width, height)
                simulation?.setViewport(width, height)
            },
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { paused = !paused }) {
                Text(if (paused) "Resume" else "Pause")
            }
        }
        if (activeLayoutState.messages.isNotEmpty()) {
            Text(
                activeLayoutState.messages.joinToString(separator = " "),
                color = layoutWarningColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun collapseSelfAndSiblings(expanded: Set<NodePath>, path: NodePath): Set<NodePath> {
    val parent = path.parent()
    return if (parent == null) {
        emptySet()
    } else {
        expanded.filterNotTo(mutableSetOf()) { it == parent || it.isDescendantOf(parent) }
    }
}

private fun NodePath.parent(): NodePath? =
    if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))

private fun NodePath.isDescendantOf(ancestor: NodePath): Boolean =
    segments.size > ancestor.segments.size &&
        segments.take(ancestor.segments.size) == ancestor.segments

private data class GraphLayoutState(
    val visible: VisibleGraph,
    val layout: GraphLayout,
    val isComputing: Boolean,
    val messages: List<String>,
)

private const val FRAME_NANOS = 16_666_667f

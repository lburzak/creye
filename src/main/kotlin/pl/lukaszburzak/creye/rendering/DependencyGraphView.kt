package pl.lukaszburzak.creye.rendering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.canvas.GraphCanvas
import pl.lukaszburzak.creye.rendering.layout.GraphLayout
import pl.lukaszburzak.creye.rendering.layout.GraphSimulationViewport
import pl.lukaszburzak.creye.rendering.layout.LivingGraphSimulation
import pl.lukaszburzak.creye.rendering.layout.LivingGraphSimulationConfig
import pl.lukaszburzak.creye.rendering.layout.layoutVisibleGraph
import pl.lukaszburzak.creye.rendering.layout.seedVisibleGraphLayout
import pl.lukaszburzak.creye.rendering.layout.validateLayout
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.projectVisibleGraph

private val layoutWarningColor = Color(0xFFFFB300)
private val controlTrackColor = Color(0xFF607D8B)
private val controlFillColor = Color(0xFF42A5F5)

/**
 * State owner for one rendered graph (ADR-009): collapse state and selection live here
 * at the render surface; the visible projection (ADR-008) is derived locally, and
 * force-directed layout is computed off the UI thread without re-running analysis.
 */
@Composable
fun DependencyGraphView(graph: DependencyGraph, modifier: Modifier = Modifier) {
    var expanded by remember(graph) { mutableStateOf(emptySet<NodePath>()) }
    var undoStack by remember(graph) { mutableStateOf(emptyList<Set<NodePath>>()) }
    var selected by remember(graph) { mutableStateOf<GraphNodeId?>(null) }

    // Every expansion change records the prior frontier so the canvas Undo can restore it.
    fun updateExpanded(next: Set<NodePath>) {
        if (next == expanded) return
        undoStack = undoStack + listOf(expanded)
        expanded = next
    }
    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        expanded = previous
    }

    val parentsWithChildren = remember(graph) {
        graph.structuralNodes.mapNotNullTo(mutableSetOf()) { it.path.parent() }
    }
    // Deepest containment rank among each node's strict descendants; drives "if applicable".
    val descendantMaxRank = remember(graph) {
        val paths = graph.structuralNodes.map { it.path }
        paths.associateWith { ancestor ->
            paths.filter { it.isDescendantOf(ancestor) }.maxOfOrNull { it.segments.last().rank() } ?: -1
        }
    }
    fun expandToRank(maxExclusive: Int) =
        updateExpanded(parentsWithChildren.filterTo(mutableSetOf()) { it.segments.last().rank() < maxExclusive })
    fun expandSubtreeToRank(path: NodePath, maxExclusive: Int) =
        updateExpanded(
            expanded + parentsWithChildren.filter {
                (it == path || it.isDescendantOf(path)) && it.segments.last().rank() < maxExclusive
            },
        )
    fun canExpandSubtreeToRank(path: NodePath, target: Int): Boolean =
        path.segments.last().rank() < target && (descendantMaxRank[path] ?: -1) >= target
    var layoutState by remember(graph) { mutableStateOf<GraphLayoutState?>(null) }
    var simulation by remember(graph) { mutableStateOf<LivingGraphSimulation?>(null) }
    var liveLayout by remember(graph) { mutableStateOf<GraphLayout?>(null) }
    var liveVisible by remember(graph) { mutableStateOf<VisibleGraph?>(null) }
    var viewport by remember(graph) { mutableStateOf<GraphSimulationViewport?>(null) }
    var paused by remember(graph) { mutableStateOf(false) }
    var centerGravity by remember(graph) { mutableStateOf(DEFAULT_CENTER_GRAVITY) }
    var nodeAttraction by remember(graph) { mutableStateOf(DEFAULT_NODE_ATTRACTION) }
    var nodeRepulsion by remember(graph) { mutableStateOf(DEFAULT_NODE_REPULSION) }

    val visible = remember(graph, expanded) { projectVisibleGraph(graph, expanded) }
    val simulationConfig = remember(centerGravity, nodeAttraction, nodeRepulsion) {
        LivingGraphSimulationConfig(
            nodeRepulsion = nodeRepulsion,
            springStrength = nodeAttraction,
            gravity = centerGravity,
        )
    }
    val seedLayout = remember(visible, layoutState) {
        val liveCenters = liveLayout?.centers().orEmpty()
        seedVisibleGraphLayout(
            visible = visible,
            seeds = layoutState?.layout?.centers().orEmpty() + liveCenters,
            normalize = liveCenters.isEmpty(),
            resolveOverlap = liveCenters.isEmpty(),
        )
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
        val liveCenters = liveLayout?.centers().orEmpty()
        val preserveLivePositions = liveCenters.isNotEmpty()
        val seeds = previousLayout?.centers().orEmpty() + liveCenters
        val immediateLayout = when {
            preserveLivePositions -> seedVisibleGraphLayout(
                visible = visible,
                seeds = seeds,
                normalize = false,
                resolveOverlap = false,
            )
            else -> previousLayout
                ?.takeIf { validateLayout(visible, it).isValid }
                ?: seedVisibleGraphLayout(visible, seeds)
        }
        layoutState = GraphLayoutState(
            visible = visible,
            layout = immediateLayout,
            isComputing = !preserveLivePositions,
            messages = validateLayout(visible, immediateLayout).messages,
        )
        if (preserveLivePositions) return@LaunchedEffect

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

    LaunchedEffect(visible, activeLayoutState.layout, activeLayoutState.isComputing, simulationConfig) {
        val seedCenters = liveLayout
            ?.takeIf { liveVisible == visible }
            ?.centers()
            ?: activeLayoutState.layout.centers()
        val nextSimulation = LivingGraphSimulation(visible, seedCenters, simulationConfig)
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
            onExpand = { id -> updateExpanded(expanded + id.path) },
            onCollapseSelfAndSiblings = { id -> updateExpanded(collapseSelfAndSiblings(expanded, id.path)) },
            onUndo = ::undo,
            canUndo = undoStack.isNotEmpty(),
            onExpandAll = { updateExpanded(parentsWithChildren) },
            onCollapseAll = { updateExpanded(emptySet()) },
            onExpandToClasses = { expandToRank(CLASS_RANK) },
            onExpandToSymbols = { expandToRank(SYMBOL_RANK) },
            onExpandNodeToClasses = { id -> expandSubtreeToRank(id.path, CLASS_RANK) },
            onExpandNodeToSymbols = { id -> expandSubtreeToRank(id.path, SYMBOL_RANK) },
            canExpandNodeToClasses = { id -> canExpandSubtreeToRank(id.path, CLASS_RANK) },
            canExpandNodeToSymbols = { id -> canExpandSubtreeToRank(id.path, SYMBOL_RANK) },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SimulationSliderControl(
                label = "Gravity",
                value = centerGravity,
                onValueChange = { centerGravity = it },
                valueRange = 0f..MAX_CENTER_GRAVITY,
                valueLabel = percentLabel(centerGravity, MAX_CENTER_GRAVITY),
            )
            SimulationSliderControl(
                label = "Attraction",
                value = nodeAttraction,
                onValueChange = { nodeAttraction = it },
                valueRange = 0f..MAX_NODE_ATTRACTION,
                valueLabel = percentLabel(nodeAttraction, MAX_NODE_ATTRACTION),
            )
            SimulationSliderControl(
                label = "Repulsion",
                value = nodeRepulsion,
                onValueChange = { nodeRepulsion = it },
                valueRange = 0f..MAX_NODE_REPULSION,
                valueLabel = percentLabel(nodeRepulsion, MAX_NODE_REPULSION),
            )
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

@Composable
private fun SimulationSliderControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(72.dp))
        ForceSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.width(118.dp).height(24.dp),
        )
        Text(valueLabel, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun ForceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    fun valueAt(x: Float, width: Float): Float {
        if (width <= 0f) return value
        val fraction = (x / width).coerceIn(0f, 1f)
        return valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction
    }

    Canvas(
        modifier = modifier
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x, size.width.toFloat()))
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange(valueAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        val trackStart = Offset(0f, size.height / 2f)
        val trackEnd = Offset(size.width, size.height / 2f)
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val thumb = Offset(size.width * fraction, size.height / 2f)
        drawLine(controlTrackColor, trackStart, trackEnd, strokeWidth = 4f)
        drawLine(controlFillColor, trackStart, thumb, strokeWidth = 4f)
        drawCircle(controlFillColor, radius = 7f, center = thumb)
    }
}

private fun percentLabel(value: Float, maxValue: Float): String =
    "${((value / maxValue) * 100).toInt()}%"

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

/** Containment depth used to expand the frontier down to a target structural level. */
private fun NodeSegment.rank(): Int = when (this) {
    is NodeSegment.Module -> 0
    is NodeSegment.Package -> 1
    is NodeSegment.File -> 2
    is NodeSegment.Class -> 3
    is NodeSegment.Symbol -> 4
}

private fun NodePath.isDescendantOf(ancestor: NodePath): Boolean =
    segments.size > ancestor.segments.size &&
        segments.take(ancestor.segments.size) == ancestor.segments

private data class GraphLayoutState(
    val visible: VisibleGraph,
    val layout: GraphLayout,
    val isComputing: Boolean,
    val messages: List<String>,
)

private const val CLASS_RANK = 3
private const val SYMBOL_RANK = 4
private const val FRAME_NANOS = 16_666_667f
private const val DEFAULT_CENTER_GRAVITY = 0.010f
private const val MAX_CENTER_GRAVITY = 0.030f
private const val DEFAULT_NODE_ATTRACTION = 0.028f
private const val MAX_NODE_ATTRACTION = 0.080f
private const val DEFAULT_NODE_REPULSION = 3_200f
private const val MAX_NODE_REPULSION = 8_000f

package pl.lukaszburzak.creye.rendering.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.component.Text
import pl.lukaszburzak.creye.domain.approval.ApprovalCompleteness
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.rendering.GraphViewState
import pl.lukaszburzak.creye.rendering.layout.GraphLayout
import pl.lukaszburzak.creye.rendering.layout.LayoutMetrics
import pl.lukaszburzak.creye.rendering.layout.LayoutPoint
import pl.lukaszburzak.creye.rendering.layout.LayoutRect
import pl.lukaszburzak.creye.rendering.projection.VisibleEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import pl.lukaszburzak.creye.rendering.projection.VisibleNode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Render palette: fixed values legible on both light and dark IDE themes. */
private object Palette {
    val nodeFill = Color(0xFF5B6670)
    val externalFill = Color(0xFF7E57C2)
    val added = Color(0xFF4CAF50)
    val modified = Color(0xFF1976D2)
    val deleted = Color(0xFFE53935)
    val selection = Color(0xFF2196F3)
    val internalEdge = Color(0xFF42A5F5)
    val externalEdge = Color(0xFFAB47BC)
    val cohesionEdge = Color(0xFFFF7043)
    val multiClassEdge = Color(0xFF90A4AE)
    val hierarchyEdge = Color(0xFFB0BEC5)
    val badgeFill = Color(0xFF455A64)
    val diagnosticMarker = Color(0xFFFFC107)
    val approval = Color(0xFF2E7D32)
    val caretHalo = Color(0xFFFFD54F)
    val caretHaloFill = Color(0x33FFD54F)
    val label = Color(0xFFECEFF1)
    val labelShadow = Color(0xCC263238)
    val collapsedRing = Color(0xFFECEFF1)
    val moduleLabel = Color(0xFFB0BEC5)
    val percentLabel = Color(0xFFFFFFFF)
}

private fun edgeColor(edge: VisibleEdge): Color = when (edge.classifications.singleOrNull()) {
    DependencyClassification.INTERNAL -> Palette.internalEdge
    DependencyClassification.EXTERNAL -> Palette.externalEdge
    DependencyClassification.COHESION -> Palette.cohesionEdge
    null -> Palette.multiClassEdge
}

/**
 * Immediate-mode graph surface (ADR-009): draws the projected visible graph and its
 * layout, handles hit-testing and gestures as state transitions reported via callbacks.
 */
@Composable
fun GraphCanvas(
    visible: VisibleGraph,
    layout: GraphLayout,
    selected: GraphNodeId?,
    emphasized: GraphNodeId.Structural?,
    viewState: GraphViewState,
    diagnosticNodes: Set<GraphNodeId>,
    showApprovalPercent: Boolean,
    onSelect: (GraphNodeId?) -> Unit,
    onShowDiff: (GraphNodeId.Structural) -> Unit,
    onToggleApproval: (GraphNodeId.Structural) -> Unit,
    onExpand: (GraphNodeId.Structural) -> Unit,
    onCollapseSelfAndSiblings: (GraphNodeId.Structural) -> Unit,
    onNodeDragStart: (GraphNodeId) -> Unit,
    onNodeDragged: (GraphNodeId, LayoutPoint, LayoutPoint) -> Unit,
    onNodeDragEnd: (GraphNodeId?) -> Unit,
    onViewportChanged: (Float, Float) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onExpandToClasses: () -> Unit,
    onExpandToSymbols: () -> Unit,
    onExpandNodeToClasses: (GraphNodeId.Structural) -> Unit,
    onExpandNodeToSymbols: (GraphNodeId.Structural) -> Unit,
    canExpandNodeToClasses: (GraphNodeId.Structural) -> Boolean,
    canExpandNodeToSymbols: (GraphNodeId.Structural) -> Boolean,
    modifier: Modifier = Modifier,
) {
    // Seeded from the controller-owned holder so pan/zoom survive a tab change (REQUIREMENTS).
    var pan by remember { mutableStateOf(viewState.pan) }
    var zoom by remember { mutableStateOf(viewState.zoom) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var dragTarget by remember { mutableStateOf<GraphNodeId?>(null) }
    var menuRequest by remember { mutableStateOf<MenuRequest?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelMeasurements = remember(visible, textMeasurer) {
        buildMap {
            for (node in visible.structuralNodes) {
                put(GraphNodeId.Structural(node.node.path), textMeasurer.measure(node.node.displayName, labelTextStyle))
            }
            for (external in visible.externalNodes) {
                put(GraphNodeId.External(external.id), textMeasurer.measure(external.id.displayName, labelTextStyle))
            }
        }
    }
    val currentVisible by rememberUpdatedState(visible)
    val currentLayout by rememberUpdatedState(layout)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnShowDiff by rememberUpdatedState(onShowDiff)
    val currentOnToggleApproval by rememberUpdatedState(onToggleApproval)
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnCollapseSelfAndSiblings by rememberUpdatedState(onCollapseSelfAndSiblings)
    val currentOnNodeDragStart by rememberUpdatedState(onNodeDragStart)
    val currentOnNodeDragged by rememberUpdatedState(onNodeDragged)
    val currentOnNodeDragEnd by rememberUpdatedState(onNodeDragEnd)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentCanUndo by rememberUpdatedState(canUndo)
    val currentOnExpandAll by rememberUpdatedState(onExpandAll)
    val currentOnCollapseAll by rememberUpdatedState(onCollapseAll)
    val currentOnExpandToClasses by rememberUpdatedState(onExpandToClasses)
    val currentOnExpandToSymbols by rememberUpdatedState(onExpandToSymbols)
    val currentOnExpandNodeToClasses by rememberUpdatedState(onExpandNodeToClasses)
    val currentOnExpandNodeToSymbols by rememberUpdatedState(onExpandNodeToSymbols)
    val currentCanExpandNodeToClasses by rememberUpdatedState(canExpandNodeToClasses)
    val currentCanExpandNodeToSymbols by rememberUpdatedState(canExpandNodeToSymbols)

    fun graphPosition(position: Offset): Offset =
        (position - pan).scaledBy(1f / zoom)

    fun hit(position: Offset, graphLayout: GraphLayout = currentLayout): GraphNodeId? {
        val p = graphPosition(position)
        return graphLayout.bounds.entries
            .mapNotNull { (id, rect) ->
                val center = rect.center.toOffset()
                val distance = hypot((p.x - center.x).toDouble(), (p.y - center.y).toDouble()).toFloat()
                if (distance <= LayoutMetrics.NODE_RADIUS) id to distance else null
            }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    fun expandableHit(position: Offset): GraphNodeId.Structural? {
        val id = hit(position) as? GraphNodeId.Structural ?: return null
        val visibleNode = currentVisible.structuralNodes.firstOrNull { it.node.path == id.path }
        return id.takeIf { visibleNode?.isCollapsed == true }
    }

    fun GraphNodeId.Structural.hasDiff(): Boolean {
        val visibleNode = currentVisible.structuralNodes.firstOrNull { it.node.path == path } ?: return false
        return visibleNode.node.change != null || visibleNode.hasDescendantChange
    }

    fun centerOn(id: GraphNodeId) {
        val center = currentLayout.centerOf(id) ?: return
        if (canvasSize == Size.Zero) return
        pan = Offset(canvasSize.width / 2f - center.x * zoom, canvasSize.height / 2f - center.y * zoom)
        viewState.pan = pan
    }

    fun goTo(id: GraphNodeId) {
        currentOnSelect(id)
        centerOn(id)
    }

    fun nodeMenuEntries(id: GraphNodeId.Structural): List<MenuEntry> = buildList {
        val visibleNode = currentVisible.structuralNodes.firstOrNull { it.node.path == id.path }
        add(MenuEntry.Item("Show diff with descendants") { currentOnShowDiff(id) })
        visibleNode?.approval?.let { approval ->
            add(MenuEntry.Item(if (approval.isFullyApproved) "Approved ✓" else "Approved") { currentOnToggleApproval(id) })
        }
        add(MenuEntry.Separator)
        val beforeGoTo = size
        id.path.nearestAncestorOfType<NodeSegment.Class>()?.let {
            add(MenuEntry.Item("Go to nearest class") { goTo(GraphNodeId.Structural(it)) })
        }
        id.path.nearestAncestorOfType<NodeSegment.Package>()?.let {
            add(MenuEntry.Item("Go to nearest package") { goTo(GraphNodeId.Structural(it)) })
        }
        id.path.nearestAncestorOfType<NodeSegment.Module>()?.let {
            add(MenuEntry.Item("Go to nearest module") { goTo(GraphNodeId.Structural(it)) })
        }
        if (size > beforeGoTo) add(MenuEntry.Separator)
        if (visibleNode?.isCollapsed == true) {
            add(MenuEntry.Item("Expand") { currentOnExpand(id) })
        }
        add(MenuEntry.Item("Collapse") { currentOnCollapseSelfAndSiblings(id) })
        if (currentCanExpandNodeToClasses(id)) {
            add(MenuEntry.Item("Expand down to Classes") { currentOnExpandNodeToClasses(id) })
        }
        if (currentCanExpandNodeToSymbols(id)) {
            add(MenuEntry.Item("Expand down to Symbols") { currentOnExpandNodeToSymbols(id) })
        }
    }

    fun canvasMenuEntries(): List<MenuEntry> = buildList {
        if (currentCanUndo) {
            add(MenuEntry.Item("Undo") { currentOnUndo() })
            add(MenuEntry.Separator)
        }
        add(MenuEntry.Item("Expand All") { currentOnExpandAll() })
        add(MenuEntry.Item("Collapse All") { currentOnCollapseAll() })
        add(MenuEntry.Separator)
        add(MenuEntry.Item("Expand down to Classes") { currentOnExpandToClasses() })
        add(MenuEntry.Item("Expand down to Symbols") { currentOnExpandToSymbols() })
    }

    Box(modifier = modifier.fillMaxSize()) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                currentOnViewportChanged(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { position -> hit(position)?.let(currentOnSelect) },
                    onTap = { position ->
                        val id = hit(position)
                        currentOnSelect(id)
                        // REQUIREMENTS (Node): a click opens the Combined Diff for the node.
                        // Gated to nodes with a diff so clicking changeless nodes is a no-op.
                        (id as? GraphNodeId.Structural)?.takeIf { it.hasDiff() }?.let(currentOnShowDiff)
                    },
                    onDoubleTap = { position ->
                        expandableHit(position)?.let(currentOnExpand)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragTarget = hit(it)
                        dragTarget?.let(currentOnNodeDragStart)
                    },
                    onDragCancel = {
                        currentOnNodeDragEnd(dragTarget)
                        dragTarget = null
                    },
                    onDragEnd = {
                        currentOnNodeDragEnd(dragTarget)
                        dragTarget = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val target = dragTarget
                        if (target == null) {
                            pan += dragAmount
                            viewState.pan = pan
                        } else {
                            currentLayout.centerOf(target)?.let { center ->
                                val graphDelta = LayoutPoint(dragAmount.x / zoom, dragAmount.y / zoom)
                                currentOnNodeDragged(
                                    target,
                                    LayoutPoint(
                                        center.x + graphDelta.x,
                                        center.y + graphDelta.y,
                                    ),
                                    graphDelta,
                                )
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                                val position = event.changes.firstOrNull()?.position ?: continue
                                val entries = when (val target = hit(position)) {
                                    is GraphNodeId.Structural -> nodeMenuEntries(target)
                                    else -> canvasMenuEntries()
                                }
                                menuRequest = MenuRequest(position, entries)
                                event.changes.forEach { it.consume() }
                            }
                            event.type == PointerEventType.Scroll -> {
                                val change = event.changes.firstOrNull() ?: continue
                                val scrollY = change.scrollDelta.y
                                if (scrollY == 0f) continue
                                val oldZoom = zoom
                                val graphPoint = graphPosition(change.position)
                                val factor = if (scrollY < 0f) ZOOM_STEP else 1f / ZOOM_STEP
                                val newZoom = (oldZoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                zoom = newZoom
                                pan = change.position - graphPoint.toOffsetAt(newZoom)
                                viewState.zoom = zoom
                                viewState.pan = pan
                                change.consume()
                            }
                        }
                    }
                }
            },
    ) {
        val viewportIssue = if (pan == Offset.Zero && zoom == 1f) layout.viewportIssue(size.width, size.height) else null
        if (viewportIssue != null) {
            drawCanvasMessage(textMeasurer, viewportIssue)
        } else {
            translate(pan.x, pan.y) {
                scale(zoom, zoom, Offset.Zero) {
                    drawHierarchyEdges(visible, layout)
                    drawEdges(visible, layout)
                    drawNodes(visible, layout, selected, emphasized, diagnosticNodes, showApprovalPercent, textMeasurer, labelMeasurements, zoom)
                }
            }
        }
    }
        menuRequest?.let { request ->
            ContextMenu(request = request, onDismiss = { menuRequest = null })
        }
    }
}

/** Right-click menu entry; [Separator] groups related actions visually. */
private sealed interface MenuEntry {
    data class Item(val label: String, val onClick: () -> Unit) : MenuEntry
    data object Separator : MenuEntry
}

private data class MenuRequest(val position: Offset, val entries: List<MenuEntry>)

/** Deepest ancestor-or-self path whose last segment is of type [T], or null if none. */
private inline fun <reified T : NodeSegment> NodePath.nearestAncestorOfType(): NodePath? {
    for (i in segments.indices.reversed()) {
        if (segments[i] is T) return NodePath(segments.subList(0, i + 1))
    }
    return null
}

/** Self-contained popup so context menus do not depend on an ambient menu representation. */
@Composable
private fun ContextMenu(request: MenuRequest, onDismiss: () -> Unit) {
    Popup(
        offset = IntOffset(request.position.x.roundToInt(), request.position.y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .background(MenuPalette.background, RoundedCornerShape(4.dp))
                .border(1.dp, MenuPalette.border, RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp)
                .width(IntrinsicSize.Max),
        ) {
            for (entry in request.entries) {
                when (entry) {
                    is MenuEntry.Separator -> Box(
                        Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 4.dp).background(MenuPalette.border),
                    )
                    is MenuEntry.Item -> MenuItemRow(entry.label) {
                        entry.onClick()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Text(
        text = label,
        color = MenuPalette.label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .hoverable(interactionSource)
            .background(if (hovered) MenuPalette.hover else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

private object MenuPalette {
    val background = Color(0xFF3C3F41)
    val border = Color(0xFF555759)
    val hover = Color(0xFF2F65CA)
    val label = Color(0xFFECEFF1)
}

private fun DrawScope.drawHierarchyEdges(visible: VisibleGraph, layout: GraphLayout) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
    for (edge in visible.hierarchyEdges) {
        val parent = layout.bounds[edge.parent] ?: continue
        val child = layout.bounds[edge.child] ?: continue
        val from = parent.center.toOffset()
        val to = child.center.toOffset()
        if (from == to) continue
        drawLine(
            Palette.hierarchyEdge,
            clipToCircle(from, to, LayoutMetrics.NODE_RADIUS),
            clipToCircle(to, from, LayoutMetrics.NODE_RADIUS),
            strokeWidth = 1.1f,
            pathEffect = dash,
        )
    }
}

private fun DrawScope.drawEdges(visible: VisibleGraph, layout: GraphLayout) {
    for (edge in visible.edges) {
        val source = layout.bounds[edge.source] ?: continue
        val target = layout.bounds[edge.target] ?: continue
        val from = source.center.toOffset()
        val to = target.center.toOffset()
        if (from == to) continue
        val start = clipToCircle(from, to, LayoutMetrics.NODE_RADIUS)
        val tip = clipToCircle(to, from, LayoutMetrics.NODE_RADIUS)
        val color = edgeColor(edge)
        drawLine(color, start, tip, strokeWidth = 1.6f)
        drawArrowhead(color, start, tip)
        if (edge.classifications.size > 1) {
            // Multi-class marker: small hollow diamond at the midpoint.
            val mid = Offset((start.x + tip.x) / 2, (start.y + tip.y) / 2)
            val d = 5f
            drawPath(
                Path().apply {
                    moveTo(mid.x, mid.y - d); lineTo(mid.x + d, mid.y)
                    lineTo(mid.x, mid.y + d); lineTo(mid.x - d, mid.y); close()
                },
                color,
                style = Stroke(width = 1.5f),
            )
        }
    }
}

/** Point on the boundary of the circle centered at [from], in the direction of [to]. */
private fun clipToCircle(from: Offset, to: Offset, radius: Float): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (distance == 0f) return from
    return Offset(from.x + dx / distance * radius, from.y + dy / distance * radius)
}

private fun DrawScope.drawArrowhead(color: Color, from: Offset, tip: Offset) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val length = 9f
    val spread = 0.5f
    val left = Offset(tip.x - length * cos(angle - spread), tip.y - length * sin(angle - spread))
    val right = Offset(tip.x - length * cos(angle + spread), tip.y - length * sin(angle + spread))
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, color)
}

private fun DrawScope.drawNodes(
    visible: VisibleGraph,
    layout: GraphLayout,
    selected: GraphNodeId?,
    emphasized: GraphNodeId.Structural?,
    diagnosticNodes: Set<GraphNodeId>,
    showApprovalPercent: Boolean,
    textMeasurer: TextMeasurer,
    labelMeasurements: Map<GraphNodeId, TextLayoutResult>,
    zoom: Float,
) {
    val zoomCompensation = zoomCompensation(zoom)
    for (visibleNode in visible.structuralNodes.sortedBy { it.node.path.segments.size }) {
        val id = GraphNodeId.Structural(visibleNode.node.path)
        val rect = layout.bounds[id] ?: continue
        val fillColor = when (visibleNode.node.change) {
            ChangeKind.ADDED -> Palette.added
            ChangeKind.MODIFIED -> Palette.modified
            ChangeKind.DELETED -> Palette.deleted
            null -> if (visibleNode.hasDescendantChange) Palette.modified else Palette.nodeFill
        }
        val center = rect.center.toOffset()
        val lastSegment = visibleNode.node.path.segments.last()
        if (emphasized == id) {
            drawCaretHalo(center, zoom)
        }
        drawNodeShape(lastSegment, center, fillColor)
        drawApprovalRing(visibleNode, center, zoom)
        labelMeasurements[id]?.let { drawLabel(it, rect, Palette.label) }
        // A source set's own label is just `main`/`test`; show its module too (REQUIREMENTS: Node).
        if (lastSegment is NodeSegment.SourceSet) {
            visibleNode.node.path.segments.filterIsInstance<NodeSegment.Module>().firstOrNull()?.let { module ->
                drawModuleLabel(textMeasurer, rect, module.id)
            }
        }
        if (showApprovalPercent) {
            drawApprovalPercent(textMeasurer, visibleNode, center)
        }
        if (selected == id) {
            drawNodeShapeStroke(
                lastSegment,
                center,
                Palette.selection,
                radiusOffset = SELECTION_RING_OFFSET * zoomCompensation,
                strokeWidth = 2.5f * zoomCompensation,
            )
        }
        if (visibleNode.isCollapsed) {
            drawNodeShapeStroke(lastSegment, center, Palette.collapsedRing, radiusOffset = -4f, strokeWidth = 1.4f)
        }
        if (visibleNode.internalizedEdges.isNotEmpty()) {
            drawBadge(textMeasurer, rect, visibleNode.internalizedEdges.size)
        }
        if (id in diagnosticNodes) {
            drawCircle(Palette.diagnosticMarker, radius = 4f, center = Offset(center.x - 10f, center.y - 10f))
        }
    }

    for (external in visible.externalNodes) {
        val id = GraphNodeId.External(external.id)
        val rect = layout.bounds[id] ?: continue
        val center = rect.center.toOffset()
        drawCircle(Palette.externalFill, radius = LayoutMetrics.NODE_RADIUS, center = center)
        labelMeasurements[id]?.let { drawLabel(it, rect, Palette.label) }
        if (selected == id) {
            drawCircle(Palette.selection, radius = LayoutMetrics.NODE_RADIUS + 3f, center = center, style = Stroke(width = 2.5f))
        }
        if (id in diagnosticNodes) {
            drawCircle(Palette.diagnosticMarker, radius = 4f, center = Offset(center.x - 10f, center.y - 10f))
        }
    }
}

private fun DrawScope.drawCaretHalo(center: Offset, zoom: Float) {
    val zoomCompensation = zoomCompensation(zoom)
    drawCircle(
        Palette.caretHaloFill,
        radius = LayoutMetrics.NODE_RADIUS + 15f * zoomCompensation,
        center = center,
    )
    drawCircle(
        Palette.caretHalo,
        radius = LayoutMetrics.NODE_RADIUS + 10f * zoomCompensation,
        center = center,
        style = Stroke(width = 3f * zoomCompensation),
    )
    drawCircle(
        Palette.caretHalo.copy(alpha = 0.82f),
        radius = LayoutMetrics.NODE_RADIUS + 16f * zoomCompensation,
        center = center,
        style = Stroke(width = 1.4f * zoomCompensation),
    )
}

private fun DrawScope.drawNodeShape(segment: NodeSegment, center: Offset, color: Color) {
    when (segment) {
        is NodeSegment.Symbol -> {
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - LayoutMetrics.NODE_RADIUS)
                    lineTo(center.x + LayoutMetrics.NODE_RADIUS * 0.866f, center.y + LayoutMetrics.NODE_RADIUS * 0.5f)
                    lineTo(center.x - LayoutMetrics.NODE_RADIUS * 0.866f, center.y + LayoutMetrics.NODE_RADIUS * 0.5f)
                    close()
                },
                color,
            )
        }
        is NodeSegment.Package -> {
            val s = LayoutMetrics.NODE_RADIUS * 0.707f
            drawRect(color, topLeft = Offset(center.x - s, center.y - s), size = Size(s * 2, s * 2))
        }
        is NodeSegment.Module -> {
            val r = LayoutMetrics.NODE_RADIUS
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r, center.y)
                    lineTo(center.x, center.y + r)
                    lineTo(center.x - r, center.y)
                    close()
                },
                color,
            )
        }
        is NodeSegment.SourceSet -> drawPath(hexagonPath(center, LayoutMetrics.NODE_RADIUS), color)
        else -> drawCircle(color, radius = LayoutMetrics.NODE_RADIUS, center = center)
    }
}

private fun DrawScope.drawNodeShapeStroke(
    segment: NodeSegment,
    center: Offset,
    color: Color,
    radiusOffset: Float,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
) {
    val r = LayoutMetrics.NODE_RADIUS + radiusOffset
    val stroke = Stroke(width = strokeWidth, pathEffect = pathEffect)
    when (segment) {
        is NodeSegment.Symbol -> {
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r * 0.866f, center.y + r * 0.5f)
                    lineTo(center.x - r * 0.866f, center.y + r * 0.5f)
                    close()
                },
                color,
                style = stroke,
            )
        }
        is NodeSegment.Package -> {
            val s = r * 0.707f
            drawRect(color, topLeft = Offset(center.x - s, center.y - s), size = Size(s * 2, s * 2), style = stroke)
        }
        is NodeSegment.Module -> {
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r, center.y)
                    lineTo(center.x, center.y + r)
                    lineTo(center.x - r, center.y)
                    close()
                },
                color,
                style = stroke,
            )
        }
        is NodeSegment.SourceSet -> drawPath(hexagonPath(center, r), color, style = stroke)
        else -> drawCircle(color, radius = r, center = center, style = stroke)
    }
}

/** Flat-top hexagon centered at [center] with circumradius [radius] (source-set nodes). */
private fun hexagonPath(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i)).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawLabel(
    measured: TextLayoutResult,
    rect: LayoutRect,
    color: Color,
) {
    val center = rect.center
    val x = center.x - measured.size.width / 2f
    val y = center.y + LayoutMetrics.NODE_RADIUS + 4f
    drawText(measured, Palette.labelShadow, Offset(x + 1f, y + 1f))
    drawText(measured, color, Offset(x, y))
}

/** Containing-module label above a source-set node (REQUIREMENTS: Node). */
private fun DrawScope.drawModuleLabel(textMeasurer: TextMeasurer, rect: LayoutRect, moduleId: String) {
    val measured = textMeasurer.measure(moduleId, moduleLabelTextStyle)
    val center = rect.center
    val x = center.x - measured.size.width / 2f
    val y = center.y - LayoutMetrics.NODE_RADIUS - measured.size.height - 3f
    drawText(measured, Palette.labelShadow, Offset(x + 1f, y + 1f))
    drawText(measured, Palette.moduleLabel, Offset(x, y))
}

/** Approval percent (e.g. `56%`) centered in the node when the indicator is toggled on. */
private fun DrawScope.drawApprovalPercent(textMeasurer: TextMeasurer, visibleNode: VisibleNode, center: Offset) {
    val summary = visibleNode.approval?.takeIf { it.totalLeaves > 0 } ?: return
    val percent = summary.approvedLeaves * 100 / summary.totalLeaves
    val measured = textMeasurer.measure("$percent%", percentTextStyle)
    val origin = Offset(center.x - measured.size.width / 2f, center.y - measured.size.height / 2f)
    drawText(measured, Palette.labelShadow, Offset(origin.x + 1f, origin.y + 1f))
    drawText(measured, Palette.percentLabel, origin)
}

/** ADR-008 intrinsic internal-dependency badge on a collapsed node — never a self-loop. */
private fun DrawScope.drawBadge(textMeasurer: TextMeasurer, rect: LayoutRect, count: Int) {
    val nodeCenter = rect.center.toOffset()
    val center = Offset(nodeCenter.x + 13f, nodeCenter.y - 13f)
    drawCircle(Palette.badgeFill, radius = 9f, center = center)
    drawCircle(Palette.label, radius = 9f, center = center, style = Stroke(width = 1f))
    val measured = textMeasurer.measure(count.toString(), TextStyle(fontSize = 9.sp))
    drawText(measured, Palette.label, Offset(center.x - measured.size.width / 2, center.y - measured.size.height / 2))
}

private fun DrawScope.drawApprovalRing(
    visibleNode: VisibleNode,
    center: Offset,
    zoom: Float,
) {
    val completeness = visibleNode.approval?.completeness ?: return
    val zoomCompensation = zoomCompensation(zoom)
    val strokeWidth = APPROVAL_RING_STROKE_WIDTH * zoomCompensation
    val pathEffect = when (completeness) {
        ApprovalCompleteness.FULL -> null
        ApprovalCompleteness.PARTIAL -> PathEffect.dashPathEffect(
            floatArrayOf(10f * zoomCompensation, 6f * zoomCompensation),
            0f,
        )
        ApprovalCompleteness.NONE -> PathEffect.dashPathEffect(
            floatArrayOf(2.5f * zoomCompensation, 6f * zoomCompensation),
            0f,
        )
    }
    drawCircle(
        color = Palette.approval,
        radius = LayoutMetrics.NODE_RADIUS + APPROVAL_RING_PADDING * zoomCompensation,
        center = center,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}

private fun zoomCompensation(zoom: Float): Float =
    1f / zoom.coerceAtLeast(MIN_ZOOM)

private fun LayoutPoint.toOffset(): Offset = Offset(x, y)

private fun Offset.scaledBy(scale: Float): Offset = Offset(x * scale, y * scale)

private fun Offset.toOffsetAt(scale: Float): Offset = Offset(x * scale, y * scale)

private fun GraphLayout.viewportIssue(width: Float, height: Float): String? {
    if (bounds.isEmpty()) return "Layout produced no visible node bounds."
    if (width <= 0f || height <= 0f) return null
    val hasVisibleBounds = bounds.values.any { rect ->
        rect.right >= 0f && rect.x <= width && rect.bottom >= 0f && rect.y <= height
    }
    return if (hasVisibleBounds) null else "Layout produced nodes outside the visible canvas."
}

private fun DrawScope.drawCanvasMessage(textMeasurer: TextMeasurer, message: String) {
    val measured = textMeasurer.measure(message, TextStyle(fontSize = 12.sp))
    drawText(
        measured,
        Palette.diagnosticMarker,
        Offset(
            x = (size.width - measured.size.width) / 2f,
            y = (size.height - measured.size.height) / 2f,
        ),
    )
}

private val labelTextStyle = TextStyle(fontSize = 11.sp)
private val moduleLabelTextStyle = TextStyle(fontSize = 9.sp)
private val percentTextStyle = TextStyle(fontSize = 9.sp)

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 4f
private const val ZOOM_STEP = 1.12f
private const val APPROVAL_RING_PADDING = 8f
private const val APPROVAL_RING_STROKE_WIDTH = 5f
private const val SELECTION_RING_OFFSET = 15f

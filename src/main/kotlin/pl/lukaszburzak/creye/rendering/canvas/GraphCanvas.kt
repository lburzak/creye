package pl.lukaszburzak.creye.rendering.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.rendering.layout.GraphLayout
import pl.lukaszburzak.creye.rendering.layout.LayoutMetrics
import pl.lukaszburzak.creye.rendering.layout.LayoutPoint
import pl.lukaszburzak.creye.rendering.layout.LayoutRect
import pl.lukaszburzak.creye.rendering.projection.VisibleEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Render palette: fixed values legible on both light and dark IDE themes. */
private object Palette {
    val nodeFill = Color(0xFF5B6670)
    val externalFill = Color(0xFF7E57C2)
    val added = Color(0xFF4CAF50)
    val modified = Color(0xFFFFB300)
    val deleted = Color(0xFFE53935)
    val selection = Color(0xFF2196F3)
    val internalEdge = Color(0xFF42A5F5)
    val externalEdge = Color(0xFFAB47BC)
    val cohesionEdge = Color(0xFFFF7043)
    val multiClassEdge = Color(0xFF90A4AE)
    val badgeFill = Color(0xFF455A64)
    val diagnosticMarker = Color(0xFFFFC107)
    val label = Color(0xFFECEFF1)
    val labelShadow = Color(0xCC263238)
    val collapsedRing = Color(0xFFECEFF1)
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
    diagnosticNodes: Set<GraphNodeId>,
    onSelect: (GraphNodeId?) -> Unit,
    onExpand: (GraphNodeId.Structural) -> Unit,
    onCollapseSelfAndSiblings: (GraphNodeId.Structural) -> Unit,
    onMoveNode: (GraphNodeId, LayoutPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var dragTarget by remember { mutableStateOf<GraphNodeId?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val currentLayout by rememberUpdatedState(layout)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnCollapseSelfAndSiblings by rememberUpdatedState(onCollapseSelfAndSiblings)
    val currentOnMoveNode by rememberUpdatedState(onMoveNode)

    fun hit(position: Offset, graphLayout: GraphLayout = currentLayout): GraphNodeId? {
        val p = position - pan
        return graphLayout.bounds.entries
            .mapNotNull { (id, rect) ->
                val center = rect.center.toOffset()
                val distance = hypot((p.x - center.x).toDouble(), (p.y - center.y).toDouble()).toFloat()
                if (distance <= LayoutMetrics.NODE_RADIUS) id to distance else null
            }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnSelect(hit(it)) },
                    onDoubleTap = { position ->
                        (hit(position) as? GraphNodeId.Structural)?.let(currentOnExpand)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTarget = hit(it) },
                    onDragCancel = { dragTarget = null },
                    onDragEnd = { dragTarget = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val target = dragTarget
                        if (target == null) {
                            pan += dragAmount
                        } else {
                            currentLayout.centerOf(target)?.let { center ->
                                currentOnMoveNode(
                                    target,
                                    LayoutPoint(center.x + dragAmount.x, center.y + dragAmount.y),
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
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val position = event.changes.firstOrNull()?.position ?: continue
                            (hit(position) as? GraphNodeId.Structural)?.let(currentOnCollapseSelfAndSiblings)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        translate(pan.x, pan.y) {
            drawEdges(visible, layout)
            drawNodes(visible, layout, selected, diagnosticNodes, textMeasurer)
        }
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
    diagnosticNodes: Set<GraphNodeId>,
    textMeasurer: TextMeasurer,
) {
    for (visibleNode in visible.structuralNodes.sortedBy { it.node.path.segments.size }) {
        val id = GraphNodeId.Structural(visibleNode.node.path)
        val rect = layout.bounds[id] ?: continue
        val changeColor = when (visibleNode.node.change) {
            ChangeKind.ADDED -> Palette.added
            ChangeKind.MODIFIED -> Palette.modified
            ChangeKind.DELETED -> Palette.deleted
            null -> null
        }
        val center = rect.center.toOffset()
        drawCircle(changeColor ?: Palette.nodeFill, radius = LayoutMetrics.NODE_RADIUS, center = center)
        drawLabel(textMeasurer, visibleNode.node.displayName, rect, Palette.label)
        if (selected == id) {
            drawCircle(Palette.selection, radius = LayoutMetrics.NODE_RADIUS + 3f, center = center, style = Stroke(width = 2.5f))
        }
        if (visibleNode.isCollapsed) {
            drawCircle(Palette.collapsedRing, radius = LayoutMetrics.NODE_RADIUS - 4f, center = center, style = Stroke(width = 1.4f))
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
        drawLabel(textMeasurer, external.id.displayName, rect, Palette.label)
        if (selected == id) {
            drawCircle(Palette.selection, radius = LayoutMetrics.NODE_RADIUS + 3f, center = center, style = Stroke(width = 2.5f))
        }
        if (id in diagnosticNodes) {
            drawCircle(Palette.diagnosticMarker, radius = 4f, center = Offset(center.x - 10f, center.y - 10f))
        }
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    rect: LayoutRect,
    color: Color,
) {
    val measured = textMeasurer.measure(text, TextStyle(fontSize = 11.sp))
    val center = rect.center
    val x = center.x - measured.size.width / 2f
    val y = center.y + LayoutMetrics.NODE_RADIUS + 4f
    drawText(measured, Palette.labelShadow, Offset(x + 1f, y + 1f))
    drawText(measured, color, Offset(x, y))
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

private fun LayoutPoint.toOffset(): Offset = Offset(x, y)

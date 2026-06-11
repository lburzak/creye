package pl.lukaszburzak.creye.rendering.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
import pl.lukaszburzak.creye.rendering.layout.LayoutRect
import pl.lukaszburzak.creye.rendering.projection.VisibleEdge
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Render palette: fixed values legible on both light and dark IDE themes. */
private object Palette {
    val containerStroke = Color(0xFF7F8B99)
    val leafFill = Color(0xFF5B6670)
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
    val containerLabel = Color(0xFF9FB6CC)
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
    onToggleCollapse: (GraphNodeId.Structural) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()

    fun hit(position: Offset): GraphNodeId? {
        val p = position - pan
        return layout.bounds.entries
            .filter { (_, r) -> p.x >= r.x && p.x <= r.right && p.y >= r.y && p.y <= r.bottom }
            .minByOrNull { (_, r) -> r.width * r.height }
            ?.key
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(layout) {
                detectTapGestures(
                    onTap = { onSelect(hit(it)) },
                    onDoubleTap = { position ->
                        (hit(position) as? GraphNodeId.Structural)?.let(onToggleCollapse)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    pan += dragAmount
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
        val from = Offset(source.x + source.width / 2, source.y + source.height / 2)
        val to = Offset(target.x + target.width / 2, target.y + target.height / 2)
        val tip = clipToRect(from, to, target)
        val start = clipToRect(to, from, source)
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

/** Intersection of the segment [from]→[to] with [rect]'s boundary, nearest to [from]. */
private fun clipToRect(from: Offset, to: Offset, rect: LayoutRect): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    var t = 1f
    fun consider(candidate: Float) {
        if (candidate in 0f..1f && candidate < t) t = candidate
    }
    if (dx != 0f) {
        consider((rect.x - from.x) / dx)
        consider((rect.right - from.x) / dx)
    }
    if (dy != 0f) {
        consider((rect.y - from.y) / dy)
        consider((rect.bottom - from.y) / dy)
    }
    return Offset(from.x + dx * t, from.y + dy * t)
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
    val containers = visible.structuralNodes
        .map { it.node.path }
        .flatMapTo(mutableSetOf()) { path ->
            (1 until path.segments.size).map { path.segments.subList(0, it) }
        }

    // Containers first (outermost on the bottom), leaves and externals on top.
    val ordered = visible.structuralNodes.sortedBy { it.node.path.segments.size }
    for (visibleNode in ordered) {
        val id = GraphNodeId.Structural(visibleNode.node.path)
        val rect = layout.bounds[id] ?: continue
        val isContainer = visibleNode.node.path.segments in containers
        val changeColor = when (visibleNode.node.change) {
            ChangeKind.ADDED -> Palette.added
            ChangeKind.MODIFIED -> Palette.modified
            ChangeKind.DELETED -> Palette.deleted
            null -> null
        }
        val corner = CornerRadius(6f)
        val topLeft = Offset(rect.x, rect.y)
        val size = Size(rect.width, rect.height)
        if (isContainer && !visibleNode.isCollapsed) {
            drawRoundRect(changeColor ?: Palette.containerStroke, topLeft, size, corner, style = Stroke(width = 1.4f))
            drawLabel(textMeasurer, visibleNode.node.displayName, rect, Palette.containerLabel, header = true)
        } else {
            drawRoundRect(changeColor ?: Palette.leafFill, topLeft, size, corner)
            drawLabel(textMeasurer, visibleNode.node.displayName, rect, Palette.label, header = false)
        }
        if (selected == id) {
            drawRoundRect(Palette.selection, topLeft, size, corner, style = Stroke(width = 2.5f))
        }
        if (visibleNode.internalizedEdges.isNotEmpty()) {
            drawBadge(textMeasurer, rect, visibleNode.internalizedEdges.size)
        }
        if (id in diagnosticNodes) {
            drawCircle(Palette.diagnosticMarker, radius = 4f, center = Offset(rect.x + 7f, rect.y + 7f))
        }
    }

    for (external in visible.externalNodes) {
        val id = GraphNodeId.External(external.id)
        val rect = layout.bounds[id] ?: continue
        val corner = CornerRadius(rect.height / 2)
        drawRoundRect(Palette.externalFill, Offset(rect.x, rect.y), Size(rect.width, rect.height), corner)
        drawLabel(textMeasurer, external.id.displayName, rect, Palette.label, header = false)
        if (selected == id) {
            drawRoundRect(Palette.selection, Offset(rect.x, rect.y), Size(rect.width, rect.height), corner, style = Stroke(width = 2.5f))
        }
        if (id in diagnosticNodes) {
            drawCircle(Palette.diagnosticMarker, radius = 4f, center = Offset(rect.x + 7f, rect.y + 7f))
        }
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    rect: LayoutRect,
    color: Color,
    header: Boolean,
) {
    val measured = textMeasurer.measure(text, TextStyle(fontSize = 11.sp))
    val x = if (header) rect.x + 8f else rect.x + (rect.width - measured.size.width) / 2
    val y = if (header) {
        rect.y + (LayoutMetrics.HEADER_HEIGHT - measured.size.height) / 2
    } else {
        rect.y + (rect.height - measured.size.height) / 2
    }
    drawText(measured, color, Offset(x, y))
}

/** ADR-008 intrinsic internal-dependency badge on a collapsed node — never a self-loop. */
private fun DrawScope.drawBadge(textMeasurer: TextMeasurer, rect: LayoutRect, count: Int) {
    val center = Offset(rect.right - 2f, rect.y + 2f)
    drawCircle(Palette.badgeFill, radius = 9f, center = center)
    drawCircle(Palette.label, radius = 9f, center = center, style = Stroke(width = 1f))
    val measured = textMeasurer.measure(count.toString(), TextStyle(fontSize = 9.sp))
    drawText(measured, Palette.label, Offset(center.x - measured.size.width / 2, center.y - measured.size.height / 2))
}

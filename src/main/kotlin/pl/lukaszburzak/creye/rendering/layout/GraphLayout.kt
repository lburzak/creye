package pl.lukaszburzak.creye.rendering.layout

import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.projection.VisibleGraph
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class LayoutRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(other: LayoutRect): Boolean =
        other.x >= x && other.y >= y && other.right <= right && other.bottom <= bottom

    fun overlaps(other: LayoutRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

data class GraphLayout(
    val bounds: Map<GraphNodeId, LayoutRect>,
    val width: Float,
    val height: Float,
)

object LayoutMetrics {
    const val CHAR_WIDTH = 8f
    const val LEAF_HEIGHT = 32f
    const val HEADER_HEIGHT = 28f
    const val PADDING = 14f
    const val GAP = 14f
    const val MIN_WIDTH = 72f
    const val ROOT_GAP = 28f
    const val EXTERNAL_COLUMN_GAP = 56f

    fun labelWidth(label: String): Float = max(MIN_WIDTH, label.length * CHAR_WIDTH + 2 * PADDING)
}

/**
 * Deterministic layout of the visible graph: nested boxes for containment, shelf-packed
 * children below each parent's header band, modules packed at the root, external nodes
 * in a column to the right. Pure function — equal input yields equal output.
 */
fun layoutVisibleGraph(visible: VisibleGraph): GraphLayout {
    val byPath = visible.structuralNodes.associateBy { it.node.path }
    val children = visible.structuralNodes
        .mapNotNull { v ->
            val parent = v.node.path.parent() ?: return@mapNotNull null
            parent to v.node.path
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, paths) -> paths.sortedBy { it.sortKey() } }
    val roots = visible.structuralNodes
        .filter { it.node.path.parent() == null }
        .map { it.node.path }
        .sortedBy { it.sortKey() }

    val sizes = mutableMapOf<NodePath, Pair<Float, Float>>()
    fun measure(path: NodePath): Pair<Float, Float> = sizes.getOrPut(path) {
        val label = byPath.getValue(path).node.displayName
        val kids = children[path].orEmpty()
        if (kids.isEmpty()) {
            LayoutMetrics.labelWidth(label) to LayoutMetrics.LEAF_HEIGHT
        } else {
            val kidSizes = kids.map { measure(it) }
            val (rowsWidth, rowsHeight) = shelfSize(kidSizes)
            val width = max(LayoutMetrics.labelWidth(label), rowsWidth) + 2 * LayoutMetrics.PADDING
            val height = LayoutMetrics.HEADER_HEIGHT + rowsHeight + 2 * LayoutMetrics.PADDING
            width to height
        }
    }

    val bounds = mutableMapOf<GraphNodeId, LayoutRect>()
    fun place(path: NodePath, x: Float, y: Float) {
        val (w, h) = sizes.getValue(path)
        bounds[GraphNodeId.Structural(path)] = LayoutRect(x, y, w, h)
        val kids = children[path].orEmpty()
        if (kids.isEmpty()) return
        placeShelf(
            sizes = kids.map { sizes.getValue(it) },
            originX = x + LayoutMetrics.PADDING,
            originY = y + LayoutMetrics.HEADER_HEIGHT + LayoutMetrics.PADDING,
            maxRowWidth = shelfRowWidth(kids.map { sizes.getValue(it) }),
        ).forEachIndexed { index, (cx, cy) -> place(kids[index], cx, cy) }
    }

    val rootSizes = roots.map { measure(it) }
    var structuralRight = 0f
    placeShelf(rootSizes, 0f, 0f, shelfRowWidth(rootSizes), gap = LayoutMetrics.ROOT_GAP)
        .forEachIndexed { index, (x, y) ->
            place(roots[index], x, y)
            structuralRight = max(structuralRight, x + rootSizes[index].first)
        }

    var externalY = 0f
    val externalX = structuralRight + LayoutMetrics.EXTERNAL_COLUMN_GAP
    for (external in visible.externalNodes.sortedBy { it.id.id }) {
        val w = LayoutMetrics.labelWidth(external.id.displayName)
        bounds[GraphNodeId.External(external.id)] =
            LayoutRect(externalX, externalY, w, LayoutMetrics.LEAF_HEIGHT)
        externalY += LayoutMetrics.LEAF_HEIGHT + LayoutMetrics.GAP
    }

    val width = bounds.values.maxOfOrNull { it.right } ?: 0f
    val height = bounds.values.maxOfOrNull { it.bottom } ?: 0f
    return GraphLayout(bounds, width, height)
}

private fun NodePath.parent(): NodePath? =
    if (segments.size <= 1) null else NodePath(segments.subList(0, segments.size - 1))

private fun NodePath.sortKey(): String = segments.joinToString("/") { it.toString() }

/** Row width target keeping shelf packs roughly square. */
private fun shelfRowWidth(sizes: List<Pair<Float, Float>>): Float {
    val totalArea = sizes.sumOf { (w, h) -> (w * h).toDouble() }.toFloat()
    val widest = sizes.maxOfOrNull { it.first } ?: 0f
    return max(widest, ceil(sqrt(totalArea.toDouble())).toFloat() * 1.5f)
}

private fun shelfSize(sizes: List<Pair<Float, Float>>, gap: Float = LayoutMetrics.GAP): Pair<Float, Float> {
    val positions = placeShelf(sizes, 0f, 0f, shelfRowWidth(sizes), gap)
    val width = positions.indices.maxOf { positions[it].first + sizes[it].first }
    val height = positions.indices.maxOf { positions[it].second + sizes[it].second }
    return width to height
}

/** Left-to-right shelf packing wrapping at [maxRowWidth]; deterministic for equal input. */
private fun placeShelf(
    sizes: List<Pair<Float, Float>>,
    originX: Float,
    originY: Float,
    maxRowWidth: Float,
    gap: Float = LayoutMetrics.GAP,
): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()
    var x = originX
    var y = originY
    var rowHeight = 0f
    for ((w, h) in sizes) {
        if (x > originX && x - originX + w > maxRowWidth) {
            x = originX
            y += rowHeight + gap
            rowHeight = 0f
        }
        positions += x to y
        x += w + gap
        rowHeight = max(rowHeight, h)
    }
    return positions
}

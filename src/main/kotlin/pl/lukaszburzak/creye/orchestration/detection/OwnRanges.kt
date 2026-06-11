package pl.lukaszburzak.creye.orchestration.detection

/**
 * Pure interval math behind the ADR-004 own-range rule: a declaration's own range
 * is its source range minus the union of its direct child declarations' ranges.
 */
object OwnRanges {

    fun subtract(full: IntRange, children: List<IntRange>): List<IntRange> {
        if (full.isEmpty()) return emptyList()
        val sorted = children.filter { !it.isEmpty() }.sortedBy { it.first }
        val result = mutableListOf<IntRange>()
        var cursor = full.first
        for (child in sorted) {
            if (child.last < cursor) continue
            if (child.first > full.last) break
            if (child.first > cursor) result += cursor until child.first
            cursor = maxOf(cursor, child.last + 1)
        }
        if (cursor <= full.last) result += cursor..full.last
        return result
    }

    fun intersects(ranges: List<IntRange>, span: IntRange): Boolean =
        !span.isEmpty() && ranges.any { maxOf(it.first, span.first) <= minOf(it.last, span.last) }

    fun intersects(range: IntRange, span: IntRange): Boolean =
        !range.isEmpty() && !span.isEmpty() &&
            maxOf(range.first, span.first) <= minOf(range.last, span.last)
}

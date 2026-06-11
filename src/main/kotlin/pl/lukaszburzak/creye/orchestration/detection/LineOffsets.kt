package pl.lukaszburzak.creye.orchestration.detection

import pl.lukaszburzak.creye.domain.change.LineRange

/** Pure 1-based line → text-offset mapping for converting diff hunk ranges. */
class LineOffsets(private val text: String) {

    private val lineStarts: IntArray = buildList {
        add(0)
        text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
    }.toIntArray()

    val lineCount: Int get() = lineStarts.size

    /**
     * Offsets covered by [range], or null when the range is empty (an insertion
     * point) or lies outside the text (an unmappable hunk side).
     */
    fun toOffsets(range: LineRange): IntRange? {
        if (range.length == 0) return null
        if (range.start < 1 || range.start > lineCount) return null
        val lastLine = (range.start + range.length - 1).coerceAtMost(lineCount)
        val start = lineStarts[range.start - 1]
        val end = if (lastLine == lineCount) text.length else lineStarts[lastLine] - 1
        return start until end.coerceAtLeast(start + 1)
    }
}

package pl.lukaszburzak.creye.orchestration.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.change.LineRange

class OwnRangesTest {

    @Test
    fun `subtract with no children keeps full range`() {
        assertEquals(listOf(0..99), OwnRanges.subtract(0..99, emptyList()))
    }

    @Test
    fun `subtract removes interior children leaving gaps`() {
        assertEquals(
            listOf(0..9, 21..39, 61..99),
            OwnRanges.subtract(0..99, listOf(10..20, 40..60)),
        )
    }

    @Test
    fun `subtract handles children at the edges`() {
        assertEquals(listOf(11..89), OwnRanges.subtract(0..99, listOf(0..10, 90..99)))
    }

    @Test
    fun `subtract handles overlapping and unsorted children`() {
        assertEquals(
            listOf(0..4, 31..99),
            OwnRanges.subtract(0..99, listOf(20..30, 5..25)),
        )
    }

    @Test
    fun `subtract covering child yields empty own range`() {
        assertEquals(emptyList<IntRange>(), OwnRanges.subtract(10..20, listOf(0..30)))
    }

    @Test
    fun `intersects detects overlap and rejects disjoint spans`() {
        assertTrue(OwnRanges.intersects(listOf(0..9, 20..29), 9..10))
        assertTrue(OwnRanges.intersects(listOf(0..9, 20..29), 25..40))
        assertFalse(OwnRanges.intersects(listOf(0..9, 20..29), 10..19))
        assertFalse(OwnRanges.intersects(emptyList(), 0..100))
    }

    @Test
    fun `line offsets map ranges and reject out-of-bounds`() {
        val text = "one\ntwo\nthree\n"
        val offsets = LineOffsets(text)
        assertEquals(0..2, offsets.toOffsets(LineRange(1, 1)))
        assertEquals(4..6, offsets.toOffsets(LineRange(2, 1)))
        assertEquals(4..12, offsets.toOffsets(LineRange(2, 2)))
        assertEquals(null, offsets.toOffsets(LineRange(3, 0)))   // insertion point
        assertEquals(null, offsets.toOffsets(LineRange(99, 1)))  // unmappable
    }
}

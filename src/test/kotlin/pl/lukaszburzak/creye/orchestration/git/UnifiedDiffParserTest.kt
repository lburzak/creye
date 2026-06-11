package pl.lukaszburzak.creye.orchestration.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.change.Hunk
import pl.lukaszburzak.creye.domain.change.LineRange

class UnifiedDiffParserTest {

    private val NUL = '\u0000'

    @Test
    fun `name-status parses plain states`() {
        val entries = UnifiedDiffParser.parseNameStatusZ(
            "M${NUL}src/A.kt${NUL}A${NUL}src/B.kt${NUL}D${NUL}src/C.kt$NUL",
        )
        assertEquals(
            listOf(
                NameStatusEntry('M', "src/A.kt", null),
                NameStatusEntry('A', "src/B.kt", null),
                NameStatusEntry('D', "src/C.kt", null),
            ),
            entries,
        )
    }

    @Test
    fun `name-status parses rename with score as two paths`() {
        val entries = UnifiedDiffParser.parseNameStatusZ(
            "R100${NUL}old/Name.kt${NUL}new/Name.kt$NUL",
        )
        assertEquals(listOf(NameStatusEntry('R', "new/Name.kt", "old/Name.kt")), entries)
    }

    @Test
    fun `name-status keeps unsupported letters`() {
        val entries = UnifiedDiffParser.parseNameStatusZ("T${NUL}link.kt$NUL")
        assertEquals(listOf(NameStatusEntry('T', "link.kt", null)), entries)
    }

    @Test
    fun `patch parses modification hunks two-sided`() {
        val patch = """
            diff --git a/src/A.kt b/src/A.kt
            index 1111111..2222222 100644
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -3,2 +3,3 @@ fun f() {
            -old line
            -old line 2
            +new line
            +new line 2
            +new line 3
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(
            listOf(Hunk(LineRange(3, 2), LineRange(3, 3))),
            result.hunksByPath["src/A.kt"],
        )
    }

    @Test
    fun `patch parses pure addition with zero-length baseline side`() {
        val patch = """
            diff --git a/src/A.kt b/src/A.kt
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -5,0 +6,2 @@
            +inserted one
            +inserted two
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(
            listOf(Hunk(LineRange(5, 0), LineRange(6, 2))),
            result.hunksByPath["src/A.kt"],
        )
    }

    @Test
    fun `patch parses pure deletion with zero-length current side`() {
        val patch = """
            diff --git a/src/A.kt b/src/A.kt
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -7,3 +6,0 @@
            -gone
            -gone
            -gone
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(
            listOf(Hunk(LineRange(7, 3), LineRange(6, 0))),
            result.hunksByPath["src/A.kt"],
        )
    }

    @Test
    fun `patch keys deleted file hunks by baseline path`() {
        val patch = """
            diff --git a/src/Gone.kt b/src/Gone.kt
            deleted file mode 100644
            --- a/src/Gone.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -fun x() = 1
            -fun y() = 2
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(
            listOf(Hunk(LineRange(1, 2), LineRange(0, 0))),
            result.hunksByPath["src/Gone.kt"],
        )
    }

    @Test
    fun `patch keys renamed file hunks by new path`() {
        val patch = """
            diff --git a/old/Name.kt b/new/Name.kt
            similarity index 90%
            rename from old/Name.kt
            rename to new/Name.kt
            --- a/old/Name.kt
            +++ b/new/Name.kt
            @@ -2,1 +2,1 @@
            -val a = 1
            +val a = 2
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(
            listOf(Hunk(LineRange(2, 1), LineRange(2, 1))),
            result.hunksByPath["new/Name.kt"],
        )
    }

    @Test
    fun `patch parses multiple files and multiple hunks`() {
        val patch = """
            diff --git a/A.kt b/A.kt
            --- a/A.kt
            +++ b/A.kt
            @@ -1,1 +1,1 @@
            -a
            +b
            @@ -9,0 +10,1 @@
            +c
            diff --git a/B.kt b/B.kt
            --- a/B.kt
            +++ b/B.kt
            @@ -4,2 +4,0 @@
            -d
            -e
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(2, result.hunksByPath["A.kt"]?.size)
        assertEquals(1, result.hunksByPath["B.kt"]?.size)
    }

    @Test
    fun `patch tolerates no-newline marker without losing sync`() {
        val patch = """
            diff --git a/A.kt b/A.kt
            --- a/A.kt
            +++ b/A.kt
            @@ -3,1 +3,1 @@
            -old
            \ No newline at end of file
            +new
            \ No newline at end of file
            @@ -8,0 +9,1 @@
            +tail
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(2, result.hunksByPath["A.kt"]?.size)
    }

    @Test
    fun `patch body lines never start new headers`() {
        // The removed body line looks like a file header but must be consumed as body.
        val patch = """
            diff --git a/A.kt b/A.kt
            --- a/A.kt
            +++ b/A.kt
            @@ -1,2 +1,1 @@
            ---- a/Fake.kt
            -+++ b/Fake.kt
            +ok
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertEquals(listOf(Hunk(LineRange(1, 2), LineRange(1, 1))), result.hunksByPath["A.kt"])
        assertEquals(setOf("A.kt"), result.hunksByPath.keys)
    }

    @Test
    fun `patch flags submodule pointer changes`() {
        val patch = """
            diff --git a/libs/dep b/libs/dep
            --- a/libs/dep
            +++ b/libs/dep
            @@ -1,1 +1,1 @@
            -Subproject commit aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            +Subproject commit bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        """.trimIndent()
        val result = UnifiedDiffParser.parsePatch(patch)
        assertTrue("libs/dep" in result.submodulePaths)
    }

    @Test
    fun `empty inputs parse to empty results`() {
        assertEquals(emptyList<NameStatusEntry>(), UnifiedDiffParser.parseNameStatusZ(""))
        assertEquals(emptyMap<String, List<Hunk>>(), UnifiedDiffParser.parsePatch("").hunksByPath)
    }
}

package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import pl.lukaszburzak.creye.domain.change.ChangeComparison
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedFile
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.change.FileChangeState
import pl.lukaszburzak.creye.domain.change.FileMove
import pl.lukaszburzak.creye.domain.change.Hunk
import pl.lukaszburzak.creye.domain.change.LineRange
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.identity.NodeSegment

class ChangedSymbolDetectorTest : BasePlatformTestCase() {

    private val baselineBox = """
        package demo

        class Box {
            fun grow(): Int {
                return 1
            }

            fun shrink() = 2
        }
    """.trimIndent()

    private fun detect(vararg files: ChangedFile): ChangedSymbols =
        ChangedSymbolDetector(project) { path -> FileSegmentContext("mod", path) }
            .detect(ChangeComparison(files.toList(), emptyList()))

    private fun detectWithModule(moduleId: String, sourceSet: String? = null, vararg files: ChangedFile): ChangedSymbols =
        ChangedSymbolDetector(project) { path -> FileSegmentContext(moduleId, path, sourceSet) }
            .detect(ChangeComparison(files.toList(), emptyList()))

    fun `test resolved source set yields a module container parenting the source set`() {
        val content = """
            package demo

            class Fresh
        """.trimIndent()
        val result = detectWithModule(
            ":feature:channels-list",
            "main",
            ChangedFile("src/Fresh.kt", null, FileChangeState.ADDED, true, null, content, emptyList()),
        )
        val fileNode = result.changed.first { it.displayName == "Fresh.kt" }
        val head = fileNode.identity.segments.take(2)
        assertEquals(
            listOf(NodeSegment.Module(":feature:channels-list"), NodeSegment.SourceSet("main")),
            head,
        )
    }

    fun `test module with no source set stays a single module container`() {
        val content = "package demo\n\nclass Fresh"
        val result = detectWithModule(
            "creye",
            null,
            ChangedFile("src/Fresh.kt", null, FileChangeState.ADDED, true, null, content, emptyList()),
        )
        val fileNode = result.changed.first { it.displayName == "Fresh.kt" }
        assertEquals(NodeSegment.Module("creye"), fileNode.identity.segments.first())
        assertTrue(fileNode.identity.segments.none { it is NodeSegment.SourceSet })
    }

    fun `test build script attaches directly under its module container with no source set or package`() {
        val result = detectWithModule(
            ":feature:channels-list",
            null,
            ChangedFile("feature/channels-list/build.gradle.kts", null, FileChangeState.ADDED, true, null, "plugins { kotlin(\"jvm\") }", emptyList()),
        )
        val fileNode = result.changed.first { it.displayName == "build.gradle.kts" }
        assertEquals(
            listOf<NodeSegment>(
                NodeSegment.Module(":feature:channels-list"),
                NodeSegment.File("build.gradle.kts", "feature/channels-list/build.gradle.kts"),
            ),
            fileNode.identity.segments,
        )
    }

    private fun modified(path: String, baseline: String, current: String, vararg hunks: Hunk) =
        ChangedFile(path, null, FileChangeState.MODIFIED, true, baseline, current, hunks.toList())

    private fun ChangedSymbols.changedNamed(name: String) =
        changed.filter { it.displayName == name }

    fun `test added file marks every declaration and the file node added`() {
        val content = """
            package demo

            class Fresh {
                val seed = 7
            }
        """.trimIndent()
        val result = detect(
            ChangedFile("src/Fresh.kt", null, FileChangeState.ADDED, true, null, content, emptyList()),
        )
        assertEquals(setOf("Fresh.kt", "Fresh", "seed"), result.changed.map { it.displayName }.toSet())
        assertTrue(result.changed.all { it.kind == ChangeKind.ADDED })
        assertEmpty(result.diagnostics)
    }

    fun `test deleted file enumerates baseline declarations individually`() {
        val result = detect(
            ChangedFile("src/Box.kt", null, FileChangeState.DELETED, true, baselineBox, null, emptyList()),
        )
        assertEquals(
            setOf("Box.kt", "Box", "grow", "shrink"),
            result.changed.map { it.displayName }.toSet(),
        )
        assertTrue(result.changed.all { it.kind == ChangeKind.DELETED })
    }

    fun `test modified function body marks function changed and class contextual`() {
        val current = baselineBox.replace("return 1", "return 42")
        val result = detect(
            modified("src/Box.kt", baselineBox, current, Hunk(LineRange(5, 1), LineRange(5, 1))),
        )
        val grow = result.changedNamed("grow").single()
        assertEquals(ChangeKind.MODIFIED, grow.kind)
        assertNotNull(grow.currentRange)
        assertNotNull(grow.baselineRange)
        assertTrue(grow.currentText!!.contains("return 42"))
        assertTrue(grow.baselineText!!.contains("return 1"))
        // Both the containing class and the file root contain the change without
        // own-range intersection, so both are contextual (ADR-004 constraint 4).
        assertEquals(
            setOf("Box", "Box.kt"),
            result.contextual.map { lastName(it.identity.segments.last()) }.toSet(),
        )
        assertEmpty(result.changedNamed("Box.kt"))
        assertEmpty(result.diagnostics)
    }

    fun `test signature edit keeps identity and marks function modified`() {
        val current = baselineBox.replace("fun grow(): Int {", "fun grow(): Long {")
        val result = detect(
            modified("src/Box.kt", baselineBox, current, Hunk(LineRange(4, 1), LineRange(4, 1))),
        )
        assertEquals(ChangeKind.MODIFIED, result.changedNamed("grow").single().kind)
    }

    fun `test import-only edit is a file-level change not a diagnostic`() {
        val baseline = """
            package demo
            import java.util.ArrayList
            fun top() = 1
        """.trimIndent()
        val current = baseline.replace("import java.util.ArrayList", "import java.util.LinkedList")
        val result = detect(
            modified("src/Top.kt", baseline, current, Hunk(LineRange(2, 1), LineRange(2, 1))),
        )
        val fileChange = result.changed.single()
        assertEquals("Top.kt", fileChange.displayName)
        assertEquals(ChangeKind.MODIFIED, fileChange.kind)
        assertTrue(fileChange.identity.segments.last() is NodeSegment.File)
        assertEmpty(result.diagnostics)
    }

    fun `test deleted declaration is enumerated individually against baseline`() {
        val current = baselineBox.lines().filterNot { it.contains("fun shrink") }.joinToString("\n")
        val result = detect(
            modified("src/Box.kt", baselineBox, current, Hunk(LineRange(8, 1), LineRange(7, 0))),
        )
        val shrink = result.changedNamed("shrink").single()
        assertEquals(ChangeKind.DELETED, shrink.kind)
        assertNull(shrink.currentRange)
        assertNotNull(shrink.baselineRange)
        assertTrue(shrink.baselineText!!.contains("fun shrink"))
        assertEmpty(result.changedNamed("grow"))
    }

    fun `test clean rename reports file move and no declaration change`() {
        val result = detect(
            ChangedFile("src/NewName.kt", "src/OldName.kt", FileChangeState.RENAMED, true,
                baselineBox, baselineBox, emptyList()),
        )
        assertEquals(listOf(FileMove("src/OldName.kt", "src/NewName.kt")), result.movedFiles)
        assertEmpty(result.changed)
        assertEmpty(result.diagnostics)
    }

    fun `test dirty rename reports file move plus modified mapping`() {
        val current = baselineBox.replace("return 1", "return 42")
        val result = detect(
            ChangedFile("src/NewName.kt", "src/OldName.kt", FileChangeState.RENAMED, true,
                baselineBox, current, listOf(Hunk(LineRange(5, 1), LineRange(5, 1)))),
        )
        assertEquals(listOf(FileMove("src/OldName.kt", "src/NewName.kt")), result.movedFiles)
        assertEquals(ChangeKind.MODIFIED, result.changedNamed("grow").single().kind)
    }

    fun `test malformed source falls back to file-level change with diagnostic`() {
        val result = detect(
            modified("src/Broken.kt", "class Ok", "class {{{", Hunk(LineRange(1, 1), LineRange(1, 1))),
        )
        val fileChange = result.changed.single()
        assertEquals("Broken.kt", fileChange.displayName)
        assertTrue(fileChange.identity.segments.last() is NodeSegment.File)
        assertEquals(
            DiagnosticSource.CHANGED_SYMBOL_DETECTION,
            result.diagnostics.single().source,
        )
    }

    fun `test unmappable hunk emits detection diagnostic`() {
        val result = detect(
            modified("src/Box.kt", baselineBox, baselineBox, Hunk(LineRange(900, 2), LineRange(900, 2))),
        )
        assertEquals(
            DiagnosticSource.CHANGED_SYMBOL_DETECTION,
            result.diagnostics.single().source,
        )
        assertEmpty(result.changed)
    }

    fun `test added declaration in modified file is marked added`() {
        val current = baselineBox.replace(
            "    fun shrink() = 2",
            "    fun shrink() = 2\n\n    fun stretch() = 3",
        )
        val result = detect(
            modified("src/Box.kt", baselineBox, current, Hunk(LineRange(8, 0), LineRange(9, 2))),
        )
        val stretch = result.changedNamed("stretch").single()
        assertEquals(ChangeKind.ADDED, stretch.kind)
        assertNotNull(stretch.currentRange)
        assertNull(stretch.baselineRange)
        assertTrue(stretch.currentText!!.contains("fun stretch"))
    }

    fun `test non-kotlin files are ignored`() {
        val result = detect(
            ChangedFile("README.md", null, FileChangeState.MODIFIED, false, null, null,
                listOf(Hunk(LineRange(1, 1), LineRange(1, 1)))),
        )
        assertEmpty(result.changed)
        assertEmpty(result.diagnostics)
    }

    private fun lastName(segment: NodeSegment): String = when (segment) {
        is NodeSegment.Class -> segment.name
        is NodeSegment.Symbol -> segment.name
        is NodeSegment.File -> segment.name
        is NodeSegment.Module -> segment.id
        is NodeSegment.SourceSet -> segment.name
        is NodeSegment.Package -> segment.fqName
    }
}

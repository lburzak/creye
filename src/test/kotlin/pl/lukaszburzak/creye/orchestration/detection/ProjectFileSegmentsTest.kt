package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource

class ProjectFileSegmentsTest : BasePlatformTestCase() {

    fun `test uses gradle module id when available`() {
        val file = myFixture.addFileToProject("src/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { GradleModule(":app", null) },
            fileForPath = { file },
            moduleForFile = { module },
        )("src/Main.kt")

        assertEquals(":app", context.moduleId)
        assertNull(context.sourceSet)
        assertEmpty(context.diagnostics)
    }

    fun `test carries the resolved source set alongside the container id`() {
        val file = myFixture.addFileToProject("src/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { GradleModule(":feature:channels-list", "main") },
            fileForPath = { file },
            moduleForFile = { module },
        )("src/Main.kt")

        assertEquals(":feature:channels-list", context.moduleId)
        assertEquals("main", context.sourceSet)
        assertEmpty(context.diagnostics)
    }

    fun `test warns when file lands on parent module via an unimported subproject`() {
        val file = myFixture.addFileToProject("feature/filter-list/src/main/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { GradleModule(":feature", null) },
            fileForPath = { file },
            moduleForFile = { module },
            unsyncedSubprojectDir = { _, _ -> "/repo/feature/filter-list" },
        )("feature/filter-list/src/main/Main.kt")

        assertEquals(":feature", context.moduleId)
        val diagnostic = context.diagnostics.single()
        assertEquals(DiagnosticSource.PROJECT_MODEL, diagnostic.source)
        assertTrue(diagnostic.message.contains("/repo/feature/filter-list"))
        assertTrue(diagnostic.message.contains("re-sync Gradle"))
        assertTrue(diagnostic.message.contains(":feature"))
    }

    fun `test no warning when file genuinely belongs to its resolved module`() {
        val file = myFixture.addFileToProject("feature/channels-list/src/main/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { GradleModule(":feature:channels-list", "main") },
            fileForPath = { file },
            moduleForFile = { module },
            unsyncedSubprojectDir = { _, _ -> null },
        )("feature/channels-list/src/main/Main.kt")

        assertEmpty(context.diagnostics)
    }

    fun `test falls back to intellij module name with project model diagnostic`() {
        val file = myFixture.addFileToProject("src/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { null },
            fileForPath = { file },
            moduleForFile = { module },
        )("src/Main.kt")

        assertEquals(module.name, context.moduleId)
        assertNull(context.sourceSet)
        val diagnostic = context.diagnostics.single()
        assertEquals(DiagnosticSource.PROJECT_MODEL, diagnostic.source)
        assertTrue(diagnostic.message.contains("Gradle module id was not available"))
    }

    fun `test unresolved file with no source-root match uses unresolved module with project model diagnostic`() {
        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            fileForPath = { null },
            moduleForDeletedPath = { null },
        )("src/Missing.kt")

        assertEquals(ProjectFileSegments.UNRESOLVED_MODULE, context.moduleId)
        assertEquals(DiagnosticSource.PROJECT_MODEL, context.diagnostics.single().source)
    }

    fun `test deleted file resolves module via source root containment`() {
        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { GradleModule(":feature", null) },
            fileForPath = { null },
            moduleForDeletedPath = { module },
        )("src/Deleted.kt")

        assertEquals(":feature", context.moduleId)
        assertEmpty(context.diagnostics)
    }

    fun `test deleted file with source-root match but no gradle id falls back to intellij module name`() {
        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModule = { null },
            fileForPath = { null },
            moduleForDeletedPath = { module },
        )("src/Deleted.kt")

        assertEquals(module.name, context.moduleId)
        val diagnostic = context.diagnostics.single()
        assertEquals(DiagnosticSource.PROJECT_MODEL, diagnostic.source)
        assertTrue(diagnostic.message.contains("Gradle module id was not available"))
    }
}

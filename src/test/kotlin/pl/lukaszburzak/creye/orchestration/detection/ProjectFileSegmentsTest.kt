package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource

class ProjectFileSegmentsTest : BasePlatformTestCase() {

    fun `test uses gradle module id when available`() {
        val file = myFixture.addFileToProject("src/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModuleId = { ":app" },
            fileForPath = { file },
            moduleForFile = { module },
        )("src/Main.kt")

        assertEquals(":app", context.moduleId)
        assertEmpty(context.diagnostics)
    }

    fun `test falls back to intellij module name with project model diagnostic`() {
        val file = myFixture.addFileToProject("src/Main.kt", "fun main() = Unit").virtualFile

        val context = ProjectFileSegments(
            project = project,
            repositoryRootPath = project.basePath.orEmpty(),
            gradleModuleId = { null },
            fileForPath = { file },
            moduleForFile = { module },
        )("src/Main.kt")

        assertEquals(module.name, context.moduleId)
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
            gradleModuleId = { ":feature" },
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
            gradleModuleId = { null },
            fileForPath = { null },
            moduleForDeletedPath = { module },
        )("src/Deleted.kt")

        assertEquals(module.name, context.moduleId)
        val diagnostic = context.diagnostics.single()
        assertEquals(DiagnosticSource.PROJECT_MODEL, diagnostic.source)
        assertTrue(diagnostic.message.contains("Gradle module id was not available"))
    }
}

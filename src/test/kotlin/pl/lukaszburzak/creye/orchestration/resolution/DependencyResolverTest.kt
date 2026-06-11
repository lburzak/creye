package pl.lukaszburzak.creye.orchestration.resolution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.permissions.KaAnalysisPermissionRegistry
import pl.lukaszburzak.creye.domain.change.ChangeComparison
import pl.lukaszburzak.creye.domain.change.ChangeDetection
import pl.lukaszburzak.creye.domain.change.ChangedFile
import pl.lukaszburzak.creye.domain.change.FileChangeState
import pl.lukaszburzak.creye.domain.change.Hunk
import pl.lukaszburzak.creye.domain.change.LineRange
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.graph.DependencyClassification
import pl.lukaszburzak.creye.domain.graph.DependencyKind
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.orchestration.detection.ChangedSymbolDetector
import pl.lukaszburzak.creye.orchestration.detection.FileSegmentContext

class DependencyResolverTest : BasePlatformTestCase() {

    fun `test resolves function call constructor call property access and internal type reference`() {
        val helper = """
            package demo

            open class Base
            class Target : Base() {
                val value: Int = 1
            }
            fun makeTarget(): Target = Target()
        """.trimIndent()
        val baseline = """
            package demo

            fun changed() = 0
        """.trimIndent()
        val current = """
            package demo

            fun changed(input: Target): Target {
                val target = makeTarget()
                target.value
                return Target()
            }
        """.trimIndent()
        myFixture.addFileToProject("src/Helper.kt", helper)
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("fun changed"), length = 4),
        )

        assertTrue(resolved.edges.any { it.kind == DependencyKind.CALL && it.structuralTargetName() == "makeTarget" })
        assertTrue(resolved.edges.any { it.kind == DependencyKind.CALL && it.structuralTargetName() == "Target" })
        assertTrue(resolved.edges.any { it.kind == DependencyKind.PROPERTY_ACCESS && it.structuralTargetName() == "value" })
        assertTrue(resolved.edges.any { it.kind == DependencyKind.TYPE_REFERENCE && it.structuralTargetName() == "Target" })
        assertTrue(resolved.edges.any { it.classification == DependencyClassification.INTERNAL })
    }

    fun `test resolves return parameter supertype and generic type references`() {
        val helper = """
            package demo

            open class Base
            class Target
        """.trimIndent()
        val baseline = """
            package demo

            class Child
            fun changed() = emptyList<Any>()
        """.trimIndent()
        val current = """
            package demo

            class Child : Base()
            fun changed(input: Target): List<Target> = emptyList()
        """.trimIndent()
        myFixture.addFileToProject("src/Helper.kt", helper)
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("class Child"), length = 2),
        )

        val typeTargets = resolved.edges.filter { it.kind == DependencyKind.TYPE_REFERENCE }
            .mapNotNull { it.structuralTargetName() }
        assertTrue(typeTargets.contains("Target"))
        assertTrue(typeTargets.contains("Base"))
        assertTrue(resolved.edges.any { it.kind == DependencyKind.TYPE_REFERENCE && it.target is GraphNodeId.External })
    }

    fun `test resolves cohesion edge between changed symbols in different files`() {
        val helperBaseline = """
            package demo

            fun helper() = 1
        """.trimIndent()
        val helperCurrent = helperBaseline.replace("1", "2")
        val baseline = """
            package demo

            fun changed() = 0
        """.trimIndent()
        val current = """
            package demo

            fun changed() = helper()
        """.trimIndent()
        myFixture.addFileToProject("src/Helper.kt", helperCurrent)
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Helper.kt", helperBaseline, helperCurrent, hunkLine = helperCurrent.lineContaining("helper")),
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("helper")),
        )

        assertTrue(resolved.edges.any {
            it.kind == DependencyKind.CALL &&
                it.classification == DependencyClassification.COHESION &&
                it.structuralTargetName() == "helper"
        })
    }

    fun `test unresolved reference emits diagnostic and no edge`() {
        val baseline = """
            package demo

            fun changed() = 0
        """.trimIndent()
        val current = """
            package demo

            fun changed() = missing()
        """.trimIndent()
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("missing")),
        )

        assertEmpty(resolved.edges)
        assertEquals(DiagnosticSource.DEPENDENCY_RESOLUTION, resolved.diagnostics.single().source)
    }

    fun `test annotation dependency is deferred as diagnostic`() {
        val baseline = """
            package demo

            fun changed() = 1
        """.trimIndent()
        val current = """
            package demo

            @Deprecated("x")
            fun changed() = 1
        """.trimIndent()
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("Deprecated")),
        )

        assertTrue(resolved.diagnostics.any { it.message.contains("Annotation dependency is deferred") })
    }

    fun `test implicit invoke dependency is deferred as diagnostic and no edge`() {
        val helper = """
            package demo

            class Runner {
                operator fun invoke() = Unit
            }
        """.trimIndent()
        val baseline = """
            package demo

            fun changed(runner: Runner) = Unit
        """.trimIndent()
        val current = """
            package demo

            fun changed(runner: Runner) {
                runner()
            }
        """.trimIndent()
        myFixture.addFileToProject("src/Helper.kt", helper)
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("runner()")),
        )

        assertTrue(resolved.edges.none { it.kind == DependencyKind.CALL })
        assertTrue(resolved.diagnostics.any { it.message.contains("Implicit invoke dependency is deferred") })
    }

    fun `test implicit receiver dependency is deferred as diagnostic and no edge`() {
        val baseline = """
            package demo

            class Box {
                val value = 1
                fun changed() = Unit
            }
        """.trimIndent()
        val current = """
            package demo

            class Box {
                val value = 1
                fun changed() {
                    value.hashCode()
                }
            }
        """.trimIndent()
        myFixture.addFileToProject("src/Main.kt", current)

        val resolved = resolve(
            changedFile("src/Main.kt", baseline, current, hunkLine = current.lineContaining("value.hashCode")),
        )

        assertTrue(resolved.edges.none { it.kind == DependencyKind.PROPERTY_ACCESS })
        assertTrue(resolved.diagnostics.any { it.message.contains("Implicit receiver dependency is deferred") })
    }

    private fun resolve(vararg files: ChangedFile): ResolvedDependencies {
        val comparison = ChangeComparison(files.toList(), emptyList())
        return ApplicationManager.getApplication().runReadAction<ResolvedDependencies> {
            withAnalysisAllowed {
                val detection = ChangeDetection(
                    comparison,
                    ChangedSymbolDetector(project) { path -> FileSegmentContext("mod", path) }.detect(comparison),
                )
                DependencyResolver(project, project.basePath!!, ::fileSegment).resolve(detection)
            }
        }
    }

    @OptIn(KaImplementationDetail::class)
    private fun <T> withAnalysisAllowed(action: () -> T): T {
        val registry = KaAnalysisPermissionRegistry.getInstance()
        val wasEdtAllowed = registry.isAnalysisAllowedOnEdt
        val wasWriteAllowed = registry.isAnalysisAllowedInWriteAction
        registry.isAnalysisAllowedOnEdt = true
        registry.isAnalysisAllowedInWriteAction = true
        return try {
            action()
        } finally {
            registry.isAnalysisAllowedOnEdt = wasEdtAllowed
            registry.isAnalysisAllowedInWriteAction = wasWriteAllowed
        }
    }

    private fun changedFile(path: String, baseline: String, current: String, hunkLine: Int, length: Int = 1) =
        ChangedFile(
            path = path,
            previousPath = null,
            state = FileChangeState.MODIFIED,
            isKotlin = true,
            baselineContent = baseline,
            currentContent = current,
            hunks = listOf(Hunk(LineRange(hunkLine, length), LineRange(hunkLine, length))),
        )

    private fun fileSegment(path: String) = FileSegmentContext("mod", path)

    private fun String.lineContaining(text: String): Int =
        lines().indexOfFirst { it.contains(text) }.also { require(it >= 0) } + 1

    private fun pl.lukaszburzak.creye.domain.graph.DependencyEdge.structuralTargetName(): String? =
        ((target as? GraphNodeId.Structural)?.path?.segments?.last() as? NodeSegment.Symbol)?.name
            ?: ((target as? GraphNodeId.Structural)?.path?.segments?.last() as? NodeSegment.Class)?.name
}

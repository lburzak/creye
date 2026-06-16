package pl.lukaszburzak.creye.orchestration

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity

class GraphAnalysisServiceTest : BasePlatformTestCase() {

    // The pipeline hops onto the EDT to save documents; blocking the EDT here would deadlock.
    override fun runInDispatchThread(): Boolean = false

    fun `test non-git project yields git error diagnostic and empty changed-file set`() {
        val service = GraphAnalysisService.getInstance(project)
        val detection = runBlocking { service.detectChanges("main").await() }
        assertEmpty(detection.comparison.files)
        assertEmpty(detection.symbols.changed)
        val diagnostic = detection.comparison.diagnostics.single()
        assertEquals(DiagnosticSource.GIT, diagnostic.source)
        assertEquals(Severity.ERROR, diagnostic.severity)
    }

    fun `test analyze on non-git project yields empty graph with git diagnostic`() {
        val service = GraphAnalysisService.getInstance(project)
        val graph = runBlocking { service.analyze("main").await() }.graph
        assertEmpty(graph.structuralNodes)
        assertEmpty(graph.externalNodes)
        assertEmpty(graph.edges)
        val diagnostic = graph.diagnostics.single()
        assertEquals(DiagnosticSource.GIT, diagnostic.source)
        assertEquals(Severity.ERROR, diagnostic.severity)
    }
}

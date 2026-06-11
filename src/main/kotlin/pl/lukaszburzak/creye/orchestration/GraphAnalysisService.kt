package pl.lukaszburzak.creye.orchestration

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import pl.lukaszburzak.creye.domain.DependencyGraph

/**
 * Project-level orchestration service (ADR-001): runs the analysis pipeline off the
 * UI thread on a plugin-owned coroutine scope and produces the ADR-007 domain graph.
 * Invocable without any UI entry point so analysis behavior is testable headlessly.
 */
@Service(Service.Level.PROJECT)
class GraphAnalysisService(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    /**
     * Launches analysis of the working directory against [branch].
     * Cancel the returned [Deferred] to cancel the analysis.
     */
    fun analyze(branch: String): Deferred<DependencyGraph> = scope.async {
        // Pipeline lands with the change-detection and dependency-model milestones.
        DependencyGraph()
    }

    companion object {
        fun getInstance(project: Project): GraphAnalysisService = project.getService(GraphAnalysisService::class.java)
    }
}

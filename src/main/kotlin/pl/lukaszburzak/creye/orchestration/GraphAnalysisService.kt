package pl.lukaszburzak.creye.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import pl.lukaszburzak.creye.domain.change.ChangeDetection
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.orchestration.graph.GraphAssembler
import pl.lukaszburzak.creye.orchestration.detection.ChangedSymbolDetector
import pl.lukaszburzak.creye.orchestration.detection.ProjectFileSegments
import pl.lukaszburzak.creye.orchestration.git.GitChangeComparator
import pl.lukaszburzak.creye.orchestration.resolution.DependencyResolver

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
        val detection = detectChangesNow(branch)
        val rootPath = repositoryRootPath()
        val resolved = readAction {
            DependencyResolver(project, rootPath, ProjectFileSegments(project, rootPath))
                .resolve(detection)
        }
        GraphAssembler.assemble(
            symbols = detection.symbols,
            resolved = resolved,
            upstreamDiagnostics = detection.comparison.diagnostics,
        )
    }

    /** Launches the ADR-003 + ADR-004 change-detection pipeline against [branch]. */
    fun detectChanges(branch: String): Deferred<ChangeDetection> = scope.async {
        detectChangesNow(branch)
    }

    private suspend fun detectChangesNow(branch: String): ChangeDetection {
        // Editors and disk must agree before git diffs the working tree (ADR-003).
        withContext(Dispatchers.EDT) {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        val comparison = withContext(Dispatchers.IO) {
            GitChangeComparator(project).compare(branch)
        }
        val symbols = readAction {
            val rootPath = repositoryRootPath()
            ChangedSymbolDetector(project, ProjectFileSegments(project, rootPath))
                .detect(comparison)
        }
        return ChangeDetection(comparison, symbols)
    }

    private fun repositoryRootPath(): String =
        GitRepositoryManager.getInstance(project).repositories.firstOrNull()?.root?.path
            ?: project.basePath.orEmpty()

    companion object {
        fun getInstance(project: Project): GraphAnalysisService = project.getService(GraphAnalysisService::class.java)
    }
}

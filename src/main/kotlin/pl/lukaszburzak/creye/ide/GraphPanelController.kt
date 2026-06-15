package pl.lukaszburzak.creye.ide

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lukaszburzak.creye.domain.graph.DependencyGraph
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.orchestration.GraphAnalysisService
import pl.lukaszburzak.creye.rendering.AnalysisPhase
import pl.lukaszburzak.creye.rendering.GraphPanelState

/**
 * Ide-layer glue between the rendering surface and orchestration (ADR-001): owns the
 * panel state flow, branch selection (ADR-011), and analysis lifecycle. The rendering
 * layer only ever sees [GraphPanelState] values and callbacks.
 */
@Service(Service.Level.PROJECT)
class GraphPanelController(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GraphPanelState())
    val state: StateFlow<GraphPanelState> = _state.asStateFlow()

    private var analysis: Deferred<DependencyGraph>? = null
    private val diffPresenter = NodeDiffPresenter(project)

    /** Restores ADR-011 state when the panel opens: branch list, persisted selection. */
    fun activate() {
        val branches = localBranches()
        val persisted = DependencyGraphSettings.getInstance(project).branch
        when {
            persisted == null -> _state.update {
                GraphPanelState(branches = branches)
            }
            persisted in branches -> {
                _state.update { GraphPanelState(branches = branches, selectedBranch = persisted) }
                runAnalysis(persisted)
            }
            else -> _state.update {
                // ADR-011: no silent fallback to another branch.
                GraphPanelState(
                    branches = branches,
                    configurationDiagnostic =
                        "Previously selected branch '$persisted' no longer exists; select a branch.",
                )
            }
        }
    }

    fun selectBranch(branch: String) {
        DependencyGraphSettings.getInstance(project).branch = branch
        _state.update { it.copy(selectedBranch = branch, configurationDiagnostic = null) }
        runAnalysis(branch)
    }

    fun refresh() {
        _state.value.selectedBranch?.let(::runAnalysis)
    }

    /** Opens the IDE diff for a clicked node and its descendants against the selected branch. */
    fun showNodeDiff(node: NodePath) {
        val graph = (_state.value.phase as? AnalysisPhase.Ready)?.graph ?: return
        val branch = _state.value.selectedBranch ?: return
        scope.launch {
            val result = withContext(Dispatchers.Default) { diffPresenter.resolve(node, graph, branch) }
            withContext(Dispatchers.EDT) {
                when (result) {
                    is NodeDiffPresenter.Result.Ready ->
                        diffPresenter.show(result.changes, "Diff vs '$branch' (${result.changes.size} files)")
                    is NodeDiffPresenter.Result.Empty -> Messages.showInfoMessage(
                        project, "No changes in the selected node against '${result.branch}'.", "Show Diff",
                    )
                    is NodeDiffPresenter.Result.Failed -> Messages.showErrorDialog(
                        project, result.message, "Show Diff",
                    )
                }
            }
        }
    }

    private fun runAnalysis(branch: String) {
        analysis?.cancel()
        val deferred = GraphAnalysisService.getInstance(project).analyze(branch)
        analysis = deferred
        _state.update { it.copy(phase = AnalysisPhase.Running) }
        scope.launch {
            val phase = try {
                AnalysisPhase.Ready(deferred.await())
            } catch (e: CancellationException) {
                return@launch // superseded by a newer run or disposal; keep the newer phase
            } catch (e: Exception) {
                AnalysisPhase.Failed(e.message ?: e.javaClass.simpleName)
            }
            // Ignore results of superseded runs that lost the race to cancel.
            if (analysis === deferred) {
                _state.update { it.copy(phase = phase) }
            }
        }
    }

    private fun localBranches(): List<String> =
        GitRepositoryManager.getInstance(project).repositories.firstOrNull()
            ?.branches?.localBranches?.map { it.name }?.sorted()
            ?: emptyList()

    companion object {
        fun getInstance(project: Project): GraphPanelController =
            project.getService(GraphPanelController::class.java)
    }
}

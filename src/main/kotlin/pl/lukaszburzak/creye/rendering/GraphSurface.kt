package pl.lukaszburzak.creye.rendering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticAttachment
import pl.lukaszburzak.creye.domain.diagnostics.Severity

private const val SELECT_PLACEHOLDER = "— select branch —"

private val warningColor = Color(0xFFFFB300)
private val errorColor = Color(0xFFE53935)

/**
 * Panel surface (ADR-009, ADR-011): header with the comparison-branch combo box and
 * refresh, then the analysis phase — hint, progress, failure, or the rendered graph
 * with its graph-level diagnostics.
 */
@Composable
fun GraphSurface(
    state: GraphPanelState,
    onBranchSelected: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(state, onBranchSelected, onRefresh)
        state.configurationDiagnostic?.let {
            Text(it, color = warningColor, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        when (val phase = state.phase) {
            AnalysisPhase.Idle -> CenteredHint(
                if (state.branches.isEmpty()) "No git branches found in this project."
                else "Select a branch to compare the working directory against.",
            )
            AnalysisPhase.Running -> Centered {
                CircularProgressIndicator()
                Text("Analyzing changes against ${state.selectedBranch}…", modifier = Modifier.padding(top = 8.dp))
            }
            is AnalysisPhase.Failed -> CenteredHint("Analysis failed: ${phase.message}", color = errorColor)
            is AnalysisPhase.Ready -> Column(modifier = Modifier.fillMaxSize()) {
                DependencyGraphView(phase.graph, modifier = Modifier.weight(1f))
                GraphDiagnostics(phase.graph.diagnostics)
            }
        }
    }
}

@Composable
private fun Header(
    state: GraphPanelState,
    onBranchSelected: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Compare against:")
        // Placeholder at index 0 keeps "nothing selected" representable (ADR-011:
        // analysis must not run until a branch is explicitly chosen).
        val items = listOf(SELECT_PLACEHOLDER) + state.branches
        val selectedIndex = state.selectedBranch?.let { state.branches.indexOf(it) + 1 } ?: 0
        ListComboBox(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedItemChange = { index -> if (index > 0) onBranchSelected(state.branches[index - 1]) },
            modifier = Modifier.width(240.dp),
            enabled = state.branches.isNotEmpty(),
        )
        OutlinedButton(onClick = onRefresh, enabled = state.selectedBranch != null) {
            Text("Refresh")
        }
    }
}

/** Graph-level diagnostics strip (ADR-010): visible without drill-down. */
@Composable
private fun GraphDiagnostics(diagnostics: List<Diagnostic>) {
    val graphLevel = diagnostics.filter { it.attachment is DiagnosticAttachment.Graph || it.attachment == null }
    if (graphLevel.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        for (diagnostic in graphLevel) {
            val color = when (diagnostic.severity) {
                Severity.ERROR -> errorColor
                Severity.WARNING -> warningColor
                Severity.INFO -> Color.Unspecified
            }
            Text("${diagnostic.severity}: [${diagnostic.source}] ${diagnostic.message}", color = color)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun CenteredHint(text: String, color: Color = Color.Unspecified) {
    Centered { Text(text, color = color) }
}

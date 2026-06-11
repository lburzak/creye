package pl.lukaszburzak.creye.rendering

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text

/**
 * Rendering layer surface (ADR-009): consumes the ADR-007 domain graph and derives
 * the render-facing projection itself. Stub until the graph-rendering milestone.
 */
@Composable
fun GraphSurface() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("creye — dependency graph (analysis pipeline not yet implemented)")
    }
}

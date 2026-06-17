package pl.lukaszburzak.creye.rendering

import androidx.compose.ui.geometry.Offset
import pl.lukaszburzak.creye.domain.graph.GraphNodeId
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.rendering.layout.LayoutPoint

/**
 * Graph view state hoisted out of the Compose composition so it survives an editor tab change
 * (REQUIREMENTS: View state). A [JewelComposePanel][org.jetbrains.jewel.bridge.JewelComposePanel]
 * disposes its composition when the editor component leaves the Swing hierarchy, discarding every
 * `remember`. This holder is owned by the project-scoped controller, which outlives the panel, so
 * the composables seed their state from it and write changes back. It is reset only when analysis
 * re-runs against a different branch.
 */
class GraphViewState {
    var expanded: Set<NodePath> = emptySet()
    var undoStack: List<Set<NodePath>> = emptyList()
    var selected: GraphNodeId? = null
    var showExternal: Boolean = false
    var paused: Boolean = false
    var pan: Offset = Offset.Zero
    var zoom: Float = 1f
    /** Last live node positions, so the restored layout resumes in place instead of re-seeding. */
    var centers: Map<GraphNodeId, LayoutPoint> = emptyMap()

    fun reset() {
        expanded = emptySet()
        undoStack = emptyList()
        selected = null
        showExternal = false
        paused = false
        pan = Offset.Zero
        zoom = 1f
        centers = emptyMap()
    }
}

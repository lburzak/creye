package pl.lukaszburzak.creye.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAware

/**
 * Toggles approval of the changed symbol under the combined-diff caret (REQUIREMENTS: Combined
 * Diff). A registered IDE action so it is discoverable via Find Action and rebindable in the
 * keymap. Context is supplied by the combined-diff panel through [TOGGLE_APPROVAL_AT_CARET];
 * the action is enabled only while that panel (or a descendant editor) holds focus.
 */
class ToggleApprovalAtCaretAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(TOGGLE_APPROVAL_AT_CARET)?.run()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(TOGGLE_APPROVAL_AT_CARET) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

/** Caret-toggle handler contributed by the focused combined-diff panel via its data context. */
val TOGGLE_APPROVAL_AT_CARET: DataKey<Runnable> =
    DataKey.create("creye.toggleApprovalAtCaret")

/** Action id as registered in plugin.xml; used to mount the action in the diff toolbar. */
const val TOGGLE_APPROVAL_AT_CARET_ACTION_ID: String = "pl.lukaszburzak.creye.ToggleApprovalAtCaret"

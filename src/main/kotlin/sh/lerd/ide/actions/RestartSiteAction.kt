package sh.lerd.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import sh.lerd.ide.site.LerdSiteService

class RestartSiteAction : AnAction("Restart Site") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val state = e.project?.let { LerdSiteService.getInstance(it).state }
        e.presentation.isEnabledAndVisible = state?.site != null && state.daemonReachable
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { SiteAction.run(it, "restart") }
    }
}

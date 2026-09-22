package sh.lerd.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import sh.lerd.ide.site.LerdSiteService

class OpenSiteAction : AnAction("Open Site in Browser") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project?.let { LerdSiteService.getInstance(it).state.site } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val site = e.project?.let { LerdSiteService.getInstance(it).state.site } ?: return
        BrowserUtil.browse("${if (site.site.tls) "https" else "http"}://${site.domain}")
    }
}

package sh.lerd.ide.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.site.LerdSiteService

/**
 * One site action from lerd's handleSiteAction switch, run off the EDT. A
 * failure is a balloon naming what lerd said, never a silent no-op.
 */
class SiteAction(
    text: String,
    private val action: String,
    private val params: (LerdSiteService) -> Map<String, String> = { emptyMap() },
) : AnAction(text) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { LerdSiteService.getInstance(it).state.daemonReachable } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        run(project, action, params)
    }

    companion object {
        fun run(
            project: Project,
            action: String,
            params: (LerdSiteService) -> Map<String, String> = { emptyMap() },
        ) {
            val service = LerdSiteService.getInstance(project)
            val domain = service.state.site?.domain ?: return
            val branch = service.state.site?.branch
            AppExecutorUtil.getAppExecutorService().execute {
                val query = buildMap {
                    putAll(params(service))
                    branch?.let { put("branch", it) }
                }
                when (val result = service.client().siteAction(domain, action, query)) {
                    is LerdResult.Ok -> service.refresh()
                    is LerdResult.Err -> notifyError(project, action, result.message)
                }
            }
        }

        private fun notifyError(project: Project, action: String, message: String) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Lerd")
                .createNotification("Lerd could not $action the site", message, NotificationType.ERROR)
                .notify(project)
        }
    }
}

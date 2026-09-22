package sh.lerd.ide.site

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import sh.lerd.ide.php.PhpInterpreterSync
import sh.lerd.ide.php.PhpLevelSync
import sh.lerd.ide.php.PhpServerSync
import sh.lerd.ide.settings.LerdSettings

/**
 * Wakes the service so the widget has an answer before anyone looks at it, and
 * says something only when there is something to say: this is a lerd site but
 * lerd is not running. A project lerd does not know stays silent.
 */
class LerdStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = LerdSiteService.getInstance(project)
        service.refresh()

        // The IDE's own PHP widget follows lerd from here on.
        project.messageBus.connect(service).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener {
                PhpLevelSync.apply(project, it)
                PhpInterpreterSync.apply(project, it)
                PhpServerSync.apply(project, it)
            },
        )
        PhpLevelSync.apply(project, service.state)
        PhpInterpreterSync.apply(project, service.state)
        PhpServerSync.apply(project, service.state)

        val connection = project.messageBus.connect()
        connection.subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { state ->
                if (state.site != null && !state.daemonReachable) {
                    connection.disconnect()
                    notifyStopped(project, state.site.displayName)
                } else if (state.site != null) {
                    connection.disconnect()
                }
            },
        )
    }

    private fun notifyStopped(project: Project, name: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Lerd")
            .createNotification("$name is a Lerd site, but Lerd is not running", NotificationType.INFORMATION)
            .addAction(
                NotificationAction.createSimpleExpiring("Open dashboard") {
                    com.intellij.ide.BrowserUtil.browse(LerdSettings.getInstance().baseUrl)
                },
            )
            .notify(project)
    }
}

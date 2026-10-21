package sh.lerd.ide.php

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.php.debug.listener.PhpDebugExternalConnectionsAccepter
import sh.lerd.ide.api.LerdClient
import sh.lerd.ide.api.LerdPhp
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdStateListener

/**
 * Mirrors PhpStorm's "Listen for PHP Debug Connections" onto lerd's Xdebug for
 * the site's PHP version, so a click on the phone icon is all debugging takes.
 * lerd's switch is per version, so this reaches every site on that version.
 */
object XdebugListenSync {
    private const val LISTEN_ACTION = "PhpListenDebugAction"

    /** The value lerd should be set to, or null when it already matches. */
    fun target(listening: Boolean, version: String, fpms: List<LerdPhp>): Boolean? {
        val current = fpms.find { it.version == version }?.xdebugEnabled
        return listening.takeIf { it != current }
    }

    fun install(project: Project, service: LerdSiteService) {
        val accepter = PhpDebugExternalConnectionsAccepter.getInstance(project)
        var listening = accepter.isStarted
        // A change seen before lerd's state arrived waits here for it.
        var pending: Boolean? = listening.takeIf { it }

        fun apply(now: Boolean) {
            pending = if (sync(project, service, now)) null else now
        }

        // The listen action's own topic is internal API, so this watches the
        // action through the public listener and reads the result after it.
        ApplicationManager.getApplication().messageBus.connect(service).subscribe(
            AnActionListener.TOPIC,
            object : AnActionListener {
                override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
                    if (event.project != project || ActionManager.getInstance().getId(action) != LISTEN_ACTION) return
                    val now = accepter.isStarted
                    if (now != listening) {
                        listening = now
                        apply(now)
                    }
                }
            },
        )
        val connection = project.messageBus.connect(service)
        connection.subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener {
                // PhpStorm's restore of listening may land after this installs.
                if (accepter.isStarted && !listening) listening = true.also { pending = true }
                pending?.let(::apply)
            },
        )
        // PhpStorm restores listening when a project opens. Only an IDE that is
        // listening acts here, so opening a project never switches Xdebug off.
        pending?.let(::apply)

        // Closing the project ends its listening as surely as the icon does.
        // The service's client is closed by then, so this one gets its own.
        Disposer.register(service) {
            if (listening) sync(project, service, false, closing = true)
        }
    }

    /** False when lerd's state is not known yet, so the caller can retry. */
    private fun sync(
        project: Project,
        service: LerdSiteService,
        listening: Boolean,
        closing: Boolean = false,
    ): Boolean {
        val state = service.state
        val version = state.site?.phpVersion
        if (version == null || !state.daemonReachable) return false
        val enabled = target(listening, version, state.status?.phpFpms.orEmpty()) ?: return true

        AppExecutorUtil.getAppExecutorService().execute {
            if (closing) {
                LerdClient { LerdSettings.getInstance().baseUrl }.use { it.xdebug(version, enabled) }
                return@execute
            }
            val verb = if (enabled) "on" else "off"
            when (val result = service.client().xdebug(version, enabled)) {
                is LerdResult.Err -> notify(project, "Could not turn Xdebug $verb for PHP $version: ${result.message}", NotificationType.WARNING)
                else -> notify(project, "Xdebug $verb for PHP $version", NotificationType.INFORMATION)
            }
            service.refresh()
        }
        return true
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        if (project.isDisposed) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Lerd")
            .createNotification(message, type)
            .notify(project)
    }
}

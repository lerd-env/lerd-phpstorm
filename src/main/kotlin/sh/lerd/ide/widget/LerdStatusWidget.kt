package sh.lerd.ide.widget

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.openapi.vfs.VirtualFile
import sh.lerd.ide.actions.SiteAction
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdState
import sh.lerd.ide.site.LerdStateListener

/**
 * The always-visible anchor: which site this project is, and whether it is up.
 * Hidden entirely for a project lerd does not know, so it costs nothing to
 * have the plugin installed while working on something else.
 */
class LerdStatusWidget(project: Project) : EditorBasedStatusBarPopup(project, false), DumbAware {

    init {
        project.messageBus.connect(this).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { update() },
        )
    }

    override fun ID(): String = WIDGET_ID

    override fun createInstance(project: Project): StatusBarWidget = LerdStatusWidget(project)

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        val state = LerdSiteService.getInstance(project).state
        val site = state.site ?: return WidgetState.HIDDEN

        val label = buildString {
            append(site.domain)
            site.phpVersion?.let { append("  PHP ").append(it) }
        }
        val tooltip = when {
            !state.daemonReachable -> "Lerd is stopped"
            site.site.paused -> "${site.displayName} is paused"
            else -> "${site.displayName}${site.branch?.let { " on $it" }.orEmpty()}"
        }
        return WidgetState(tooltip, label, true)
    }

    override fun createPopup(context: DataContext): ListPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(
            "Lerd",
            popupActions(LerdSiteService.getInstance(project).state),
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
        )

    private fun popupActions(state: LerdState): ActionGroup {
        val group = DefaultActionGroup()
        val site = state.site ?: return group

        if (!state.daemonReachable) {
            group.add(openDashboard())
            return group
        }

        val scheme = if (site.site.tls) "https" else "http"
        group.add(simple("Open in Browser") { BrowserUtil.browse("$scheme://${site.domain}") })
        group.add(SiteAction("Restart", "restart"))
        group.add(SiteAction(if (site.site.tls) "Disable HTTPS" else "Enable HTTPS", if (site.site.tls) "unsecure" else "secure"))
        group.add(SiteAction(if (site.site.paused) "Unpause" else "Pause", if (site.site.paused) "unpause" else "pause"))
        group.addSeparator()
        group.add(phpVersions(state))
        group.addSeparator()
        group.add(openDashboard())
        return group
    }

    private fun phpVersions(state: LerdState): ActionGroup {
        val current = state.site?.phpVersion
        val versions = state.status?.phpFpms?.map { it.version }.orEmpty()
        val group = DefaultActionGroup("PHP Version", true)
        if (versions.isEmpty()) {
            group.add(simple("No versions reported") {}.also { it.templatePresentation.isEnabled = false })
            return group
        }
        versions.forEach { version ->
            val label = if (version == current) "$version  (current)" else version
            group.add(
                simple(label) {
                    SiteAction.run(project, "php") { mapOf("version" to version) }
                },
            )
        }
        return group
    }

    private fun openDashboard(): AnAction =
        simple("Open Lerd Dashboard") { BrowserUtil.browse(LerdSettings.getInstance().baseUrl) }

    private fun simple(text: String, run: () -> Unit): AnAction = object : AnAction(text), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = run()
    }

    companion object {
        const val WIDGET_ID: String = "sh.lerd.ide.StatusWidget"
    }
}

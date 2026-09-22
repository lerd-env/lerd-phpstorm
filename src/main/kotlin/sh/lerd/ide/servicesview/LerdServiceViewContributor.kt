package sh.lerd.ide.servicesview

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import sh.lerd.ide.LerdIcons
import sh.lerd.ide.actions.SiteAction
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.services.TerminalLauncher
import sh.lerd.ide.site.LerdSiteService
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Puts the site's containers in the IDE's Services window, beside Docker and
 * the databases, which is where a container people want a shell into belongs.
 *
 * Only containers: the one serving the site and one per service it uses. The
 * workers are systemd units with nothing to enter, and live on the Site tab.
 */
class LerdServiceViewContributor : ServiceViewContributor<SiteContainer> {

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        SimpleServiceViewDescriptor("Lerd", LerdIcons.ToolWindow)

    override fun getServices(project: Project): List<SiteContainer> {
        val state = LerdSiteService.getInstance(project).state
        val site = state.site ?: return emptyList()
        if (!state.daemonReachable) return emptyList()
        return SiteContainers.of(site, servicesOf(project))
    }

    override fun getServiceDescriptor(project: Project, service: SiteContainer): ServiceViewDescriptor =
        ContainerDescriptor(project, service)

    /**
     * The service list only fills in versions and ports, so a failure here
     * leaves the rows plainer rather than emptying the node.
     */
    private fun servicesOf(project: Project) =
        (LerdSiteService.getInstance(project).client().services() as? LerdResult.Ok)?.value.orEmpty()

    private class ContainerDescriptor(
        private val project: Project,
        private val entry: SiteContainer,
    ) : ServiceViewDescriptor {

        override fun getPresentation(): ItemPresentation = object : ItemPresentation {
            override fun getPresentableText(): String = entry.name
            override fun getLocationString(): String = entry.detail
            override fun getIcon(unused: Boolean): Icon =
                if (entry.running) AllIcons.RunConfigurations.TestState.Run else AllIcons.RunConfigurations.TestIgnored
        }

        override fun getId(): String = entry.container

        override fun getContentComponent(): JComponent = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 16)
            add(
                JBLabel(
                    buildString {
                        append("<html><b>").append(entry.name).append("</b><br>")
                        append(entry.container).append("<br>")
                        append(if (entry.running) "running" else "stopped")
                        if (entry.detail.isNotBlank()) append("<br>").append(entry.detail)
                        entry.connectionUrl?.let { append("<br>").append(it) }
                        append("</html>")
                    },
                ),
                BorderLayout.NORTH,
            )
        }

        override fun getToolbarActions(): ActionGroup = actions()

        override fun getPopupActions(): ActionGroup = actions()

        private fun actions(): ActionGroup {
            val group = DefaultActionGroup()
            if (entry.running) {
                group.add(
                    action("Open Terminal", AllIcons.Debugger.Console) {
                        TerminalLauncher.run(project, entry.name, entry.shellCommand, sitePath())
                    },
                )
                group.add(
                    action("Follow Log", AllIcons.Debugger.Db_set_breakpoint) {
                        TerminalLauncher.run(project, "${entry.name} log", entry.logsCommand, sitePath())
                    },
                )
                group.addSeparator()
            }
            group.add(lifecycle())
            return group
        }

        private fun lifecycle(): AnAction = when (entry.kind) {
            SiteContainer.Kind.RUNTIME ->
                if (entry.running) {
                    action("Restart", AllIcons.Actions.Restart) { SiteAction.run(project, "restart") }
                } else {
                    action("Start", AllIcons.Actions.Execute) { SiteAction.run(project, "restart") }
                }

            SiteContainer.Kind.SERVICE ->
                if (entry.running) {
                    action("Stop", AllIcons.Actions.Suspend) { service("stop") }
                } else {
                    action("Start", AllIcons.Actions.Execute) { service("start") }
                }
        }

        private fun service(verb: String) {
            AppExecutorUtil.getAppExecutorService().execute {
                LerdSiteService.getInstance(project).client().serviceAction(entry.name, verb)
                LerdSiteService.getInstance(project).refresh()
            }
        }

        private fun sitePath(): String? = LerdSiteService.getInstance(project).state.site?.path

        private fun action(text: String, icon: Icon, run: () -> Unit): AnAction =
            object : AnAction(text, null, icon) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) = run()
            }
    }
}

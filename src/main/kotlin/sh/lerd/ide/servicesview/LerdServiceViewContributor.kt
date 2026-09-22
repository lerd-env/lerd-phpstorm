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
import sh.lerd.ide.actions.SiteAction
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.site.LerdSiteService
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Puts the site's moving parts in the IDE's Services window, beside Docker and
 * the databases: the PHP runtime serving it, its workers, and the services it
 * uses, each startable where the IDE already keeps such things.
 *
 * The list is read from the same project service every other surface reads, so
 * the Services window can never disagree with the tool window.
 */
class LerdServiceViewContributor : ServiceViewContributor<SiteRuntime> {

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        SimpleServiceViewDescriptor("Lerd", AllIcons.Nodes.Services)

    override fun getServices(project: Project): List<SiteRuntime> {
        val state = LerdSiteService.getInstance(project).state
        val site = state.site ?: return emptyList()
        if (!state.daemonReachable) return emptyList()
        return SiteRuntimes.of(site, servicesOf(project))
    }

    override fun getServiceDescriptor(project: Project, service: SiteRuntime): ServiceViewDescriptor =
        RuntimeDescriptor(project, service)

    /**
     * The service list is only needed to colour the rows, so a failure here
     * leaves them uncoloured rather than emptying the node.
     */
    private fun servicesOf(project: Project) =
        (LerdSiteService.getInstance(project).client().services() as? LerdResult.Ok)?.value.orEmpty()

    private class RuntimeDescriptor(
        private val project: Project,
        private val runtime: SiteRuntime,
    ) : ServiceViewDescriptor {

        override fun getPresentation(): ItemPresentation = object : ItemPresentation {
            override fun getPresentableText(): String = runtime.name
            override fun getLocationString(): String = runtime.detail
            override fun getIcon(unused: Boolean): Icon = icon()
        }

        override fun getId(): String = "${runtime.kind}:${runtime.name}"

        override fun getContentComponent(): JComponent = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 16)
            add(
                JBLabel(
                    buildString {
                        append("<html><b>").append(runtime.name).append("</b><br>")
                        append(state()).append("<br>")
                        if (runtime.detail.isNotBlank()) append(runtime.detail)
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
            if (runtime.running) {
                runtime.stopAction?.let { group.add(action("Stop", AllIcons.Actions.Suspend, it)) }
                if (runtime.kind == SiteRuntime.Kind.RUNTIME) {
                    group.add(action("Restart", AllIcons.Actions.Restart, "restart"))
                }
            } else {
                runtime.startAction?.let { group.add(action("Start", AllIcons.Actions.Execute, it)) }
            }
            return group
        }

        private fun action(text: String, icon: Icon, verb: String): AnAction =
            object : AnAction(text, null, icon) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                override fun actionPerformed(e: AnActionEvent) {
                    if (runtime.kind == SiteRuntime.Kind.SERVICE) {
                        AppExecutorUtil.getAppExecutorService().execute {
                            LerdSiteService.getInstance(project).client().serviceAction(runtime.name, verb)
                            LerdSiteService.getInstance(project).refresh()
                        }
                    } else {
                        SiteAction.run(project, verb)
                    }
                }
            }

        private fun state(): String = when {
            runtime.failing -> "failed"
            runtime.running -> "running"
            else -> "stopped"
        }

        private fun icon(): Icon = when {
            runtime.failing -> AllIcons.General.Error
            runtime.running -> AllIcons.RunConfigurations.TestState.Run
            else -> AllIcons.RunConfigurations.TestIgnored
        }
    }
}

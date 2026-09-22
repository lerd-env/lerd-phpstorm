package sh.lerd.ide.actions

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.api.SiteCommand
import sh.lerd.ide.logs.LerdLogFilter
import sh.lerd.ide.logs.LogStream
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService

/**
 * Offers the site's own command set (artisan, console, composer, whatever the
 * framework definition declares) and streams the chosen one into a console
 * tab, stack traces linked like any other log.
 */
class RunCommandAction : AnAction("Run Lerd Command...") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val state = e.project?.let { LerdSiteService.getInstance(it).state }
        e.presentation.isEnabledAndVisible = state?.site != null && state.daemonReachable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = LerdSiteService.getInstance(project)
        val site = service.state.site ?: return

        AppExecutorUtil.getAppExecutorService().execute {
            val commands = service.client().commands(site.domain, site.branch)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                when (commands) {
                    is LerdResult.Ok -> choose(project, commands.value)
                    is LerdResult.Err -> Messages.showErrorDialog(project, commands.message, "Lerd")
                }
            }
        }
    }

    private fun choose(project: Project, commands: List<SiteCommand>) {
        if (commands.isEmpty()) {
            Messages.showInfoMessage(project, "This site declares no commands.", "Lerd")
            return
        }
        val step = object : BaseListPopupStep<SiteCommand>("Run Lerd Command", commands) {
            override fun getTextFor(value: SiteCommand): String =
                value.label.ifBlank { value.name } + "   " + value.command

            override fun onChosen(value: SiteCommand, finalChoice: Boolean): PopupStep<*>? {
                doFinalStep { confirmAndRun(project, value) }
                return PopupStep.FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project)
    }

    private fun confirmAndRun(project: Project, command: SiteCommand) {
        if (command.confirm) {
            val answer = Messages.showYesNoDialog(
                project,
                "${command.command}\n\nThis command is marked as destructive.",
                command.label.ifBlank { command.name },
                "Run",
                "Cancel",
                Messages.getWarningIcon(),
            )
            if (answer != Messages.YES) return
        }
        run(project, command)
    }

    private fun run(project: Project, command: SiteCommand) {
        val service = LerdSiteService.getInstance(project)
        val site = service.state.site ?: return

        val console = ConsoleViewImpl(project, true)
        console.addMessageFilter(LerdLogFilter(project) { service.state.site?.path })
        console.print("${command.command}\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        val window = ToolWindowManager.getInstance(project).getToolWindow("Lerd") ?: return
        val content = ContentFactory.getInstance()
            .createContent(console.component, command.label.ifBlank { command.name }, true)
        content.setDisposer(console)
        window.contentManager.addContent(content)
        window.contentManager.setSelectedContent(content)
        window.activate(null)

        val stream = LogStream(
            baseUrl = { LerdSettings.getInstance().baseUrl },
            path = service.client().commandRunPath(site.domain, command.name),
            onLine = { console.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT) },
            method = "POST",
            retry = false,
            onEvent = { event, data ->
                when (event) {
                    "stdout" -> console.print(data + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
                    "stderr", "error" -> console.print(data + "\n", ConsoleViewContentType.ERROR_OUTPUT)
                    "done" -> {
                        console.print("\nfinished\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        service.refresh()
                    }
                }
            },
        )
        Disposer.register(console) { stream.close() }
        stream.start()
    }
}

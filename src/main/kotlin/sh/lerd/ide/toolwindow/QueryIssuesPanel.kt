package sh.lerd.ide.toolwindow

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.api.LerdService
import sh.lerd.ide.db.DataSourceTarget
import sh.lerd.ide.db.DataSourceTargets
import sh.lerd.ide.db.QueryConsole
import sh.lerd.ide.logs.LerdLogFilter
import sh.lerd.ide.logs.QueryFinding
import sh.lerd.ide.logs.QueryFindings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdState
import sh.lerd.ide.site.LerdStateListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Only the queries worth fixing: repeats inside one request (N+1) and anything
 * over the slow threshold, read from real captured traffic rather than from the
 * code. The raw per-query stream lives in the Dumps tab's Queries lens; this is
 * the verdict on it. Every finding carries the application line it came from,
 * which in an IDE means one click to the fix.
 */
class QueryIssuesPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val findingsList = JBList(CollectionListModel<QueryFinding>())
    private val console: ConsoleView = ConsoleViewImpl(project, true)
    private val status = JBLabel().apply { foreground = JBColor.GRAY }
    private var services: List<LerdService> = emptyList()

    init {
        Disposer.register(this, console)
        (console as ConsoleViewImpl).addMessageFilter(
            LerdLogFilter(project) { LerdSiteService.getInstance(project).state.site?.path },
        )

        findingsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        findingsList.cellRenderer = FindingRenderer()
        findingsList.addListSelectionListener { if (!it.valueIsAdjusting) showSelected() }
        object : com.intellij.ui.DoubleClickListener() {
            override fun onDoubleClick(event: java.awt.event.MouseEvent): Boolean {
                openInEditor()
                return true
            }
        }.installOn(findingsList)

        val splitter = OnePixelSplitter(false, 0.45f)
        splitter.firstComponent = JBScrollPane(findingsList).apply { border = JBUI.Borders.empty() }
        splitter.secondComponent = console.component

        add(toolbar(), BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { if (it.daemonReachable) refresh() },
        )
        refresh()
    }

    private fun toolbar(): JComponent {
        val group = DefaultActionGroup(
            object : AnAction("Refresh", "Re-read the captured traffic", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = refresh()
            },
            object : AnAction(
                "Open in SQL Editor",
                "Open the query against this site's database",
                AllIcons.Actions.Execute,
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = findingsList.selectedValue != null
                }

                override fun actionPerformed(e: AnActionEvent) = openInEditor()
            },
        )
        val actions = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        actions.targetComponent = this

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(4, 8)
                    add(status, BorderLayout.CENTER)
                },
                BorderLayout.WEST,
            )
            add(actions.component, BorderLayout.EAST)
        }
    }

    private fun refresh() {
        val state: LerdState = LerdSiteService.getInstance(project).state
        val site = state.site
        if (site == null || !state.daemonReachable) {
            status.text = if (site == null) "no Lerd site for this project" else "Lerd is stopped"
            (findingsList.model as CollectionListModel<QueryFinding>).removeAll()
            return
        }

        status.text = "reading captured traffic"
        AppExecutorUtil.getAppExecutorService().execute {
            val client = LerdSiteService.getInstance(project).client()
            val known = (client.services() as? LerdResult.Ok)?.value.orEmpty()
                .filter { it.name in site.site.services }
            val report = client.analyzeQueries(site.domain, site.branch)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                services = known
                when (report) {
                    is LerdResult.Ok -> {
                        val findings = QueryFindings.of(report.value)
                        (findingsList.model as CollectionListModel<QueryFinding>).replaceAll(findings)
                        status.text = when {
                            report.value.summary.requestsAnalyzed == 0 ->
                                "nothing captured yet - load the site in a browser"

                            findings.isEmpty() ->
                                "${report.value.summary.requestsAnalyzed} requests, nothing to flag"

                            else ->
                                "${findings.size} findings across ${report.value.summary.requestsAnalyzed} requests"
                        }
                        if (findings.isNotEmpty()) findingsList.selectedIndex = 0 else console.clear()
                    }

                    is LerdResult.Err -> status.text = report.message
                }
            }
        }
    }

    /** The site's own database, which is what these queries actually ran against. */
    private fun databaseTarget(): DataSourceTarget? {
        val site = LerdSiteService.getInstance(project).state.site ?: return null
        return services.firstNotNullOfOrNull {
            DataSourceTargets.of(it.name, it.connectionUrl, site.site.database)
        }
    }

    private fun openInEditor() {
        val finding = findingsList.selectedValue ?: return
        QueryConsole.open(project, databaseTarget(), finding.kind, finding.request, finding.sql)
    }

    private fun showSelected() {
        val finding = findingsList.selectedValue ?: return
        console.clear()
        console.print("${finding.kind}  ${finding.request}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        finding.caller?.let {
            // Printed as file:line so the console's own filter links it.
            console.print("${it.file}:${it.line}\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
        console.print("\n${finding.detail}\n", ConsoleViewContentType.NORMAL_OUTPUT)
        console.showFromTop()
    }

    override fun dispose() = Unit

    private inner class FindingRenderer : javax.swing.ListCellRenderer<QueryFinding> {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out QueryFinding>,
            value: QueryFinding?,
            index: Int,
            selected: Boolean,
            focused: Boolean,
        ): Component {
            val row = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(6, 8)
                background = if (selected) list.selectionBackground else list.background
                isOpaque = true
            }
            if (value == null) return row

            val text = JPanel(GridLayout(2, 1, 0, JBUI.scale(2))).apply { isOpaque = false }
            text.add(
                JBLabel(value.title).apply {
                    foreground = if (selected) list.selectionForeground else list.foreground
                },
            )
            text.add(
                JBLabel(value.request.ifBlank { "-" }).apply {
                    foreground = JBColor.GRAY
                    font = JBUI.Fonts.smallFont()
                },
            )

            row.add(
                JBLabel(if (value.kind == "slow") AllIcons.General.Warning else AllIcons.General.Error)
                    .apply { border = JBUI.Borders.emptyRight(8) },
                BorderLayout.WEST,
            )
            row.add(text, BorderLayout.CENTER)
            return row
        }
    }
}

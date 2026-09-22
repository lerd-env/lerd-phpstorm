package sh.lerd.ide.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import sh.lerd.ide.actions.SiteAction
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.api.LerdService
import sh.lerd.ide.db.DataSourceTargets
import sh.lerd.ide.db.DatabaseConnector
import sh.lerd.ide.services.ServiceClients
import sh.lerd.ide.services.TerminalLauncher
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdState
import sh.lerd.ide.site.LerdStateListener
import sh.lerd.ide.site.ResolvedSite
import sh.lerd.ide.site.SiteWorkers
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The site overview: what this project runs on, and the switches that change
 * it. Laid out as a label/value form with an action bar on top, the shape
 * every other JetBrains dashboard uses, so nothing here needs explaining.
 */
class SitePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val body = JPanel(BorderLayout())

    private var phpVersions: List<String> = emptyList()
    private var nodeVersions: List<String> = emptyList()
    private var allServices: List<LerdService> = emptyList()

    init {
        add(toolbar(), BorderLayout.NORTH)
        add(JBScrollPane(body).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { render(it) },
        )
        loadCatalogues()
        render(LerdSiteService.getInstance(project).state)
    }

    // ---------------------------------------------------------------- toolbar

    private fun toolbar(): JComponent {
        val group = DefaultActionGroup(
            action("Refresh", AllIcons.Actions.Refresh) {
                loadCatalogues()
                LerdSiteService.getInstance(project).refresh()
            },
            action("Open Site", AllIcons.General.Web) { openSite() },
            action("Restart", AllIcons.Actions.Restart) { SiteAction.run(project, "restart") },
            action("HTTPS", AllIcons.General.InspectionsOK) { toggleTls() },
            action("Run Command", AllIcons.Actions.Execute) { runCommand() },
            action("Container Shell", AllIcons.Debugger.Console) { containerShell() },
            action("Doctor", AllIcons.General.InspectionsEye) { showDoctor() },
        )
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = this
        toolbar.component.border = JBUI.Borders.customLineBottom(JBColor.border())
        return toolbar.component
    }

    /** Toolbar buttons show their label the way the platform's dashboards do. */
    private fun action(text: String, icon: Icon, run: () -> Unit): AnAction =
        object : AnAction(text, null, icon) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                val state = LerdSiteService.getInstance(project).state
                e.presentation.isEnabled = state.site != null && state.daemonReachable
                e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
                if (text == "HTTPS") {
                    e.presentation.text = if (state.site?.site?.tls == true) "Disable HTTPS" else "Enable HTTPS"
                }
            }

            override fun actionPerformed(e: AnActionEvent) = run()
        }

    // ------------------------------------------------------------------- body

    private fun render(state: LerdState) {
        val site = state.site
        val form = when {
            site == null -> message("This project is not a Lerd site.")
            !state.daemonReachable -> message("${site.displayName} is a Lerd site, but Lerd is not running.")
            else -> form(site)
        }
        body.removeAll()
        body.add(form, BorderLayout.NORTH)
        body.revalidate()
        body.repaint()
    }

    private fun message(text: String): JComponent = JBLabel(text, SwingConstants.LEFT).apply {
        border = JBUI.Borders.empty(16)
        foreground = JBColor.GRAY
    }

    /**
     * The three sections side by side, reflowing to fewer columns as the tool
     * window narrows, because it is as often a strip at the side as a band
     * along the bottom.
     */
    private fun form(site: ResolvedSite): JComponent {
        val sections = buildList {
            add(section(details(site)))
            workersSection(site)?.let { add(section(it, "Workers")) }
            servicesSection(site)?.let { add(section(it, "Services")) }
        }
        return ResponsiveColumns(sections)
    }

    private fun section(content: JComponent, title: String? = null): JComponent =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(12, 16, 12, 16)
            title?.let {
                add(
                    JBLabel(it).apply {
                        font = font.deriveFont(java.awt.Font.BOLD)
                        border = JBUI.Borders.emptyBottom(6)
                    },
                    BorderLayout.NORTH,
                )
            }
            add(content, BorderLayout.CENTER)
        }

    private fun details(site: ResolvedSite): JComponent = panel {
        row("Site:") {
            link(site.domain) { openSite() }
            if (site.site.paused) label("paused").applyToComponent { foreground = JBColor.GRAY }
        }
        site.frameworkLabel?.let { row("Framework:") { label(it) } }
        site.branch?.let { row("Branch:") { label(it) } }
        site.path?.let { row("Path:") { label(shorten(it)).applyToComponent { foreground = JBColor.GRAY } } }
        row("PHP:") {
            versionCombo(phpVersions, site.phpVersion) { chosen ->
                SiteAction.run(project, "php") { mapOf("version" to chosen) }
            }
        }
        row("Node:") {
            versionCombo(nodeVersions, site.nodeVersion) { chosen ->
                SiteAction.run(project, "node") { mapOf("version" to chosen) }
            }
        }
    }

    private fun workersSection(site: ResolvedSite): JComponent? {
        val workers = SiteWorkers.of(site)
        if (workers.isEmpty()) return null
        return panel {
            workers.forEach { worker ->
                row {
                    cell(statusDot(worker.running, worker.failing)).gap(RightGap.SMALL)
                    label(worker.label).widthGroup("worker")
                    label(stateWord(worker.running, worker.failing))
                        .applyToComponent { foreground = JBColor.GRAY }
                        .gap(RightGap.COLUMNS)
                    link(if (worker.running) "Stop" else "Start") {
                        SiteAction.run(project, if (worker.running) worker.stopAction else worker.startAction)
                    }
                }
            }
        }
    }

    private fun servicesSection(site: ResolvedSite): JComponent? {
        val used = allServices.filter { it.name in site.site.services }
        if (used.isEmpty()) return null
        return panel {
            used.forEach { service ->
                row {
                    cell(statusDot(service.running, false)).gap(RightGap.SMALL)
                    label(service.name).widthGroup("service")
                    label(service.port?.toString().orEmpty())
                        .applyToComponent { foreground = JBColor.GRAY }
                        .gap(RightGap.COLUMNS)

                    val target = DataSourceTargets.of(service.name, service.connectionUrl, site.site.database)
                    if (target != null && service.running) {
                        link("Database") { DatabaseConnector.open(project, target) }.gap(RightGap.SMALL)
                    }
                    if (ServiceClients.of(service.connectionUrl, site.site.database) != null && service.running) {
                        link("Shell") { shell(site, service) }.gap(RightGap.SMALL)
                    }
                    service.dashboard?.takeIf { it.isNotBlank() && service.running }?.let { dashboard ->
                        link("Admin UI") {
                            BrowserUtil.browse(LerdSettings.getInstance().baseUrl.trimEnd('/') + dashboard)
                        }.gap(RightGap.SMALL)
                    }
                    link(if (service.running) "Stop" else "Start") {
                        runService(service.name, if (service.running) "stop" else "start")
                    }
                }
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Row.versionCombo(
        versions: List<String>,
        current: String?,
        apply: (String) -> Unit,
    ) = comboBox(versions.ifEmpty { listOfNotNull(current) })
        .applyToComponent {
            selectedItem = current
            isEnabled = versions.size > 1
            addActionListener {
                val chosen = selectedItem as? String ?: return@addActionListener
                if (chosen != current) apply(chosen)
            }
        }
        .align(AlignX.LEFT)

    private fun statusDot(running: Boolean, failing: Boolean): JBLabel = JBLabel("●").apply {
        foreground = when {
            failing -> JBColor.RED
            running -> RUNNING
            else -> JBColor.GRAY
        }
    }

    private fun stateWord(running: Boolean, failing: Boolean): String = when {
        failing -> "failed"
        running -> "running"
        else -> "stopped"
    }

    private fun shorten(path: String): String {
        val home = System.getProperty("user.home")
        return if (path.startsWith(home)) "~" + path.removePrefix(home) else path
    }

    // ---------------------------------------------------------------- actions

    private fun site(): ResolvedSite? = LerdSiteService.getInstance(project).state.site

    private fun openSite() {
        val site = site() ?: return
        BrowserUtil.browse("${if (site.site.tls) "https" else "http"}://${site.domain}")
    }

    private fun toggleTls() {
        val site = site() ?: return
        SiteAction.run(project, if (site.site.tls) "unsecure" else "secure")
    }

    private fun runCommand() {
        val action = ActionManager.getInstance().getAction("sh.lerd.ide.RunCommand") ?: return
        ActionUtil.invokeAction(action, this, ActionPlaces.TOOLWINDOW_CONTENT, null, null)
    }

    /** A shell inside the site's own container, which is where its php lives. */
    private fun containerShell() {
        val site = site() ?: return
        // lerd shell takes no site argument; it resolves the site from the
        // working directory, which for a worktree is the worktree's own path.
        TerminalLauncher.run(project, "${site.displayName} shell", listOf(lerdBinary(), "shell"), site.path)
    }

    private fun lerdBinary(): String {
        val installed = java.nio.file.Path.of(System.getProperty("user.home"), ".local", "bin", "lerd")
        return if (java.nio.file.Files.isExecutable(installed)) installed.toString() else "lerd"
    }

    private fun shell(site: ResolvedSite, service: LerdService) {
        val client = ServiceClients.of(service.connectionUrl, site.site.database) ?: return
        TerminalLauncher.run(project, service.name, client, site.path)
    }

    private fun runService(name: String, action: String) {
        AppExecutorUtil.getAppExecutorService().execute {
            LerdSiteService.getInstance(project).client().serviceAction(name, action)
            loadCatalogues()
            LerdSiteService.getInstance(project).refresh()
        }
    }

    private fun showDoctor() {
        val site = site() ?: return
        AppExecutorUtil.getAppExecutorService().execute {
            val report = LerdSiteService.getInstance(project).client().doctor(site.domain, site.branch)
            val text = when (report) {
                is LerdResult.Ok -> report.value.checks
                    .filter { it.status != "ok" }
                    .joinToString("\n") { "${it.status.uppercase()}  ${it.label}: ${it.detail.orEmpty()}" }
                    .ifBlank { "Every check passed." }

                is LerdResult.Err -> report.message
            }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) Messages.showInfoMessage(project, text, "Site Doctor")
            }
        }
    }

    /** Versions and the service list change rarely, so they are fetched on demand. */
    private fun loadCatalogues() {
        AppExecutorUtil.getAppExecutorService().execute {
            val client = LerdSiteService.getInstance(project).client()
            val php = (client.phpVersions() as? LerdResult.Ok)?.value.orEmpty()
            val node = (client.nodeVersions() as? LerdResult.Ok)?.value.orEmpty()
            val svc = (client.services() as? LerdResult.Ok)?.value.orEmpty()
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                phpVersions = php
                nodeVersions = node
                allServices = svc
                render(LerdSiteService.getInstance(project).state)
            }
        }
    }

    override fun dispose() = Unit

    private companion object {
        val RUNNING = JBColor(0x3C9B4A, 0x57965C)
    }
}

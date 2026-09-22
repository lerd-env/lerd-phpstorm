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
import com.intellij.ui.OnePixelSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.logs.LerdLogFilter
import sh.lerd.ide.logs.LogGroup
import sh.lerd.ide.logs.LogGrouping
import sh.lerd.ide.logs.LogSource
import sh.lerd.ide.logs.LogSources
import sh.lerd.ide.logs.LogStream
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdState
import sh.lerd.ide.site.LerdStateListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Logs for the open site, in the two shapes they actually come in.
 *
 * A framework log is a list of distinct errors with the newest first and the
 * full record beside it, because a log with one failure in it two hundred times
 * is one problem, not two hundred lines. Everything else is a live tail, and
 * both panes are consoles, so a path in a stack trace is a link either way.
 */
class LogsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val picker = JComboBox<LogSource>()
    private val search = SearchTextField(false)
    private val status = JBLabel().apply { foreground = JBColor.GRAY }

    private val console: ConsoleView = ConsoleViewImpl(project, true)
    private val groupList = JBList(CollectionListModel<LogGroup>())
    private val cards = JPanel(BorderLayout())
    private val splitter = OnePixelSplitter(false, 0.38f)

    private var stream: LogStream? = null
    private var sources: List<LogSource> = emptyList()
    private var groups: List<LogGroup> = emptyList()
    private var selecting = false

    init {
        Disposer.register(this, console)
        (console as ConsoleViewImpl).addMessageFilter(
            LerdLogFilter(project) { LerdSiteService.getInstance(project).state.site?.path },
        )

        picker.renderer = SimpleListCellRenderer.create<LogSource> { label, value, _ ->
            label.text = value?.label.orEmpty()
        }
        picker.addActionListener {
            if (!selecting) (picker.selectedItem as? LogSource)?.let(::attach)
        }
        search.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
            },
        )

        groupList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        groupList.cellRenderer = GroupRenderer()
        groupList.addListSelectionListener { if (!it.valueIsAdjusting) showSelectedGroup() }

        splitter.firstComponent = JBScrollPane(groupList).apply { border = JBUI.Borders.empty() }
        splitter.secondComponent = console.component

        add(toolbar(), BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { reload(it) },
        )
        reload(LerdSiteService.getInstance(project).state)
    }

    private fun toolbar(): JComponent {
        val group = DefaultActionGroup(
            simple("Refresh", AllIcons.Actions.Refresh) {
                (picker.selectedItem as? LogSource)?.let(::attach)
            },
            simple("Clear", AllIcons.Actions.GC) { console.clear() },
        )
        val actions = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        actions.targetComponent = this

        val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            add(picker)
            add(search)
            add(status)
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
            add(left, BorderLayout.WEST)
            add(actions.component, BorderLayout.EAST)
        }
    }

    private fun simple(text: String, icon: javax.swing.Icon, run: () -> Unit): AnAction =
        object : AnAction(text, null, icon) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) = run()
        }

    // ------------------------------------------------------------------ state

    private fun reload(state: LerdState) {
        val site = state.site
        if (site == null || !state.daemonReachable) {
            detach()
            selecting = true
            picker.model = DefaultComboBoxModel()
            selecting = false
            sources = emptyList()
            status.text = if (site == null) "no Lerd site for this project" else "Lerd is stopped"
            showConsoleOnly()
            return
        }
        status.text = ""

        AppExecutorUtil.getAppExecutorService().execute {
            val files = if (site.site.hasAppLogs) {
                (LerdSiteService.getInstance(project).client().appLogFiles(site.domain, site.branch)
                    as? LerdResult.Ok)?.value.orEmpty().map { it.name }
            } else {
                emptyList()
            }
            val next = LogSources.of(site, files)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) applySources(next)
            }
        }
    }

    private fun applySources(next: List<LogSource>) {
        if (next.map { it.id } == sources.map { it.id }) return
        val keep = (picker.selectedItem as? LogSource)?.id
        sources = next
        selecting = true
        picker.model = DefaultComboBoxModel(next.toTypedArray())
        val restored = next.firstOrNull { it.id == keep } ?: next.firstOrNull()
        picker.selectedItem = restored
        selecting = false
        restored?.let(::attach)
    }

    private fun attach(source: LogSource) {
        detach()
        console.clear()
        val service = LerdSiteService.getInstance(project)
        val site = service.state.site ?: return

        when (source.kind) {
            LogSource.Kind.APP -> {
                showSplit()
                status.text = "loading"
                AppExecutorUtil.getAppExecutorService().execute {
                    val entries = service.client()
                        .appLogEntries(site.domain, source.path, LerdSettings.getInstance().logBufferLines, site.branch)
                    val next = when (entries) {
                        is LerdResult.Ok -> LogGrouping.of(entries.value)
                        is LerdResult.Err -> emptyList()
                    }
                    val error = (entries as? LerdResult.Err)?.message
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        groups = next
                        applyFilter()
                        status.text = error ?: summary(next)
                    }
                }
            }

            LogSource.Kind.STREAM -> {
                showConsoleOnly()
                status.text = ""
                stream = LogStream(
                    baseUrl = { LerdSettings.getInstance().baseUrl },
                    path = source.path,
                    onLine = { line -> if (matches(line)) printLine(line) },
                ).also { it.start() }
            }
        }
    }

    private fun summary(groups: List<LogGroup>): String = when {
        groups.isEmpty() -> "nothing logged"
        else -> "${groups.size} distinct, ${groups.sumOf { it.count }} total"
    }

    private fun matches(line: String): Boolean {
        val needle = search.text.trim()
        return needle.isEmpty() || line.contains(needle, ignoreCase = true)
    }

    private fun printLine(line: String) = console.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT)

    private fun applyFilter() {
        if (currentSource()?.kind != LogSource.Kind.APP) return
        val needle = search.text.trim()
        val shown = if (needle.isEmpty()) {
            groups
        } else {
            groups.filter {
                it.title.contains(needle, true) || it.latest.detail.orEmpty().contains(needle, true)
            }
        }
        (groupList.model as CollectionListModel<LogGroup>).replaceAll(shown)
        if (shown.isNotEmpty()) groupList.selectedIndex = 0 else console.clear()
    }

    private fun showSelectedGroup() {
        val group = groupList.selectedValue ?: return
        console.clear()
        console.print("${group.level}  ${group.title}\n", ConsoleViewContentType.ERROR_OUTPUT)
        console.print(
            "seen ${group.count}x, first ${group.firstSeen}, last ${group.lastSeen}\n\n",
            ConsoleViewContentType.SYSTEM_OUTPUT,
        )
        console.print(
            (group.latest.detail ?: group.latest.message) + "\n",
            ConsoleViewContentType.NORMAL_OUTPUT,
        )
        console.showFromTop()
    }

    private fun currentSource(): LogSource? = picker.selectedItem as? LogSource

    private fun showSplit() {
        cards.removeAll()
        cards.add(splitter, BorderLayout.CENTER)
        cards.revalidate()
        cards.repaint()
    }

    private fun showConsoleOnly() {
        cards.removeAll()
        cards.add(console.component, BorderLayout.CENTER)
        cards.revalidate()
        cards.repaint()
    }

    private fun detach() {
        stream?.close()
        stream = null
    }

    override fun dispose() = detach()

    /** Two lines per row, the way the platform renders a problem list. */
    private inner class GroupRenderer : javax.swing.ListCellRenderer<LogGroup> {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out LogGroup>,
            value: LogGroup?,
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

            val text = JPanel(java.awt.GridLayout(2, 1, 0, JBUI.scale(2))).apply { isOpaque = false }
            text.add(
                JBLabel(value.title).apply {
                    foreground = if (selected) list.selectionForeground else list.foreground
                },
            )
            text.add(
                JBLabel("first ${value.firstSeen}, last ${value.lastSeen}").apply {
                    foreground = JBColor.GRAY
                    font = JBUI.Fonts.smallFont()
                },
            )

            row.add(levelIcon(value.level), BorderLayout.WEST)
            row.add(text, BorderLayout.CENTER)
            if (value.count > 1) {
                row.add(
                    JBLabel(value.count.toString()).apply {
                        foreground = JBColor.GRAY
                        border = JBUI.Borders.emptyLeft(8)
                    },
                    BorderLayout.EAST,
                )
            }
            return row
        }

        private fun levelIcon(level: String): JComponent = JBLabel(
            when (level) {
                "ERROR", "CRITICAL", "ALERT", "EMERGENCY" -> AllIcons.General.Error
                "WARNING" -> AllIcons.General.Warning
                else -> AllIcons.General.Information
            },
        ).apply { border = JBUI.Borders.emptyRight(8) }
    }
}

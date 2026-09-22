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
import com.intellij.openapi.actionSystem.ToggleAction
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
import sh.lerd.ide.logs.DumpRow
import sh.lerd.ide.logs.DumpRows
import sh.lerd.ide.logs.LerdLogFilter
import sh.lerd.ide.settings.LerdSettings
import sh.lerd.ide.site.LerdSiteService
import sh.lerd.ide.site.LerdStateListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * What the running site dumped: dump(), dd(), queries, jobs, whatever the
 * bridge captured, newest first, with the line that produced it linked.
 */
class DumpsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val rows = JBList(CollectionListModel<DumpRow>())
    private val console: ConsoleView = ConsoleViewImpl(project, true)
    private val status = JBLabel().apply { foreground = JBColor.GRAY }
    private val search = com.intellij.ui.SearchTextField(false)
    private val lensTabs = com.intellij.ui.components.JBTabbedPane()
    private val body = JPanel(BorderLayout())
    private var includeTests = false
    private var captured: List<sh.lerd.ide.api.DumpEvent> = emptyList()
    private var lenses: List<sh.lerd.ide.logs.DumpLens> = emptyList()
    private var shown: List<sh.lerd.ide.api.DumpEvent> = emptyList()
    private var switching = false

    init {
        Disposer.register(this, console)
        (console as ConsoleViewImpl).addMessageFilter(
            LerdLogFilter(project) { LerdSiteService.getInstance(project).state.site?.path },
        )

        rows.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rows.cellRenderer = DumpRenderer()
        rows.addListSelectionListener { if (!it.valueIsAdjusting) showSelected() }

        val splitter = OnePixelSplitter(false, 0.42f)
        splitter.firstComponent = JBScrollPane(rows).apply { border = JBUI.Borders.empty() }
        splitter.secondComponent = console.component
        body.add(splitter, BorderLayout.CENTER)

        lensTabs.tabComponentInsets = JBUI.emptyInsets()
        lensTabs.addChangeListener { if (!switching) applyLens() }

        search.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = applyLens()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = applyLens()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = applyLens()
            },
        )

        add(toolbar(), BorderLayout.NORTH)
        add(lensTabs, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            LerdSiteService.TOPIC,
            LerdStateListener { if (it.daemonReachable) refresh() },
        )
        refresh()
    }

    private fun toolbar(): JComponent {
        val group = DefaultActionGroup(
            object : AnAction("Refresh", "Re-read what the site dumped", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = refresh()
            },
            object : ToggleAction("Include Test Runs", null, AllIcons.Nodes.Test) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun isSelected(e: AnActionEvent): Boolean = includeTests
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    includeTests = state
                    rebuildLenses()
                }
            },
        )
        val actions = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        actions.targetComponent = this

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
            add(
                JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
                    isOpaque = false
                    add(search)
                    add(status)
                },
                BorderLayout.WEST,
            )
            add(actions.component, BorderLayout.EAST)
        }
    }

    private fun refresh() {
        val state = LerdSiteService.getInstance(project).state
        val site = state.site
        if (site == null || !state.daemonReachable) {
            status.text = if (site == null) "no Lerd site for this project" else "Lerd is stopped"
            captured = emptyList()
            rebuildLenses()
            return
        }

        AppExecutorUtil.getAppExecutorService().execute {
            val limit = LerdSettings.getInstance().logBufferLines
            val events = LerdSiteService.getInstance(project).client()
                .dumps(site.domain, limit, site.branch)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                when (events) {
                    is LerdResult.Ok -> {
                        captured = events.value
                        rebuildLenses()
                    }

                    is LerdResult.Err -> status.text = events.message
                }
            }
        }
    }

    /**
     * One tab per lens that has something in it. The list and detail pane are
     * shared and move into whichever tab is selected, so eleven lenses cost one
     * list rather than eleven.
     */
    private fun rebuildLenses() {
        val next = sh.lerd.ide.logs.DumpLenses.of(captured, includeTests)
        val keep = selectedKind()

        switching = true
        lensTabs.removeAll()
        lenses = next
        next.forEach { lens ->
            lensTabs.addTab("${lens.label}  ${lens.count}", JPanel(BorderLayout()))
        }
        val index = next.indexOfFirst { it.kind == keep }.takeIf { it >= 0 } ?: 0
        if (next.isNotEmpty()) lensTabs.selectedIndex = index
        switching = false

        applyLens()
    }

    private fun selectedKind(): String? =
        lenses.getOrNull(lensTabs.selectedIndex)?.kind

    private fun applyLens() {
        val kind = selectedKind()
        // The shared list lives in whichever tab is showing.
        (lensTabs.selectedComponent as? JPanel)?.let { host ->
            if (body.parent !== host) {
                (body.parent as? JPanel)?.remove(body)
                host.add(body, BorderLayout.CENTER)
                host.revalidate()
            }
        }

        val matching = DumpRows.eventsOf(captured, kind, search.text, includeTests)
        shown = matching
        val next = matching.map(DumpRows::row)
        (rows.model as CollectionListModel<DumpRow>).replaceAll(next)
        status.text = when {
            captured.isEmpty() -> "nothing captured yet - dump() something and load the site"
            next.isEmpty() -> "nothing matches"
            else -> "${next.size} shown"
        }
        if (next.isNotEmpty()) rows.selectedIndex = 0 else console.clear()
    }

    private fun showSelected() {
        val index = rows.selectedIndex.takeIf { it >= 0 } ?: return
        val row = rows.model.getElementAt(index) ?: return
        val event = shown.getOrNull(index)
        console.clear()
        console.print(
            listOf(row.kind, row.where, row.timestamp).filter { it.isNotBlank() }.joinToString("  ") + "\n\n",
            ConsoleViewContentType.SYSTEM_OUTPUT,
        )

        if (event == null) {
            console.print(row.detail + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
            return
        }
        sh.lerd.ide.logs.DumpDetail.of(event).forEach { line ->
            console.print(line.text + "\n", contentType(line.style))
        }
    }

    /** Vendor frames stay readable but recede; the app frames are the point. */
    private fun contentType(style: sh.lerd.ide.logs.DumpDetail.Style): ConsoleViewContentType = when (style) {
        sh.lerd.ide.logs.DumpDetail.Style.HEADING -> ConsoleViewContentType.SYSTEM_OUTPUT
        sh.lerd.ide.logs.DumpDetail.Style.ORIGIN -> ConsoleViewContentType.ERROR_OUTPUT
        sh.lerd.ide.logs.DumpDetail.Style.VENDOR_FRAME -> ConsoleViewContentType.SYSTEM_OUTPUT
        else -> ConsoleViewContentType.NORMAL_OUTPUT
    }

    override fun dispose() = Unit

    private inner class DumpRenderer : javax.swing.ListCellRenderer<DumpRow> {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out DumpRow>,
            value: DumpRow?,
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
                JBLabel(listOf(value.kind, value.where).filter { it.isNotBlank() }.joinToString("  ")).apply {
                    foreground = JBColor.GRAY
                    font = JBUI.Fonts.smallFont()
                },
            )
            row.add(text, BorderLayout.CENTER)
            sh.lerd.ide.logs.DumpTimes.short(value.timestamp).takeIf { it.isNotBlank() }?.let { at ->
                row.add(
                    JBLabel(at).apply {
                        foreground = JBColor.GRAY
                        font = JBUI.Fonts.smallFont()
                        border = JBUI.Borders.emptyLeft(8)
                        verticalAlignment = javax.swing.SwingConstants.TOP
                    },
                    BorderLayout.EAST,
                )
            }
            return row
        }
    }
}

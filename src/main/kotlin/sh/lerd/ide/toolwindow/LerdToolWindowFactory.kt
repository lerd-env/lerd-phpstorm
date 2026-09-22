package sh.lerd.ide.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

class LerdToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val site = SitePanel(project)
        val logs = LogsPanel(project)
        val queryIssues = QueryIssuesPanel(project)
        toolWindow.contentManager.addContent(tab(site, "Site"))
        toolWindow.contentManager.addContent(tab(logs, "Logs"))
        toolWindow.contentManager.addContent(tab(queryIssues, "N+1 & Slow"))
        toolWindow.contentManager.addContent(tab(DumpsPanel(project), "Dumps"))
    }

    private fun <T> tab(panel: T, title: String): Content
        where T : JComponent, T : com.intellij.openapi.Disposable =
        ContentFactory.getInstance().createContent(panel, title, false).also {
            it.setDisposer(panel)
            // Only a command run's tab is closeable; closing Site or Logs would
            // leave the tool window empty with no way back.
            it.isCloseable = false
        }
}

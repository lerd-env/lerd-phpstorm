package sh.lerd.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import sh.lerd.ide.settings.LerdSettings

class OpenDashboardAction : AnAction("Open Lerd Dashboard") {
    override fun actionPerformed(e: AnActionEvent) =
        BrowserUtil.browse(LerdSettings.getInstance().baseUrl)
}

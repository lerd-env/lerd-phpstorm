package sh.lerd.ide.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class LerdStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LerdStatusWidget.WIDGET_ID

    override fun getDisplayName(): String = "Lerd"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = LerdStatusWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = Unit

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

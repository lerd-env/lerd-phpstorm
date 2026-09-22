package sh.lerd.ide.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import sh.lerd.ide.api.LerdClient

class LerdConfigurable : BoundConfigurable("Lerd") {
    override fun createPanel(): DialogPanel {
        val settings = LerdSettings.getInstance()
        return panel {
            row("Dashboard address:") {
                textField()
                    .bindText({ settings.baseUrl }, { settings.baseUrl = it })
                    .comment("Where the Lerd dashboard listens. The default is ${LerdClient.DEFAULT_BASE_URL}.")
            }
            row("Log lines to keep:") {
                intTextField(200..50_000)
                    .bindIntText({ settings.logBufferLines }, { settings.logBufferLines = it })
            }
        }
    }
}

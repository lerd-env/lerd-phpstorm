package sh.lerd.ide.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import sh.lerd.ide.api.LerdClient

@State(name = "LerdSettings", storages = [Storage("lerd.xml")])
class LerdSettings : PersistentStateComponent<LerdSettings.State> {
    data class State(
        var baseUrl: String = LerdClient.DEFAULT_BASE_URL,
        var logBufferLines: Int = 2000,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var baseUrl: String
        get() = state.baseUrl.takeIf { it.isNotBlank() } ?: LerdClient.DEFAULT_BASE_URL
        set(value) {
            state.baseUrl = value
        }

    var logBufferLines: Int
        get() = state.logBufferLines.coerceIn(200, 50_000)
        set(value) {
            state.logBufferLines = value
        }

    companion object {
        fun getInstance(): LerdSettings = ApplicationManager.getApplication().getService(LerdSettings::class.java)
    }
}

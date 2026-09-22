package sh.lerd.ide.site

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import sh.lerd.ide.api.LerdClient
import sh.lerd.ide.api.LerdEvents
import sh.lerd.ide.api.LerdResult
import sh.lerd.ide.api.LerdStatus
import sh.lerd.ide.settings.LerdSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the plugin knows about the open project right now. Every surface reads
 * this one object, so they cannot disagree with each other.
 */
data class LerdState(
    val daemonReachable: Boolean = false,
    val site: ResolvedSite? = null,
    val status: LerdStatus? = null,
) {
    val isLerdProject: Boolean get() = site != null
}

fun interface LerdStateListener {
    fun stateChanged(state: LerdState)
}

@Service(Service.Level.PROJECT)
class LerdSiteService(private val project: Project) : Disposable {
    private val client = LerdClient { LerdSettings.getInstance().baseUrl }
    private val refreshing = AtomicBoolean(false)

    @Volatile
    var state: LerdState = LerdState()
        private set

    private val events = LerdEvents(
        baseUrl = { LerdSettings.getInstance().baseUrl },
        scheduler = AppExecutorUtil.getAppScheduledExecutorService(),
        onChange = { kind -> onEvent(kind) },
    )

    init {
        Disposer.register(this, client::close)
        Disposer.register(this, events::close)
        events.start()
        refresh()
    }

    /** Re-reads the daemon off the EDT and publishes the result. */
    fun refresh() {
        if (!refreshing.compareAndSet(false, true)) return
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                publish(read())
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun read(): LerdState {
        val base = project.basePath ?: return LerdState()
        val projectPath = Path.of(base)

        val sites = client.sites()
        if (sites is LerdResult.Ok) {
            val status = client.status().valueOrNull()
            return LerdState(
                daemonReachable = true,
                site = SiteResolver.resolve(sites.value, projectPath),
                status = status,
            )
        }

        // lerd is stopped. The registry still knows whether this project is one
        // of its sites, which is the difference between "start lerd" and
        // staying quiet.
        return LerdState(
            daemonReachable = false,
            site = SiteResolver.resolve(SitesRegistry.read(), projectPath),
        )
    }

    private fun onEvent(kind: String) {
        when (kind) {
            LerdEvents.KIND_DISCONNECTED -> publish(state.copy(daemonReachable = false))
            else -> refresh()
        }
    }

    private fun publish(next: LerdState) {
        if (next == state) return
        state = next
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(TOPIC).stateChanged(next)
            }
        }) { project.isDisposed }
    }

    fun client(): LerdClient = client

    fun setIdeVisible(visible: Boolean) = events.setVisible(visible)

    override fun dispose() = Unit

    companion object {
        val TOPIC: Topic<LerdStateListener> = Topic.create("lerd state", LerdStateListener::class.java)

        fun getInstance(project: Project): LerdSiteService =
            project.getService(LerdSiteService::class.java)
    }
}

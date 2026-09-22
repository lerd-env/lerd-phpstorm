package sh.lerd.ide.servicesview

import sh.lerd.ide.api.LerdService
import sh.lerd.ide.site.ResolvedSite
import sh.lerd.ide.site.SiteWorkers

/** One row under Lerd in the IDE's Services window. */
data class SiteRuntime(
    val name: String,
    val detail: String,
    val running: Boolean,
    val failing: Boolean,
    val kind: Kind,
    /** The site action or service action that starts it, null when it has none. */
    val startAction: String?,
    val stopAction: String?,
) {
    enum class Kind { RUNTIME, WORKER, SERVICE }
}

/**
 * What is actually running for this site: the PHP runtime serving it, its
 * workers, and the services it uses. Everything the dashboard calls a moving
 * part, in the window the IDE already keeps them in.
 */
object SiteRuntimes {
    fun of(site: ResolvedSite, services: List<LerdService>): List<SiteRuntime> {
        val entries = mutableListOf<SiteRuntime>()

        runtimeName(site)?.let { name ->
            entries += SiteRuntime(
                name = name,
                detail = site.domain,
                running = site.site.fpmRunning,
                failing = false,
                kind = SiteRuntime.Kind.RUNTIME,
                startAction = "restart",
                stopAction = null,
            )
        }

        SiteWorkers.of(site).forEach { worker ->
            entries += SiteRuntime(
                name = worker.label,
                detail = "",
                running = worker.running,
                failing = worker.failing,
                kind = SiteRuntime.Kind.WORKER,
                startAction = worker.startAction,
                stopAction = worker.stopAction,
            )
        }

        site.site.services.forEach { used ->
            val service = services.find { it.name == used }
            entries += SiteRuntime(
                name = used,
                detail = service?.port?.toString().orEmpty(),
                running = service?.running == true,
                failing = false,
                kind = SiteRuntime.Kind.SERVICE,
                startAction = "start",
                stopAction = "stop",
            )
        }
        return entries
    }

    /** Null when a host process serves the site, which lerd does not start or stop. */
    private fun runtimeName(site: ResolvedSite): String? {
        val version = site.phpVersion.orEmpty()
        return when {
            site.site.runtime == "native" -> null
            site.site.customContainer -> "Container"
            site.site.runtime == "frankenphp" -> "FrankenPHP $version".trim()
            site.site.runtime == "fpm-custom" -> "Custom FPM $version".trim()
            version.isBlank() -> null
            else -> "PHP-FPM $version"
        }
    }
}

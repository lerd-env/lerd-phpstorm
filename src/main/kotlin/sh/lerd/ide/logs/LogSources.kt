package sh.lerd.ide.logs

import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.site.ResolvedSite
import sh.lerd.ide.site.SiteWorkers

/**
 * One thing the Logs tab can show. STREAM sources are SSE endpoints carrying
 * plain lines; APP sources are the framework's own log files, which the daemon
 * serves parsed rather than streamed.
 */
data class LogSource(
    val id: String,
    val label: String,
    val kind: Kind,
    val path: String,
) {
    enum class Kind { STREAM, APP }
}

/**
 * Builds the source list for a site, mirroring what the dashboard's log tabs
 * offer. The endpoints are keyed by site *name* rather than domain, and a
 * worktree's generic worker units are suffixed with its directory, both of
 * which come from the daemon's unit naming rather than from choice.
 */
object LogSources {
    fun of(site: ResolvedSite, appLogFiles: List<String> = emptyList()): List<LogSource> {
        val s = site.site
        val name = s.name ?: s.domain
        val unitName = site.worktree?.path
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.let { "$name-$it" }
            ?: name

        val sources = mutableListOf<LogSource>()

        appLogFiles.forEach { file ->
            sources += LogSource("app:$file", file, LogSource.Kind.APP, file)
        }

        fpmContainer(s)?.let {
            sources += LogSource("fpm", fpmLabel(s), LogSource.Kind.STREAM, "/api/logs/$it")
        }

        SiteWorkers.of(site).forEach { worker ->
            val id = if (worker.named) worker.name else "worker:${worker.name}"
            sources += stream(id, worker.label, worker.logPath(name, unitName))
        }

        s.services.forEach { service ->
            sources += stream("service:$service", service, "/api/logs/lerd-$service")
        }

        sources += stream("nginx", "nginx", "/api/logs/lerd-nginx")
        return sources
    }

    private fun stream(id: String, label: String, path: String) =
        LogSource(id, label, LogSource.Kind.STREAM, path)

    /** Null when the site is served by a host process, which has no container log. */
    private fun fpmContainer(s: LerdSite): String? {
        s.phpLogUnit?.takeIf { it.isNotBlank() }?.let { return it }
        val name = s.name ?: s.domain
        return when {
            s.runtime == "native" -> null
            s.customContainer -> "lerd-custom-$name"
            s.runtime == "frankenphp" -> "lerd-fp-$name"
            s.runtime == "fpm-custom" -> "lerd-cfpm-$name"
            s.phpVersion.isNullOrBlank() -> null
            else -> "lerd-php${s.phpVersion.replace(".", "")}-fpm"
        }
    }

    private fun fpmLabel(s: LerdSite): String = when {
        s.runtime == "native" -> "PHP"
        s.customContainer -> "Container"
        s.runtime == "frankenphp" -> "FrankenPHP"
        s.runtime == "fpm-custom" -> "Custom FPM"
        else -> "PHP-FPM"
    }
}

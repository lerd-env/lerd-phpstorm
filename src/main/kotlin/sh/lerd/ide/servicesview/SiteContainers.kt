package sh.lerd.ide.servicesview

import sh.lerd.ide.api.LerdService
import sh.lerd.ide.site.ResolvedSite

/** One container behind the open site, as the IDE's Services window shows it. */
data class SiteContainer(
    val name: String,
    val container: String,
    val detail: String,
    val running: Boolean,
    val kind: Kind,
    val connectionUrl: String? = null,
) {
    enum class Kind { RUNTIME, SERVICE }

    /**
     * lerd knows how to enter the site's own container properly, with the right
     * working directory and environment; a service container has no such
     * command, so it is entered directly.
     */
    val shellCommand: List<String>
        get() = when (kind) {
            Kind.RUNTIME -> listOf("lerd", "shell")
            Kind.SERVICE -> listOf("podman", "exec", "-it", container, "sh")
        }

    val logsCommand: List<String>
        get() = listOf("lerd", "logs", "-f", if (kind == Kind.RUNTIME) "fpm" else name)
}

/**
 * The containers this site runs on: the one serving it, and one per service it
 * uses. Workers are deliberately absent, because a systemd unit is not
 * something the Services window can open a shell into.
 */
object SiteContainers {
    fun of(site: ResolvedSite, services: List<LerdService>): List<SiteContainer> {
        val entries = mutableListOf<SiteContainer>()

        runtimeContainer(site)?.let { (label, container) ->
            entries += SiteContainer(
                name = label,
                container = container,
                detail = site.domain,
                running = site.site.fpmRunning,
                kind = SiteContainer.Kind.RUNTIME,
            )
        }

        site.site.services.forEach { used ->
            val service = services.find { it.name == used }
            entries += SiteContainer(
                name = used,
                container = "lerd-$used",
                detail = listOfNotNull(service?.version, service?.port?.toString()).joinToString("  "),
                running = service?.running == true,
                kind = SiteContainer.Kind.SERVICE,
                connectionUrl = service?.connectionUrl,
            )
        }
        return entries
    }

    /** Null when a host process serves the site: there is no container to enter. */
    private fun runtimeContainer(site: ResolvedSite): Pair<String, String>? {
        val name = site.site.name ?: site.site.domain
        val version = site.phpVersion.orEmpty()
        site.site.phpLogUnit?.takeIf { it.isNotBlank() }?.let { return "PHP $version".trim() to it }
        return when {
            site.site.runtime == "native" -> null
            site.site.customContainer -> "Container" to "lerd-custom-$name"
            site.site.runtime == "frankenphp" -> "FrankenPHP $version".trim() to "lerd-fp-$name"
            site.site.runtime == "fpm-custom" -> "Custom FPM $version".trim() to "lerd-cfpm-$name"
            version.isBlank() -> null
            else -> "PHP-FPM $version" to "lerd-php${version.replace(".", "")}-fpm"
        }
    }
}

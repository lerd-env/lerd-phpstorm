package sh.lerd.ide.site

import org.yaml.snakeyaml.Yaml
import sh.lerd.ide.api.LerdSite
import java.nio.file.Files
import java.nio.file.Path

/**
 * The on-disk site registry, read directly when lerd-ui is not running. It
 * carries only what the widget needs to say "this is site X and lerd is
 * stopped"; anything richer comes from the API once the daemon is back.
 */
object SitesRegistry {
    fun defaultPath(): Path {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        val base = xdg?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".local", "share")
        return base.resolve("lerd").resolve("sites.yaml")
    }

    fun read(file: Path = defaultPath()): List<LerdSite> {
        if (!Files.isRegularFile(file)) return emptyList()
        val root = try {
            Yaml().load<Any?>(Files.newBufferedReader(file))
        } catch (_: Exception) {
            return emptyList()
        }
        val entries = ((root as? Map<*, *>)?.get("sites") as? List<*>).orEmpty()
        return entries.mapNotNull { entry -> (entry as? Map<*, *>)?.let(::toSite) }
    }

    private fun toSite(entry: Map<*, *>): LerdSite? {
        val path = entry.string("path") ?: return null
        val domains = (entry["domains"] as? List<*>)?.mapNotNull { it?.toString() }
            ?: listOfNotNull(entry.string("domain"))
        return LerdSite(
            name = entry.string("name"),
            domain = domains.firstOrNull().orEmpty(),
            domains = domains,
            path = path,
            phpVersion = entry.string("php_version"),
            nodeVersion = entry.string("node_version"),
            framework = entry.string("framework"),
            tls = entry["secured"] == true,
            paused = entry["paused"] == true,
            pinned = entry["pinned"] == true,
        )
    }

    private fun Map<*, *>.string(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }
}

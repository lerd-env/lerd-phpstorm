package sh.lerd.ide.logs

import java.nio.file.Path

/**
 * Where a path logged by lerd might live on disk.
 *
 * Containers bind-mount the user's home at the same absolute path
 * (Volume=%h:%h), so a path a container logged is already the host's. Only a
 * relative one needs the site root put back in front of it.
 */
object LogPaths {
    fun candidates(path: String, siteRoot: String?): List<String> = when {
        path.startsWith("/") -> listOf(path)
        siteRoot.isNullOrBlank() -> emptyList()
        else -> listOf(Path.of(siteRoot, path).normalize().toString())
    }
}

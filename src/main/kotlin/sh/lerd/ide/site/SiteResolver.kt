package sh.lerd.ide.site

import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorker
import sh.lerd.ide.api.LerdWorktree
import java.nio.file.Path

/**
 * A project that belongs to lerd, as one site or as one worktree of it. The
 * accessors answer for whichever of the two the IDE actually has open, so
 * callers never have to ask which case they are in.
 */
data class ResolvedSite(
    val site: LerdSite,
    val worktree: LerdWorktree? = null,
) {
    val isWorktree: Boolean get() = worktree != null
    val branch: String? get() = worktree?.branch
    val domain: String get() = worktree?.domain?.takeIf { it.isNotBlank() } ?: site.domain
    val path: String? get() = worktree?.path ?: site.path
    val phpVersion: String? get() = worktree?.phpVersion ?: site.phpVersion
    val nodeVersion: String? get() = worktree?.nodeVersion ?: site.nodeVersion
    val frameworkLabel: String? get() = worktree?.frameworkLabel ?: site.frameworkLabel
    val workers: List<LerdWorker>
        get() = worktree?.workers?.takeIf { it.isNotEmpty() } ?: site.workers
    val displayName: String get() = site.name ?: site.domain
}

/**
 * Maps the directory the IDE has open onto a lerd site.
 *
 * Paths are compared canonicalised, never as strings: lerd itself does this in
 * config.SamePath because /home is a symlink to /var/home on ostree distros and
 * macOS volumes fold case. Opening a subdirectory of a site still counts as
 * that site, and the deepest registered path wins, so a worktree inside a site
 * and a package inside a monorepo both resolve to the nearer one.
 */
object SiteResolver {
    fun resolve(sites: List<LerdSite>, projectPath: Path): ResolvedSite? {
        val project = canonical(projectPath)

        var best: ResolvedSite? = null
        var bestDepth = -1

        fun consider(candidatePath: String?, resolved: () -> ResolvedSite) {
            val path = candidatePath?.takeIf { it.isNotBlank() } ?: return
            val canonical = canonical(Path.of(path))
            if (!project.startsWith(canonical)) return
            val depth = canonical.nameCount
            if (depth > bestDepth) {
                bestDepth = depth
                best = resolved()
            }
        }

        for (site in sites) {
            consider(site.path) { ResolvedSite(site) }
            for (worktree in site.worktrees) {
                consider(worktree.path) { ResolvedSite(site, worktree) }
            }
        }
        return best
    }

    private fun canonical(path: Path): Path = try {
        path.toRealPath()
    } catch (_: Exception) {
        // A registered site whose directory has been deleted still has to
        // compare cleanly rather than abort the whole resolution.
        path.toAbsolutePath().normalize()
    }
}

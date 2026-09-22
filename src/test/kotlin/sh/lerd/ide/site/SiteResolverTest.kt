package sh.lerd.ide.site

import org.junit.jupiter.api.io.TempDir
import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorktree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteResolverTest {
    @TempDir
    lateinit var tmp: Path

    private fun dir(name: String): Path = Files.createDirectories(tmp.resolve(name))

    private fun site(name: String, path: Path, vararg worktrees: LerdWorktree) = LerdSite(
        name = name,
        domain = "$name.test",
        path = path.toString(),
        worktrees = worktrees.toList(),
    )

    @Test
    fun `resolves a project opened at the site root`() {
        val root = dir("myapp")
        val resolved = SiteResolver.resolve(listOf(site("myapp", root)), root)

        assertEquals("myapp", resolved?.site?.name)
        assertNull(resolved?.worktree)
        assertEquals("myapp.test", resolved?.domain)
    }

    @Test
    fun `a directory that belongs to no site resolves to nothing`() {
        val root = dir("myapp")
        val elsewhere = dir("not-a-site")

        assertNull(SiteResolver.resolve(listOf(site("myapp", root)), elsewhere))
    }

    @Test
    fun `resolves a project opened below the site root`() {
        // Opening the app/ subfolder of a site is still that site.
        val root = dir("myapp")
        val inner = Files.createDirectories(root.resolve("app/Http"))

        assertEquals("myapp", SiteResolver.resolve(listOf(site("myapp", root)), inner)?.site?.name)
    }

    @Test
    fun `resolves a worktree to its parent site plus the branch`() {
        val root = dir("myapp")
        val wt = dir("myapp-checkout")
        val sites = listOf(
            site(
                "myapp", root,
                LerdWorktree(
                    branch = "feature/checkout",
                    domain = "feature-checkout.myapp.test",
                    path = wt.toString(),
                    phpVersion = "8.3",
                ),
            ),
        )

        val resolved = SiteResolver.resolve(sites, wt)

        assertEquals("myapp", resolved?.site?.name)
        assertEquals("feature/checkout", resolved?.branch)
        assertEquals("feature-checkout.myapp.test", resolved?.domain)
        assertEquals("8.3", resolved?.phpVersion)
    }

    @Test
    fun `a worktree nested inside the site wins over the site itself`() {
        val root = dir("myapp")
        val wt = Files.createDirectories(root.resolve(".worktrees/checkout"))
        val sites = listOf(
            site("myapp", root, LerdWorktree(branch = "checkout", domain = "wt.myapp.test", path = wt.toString())),
        )

        assertEquals("checkout", SiteResolver.resolve(sites, wt)?.branch)
    }

    @Test
    fun `a symlinked project path resolves to the same site`() {
        // /home -> /var/home on ostree distros, so string comparison is not enough.
        val root = dir("myapp")
        val link = tmp.resolve("myapp-link")
        Files.createSymbolicLink(link, root)

        assertEquals("myapp", SiteResolver.resolve(listOf(site("myapp", root)), link)?.site?.name)
    }

    @Test
    fun `a site whose directory is gone does not break resolution`() {
        val root = dir("myapp")
        val missing = site("ghost", tmp.resolve("deleted-long-ago"))

        val resolved = SiteResolver.resolve(listOf(missing, site("myapp", root)), root)

        assertEquals("myapp", resolved?.site?.name)
    }

    @Test
    fun `the most specific site wins when one contains another`() {
        val outer = dir("mono")
        val inner = Files.createDirectories(outer.resolve("packages/api"))

        val resolved = SiteResolver.resolve(listOf(site("mono", outer), site("api", inner)), inner)

        assertEquals("api", resolved?.site?.name)
    }

    @Test
    fun `falls back to worker and php details of the worktree when it has them`() {
        val root = dir("myapp")
        val wt = dir("wt")
        val parent = LerdSite(
            name = "myapp",
            domain = "myapp.test",
            path = root.toString(),
            phpVersion = "8.4",
            worktrees = listOf(LerdWorktree(branch = "b", domain = "b.myapp.test", path = wt.toString())),
        )

        val resolved = SiteResolver.resolve(listOf(parent), wt)

        // The worktree declares no PHP override, so the parent's version stands.
        assertEquals("8.4", resolved?.phpVersion)
        assertTrue(resolved?.isWorktree == true)
    }
}

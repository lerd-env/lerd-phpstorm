package sh.lerd.ide.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelsTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    @Test
    fun `decodes the sites payload`() {
        val sites = LerdJson.decodeSites(fixture("sites.json"))

        assertEquals(2, sites.size)
        val app = sites.first()
        assertEquals("myapp", app.name)
        assertEquals("myapp.test", app.domain)
        assertEquals(listOf("myapp.test", "admin.myapp.test"), app.domains)
        assertEquals("8.4", app.phpVersion)
        assertEquals("Laravel 12", app.frameworkLabel)
        assertTrue(app.tls)
        assertTrue(app.fpmRunning)
        assertEquals(listOf("mysql", "redis"), app.services)
    }

    @Test
    fun `decodes workers and worktrees`() {
        val app = LerdJson.decodeSites(fixture("sites.json")).first()

        assertEquals(2, app.workers.size)
        assertTrue(app.workers[0].running)
        assertTrue(app.workers[1].failing)

        val worktree = app.worktrees.single()
        assertEquals("feature/checkout", worktree.branch)
        assertEquals("/home/dev/Code/myapp-worktrees/feature-checkout", worktree.path)
        assertTrue(worktree.dbIsolated)
    }

    @Test
    fun `an unknown field does not fail the payload`() {
        // lerd's API carries no version, so it may grow keys at any time. A key
        // we have never seen must not take the plugin down.
        val sites = LerdJson.decodeSites(fixture("sites.json"))
        assertEquals("blog", sites[1].name)
    }

    @Test
    fun `absent optional fields fall back rather than throwing`() {
        val blog = LerdJson.decodeSites(fixture("sites.json"))[1]

        assertNull(blog.frameworkLabel)
        assertTrue(blog.paused)
        assertTrue(blog.domains.isEmpty())
        assertTrue(blog.workers.isEmpty())
    }

    @Test
    fun `decodes the status payload`() {
        val status = LerdJson.decodeStatus(fixture("status.json"))

        assertEquals("b1cc30dd", status.instance)
        assertEquals("8.4", status.phpDefault)
        assertTrue(status.nginx.running)
        assertEquals(listOf("8.4", "8.3"), status.phpFpms.map { it.version })
    }
}

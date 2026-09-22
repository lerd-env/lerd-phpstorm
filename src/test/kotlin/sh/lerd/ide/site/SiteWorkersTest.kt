package sh.lerd.ide.site

import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorker
import sh.lerd.ide.api.LerdWorktree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteWorkersTest {
    private fun site(block: LerdSite.() -> LerdSite = { this }) =
        LerdSite(name = "myapp", domain = "myapp.test", path = "/home/dev/myapp").block()

    private fun of(site: LerdSite, worktree: LerdWorktree? = null) =
        SiteWorkers.of(ResolvedSite(site, worktree))

    @Test
    fun `a site with no workers has no controls`() {
        assertTrue(of(site()).isEmpty())
    }

    @Test
    fun `a declared queue worker appears even while stopped`() {
        // The toggle has to exist before the worker runs, or there is no way
        // to start it.
        val queue = of(site { copy(hasQueueWorker = true) }).single()

        assertEquals("queue", queue.name)
        assertEquals("Queue", queue.label)
        assertFalse(queue.running)
    }

    @Test
    fun `a worker that is running but undeclared still appears`() {
        val horizon = of(site { copy(horizonRunning = true) }).single()

        assertEquals("horizon", horizon.name)
        assertTrue(horizon.running)
    }

    @Test
    fun `named workers carry their own action names`() {
        val queue = of(site { copy(hasQueueWorker = true) }).single()

        assertEquals("queue:start", queue.startAction)
        assertEquals("queue:stop", queue.stopAction)
    }

    @Test
    fun `framework workers go through the generic worker action`() {
        val vite = of(site { copy(workers = listOf(LerdWorker(name = "vite", label = "Vite"))) }).single()

        assertEquals("worker:vite:start", vite.startAction)
        assertEquals("worker:vite:stop", vite.stopAction)
        assertEquals("Vite", vite.label)
    }

    @Test
    fun `a failing worker is reported as failing, not as stopped`() {
        val schedule = of(site { copy(hasScheduleWorker = true, scheduleFailing = true) }).single()

        assertTrue(schedule.failing)
        assertFalse(schedule.running)
    }

    @Test
    fun `stripe shows up once its secret is set`() {
        assertEquals(listOf("stripe"), of(site { copy(stripeSecretSet = true) }).map { it.name })
    }

    @Test
    fun `named workers come before framework workers`() {
        val controls = of(
            site {
                copy(hasQueueWorker = true, workers = listOf(LerdWorker(name = "vite")))
            },
        )

        assertEquals(listOf("queue", "vite"), controls.map { it.name })
    }

    @Test
    fun `a worktree's workers replace the parent's`() {
        val s = site { copy(workers = listOf(LerdWorker(name = "vite", running = true))) }
        val wt = LerdWorktree(
            branch = "b",
            domain = "b.myapp.test",
            path = "/home/dev/wt/b",
            workers = listOf(LerdWorker(name = "vite", running = false)),
        )

        assertFalse(of(s, wt).single { it.name == "vite" }.running)
    }

    @Test
    fun `log paths follow the endpoint each kind of worker has`() {
        val controls = of(
            site { copy(hasQueueWorker = true, workers = listOf(LerdWorker(name = "vite"))) },
        )

        assertEquals("/api/queue/myapp/logs", controls[0].logPath("myapp", "myapp"))
        assertEquals("/api/worker/myapp-feature/vite/logs", controls[1].logPath("myapp", "myapp-feature"))
    }
}

package sh.lerd.ide.logs

import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorker
import sh.lerd.ide.api.LerdWorktree
import sh.lerd.ide.site.ResolvedSite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogSourcesTest {
    private fun site(
        name: String = "myapp",
        php: String? = "8.4",
        runtime: String? = null,
        workers: List<LerdWorker> = emptyList(),
        services: List<String> = emptyList(),
        block: LerdSite.() -> LerdSite = { this },
    ) = LerdSite(
        name = name,
        domain = "$name.test",
        path = "/home/dev/$name",
        phpVersion = php,
        runtime = runtime,
        workers = workers,
        services = services,
    ).block()

    private fun sourcesOf(site: LerdSite, worktree: LerdWorktree? = null, appFiles: List<String> = emptyList()) =
        LogSources.of(ResolvedSite(site, worktree), appFiles)

    @Test
    fun `php-fpm names the shared container for the site's version`() {
        val fpm = sourcesOf(site()).single { it.id == "fpm" }

        assertEquals("/api/logs/lerd-php84-fpm", fpm.path)
        assertEquals("PHP-FPM", fpm.label)
    }

    @Test
    fun `a frankenphp site names its own container`() {
        val fpm = sourcesOf(site(runtime = "frankenphp")).single { it.id == "fpm" }

        assertEquals("/api/logs/lerd-fp-myapp", fpm.path)
        assertEquals("FrankenPHP", fpm.label)
    }

    @Test
    fun `a native site offers no fpm source at all`() {
        // There is no container to read; inventing one would stream something else.
        assertTrue(sourcesOf(site(runtime = "native")).none { it.id == "fpm" })
    }

    @Test
    fun `the daemon's own unit name wins over the derived container`() {
        val s = site().copy(phpLogUnit = "lerd-php-native-myapp")

        assertEquals("/api/logs/lerd-php-native-myapp", sourcesOf(s).single { it.id == "fpm" }.path)
    }

    @Test
    fun `named workers use their own endpoints and the site name`() {
        val s = site().copy(hasQueueWorker = true, hasScheduleWorker = true, hasHorizon = true)

        val paths = sourcesOf(s).associate { it.id to it.path }

        assertEquals("/api/queue/myapp/logs", paths["queue"])
        assertEquals("/api/schedule/myapp/logs", paths["schedule"])
        assertEquals("/api/horizon/myapp/logs", paths["horizon"])
    }

    @Test
    fun `framework workers use the generic worker endpoint`() {
        val s = site(workers = listOf(LerdWorker(name = "vite", label = "Vite")))

        val vite = sourcesOf(s).single { it.id == "worker:vite" }

        assertEquals("/api/worker/myapp/vite/logs", vite.path)
        assertEquals("Vite", vite.label)
    }

    @Test
    fun `a worktree's framework worker unit carries the worktree directory`() {
        val s = site(workers = listOf(LerdWorker(name = "vite")))
        val wt = LerdWorktree(branch = "feature/x", domain = "x.myapp.test", path = "/home/dev/wt/feature-x")

        val vite = sourcesOf(s, wt).single { it.id == "worker:vite" }

        assertEquals("/api/worker/myapp-feature-x/vite/logs", vite.path)
    }

    @Test
    fun `app log files come first, one source each`() {
        val s = site().copy(hasAppLogs = true)

        val sources = sourcesOf(s, appFiles = listOf("laravel.log", "worker.log"))

        assertEquals(listOf("app:laravel.log", "app:worker.log"), sources.take(2).map { it.id })
        assertTrue(sources.take(2).all { it.kind == LogSource.Kind.APP })
    }

    @Test
    fun `services and nginx are offered last`() {
        val sources = sourcesOf(site(services = listOf("mysql", "redis")))

        val ids = sources.map { it.id }
        assertTrue(ids.containsAll(listOf("service:mysql", "service:redis", "nginx")))
        assertEquals("nginx", ids.last())
        assertEquals("/api/logs/lerd-mysql", sources.single { it.id == "service:mysql" }.path)
    }

    @Test
    fun `a site with nothing running still offers nginx`() {
        assertEquals(listOf("nginx"), sourcesOf(site(php = null)).map { it.id })
    }
}

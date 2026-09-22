package sh.lerd.ide.servicesview

import sh.lerd.ide.api.LerdService
import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorker
import sh.lerd.ide.site.ResolvedSite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteRuntimesTest {
    private fun site(
        php: String? = "8.4",
        runtime: String? = null,
        fpmRunning: Boolean = true,
        workers: List<LerdWorker> = emptyList(),
        services: List<String> = emptyList(),
    ) = ResolvedSite(
        LerdSite(
            name = "myapp",
            domain = "myapp.test",
            path = "/home/dev/myapp",
            phpVersion = php,
            runtime = runtime,
            fpmRunning = fpmRunning,
            workers = workers,
            services = services,
        ),
    )

    @Test
    fun `the runtime the site is served by comes first`() {
        val entries = SiteRuntimes.of(site(), emptyList())

        val runtime = entries.first()
        assertEquals("PHP-FPM 8.4", runtime.name)
        assertEquals(SiteRuntime.Kind.RUNTIME, runtime.kind)
        assertTrue(runtime.running)
    }

    @Test
    fun `a frankenphp site says so`() {
        assertEquals("FrankenPHP 8.4", SiteRuntimes.of(site(runtime = "frankenphp"), emptyList()).first().name)
    }

    @Test
    fun `a site served by a host process has no runtime entry to start or stop`() {
        val entries = SiteRuntimes.of(site(runtime = "native"), emptyList())

        assertTrue(entries.none { it.kind == SiteRuntime.Kind.RUNTIME })
    }

    @Test
    fun `workers come with their state`() {
        val entries = SiteRuntimes.of(
            site(workers = listOf(LerdWorker(name = "vite", label = "Vite", running = true))),
            emptyList(),
        )

        val vite = entries.single { it.kind == SiteRuntime.Kind.WORKER }
        assertEquals("Vite", vite.name)
        assertTrue(vite.running)
    }

    @Test
    fun `only the services this site uses are listed`() {
        val entries = SiteRuntimes.of(
            site(services = listOf("mysql", "redis")),
            listOf(
                LerdService(name = "mysql", status = "active", port = 3307),
                LerdService(name = "redis", status = "inactive"),
                LerdService(name = "typesense", status = "active"),
            ),
        )

        val services = entries.filter { it.kind == SiteRuntime.Kind.SERVICE }
        assertEquals(listOf("mysql", "redis"), services.map { it.name })
        assertTrue(services[0].running)
        assertFalse(services[1].running)
        assertEquals("3307", services[0].detail)
    }

    @Test
    fun `a stopped runtime is reported as stopped rather than missing`() {
        val runtime = SiteRuntimes.of(site(fpmRunning = false), emptyList()).first()

        assertFalse(runtime.running)
    }
}

package sh.lerd.ide.servicesview

import sh.lerd.ide.api.LerdService
import sh.lerd.ide.api.LerdSite
import sh.lerd.ide.api.LerdWorker
import sh.lerd.ide.site.ResolvedSite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteContainersTest {
    private fun site(
        php: String? = "8.4",
        runtime: String? = null,
        fpmRunning: Boolean = true,
        services: List<String> = emptyList(),
    ) = ResolvedSite(
        LerdSite(
            name = "myapp",
            domain = "myapp.test",
            path = "/home/dev/myapp",
            phpVersion = php,
            runtime = runtime,
            fpmRunning = fpmRunning,
            workers = listOf(LerdWorker(name = "vite", label = "Vite", running = true)),
            services = services,
        ),
    )

    @Test
    fun `the container serving the site comes first, named as podman names it`() {
        val first = SiteContainers.of(site(), emptyList()).first()

        assertEquals("PHP-FPM 8.4", first.name)
        assertEquals("lerd-php84-fpm", first.container)
        assertTrue(first.running)
    }

    @Test
    fun `workers are not containers and are left out`() {
        // A worker is a systemd unit with nothing to shell into; it belongs on
        // the Site tab, not in a window about containers.
        assertTrue(SiteContainers.of(site(), emptyList()).none { it.name == "Vite" })
    }

    @Test
    fun `a frankenphp site names its own container`() {
        assertEquals("lerd-fp-myapp", SiteContainers.of(site(runtime = "frankenphp"), emptyList()).first().container)
    }

    @Test
    fun `a site served by a host process contributes no container`() {
        assertTrue(SiteContainers.of(site(runtime = "native"), emptyList()).isEmpty())
    }

    @Test
    fun `services carry their container, port and version`() {
        val entries = SiteContainers.of(
            site(services = listOf("mysql", "redis")),
            listOf(
                LerdService(name = "mysql", status = "active", version = "8.4.11", port = 3307),
                LerdService(name = "redis", status = "inactive"),
            ),
        )

        val mysql = entries.single { it.name == "mysql" }
        assertEquals("lerd-mysql", mysql.container)
        assertEquals("8.4.11  3307", mysql.detail)
        assertTrue(mysql.running)
        assertFalse(entries.single { it.name == "redis" }.running)
    }

    @Test
    fun `the site's own container is shelled into through lerd, a service through podman`() {
        val entries = SiteContainers.of(site(services = listOf("mysql")), emptyList())

        assertEquals(listOf("shell"), entries.first().shellCommand.drop(1))
        assertEquals(listOf("exec", "-it", "lerd-mysql", "sh"), entries.last().shellCommand.drop(1))
    }

    @Test
    fun `a service lerd knows nothing about still gets a row rather than vanishing`() {
        val entries = SiteContainers.of(site(services = listOf("typesense")), emptyList())

        val typesense = entries.single { it.name == "typesense" }
        assertEquals("lerd-typesense", typesense.container)
        assertFalse(typesense.running)
    }
}

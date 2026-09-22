package sh.lerd.ide.site

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitesRegistryTest {
    @TempDir
    lateinit var tmp: Path

    private fun registry(body: String): Path =
        tmp.resolve("sites.yaml").also { Files.writeString(it, body) }

    @Test
    fun `reads name, domains and path`() {
        val file = registry(
            """
            sites:
                - name: myapp
                  domains:
                    - myapp.test
                    - admin.myapp.test
                  path: /home/dev/Code/myapp
                  php_version: "8.4"
                  secured: true
                - name: blog
                  domains:
                    - blog.test
                  path: /home/dev/Code/blog
                  php_version: "8.3"
            """.trimIndent(),
        )

        val sites = SitesRegistry.read(file)

        assertEquals(2, sites.size)
        assertEquals("myapp", sites[0].name)
        assertEquals("myapp.test", sites[0].domain)
        assertEquals(listOf("myapp.test", "admin.myapp.test"), sites[0].domains)
        assertEquals("/home/dev/Code/myapp", sites[0].path)
        assertEquals("8.4", sites[0].phpVersion)
        assertTrue(sites[0].tls)
    }

    @Test
    fun `accepts the legacy single domain key`() {
        val file = registry(
            """
            sites:
                - name: old
                  domain: old.test
                  path: /home/dev/Code/old
            """.trimIndent(),
        )

        assertEquals("old.test", SitesRegistry.read(file).single().domain)
    }

    @Test
    fun `a missing file reads as no sites rather than an error`() {
        assertTrue(SitesRegistry.read(tmp.resolve("absent.yaml")).isEmpty())
    }

    @Test
    fun `a malformed file reads as no sites`() {
        assertTrue(SitesRegistry.read(registry("sites: [oh dear: : :")).isEmpty())
    }

    @Test
    fun `an entry with no path is skipped`() {
        val file = registry(
            """
            sites:
                - name: pathless
                  domains: [pathless.test]
                - name: real
                  domains: [real.test]
                  path: /home/dev/Code/real
            """.trimIndent(),
        )

        assertEquals(listOf("real"), SitesRegistry.read(file).map { it.name })
    }
}

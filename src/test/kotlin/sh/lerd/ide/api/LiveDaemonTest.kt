package sh.lerd.ide.api

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs against whatever lerd is actually on this machine, and is skipped when
 * none is. Its job is to catch the API drifting away from the models: a shape
 * that stops decoding here has stopped decoding in the plugin too.
 */
class LiveDaemonTest {
    private val client = LerdClient()

    private fun requireDaemon() {
        assumeTrue(client.status() is LerdResult.Ok, "no lerd-ui on 127.0.0.1:7073")
    }

    @Test
    fun `the real status payload decodes`() {
        requireDaemon()
        val status = client.status()

        val value = (status as LerdResult.Ok).value
        assertTrue(value.instance.isNotBlank(), "status carried no instance id")
    }

    @Test
    fun `the real sites payload decodes and every site has a domain`() {
        requireDaemon()
        val sites = (client.sites() as LerdResult.Ok).value

        assumeTrue(sites.isNotEmpty(), "no sites linked on this machine")
        assertTrue(sites.all { it.domain.isNotBlank() })
        assertTrue(sites.all { !it.path.isNullOrBlank() })
    }
}

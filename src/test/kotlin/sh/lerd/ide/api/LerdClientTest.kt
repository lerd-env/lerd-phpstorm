package sh.lerd.ide.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LerdClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: LerdClient
    private val seen = mutableListOf<HttpExchange>()

    private fun respond(exchange: HttpExchange, status: Int, body: String, type: String = "application/json") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/status") { ex ->
            seen += ex
            respond(ex, 200, """{"instance":"abc","php_default":"8.4"}""")
        }
        server.createContext("/api/sites/myapp.test/restart") { ex ->
            seen += ex
            respond(ex, 200, """{"ok":true}""")
        }
        server.createContext("/api/sites/myapp.test/php") { ex ->
            seen += ex
            respond(ex, 200, """{"ok":false,"error":"php 8.9 is not installed"}""")
        }
        server.createContext("/api/sites/myapp.test/rebuild") { ex ->
            seen += ex
            respond(ex, 500, "something went wrong\n", "text/plain")
        }
        server.start()
        client = LerdClient { "http://127.0.0.1:${server.address.port}" }
    }

    @AfterTest
    fun stop() {
        client.close()
        server.stop(0)
    }

    @Test
    fun `a read succeeds and parses`() {
        val status = client.status().valueOrNull()
        assertEquals("abc", status?.instance)
    }

    @Test
    fun `a read sends no CSRF header and no origin`() {
        client.status()
        val headers = seen.single().requestHeaders
        assertNull(headers.getFirst("X-Lerd-CSRF"))
        assertNull(headers.getFirst("Origin"))
    }

    @Test
    fun `a mutation carries the CSRF header`() {
        // Without it lerd's gate rejects every POST from a non-browser client
        // over TCP loopback.
        val result = client.siteAction("myapp.test", "restart")

        assertTrue(result is LerdResult.Ok)
        val headers = seen.single().requestHeaders
        assertEquals("1", headers.getFirst("X-Lerd-CSRF"))
        assertNull(headers.getFirst("Origin"))
        assertNull(headers.getFirst("X-Forwarded-For"))
    }

    @Test
    fun `an ok-false body with http 200 is an error`() {
        val result = client.siteAction("myapp.test", "php", mapOf("version" to "8.9"))

        assertEquals("php 8.9 is not installed", (result as LerdResult.Err).message)
    }

    @Test
    fun `a plain text error body becomes the same envelope`() {
        val result = client.siteAction("myapp.test", "rebuild")

        assertEquals("something went wrong", (result as LerdResult.Err).message)
    }

    @Test
    fun `query parameters are encoded`() {
        client.siteAction("myapp.test", "php", mapOf("version" to "8.4", "branch" to "feature/checkout"))

        val query = seen.single().requestURI.rawQuery
        assertTrue(query.contains("version=8.4"), query)
        assertTrue(query.contains("branch=feature%2Fcheckout"), query)
    }

    @Test
    fun `a daemon that is not listening is an error, never an exception`() {
        val offline = LerdClient { "http://127.0.0.1:1" }
        val result = offline.status()
        offline.close()

        assertTrue(result is LerdResult.Err)
    }
}

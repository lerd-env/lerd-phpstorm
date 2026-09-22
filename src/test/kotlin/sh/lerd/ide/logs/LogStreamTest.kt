package sh.lerd.ide.logs

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogStreamTest {
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<HttpExchange>()
    private var streams = mutableListOf<LogStream>()

    private fun sse(exchange: HttpExchange, body: String) {
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun stream(path: String, onLine: (String) -> Unit): LogStream =
        LogStream({ "http://127.0.0.1:${server.address.port}" }, path, onLine)
            .also { streams += it; it.start() }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @AfterTest
    fun stop() {
        streams.forEach { it.close() }
        server.stop(0)
    }

    @Test
    fun `delivers each data line`() {
        val lines = CopyOnWriteArrayList<String>()
        val seen = CountDownLatch(3)
        server.createContext("/api/logs/lerd-nginx") { ex ->
            requests += ex
            sse(ex, "id: 1\ndata: first\n\nid: 2\ndata: second\n\nid: 3\ndata: third\n\n")
        }

        stream("/api/logs/lerd-nginx") { lines += it; seen.countDown() }

        assertTrue(seen.await(5, TimeUnit.SECONDS), "only saw $lines")
        assertEquals(listOf("first", "second", "third"), lines.toList())
    }

    @Test
    fun `asks for an event stream`() {
        val seen = CountDownLatch(1)
        server.createContext("/api/logs/lerd-dns") { ex ->
            requests += ex
            sse(ex, "data: x\n\n")
        }

        stream("/api/logs/lerd-dns") { seen.countDown() }
        assertTrue(seen.await(5, TimeUnit.SECONDS))

        assertEquals("text/event-stream", requests.single().requestHeaders.getFirst("Accept"))
    }

    @Test
    fun `resumes from the last id it saw`() {
        val reconnected = CountDownLatch(2)
        server.createContext("/api/logs/lerd-mysql") { ex ->
            requests += ex
            reconnected.countDown()
            // First connection ends after one line, forcing a reconnect.
            sse(ex, "id: 42\ndata: only\n\n")
        }

        stream("/api/logs/lerd-mysql") {}

        assertTrue(reconnected.await(10, TimeUnit.SECONDS), "never reconnected")
        assertEquals("42", requests[1].requestHeaders.getFirst("Last-Event-ID"))
    }

    @Test
    fun `a comment heartbeat is not a log line`() {
        val lines = CopyOnWriteArrayList<String>()
        val seen = CountDownLatch(1)
        server.createContext("/api/logs/lerd-redis") { ex ->
            requests += ex
            sse(ex, ": heartbeat\n\ndata: real\n\n")
        }

        stream("/api/logs/lerd-redis") { lines += it; seen.countDown() }
        assertTrue(seen.await(5, TimeUnit.SECONDS))

        assertEquals(listOf("real"), lines.toList())
    }

    @Test
    fun `closing stops delivery`() {
        val lines = CopyOnWriteArrayList<String>()
        val seen = CountDownLatch(1)
        server.createContext("/api/logs/lerd-quiet") { ex ->
            requests += ex
            sse(ex, "data: one\n\n")
        }

        val s = stream("/api/logs/lerd-quiet") { lines += it; seen.countDown() }
        assertTrue(seen.await(5, TimeUnit.SECONDS))
        s.close()
        val delivered = lines.size

        Thread.sleep(300)
        assertEquals(delivered, lines.size)
    }

    @Test
    fun `named events are delivered apart from plain data`() {
        val events = CopyOnWriteArrayList<Pair<String, String>>()
        val lines = CopyOnWriteArrayList<String>()
        val seen = CountDownLatch(3)
        server.createContext("/api/sites/myapp.test/commands/migrate/run") { ex ->
            requests += ex
            sse(ex, "event: stdout\ndata: migrating\n\nevent: done\ndata: {\"exit\":0}\n\ndata: trailing\n\n")
        }

        val s = LogStream(
            { "http://127.0.0.1:${server.address.port}" },
            "/api/sites/myapp.test/commands/migrate/run",
            onLine = { lines += it; seen.countDown() },
            method = "POST",
            retry = false,
            onEvent = { event, data -> events += event to data; seen.countDown() },
        )
        streams += s
        s.start()
        assertTrue(seen.await(5, TimeUnit.SECONDS), "saw $events / $lines")

        assertEquals(listOf("stdout" to "migrating", "done" to """{"exit":0}"""), events.toList())
        assertEquals(listOf("trailing"), lines.toList())
    }

    @Test
    fun `a command run posts with the CSRF header and does not reconnect`() {
        val done = CountDownLatch(1)
        server.createContext("/api/sites/myapp.test/commands/test/run") { ex ->
            requests += ex
            sse(ex, "event: done\ndata: {}\n\n")
        }

        val s = LogStream(
            { "http://127.0.0.1:${server.address.port}" },
            "/api/sites/myapp.test/commands/test/run",
            onLine = {},
            method = "POST",
            retry = false,
            onEvent = { _, _ -> done.countDown() },
        )
        streams += s
        s.start()
        assertTrue(done.await(5, TimeUnit.SECONDS))

        Thread.sleep(400)
        assertEquals(1, requests.size)
        assertEquals("POST", requests.single().requestMethod)
        assertEquals("1", requests.single().requestHeaders.getFirst("X-Lerd-CSRF"))
    }
}

package sh.lerd.ide.logs

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tails one of lerd's SSE log endpoints. The daemon numbers every line with an
 * `id`, so a dropped connection resumes with Last-Event-ID instead of replaying
 * the buffer or losing the gap. A stopped lerd is not an error here: the stream
 * just keeps trying until it is closed.
 */
class LogStream(
    private val baseUrl: () -> String,
    private val path: String,
    private val onLine: (String) -> Unit,
    private val onStateChange: (connected: Boolean) -> Unit = {},
    /** POST for endpoints that start work, GET for the ones that only tail. */
    private val method: String = "GET",
    /**
     * A log tail reconnects forever; a command run happens once and its stream
     * ending means the command finished.
     */
    private val retry: Boolean = true,
    /** Named SSE events, which command runs use to separate stdout from stderr. */
    private val onEvent: (event: String, data: String) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private val closed = AtomicBoolean(false)

    @Volatile
    private var lastEventId: String? = null

    @Volatile
    private var worker: Thread? = null

    fun start() {
        if (worker != null) return
        worker = Thread({ loop() }, "lerd log $path").apply {
            isDaemon = true
            start()
        }
    }

    private fun loop() {
        var attempt = 0
        while (!closed.get()) {
            val connected = readOnce()
            if (closed.get() || !retry) return
            attempt = if (connected) 0 else attempt + 1
            val delay = RETRY_DELAYS_MS[minOf(attempt, RETRY_DELAYS_MS.lastIndex)]
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    /** Returns true when the connection was established, whatever ended it. */
    private fun readOnce(): Boolean {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl().trimEnd('/') + path))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
        if (method == "POST") {
            builder.header("X-Lerd-CSRF", "1").POST(HttpRequest.BodyPublishers.noBody())
        } else {
            builder.GET()
        }
        lastEventId?.let { builder.header("Last-Event-ID", it) }

        return try {
            val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() >= 400) return false
            onStateChange(true)
            response.body().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    consume(line)
                }
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!closed.get()) onStateChange(false)
        }
    }

    @Volatile
    private var pendingEvent: String? = null

    private fun consume(line: String) {
        when {
            line.startsWith(":") -> Unit // heartbeat
            line.startsWith("id:") -> lastEventId = line.removePrefix("id:").trim()
            line.startsWith("event:") -> pendingEvent = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                val data = line.removePrefix("data:").removePrefix(" ")
                val event = pendingEvent
                pendingEvent = null
                if (event == null) onLine(data) else onEvent(event, data)
            }
        }
    }

    override fun close() {
        closed.set(true)
        worker?.interrupt()
        worker = null
        http.close()
    }

    private companion object {
        val RETRY_DELAYS_MS = longArrayOf(200, 500, 1_000, 3_000, 5_000)
    }
}

package sh.lerd.ide.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Subscribes to lerd's /api/ws fan-out. Every frame names the kind of state
 * that changed; the plugin only needs the kind, then re-reads whatever it
 * cares about. Reconnects with a backoff, because a stopped lerd is a normal
 * state to sit in for an hour.
 *
 * No Origin header is sent: lerd allows a missing one for non-browser clients,
 * and sending a wrong one would demote us out of local authority.
 */
class LerdEvents(
    private val baseUrl: () -> String,
    private val scheduler: ScheduledExecutorService,
    private val onChange: (kind: String) -> Unit,
) : AutoCloseable {
    private val http: HttpClient = HttpClient.newHttpClient()
    private val closed = AtomicBoolean(false)
    private var attempt = 0

    @Volatile
    private var socket: WebSocket? = null

    fun start() {
        connect()
    }

    private fun connect() {
        if (closed.get()) return
        val url = baseUrl().trimEnd('/').replaceFirst("http", "ws") + "/api/ws"
        http.newWebSocketBuilder()
            .buildAsync(URI.create(url), Listener())
            .whenComplete { ws, error ->
                if (error != null) {
                    scheduleReconnect()
                } else {
                    socket = ws
                    attempt = 0
                }
            }
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        val delay = RECONNECT_DELAYS_SECONDS[minOf(attempt, RECONNECT_DELAYS_SECONDS.lastIndex)]
        attempt++
        scheduler.schedule({ connect() }, delay, TimeUnit.SECONDS)
    }

    /** Tells the daemon to drop to its slow cadence while the IDE is in the background. */
    fun setVisible(visible: Boolean) {
        socket?.sendText("""{"type":"visibility","visible":$visible}""", true)
    }

    override fun close() {
        closed.set(true)
        socket?.abort()
        socket = null
    }

    private inner class Listener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
            onChange(KIND_CONNECTED)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val frame = buffer.toString()
                buffer.setLength(0)
                onChange(kindOf(frame))
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            socket = null
            onChange(KIND_DISCONNECTED)
            scheduleReconnect()
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            socket = null
            onChange(KIND_DISCONNECTED)
            scheduleReconnect()
            return null
        }
    }

    companion object {
        const val KIND_CONNECTED = "connected"
        const val KIND_DISCONNECTED = "disconnected"

        private val RECONNECT_DELAYS_SECONDS = longArrayOf(1, 2, 5, 10, 30)

        /** The frame's "type" is the single kind that changed, or "snapshot". */
        fun kindOf(frame: String): String = try {
            Json.parseToJsonElement(frame).jsonObject["type"]?.jsonPrimitive?.content ?: "snapshot"
        } catch (_: Exception) {
            "snapshot"
        }
    }
}

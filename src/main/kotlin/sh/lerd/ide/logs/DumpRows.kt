package sh.lerd.ide.logs

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sh.lerd.ide.api.DumpEvent
import sh.lerd.ide.api.DumpSource

/** One captured event, ready to render as a row with a detail pane. */
data class DumpRow(
    val title: String,
    val detail: String,
    val kind: String,
    val where: String,
    val timestamp: String,
    val caller: DumpSource?,
)

/**
 * Turns lerd's captured events into rows for one lens.
 *
 * Each kind carries its own opaque payload, and the daemon promises nothing
 * about its shape, so a title is read from the fields that kind usually has and
 * falls back through the label and the rendered text rather than coming out
 * blank. Events from a test run are tagged rather than dropped by the daemon,
 * so the lens hides them unless asked, the same way the dashboard does.
 */
object DumpRows {
    private val TITLE_KEYS = mapOf(
        "query" to listOf("sql"),
        "job" to listOf("class", "name"),
        "view" to listOf("name", "view", "path"),
        "mail" to listOf("subject", "to"),
        "cache" to listOf("key"),
        "event" to listOf("name", "class"),
        "log" to listOf("message"),
        "message" to listOf("text", "message"),
    )

    fun of(
        events: List<DumpEvent>,
        kind: String? = null,
        search: String = "",
        includeTests: Boolean = false,
    ): List<DumpRow> = eventsOf(events, kind, search, includeTests).map(::row)

    /**
     * The same selection as [of], as events, so a caller that needs the payload
     * behind a row does not have to match them up again.
     */
    fun eventsOf(
        events: List<DumpEvent>,
        kind: String? = null,
        search: String = "",
        includeTests: Boolean = false,
    ): List<DumpEvent> = events
        .filter { includeTests || !it.ctx.test }
        .filter { kind == null || it.kind == kind }
        .sortedByDescending { it.ts }
        .filter { matches(row(it), search) }

    fun row(event: DumpEvent): DumpRow {
        val body = event.text.orEmpty()
        return DumpRow(
            title = title(event, body),
            detail = detail(event, body),
            kind = event.kind,
            where = event.ctx.request ?: event.ctx.worker ?: event.ctx.command.orEmpty(),
            timestamp = event.ts,
            caller = event.src?.takeIf { it.file.isNotBlank() && it.line > 0 },
        )
    }

    private fun title(event: DumpEvent, body: String): String {
        fromData(event)?.let { return it }
        event.label?.takeIf { it.isNotBlank() }?.let { return it }
        body.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return DumpLenses.label(event.kind)
    }

    private fun fromData(event: DumpEvent): String? {
        val data = event.data ?: return null

        if (event.kind == "http") {
            val parts = listOfNotNull(
                data.string("method"),
                data.string("url") ?: data.string("uri"),
                data.string("status"),
            )
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }
        if (event.kind == "exception") {
            val cls = data.string("class")
            val message = data.string("message")
            return listOfNotNull(cls, message).takeIf { it.isNotEmpty() }?.joinToString(": ")
        }

        return TITLE_KEYS[event.kind].orEmpty().firstNotNullOfOrNull { data.string(it) }
    }

    private fun detail(event: DumpEvent, body: String): String = when {
        body.isNotBlank() -> body
        event.data != null -> event.data.entries.joinToString("\n") { (key, value) -> "$key: $value" }
        else -> event.label.orEmpty()
    }

    private fun matches(row: DumpRow, search: String): Boolean {
        val needle = search.trim()
        return needle.isEmpty() ||
            row.title.contains(needle, true) ||
            row.detail.contains(needle, true) ||
            row.where.contains(needle, true)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
}

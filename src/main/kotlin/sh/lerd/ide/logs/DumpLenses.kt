package sh.lerd.ide.logs

import sh.lerd.ide.api.DumpEvent

/** One lens over the captured events, with how much is behind it. */
data class DumpLens(val kind: String, val label: String, val count: Int)

/**
 * The lenses the captured events actually fill. Empty ones are left out rather
 * than shown as zeroes, and a kind the plugin has never heard of still gets a
 * lens, because the daemon is free to grow them.
 */
object DumpLenses {
    /** The dashboard's order, so the two read the same way. */
    private val ORDER = listOf(
        "dump", "query", "job", "view", "mail", "cache",
        "event", "http", "log", "exception", "message",
    )

    private val LABELS = mapOf(
        "dump" to "Dumps",
        "query" to "Queries",
        "job" to "Jobs",
        "view" to "Views",
        "mail" to "Mail",
        "cache" to "Cache",
        "event" to "Events",
        "http" to "HTTP",
        "log" to "Logs",
        "exception" to "Exceptions",
        "message" to "Messages",
    )

    fun of(events: List<DumpEvent>, includeTests: Boolean = false): List<DumpLens> = events
        .filter { includeTests || !it.ctx.test }
        .groupingBy { it.kind }
        .eachCount()
        .map { (kind, count) -> DumpLens(kind, label(kind), count) }
        .sortedBy { ORDER.indexOf(it.kind).takeIf { i -> i >= 0 } ?: ORDER.size }

    fun label(kind: String): String =
        LABELS[kind] ?: kind.replaceFirstChar { it.uppercase() }
}

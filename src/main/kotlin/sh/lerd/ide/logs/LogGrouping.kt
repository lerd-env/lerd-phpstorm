package sh.lerd.ide.logs

import sh.lerd.ide.api.AppLogEntry

/** One error, however many times it happened. */
data class LogGroup(
    val title: String,
    val level: String,
    val count: Int,
    val firstSeen: String,
    val lastSeen: String,
    val latest: AppLogEntry,
)

/**
 * Collapses a framework log into one row per distinct error. A log with the
 * same failure in it two hundred times is two hundred lines of scrolling and
 * one actual problem; grouping is what makes the list readable.
 */
object LogGrouping {
    private val VARIABLE = Regex("""\b[0-9a-f]{8}-[0-9a-f-]{27}\b|\b\d+\b|'[^']*'|"[^"]*"""")

    fun of(entries: List<AppLogEntry>): List<LogGroup> = entries
        .groupBy { signature(it) }
        .map { (_, occurrences) ->
            val ordered = occurrences.sortedBy { instant(it.date) }
            val latest = ordered.last()
            LogGroup(
                title = title(latest.message),
                level = latest.level.uppercase(),
                count = ordered.size,
                firstSeen = ordered.first().date,
                lastSeen = latest.date,
                latest = latest,
            )
        }
        .sortedByDescending { instant(it.lastSeen) }

    /**
     * Log dates come in more than one shape (monolog's "yyyy-MM-dd HH:mm:ss",
     * ISO from other channels), and an undated line must not sort to the top,
     * so order on a parsed instant rather than on the text.
     */
    private fun instant(date: String): Long {
        val trimmed = date.trim()
        if (trimmed.isEmpty()) return Long.MIN_VALUE
        val normalised = trimmed.replace('T', ' ').removeSuffix("Z").substringBefore('.')
        return runCatching {
            java.time.LocalDateTime
                .parse(normalised, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .toEpochSecond(java.time.ZoneOffset.UTC)
        }.getOrElse { Long.MIN_VALUE + 1 }
    }

    /** What makes two lines the same error: the message with its values removed. */
    private fun signature(entry: AppLogEntry): String =
        entry.level.uppercase() + "|" + VARIABLE.replace(title(entry.message), "*")

    /**
     * Monolog appends the serialised exception to the message; the readable
     * part is what comes before it.
     */
    private fun title(message: String): String = message
        .substringBefore(" {\"exception\"")
        .lineSequence()
        .first()
        .trim()
}

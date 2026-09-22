package sh.lerd.ide.logs

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Captured events are stamped in UTC; a row shows them in the reader's own
 * time. Today's events need only the clock, older ones carry their date so a
 * dump from Tuesday is never read as one from this morning.
 */
object DumpTimes {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DATE = DateTimeFormatter.ofPattern("d MMM HH:mm")

    fun short(
        timestamp: String,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (timestamp.isBlank()) return ""
        val instant = parse(timestamp) ?: return timestamp

        val at = instant.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return if (at.toLocalDate() == today) at.format(TIME) else at.format(DATE)
    }

    private fun parse(timestamp: String): Instant? =
        runCatching { Instant.parse(timestamp) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC) }.getOrNull()
}

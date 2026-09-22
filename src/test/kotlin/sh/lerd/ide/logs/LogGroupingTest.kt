package sh.lerd.ide.logs

import sh.lerd.ide.api.AppLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogGroupingTest {
    private fun entry(level: String, date: String, message: String, detail: String? = null) =
        AppLogEntry(level = level, date = date, message = message, detail = detail)

    @Test
    fun `the same error repeated becomes one row with a count`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-21 10:00:00", "Zoho auto token refresh failed"),
                entry("ERROR", "2026-09-21 12:00:00", "Zoho auto token refresh failed"),
                entry("ERROR", "2026-09-21 13:00:00", "Zoho auto token refresh failed"),
            ),
        )

        val group = groups.single()
        assertEquals("Zoho auto token refresh failed", group.title)
        assertEquals(3, group.count)
        assertEquals("2026-09-21 10:00:00", group.firstSeen)
        assertEquals("2026-09-21 13:00:00", group.lastSeen)
    }

    @Test
    fun `groups are ordered by how recently they happened`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-20 10:00:00", "older"),
                entry("ERROR", "2026-09-21 10:00:00", "newer"),
            ),
        )

        assertEquals(listOf("newer", "older"), groups.map { it.title })
    }

    @Test
    fun `the varying part of a message does not split one error into many`() {
        // Ids, uuids and quoted values differ per occurrence; the error does not.
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-21 10:00:00", "No query results for model [App\\Models\\User] 4821"),
                entry("ERROR", "2026-09-21 10:01:00", "No query results for model [App\\Models\\User] 9137"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
    }

    @Test
    fun `the title is the first line, not the whole stack trace`() {
        val groups = LogGrouping.of(
            listOf(entry("ERROR", "2026-09-21 10:00:00", "Boom {\"exception\":\"[object] (RuntimeException")),
        )

        assertEquals("Boom", groups.single().title)
    }

    @Test
    fun `the newest occurrence keeps its full detail for the right pane`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-21 10:00:00", "Boom", detail = "old trace"),
                entry("ERROR", "2026-09-21 11:00:00", "Boom", detail = "new trace"),
            ),
        )

        assertEquals("new trace", groups.single().latest.detail)
    }

    @Test
    fun `levels are kept so the list can show what kind of thing it is`() {
        val groups = LogGrouping.of(listOf(entry("warning", "2026-09-21 10:00:00", "Slow query")))

        assertEquals("WARNING", groups.single().level)
    }

    @Test
    fun `an empty log is an empty list, not an error`() {
        assertTrue(LogGrouping.of(emptyList()).isEmpty())
    }

    @Test
    fun `ordering is by time, not by the shape of the date string`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-21T09:00:00Z", "iso"),
                entry("ERROR", "2026-09-21 23:00:00", "space separated"),
                entry("ERROR", "2026-09-21 08:00:00", "earliest"),
            ),
        )

        assertEquals(listOf("space separated", "iso", "earliest"), groups.map { it.title })
    }

    @Test
    fun `an entry with no date sorts last rather than first`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "", "undated"),
                entry("ERROR", "2026-09-21 08:00:00", "dated"),
            ),
        )

        assertEquals(listOf("dated", "undated"), groups.map { it.title })
    }

    @Test
    fun `within a group the newest occurrence wins whatever order they arrive in`() {
        val groups = LogGrouping.of(
            listOf(
                entry("ERROR", "2026-09-21 11:00:00", "Boom", detail = "new trace"),
                entry("ERROR", "2026-09-21 09:00:00", "Boom", detail = "old trace"),
            ),
        )

        assertEquals("new trace", groups.single().latest.detail)
        assertEquals("2026-09-21 09:00:00", groups.single().firstSeen)
    }
}

package sh.lerd.ide.logs

import sh.lerd.ide.api.DumpContext
import sh.lerd.ide.api.DumpEvent
import sh.lerd.ide.api.DumpSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpRowsTest {
    private fun event(
        kind: String = "var",
        ts: String = "2026-09-21T10:00:00Z",
        text: String? = null,
        label: String? = null,
        file: String? = null,
        request: String? = "GET /",
        test: Boolean = false,
    ) = DumpEvent(
        id = ts,
        ts = ts,
        kind = kind,
        label = label,
        text = text,
        ctx = DumpContext(request = request, test = test),
        src = file?.let { DumpSource(it, 42) },
    )

    @Test
    fun `newest first`() {
        val rows = DumpRows.of(
            listOf(
                event(ts = "2026-09-21T10:00:00Z", text = "older"),
                event(ts = "2026-09-21T12:00:00Z", text = "newer"),
            ),
        )

        assertEquals(listOf("newer", "older"), rows.map { it.title })
    }

    @Test
    fun `a label is the title when the dump carries one`() {
        val row = DumpRows.of(listOf(event(label = "user id", text = "7"))).single()

        assertEquals("user id", row.title)
        assertEquals("7", row.detail)
    }

    @Test
    fun `a query dump reads as its statement`() {
        val row = DumpRows.of(listOf(event(kind = "query", text = null, label = "select * from users"))).single()

        assertEquals("select * from users", row.title)
    }

    @Test
    fun `the first line is the title when there is no label`() {
        val row = DumpRows.of(listOf(event(text = "array:2 [\n  0 => 1\n]"))).single()

        assertEquals("array:2 [", row.title)
        assertTrue(row.detail.contains("0 => 1"))
    }

    @Test
    fun `the caller is kept so it can be linked`() {
        val row = DumpRows.of(listOf(event(file = "/app/Http/Foo.php", text = "x"))).single()

        assertEquals("/app/Http/Foo.php", row.caller?.file)
        assertEquals(42, row.caller?.line)
    }

    @Test
    fun `dumps captured inside a test run are left out by default`() {
        val rows = DumpRows.of(listOf(event(text = "from a test", test = true), event(text = "from the app")))

        assertEquals(listOf("from the app"), rows.map { it.title })
    }

    @Test
    fun `test dumps can be asked for`() {
        val rows = DumpRows.of(listOf(event(text = "from a test", test = true)), includeTests = true)

        assertEquals(1, rows.size)
    }

    @Test
    fun `an empty capture is an empty list`() {
        assertTrue(DumpRows.of(emptyList()).isEmpty())
    }
}

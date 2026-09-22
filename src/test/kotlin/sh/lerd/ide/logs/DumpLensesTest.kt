package sh.lerd.ide.logs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sh.lerd.ide.api.DumpContext
import sh.lerd.ide.api.DumpEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpLensesTest {
    private fun data(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun event(
        kind: String,
        ts: String = "2026-09-21T10:00:00Z",
        data: JsonObject? = null,
        text: String? = null,
        label: String? = null,
        test: Boolean = false,
    ) = DumpEvent(
        id = ts,
        ts = ts,
        kind = kind,
        label = label,
        text = text,
        data = data,
        ctx = DumpContext(request = "GET /", test = test),
    )

    @Test
    fun `a lens is offered only when it has something in it`() {
        val lenses = DumpLenses.of(listOf(event("query"), event("query"), event("mail")))

        assertEquals(listOf("query", "mail"), lenses.map { it.kind })
        assertEquals(listOf(2, 1), lenses.map { it.count })
    }

    @Test
    fun `lenses keep the order the dashboard shows them in`() {
        val lenses = DumpLenses.of(listOf(event("exception"), event("dump"), event("job")))

        assertEquals(listOf("dump", "job", "exception"), lenses.map { it.kind })
    }

    @Test
    fun `a kind the plugin has never heard of still gets a lens`() {
        // The daemon may grow kinds; refusing to show them would hide data.
        val lenses = DumpLenses.of(listOf(event("telemetry")))

        assertEquals(listOf("telemetry"), lenses.map { it.kind })
        assertEquals("Telemetry", lenses.single().label)
    }

    @Test
    fun `test-run events are counted only when asked for`() {
        val events = listOf(event("query"), event("query", test = true))

        assertEquals(1, DumpLenses.of(events).single().count)
        assertEquals(2, DumpLenses.of(events, includeTests = true).single().count)
    }

    @Test
    fun `a query reads as its statement`() {
        val row = DumpRows.of(listOf(event("query", data = data("""{"sql":"select 1","time_ms":4}"""))))
            .single()

        assertEquals("select 1", row.title)
    }

    @Test
    fun `an http call reads as its method, url and status`() {
        val row = DumpRows.of(
            listOf(event("http", data = data("""{"method":"GET","url":"https://api.test/x","status":200}"""))),
        ).single()

        assertEquals("GET https://api.test/x 200", row.title)
    }

    @Test
    fun `an exception reads as its class and message`() {
        val row = DumpRows.of(
            listOf(event("exception", data = data("""{"class":"RuntimeException","message":"Boom"}"""))),
        ).single()

        assertEquals("RuntimeException: Boom", row.title)
    }

    @Test
    fun `a payload whose shape we do not know falls back rather than reading blank`() {
        val row = DumpRows.of(
            listOf(event("telemetry", data = data("""{"whatever":1}"""), label = "a label")),
        ).single()

        assertEquals("a label", row.title)
    }

    @Test
    fun `filtering narrows by lens and by text`() {
        val events = listOf(
            event("query", data = data("""{"sql":"select * from users"}""")),
            event("query", data = data("""{"sql":"select * from orders"}""")),
            event("mail", data = data("""{"subject":"Welcome"}""")),
        )

        assertEquals(2, DumpRows.of(events, kind = "query").size)
        assertEquals(1, DumpRows.of(events, kind = "query", search = "orders").size)
        assertTrue(DumpRows.of(events, kind = "mail", search = "orders").isEmpty())
    }
}

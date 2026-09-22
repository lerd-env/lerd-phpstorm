package sh.lerd.ide.logs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sh.lerd.ide.api.DumpContext
import sh.lerd.ide.api.DumpEvent
import sh.lerd.ide.api.DumpSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DumpDetailTest {
    private val trace = """
        [
          {"file":"/home/dev/app/vendor/laravel/framework/src/Illuminate/Events/Dispatcher.php","line":492,"func":"Illuminate\\Events\\Dispatcher->dispatch"},
          {"file":"/home/dev/app/vendor/laravel/framework/src/Illuminate/Http/Client/PendingRequest.php","line":1758,"func":"PendingRequest->send"},
          {"file":"/home/dev/app/app/Services/Zoho/ZohoOAuthService.php","line":116,"func":"ZohoOAuthService->refreshToken"},
          {"file":"/home/dev/app/app/Console/Commands/SyncCommand.php","line":68,"func":"SyncCommand->handle"},
          {"file":"/home/dev/app/artisan","line":16,"func":"Application->handleCommand"}
        ]
    """.trimIndent()

    private fun event(data: String, src: DumpSource? = null) = DumpEvent(
        id = "1",
        ts = "2026-09-21T10:00:00Z",
        kind = "http",
        ctx = DumpContext(request = "artisan zoho:sync"),
        src = src,
        data = Json.parseToJsonElement(data) as JsonObject,
    )

    private fun lines(event: DumpEvent) = DumpDetail.of(event)

    @Test
    fun `the first frame outside vendor is called out as where it came from`() {
        val out = lines(event("""{"method":"POST","trace":$trace}"""))

        val origin = out.first { it.style == DumpDetail.Style.ORIGIN }
        assertEquals("/home/dev/app/app/Services/Zoho/ZohoOAuthService.php:116", origin.text.trim())
    }

    @Test
    fun `every frame is printed as file colon line so the console can link it`() {
        val out = lines(event("""{"trace":$trace}"""))

        val frames = out.filter { it.style == DumpDetail.Style.FRAME || it.style == DumpDetail.Style.VENDOR_FRAME }
        assertEquals(5, frames.size)
        assertTrue(frames.all { Regex(""":\d+""").containsMatchIn(it.text) })
    }

    @Test
    fun `vendor frames are marked so they can be dimmed`() {
        val out = lines(event("""{"trace":$trace}"""))

        assertEquals(2, out.count { it.style == DumpDetail.Style.VENDOR_FRAME })
        assertEquals(3, out.count { it.style == DumpDetail.Style.FRAME })
    }

    @Test
    fun `the frame carries its function name alongside the location`() {
        val out = lines(event("""{"trace":$trace}"""))

        assertTrue(out.any { it.text.contains("ZohoOAuthService->refreshToken") })
    }

    @Test
    fun `the trace is not repeated as a raw field`() {
        val out = lines(event("""{"method":"POST","trace":$trace}"""))

        assertTrue(out.any { it.text.startsWith("method") })
        assertFalse(out.any { it.text.startsWith("trace") })
    }

    @Test
    fun `bindings and other values still show`() {
        val out = lines(event("""{"sql":"select 1","time_ms":4.2,"bindings":[1,2]}"""))

        assertTrue(out.any { it.text.contains("select 1") })
        assertTrue(out.any { it.text.contains("4.2") })
        assertTrue(out.any { it.text.contains("[1,2]") })
    }

    @Test
    fun `an event with no trace falls back to its own caller`() {
        val out = lines(event("""{"sql":"select 1"}""", src = DumpSource("/home/dev/app/app/Repo.php", 9)))

        assertEquals("/home/dev/app/app/Repo.php:9", out.first { it.style == DumpDetail.Style.ORIGIN }.text.trim())
    }

    @Test
    fun `an all-vendor trace still names an origin rather than none`() {
        val vendorOnly = """[{"file":"/home/dev/app/vendor/x/y.php","line":3,"func":"y"}]"""
        val out = lines(event("""{"trace":$vendorOnly}"""))

        assertEquals("/home/dev/app/vendor/x/y.php:3", out.first { it.style == DumpDetail.Style.ORIGIN }.text.trim())
    }

    @Test
    fun `a dump with rendered text shows the text rather than its fields`() {
        val e = DumpEvent(id = "1", ts = "t", kind = "dump", text = "array:1 [\n  0 => 1\n]")

        assertTrue(DumpDetail.of(e).any { it.text.contains("0 => 1") })
    }
}

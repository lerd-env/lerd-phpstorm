package sh.lerd.ide.logs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sh.lerd.ide.api.DumpEvent

/**
 * Renders one captured event for the detail pane.
 *
 * The payload's own fields are printed as a short list, and the stack trace
 * gets a line per frame written as file:line so the console links it into the
 * editor. The frame that matters is the first one outside vendor/, which for a
 * framework event is many frames down and the only one worth opening, so it is
 * pulled out and printed first.
 */
object DumpDetail {
    enum class Style { HEADING, ORIGIN, FIELD, FRAME, VENDOR_FRAME, TEXT }

    data class Line(val text: String, val style: Style)

    fun of(event: DumpEvent): List<Line> {
        val out = mutableListOf<Line>()
        val frames = frames(event)

        origin(event, frames)?.let {
            out += Line("thrown from", Style.HEADING)
            out += Line("  $it", Style.ORIGIN)
            out += Line("", Style.TEXT)
        }

        event.text?.takeIf { it.isNotBlank() }?.let {
            out += Line(it, Style.TEXT)
            out += Line("", Style.TEXT)
        }

        val fields = event.data.orEmpty().filterKeys { it != TRACE }
        if (fields.isNotEmpty()) {
            fields.forEach { (key, value) -> out += Line("$key: ${render(value)}", Style.FIELD) }
            out += Line("", Style.TEXT)
        }

        if (frames.isNotEmpty()) {
            out += Line("stack", Style.HEADING)
            frames.forEach { frame ->
                val style = if (frame.vendor) Style.VENDOR_FRAME else Style.FRAME
                out += Line("  ${frame.file}:${frame.line}  ${frame.func}", style)
            }
        }
        return out
    }

    private data class Frame(val file: String, val line: Int, val func: String, val vendor: Boolean)

    private fun frames(event: DumpEvent): List<Frame> {
        val raw = event.data?.get(TRACE) as? JsonArray ?: return emptyList()
        return raw.mapNotNull { element ->
            val frame = element as? JsonObject ?: return@mapNotNull null
            val file = frame.string("file") ?: return@mapNotNull null
            val line = frame.string("line")?.toIntOrNull() ?: return@mapNotNull null
            Frame(file, line, frame.string("func").orEmpty(), file.contains("/vendor/"))
        }
    }

    private fun origin(event: DumpEvent, frames: List<Frame>): String? {
        frames.firstOrNull { !it.vendor }?.let { return "${it.file}:${it.line}" }
        event.src?.takeIf { it.file.isNotBlank() && it.line > 0 }?.let { return "${it.file}:${it.line}" }
        return frames.firstOrNull()?.let { "${it.file}:${it.line}" }
    }

    private fun render(value: kotlinx.serialization.json.JsonElement): String =
        (value as? JsonPrimitive)?.content ?: value.toString()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private const val TRACE = "trace"
}

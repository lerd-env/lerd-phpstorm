package sh.lerd.ide.api

import kotlinx.serialization.json.Json

/**
 * One Json instance for the whole plugin, configured to survive an API that
 * grows without notice: unknown keys are dropped and an explicit null is read
 * as the field's default rather than blowing up a non-nullable property.
 */
object LerdJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    fun decodeSites(body: String): List<LerdSite> = format.decodeFromString(body)

    fun decodeStatus(body: String): LerdStatus = format.decodeFromString(body)
}

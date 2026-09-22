package sh.lerd.ide.api

/**
 * Every call answers with one of these. The daemon being down, a route that
 * moved, and a body we cannot parse are all ordinary outcomes here, because an
 * IDE plugin that throws on a stopped daemon is a plugin people disable.
 */
sealed interface LerdResult<out T> {
    data class Ok<T>(val value: T) : LerdResult<T>

    data class Err(val message: String, val cause: Throwable? = null) : LerdResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value
}

package sh.lerd.ide.api

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Talks to the lerd-ui daemon over TCP loopback, the same API the dashboard
 * uses. Three rules come from lerd's gate in internal/ui/remote_control.go:
 * mutations must carry X-Lerd-CSRF, and the request must send neither an Origin
 * nor an X-Forwarded-* header, either of which demotes us out of local
 * authority. The Linux unix socket would skip the CSRF check, but
 * java.net.http cannot dial one.
 *
 * baseUrl is a supplier rather than a value so a settings change takes effect
 * without rebuilding the client.
 */
class LerdClient(
    private val baseUrl: () -> String = { DEFAULT_BASE_URL },
) : AutoCloseable {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun status(): LerdResult<LerdStatus> = get("/api/status", LerdJson::decodeStatus)

    fun sites(): LerdResult<List<LerdSite>> = get("/api/sites", LerdJson::decodeSites)

    /**
     * Runs one of handleSiteAction's actions (restart, secure, php, worker:...)
     * against a site, identified by domain the way the API keys it.
     */
    fun siteAction(
        domain: String,
        action: String,
        params: Map<String, String> = emptyMap(),
    ): LerdResult<Unit> {
        val path = "/api/sites/${encode(domain)}/$action"
        return post(path, params) { body ->
            val parsed = runCatching { LerdJson.format.decodeFromString<SiteActionResponse>(body) }.getOrNull()
            if (parsed != null && !parsed.ok) throw LerdApiException(parsed.error ?: "the action failed")
        }
    }

    fun commands(domain: String, branch: String? = null): LerdResult<List<SiteCommand>> =
        get("/api/sites/${encode(domain)}/commands", branch.asParams()) {
            LerdJson.format.decodeFromString<SiteCommandsResponse>(it).commands
        }

    /** The path a command run streams from; the run itself goes through LogStream. */
    fun commandRunPath(domain: String, command: String): String =
        "/api/sites/${encode(domain)}/commands/${encode(command)}/run"

    fun analyzeQueries(site: String, branch: String? = null): LerdResult<QueryAnalysis> =
        get("/api/queries/analyze", branch.asParams() + ("site" to site)) {
            LerdJson.format.decodeFromString(it)
        }

    fun dumps(site: String, limit: Int, branch: String? = null): LerdResult<List<DumpEvent>> =
        get("/api/dumps", branch.asParams() + ("site" to site) + ("limit" to limit.toString())) {
            LerdJson.format.decodeFromString(it)
        }

    /** The SSE path for live dumps; the stream itself goes through LogStream. */
    fun dumpStreamPath(): String = "/api/dumps/stream"

    fun services(): LerdResult<List<LerdService>> =
        get("/api/services") { LerdJson.format.decodeFromString(it) }

    fun phpVersions(): LerdResult<List<String>> =
        get("/api/php-versions") { LerdJson.format.decodeFromString(it) }

    fun nodeVersions(): LerdResult<List<String>> =
        get("/api/node-versions") { LerdJson.format.decodeFromString(it) }

    fun doctor(domain: String, branch: String? = null): LerdResult<DoctorResponse> =
        get("/api/sites/${encode(domain)}/doctor", branch.asParams()) {
            LerdJson.format.decodeFromString(it)
        }

    fun serviceAction(name: String, action: String): LerdResult<Unit> =
        post("/api/services/${encode(name)}/$action", emptyMap()) { }

    fun appLogFiles(domain: String, branch: String? = null): LerdResult<List<AppLogFile>> =
        get("/api/app-logs/${encode(domain)}", branch.asParams()) {
            LerdJson.format.decodeFromString<AppLogFilesResponse>(it).files
        }

    fun appLogEntries(
        domain: String,
        file: String,
        limit: Int,
        branch: String? = null,
    ): LerdResult<List<AppLogEntry>> =
        get(
            "/api/app-logs/${encode(domain)}/${encode(file)}",
            branch.asParams() + ("limit" to limit.toString()),
        ) {
            LerdJson.format.decodeFromString<AppLogEntriesResponse>(it).entries
        }

    private fun String?.asParams(): Map<String, String> =
        this?.let { mapOf("branch" to it) } ?: emptyMap()

    private fun <T> get(
        path: String,
        params: Map<String, String>,
        parse: (String) -> T,
    ): LerdResult<T> = send(request(path, params).GET().build(), parse)

    private fun <T> get(path: String, parse: (String) -> T): LerdResult<T> =
        send(request(path, emptyMap()).GET().build(), parse)

    private fun <T> post(path: String, params: Map<String, String>, parse: (String) -> T): LerdResult<T> =
        send(
            request(path, params)
                .header(CSRF_HEADER, "1")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            parse,
        )

    private fun request(path: String, params: Map<String, String>): HttpRequest.Builder {
        val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val url = baseUrl().trimEnd('/') + path + if (query.isEmpty()) "" else "?$query"
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Accept", "application/json")
    }

    private fun <T> send(request: HttpRequest, parse: (String) -> T): LerdResult<T> = try {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val body = response.body().orEmpty()
        if (response.statusCode() >= 400) {
            LerdResult.Err(errorMessage(body, response.statusCode()))
        } else {
            LerdResult.Ok(parse(body))
        }
    } catch (e: LerdApiException) {
        LerdResult.Err(e.message.orEmpty(), e)
    } catch (e: IOException) {
        LerdResult.Err("Lerd is not reachable", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        LerdResult.Err("interrupted", e)
    } catch (e: RuntimeException) {
        // A shape we cannot parse is a broken contract, not a crash: say so and
        // let the caller render "unavailable".
        LerdResult.Err("Lerd answered with something this plugin cannot read", e)
    }

    /**
     * Handlers are inconsistent: some answer {"ok":false,"error":...}, others
     * plain text via http.Error. Fold both into one message, as the dashboard's
     * decodeJSONText does.
     */
    private fun errorMessage(body: String, status: Int): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "Lerd answered HTTP $status"
        val parsed = runCatching {
            LerdJson.format.decodeFromString<SiteActionResponse>(trimmed)
        }.getOrNull()
        return parsed?.error?.takeIf { it.isNotBlank() } ?: trimmed
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    override fun close() {
        http.close()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:7073"
        private const val CSRF_HEADER = "X-Lerd-CSRF"
        private const val REQUEST_TIMEOUT_SECONDS = 10L
    }
}

private class LerdApiException(message: String) : RuntimeException(message)

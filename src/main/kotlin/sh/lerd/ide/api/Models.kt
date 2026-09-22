package sh.lerd.ide.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slice of lerd's dashboard payloads the plugin reads. The API carries no
 * version, so every field here is optional with a harmless default: a missing
 * key means "the daemon did not say", never a crash.
 */

@Serializable
data class LerdSite(
    val name: String? = null,
    val domain: String = "",
    val domains: List<String> = emptyList(),
    val path: String? = null,
    @SerialName("php_version") val phpVersion: String? = null,
    @SerialName("node_version") val nodeVersion: String? = null,
    val runtime: String? = null,
    val framework: String? = null,
    @SerialName("framework_label") val frameworkLabel: String? = null,
    val tls: Boolean = false,
    @SerialName("fpm_running") val fpmRunning: Boolean = false,
    val paused: Boolean = false,
    val pinned: Boolean = false,
    @SerialName("idle_suspended") val idleSuspended: Boolean = false,
    @SerialName("has_app_logs") val hasAppLogs: Boolean = false,
    @SerialName("db_database") val database: String? = null,
    @SerialName("custom_container") val customContainer: Boolean = false,
    @SerialName("php_log_unit") val phpLogUnit: String? = null,
    @SerialName("has_queue_worker") val hasQueueWorker: Boolean = false,
    @SerialName("has_schedule_worker") val hasScheduleWorker: Boolean = false,
    @SerialName("has_horizon") val hasHorizon: Boolean = false,
    @SerialName("has_reverb") val hasReverb: Boolean = false,
    @SerialName("queue_running") val queueRunning: Boolean = false,
    @SerialName("queue_failing") val queueFailing: Boolean = false,
    @SerialName("horizon_running") val horizonRunning: Boolean = false,
    @SerialName("horizon_failing") val horizonFailing: Boolean = false,
    @SerialName("schedule_running") val scheduleRunning: Boolean = false,
    @SerialName("schedule_failing") val scheduleFailing: Boolean = false,
    @SerialName("reverb_running") val reverbRunning: Boolean = false,
    @SerialName("reverb_failing") val reverbFailing: Boolean = false,
    @SerialName("stripe_running") val stripeRunning: Boolean = false,
    @SerialName("stripe_secret_set") val stripeSecretSet: Boolean = false,
    @SerialName("doctor_applicable") val doctorApplicable: Boolean = false,
    val services: List<String> = emptyList(),
    @SerialName("framework_workers") val workers: List<LerdWorker> = emptyList(),
    val worktrees: List<LerdWorktree> = emptyList(),
)

@Serializable
data class LerdWorker(
    val name: String = "",
    val label: String? = null,
    val running: Boolean = false,
    val failing: Boolean = false,
    val unreachable: Boolean = false,
)

@Serializable
data class LerdWorktree(
    val branch: String? = null,
    val domain: String? = null,
    val path: String? = null,
    @SerialName("php_version") val phpVersion: String? = null,
    @SerialName("node_version") val nodeVersion: String? = null,
    @SerialName("framework_label") val frameworkLabel: String? = null,
    @SerialName("db_isolated") val dbIsolated: Boolean = false,
    @SerialName("framework_workers") val workers: List<LerdWorker> = emptyList(),
)

@Serializable
data class LerdStatus(
    val dns: LerdCheck = LerdCheck(),
    val nginx: LerdCheck = LerdCheck(),
    @SerialName("php_fpms") val phpFpms: List<LerdPhp> = emptyList(),
    @SerialName("php_default") val phpDefault: String? = null,
    @SerialName("node_default") val nodeDefault: String? = null,
    @SerialName("watcher_running") val watcherRunning: Boolean = false,
    val home: String? = null,
    /** Changes when lerd-ui restarts; the only compatibility signal the API has. */
    val instance: String = "",
)

@Serializable
data class LerdCheck(val running: Boolean = false)

@Serializable
data class LerdPhp(
    val version: String = "",
    val running: Boolean = false,
    @SerialName("xdebug_enabled") val xdebugEnabled: Boolean = false,
    @SerialName("xdebug_mode") val xdebugMode: String? = null,
)

/** Every site action answers with this, whether it succeeded or not. */
@Serializable
data class SiteActionResponse(
    val ok: Boolean = true,
    val error: String? = null,
)

@Serializable
data class AppLogFilesResponse(val files: List<AppLogFile> = emptyList())

@Serializable
data class AppLogFile(
    val name: String = "",
    val size: Long = 0,
    @SerialName("mod_time") val modifiedAt: String? = null,
)

@Serializable
data class AppLogEntriesResponse(val entries: List<AppLogEntry> = emptyList())

@Serializable
data class AppLogEntry(
    val level: String = "",
    val date: String = "",
    val channel: String? = null,
    val message: String = "",
    /** The full record including the stack trace, when the line carried one. */
    val detail: String? = null,
)

@Serializable
data class LerdService(
    val name: String = "",
    /** "active" when the container is up; anything else is treated as down. */
    val status: String = "",
    val version: String? = null,
    val category: String? = null,
    val dashboard: String? = null,
    val port: Int? = null,
    @SerialName("connection_url") val connectionUrl: String? = null,
    @SerialName("site_domains") val siteDomains: List<String> = emptyList(),
) {
    val running: Boolean get() = status == "active"
}

@Serializable
data class DoctorResponse(
    val checks: List<DoctorCheck> = emptyList(),
    val failures: Int = 0,
    val warnings: Int = 0,
)

@Serializable
data class DoctorCheck(
    val name: String = "",
    val label: String = "",
    val status: String = "",
    val detail: String? = null,
    /** Names a command from the site's command set that resolves the finding. */
    val fix: String? = null,
)

@Serializable
data class SiteCommandsResponse(val commands: List<SiteCommand> = emptyList())

@Serializable
data class SiteCommand(
    val name: String = "",
    val label: String = "",
    val command: String = "",
    val description: String? = null,
    /** True when the definition wants the user asked before it runs. */
    val confirm: Boolean = false,
)

@Serializable
data class DumpEvent(
    val id: String = "",
    val ts: String = "",
    /** "var" for a dump() call, "query", "job", "view", "mail". */
    val kind: String = "",
    val ctx: DumpContext = DumpContext(),
    val src: DumpSource? = null,
    val label: String? = null,
    /** The rendered VarDumper output for a dump; empty for other kinds. */
    val text: String? = null,
    /** Kind-specific fields, whose shape the daemon does not promise. */
    val data: kotlinx.serialization.json.JsonObject? = null,
    val trunc: Boolean = false,
)

@Serializable
data class DumpContext(
    val type: String? = null,
    val site: String? = null,
    val domain: String? = null,
    val request: String? = null,
    val worker: String? = null,
    val command: String? = null,
    val test: Boolean = false,
)

@Serializable
data class DumpSource(val file: String = "", val line: Int = 0)

@Serializable
data class QueryAnalysis(
    val requests: List<RequestAnalysis> = emptyList(),
    val summary: QuerySummary = QuerySummary(),
)

@Serializable
data class QuerySummary(
    @SerialName("requests_analyzed") val requestsAnalyzed: Int = 0,
    @SerialName("n_plus_one_findings") val nPlusOneFindings: Int = 0,
    @SerialName("slow_findings") val slowFindings: Int = 0,
)

@Serializable
data class RequestAnalysis(
    val site: String? = null,
    val request: String? = null,
    val worker: String? = null,
    @SerialName("query_count") val queryCount: Int = 0,
    @SerialName("total_time_ms") val totalTimeMs: Double = 0.0,
    @SerialName("n_plus_one") val nPlusOne: List<NPlusOneFinding> = emptyList(),
    val slow: List<SlowFinding> = emptyList(),
)

@Serializable
data class NPlusOneFinding(
    val fingerprint: String = "",
    val count: Int = 0,
    @SerialName("total_time_ms") val totalTimeMs: Double = 0.0,
    @SerialName("sample_sql") val sampleSql: String = "",
    val caller: AnalyzedCaller = AnalyzedCaller(),
)

@Serializable
data class SlowFinding(
    val sql: String = "",
    @SerialName("time_ms") val timeMs: Double = 0.0,
    val caller: AnalyzedCaller = AnalyzedCaller(),
)

@Serializable
data class AnalyzedCaller(val file: String = "", val line: Int = 0)

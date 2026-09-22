package sh.lerd.ide.logs

import sh.lerd.ide.api.AnalyzedCaller
import sh.lerd.ide.api.QueryAnalysis
import kotlin.math.roundToLong

/** One thing worth fixing, with the line to fix it on. */
data class QueryFinding(
    val kind: String,
    val title: String,
    val request: String,
    val detail: String,
    /** The statement itself, for opening in a SQL editor. */
    val sql: String,
    val caller: AnalyzedCaller?,
    /** How much this costs, used only to put the worst first. */
    val weight: Double,
)

/**
 * Flattens lerd's query report into one ranked list. The dashboard groups by
 * request because it has the room; in a tool window the useful question is
 * "what do I fix first".
 */
object QueryFindings {
    fun of(analysis: QueryAnalysis): List<QueryFinding> = analysis.requests
        .flatMap { request ->
            val where = request.request ?: request.worker.orEmpty()

            val repeats = request.nPlusOne.map { finding ->
                QueryFinding(
                    kind = "N+1",
                    title = "${finding.count}x  ${finding.fingerprint}",
                    request = where,
                    detail = buildString {
                        appendLine("${finding.count} repeats, ${finding.totalTimeMs.round()}ms in total")
                        appendLine()
                        appendLine(finding.sampleSql)
                    },
                    sql = finding.sampleSql.ifBlank { finding.fingerprint },
                    caller = finding.caller.orNull(),
                    weight = finding.count * 10.0 + finding.totalTimeMs,
                )
            }

            val slow = request.slow.map { finding ->
                QueryFinding(
                    kind = "slow",
                    title = "${finding.timeMs.round()}ms  ${finding.sql}",
                    request = where,
                    detail = finding.sql,
                    sql = finding.sql,
                    caller = finding.caller.orNull(),
                    weight = finding.timeMs,
                )
            }

            repeats + slow
        }
        .sortedByDescending { it.weight }

    private fun AnalyzedCaller.orNull(): AnalyzedCaller? = takeIf { it.file.isNotBlank() && it.line > 0 }

    private fun Double.round(): Long = roundToLong()
}

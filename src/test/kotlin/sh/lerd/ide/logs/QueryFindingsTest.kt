package sh.lerd.ide.logs

import sh.lerd.ide.api.AnalyzedCaller
import sh.lerd.ide.api.NPlusOneFinding
import sh.lerd.ide.api.QueryAnalysis
import sh.lerd.ide.api.RequestAnalysis
import sh.lerd.ide.api.SlowFinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryFindingsTest {
    private fun caller(file: String, line: Int) = AnalyzedCaller(file, line)

    @Test
    fun `an n plus one is listed with its repeat count and caller`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(
                        request = "GET /orders",
                        nPlusOne = listOf(
                            NPlusOneFinding(
                                fingerprint = "select * from users where id = ?",
                                count = 12,
                                totalTimeMs = 31.0,
                                sampleSql = "select * from users where id = 4",
                                caller = caller("/app/Http/OrderController.php", 88),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val finding = findings.single()
        assertEquals("N+1", finding.kind)
        assertEquals("12x  select * from users where id = ?", finding.title)
        assertEquals("GET /orders", finding.request)
        assertEquals("/app/Http/OrderController.php", finding.caller?.file)
        assertEquals(88, finding.caller?.line)
    }

    @Test
    fun `a slow query is listed with how long it took`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(
                        request = "GET /reports",
                        slow = listOf(
                            SlowFinding("select * from orders", 340.5, caller("/app/Repo.php", 21)),
                        ),
                    ),
                ),
            ),
        )

        val finding = findings.single()
        assertEquals("slow", finding.kind)
        assertEquals("341ms  select * from orders", finding.title)
    }

    @Test
    fun `the worst offenders come first`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(
                        request = "GET /x",
                        nPlusOne = listOf(
                            NPlusOneFinding(fingerprint = "a", count = 3, totalTimeMs = 5.0),
                            NPlusOneFinding(fingerprint = "b", count = 40, totalTimeMs = 9.0),
                        ),
                        slow = listOf(SlowFinding("c", 900.0)),
                    ),
                ),
            ),
        )

        // A 900ms query and a 40x repeat both matter more than a 3x repeat.
        assertEquals(listOf("slow", "N+1", "N+1"), findings.map { it.kind })
        assertTrue(findings[1].title.startsWith("40x"))
    }

    @Test
    fun `a caller the analyzer could not place is left out rather than faked`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(request = "GET /y", slow = listOf(SlowFinding("q", 200.0))),
                ),
            ),
        )

        assertEquals(null, findings.single().caller)
    }

    @Test
    fun `a report with nothing in it is an empty list`() {
        assertTrue(QueryFindings.of(QueryAnalysis()).isEmpty())
    }

    @Test
    fun `a finding carries the statement so it can be opened in a SQL editor`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(
                        request = "GET /orders",
                        nPlusOne = listOf(
                            NPlusOneFinding(
                                fingerprint = "select * from users where id = ?",
                                count = 4,
                                sampleSql = "select * from users where id = 7",
                            ),
                        ),
                        slow = listOf(SlowFinding("select * from orders", 500.0)),
                    ),
                ),
            ),
        )

        assertEquals("select * from orders", findings.first { it.kind == "slow" }.sql)
        // A concrete sample runs; the collapsed fingerprint is the fallback.
        assertEquals("select * from users where id = 7", findings.first { it.kind == "N+1" }.sql)
    }

    @Test
    fun `a finding with no sample falls back to the fingerprint`() {
        val findings = QueryFindings.of(
            QueryAnalysis(
                requests = listOf(
                    RequestAnalysis(
                        request = "GET /x",
                        nPlusOne = listOf(NPlusOneFinding(fingerprint = "select 1", count = 2)),
                    ),
                ),
            ),
        )

        assertEquals("select 1", findings.single().sql)
    }
}

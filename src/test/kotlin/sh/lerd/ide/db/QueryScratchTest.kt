package sh.lerd.ide.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryScratchTest {
    @Test
    fun `the sample query is what gets written, with the finding above it`() {
        val text = QueryScratch.text(
            kind = "N+1",
            request = "GET /orders",
            sql = "select * from users where id = 4",
        )

        assertTrue(text.startsWith("-- N+1 in GET /orders\n"))
        assertTrue(text.trimEnd().endsWith("select * from users where id = 4;"))
    }

    @Test
    fun `a query that already ends in a semicolon does not get a second one`() {
        val text = QueryScratch.text("slow", "GET /x", "select 1;")

        assertTrue(text.trimEnd().endsWith("select 1;"))
        assertTrue(!text.trimEnd().endsWith(";;"))
    }

    @Test
    fun `a placeholder query is left runnable rather than half rewritten`() {
        // An N+1 fingerprint keeps its ? bindings; the user fills them in.
        val text = QueryScratch.text("N+1", "GET /x", "select * from users where id = ?")

        assertTrue(text.contains("select * from users where id = ?;"))
    }

    @Test
    fun `the file name says which site it belongs to`() {
        assertEquals("myapp-query.sql", QueryScratch.fileName("myapp.test"))
        assertEquals("query.sql", QueryScratch.fileName(""))
    }
}

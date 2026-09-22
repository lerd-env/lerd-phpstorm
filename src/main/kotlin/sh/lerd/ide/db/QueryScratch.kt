package sh.lerd.ide.db

/**
 * The SQL scratch a finding opens into. It is a plain .sql file rather than a
 * console of our own making, so it runs with PhpStorm's own Ctrl+Enter against
 * whichever data source the editor is attached to.
 */
object QueryScratch {
    fun text(kind: String, request: String, sql: String): String = buildString {
        append("-- ").append(kind)
        if (request.isNotBlank()) append(" in ").append(request)
        appendLine()
        appendLine("-- opened from Lerd")
        appendLine()
        append(sql.trimEnd().removeSuffix(";")).appendLine(";")
    }

    fun fileName(domain: String): String =
        domain.substringBefore('.').takeIf { it.isNotBlank() }?.let { "$it-query.sql" } ?: "query.sql"
}

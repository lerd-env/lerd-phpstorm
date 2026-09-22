package sh.lerd.ide.logs

/**
 * Finds the file references in a log line that are worth turning into links:
 * the `path.php(42)` form PHP stack traces use and the `path.php:42` form
 * exceptions, linters and most tooling use.
 *
 * Paths may be relative, because FPM logs them as the container sees them
 * (/app/...) and resolution happens later against the site root.
 */
object FileLineMatcher {
    data class Hit(val path: String, val line: Int, val start: Int, val end: Int)

    private const val MAX_LINE = 1_000_000

    private val EXTENSIONS = setOf("php", "js", "ts", "jsx", "tsx", "vue", "svelte", "twig", "blade")

    // A path segment, then an extension, then either (42) or :42. The path is
    // kept deliberately loose; the extension is what proves it is a file and
    // not a host:port.
    private val PATTERN = Regex(
        """([\w./\-@]*[\w\-@]+\.([A-Za-z]+))(?:\((\d+)\)|:(\d+))""",
    )

    fun find(text: String): List<Hit> = PATTERN.findAll(text).mapNotNull { match ->
        val path = match.groupValues[1]
        val extension = match.groupValues[2].lowercase()
        if (extension !in EXTENSIONS) return@mapNotNull null

        val line = (match.groupValues[3].takeIf { it.isNotEmpty() } ?: match.groupValues[4])
            .toIntOrNull() ?: return@mapNotNull null
        if (line !in 1..MAX_LINE) return@mapNotNull null

        Hit(path, line, match.range.first, match.range.last + 1)
    }.toList()
}

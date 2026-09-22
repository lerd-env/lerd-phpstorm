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

    private val BARE_PATTERN = Regex("""([\w./\-@]*[\w\-@]+\.([A-Za-z]+))""")

    /**
     * Line-numbered references first, then bare paths for the events that name
     * a file and nothing else, such as a view render. A bare match must contain
     * a directory separator, so a file name mentioned in prose stays prose, and
     * must not sit inside a URL.
     */
    fun find(text: String): List<Hit> {
        val located = withLines(text)
        val taken = located.map { it.start until it.end }
        return (located + bare(text).filterNot { hit -> taken.any { hit.start in it } })
            .sortedBy { it.start }
    }

    private fun bare(text: String): List<Hit> = BARE_PATTERN.findAll(text).mapNotNull { match ->
        val path = match.groupValues[1]
        if (!path.contains('/')) return@mapNotNull null
        if (match.groupValues[2].lowercase() !in EXTENSIONS) return@mapNotNull null
        // Inside a URL the slashes are not a filesystem path.
        if (match.range.first > 0 && text[match.range.first - 1] == ':') return@mapNotNull null
        Hit(path, 1, match.range.first, match.range.last + 1)
    }.toList()

    private fun withLines(text: String): List<Hit> = PATTERN.findAll(text).mapNotNull { match ->
        val path = match.groupValues[1]
        val extension = match.groupValues[2].lowercase()
        if (extension !in EXTENSIONS) return@mapNotNull null

        val line = (match.groupValues[3].takeIf { it.isNotEmpty() } ?: match.groupValues[4])
            .toIntOrNull() ?: return@mapNotNull null
        if (line !in 1..MAX_LINE) return@mapNotNull null

        Hit(path, line, match.range.first, match.range.last + 1)
    }.toList()
}

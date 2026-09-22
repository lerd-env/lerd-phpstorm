package sh.lerd.ide.services

import java.net.URI

/** The command line that opens a shell client for a service. */
data class ClientCommand(val binary: String, val args: List<String>) {
    /** For showing the user what will run, with any password hidden. */
    fun display(): String {
        val shown = mutableListOf(binary)
        var maskNext = false
        for (arg in args) {
            shown += when {
                maskNext -> "*****"
                arg.startsWith("--password=") -> "--password=*****"
                else -> arg
            }
            maskNext = arg == "-a"
        }
        return shown.joinToString(" ")
    }
}

/**
 * Turns the connection URL lerd publishes for a service into the client command
 * for it. lerd ships the clients itself as shims, so the binary name is all the
 * caller needs; a service with no shell client simply gets no command.
 */
object ServiceClients {
    /**
     * lerd ships its own client binaries so the versions match the containers.
     * Prefer them; fall back to whatever is on PATH when a shim is absent.
     */
    fun resolveBinary(binary: String, shimDir: java.nio.file.Path = defaultShimDir()): String {
        val shim = shimDir.resolve(binary)
        return if (java.nio.file.Files.isExecutable(shim)) shim.toString() else binary
    }

    fun defaultShimDir(): java.nio.file.Path {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        val base = xdg?.let { java.nio.file.Path.of(it) }
            ?: java.nio.file.Path.of(System.getProperty("user.home"), ".local", "share")
        return base.resolve("lerd").resolve("bin")
    }

    fun of(connectionUrl: String?, siteDatabase: String? = null): ClientCommand? {
        val url = connectionUrl?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 }?.toString()
        val (user, password) = credentials(uri)
        val urlDatabase = uri.path.orEmpty().trimStart('/').takeIf { it.isNotBlank() }
        val database = usableDatabase(siteDatabase) ?: urlDatabase

        return when (uri.scheme?.lowercase()) {
            "mysql", "mariadb" -> ClientCommand(
                "mysql",
                buildList {
                    add("-h"); add(host)
                    port?.let { add("-P"); add(it) }
                    user?.let { add("-u"); add(it) }
                    password?.let { add("--password=$it") }
                    database?.let { add(it) }
                },
            )

            "postgres", "postgresql" -> ClientCommand("psql", listOf(withDatabase(url, database)))

            "redis", "rediss" -> ClientCommand(
                "redis-cli",
                buildList {
                    add("-h"); add(host)
                    port?.let { add("-p"); add(it) }
                    password?.let { add("-a"); add(it) }
                    urlDatabase?.toIntOrNull()?.let { add("-n"); add(it.toString()) }
                },
            )

            "mongodb", "mongodb+srv" -> ClientCommand("mongosh", listOf(url))

            else -> null
        }
    }

    private fun credentials(uri: URI): Pair<String?, String?> {
        val info = uri.userInfo ?: return null to null
        val user = info.substringBefore(':').takeIf { it.isNotBlank() }
        val password = info.substringAfter(':', "").takeIf { it.isNotBlank() }
        return user to password
    }

    /** A sqlite site reports a file path here, which is not a name to hand a client. */
    private fun usableDatabase(name: String?): String? =
        name?.takeIf { it.isNotBlank() && !it.contains('/') && !it.endsWith(".sqlite") }

    private fun withDatabase(url: String, database: String?): String {
        if (database == null) return url
        val cut = url.indexOf('/', url.indexOf("//") + 2)
        return if (cut < 0) "$url/$database" else url.substring(0, cut + 1) + database
    }
}

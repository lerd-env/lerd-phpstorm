package sh.lerd.ide.db

import java.net.URI

/** Everything needed to open one lerd service in the IDE's database tools. */
data class DataSourceTarget(
    val name: String,
    val url: String,
    val user: String?,
    val password: String?,
    /** The database to land on, not just connect to. */
    val database: String?,
    /** Driver ids to try in order; IDE builds differ on which they ship. */
    val driverCandidates: List<String>,
)

/**
 * Turns the connection URL lerd publishes into a data source the Database tool
 * window can open. The coordinates are already host-facing, so only the scheme
 * and the database name need work.
 */
object DataSourceTargets {
    fun of(service: String, connectionUrl: String?, siteDatabase: String? = null): DataSourceTarget? {
        val url = connectionUrl?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val (user, password) = credentials(uri)

        val urlDatabase = uri.path.orEmpty().trimStart('/').takeIf { it.isNotBlank() }
        val database = usableDatabase(siteDatabase) ?: urlDatabase

        // Ids as the IDE's own driver definitions declare them, newest first.
        val drivers = when (scheme) {
            "mysql" -> listOf("mysql.8", "mysql.base")
            "mariadb" -> listOf("mariadb", "mysql.8", "mysql.base")
            "postgres", "postgresql" -> listOf("postgresql")
            "redis", "rediss" -> listOf("redis")
            "mongodb", "mongodb+srv" -> listOf("mongo.base")
            else -> return null
        }

        val jdbcScheme = when (scheme) {
            "postgres" -> "postgresql"
            "rediss" -> "redis"
            else -> scheme
        }

        // Mongo's driver takes the native URL; everything else takes jdbc:.
        val target = if (jdbcScheme.startsWith("mongodb")) {
            "$jdbcScheme://$host:$port" + (database?.let { "/$it" } ?: "")
        } else {
            "jdbc:$jdbcScheme://$host:$port" + (database?.let { "/$it" } ?: "")
        }

        return DataSourceTarget(
            name = (database ?: host) + " (Lerd $service)",
            url = target,
            user = user,
            password = password,
            database = database,
            driverCandidates = drivers,
        )
    }

    /** Same server, whatever database the existing entry happens to point at. */
    fun sameServer(a: String, b: String): Boolean = serverPart(a) == serverPart(b)

    private fun serverPart(url: String): String =
        url.substringBeforeLast('/').takeIf { it.count { c -> c == '/' } >= 2 } ?: url

    private fun credentials(uri: URI): Pair<String?, String?> {
        val info = uri.userInfo ?: return null to null
        return info.substringBefore(':').takeIf { it.isNotBlank() } to
            info.substringAfter(':', "").takeIf { it.isNotBlank() }
    }

    /** A sqlite site reports a file path here, which is not a database to dial. */
    private fun usableDatabase(name: String?): String? =
        name?.takeIf { it.isNotBlank() && !it.contains('/') && !it.endsWith(".sqlite") }
}

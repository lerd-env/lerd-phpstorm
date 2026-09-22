package sh.lerd.ide.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataSourceTargetsTest {
    @Test
    fun `mysql becomes a jdbc url the IDE can open`() {
        val target = DataSourceTargets.of("mysql", "mysql://root:lerd@127.0.0.1:3307/lerd")!!

        assertEquals("jdbc:mysql://127.0.0.1:3307/lerd", target.url)
        assertEquals("root", target.user)
        assertEquals("lerd", target.password)
        // The IDE ships these ids; a plain "mysql" matches no driver at all.
        assertEquals(listOf("mysql.8", "mysql.base"), target.driverCandidates)
    }

    @Test
    fun `the site's own database is what gets opened`() {
        val target = DataSourceTargets.of("mysql", "mysql://root:lerd@127.0.0.1:3307/lerd", "shop")!!

        assertEquals("jdbc:mysql://127.0.0.1:3307/shop", target.url)
        assertEquals("shop (Lerd mysql)", target.name)
        assertEquals("shop", target.database)
    }

    @Test
    fun `a sqlite path is not a database to open over the network`() {
        val target = DataSourceTargets.of("mysql", "mysql://root@127.0.0.1:3307/lerd", "database/db.sqlite")!!

        assertEquals("jdbc:mysql://127.0.0.1:3307/lerd", target.url)
    }

    @Test
    fun `postgres maps to its own driver`() {
        val target = DataSourceTargets.of("postgres", "postgresql://postgres:lerd@127.0.0.1:5434/lerd")!!

        assertEquals("jdbc:postgresql://127.0.0.1:5434/lerd", target.url)
        assertEquals(listOf("postgresql"), target.driverCandidates)
    }

    @Test
    fun `mariadb prefers its own driver but accepts mysql's`() {
        val target = DataSourceTargets.of("mariadb", "mariadb://root@127.0.0.1:3306/app")!!

        assertEquals(listOf("mariadb", "mysql.8", "mysql.base"), target.driverCandidates)
        assertEquals("jdbc:mariadb://127.0.0.1:3306/app", target.url)
    }

    @Test
    fun `redis keeps its own url shape`() {
        val target = DataSourceTargets.of("redis", "redis://127.0.0.1:6379")!!

        assertEquals("jdbc:redis://127.0.0.1:6379", target.url)
        assertEquals(listOf("redis"), target.driverCandidates)
        assertNull(target.user)
        assertNull(target.database)
    }

    @Test
    fun `mongodb is handed over unchanged`() {
        assertEquals("mongodb://127.0.0.1:27017", DataSourceTargets.of("mongo", "mongodb://127.0.0.1:27017")!!.url)
    }

    @Test
    fun `a service the database tools cannot open has no target`() {
        assertNull(DataSourceTargets.of("mailpit", "smtp://127.0.0.1:1025"))
        assertNull(DataSourceTargets.of("mailpit", null))
        assertNull(DataSourceTargets.of("x", "not a url"))
    }

    @Test
    fun `two urls for the same server match even when the database differs`() {
        assertEquals(
            true,
            DataSourceTargets.sameServer("jdbc:mysql://127.0.0.1:3307/lerd", "jdbc:mysql://127.0.0.1:3307/shop"),
        )
        assertEquals(
            false,
            DataSourceTargets.sameServer("jdbc:mysql://127.0.0.1:3307/lerd", "jdbc:mysql://127.0.0.1:3306/lerd"),
        )
    }
}

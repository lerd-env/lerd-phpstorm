package sh.lerd.ide.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceClientsTest {
    @Test
    fun `mysql connects with the host coordinates lerd published`() {
        val cmd = ServiceClients.of("mysql://root:lerd@127.0.0.1:3307/lerd")!!

        assertEquals("mysql", cmd.binary)
        assertEquals(listOf("-h", "127.0.0.1", "-P", "3307", "-u", "root", "--password=lerd", "lerd"), cmd.args)
    }

    @Test
    fun `the site's own database wins over the service default`() {
        val cmd = ServiceClients.of("mysql://root:lerd@127.0.0.1:3307/lerd", siteDatabase = "shop")!!

        assertEquals("shop", cmd.args.last())
    }

    @Test
    fun `a sqlite file is not a database name to pass to a client`() {
        // Sites on sqlite report a path here; passing it would open the wrong thing.
        val cmd = ServiceClients.of("mysql://root:lerd@127.0.0.1:3307/lerd", siteDatabase = "database/db.sqlite")!!

        assertEquals("lerd", cmd.args.last())
    }

    @Test
    fun `mariadb uses the mysql client`() {
        assertEquals("mysql", ServiceClients.of("mariadb://root@127.0.0.1:3306/app")!!.binary)
    }

    @Test
    fun `an empty password is left out rather than passed empty`() {
        val cmd = ServiceClients.of("mysql://root@127.0.0.1:3307/lerd")!!

        assertEquals(listOf("-h", "127.0.0.1", "-P", "3307", "-u", "root", "lerd"), cmd.args)
    }

    @Test
    fun `postgres hands psql the whole url`() {
        val cmd = ServiceClients.of("postgresql://postgres:lerd@127.0.0.1:5434/lerd", siteDatabase = "shop")!!

        assertEquals("psql", cmd.binary)
        assertEquals(listOf("postgresql://postgres:lerd@127.0.0.1:5434/shop"), cmd.args)
    }

    @Test
    fun `redis connects by host and port`() {
        val cmd = ServiceClients.of("redis://127.0.0.1:6379")!!

        assertEquals("redis-cli", cmd.binary)
        assertEquals(listOf("-h", "127.0.0.1", "-p", "6379"), cmd.args)
    }

    @Test
    fun `a redis password and database index are carried through`() {
        val cmd = ServiceClients.of("redis://:secret@127.0.0.1:6380/3")!!

        assertEquals(listOf("-h", "127.0.0.1", "-p", "6380", "-a", "secret", "-n", "3"), cmd.args)
    }

    @Test
    fun `mongodb hands mongosh the whole url`() {
        val cmd = ServiceClients.of("mongodb://127.0.0.1:27017")!!

        assertEquals("mongosh", cmd.binary)
    }

    @Test
    fun `a service with no client gets no button`() {
        assertNull(ServiceClients.of("smtp://127.0.0.1:1025"))
        assertNull(ServiceClients.of(null))
        assertNull(ServiceClients.of(""))
    }

    @Test
    fun `a url lerd never produced does not throw`() {
        assertNull(ServiceClients.of("not a url at all"))
    }

    @Test
    fun `the command is shown with the password masked`() {
        val cmd = ServiceClients.of("mysql://root:lerd@127.0.0.1:3307/lerd")!!

        assertEquals("mysql -h 127.0.0.1 -P 3307 -u root --password=***** lerd", cmd.display())
    }

    @Test
    fun `lerd's own client binary is preferred over whatever is on PATH`() {
        val dir = java.nio.file.Files.createTempDirectory("lerd-bin")
        val shim = java.nio.file.Files.createFile(dir.resolve("redis-cli"))
        shim.toFile().setExecutable(true)

        assertEquals(shim.toString(), ServiceClients.resolveBinary("redis-cli", dir))
    }

    @Test
    fun `a missing shim falls back to the bare name`() {
        val dir = java.nio.file.Files.createTempDirectory("lerd-bin")

        assertEquals("mongosh", ServiceClients.resolveBinary("mongosh", dir))
    }
}

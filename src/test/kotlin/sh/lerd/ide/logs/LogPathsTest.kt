package sh.lerd.ide.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogPathsTest {
    @Test
    fun `an absolute path is already the host's`() {
        // lerd mounts the home directory at the same path inside containers,
        // so a path a container logged needs no translation.
        assertEquals(
            listOf("/home/dev/myapp/app/User.php"),
            LogPaths.candidates("/home/dev/myapp/app/User.php", "/home/dev/myapp"),
        )
    }

    @Test
    fun `a relative path is resolved against the site root`() {
        assertEquals(
            listOf("/home/dev/myapp/app/User.php"),
            LogPaths.candidates("app/User.php", "/home/dev/myapp"),
        )
    }

    @Test
    fun `a relative path with no site root resolves to nothing`() {
        assertTrue(LogPaths.candidates("app/User.php", null).isEmpty())
    }

    @Test
    fun `a dotted relative path is normalised`() {
        assertEquals(
            listOf("/home/dev/myapp/app/User.php"),
            LogPaths.candidates("./public/../app/User.php", "/home/dev/myapp"),
        )
    }
}

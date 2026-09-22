package sh.lerd.ide.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLineMatcherTest {
    @Test
    fun `finds a php stack frame`() {
        val line = "#0 /home/dev/myapp/app/Http/Controllers/UserController.php(42): App\\Foo->bar()"

        val hit = FileLineMatcher.find(line).single()

        assertEquals("/home/dev/myapp/app/Http/Controllers/UserController.php", hit.path)
        assertEquals(42, hit.line)
    }

    @Test
    fun `finds the colon form used by exceptions and linters`() {
        val line = "PHP Fatal error: Uncaught TypeError in /home/dev/myapp/app/Models/User.php:88"

        val hit = FileLineMatcher.find(line).single()

        assertEquals("/home/dev/myapp/app/Models/User.php", hit.path)
        assertEquals(88, hit.line)
        assertEquals(line.indexOf("/home"), hit.start)
        assertEquals(line.length, hit.end)
    }

    @Test
    fun `finds a relative path so a container-side log still navigates`() {
        // FPM logs paths as the container sees them, rooted at /app.
        val line = "  at app/Jobs/SendInvoice.php:17"

        val hit = FileLineMatcher.find(line).single()

        assertEquals("app/Jobs/SendInvoice.php", hit.path)
        assertEquals(17, hit.line)
    }

    @Test
    fun `finds several frames on one line`() {
        val line = "a.php:1 then /b/c.php(2) and d.php:3"

        assertEquals(listOf(1, 2, 3), FileLineMatcher.find(line).map { it.line })
    }

    @Test
    fun `ignores a bare file with no line number`() {
        assertTrue(FileLineMatcher.find("loaded config.php successfully").isEmpty())
    }

    @Test
    fun `ignores a url that happens to carry a port`() {
        assertTrue(FileLineMatcher.find("GET http://myapp.test:7073/index.php took 4ms").isEmpty())
    }

    @Test
    fun `matches the file types worth opening`() {
        val hits = FileLineMatcher.find("x.php:1 y.blade.php:2 z.js:3 w.ts:4 v.vue:5 u.twig:6")

        assertEquals(6, hits.size)
    }

    @Test
    fun `ignores a line number that is absurd`() {
        assertTrue(FileLineMatcher.find("weird.php:99999999999").isEmpty())
    }
}

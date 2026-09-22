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

    @Test
    fun `a bare path with no line number still links`() {
        // A view event names the template it rendered and nothing else.
        val hit = FileLineMatcher.find("path: /home/dev/app/resources/views/app.blade.php").single()

        assertEquals("/home/dev/app/resources/views/app.blade.php", hit.path)
        assertEquals(1, hit.line)
    }

    @Test
    fun `a relative bare path links too`() {
        val hit = FileLineMatcher.find("rendered resources/views/mail/invoice.blade.php").single()

        assertEquals("resources/views/mail/invoice.blade.php", hit.path)
    }

    @Test
    fun `a bare file name in prose is still left alone`() {
        // Without a directory it is a word, not a path.
        assertTrue(FileLineMatcher.find("loaded config.php successfully").isEmpty())
    }

    @Test
    fun `a url is not turned into a file link`() {
        assertTrue(FileLineMatcher.find("GET http://myapp.test:7073/index.php took 4ms").isEmpty())
        assertTrue(FileLineMatcher.find("posted to https://api.test/hooks/stripe.php").isEmpty())
    }

    @Test
    fun `a path that already carries a line is not matched twice`() {
        val hits = FileLineMatcher.find("at /home/dev/app/app/Repo.php:21")

        assertEquals(1, hits.size)
        assertEquals(21, hits.single().line)
    }
}

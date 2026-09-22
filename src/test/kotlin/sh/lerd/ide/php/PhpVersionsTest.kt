package sh.lerd.ide.php

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhpVersionsTest {
    @Test
    fun `a major-minor version is used as is`() {
        assertEquals("8.4", PhpVersions.languageLevel("8.4"))
    }

    @Test
    fun `a patch version drops to the language level`() {
        // lerd reports what is installed; the IDE only has a level per minor.
        assertEquals("8.4", PhpVersions.languageLevel("8.4.13"))
    }

    @Test
    fun `surrounding noise is ignored`() {
        assertEquals("8.5", PhpVersions.languageLevel(" 8.5-rc1 "))
    }

    @Test
    fun `nothing usable yields nothing`() {
        assertNull(PhpVersions.languageLevel(null))
        assertNull(PhpVersions.languageLevel(""))
        assertNull(PhpVersions.languageLevel("latest"))
    }
}

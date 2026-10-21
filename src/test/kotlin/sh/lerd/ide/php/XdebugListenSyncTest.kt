package sh.lerd.ide.php

import sh.lerd.ide.api.LerdPhp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XdebugListenSyncTest {
    private val fpms = listOf(
        LerdPhp(version = "8.3", xdebugEnabled = true),
        LerdPhp(version = "8.4", xdebugEnabled = false),
    )

    @Test
    fun `listening turns xdebug on for a version that has it off`() {
        assertEquals(true, XdebugListenSync.target(true, "8.4", fpms))
    }

    @Test
    fun `stopping turns xdebug off for a version that has it on`() {
        assertEquals(false, XdebugListenSync.target(false, "8.3", fpms))
    }

    @Test
    fun `a version already in step is left alone`() {
        // Each toggle restarts FPM, so a matching state must not send one.
        assertNull(XdebugListenSync.target(true, "8.3", fpms))
        assertNull(XdebugListenSync.target(false, "8.4", fpms))
    }

    @Test
    fun `a version lerd did not report is still toggled`() {
        assertEquals(true, XdebugListenSync.target(true, "8.5", fpms))
    }
}

package sh.lerd.ide.logs

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DumpTimesTest {
    private val zone: ZoneId = ZoneId.of("Europe/Bucharest")
    // 21:00 UTC is already the 22nd in Bucharest, which would make an event
    // from the same evening read as yesterday's.
    private val now: Instant = Instant.parse("2026-09-21T18:00:00Z")

    @Test
    fun `an event from today shows the time of day`() {
        assertEquals("23:24:54", DumpTimes.short("2026-09-21T20:24:54.874Z", now, zone))
    }

    @Test
    fun `an older event carries its date so it cannot be misread`() {
        assertEquals("19 Sep 12:30", DumpTimes.short("2026-09-19T09:30:00Z", now, zone))
    }

    @Test
    fun `a timestamp without a zone is read as UTC, the way the daemon writes it`() {
        assertEquals("23:24:54", DumpTimes.short("2026-09-21T20:24:54", now, zone))
    }

    @Test
    fun `an unparseable timestamp is shown as it came rather than dropped`() {
        assertEquals("whenever", DumpTimes.short("whenever", now, zone))
    }

    @Test
    fun `an empty timestamp shows nothing`() {
        assertEquals("", DumpTimes.short("", now, zone))
    }
}

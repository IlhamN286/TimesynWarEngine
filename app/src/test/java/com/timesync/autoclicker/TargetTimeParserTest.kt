package com.timesync.autoclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetTimeParserTest {
    @Test
    fun parsesMillisecondsInStrict24HourFormat() {
        assertEquals(52_825_750, TargetTimeParser.parseMillisOfDay("14:40:25:750"))
        assertEquals(0, TargetTimeParser.parseMillisOfDay("00:00:00:000"))
        assertEquals(86_399_999, TargetTimeParser.parseMillisOfDay("23:59:59:999"))
    }

    @Test
    fun rejectsOldOrIncompleteFormats() {
        assertNull(TargetTimeParser.parseMillisOfDay("20:00:00"))
        assertNull(TargetTimeParser.parseMillisOfDay("20:00:00:00"))
        assertNull(TargetTimeParser.parseMillisOfDay("2:00:00:000"))
        assertNull(TargetTimeParser.parseMillisOfDay("20:00:00:000:1"))
    }

    @Test
    fun rejectsOutOfRangeComponents() {
        assertNull(TargetTimeParser.parseMillisOfDay("24:00:00:000"))
        assertNull(TargetTimeParser.parseMillisOfDay("12:60:00:000"))
        assertNull(TargetTimeParser.parseMillisOfDay("12:00:60:000"))
        assertNull(TargetTimeParser.parseMillisOfDay("12:00:00:1000"))
    }
}

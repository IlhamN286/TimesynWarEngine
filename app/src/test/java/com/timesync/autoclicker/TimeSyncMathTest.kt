package com.timesync.autoclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSyncMathTest {

    @Test
    fun parseHttpDate_parsesGmtDate() {
        assertEquals(0L, TimeSyncMath.parseHttpDate("Thu, 01 Jan 1970 00:00:00 GMT"))
    }

    @Test
    fun parseHttpDate_returnsNullForMissingOrInvalidHeader() {
        assertNull(TimeSyncMath.parseHttpDate(null))
        assertNull(TimeSyncMath.parseHttpDate(""))
        assertNull(TimeSyncMath.parseHttpDate("not-a-date"))
    }

    @Test
    fun estimateOffset_usesHalfRttAndHalfSecondCorrection() {
        assertEquals(600L, TimeSyncMath.estimateOffset(10_000L, 200L, 10_000L))
    }

    @Test
    fun estimateOffset_neverUsesNegativeRtt() {
        assertEquals(500L, TimeSyncMath.estimateOffset(10_000L, -50L, 10_000L))
    }

    @Test
    fun median_returnsMiddleValueAfterSorting() {
        assertEquals(20L, TimeSyncMath.median(listOf(30L, 10L, 20L)))
    }

    @Test
    fun median_returnsUpperMiddleForEvenSample() {
        assertEquals(30L, TimeSyncMath.median(listOf(40L, 10L, 30L, 20L)))
    }

    @Test
    fun median_returnsNullForEmptySample() {
        assertNull(TimeSyncMath.median(emptyList()))
    }

    @Test
    fun pingLead_isClampedToSupportedRange() {
        assertEquals(25L, TimeSyncMath.pingLead(0L))
        assertEquals(65L, TimeSyncMath.pingLead(100L))
        assertEquals(160L, TimeSyncMath.pingLead(1_000L))
    }
}

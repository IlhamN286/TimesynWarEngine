package com.timesync.autoclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelayCalibrationMathTest {

    @Test
    fun percentile95_usesUpperTailSample() {
        assertEquals(19L, DelayCalibrationMath.percentile95((1L..20L).toList()))
    }

    @Test
    fun percentile95_returnsNullForEmptyList() {
        assertNull(DelayCalibrationMath.percentile95(emptyList()))
    }

    @Test
    fun recommendation_isClampedAndRoundedToTwentyMs() {
        val result = DelayCalibrationMath.recommendDelay(100L, 140L, 5L)
        assertEquals(320L, result)
        assertEquals(0L, result % 20L)
        assertEquals(1200L, DelayCalibrationMath.recommendDelay(5000L, 9000L, 500L))
    }

    @Test
    fun buildResult_requiresAtLeastFiveNetworkAndTwentyLocalSamples() {
        assertNull(
            DelayCalibrationMath.buildResult(
                networkSamples = listOf(20L, 30L, 40L, 50L),
                localSamples = List(20) { 1L },
                attempts = 8
            )
        )
        assertNull(
            DelayCalibrationMath.buildResult(
                networkSamples = List(5) { 50L },
                localSamples = List(19) { 1L },
                attempts = 8
            )
        )
    }

    @Test
    fun buildResult_returnsMetricsWhenEnoughSamplesExist() {
        val result = DelayCalibrationMath.buildResult(
            networkSamples = listOf(100L, 110L, 120L, 130L, 200L, 140L, 150L, 160L),
            localSamples = List(20) { 2L },
            attempts = 8
        )
        assertEquals(8, result?.successfulNetworkSamples)
        assertEquals(8, result?.attemptedNetworkSamples)
        assertEquals(200L, result?.networkP95Ms)
        assertEquals(320L, result?.recommendedDelayMs)
    }
}

package com.timesync.autoclicker

import android.os.SystemClock
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.roundToLong

internal data class DelayCalibrationResult(
    val networkMedianMs: Long,
    val networkP95Ms: Long,
    val networkJitterMs: Long,
    val localP95Ms: Long,
    val successfulNetworkSamples: Int,
    val attemptedNetworkSamples: Int,
    val recommendedDelayMs: Long
)

internal object DelayCalibrationMath {
    const val MIN_DELAY_MS = 180L
    const val MAX_DELAY_MS = 1200L
    private const val MIN_RECOMMENDED_DELAY_MS = 320L
    private const val DELAY_STEP_MS = 20L

    fun percentile95(samples: List<Long>): Long? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    fun recommendDelay(networkMedianMs: Long, networkP95Ms: Long, localP95Ms: Long): Long {
        val jitter = (networkP95Ms - networkMedianMs).coerceAtLeast(0L)
        val raw = maxOf(
            MIN_RECOMMENDED_DELAY_MS,
            (networkP95Ms * 0.35 + jitter * 0.50 + localP95Ms * 2.0).roundToLong()
        )
        val stepped = ((raw + DELAY_STEP_MS - 1) / DELAY_STEP_MS) * DELAY_STEP_MS
        return stepped.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun buildResult(networkSamples: List<Long>, localSamples: List<Long>, attempts: Int): DelayCalibrationResult? {
        if (networkSamples.size < 5 || localSamples.size < 20) return null
        val median = networkSamples.sorted()[networkSamples.size / 2]
        val p95 = percentile95(networkSamples) ?: return null
        val localP95 = percentile95(localSamples) ?: return null
        val jitter = (p95 - median).coerceAtLeast(0L)
        return DelayCalibrationResult(
            networkMedianMs = median,
            networkP95Ms = p95,
            networkJitterMs = jitter,
            localP95Ms = localP95,
            successfulNetworkSamples = networkSamples.size,
            attemptedNetworkSamples = attempts,
            recommendedDelayMs = recommendDelay(median, p95, localP95)
        )
    }
}

internal object DelayCalibrationRunner {
    private const val ENDPOINT = "https://www.google.com/generate_204"
    private const val NETWORK_ATTEMPTS = 8
    private const val NETWORK_TIMEOUT_MS = 1800
    private const val LOCAL_SAMPLES = 20

    fun run(onComplete: (Result<DelayCalibrationResult>) -> Unit) {
        thread(name = "delay-calibration-safe") {
            try {
                val networkSamples = mutableListOf<Long>()
                repeat(NETWORK_ATTEMPTS) {
                    var connection: HttpsURLConnection? = null
                    try {
                        val started = SystemClock.elapsedRealtime()
                        connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
                            requestMethod = "HEAD"
                            connectTimeout = NETWORK_TIMEOUT_MS
                            readTimeout = NETWORK_TIMEOUT_MS
                            useCaches = false
                            instanceFollowRedirects = false
                            setRequestProperty("Cache-Control", "no-cache, no-store")
                            setRequestProperty("Connection", "close")
                        }
                        val responseCode = connection.responseCode
                        val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
                        if (responseCode in 200..299) networkSamples.add(elapsed)
                    } finally {
                        connection?.disconnect()
                    }
                    SystemClock.sleep(120L)
                }

                // Safe local-only test: measure scheduler/sleep drift; no touch or accessibility action.
                val localSamples = mutableListOf<Long>()
                repeat(LOCAL_SAMPLES) {
                    val started = SystemClock.elapsedRealtime()
                    SystemClock.sleep(10L)
                    localSamples.add((SystemClock.elapsedRealtime() - started - 10L).coerceAtLeast(0L))
                }

                val result = DelayCalibrationMath.buildResult(
                    networkSamples = networkSamples,
                    localSamples = localSamples,
                    attempts = NETWORK_ATTEMPTS
                ) ?: throw IllegalStateException(
                    "Sampel jaringan tidak cukup (" + networkSamples.size + "/5 minimal) atau tes lokal tidak lengkap."
                )
                onComplete(Result.success(result))
            } catch (error: Exception) {
                onComplete(Result.failure(error))
            }
        }
    }
}

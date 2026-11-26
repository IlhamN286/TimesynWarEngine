package com.timesync.autoclicker

import android.os.SystemClock
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

object NtpTimeSync {

    private val endpoints = listOf(
        "https://shopee.co.id",
        "https://www.google.com/generate_204",
        "https://time.is"
    )

    fun syncNow(onComplete: ((offsetMs: Long, rttMs: Long, ok: Boolean) -> Unit)? = null) {
        thread(name = "timesyn-time-sync") {
            val offsets = mutableListOf<Long>()
            val rtts = mutableListOf<Long>()

            for (endpoint in endpoints) {
                var connection: HttpsURLConnection? = null
                try {
                    val url = URL(endpoint)
                    if (url.protocol != "https") continue

                    val startedAt = SystemClock.elapsedRealtime()
                    connection = (url.openConnection() as HttpsURLConnection).apply {
                        requestMethod = "HEAD"
                        useCaches = false
                        instanceFollowRedirects = false
                        setRequestProperty("Cache-Control", "no-cache, no-store")
                        setRequestProperty("Connection", "close")
                        connectTimeout = 2000
                        readTimeout = 2000
                    }

                    val responseCode = connection.responseCode
                    val completedAt = SystemClock.elapsedRealtime()
                    val dateHeader = if (responseCode in 200..299) {
                        connection.getHeaderField("Date")
                    } else {
                        null
                    }

                    val serverDateMs = TimeSyncMath.parseHttpDate(dateHeader)
                    if (serverDateMs != null) {
                        val rtt = (completedAt - startedAt).coerceAtLeast(1L)
                        offsets.add(
                            TimeSyncMath.estimateOffset(
                                serverDateMs,
                                rtt,
                                System.currentTimeMillis()
                            )
                        )
                        rtts.add(rtt)
                    }
                } catch (_: Exception) {
                    // Try the next trusted HTTPS time source.
                } finally {
                    connection?.disconnect()
                }
            }

            if (offsets.isNotEmpty()) {
                val medianOffset = TimeSyncMath.median(offsets) ?: 0L
                val medianRtt = TimeSyncMath.median(rtts) ?: 0L
                AutoClickAccessibilityService.ntpOffsetMs = medianOffset
                AutoClickAccessibilityService.dynamicPingLeadMs = TimeSyncMath.pingLead(medianRtt)
                onComplete?.invoke(medianOffset, medianRtt, true)
            } else {
                onComplete?.invoke(AutoClickAccessibilityService.ntpOffsetMs, 0L, false)
            }
        }
    }
}

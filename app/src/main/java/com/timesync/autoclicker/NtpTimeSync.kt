package com.timesync.autoclicker

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object NtpTimeSync {

    private val endpoints = listOf(
        "https://shopee.co.id",
        "https://www.google.com/generate_204",
        "https://time.is"
    )

    fun syncNow(onComplete: ((offsetMs: Long, rttMs: Long, ok: Boolean) -> Unit)? = null) {
        thread {
            val offsets = mutableListOf<Long>()
            val rtts = mutableListOf<Long>()

            for (ep in endpoints) {
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    val conn = (URL(ep).openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache, no-store")
                        setRequestProperty("Connection", "close")
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    conn.connect()
                    val t1 = SystemClock.elapsedRealtime()
                    val dateHeader = conn.getHeaderField("Date")
                    conn.disconnect()

                    val serverDateMs = TimeSyncMath.parseHttpDate(dateHeader)
                    if (serverDateMs != null) {
                        val rtt = (t1 - t0).coerceAtLeast(1L)
                        offsets.add(TimeSyncMath.estimateOffset(
                            serverDateMs,
                            rtt,
                            System.currentTimeMillis()
                        ))
                        rtts.add(rtt)
                    }
                } catch (_: Exception) { }
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

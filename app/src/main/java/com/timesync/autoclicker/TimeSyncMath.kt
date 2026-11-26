package com.timesync.autoclicker

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure time-sync calculations, separated from Android/network APIs so they can
 * be verified with local JVM unit tests.
 */
internal object TimeSyncMath {
    private const val HTTP_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z"

    fun parseHttpDate(dateHeader: String?): Long? {
        if (dateHeader.isNullOrBlank()) return null
        return try {
            val formatter = SimpleDateFormat(HTTP_DATE_PATTERN, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
            formatter.parse(dateHeader)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun estimateOffset(serverDateMs: Long, roundTripMs: Long, localNowMs: Long): Long {
        val safeRtt = roundTripMs.coerceAtLeast(1L)
        return serverDateMs + (safeRtt / 2L) + 500L - localNowMs
    }

    /** Matches the existing upper-middle selection for an even-sized sample. */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        return values.sorted()[values.size / 2]
    }

    fun pingLead(roundTripMs: Long): Long =
        ((roundTripMs / 2L) + 15L).coerceIn(25L, 160L)
}

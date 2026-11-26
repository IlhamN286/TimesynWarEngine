package com.timesync.autoclicker

/**
 * Parses strict 24-hour target time in HH:mm:ss:SSS format.
 * Returns milliseconds since local midnight, or null for invalid input.
 */
internal object TargetTimeParser {
    private val pattern = Regex("^(\\d{2}):(\\d{2}):(\\d{2}):(\\d{3})$")

    fun parseMillisOfDay(value: String): Int? {
        val match = pattern.matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val second = match.groupValues[3].toIntOrNull() ?: return null
        val millisecond = match.groupValues[4].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59 || millisecond !in 0..999) {
            return null
        }
        return (((hour * 60 + minute) * 60 + second) * 1000) + millisecond
    }
}

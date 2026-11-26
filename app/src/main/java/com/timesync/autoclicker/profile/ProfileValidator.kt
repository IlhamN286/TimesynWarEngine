package com.timesync.autoclicker.profile

import com.timesync.autoclicker.TargetTimeParser

object ProfileValidator {
    const val MAX_ACTIONS = 10
    const val MIN_ACTION_DELAY_MS = 100L
    const val MAX_ACTION_DELAY_MS = 5000L

    fun validate(profile: ApplicationProfile): String? {
        if (profile.name.isBlank()) return "Nama profile wajib diisi."
        if (!PACKAGE_PATTERN.matches(profile.packageName)) return "Package name tidak valid."
        if (TargetTimeParser.parseMillisOfDay(profile.timing.targetTime) == null) {
            return "Target time harus menggunakan HH:mm:ss:SSS."
        }
        if (profile.timing.executionOffsetMs !in -500..500) return "Execution Offset harus -500 sampai +500 ms."
        if (profile.timing.clickIntervalMs !in 180L..1000L) return "Click Interval harus 180–1000 ms."
        if (profile.timing.refreshDelayMs !in 180L..1200L) return "Delay Target harus 180–1200 ms."
        if (profile.layout.actions.size > MAX_ACTIONS) return "Maksimal $MAX_ACTIONS titik aksi."
        if (profile.layout.actions.any { it.x !in 0f..1f || it.y !in 0f..1f }) {
            return "Koordinat harus berada dalam rentang 0–1."
        }
        if (profile.layout.actions.any { it.delayAfterMs !in MIN_ACTION_DELAY_MS..MAX_ACTION_DELAY_MS }) {
            return "Jeda setiap action harus 100–5000 ms."
        }
        if (profile.layout.actions.map { it.order }.toSet().size != profile.layout.actions.size) {
            return "Urutan action tidak boleh duplikat."
        }
        return null
    }

    private val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
}

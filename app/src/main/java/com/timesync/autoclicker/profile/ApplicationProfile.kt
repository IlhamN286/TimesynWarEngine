package com.timesync.autoclicker.profile

import java.util.UUID

data class ApplicationProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageName: String,
    val timing: TimingConfig = TimingConfig(),
    val layout: LayoutConfig = LayoutConfig()
)

data class TimingConfig(
    val targetTime: String = "20:00:00:000",
    val executionOffsetMs: Int = 0,
    val clickIntervalMs: Long = 250L,
    val refreshDelayMs: Long = 320L
)

data class LayoutConfig(
    val referenceWidth: Int = 1080,
    val referenceHeight: Int = 2400,
    val actions: List<ActionPoint> = emptyList()
)

data class ActionPoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Action",
    val x: Float,
    val y: Float,
    val order: Int,
    val delayAfterMs: Long = 0L,
    val enabled: Boolean = true
)

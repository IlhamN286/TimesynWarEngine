package com.timesync.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoClickAccessibilityService? = null
        @Volatile var ntpOffsetMs: Long = 0L
        @Volatile var dynamicPingLeadMs: Long = 45L
        private const val CHANNEL_ID = "timesyn_secure_service"
        private const val NOTIF_ID = 3003
        private const val PREFS_NAME = "timesyn_war_prefs"

        const val MODE_SINGLE_TARGET = 0
        const val MODE_POPUP_BYPASS = 1
        const val MODE_SHIPPING_REFRESH = 2
    }

    private lateinit var windowManager: WindowManager
    private var target1View: CrosshairTargetView? = null
    private var target2View: CrosshairTargetView? = null
    private var controlBarView: LinearLayout? = null
    private var floatingClockView: TextView? = null

    private lateinit var target1Params: WindowManager.LayoutParams
    private lateinit var target2Params: WindowManager.LayoutParams
    private lateinit var barParams: WindowManager.LayoutParams
    private lateinit var clockParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val random = Random()

    private val isScheduled = AtomicBoolean(false)
    private val isExecuting = AtomicBoolean(false)
    private val pingCalibratedThisRun = AtomicBoolean(false)

    private var anchorTargetElapsedMs: Long = 0L
    private var executionMode = MODE_SHIPPING_REFRESH
    private var refreshDelayMs = 320L
    private var autoStopOnPageChange = true
    private var initialWindowClass: String? = null
    private var statusTextRef: TextView? = null
    private var btnStartRef: TextView? = null

    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        promoteToForeground()
    }

    private fun promoteToForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Timesyn War Engine Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Menjaga presisi waktu eksekusi flash sale di latar belakang"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Timesyn War Engine Aktif")
                .setContentText("Siap tempur flash sale Shopee")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isExecuting.get() || !autoStopOnPageChange || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            try {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName) return
                val clsName = event.className?.toString() ?: return

                if (clsName.contains("Dialog", ignoreCase = true) ||
                    clsName.contains("Popup", ignoreCase = true) ||
                    clsName.contains("BottomSheet", ignoreCase = true)
                ) {
                    return
                }

                val isPaymentScreen = clsName.contains("Payment", ignoreCase = true) ||
                    clsName.contains("Pay", ignoreCase = true) ||
                    clsName.contains("Pin", ignoreCase = true) ||
                    clsName.contains("Cashier", ignoreCase = true) ||
                    clsName.contains("ShopeePay", ignoreCase = true) ||
                    clsName.contains("CheckoutPay", ignoreCase = true)

                val changedToPaymentWebView = initialWindowClass != null &&
                    initialWindowClass != clsName &&
                    clsName.contains("WebView", ignoreCase = true)

                if (isPaymentScreen || changedToPaymentWebView) {
                    stopExecution("TEMBUS!")
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopExecution(label: String) {
        isScheduled.set(false)
        isExecuting.set(false)
        handler.removeCallbacksAndMessages(null)
        restoreTargetOverlays()
        statusTextRef?.text = label
        statusTextRef?.setTextColor(Color.parseColor("#10B981"))
        btnStartRef?.let {
            it.text = "MULAI"
            (it.background as GradientDrawable).setColor(Color.parseColor("#10B981"))
        }
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
        instance = null
        super.onDestroy()
    }

    /** True while a scheduled countdown or automated action is active. */
    fun isCalibrationBusy(): Boolean = isScheduled.get() || isExecuting.get()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(
        scheduledEpochMs: Long,
        mode: Int,
        customRefreshDelayMs: Long,
        enableAutoStop: Boolean
    ) {
        removeOverlay()
        promoteToForeground()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val atomicNow = System.currentTimeMillis() + ntpOffsetMs
        val deltaFromNowMs = (scheduledEpochMs - atomicNow).coerceAtLeast(1000L)
        anchorTargetElapsedMs = SystemClock.elapsedRealtime() + deltaFromNowMs

        executionMode = mode
        refreshDelayMs = customRefreshDelayMs.coerceIn(180L, 1200L)
        autoStopOnPageChange = enableAutoStop
        pingCalibratedThisRun.set(false)

        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels

        target1View = CrosshairTargetView(this, "1", "#FACC15").also { view ->
            val defaultX = (sw * 0.43).toInt()
            val defaultY = sh - dp(86)
            val savedX = prefs.getInt("t1_x_$executionMode", defaultX)
            val savedY = prefs.getInt("t1_y_$executionMode", defaultY)
            target1Params = createCrosshairParams(savedX, savedY)
            attachDragListener(view, target1Params, "t1", executionMode)
            try { windowManager.addView(view, target1Params) } catch (_: Exception) { }
        }

        if (executionMode != MODE_SINGLE_TARGET) {
            target2View = CrosshairTargetView(this, "2", "#4ADE80").also { view ->
                val defaultX = sw - dp(82)
                val defaultY = sh - dp(86)
                val savedX = prefs.getInt("t2_x_$executionMode", defaultX)
                val savedY = prefs.getInt("t2_y_$executionMode", defaultY)
                target2Params = createCrosshairParams(savedX, savedY)
                attachDragListener(view, target2Params, "t2", executionMode)
                try { windowManager.addView(view, target2Params) } catch (_: Exception) { }
            }
        }

        floatingClockView = TextView(this).apply {
            text = "19:59:59.0"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#F5FFFFFF"))
                setStroke(dp(1), Color.parseColor("#94A3B8"))
            }
        }
        clockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(105)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager.addView(floatingClockView, clockParams)
            startFloatingClockTicker()
        } catch (_: Exception) { }

        val statusText = TextView(this).apply {
            text = "SIAP"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, dp(8), 0)
        }
        statusTextRef = statusText

        val delayPresets = listOf(220L, 320L, 420L, 550L)
        var presetIdx = delayPresets.indexOf(refreshDelayMs).let { if (it >= 0) it else 1 }
        val btnDelay = createPillButton("${refreshDelayMs}ms", "#334155").apply {
            setOnClickListener {
                if (isScheduled.get() || isExecuting.get()) return@setOnClickListener
                presetIdx = (presetIdx + 1) % delayPresets.size
                refreshDelayMs = delayPresets[presetIdx]
                text = "${refreshDelayMs}ms"
                prefs.edit().putLong("refresh_delay_ms", refreshDelayMs).apply()
            }
        }

        val btnStart = createPillButton("MULAI", "#10B981")
        btnStartRef = btnStart
        val btnTest = createPillButton("TES", "#3B82F6")
        val btnClose = createPillButton("✕", "#475569")

        btnStart.setOnClickListener {
            if (isScheduled.compareAndSet(false, true)) {
                isExecuting.set(false)
                pingCalibratedThisRun.set(false)
                handler.removeCallbacksAndMessages(null)
                try {
                    initialWindowClass = rootInActiveWindow?.className?.toString()
                } catch (_: Exception) {
                    initialWindowClass = null
                }

                hideTargetOverlaysForClick()
                statusText.setTextColor(Color.parseColor("#38BDF8"))
                btnStart.text = "STOP"
                (btnStart.background as GradientDrawable).setColor(Color.parseColor("#EF4444"))
                startMonotonicCountdown(statusText, btnStart)
            } else {
                stopExecution("BATAL")
            }
        }

        btnTest.setOnClickListener {
            if (isScheduled.get() || isExecuting.get()) return@setOnClickListener
            hideTargetOverlaysForClick()
            statusText.text = "TES T1..."
            statusText.setTextColor(Color.parseColor("#38BDF8"))

            handler.postDelayed({
                performHybridTap(
                    params = target1Params,
                    fallbackKeywords = listOf("Konfirmasi", "Lanjutkan", "Simpan")
                ) { ok1, method1 ->
                    if (target2View != null) {
                        statusText.text = "TES T2..."
                        handler.postDelayed({
                            performHybridTap(
                                params = target2Params,
                                fallbackKeywords = listOf("Buat Pesanan", "Checkout", "Bayar Sekarang")
                            ) { ok2, method2 ->
                                val success = ok1 || ok2
                                statusText.text = if (success) "OK ($method1/$method2)" else "BLOKIR OS"
                                statusText.setTextColor(Color.parseColor(if (success) "#10B981" else "#EF4444"))
                                handler.postDelayed({
                                    restoreTargetOverlays()
                                    statusText.setTextColor(Color.parseColor("#E2E8F0"))
                                }, 900L)
                            }
                        }, refreshDelayMs)
                    } else {
                        statusText.text = if (ok1) "OK ($method1)" else "BLOKIR OS"
                        statusText.setTextColor(Color.parseColor(if (ok1) "#10B981" else "#EF4444"))
                        handler.postDelayed({
                            restoreTargetOverlays()
                            statusText.setTextColor(Color.parseColor("#E2E8F0"))
                        }, 900L)
                    }
                }
            }, 90L)
        }

        btnClose.setOnClickListener { removeOverlay() }

        controlBarView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#F20F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            addView(statusText)
            addView(btnStart)
            addView(spaceView(5))
            if (executionMode == MODE_SHIPPING_REFRESH) {
                addView(btnDelay)
                addView(spaceView(5))
            }
            addView(btnTest)
            addView(spaceView(5))
            addView(btnClose)
        }

        barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(controlBarView, barParams)
        } catch (_: Exception) { }
    }

    private fun startFloatingClockTicker() {
        val ticker = object : Runnable {
            override fun run() {
                val tv = floatingClockView ?: return
                if (!tv.isAttachedToWindow) return
                val nowMs = System.currentTimeMillis() + ntpOffsetMs
                val tenths = (nowMs % 1000L) / 100L
                val timeStr = clockFormat.format(Date(nowMs))
                tv.text = "$timeStr.$tenths"
                clockHandler.postDelayed(this, 50L)
            }
        }
        clockHandler.post(ticker)
    }

    private fun hideTargetOverlaysForClick() {
        val passFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        target1View?.let { v ->
            if (v.isAttachedToWindow) {
                try {
                    v.visibility = View.GONE
                    target1Params.flags = passFlags
                    windowManager.updateViewLayout(v, target1Params)
                } catch (_: Exception) { }
            }
        }
        target2View?.let { v ->
            if (v.isAttachedToWindow) {
                try {
                    v.visibility = View.GONE
                    target2Params.flags = passFlags
                    windowManager.updateViewLayout(v, target2Params)
                } catch (_: Exception) { }
            }
        }
    }

    private fun restoreTargetOverlays() {
        val normalFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        target1View?.let { v ->
            if (v.isAttachedToWindow) {
                try {
                    target1Params.flags = normalFlags
                    v.visibility = View.VISIBLE
                    v.alpha = 1.0f
                    windowManager.updateViewLayout(v, target1Params)
                } catch (_: Exception) { }
            }
        }
        target2View?.let { v ->
            if (v.isAttachedToWindow) {
                try {
                    target2Params.flags = normalFlags
                    v.visibility = View.VISIBLE
                    v.alpha = 1.0f
                    windowManager.updateViewLayout(v, target2Params)
                } catch (_: Exception) { }
            }
        }
    }

    private fun createCrosshairParams(startX: Int, startY: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(54),
            dp(54),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun createPillButton(label: String, hexBg: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor(hexBg))
            }
        }
    }

    private fun spaceView(dpWidth: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(dpWidth), 1) }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        view: View,
        params: WindowManager.LayoutParams,
        keyPrefix: String,
        mode: Int
    ) {
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            if (isScheduled.get() || isExecuting.get()) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    try {
                        params.x = initX + (event.rawX - touchX).toInt()
                        params.y = initY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putInt("${keyPrefix}_x_$mode", params.x)
                        .putInt("${keyPrefix}_y_$mode", params.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun startMonotonicCountdown(statusText: TextView, btnStart: TextView) {
        val ticker = object : Runnable {
            override fun run() {
                if (!isScheduled.get()) return
                val nowElapsed = SystemClock.elapsedRealtime()
                val rawRemaining = anchorTargetElapsedMs - nowElapsed

                if (rawRemaining in 4500L..5600L && pingCalibratedThisRun.compareAndSet(false, true)) {
                    calibrateShopeeTtfbAndWarmup()
                }

                val effectiveRemaining = rawRemaining - dynamicPingLeadMs

                if (effectiveRemaining <= 0L) {
                    if (isScheduled.compareAndSet(true, false)) {
                        isExecuting.set(true)
                        statusText.text = "WAR!"
                        executeFlashSaleSequence(statusText)
                    }
                } else {
                    val sec = rawRemaining / 1000
                    val ms = rawRemaining % 1000
                    statusText.text = String.format(Locale.US, "-%ds.%03d", sec, ms)
                    val nextDelay = when {
                        effectiveRemaining < 120L -> 1L
                        effectiveRemaining < 1000L -> 5L
                        else -> 20L
                    }
                    handler.postDelayed(this, nextDelay)
                }
            }
        }
        handler.post(ticker)
    }

    private fun calibrateShopeeTtfbAndWarmup() {
        thread {
            try {
                val samples = mutableListOf<Long>()
                repeat(3) {
                    val t0 = SystemClock.elapsedRealtime()
                    val conn = (URL("https://shopee.co.id/api/v4/pages/get_homepage_category_list").openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache, no-store")
                        setRequestProperty("Connection", "close")
                        connectTimeout = 1200
                        readTimeout = 1200
                    }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..499) {
                        samples.add(SystemClock.elapsedRealtime() - t0)
                    }
                }
                if (samples.isNotEmpty()) {
                    samples.sort()
                    val medianRtt = samples[samples.size / 2]
                    dynamicPingLeadMs = ((medianRtt / 2) + 12L).coerceIn(20L, 150L)
                }
            } catch (_: Exception) { }
        }
    }

    private fun executeFlashSaleSequence(statusText: TextView) {
        hideTargetOverlaysForClick()

        performHybridTap(
            params = target1Params,
            fallbackKeywords = listOf("Konfirmasi", "Lanjutkan", "Simpan")
        ) { ok, _ ->
            if (!ok) statusText.text = "CEK IZIN"
        }

        if (target2View != null) {
            val maxOrderLoops = 20
            var loopCount = 0

            val orderBurstRunnable = object : Runnable {
                override fun run() {
                    if (!isExecuting.get() || loopCount >= maxOrderLoops) {
                        stopExecution("SELESAI")
                        return
                    }
                    loopCount++
                    statusText.text = "ORDER #$loopCount"
                    performHybridTap(
                        params = target2Params,
                        fallbackKeywords = listOf("Buat Pesanan", "Checkout", "Bayar Sekarang")
                    )
                    handler.postDelayed(this, 215L + random.nextInt(15))
                }
            }

            handler.postDelayed(orderBurstRunnable, refreshDelayMs)
        } else {
            var singleLoop = 0
            val singleBurstRunnable = object : Runnable {
                override fun run() {
                    if (!isExecuting.get() || singleLoop >= 12) {
                        stopExecution("SELESAI")
                        return
                    }
                    singleLoop++
                    statusText.text = "TAP #$singleLoop"
                    performHybridTap(
                        params = target1Params,
                        fallbackKeywords = listOf("Buat Pesanan", "Konfirmasi", "Beli Sekarang")
                    )
                    handler.postDelayed(this, 215L + random.nextInt(15))
                }
            }
            handler.postDelayed(singleBurstRunnable, 215L)
        }
    }

    private fun performHybridTap(
        params: WindowManager.LayoutParams,
        fallbackKeywords: List<String>,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val sw = resources.displayMetrics.widthPixels.toFloat()
        val sh = resources.displayMetrics.heightPixels.toFloat()
        val safeMargin = dp(10).toFloat()

        val halfSize = dp(54) / 2f
        val statusBarOffset = getStatusBarHeight().toFloat()

        val rawX = params.x.toFloat() + halfSize
        val rawY = params.y.toFloat() + halfSize

        val cx = rawX.coerceIn(safeMargin, (sw - safeMargin).coerceAtLeast(safeMargin + 1f))
        val cy = rawY.coerceIn(safeMargin, (sh - safeMargin).coerceAtLeast(safeMargin + 1f))

        // JALUR 1: Direct Accessibility Node Click (Dengan Prioritas Popup-First)
        var nodeClicked = false
        var popupDismissed = false
        try {
            val root = rootInActiveWindow
            if (root != null) {
                // PRIORITAS 0: Tutup popup error Shopee ("Coba Lagi", "OK", "Mengerti") lebih dulu
                popupDismissed = clickNodeByExactText(root, listOf("Coba Lagi", "OK", "Mengerti"))
                if (popupDismissed) {
                    nodeClicked = true
                } else {
                    nodeClicked = clickNodeAtCoordinate(root, cx.toInt(), cy.toInt()) ||
                        clickNodeAtCoordinate(root, cx.toInt(), (cy + statusBarOffset).toInt()) ||
                        clickNodeByKeywords(root, fallbackKeywords)
                }
            }
        } catch (_: Exception) { }

        // JALUR 2: Hardware Touch Gesture (dispatchGesture)
        try {
            val jitterX = (random.nextGaussian() * 1.1).toFloat().coerceIn(-2.0f, 2.0f)
            val jitterY = (random.nextGaussian() * 1.1).toFloat().coerceIn(-2.0f, 2.0f)
            val duration = (42L + (random.nextGaussian() * 5).toLong()).coerceIn(34L, 54L)

            val gx = (cx + jitterX).coerceIn(safeMargin, (sw - safeMargin).coerceAtLeast(safeMargin + 1f))
            val gy = (cy + jitterY).coerceIn(safeMargin, (sh - safeMargin).coerceAtLeast(safeMargin + 1f))

            val path = Path().apply { moveTo(gx, gy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    val tag = when {
                        popupDismissed -> "POPUP_OK"
                        nodeClicked -> "HYBRID"
                        else -> "GESTURE"
                    }
                    onResult?.invoke(true, tag)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onResult?.invoke(nodeClicked, if (nodeClicked) "NODE" else "CANCEL")
                }
            }

            val dispatched = dispatchGesture(gesture, callback, handler)
            if (!dispatched) {
                onResult?.invoke(nodeClicked, if (nodeClicked) "NODE" else "NO_DISP")
            }
        } catch (_: Exception) {
            onResult?.invoke(nodeClicked, if (nodeClicked) "NODE" else "ERR")
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun clickNodeAtCoordinate(node: AccessibilityNodeInfo?, x: Int, y: Int): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return false

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (clickNodeAtCoordinate(child, x, y)) {
                return true
            }
        }

        return performClickUpwards(node)
    }

    private fun clickNodeByExactText(root: AccessibilityNodeInfo, exactLabels: List<String>): Boolean {
        for (label in exactLabels) {
            val matches = root.findAccessibilityNodeInfosByText(label)
            if (!matches.isNullOrEmpty()) {
                for (m in matches) {
                    val txt = m.text?.toString()?.trim() ?: ""
                    if (txt.equals(label, ignoreCase = true) && performClickUpwards(m)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun clickNodeByKeywords(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (kw in keywords) {
            val matches = root.findAccessibilityNodeInfosByText(kw)
            if (!matches.isNullOrEmpty()) {
                for (m in matches) {
                    if (performClickUpwards(m)) return true
                }
            }
        }
        return false
    }

    private fun performClickUpwards(node: AccessibilityNodeInfo?): Boolean {
        var curr = node
        var depth = 0
        while (curr != null && depth < 6) {
            if (curr.isClickable && curr.isEnabled) {
                if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            curr = curr.parent
            depth++
        }
        return false
    }

    fun removeOverlay() {
        isScheduled.set(false)
        isExecuting.set(false)
        handler.removeCallbacksAndMessages(null)
        clockHandler.removeCallbacksAndMessages(null)
        try {
            target1View?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        } catch (_: Exception) { }
        try {
            target2View?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        } catch (_: Exception) { }
        try {
            controlBarView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        } catch (_: Exception) { }
        try {
            floatingClockView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        } catch (_: Exception) { }
        target1View = null
        target2View = null
        controlBarView = null
        floatingClockView = null
    }

    private class CrosshairTargetView(
        context: Context,
        private val label: String,
        hexColor: String
    ) : View(context) {

        private val baseColor = Color.parseColor(hexColor)

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }

        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 14f * resources.displayMetrics.density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - 4f

            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)

            canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint)
            canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint)

            val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(label, cx, textY, textPaint)
        }
    }
}

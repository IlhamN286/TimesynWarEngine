package com.timesync.autoclicker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var tvAtomicClock: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessStatus: TextView
    private lateinit var etTargetTime: EditText
    private lateinit var etRefreshDelay: EditText
    private lateinit var switchAutoStop: Switch

    private val clockFmt = SimpleDateFormat("HH:mm:ss.S", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("timesyn_war_prefs", Context.MODE_PRIVATE)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(32))
        }

        // Header
        val tvTitle = TextView(this).apply {
            text = "TIMESYN WAR ENGINE"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tvSub = TextView(this).apply {
            text = "v4.2 • Hybrid Tap + Safe Delay Calibration"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(tvTitle)
        container.addView(tvSub)

        // Card 1: Jam Atomik WIB
        val clockCard = createCard().apply {
            tvAtomicClock = TextView(this@MainActivity).apply {
                text = "00:00:00.0"
                setTextColor(Color.parseColor("#4ADE80"))
                textSize = 30f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            tvSyncStatus = TextView(this@MainActivity).apply {
                text = "Menyinkronkan waktu server..."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(10))
            }
            val btnResync = createActionButton("SINKRON ULANG WAKTU SERVER", "#1E293B", "#38BDF8").apply {
                setOnClickListener {
                    tvSyncStatus.text = "Mengkalibrasi NTP & RTT Shopee..."
                    NtpTimeSync.syncNow { offset, rtt, ok ->
                        runOnUiThread {
                            tvSyncStatus.text = if (ok) {
                                "Tersinkron WIB • Offset: ${offset}ms • Ping RTT: ${rtt}ms"
                            } else {
                                "Menggunakan jam perangkat lokal (Cek koneksi internet)"
                            }
                        }
                    }
                }
            }
            addView(tvAtomicClock)
            addView(tvSyncStatus)
            addView(btnResync)
        }
        container.addView(clockCard)
        container.addView(spacer(14))

        // Card 2: Status Izin (Overlay, Aksesibilitas, & Restricted Settings HyperOS)
        val permCard = createCard().apply {
            val lbl = TextView(this@MainActivity).apply {
                text = "1. IZIN SISTEM (WAJIB AKTIF)"
                setTextColor(Color.parseColor("#FACC15"))
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(8))
            }
            tvOverlayStatus = TextView(this@MainActivity).apply {
                textSize = 12.5f
                setPadding(0, dp(4), 0, dp(6))
            }
            val btnOverlay = createActionButton("IZINKAN TAMPIL DI ATAS APLIKASI", "#2563EB", "#FFFFFF").apply {
                setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }

            tvAccessStatus = TextView(this@MainActivity).apply {
                textSize = 12.5f
                setPadding(0, dp(12), 0, dp(6))
            }
            val btnAccess = createActionButton("BUKA PENGATURAN AKSESIBILITAS", "#059669", "#FFFFFF").apply {
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }

            val btnAppInfo = createActionButton("BUKA INFO APLIKASI (IZIN SETELAN TERBATAS XIAOMI)", "#334155", "#FACC15").apply {
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }

            val tvHyperNote = TextView(this@MainActivity).apply {
                text = "Tip HyperOS/Android 15: Jika tombol Aksesibilitas abu-abu, buka Info Aplikasi di atas -> ketuk titik 3 pojok kanan atas -> Izinkan setelan terbatas."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setPadding(0, dp(8), 0, 0)
            }

            addView(lbl)
            addView(tvOverlayStatus)
            addView(btnOverlay)
            addView(tvAccessStatus)
            addView(btnAccess)
            addView(spacer(8))
            addView(btnAppInfo)
            addView(tvHyperNote)
        }
        container.addView(permCard)
        container.addView(spacer(14))

        // Card 3: Konfigurasi War Flash Sale
        val configCard = createCard().apply {
            val lbl = TextView(this@MainActivity).apply {
                text = "2. PENGATURAN WAR FLASH SALE"
                setTextColor(Color.parseColor("#4ADE80"))
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(10))
            }

            val lblTime = TextView(this@MainActivity).apply {
                text = "Jam Target Eksekusi (Format HH:mm:ss - WIB):"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
            }

            val nextHourCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val defaultTimeStr = String.format(
                Locale.US,
                "%02d:00:00",
                nextHourCal.get(Calendar.HOUR_OF_DAY)
            )

            etTargetTime = EditText(this@MainActivity).apply {
                setText(prefs.getString("target_time_str", defaultTimeStr))
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.MONOSPACE
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }

            val presetRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(12))
            }
            for (preset in listOf("00:00:00", "12:00:00", "18:00:00", "20:00:00")) {
                val b = Button(this@MainActivity).apply {
                    text = preset.substring(0, 5)
                    textSize = 11f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setBackgroundColor(Color.parseColor("#334155"))
                    setOnClickListener { etTargetTime.setText(preset) }
                }
                val lp = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                presetRow.addView(b, lp)
            }

            val lblDelay = TextView(this@MainActivity).apply {
                text = "Jeda Target 1 (Konfirmasi) -> Target 2 (Buat Pesanan) [180–1200 ms]:"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
            }
            etRefreshDelay = EditText(this@MainActivity).apply {
                setText(prefs.getLong("refresh_delay_ms", 320L).coerceIn(180L, 1200L).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.MONOSPACE
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }

            val btnCalibrateDelay = createActionButton("KALIBRASI DELAY OTOMATIS (HYBRID / SAFE)", "#0E7490", "#FFFFFF").apply {
                setOnClickListener { DelayCalibrationDialog.show(this@MainActivity, etRefreshDelay) }
            }

            switchAutoStop = Switch(this@MainActivity).apply {
                text = "Auto-Stop otomatis saat tembus halaman Pembayaran"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 12.5f
                isChecked = prefs.getBoolean("auto_stop_page", true)
                setPadding(0, dp(12), 0, dp(12))
            }

            val btnLaunch = createActionButton("LUNCURKAN TARGET & JAM MELAYANG", "#EE4D2D", "#FFFFFF").apply {
                textSize = 13.5f
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener { launchWarOverlay() }
            }

            addView(lbl)
            addView(lblTime)
            addView(spacer(4))
            addView(etTargetTime)
            addView(presetRow)
            addView(lblDelay)
            addView(spacer(4))
            addView(etRefreshDelay)
            addView(spacer(8))
            addView(btnCalibrateDelay)
            addView(switchAutoStop)
            addView(btnLaunch)
        }
        container.addView(configCard)

        rootScroll.addView(container)
        setContentView(rootScroll)

        NtpTimeSync.syncNow { offset, rtt, ok ->
            runOnUiThread {
                tvSyncStatus.text = if (ok) {
                    "Tersinkron WIB • Offset: ${offset}ms • Ping RTT: ${rtt}ms"
                } else {
                    "Mode Jam Lokal WIB"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        startClockLoop()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun startClockLoop() {
        val r = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis() + AutoClickAccessibilityService.ntpOffsetMs
                tvAtomicClock.text = clockFmt.format(Date(now)) + " WIB"
                uiHandler.postDelayed(this, 80L)
            }
        }
        uiHandler.post(r)
    }

    private fun refreshPermissionStatus() {
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        tvOverlayStatus.text = if (overlayOk) {
            "• Izin Overlay (Tampil di Atas): AKTIF"
        } else {
            "• Izin Overlay (Tampil di Atas): BELUM AKTIF"
        }
        tvOverlayStatus.setTextColor(Color.parseColor(if (overlayOk) "#4ADE80" else "#F87171"))

        val accessOk = isAccessibilityEnabled()
        tvAccessStatus.text = if (accessOk) {
            "• Layanan Aksesibilitas: AKTIF"
        } else {
            "• Layanan Aksesibilitas: BELUM AKTIF"
        }
        tvAccessStatus.setTextColor(Color.parseColor(if (accessOk) "#4ADE80" else "#F87171"))
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (AutoClickAccessibilityService.instance != null) return true
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun launchWarOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin Tampil di Atas Aplikasi dulu!", Toast.LENGTH_SHORT).show()
            return
        }
        val service = AutoClickAccessibilityService.instance
        if (!isAccessibilityEnabled() || service == null) {
            Toast.makeText(this, "Aktifkan Layanan Aksesibilitas Timesyn War Engine dulu!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val timeText = etTargetTime.text.toString().trim()
        val parts = timeText.split(":")
        if (parts.size != 3) {
            Toast.makeText(this, "Format jam harus HH:mm:ss (contoh: 20:00:00)", Toast.LENGTH_SHORT).show()
            return
        }

        val hh = parts[0].toIntOrNull() ?: 20
        val mm = parts[1].toIntOrNull() ?: 0
        val ss = parts[2].toIntOrNull() ?: 0
        val delayMs = etRefreshDelay.text.toString().trim().toLongOrNull()?.coerceIn(180L, 1200L) ?: 320L
        val autoStop = switchAutoStop.isChecked

        getSharedPreferences("timesyn_war_prefs", Context.MODE_PRIVATE).edit()
            .putString("target_time_str", timeText)
            .putLong("refresh_delay_ms", delayMs)
            .putBoolean("auto_stop_page", autoStop)
            .apply()

        val tz = TimeZone.getTimeZone("Asia/Jakarta")
        val atomicNow = System.currentTimeMillis() + AutoClickAccessibilityService.ntpOffsetMs
        val targetCal = Calendar.getInstance(tz).apply {
            timeInMillis = atomicNow
            set(Calendar.HOUR_OF_DAY, hh.coerceIn(0, 23))
            set(Calendar.MINUTE, mm.coerceIn(0, 59))
            set(Calendar.SECOND, ss.coerceIn(0, 59))
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= atomicNow) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        service.showOverlay(
            scheduledEpochMs = targetCal.timeInMillis,
            mode = AutoClickAccessibilityService.MODE_SHIPPING_REFRESH,
            customRefreshDelayMs = delayMs,
            enableAutoStop = autoStop
        )

        Toast.makeText(this, "Overlay aktif! Buka Shopee sekarang.", Toast.LENGTH_SHORT).show()

        val shopeeIntent = packageManager.getLaunchIntentForPackage("com.shopee.lite.id")
            ?: packageManager.getLaunchIntentForPackage("com.shopee.id")
        if (shopeeIntent != null) {
            startActivity(shopeeIntent)
        }
    }

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#1E293B"))
            setStroke(dp(1), Color.parseColor("#334155"))
        }
    }

    private fun createActionButton(label: String, bgHex: String, textHex: String): Button = Button(this).apply {
        text = label
        setTextColor(Color.parseColor(textHex))
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(bgHex))
        }
    }

    private fun spacer(dpH: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(dpH))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

package com.timesync.autoclicker.profile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import java.util.Calendar
import java.util.TimeZone
import com.timesync.autoclicker.AutoClickAccessibilityService
import com.timesync.autoclicker.TargetTimeParser
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ApplicationProfilesActivity : Activity() {
    private lateinit var repository: ApplicationProfileRepository
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ApplicationProfileRepository(this)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) renderProfiles()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        root.addView(TextView(this).apply {
            text = "APPLICATION PROFILES"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Setiap aplikasi menyimpan timing dan layout secara terpisah."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(14))
        })
        root.addView(actionButton("＋ TAMBAH APLIKASI") { showEditor(null) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actionButton("KEMBALI") { finish() })
        setContentView(root)
        renderProfiles()
    }

    private fun renderProfiles() {
        list.removeAllViews()
        val profiles = repository.getAll()
        if (profiles.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Belum ada profile. Tambahkan aplikasi untuk mulai mengatur timing dan layout."
                setTextColor(Color.LTGRAY)
                textSize = 13f
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
        }
        profiles.forEach { profile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.rgb(30, 41, 59))
            }
            card.addView(TextView(this).apply {
                text = profile.name
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(TextView(this).apply {
                text = profile.packageName + "\n" + profile.layout.actions.size + " action • Offset " + profile.timing.executionOffsetMs + " ms • Interval " + profile.timing.clickIntervalMs + " ms"
                setTextColor(Color.LTGRAY)
                textSize = 11f
                setPadding(0, dp(4), 0, dp(8))
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallButton("EDIT") { showEditor(profile) }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(smallButton("PAKAI") { confirmLaunchProfile(profile) }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(smallButton("LAYOUT") {
                startActivity(Intent(this@ApplicationProfilesActivity, ProfileLayoutEditorActivity::class.java)
                    .putExtra(EXTRA_PROFILE_ID, profile.id))
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(smallButton("DUPLIKAT") {
                val copy = repository.duplicate(profile.id, profile.name + " Copy")
                if (copy == null) toast("Gagal menduplikasi profile") else renderProfiles()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(smallButton("HAPUS") {
                AlertDialog.Builder(this@ApplicationProfilesActivity)
                    .setTitle("Hapus profile?")
                    .setMessage("Profile " + profile.name + " akan dihapus dari perangkat.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus") { _, _ ->
                        repository.delete(profile.id)
                        renderProfiles()
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            card.addView(row)
            val params = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
            list.addView(card, params)
        }
    }

    private fun confirmLaunchProfile(profile: ApplicationProfile) {
        if (profile.layout.actions.none { it.enabled }) {
            toast("Tambahkan minimal satu action pada Layout terlebih dahulu.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Jalankan profile?")
            .setMessage("Profile ini akan menjalankan satu urutan tap pada aplikasi " + profile.name +
                ". Uji dahulu pada layar yang aman. Jangan gunakan pada tombol pembayaran atau konfirmasi transaksi.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ -> launchProfile(profile) }
            .show()
    }

    private fun launchProfile(profile: ApplicationProfile) {
        if (ProfileValidator.validate(profile) != null) {
            toast(ProfileValidator.validate(profile) ?: "Profile tidak valid")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Aktifkan izin Tampil di Atas Aplikasi terlebih dahulu.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            toast("Aktifkan Accessibility Service TimesynWarEngine terlebih dahulu.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(profile.packageName)
        if (launchIntent == null) {
            toast("Aplikasi tidak ditemukan atau tidak memiliki launcher activity.")
            return
        }
        val timeOfDay = TargetTimeParser.parseMillisOfDay(profile.timing.targetTime) ?: run {
            toast("Target time tidak valid")
            return
        }
        val target = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            val now = System.currentTimeMillis() + AutoClickAccessibilityService.ntpOffsetMs
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, timeOfDay / 3_600_000)
            set(Calendar.MINUTE, (timeOfDay % 3_600_000) / 60_000)
            set(Calendar.SECOND, (timeOfDay % 60_000) / 1_000)
            set(Calendar.MILLISECOND, timeOfDay % 1_000)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        service.showProfileOverlay(profile, target.timeInMillis)
        try {
            startActivity(launchIntent)
            toast("Profile aktif. Pastikan aplikasi target terbuka sebelum menekan MULAI.")
        } catch (_: Exception) {
            service.removeOverlay()
            toast("Gagal membuka aplikasi target.")
        }
    }

    private fun showEditor(existing: ApplicationProfile?) {
        val name = field(existing?.name.orEmpty(), "Nama aplikasi")
        val pkg = field(existing?.packageName.orEmpty(), "Package name, contoh com.example.app")
        val target = field(existing?.timing?.targetTime ?: "20:00:00:000", "Target HH:mm:ss:SSS")
        val offset = field((existing?.timing?.executionOffsetMs ?: 0).toString(), "Execution Offset -500..500 ms", true)
        val interval = field((existing?.timing?.clickIntervalMs ?: 250L).toString(), "Click Interval 180..1000 ms", true)
        val delay = field((existing?.timing?.refreshDelayMs ?: 320L).toString(), "Delay Target 180..1200 ms", true)
        val pickApp = Button(this).apply {
            text = "PILIH DARI APLIKASI TERPASANG"
            setOnClickListener {
                val launchables = packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                ).distinctBy { it.activityInfo.packageName }
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                if (launchables.isEmpty()) {
                    toast("Tidak ada aplikasi launcher yang dapat dipilih.")
                } else {
                    val labels = launchables.map {
                        it.loadLabel(packageManager).toString() + "  •  " + it.activityInfo.packageName
                    }.toTypedArray()
                    AlertDialog.Builder(this@ApplicationProfilesActivity)
                        .setTitle("Pilih aplikasi")
                        .setItems(labels) { _, which ->
                            val selected = launchables[which]
                            name.setText(selected.loadLabel(packageManager).toString())
                            pkg.setText(selected.activityInfo.packageName)
                        }.show()
                }
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name)
            addView(pkg)
            addView(pickApp)
            listOf(target, offset, interval, delay).forEach { addView(it) }
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah aplikasi" else "Edit profile")
            .setView(content)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val profile = ApplicationProfile(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.text.toString().trim(),
                    packageName = pkg.text.toString().trim(),
                    timing = TimingConfig(
                        targetTime = target.text.toString().trim(),
                        executionOffsetMs = offset.text.toString().toIntOrNull() ?: Int.MIN_VALUE,
                        clickIntervalMs = interval.text.toString().toLongOrNull() ?: Long.MIN_VALUE,
                        refreshDelayMs = delay.text.toString().toLongOrNull() ?: Long.MIN_VALUE
                    ),
                    layout = existing?.layout ?: LayoutConfig()
                )
                val error = ProfileValidator.validate(profile)
                if (error != null) {
                    toast(error)
                } else if (!repository.save(profile)) {
                    toast("Gagal menyimpan profile. Periksa kembali data yang dimasukkan.")
                } else {
                    renderProfiles()
                    toast("Profile tersimpan")
                }
            }.show()
    }

    private fun field(value: String, hintText: String, numeric: Boolean = false) =
        EditText(this).apply {
            setText(value)
            hint = hintText
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 14f
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 9f
        minWidth = 0
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { action() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}

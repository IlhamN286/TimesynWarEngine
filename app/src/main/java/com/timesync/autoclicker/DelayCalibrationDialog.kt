package com.timesync.autoclicker

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

internal object DelayCalibrationDialog {
    fun show(activity: Activity, delayInput: EditText) {
        if (AutoClickAccessibilityService.instance?.isCalibrationBusy() == true) {
            Toast.makeText(activity, "Kalibrasi tidak dapat berjalan saat Auto Click aktif.", Toast.LENGTH_LONG).show()
            return
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val status = TextView(activity).apply {
            text = "Pengujian aman: hanya mengukur koneksi HTTPS dan respons scheduler lokal. Tidak ada tap ke Shopee."
            setTextColor(Color.DKGRAY)
            textSize = 14f
        }
        val progress = ProgressBar(activity).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val metrics = TextView(activity).apply {
            text = "Network: belum diuji\nRespons lokal: belum diuji\nRekomendasi: —"
            setTextColor(Color.DKGRAY)
            textSize = 13f
            setPadding(0, 12, 0, 8)
        }
        val startButton = Button(activity).apply { text = "MULAI KALIBRASI" }
        content.addView(status)
        content.addView(progress)
        content.addView(metrics)
        content.addView(startButton)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Kalibrasi Delay Otomatis")
            .setView(content)
            .setNegativeButton("Close", null)
            .setPositiveButton("Terapkan", null)
            .create()

        dialog.setCancelable(false)
        dialog.setOnShowListener {
            val closeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val applyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            closeButton.isEnabled = false
            applyButton.isEnabled = false
            closeButton.setOnClickListener { dialog.dismiss() }

            startButton.setOnClickListener {
                if (AutoClickAccessibilityService.instance?.isCalibrationBusy() == true) {
                    status.text = "Auto Click sedang aktif. Hentikan sesi terlebih dahulu."
                    return@setOnClickListener
                }
                startButton.isEnabled = false
                progress.visibility = View.VISIBLE
                status.text = "Mengukur jaringan HTTPS dan respons lokal..."
                metrics.text = "Network: mengambil 8 sampel (minimal 5 berhasil)\nRespons lokal: 20 sampel\nRekomendasi: menghitung..."

                DelayCalibrationRunner.run { calibration ->
                    activity.runOnUiThread {
                        if (activity.isFinishing || !dialog.isShowing) return@runOnUiThread
                        progress.visibility = View.GONE
                        calibration.onSuccess { result ->
                            status.text = "Kalibrasi selesai. Ini estimasi konservatif, bukan jaminan respons server target."
                            metrics.text = "Network berhasil: " + result.successfulNetworkSamples + "/" + result.attemptedNetworkSamples +
                                "\nRTT median: " + result.networkMedianMs + " ms" +
                                "\nRTT p95: " + result.networkP95Ms + " ms" +
                                "\nJitter: " + result.networkJitterMs + " ms" +
                                "\nRespons lokal p95: " + result.localP95Ms + " ms" +
                                "\n\nREKOMENDASI: " + result.recommendedDelayMs + " ms"
                            applyButton.isEnabled = true
                            applyButton.setOnClickListener {
                                delayInput.setText(result.recommendedDelayMs.toString())
                                activity.getSharedPreferences("timesyn_war_prefs", Context.MODE_PRIVATE).edit()
                                    .putLong("refresh_delay_ms", result.recommendedDelayMs)
                                    .apply()
                                Toast.makeText(activity, "Delay " + result.recommendedDelayMs + " ms diterapkan.", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }.onFailure { error ->
                            status.text = "Kalibrasi gagal atau sampel tidak mencukupi. Pengaturan delay tidak diubah."
                            metrics.text = error.message ?: "Periksa koneksi internet, lalu coba kembali."
                        }
                        closeButton.isEnabled = true
                        dialog.setCancelable(true)
                    }
                }
            }
        }
        dialog.show()
    }
}

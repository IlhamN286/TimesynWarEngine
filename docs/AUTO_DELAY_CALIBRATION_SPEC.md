# Automatic Delay Calibration — Hybrid / Safe Mode

## Tujuan
Menambahkan kalibrasi jeda otomatis untuk merekomendasikan nilai `refresh_delay_ms` tanpa melakukan tap pada aplikasi target, checkout, pembayaran, atau transaksi.

## Batasan keselamatan
- Safe Calibration tidak memanggil `dispatchGesture`, `performAction(ACTION_CLICK)`, atau fungsi tap milik Accessibility Service.
- Tidak membuka atau mengirim aksi ke Shopee.
- Pengujian hanya melakukan request HTTPS ringan ke endpoint health/time yang sudah digunakan aplikasi dan pengukuran kerja lokal di perangkat.
- Kalibrasi tidak berjalan ketika Auto Click sedang dijadwalkan/berjalan.
- Hasil adalah rekomendasi konservatif, bukan jaminan bahwa server atau aplikasi target akan menerima setiap tap pada interval tersebut.

## Metode Hybrid
1. **Network sampling**: ambil beberapa sampel HTTPS HEAD (timeout singkat), catat RTT dan status berhasil/gagal. Jangan memakai HTTP cleartext.
2. **Local response sampling**: jalankan sejumlah siklus kerja lokal non-interaktif dengan `SystemClock.elapsedRealtime()` untuk mengukur variasi waktu respons/penjadwalan perangkat. Tidak ada input ke aplikasi lain.
3. **Analisis**: hitung median RTT, p95 RTT, jitter (p95 - median), local p95, dan rasio request berhasil.
4. **Rekomendasi**: hitung baseline konservatif dari nilai-nilai tersebut, bulatkan ke kelipatan 20 ms, lalu batasi ke rentang yang didukung aplikasi (180–1200 ms). Jangan memilih nilai lebih cepat hanya karena satu sampel cepat.
5. **Konfirmasi**: tampilkan metrik, rekomendasi, dan tombol Terapkan. Pengguna harus menekan Terapkan untuk mengubah nilai jeda yang tersimpan.

## Rancangan UI
Panel **Kalibrasi Jeda Otomatis (Hybrid / Safe)** memuat:
- Tombol Mulai Kalibrasi.
- Status tahap: Network Test → Local Response Test → Analisis.
- Ringkasan median/p95 RTT, jitter, local p95, jumlah sampel berhasil.
- Nilai rekomendasi (ms), tombol Terapkan, dan tombol **Close**.

### Perilaku Close
- Tombol Close disembunyikan atau nonaktif selama pengujian berlangsung agar proses tidak tertinggal di background.
- Setelah selesai atau gagal, tombol Close aktif dan menutup panel/hasil kalibrasi saja.
- Menutup panel tidak menghentikan aplikasi, tidak menutup Accessibility Service, dan tidak mengubah konfigurasi jeda.
- Jika pengguna menutup panel saat proses belum mulai, panel langsung ditutup.
- Jika proses dibatalkan melalui mekanisme pembatalan tersendiri, seluruh worker/callback harus dibersihkan dan UI menampilkan status Dibatalkan.

## Tahapan implementasi
1. Tambahkan model hasil kalibrasi dan fungsi analisis murni yang dapat diuji dengan unit test.
2. Tambahkan runner network/local sampling pada worker thread; gunakan timeout, batas sampel, dan mekanisme pembatalan.
3. Tambahkan panel UI di `MainActivity` (layout dibangun secara programatik).
4. Tambahkan integrasi Terapkan ke preference `refresh_delay_ms`; jangan mengubah nilai otomatis sebelum pengguna menyetujui.
5. Tambahkan tombol Close sesuai perilaku di atas.
6. Tambahkan unit test untuk median, p95, jitter, batas rekomendasi, kegagalan koneksi, dan kondisi sampel tidak cukup.
7. Jalankan GitHub Actions, instal APK debug, lalu uji pada perangkat fisik Android 15.

## Aturan awal rekomendasi
- Minimal 5 sampel network berhasil dari maksimal 8 percobaan; jika tidak tercapai, tampilkan kalibrasi gagal/tidak cukup data dan jangan mengubah konfigurasi.
- Local sampling minimal 20 siklus.
- Gunakan p95, bukan rata-rata saja, untuk mengurangi sensitivitas terhadap lonjakan.
- Nilai rekomendasi tidak boleh di bawah 180 ms atau di atas 1200 ms pada versi aplikasi saat ini.
- Nilai ambang final harus ditinjau setelah hasil uji perangkat nyata; angka ini adalah parameter awal, bukan standar universal.

## Kriteria penerimaan
- Tidak ada aksi sentuh/klik ke aplikasi target selama safe calibration.
- Kalibrasi tidak berjalan bersamaan dengan sesi Auto Click.
- Koneksi gagal/timeout ditangani tanpa crash dan tanpa mengubah nilai tersimpan.
- Hasil dan metrik dapat ditampilkan secara jelas.
- Terapkan hanya mengubah jeda setelah konfirmasi pengguna.
- Tombol Close aktif setelah proses selesai/gagal dan menutup panel tanpa menghentikan service.
- Semua unit test dan build debug lulus.

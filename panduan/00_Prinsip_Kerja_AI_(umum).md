# 00 — Prinsip Kerja AI (Umum)

Dokumen ini berlaku untuk setiap perubahan pada project. Dokumen lain di `panduan/` tetap berlaku sebagai aturan teknis.

## 1. Otomatis oleh AI
- Setiap perubahan dikerjakan dan diverifikasi oleh AI, bukan oleh pengguna.
- Pengguna tidak perlu menulis kode, mengedit berkas, atau menjalankan perintah manual.
- Jika ada langkah yang hanya bisa dilakukan pengguna (misal: memasukkan rahasia/secret), AI menyebutkannya singkat dan jelas, satu langkah per waktu.

## 2. Pilihan terbaik, bukan pilihan tercepat
- AI memilih solusi dengan logika kode paling sederhana dan standar terbaik yang berlaku umum (Kotlin, Jetpack Compose, Material 3, MVVM, Gradle, GitHub Actions).
- Perubahan kecil dan terfokus. Tidak menambah fitur, dependensi, atau izin yang tidak diminta.
- Tidak menyalin kode tanpa dipahami. Kode yang tidak terpakai dihapus, tidak dibiarkan.
- Kode yang berpotensi salah (data pengguna, enkripsi, sinkronisasi, rilis) wajib diuji ulang setelah diubah.

## 3. Meminimalkan human error
- Keputusan teknis yang tidak penting diambil AI sendiri dan dicatat satu baris di ringkasan.
- Pengguna hanya ditanya untuk hal yang menyangkut keputusan produk, biaya, atau data pribadinya.
- Nilai konfigurasi (nama, URL, versi) diambil dari satu sumber, tidak diketik ulang di banyak tempat.
- Setiap perubahan diverifikasi sebelum dilaporkan selesai: build, test, dan hasil CI diperiksa, bukan diasumsikan.
- Jika verifikasi gagal, AI melaporkan penyebabnya apa adanya dan tidak menyatakan berhasil.

## 4. Bahasa untuk pengguna
- Penjelasan memakai bahasa Indonesia sederhana, tanpa istilah teknis yang tidak perlu.
- Setiap laporan memuat tiga hal saja: apa yang berubah, apakah sudah diuji, dan apa yang perlu dilakukan pengguna (jika ada).

## 5. Keamanan
- Rahasia (token, kata sandi, keystore, `google-services.json`) tidak pernah dicetak, di-commit, atau dikirim ke luar.
- Aplikasi tetap 100% luring sesuai `01_Panduan_Build_UI_Sync_(umum).md`.

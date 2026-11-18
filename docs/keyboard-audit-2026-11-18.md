# Audit KetikKu terbaru — 18 November 2026

Basis: https://github.com/nozailkhanzz184/KetikKu/commit/5b7b8f3

## Kesimpulan

Source terbaru sudah menghapus fitur serif italic, menambahkan kamus bawaan 200 kata dan tracker multitouch grid. Audit lama tidak boleh dipakai sebagai diagnosis langsung. Lima penyesuaian diterapkan pada branch kerja; penyebab lag belum dipastikan oleh profil perangkat.

## Perubahan

| Area | Implementasi |
|---|---|
| Saran | Maksimal 3 kandidat pada DictionaryEngine; 3 slot sama lebar pada SuggestionChipsRow; controller default 3 kata |
| Toolbar | Clipboard dipindah sebelum tombol floating, Setelan tetap paling kanan |
| Simbol | 10 / 9 / 7 simbol dengan inset baris kedua dan ABC/Backspace 1,45 pada baris ketiga, mengikuti QWERTY |
| Setelan | Kolom input multiline asli menggantikan menu/route Uji Ketik; TextFieldValue menyimpan selection; warna selection eksplisit |
| Kosakata | 600 unik: 420 ID + 180 EN; tambahan suggestion-only, bukan entri pribadi |
| Jalur prefix | Indeks prefix dengan daftar hasil maksimal 4, bukan scan seluruh bucket huruf awal |
| Test | Ekspektasi jumlah diperbarui; regression test default tiga, mutasi list dan prefix kamus 10k ditambah |

## Bukti kode dan risiko yang tersisa

- `app/src/main/java/com/example/ime/KetikKuKeyboardView.kt`: KeyPointersTracker sudah memisahkan PointerId dan pressed-count. Ketukan utama commit saat UP; jika urutan UP berbeda dari DOWN, urutan input perlu diuji. Bukan bug yang sudah direproduksi.
- `app/src/main/java/com/example/data/DictionaryEngine.kt`: versi basis mengelompokkan prefix hanya menurut huruf pertama. Lookup map cepat tetapi scan bucket linear. Patch menggantinya dengan prefix-key lookup dan maksimal empat entri per key. Pembuatan indeks tetap sinkron saat cache berubah; map prefix menambah memori. Profil pada kamus besar tetap wajib.
- Perbandingan snapshot cache masih structural equality O(N), sengaja dipertahankan agar mutasi list ukuran sama tidak menghasilkan data usang. Refactor aman selanjutnya membutuhkan revision/snapshot immutable pada repository, bukan identity check saja.
- `KeyboardController.kt` sudah membatasi buffer kata aktif; jangan membaca seluruh editor pada setiap karakter. Audit selection callback dan reseed InputConnection masih perlu test perangkat.
- `SettingsScreen.kt`: selection menggunakan TextFieldValue dan LocalTextSelectionColors. Visibilitas aktual, scroll saat IME muncul, warna terang/gelap, serta rotasi belum diuji secara visual.
- `MainActivity.kt`: halaman lama tidak lagi bisa dinavigasi; intent playground lama diarahkan ke Setelan. File PlaygroundScreen dipertahankan sebagai source tak terhubung, bukan halaman aktif.

## Verifikasi yang benar-benar dilakukan

1. Source engine dan kosakata dikompilasi dengan Kotlin 2.2.10 pada JVM, menggunakan stub minimal PersonalDictionaryEntry di luar repo agar tidak menarik dependency Compose/Android.
2. Smoke assertions lulus: 420 ID + 180 EN = 600 unik; tiga saran default; prefix Indonesia/Inggris; prioritas shortcut; mutasi list dengan penambahan dan replacement ukuran sama; maksimal tiga dan unik pada 10k entri; prefix seluruh kata bawaan.
3. `git diff --check` lulus sebelum pengemasan.

Full unit/Robolectric suite dan Android compilation **belum dijalankan**: Android SDK tidak tersedia; repository juga tidak menyertakan gradlew/wrapper JAR dan sandbox tidak memiliki Gradle. Tidak membangun APK atau mengklaim responsivitas telah diperbaiki di perangkat.

## Langkah berikut

Jalankan test Android sesuai workflow repository di lingkungan pengembang; lalu uji dua-jari overlap, backspace repeat, long-press, selection replacement, paste panjang, tema gelap/terang, floating mode dan landscape. Bandingkan frame/latency untuk kamus 0/1k/10k dan dokumen 100/10k/100k karakter. Lihat `prompt.md` untuk instruksi AI Studio lengkap. Tidak mengubah signingConfigs, applicationId, konfigurasi Firebase atau membuat release/tag.
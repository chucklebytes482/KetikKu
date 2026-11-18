# KetikKu — instruksi perbaikan lanjutan untuk Google AI Studio

## Konteks dan perubahan yang sudah diterapkan

Basis source: commit `5b7b8f3` (18 November 2026), bukan snapshot audit sebelumnya. Pertahankan Kotlin/Compose, signingConfigs, applicationId, Firebase, dan perilaku clipboard/shortcut yang tidak terkait.

1. **Tiga kolom saran**: engine menghasilkan maksimal tiga kandidat; UI memakai tiga slot berlebar sama, slot kosong tetap menyisakan ruang. Default: Saya / Tidak / Yang. Jangan mengisi dengan kandidat palsu jika hanya ada satu atau dua kecocokan.
2. **Toolbar ditukar**: urutan menjadi Clipboard → Mode Keyboard Mengambang → Setelan. Fungsi, active state, accessibility label dan test tag tetap mengikuti alatnya.
3. **Panel simbol mengikuti geometri QWERTY**: baris 10 simbol; baris 9 simbol dengan inset yang sama; baris ABC berbobot 1,45 + 7 simbol + Backspace berbobot 1,45; navigasi bawah tetap sama. Simbol ° ~ ^ } { dihapus dari panel. Jangan berubah ke grid 7/9 kolom pada layar sempit. Uji juga floating mode dan landscape.
4. **Uji ketik menyatu ke Setelan**: tombol/menu dan route halaman Uji Ketik terpisah dihapus. Kolom OutlinedTextField ada di Setelan dengan TextFieldValue agar selection/composition tidak hilang saat mengetik; highlight dan handle selection menggunakan warna tema yang terlihat. Intent lama playground diarahkan ke Setelan. File PlaygroundScreen lama belum dihapus agar patch tidak menghapus sumber yang mungkin masih dipakai oleh pengembang.
5. **Kosakata bawaan 3×**: 200 → 600 kata unik (420 Indonesia + 180 Inggris, rasio 70/30). Tambahan ada di ExpandedSuggestionWords.kt. Kata bawaan hanya untuk saran, tidak masuk database pribadi atau sinkronisasi. Pertahankan shortcut pribadi sebagai prioritas pertama, tanpa autocorrect fuzzy otomatis. Kurasi ejaan/kealamian kata tambahan tetap boleh dilakukan dengan jumlah dan rasio yang sama.

## Perbaikan jalur saran yang sudah dilakukan

DictionaryEngine sekarang menyimpan indeks prefix penuh dengan maksimal empat entri per prefix (tiga hasil + cadangan untuk exact match), sehingga tidak perlu memindai bucket huruf pertama setiap karakter. Urutan prioritas tetap shortcut → exact → prefix pribadi → prefix bawaan → kata yang sedang diketik. Prefix dibatasi 30 UTF-16 seperti kontrak controller lama.

Jangan menyatakan lag sudah fixed: cache masih menggunakan kesamaan isi sourceSnapshot/personalEntries, yang berpotensi O(N) pada setiap panggilan. Jangan menggantinya hanya dengan reference equality: mutasi list dengan ukuran sama akan meninggalkan cache usang. Desain berikutnya sebaiknya menggunakan revision ID dari repository dan snapshot immutable; bangun indeks saat revisi berubah di worker, kemudian publish snapshot atomik. Jangan menjalankan InputConnection pada worker sembarangan.

## Analisis terbaru yang wajib diverifikasi

- Kode terbaru telah memakai KeyPointersTracker berbasis PointerId; dugaan lama bahwa seluruh tombol huruf memakai awaitEachGesture per tombol sudah tidak berlaku. Audit handleDown/Up, cancel, popup, two-thumb rollover, ketukan tombol sama dan backspace repeat berdasarkan source sekarang.
- Commit saat UP masih dapat menghasilkan urutan UP yang berbeda dari DOWN pada dua jari. Reproduksi dahulu dan tetapkan semantik produk sebelum mengubah ordering; jangan pindah semua commit ke DOWN karena dapat merusak cancel/long-press.
- Ukur equality cache, waktu pembangunan indeks dan alokasi prefix pada kamus 10k. Indeks dibatasi jumlah hasil tetapi banyaknya key prefix dan memori tetap bertambah mengikuti jumlah/panjang kata.
- Audit invalidasi cache kata lokal, callback selection tertunda/tergabung, null/gagal InputConnection, paste besar, selection replacement, password dan surrogate pair.
- Profile recomposition/layout setelah saran diperluas ke tiga; pertahankan pressed-state read di draw phase dan rememberUpdatedState.

## Pengujian dan batas verifikasi

Smoke test JVM memakai Kotlin 2.2.10 berhasil untuk engine saran aktual dan kosakata aktual, dengan stub model minimal di luar project: 600 kata unik, tiga default, prefix ID/EN, shortcut pribadi, list yang dimutasi, dan 10k entri. Ini **bukan** full Android build atau kelulusan seluruh Robolectric suite. git diff --check berhasil.

Jalankan `gradle :app:testDebugUnitTest --no-daemon` di lingkungan Android SDK/Gradle yang sesuai repository. Sandbox pemeriksaan tidak memiliki Android SDK atau Gradle wrapper executable/JAR; belum ada APK baru yang dibangun. Jangan mengubah signing atau membuat release/tag untuk mengakali tes.

Tambahkan UI/device tests: toolbar ordering; tiga slot saran dan ellipsis pada floating; simbol 10/9/7 dan backspace; kolom Setelan yang bisa diketik memakai IME asli serta selection highlight/handles terlihat pada tema terang/gelap. Uji rotasi dan scroll ketika IME tampil.

Benchmark release di perangkat nyata: 10–15 karakter/detik selama 60 detik, dokumen 100/10k/100k karakter, kamus 0/1k/10k, saran aktif/nonaktif. Karakter tidak hilang/ganda/terbalik. Catat latency DOWN→UP, UP→commit, p50/p95, frame jank; jangan merekam teks atau password. Patch lanjutan wajib menyertakan diff kecil, risiko regresi dan hasil tes yang benar-benar dijalankan.
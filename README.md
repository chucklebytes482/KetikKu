# KetikKu

Keyboard Android ringan berbasis **Kotlin + Jetpack Compose + Material 3 (MVVM)** yang terinspirasi dari fitur inti Google Keyboard (Gboard) **tanpa panel emoji, GIF, maupun stiker**, dilengkapi **metode ganda cadangan pengaturan & kamus pribadi** (Sinkronisasi Google Drive 2 Arah + Ekspor/Impor JSON Lokal tanpa login Google).

## Fitur Utama Keyboard (IME)
- **Tata Letak & Pengetikan:** QWERTY (Bahasa Indonesia & English US), Simbol 1/2, Numpad, Shift / Caps Lock / Kapitalisasi Otomatis, Baris Angka opsional.
- **Ketik Geser & Gestur:** *Glide Typing* dengan jejak kanvas, *Spacebar Trackpad* untuk menggeser kursor, *Swipe Backspace* untuk hapus per kata, spasi ganda jadi titik, popup karakter alternatif tekan-lama.
- **Kamus Pribadi Khusus:** Menu khusus **Kamus Pribadi** di Setelan dengan bilah pencarian cepat, filter bahasa (`Semua`, `ID`, `EN`), serta penyuntingan langsung kata dan pintasannya.
- **Produktivitas Teks Murni:** Koreksi otomatis & strip 3 saran kata (prediksi bigram + *Damerau-Levenshtein* + *shortcut* kamus pribadi), Riwayat Papan Klip dengan fitur sematkan (*pin*), tema otomatis mengikuti mode gelap/terang sistem, dan panel Edit Teks.

## Metode Ganda Cadangan Pengaturan & Kamus
1. **Metode 1 — Sinkronisasi Google Drive (2 Arah):**
   - Masuk dengan Google via *Credential Manager* dan otorisasi scope `drive.file`.
   - Menyimpan `ketikku_settings.json` dan `ketikku_dictionary.json` di folder Google Drive (`KetikKu` atau folder berbagi/Shortcut) dengan resolusi konflik berbasis *timestamp* + riwayat *snapshot* di Firestore.
2. **Metode 2 — Cadangan Lokal JSON (Tanpa Login Drive):**
   - Berfungsi 100% luring (*offline*) tanpa akun Google melalui tab **Cadangan**.
   - Ekspor & pulihkan berkas tunggal `ketikku_backup_*.json` (berisi seluruh setelan + kamus pribadi) langsung lewat pemilih dokumen sistem Android (*Storage Access Framework*).

## Konfigurasi Build Rilis (R8 + Resource Shrinking + arm64-v8a)
Konfigurasi `release` pada `app/build.gradle.kts`:
- `isMinifyEnabled = true` (minifikasi & optimasi R8/ProGuard)
- `isShrinkResources = true` (penyusutan resource tak terpakai)
- `ndk { abiFilters += "arm64-v8a" }` (khusus arsitektur ARM64 agar ukuran APK `< 10 MB`)

Artefak siap rilis tersedia di folder `releases/`:
- `releases/KetikKu-v1.0-arm64-v8a-release.apk` (`4.0 MB`)
- `releases/CHECKSUMS.txt` & `releases/CERT_FINGERPRINT.txt`

## Cara Push ke GitHub & Membuat GitHub Release
1. Commit dan push seluruh repositori ke GitHub:
   ```bash
   git add .
   git commit -m "Release v1.0 KetikKu (R8 + arm64-v8a < 10 MB)"
   git push origin main
   ```
2. Unggah langsung berkas APK di `releases/` ke GitHub Release menggunakan GitHub CLI:
   ```bash
   gh release create v1.0.0 \
     releases/KetikKu-v1.0-arm64-v8a-release.apk \
     releases/CHECKSUMS.txt \
     releases/CERT_FINGERPRINT.txt \
     --title "KetikKu v1.0.0" \
     --generate-notes
   ```
3. Atau dorong tag (`git tag v1.0.0 && git push origin v1.0.0`) agar workflow `.github/workflows/release.yml` otomatis menjalankan tes, membangun APK R8 `arm64-v8a`, dan mempublikasikan GitHub Release.

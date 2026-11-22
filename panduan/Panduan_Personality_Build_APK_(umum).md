# Panduan Personality & Aturan Build APK Android (< 10 MB)

**Kotlin + Jetpack Compose + Material 3 | MVVM | Rilis APK via GitHub Actions**

### Daftar Dokumen di `panduan/`
1. `Panduan_Personality_Build_APK_(umum).md` — Aturan inti, alur kerja, template prompt, dan checklist rilis.
2. `Template_Source_Code_UI_Sync_(umum).md` — Contoh kode Kotlin siap salin (tema sistem, Beranda, sinkronisasi otomatis perangkat, mode pilih, & test).
3. `apk_Release_Setup_Guide_(umum).md` — Setup keystore, GitHub Secrets, `.gitignore`, Gradle R8, ProGuard, dan `.github/workflows/release.yml`.
4. `Prompt_Generator_Kredensial_Rilis_(umum).md` — Prompt generator kredensial rilis permanen ke kontainer VeraCrypt (`.hc`).
5. `Alur_Mindmap_Build_APK_(umum).json` — Ringkasan visual alur build.

---

## Aturan Wajib vs Dilarang

| Aspek | Wajib | Dilarang |
|---|---|---|
| **Penamaan & ID** | 1 kata asli Euskara 3 suku kata (4–8 huruf Latin tanpa diakritik) → `com.aistudio.<kata>`, ikon adaptif kustom. | Nama > 2 kata, diakritik, atau akhiran acak. |
| **Tema & UI** | Otomatis mengikuti sistem (`isSystemInDarkTheme()`), edge-to-edge (`WindowInsets.safeDrawing`), target sentuh ≥ 48dp, label singkat saja. | Pemilih tema (Gelap/Terang), paragraf penjelas berlebih di aplikasi (semua panduan rinci hanya di GitHub Pages `docs/index.html`). |
| **Prioritas Beranda** | **#1 Cadangan Perangkat** di paling atas, **#2 Web Panduan (GitHub Pages)** di bawahnya, lalu daftar konten utama. | Menyembunyikan status cadangan atau panduan di menu berlapis. |
| **Sinkronisasi** | **Hanya sinkronisasi otomatis 2 arah ke perangkat saat aplikasi dibuka**, ke 1 berkas tunggal konsisten di `/storage/emulated/0/Documents/<NamaApp>/<slug>_data.json` (selalu menimpa berkas sebelumnya; memulihkan bila data lokal kosong atau berkas lebih baru). | Google Drive/cloud login, layanan latar belakang/WorkManager, pemantau real-time, jadwal periodik, atau salinan berkas ganda bertanggal. |
| **Ukuran APK** | `< 10 MB` (`isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters += "arm64-v8a"`), hapus dependensi tak terpakai. | Menyimpan `.apk`, `.aab`, `.jks`, atau `.hc` di Git. |
| **Zona Folder** | `panduan/` = panduan & template; `docs/` = **khusus GitHub Pages**; `secret/` = kontainer VeraCrypt `.hc` lokal (masuk `.gitignore`, tidak pernah ke GitHub). | Mencampur panduan ke `docs/` atau mem-push `secret/` ke GitHub. |

---

## Alur Ringkas (Fase 1–5)

1. **Identitas & Skema Data:** Tentukan nama Euskara (`com.aistudio.<kata>`), arsitektur MVVM (`StateFlow`), dan format JSON seragam dengan `id`, `updatedAtMillis`, serta `deleted`.
2. **Penyimpanan & Sinkronisasi Perangkat:** Gunakan penyimpanan lokal (DataStore/Room) dan jalankan **sinkronisasi otomatis 2 arah ke `/storage/emulated/0/Documents/<NamaApp>/` setiap kali aplikasi dibuka** (1 berkas tunggal, menimpa berkas lama, tanpa login/internet).
3. **Kurasi Ringan:** Pangkas fitur opsional, izin sistem tak perlu, dan dependensi Gradle yang tidak dipakai. Gunakan `remember`, `derivedStateOf`, dan `Dispatchers.IO`.
4. **Pengujian & Build R8:** Pastikan Unit Test & Robolectric lulus (`gradle :app:testDebugUnitTest --no-daemon`) dan build rilis R8 `arm64-v8a` terverifikasi `apksigner`.
5. **Rilis & GitHub Pages:** Publikasikan APK via `.github/workflows/release.yml` ke GitHub Releases dan tempatkan panduan lengkap pengguna di `docs/index.html` (ramah HP, tanpa teks hash mentah panjang).

---

## Template Prompt Memulai Proyek Baru

```text
Buat aplikasi Android (Kotlin + Jetpack Compose + Material 3 + MVVM):
- Nama & applicationId: 1 kata Euskara 3 suku kata tanpa diakritik ([Kata]) | com.aistudio.[kata] + ikon adaptif kustom
- Fitur Inti:
  1. [Fitur 1]
  2. [Fitur 2]
  3. [Fitur 3]
- Standar UI & Ringan (< 10 MB):
  - Tema otomatis sistem (isSystemInDarkTheme(), tanpa pemilih tema).
  - Tanpa paragraf instruksi panjang di aplikasi; panduan rinci hanya di GitHub Pages (docs/index.html).
  - Prioritas Beranda: #1 Cadangan Perangkat, #2 Web Panduan (GitHub Pages).
  - Sinkronisasi hanya sinkronisasi otomatis 2 arah ke perangkat saat aplikasi dibuka ke 1 berkas tunggal di /storage/emulated/0/Documents/[NamaApp]/ (menimpa berkas sebelumnya, tanpa cloud/login/layanan latar belakang).
  - R8 + shrink + arm64-v8a pada release; hapus dependensi tak terpakai.
- Pengujian & Rilis:
  - Sertakan Unit Test & Robolectric UI Test.
  - Simpan panduan di panduan/, jaga docs/ khusus GitHub Pages, dan blokir secret/ & *.hc di .gitignore.
```

---

## Checklist Audit Rilis

- [ ] APK rilis `< 10 MB` (R8 + shrink + `arm64-v8a`) dan lolos `apksigner verify`.
- [ ] Unit Test & Robolectric 100% lulus.
- [ ] Tema otomatis sistem, prioritas Beranda `#1 Cadangan Perangkat` & `#2 Web Panduan`, tanpa keterangan berlebih di UI.
- [ ] Sinkronisasi hanya otomatis ke perangkat saat aplikasi dibuka (1 berkas tunggal konsisten di `Documents/<NamaApp>/`).
- [ ] Folder `docs/` khusus GitHub Pages, `panduan/` berisi dokumentasi, dan tidak ada file sensitif (`.jks`, `.hc`, `secret/`) atau `.apk` di Git.

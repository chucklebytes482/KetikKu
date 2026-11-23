# Dokumentasi & Instruksi Kredensial Rilis & VeraCrypt HTML (Umum)

Panduan operasional bagi pengembang untuk menghasilkan kredensial rilis permanen Android, mengemas berkas rahasia ke kontainer **VeraCrypt v5** (`<slug_app>_secrets.hc`), lalu membungkusnya menjadi **1 berkas HTML mandiri di root** (`<slug_app>_secrets_download.html`) agar dapat diunduh lewat browser dan langsung dihapus dari repositori setelah selesai.

---

## 1. Cara Penggunaan Dokumen Ini

Cukup kirimkan file panduan ini atau instruksi singkat ke AI, lalu ketik:
> **"jalankan"** atau **"jalankan yang sesuai saja"**

AI akan secara otomatis memahami spesifikasi di bawah dan menjalankan seluruh proses pembuatan kredensial, kontainer VeraCrypt, serta berkas HTML pengunduh di root.

---

## 2. Spesifikasi Otomatis yang Dijalankan oleh AI

1. **Keystore & Kredensial Rilis (`release/`)**:
   - `<slug_app>-release.jks`: Keystore permanen RSA 2048-bit, berlaku 30 tahun (`-validity 10950`), alias `upload`, dengan `STORE_PASSWORD` dan `KEY_PASSWORD` acak 24 karakter alfanumerik.
   - `keystore.properties`: Berisi konfigurasi penandatanganan lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
   - `GITHUB_SECRETS.md`: Kotak kode 1-ketuk untuk `RELEASE_KEYSTORE_BASE64` (1 baris tanpa spasi/baris baru), `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`, serta pengingat `PUBLIC_DIST_TOKEN` (1 Fine-grained PAT bersama untuk semua repo `*-site`).

2. **Kontainer VeraCrypt (`<slug_app>_secrets.hc`)**:
   - Format: VeraCrypt v5, AES-256-XTS, PBKDF2-HMAC-SHA512 (500.000 iterasi), filesystem internal `ext2`, ukuran 1,00 MiB.
   - Diproses di `/tmp` dan langsung dikemas ke HTML mandiri.

3. **Berkas HTML Pengunduh di Root (`<slug_app>_secrets_download.html`)**:
   - Berisi biner `.hc` (Base64 dibungkus 76 karakter/baris) dengan tombol unduh utama (Blob), tautan cadangan (Data URL), dan verifikasi SHA-256 otomatis di browser.
   - **Tidak disembunyikan di `.gitignore`** agar terlihat di root repo privat, sehingga pengguna dapat mengunduhnya lewat browser lalu menghapusnya dengan aman.
   - RAHASIA MENTAH (`*.jks`, `*.keystore`, `*.b64`, `keystore.properties`, `.env`, dll.) tetap diblokir total di `.gitignore`.

---

## 3. Daftar Isi Standar Kontainer `<slug_app>_secrets.hc`

| Lokasi di Dalam `.hc` | Isi |
|---|---|
| `release/<slug_app>-release.jks` | Keystore rilis permanen (RSA 2048-bit, alias `upload`, berlaku 30 tahun) |
| `release/keystore.properties` | Konfigurasi signing lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `release/GITHUB_SECRETS.md` | Nilai 1-ketuk salin: `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` (+ info `PUBLIC_DIST_TOKEN`) |
| *(Opsional)* `release/.env` | Hanya bila aplikasi memakai API key eksternal (tidak diperlukan untuk aplikasi 100% luring) |

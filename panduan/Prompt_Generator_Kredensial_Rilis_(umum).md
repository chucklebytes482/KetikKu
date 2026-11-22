# Prompt Generator Kredensial Rilis & Kontainer VeraCrypt (Umum)

Panduan singkat untuk meminta AI membuat kredensial rilis permanen Android dan mengemas **berkas rahasia saja** ke dalam kontainer **VeraCrypt v5** di `secret/<slug_app>_secrets.hc` (diabaikan penuh oleh `.gitignore`, hanya lokal di AI Studio).

---

## 1. Perintah Siap Salin untuk AI

```text
Buatkan kredensial rilis permanen untuk aplikasi Android yang sedang kita buat, lalu kemas HANYA berkas rahasianya ke dalam 1 kontainer standar VeraCrypt v5 di `secret/<slug_app>_secrets.hc` (ukuran 1,00 MiB, diabaikan penuh di `.gitignore`).

Berkas rahasia yang wajib dimasukkan ke folder `release/` di dalam kontainer `.hc`:
1. `<slug_app>-release.jks`: keystore permanen RSA 2048-bit, berlaku 30 tahun (`-validity 10950`), alias `upload`, dengan `STORE_PASSWORD` dan `KEY_PASSWORD` acak 24 karakter alfanumerik.
2. `keystore.properties`: berisi `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`.
3. `GITHUB_SECRETS.md`: hanya berisi kotak kode terpisah 1-ketuk untuk `RELEASE_KEYSTORE_BASE64` (1 baris tanpa spasi/baris baru), `STORE_PASSWORD`, `KEY_PASSWORD`, dan `KEY_ALIAS` (`upload`).
4. *(Opsional)* `google-services.json` atau `.env` hanya jika aplikasi memang memakainya.

Aturan Keamanan & Verifikasi:
- Format kontainer: VeraCrypt v5, AES-256-XTS, PBKDF2-HMAC-SHA512 (500.000 iterasi / PIM default), header utama + cadangan valid, filesystem internal `ext2`.
- Jangan ubah `debug.keystore`, `debug.keystore.base64`, atau `signingConfigs` / `signingConfig` di `app/build.gradle.kts`.
- Proses berkas mentah di `/tmp` lalu hapus bersih setelah masuk kontainer `.hc`.
- Pastikan `.gitignore` memblokir `secret/`, `*.hc`, `*.jks`, `*.keystore`, `*.b64`, `keystore.properties`, `google-services.json`, dan `.env`.
- Verifikasi dekripsi kontainer (header utama & cadangan, CRC-32, `e2fsck`, dan `keytool` SHA-1/SHA-256), lalu tampilkan password kontainer di respons chat saja (jangan tulis ke berkas repositori mana pun).
```

---

## 2. Daftar Isi Standar Kontainer `secret/<slug_app>_secrets.hc`

Hanya berisi berkas rahasia (tanpa panduan umum, tanpa setup Google Cloud, dan tanpa file publik):

| Lokasi di Dalam `.hc` | Isi |
|---|---|
| `release/<slug_app>-release.jks` | Keystore rilis permanen (RSA 2048-bit, alias `upload`, berlaku 30 tahun) |
| `release/keystore.properties` | Konfigurasi signing lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `release/GITHUB_SECRETS.md` | Nilai 1-ketuk salin: `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` |
| *(Opsional)* `release/google-services.json` / `.env` | Hanya bila aplikasi memakai layanan eksternal atau API key |
| *(Opsional)* `katasandi/` | Daftar kata sandi tambahan bila diminta |

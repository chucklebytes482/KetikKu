# Panduan Konfigurasi Release Ketikku

Dokumen ini berisi seluruh nilai konfigurasi permanen untuk rilis aplikasi Ketikku. Setiap nilai penting telah diletakkan di dalam kotak kode tersendiri agar dapat disalin dengan satu ketukan tombol salin.

---

## 1. GitHub Secrets

Buka repositori GitHub Anda → **Settings** → **Secrets and variables** → **Actions** → klik **New repository secret**.

### Secret 1: RELEASE_KEYSTORE_BASE64
Nama Secret:
```text
RELEASE_KEYSTORE_BASE64
```
Nilai Secret (salin seluruh blok di bawah):
```text
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
```

---

### Secret 2: STORE_PASSWORD
Nama Secret:
```text
STORE_PASSWORD
```
Nilai Secret:
```text
EcjhX78Euyowker3Om9eF8tL
```

---

### Secret 3: KEY_PASSWORD
Nama Secret:
```text
KEY_PASSWORD
```
Nilai Secret:
```text
GDhiQEDdinW5QGgnFbbM3zXJ
```

---

### Secret 4: KEY_ALIAS (Tambahkan jika workflow membaca alias dari secret)
Nama Secret:
```text
KEY_ALIAS
```
Nilai Secret:
```text
ketikku
```

---

## 2. Pendaftaran Fingerprint Aplikasi

Nama paket Android:
```text
com.aistudio.ketikku.xqvrmz
```

### Fingerprint SHA-1 (untuk Google Cloud Console & Firebase)
Salin untuk kolom **SHA-1 certificate fingerprint**:
```text
96:C5:95:14:FD:F7:C9:3B:C3:8C:D9:58:1A:80:C1:57:4A:2A:DB:3B
```

### Fingerprint SHA-256 (untuk Firebase Console)
Salin saat klik **Add fingerprint** kedua di Firebase Console:
```text
7F:A9:7C:B2:86:E3:36:3E:6B:2A:9C:8B:A8:81:2E:75:FD:A6:A6:9B:17:3E:9B:02:4D:C0:A2:85:DE:06:09:92
```

---

## 3. Checklist Google Cloud Project (`gen-lang-client-0224561583`)

- **Firebase Authentication:** Status provider Google harus aktif (Enabled).
- **Google Drive API:** Di APIs & Services → Library, pastikan Google Drive API berstatus Enabled.
- **Scope Drive:** Tambahkan scope berikut ke OAuth consent screen:
```text
https://www.googleapis.com/auth/drive.file
```
- **Test Users:** Bila Status Publishing masih *Testing*, tambahkan email Google yang dipakai login di aplikasi ke daftar pengguna uji (Test users).

---

## 4. Jalankan Build APK Release

1. Di GitHub: buka tab **Actions** pada repository Anda.
2. Pilih workflow release → klik tombol **Run workflow**.
3. Setelah selesai, unduh file APK hasil build dari bagian artifacts.

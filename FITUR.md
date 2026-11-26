# KetikKu: daftar fitur lengkap

Dokumen ini hanya referensi internal. File ini ada di root repo dan tidak dipublikasikan ke GitHub Pages (Pages membaca `/docs`).

Keterangan status:
- **Aktif**: bisa dipakai dari UI.
- **Tersimpan, belum ada UI**: ada di data/kode, tetapi belum ditemukan kontrol di layar. Belum dianggap fitur yang bisa dipakai.

---

## 1. Aktivasi

- Tab Setelan berisi dua langkah: "1. Aktifkan" (membuka Setelan Sistem untuk mengaktifkan IME) dan "2. Pilih" (memilih KetikKu sebagai keyboard aktif).
- Kolom "Coba ketik di sini" di Playground untuk mencoba keyboard sebelum dipakai di aplikasi lain.
- Status aktivasi ditampilkan (misalnya "Aktifkan KetikKu di Setelan Sistem untuk Dipakai").

## 2. Kamus pribadi

- Ada 7 folder tetap. Kode folder dan urutannya sesuai `SettingsRepository.PRIMARY_FOLDERS`:

  | No | Kode folder | Konstanta | Keterangan |
  |---|---|---|---|
  | 1 | `aut` | `FOLDER_A2F` | A2F |
  | 2 | `pw` | `FOLDER_KATA_SANDI` | Kata Sandi |
  | 3 | `kred` | `FOLDER_KREDENSIAL` | Kredensial |
  | 4 | `num` | `FOLDER_NOMOR` | Nomor |
  | 5 | `prib` | `FOLDER_PRIBADI` | Pribadi |
  | 6 | `bc` | `FOLDER_BALASAN_CEPAT` | Balasan Cepat |
  | 7 | `tras` | `FOLDER_SAMPAH` | Sampah |

  Folder `gm` (Gmail) sudah dihapus. Entri yang masih ada di `gm` dipindah ke Sampah dengan asal `gm` (migrasi `GM_RETIRED_V5_DONE`) dan bisa dipulihkan ke root.

- Di layar Kamus, nama folder tampil apa adanya dari kode di atas (misalnya `pw`, `num`).
- Urutan: 7 folder utama selalu di atas sesuai tabel. Folder kustom (jika ada) di bawahnya (`normalizeOrderedFolders`).
- Nama folder tidak bisa diubah. Folder tidak bisa dihapus. Entri tidak bisa dipindah folder.
- Hapus entri berarti memindahkannya ke **Sampah**. Dari Sampah entri bisa dipulihkan (`restoreDictionaryEntries`) atau dihapus permanen (`deleteDictionaryItems`). Akun A2F di Sampah hanya bisa dipulihkan.
- Di layar Kamus tersedia pencarian, mode pilih (multi-pilih) untuk aksi massal, dan opsi folder (`DictionaryScreen.kt`, `DictionaryDialogs.kt`).
- Urutan entri di dalam folder: entri yang punya pintasan dulu. Lalu entri A2F dan Kredensial yang punya website, diurutkan menurut **domain utama tanpa TLD** (`SettingsRepository.mainDomainSortKey`): `google.com` dan `myaccount.google.com` masuk ke urutan "google", bukan "com". `bbc.co.uk` masuk ke "bbc". Entri tanpa website diletakkan setelahnya. Sisanya diurutkan menurut kata.
- Bilah saran `kred` menampilkan semua entri aktif. Urutannya: yang paling sering dipakai dulu, lalu yang paling baru diubah.

## 3. Pintasan

Pintasan adalah kata pemicu yang memunculkan chip di bilah saran. Teks **tidak** diganti otomatis. Isi masuk ketika chip diketuk.

| Pintasan | Pola | Contoh | Perilaku |
|---|---|---|---|
| Kata Sandi | `pw` + huruf pertama entri | `pwg` | Semua entri dengan huruf awal yang sama memakai satu pintasan |
| Kredensial | `kred` + domain utama | `kredgithub` | Entri dengan domain sama berbagi pintasan. `kred` utuh menampilkan semua kredensial aktif |
| A2F | `aut` + domain utama | `autgoogle` | Akun dengan domain sama berbagi pintasan. Menampilkan kode TOTP |
| Nomor | `num` | `num` | Menampilkan isi folder Nomor |

Catatan:
- Entri A2F tanpa website tidak punya pintasan. Pesan yang muncul: "Pintasan A2F tidak dapat dibuat dari website".
- Kredensial lama tanpa website tetap ada dan tetap muncul lewat `kred`, tetapi tidak punya pintasan.

## 4. Kredensial (kred)

- Setiap entri kredensial terhubung ke **domain utama** website (misalnya `github.com`).
- Website wajib diisi saat membuat kredensial.
- Dialog kredensial menampilkan informasi entri dan menolak penyimpanan tanpa website.
- Migrasi `KRED_DOMAIN_V4_DONE` mengatur data lama saat pertama dijalankan.

## 5. Autentikasi dua faktor (A2F)

- Bilah A2F menampilkan **satu kolom per akun**. Seluruh kolom bisa disentuh, dan yang ditempel selalu **kode 6 digit saja** (email hanya tampilan, tidak ikut ditempel).
- Urutan kolom A2F, dan urutan saran `kred`, `pw`, `num`: paling sering dipakai dulu, lalu paling baru diubah. Pemakaian dicatat per ID entri (kunci `entry:<id>` di statistik pemakaian), bukan isi entri.
- Website bisa diedit setelah akun disimpan. Email dan seed tetap terkunci, dan pintasan mengikuti domain website terbaru.
- Kode TOTP: SHA-1, 6 digit, periode 30 detik.
- Seed A2F dilindungi Android Keystore.
- Seed A2F ikut dalam cadangan VXENC terenkripsi. Saat dipulihkan, seed dienkripsi ulang dengan Android Keystore perangkat tujuan.

## 6. Saran kata

- Kamus pribadi mulai menyarankan kata dari **3 huruf**. Di bawah 3 huruf tidak ada saran kata dari kamus pribadi.
- Email (aut) dan website (aut, kred) ikut muncul sebagai saran setelah **3 huruf** pertama diketik, misalnya `goog` -> `google.com`. Isi `pw` dan kata sandi `kred` hanya muncul ketika pintasannya diketik. Entri di Sampah tidak muncul.
- Hasil A2F, kredensial, nomor, dan kata sandi ditampilkan tiga kolom per halaman. Satu geseran berpindah ke halaman berikutnya atau sebelumnya.
- Idle suggestion: saran muncul saat tidak mengetik.
- Autocorrect utama (`primaryAutoCorrect`).
- Personalisasi perilaku pengguna: menyimpan hingga 600 entri (`PERSONALITY_MAX_ENTRIES = 600`).
- Kebiasaan sesi: kata yang sudah diketik dan diselesaikan (spasi, enter, atau tanda baca) 2 kali atau lebih, panjang 3–30 huruf, disarankan lagi setelah 3 huruf pertama. Contoh: `autgoogle` diketik beberapa kali, lalu `aut` menyarankan `autgoogle`. Disimpan **hanya di memori** selama controller keyboard hidup: tidak ditulis ke berkas, tidak masuk kamus pribadi, tidak dipakai di kolom sandi, maksimum 200 kata.

## 7. Papan klip

- Riwayat menyimpan **25 item** (`HISTORY_LIMIT = 25`).
- Preview menampilkan 200 karakter pertama.
- Paste langsung untuk teks sampai 10.000 karakter. Teks lebih panjang dikirim per bagian (chunked).
- Teks salinan tidak tampil di bilah saran.
- Riwayat tersimpan di panel Papan Klip.
- Dapat diatur lewat `clipboardHistoryEnabled`.
- Deteksi otomatis:
  - Nomor: diawali +62, 62, atau 08 dan minimal 11 digit.
  - Alamat `@gmail.com` tidak lagi disimpan otomatis.

## 8. Keyboard

- Baris bawah: tombol Ω, Ctrl, Spasi, titik/koma, Enter.
- Tombol Hapus: ketuk untuk menghapus satu karakter. Tahan untuk menghapus terus-menerus. Hapus terverifikasi (mengecek teks yang benar-benar terhapus).
- Tombol `,` ditahan untuk titik/koma tambahan.
- Geser tombol Spasi untuk memindahkan kursor.
- Tahan tombol Spasi untuk berganti ke keyboard sistem.
- Mode Melayang (floating): keyboard bisa dipindah, posisi tersimpan (`floatingModeEnabled`).
- Tinggi keyboard: preset NORMAL (`heightPreset`, satu-satunya pilihan).
- Bahasa: Bahasa Indonesia (`id-ID`) dan English US (`en-US`).

## 9. Ctrl dan pintasan teks

- Ketuk Ctrl sekali untuk satu kombinasi berikutnya.
- Kombinasi yang dikirim sebagai aksi: Ctrl+A (pilih semua), Ctrl+C (salin), Ctrl+X (potong), Ctrl+V (tempel), Ctrl+Z (batal), Ctrl+Y (ulang).
- Ctrl+B, Ctrl+I, Ctrl+U, Ctrl+F dihapus: tidak dikirim ke aplikasi, huruf diketik biasa.
- Ctrl mati otomatis setelah satu huruf, hapus, spasi, atau enter.

## 10. Panel simbol

- **Panel 1**: angka; `@ # $ _ & * % / •`; `- + × ÷ =`; panah.
- **Panel 2**: `✓ Rp ← → ↑ ↓ ^ § ~ |`; `< > { } [ ] \ ° ±`; `` ` `` dan emoji.
- Pindah halaman dengan tombol `1/2`.

## 11. Gaya teks

- Fungsi konversi di `FontStyleConverter.kt`: `toMonospace`, `toSerifItalic`, `toArialItalic`.
- `toSerifItalic` dan `toArialItalic` saat ini sama dengan `toMonospace`.
- Status: **tersimpan, belum ada UI**. Fungsi `convertSelection*` di `KeyboardController.kt` belum dipanggil dari layar. Pengaturan `arialItalicEnabled` dan `serifMiringEnabled` belum punya kontrol di Setelan.

## 12. Cadangan perangkat

- Kamus pribadi, folder, dan akun A2F dicadangkan ke satu berkas `ketikku.vxenc` (VaultExplorer VXENC v1, AES-256-GCM tanpa keyfile). Papan klip dan setelan tidak ikut.
- Pilih folder `Documents` atau subfoldernya di **Setelan → Cadangan Perangkat**. Ketik kata sandi (spasi diabaikan), lalu pilih **Buka & sinkronkan**. Jika belum ada berkas, KetikKu membuatnya.
- Untuk memulihkan, letakkan `ketikku.vxenc` di folder tersebut dan gunakan kata sandi yang sama. Entri digabungkan menurut ID dan waktu perubahan; entri lokal yang tidak tercantum tetap disimpan. Berkas yang gagal diautentikasi tidak dipakai atau ditimpa.
- Perubahan kamus disinkronkan otomatis selama sesi cadangan terbuka. **Kunci cadangan** mengakhiri sesi; kata sandi tidak disimpan permanen.
- Berkas JSON, Base64, atau cadangan lama lain tidak dibaca maupun dihapus. Penyimpanan dilakukan secara luring tanpa Google Drive atau GitHub.

## 13. Pengaturan tersimpan (`KeyboardSettings`)

| Pengaturan | Default | Status |
|---|---|---|
| `heightPreset` | NORMAL | Aktif (hanya NORMAL) |
| `floatingModeEnabled` | false | Aktif |
| `hapticEnabled` | true | Dipakai di keyboard (getaran). Kontrol di Setelan belum ditemukan |
| `clipboardHistoryEnabled` | - | Aktif |
| `arialItalicEnabled` | false | Tersimpan, belum ada UI |
| `serifMiringEnabled` | false | Tersimpan, belum ada UI (ViewModel punya fungsi toggle, belum dipanggil dari layar) |
| `swapPeriodCommaEnabled` | false | Tersimpan, belum ada UI |
| `swapSymbolPanelsEnabled` | false | Tersimpan, belum ada UI |

## 14. Izin

- `VIBRATE`: untuk getar tombol.
- Izin baca/tulis untuk folder cadangan tertentu diberikan lewat pemilih folder Android (Storage Access Framework).
- Tidak ada `INTERNET`. Tidak ada `ACCESS_NETWORK_STATE`.
- Aplikasi 100% luring. Dicek oleh `scripts/check_offline.py`.

## 15. Web panduan

- `docs/index.html`, dipublikasikan lewat GitHub Pages (`/docs` pada branch `main`).
- Berkas ini di root tidak ikut dipublikasikan.

---

## Checklist uji perangkat (belum dites)

- [ ] Ketik 1–2 huruf: tidak ada saran kata dari kamus pribadi.
- [ ] Ketik 3 huruf: saran kata muncul.
- [ ] Ketik `kred`: semua kredensial aktif bisa dilihat dengan menggeser tiga kolom sekaligus.
- [ ] Ketik `kredgithub`: pintasan kredensial muncul.
- [ ] Ketik `goog`: saran `google.com` muncul dari folder aut.
- [ ] Bilah A2F: satu kolom per akun.
- [ ] Dialog kredensial: menolak simpan tanpa website.

## Keputusan default yang belum dikonfirmasi

- Pemicu `kred` hanya saat kata utuh `kred`.
- Kredensial lama tanpa website tetap ada tanpa pintasan.
- Pintasan kredensial boleh sama untuk domain yang sama.
- Aturan hapus permanen dari Sampah: entri asal `aut` (A2F) tidak pernah bisa dihapus permanen. Entri asal `pw` dan `kred` hanya bisa jika ada duplikat aktif. Entri lain boleh.

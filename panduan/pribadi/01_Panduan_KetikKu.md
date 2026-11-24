# Panduan KetikKu — luring dan A2F

## Kebijakan proyek
- KetikKu tetap luring, tanpa Firebase, Drive, login cloud, atau izin INTERNET.
- Perubahan langsung ke main tanpa menulis ulang riwayat. APK dibangun hanya di GitHub Actions.
- Tampilan mempertahankan Material 3 dan tema yang sudah ada.

## Menambahkan akun A2F
1. Buka Kamus Pribadi → folder aut (A2F) → tambah entri. Pintasan aut dibuat otomatis dari email dan website, tidak bisa diisi manual.
2. Masukkan seed Base32 yang diberikan layanan dan pintasan unik 1–30 huruf/angka tanpa spasi.
3. Implementasi mendukung TOTP SHA-1, enam digit, periode 30 detik. Layanan dengan SHA-256/SHA-512 atau konfigurasi lain belum didukung.
4. Ketik pintasan untuk menampilkan kode. Saat saran disentuh, kode dihitung ulang dan dimasukkan sebagai angka ASCII tanpa gaya font atau spasi tambahan. Kode tidak dicatat sebagai statistik kebiasaan.
5. Jam perangkat harus akurat. Simpan juga recovery code dari layanan di tempat terpisah.

## Folder kamus
- Folder utama: gm (Gmail), num (Nomor), prib (Pribadi), pw (Kata Sandi), kred (Kredensial), aut (A2F), tras (Sampah), dan bc (Balasan Cepat).
- Pintasan otomatis: gm, pw, num, aut, dan kred. Tombol Pintasan membuat ulang pintasan ini.
- Tidak ada fitur pindah folder. Hapus berarti pindah ke Sampah, dan entri di Sampah bisa dipulihkan.

## Penyimpanan dan migrasi
- Seed baru dienkripsi AES-256-GCM dengan kunci non-exportable Android Keystore, format lokal `k2:iv:ciphertext`.
- Data lokal versi lama dibaca dengan format lama kemudian dienkripsi ulang saat kamus dimuat. Migrasi tidak menghapus akun atau berkas cadangan.
- Kunci lama yang publik tetap tersedia hanya untuk pemulihan/migrasi. Mengenkripsi ulang tidak membatalkan kebocoran seed lama: jika pernah terekspos, daftar ulang A2F di layanan terkait.
- Android system backup dan transfer data otomatis dinonaktifkan. Ciphertext lokal bukan cadangan lintas perangkat.

## Cadangan otomatis terenkripsi
- Berkas: `ketikku_umum.json`, `ketikku_pw.json`, `ketikku_kred.json`, `ketikku_aut.json`, dan `ketikku_aut_terenkripsi.json`. Nama lama (`ketikku_kata_sandi.json`, `ketikku_kredensial.json`, `ketikku_a2f_terenkripsi.json`) tetap dibaca sebagai cadangan jika berkas baru belum ada.
- Semua berkas dienkripsi dengan satu passphrase: AES-256-GCM, kunci = SHA-256(passphrase), format `ketikku-enc-v2`. Setiap berkas terikat jenisnya lewat AAD `format|jenis`, jadi tidak bisa ditukar antar-jenis.
- Berkas `ketikku-enc-v1` (PBKDF2) dari versi lama tetap bisa dibaca. Semua penulisan baru memakai v2.
- Kartu cadangan perangkat punya tiga langkah: (1) atur passphrase, minimal 12 karakter; (2) pilih folder di Documents; (3) tombol **↓ Timpa ke bawah** (aplikasi → perangkat) dan **↑ Timpa ke atas** (perangkat → aplikasi).
- Sebelum passphrase diatur, berkas tetap JSON biasa.
- Passphrase disimpan di perangkat dalam bentuk terbungkus Android Keystore. Siapa pun yang bisa membuka perangkat atau data aplikasi dapat memulihkannya.
- HP baru: pasang aplikasi, buka cadangan (berkas terenkripsi terbaca hanya setelah passphrase dimasukkan), lalu aktifkan dengan passphrase yang sama. Keystore tidak ikut pindah, jadi passphrase wajib.
- Berkas tanpa enkripsi tetap dibaca untuk kompatibilitas, lalu ditulis ulang terenkripsi setelah passphrase aktif.
- Aturan per berkas (standar sinkronisasi 06 §2): berkas belum ada di perangkat → disalin; nama sama dan isi berbeda → ditimpa; isi sama → dilewati. Berkas yang hanya ada di tujuan tidak dihapus.
- Berkas tersembunyi (diawali titik) tidak disentuh. Aplikasi hanya membaca dan menulis lima nama `ketikku_*.json` yang tetap di atas.
- Batasan standar 06 §6: ukuran sama tapi isi berbeda tidak tertangkap oleh pemeriksaan ukuran. Aplikasi membandingkan isi langsung, jadi batasan ini tidak berlaku di sini.
- Cadangan otomatis saat ada perubahan tetap aktif. Ini menyimpang dari Lotura 05 §6 ("tidak ada auto-sync") atas permintaan pemilik.
- Kata sandi dan kredensial hanya aman selama passphrase dijaga; simpan passphrase terpisah dari berkas.
- Cadangan A2F otomatis: akun baru ditambahkan dari berkas ke aplikasi, tetapi aplikasi tidak menghapus akun A2F lokal karena berkas. Menghapus akun A2F terakhir tidak mengosongkan berkas, sehingga akun bisa kembali pada sinkron berikutnya.
- “Timpa ke atas” (↑) berarti perangkat → aplikasi; “Timpa ke bawah” (↓) berarti aplikasi → perangkat. Istilah lain (unggah, unduh, push) tidak dipakai di antarmuka.
- Timpa ke atas menggabungkan entri per id dan versi yang lebih baru menang. Entri yang hanya ada di aplikasi tetap ada.
- Aplikasi tidak otomatis menghapus berkas tujuan, termasuk `ketikku_a2f.json` lama. Berkas `ketikku_a2f.json` (ekspor berpassword manual) tidak ditulis otomatis.

## Cadangan A2F
- Cadangan A2F ikut sinkron otomatis dan dienkripsi dengan passphrase perangkat (AES-GCM, kunci SHA-256).
- Ekspor/impor A2F berpassword manual sudah dihapus dari panel cadangan. Seed tidak ikut pindah lewat berkas manual.
- Simpan passphrase di tempat aman. Passphrase tidak disimpan aplikasi dan tidak dapat dipulihkan.

## Pemulihan cadangan lama
- Berkas A2F lama (`ketikku_a2f.json`) tidak dipulihkan lewat panel. Aplikasi tidak membacanya secara otomatis.
- Berkas dengan ciphertext Keystore dari HP lain tidak dapat dipulihkan.
- Hapus atau ganti berkas `ketikku_a2f.json` lama **secara manual**. Migrasi aplikasi tidak membuat berkas lama aman.
- Jangan mengirim seed atau cadangan lama ke AI, log, tiket, atau repositori.

## Verifikasi
- Unit/Robolectric: vektor RFC 6238, batas periode, Base32 invalid, cadangan password, password salah, manipulasi ciphertext dan parameter derivasi.
- Instrumentasi `A2fKeystoreInstrumentedTest` memerlukan Android asli/emulator untuk memeriksa kunci non-exportable dan round-trip cadangan. Unit test JVM bukan bukti hardware-backed Keystore.
# Panduan KetikKu — luring dan A2F

## Kebijakan proyek
- KetikKu tetap luring, tanpa Firebase, Drive, login cloud, atau izin INTERNET.
- Perubahan langsung ke main tanpa menulis ulang riwayat. APK dibangun hanya di GitHub Actions.
- Tampilan mempertahankan Material 3 dan tema yang sudah ada.

## Menambahkan akun A2F
1. Buka Kamus Pribadi → folder A2F → tambah entri.
2. Masukkan seed Base32 yang diberikan layanan dan pintasan unik 1–30 huruf/angka tanpa spasi.
3. Implementasi mendukung TOTP SHA-1, enam digit, periode 30 detik. Layanan dengan SHA-256/SHA-512 atau konfigurasi lain belum didukung.
4. Ketik pintasan untuk menampilkan kode. Saat saran disentuh, kode dihitung ulang dan dimasukkan sebagai angka ASCII tanpa gaya font atau spasi tambahan. Kode tidak dicatat sebagai statistik kebiasaan.
5. Jam perangkat harus akurat. Simpan juga recovery code dari layanan di tempat terpisah.

## Penyimpanan dan migrasi
- Seed baru dienkripsi AES-256-GCM dengan kunci non-exportable Android Keystore, format lokal `k2:iv:ciphertext`.
- Data lokal versi lama dibaca dengan format lama kemudian dienkripsi ulang saat kamus dimuat. Migrasi tidak menghapus akun atau berkas cadangan.
- Kunci lama yang publik tetap tersedia hanya untuk pemulihan/migrasi. Mengenkripsi ulang tidak membatalkan kebocoran seed lama: jika pernah terekspos, daftar ulang A2F di layanan terkait.
- Android system backup dan transfer data otomatis dinonaktifkan. Ciphertext lokal bukan cadangan lintas perangkat.

## Cadangan otomatis biasa
- Hanya tiga berkas: `ketikku_umum.json`, `ketikku_kata_sandi.json`, `ketikku_kredensial.json`.
- Ketiganya JSON biasa, **bukan cadangan berpassword**; amankan foldernya karena kata sandi/kredensial dapat terbaca.
- A2F tidak ditulis atau dipulihkan otomatis dari berkas tersebut. Mengimpor kamus biasa tidak menghapus akun A2F lokal.
- “Timpa ke atas” berarti berkas → aplikasi; “Timpa ke bawah” berarti aplikasi → berkas.
- Aplikasi tidak otomatis menghapus berkas tujuan, termasuk `ketikku_a2f.json` lama.

## Cadangan A2F lintas perangkat
1. Buka panel cadangan perangkat → **Ekspor A2F berpassword**.
2. Gunakan password unik minimal 12 karakter; ulangi untuk konfirmasi.
3. Pilih lokasi berkas `ketikku_a2f.json`. Semua metadata akun dan seed ada dalam payload terenkripsi AES-256-GCM. Kunci berasal dari PBKDF2-HMAC-SHA1, 600.000 iterasi, salt acak 16 byte (kompatibel API 24).
4. Simpan berkas dan password secara terpisah. Password tidak disimpan aplikasi dan tidak dapat dipulihkan.
5. Ekspor **sebelum** uninstall, hapus data aplikasi, atau pindah HP. Pada perangkat tujuan pilih **Impor A2F**, isi password, lalu pilih berkas. Seed dienkripsi ulang memakai Keystore perangkat tujuan.
6. Password salah, berkas rusak, ID bentrok, seed berbeda untuk ID yang sama, atau pintasan duplikat membatalkan impor tanpa mengganti kamus.

## Pemulihan cadangan lama
- Gunakan tombol Impor A2F secara eksplisit dengan password kosong untuk JSON A2F lama.
- Berkas dengan ciphertext Keystore dari HP lain tidak dapat dipulihkan melalui jalur lama; gunakan ekspor berpassword dari HP asal.
- Setelah pemulihan, ekspor cadangan berpassword dan ganti/hapus berkas lama **secara manual**. Migrasi aplikasi tidak membuat berkas lama aman.
- Jangan mengirim seed atau cadangan lama ke AI, log, tiket, atau repositori.

## Verifikasi
- Unit/Robolectric: vektor RFC 6238, batas periode, Base32 invalid, cadangan password, password salah, manipulasi ciphertext dan parameter derivasi.
- Instrumentasi `A2fKeystoreInstrumentedTest` memerlukan Android asli/emulator untuk memeriksa kunci non-exportable dan round-trip cadangan. Unit test JVM bukan bukti hardware-backed Keystore.
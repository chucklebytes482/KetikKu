# AGENTS.md

Panduan umum (berlaku untuk semua repo) ada di root repo `plan`: https://github.com/chucklebytes482/plan

Baca dan ikuti berkas berikut, dalam urutan ini:
- `00-prinsip-kerja.md`
- `01-penamaan.md`
- `02-struktur-proyek.md`
- `03-build-dan-kualitas.md`
- `04-ui-tema.md`
- `05-cadangan-dan-sinkron.md`
- `06-rahasia-dan-rilis.md`
- `07-format-vxenc.md`
- `08-checklist-app-baru.md`

## Aturan khusus repo ini
- Commit kecil dilakukan lokal di `main`. Jangan push sebelum pengguna mengetik `push` (00-prinsip-kerja.md bagian 6). Saat diminta, semua commit yang menunggu di-push sekaligus.
- Build dan unit test diperiksa lewat GitHub Actions (`release.yml`) sesuai 03-build-dan-kualitas.md. Sandbox tidak punya Android SDK. Keputusan ini mengikuti aturan repo, yang diutamakan di atas panduan. Pekerjaan belum selesai sebelum CI hijau, dan kegagalan CI dilaporkan apa adanya.
- Build APK hanya lewat GitHub Actions. Jangan build di sandbox.
- Identitas git: `KetikKu Bot <bot@users.noreply.github.com>`.
- Nama `KetikKu` dan paket yang sudah ada tidak diubah. Aturan penamaan Euskara berlaku untuk aplikasi baru.
- Folder cadangan: `ketikku_backup_vxenc` (lihat `01-penamaan.md` bagian 4).

## Izin push perbaikan CI
- Pengguna mengizinkan push otomatis untuk perbaikan CI yang release APK di repo ini (lihat 03-build-dan-kualitas.md bagian 4). Pemantauan 6 menit setelah push mengikuti skill ci-release-watch.

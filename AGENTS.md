# AGENTS.md

Panduan umum (berlaku untuk semua repo) ada di root repo `plan`: https://github.com/chucklebytes482/plan

Baca dan ikuti berkas berikut, dalam urutan ini:
- `00-prinsip-kerja.md`
- `01-penamaan.md`
- `02-struktur-proyek.md` (termasuk aturan luring)
- `03-build-dan-kualitas.md`
- `04-ui-tema.md`
- `05-cadangan-dan-sinkron.md`
- `06-rahasia-dan-rilis.md`
- `07-format-vxenc.md`
- `08-checklist-app-baru.md`

## Aturan khusus repo ini
- Commit langsung ke `main`.
- Build APK hanya lewat GitHub Actions. Jangan build di sandbox.
- Identitas git: `KetikKu Bot <bot@users.noreply.github.com>`.
- Nama `KetikKu` dan paket yang sudah ada tidak diubah. Aturan penamaan Euskara berlaku untuk aplikasi baru.
- Folder cadangan: `ketikku_backup_vxenc` (lihat `01-penamaan.md` bagian 4).

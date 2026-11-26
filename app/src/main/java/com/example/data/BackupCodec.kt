package com.example.data

import android.util.Base64

/**
 * Format berkas cadangan: JSON dibungkus Base64 satu baris (tanpa enkripsi, tanpa passphrase).
 *
 * Base64 hanya pengkodean, bukan perlindungan. Isi berkas bisa dibaca siapa pun yang membuka
 * folder Documents. Berkas JSON lama (teks biasa) tetap dibaca agar tidak hilang.
 */
object BackupCodec {
    /** Amplop JSON lama dari versi berenkripsi (`ketikku-enc-v1/v2`). Tidak dibaca sebagai data. */
    private val LEGACY_FORMAT_RE = Regex("\"format\"\\s*:\\s*\"ketikku-enc-")

    /** JSON -> satu baris Base64 (tanpa pemotongan baris). */
    fun encode(json: String): String = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Isi berkas -> JSON. Berkas yang sudah berupa JSON teks biasa dikembalikan apa adanya.
     * Melempar exception jika isi bukan Base64 yang valid.
     */
    fun decode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        return String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
    }

    /** True jika JSON berupa amplop terenkripsi lama, yang tidak dimigrasi dan dilewati. */
    fun isLegacyEncryptedEnvelope(json: String): Boolean {
        val trimmed = json.trimStart()
        if (!trimmed.startsWith("{")) return false
        // Regex, bukan JSONObject: org.json di unit test JVM hanya stub android.jar.
        return LEGACY_FORMAT_RE.containsMatchIn(trimmed)
    }
}

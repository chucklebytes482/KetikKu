package com.example.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP (RFC 6238) dengan HMAC-SHA1, 6 digit, periode 30 detik.
 * Kode TIDAK di-cache: selalu dihitung ulang dari waktu sekarang.
 */
object TotpGenerator {
    const val PERIOD_SECONDS = 30L
    const val DIGITS = 6
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val MIN_SECRET_CHARS = 16

    /** Hapus spasi, tanda hubung, padding '=', lalu uppercase. */
    fun normalizeBase32(raw: String): String =
        raw.filterNot { it.isWhitespace() || it == '-' }
            .trimEnd('=')
            .uppercase(Locale.ROOT)

    fun isValidBase32Secret(raw: String): Boolean {
        val normalized = normalizeBase32(raw)
        return normalized.length >= MIN_SECRET_CHARS &&
            normalized.all { it in BASE32_ALPHABET } &&
            decodeBase32(normalized) != null
    }

    fun decodeBase32(normalized: String): ByteArray? {
        if (normalized.length % 8 !in setOf(0, 2, 4, 5, 7)) return null
        var buffer = 0
        var bits = 0
        val out = ByteArrayOutputStream()
        for (c in normalized) {
            val value = BASE32_ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = ((buffer shl 5) or value) and 0xFFFF
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    fun generate(secret: ByteArray, nowMillis: Long): String {
        require(secret.isNotEmpty() && nowMillis >= 0)
        val counter = (nowMillis / 1000L) / PERIOD_SECONDS
        val message = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return otp.toString().padStart(DIGITS, '0')
    }

    /** Kode untuk seed base32 mentah (belum terenkripsi). Null jika seed tidak valid. */
    fun currentCodeFromBase32(base32: String, nowMillis: Long = System.currentTimeMillis()): String? {
        if (nowMillis < 0 || !isValidBase32Secret(base32)) return null
        val bytes = decodeBase32(normalizeBase32(base32)) ?: return null
        return generate(bytes, nowMillis)
    }

    /** Detik tersisa sebelum kode berganti. */
    fun secondsRemaining(nowMillis: Long = System.currentTimeMillis()): Int =
        (PERIOD_SECONDS - (nowMillis / 1000L) % PERIOD_SECONDS).toInt()

    /** Milidetik sampai batas periode berikutnya (untuk jadwal refresh). */
    fun millisUntilNextPeriod(nowMillis: Long = System.currentTimeMillis()): Long =
        PERIOD_SECONDS * 1000L - (nowMillis % (PERIOD_SECONDS * 1000L))
}

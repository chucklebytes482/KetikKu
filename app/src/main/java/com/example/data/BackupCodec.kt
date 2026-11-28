package com.example.data

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** VaultExplorer VXENC v1, AES-256-GCM (cipher ID 1), without keyfiles. */
object BackupCodec {
    private val magic = byteArrayOf(0x56, 0x58, 0x45, 0x4e, 0x43, 1)
    private const val HEADER = 128
    private const val CHUNK = 65536
    private const val TAG = 16
    private const val ITERATIONS = 100_000 // VaultExplorer writer default
    private const val MAX_CLEAR = 16 * 1024 * 1024
    internal const val MAX_FILE = HEADER + MAX_CLEAR + (MAX_CLEAR / CHUNK) * TAG
    private val random = SecureRandom()

    class AuthenticationException : Exception("Kata sandi salah atau berkas VXENC rusak.")

    /** Membuang spasi dan tab ASCII hanya di awal dan akhir. Spasi di tengah tetap dipakai. */
    fun trimPassword(value: String): String = value.trim(' ', '\t')

    /** Kata sandi dinilai setelah [trimPassword]. Tidak ada minimal karakter; maks 128 byte UTF-8. */
    fun isValidPassword(value: String): Boolean {
        val password = trimPassword(value)
        if (password.isEmpty()) return false
        val bytes = password.toByteArray(Charsets.UTF_8)
        try {
            return bytes.size <= 128
        } finally {
            bytes.fill(0)
        }
    }

    internal fun encode(json: String, password: ByteArray): ByteArray {
        val plain = json.toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= MAX_CLEAR) { "Data terlalu besar untuk cadangan VXENC." }
            val salt = ByteArray(32).also(random::nextBytes)
            val fileId = ByteArray(16).also(random::nextBytes)
            val header = ByteBuffer.wrap(ByteArray(HEADER))
                .put(magic).putShort(1).putShort(0).putInt(ITERATIONS)
                .put(salt).put(fileId).putLong(plain.size.toLong()).array()
            return withKeys(password, salt, ITERATIONS) { dataKey, headerKey ->
                headerTag(headerKey, header).copyInto(header, 70)
                val out = header.copyOf(storedSize(plain.size))
                var pos = 0
                var offset = HEADER
                while (pos < plain.size) {
                    val length = minOf(CHUNK, plain.size - pos)
                    val cipher = chunkCipher(Cipher.ENCRYPT_MODE, dataKey, fileId, pos / CHUNK)
                    offset += cipher.doFinal(plain, pos, length, out, offset)
                    pos += length
                }
                out
            }
        } finally {
            plain.fill(0)
        }
    }

    internal fun decode(file: ByteArray, password: ByteArray): String {
        require(file.size in HEADER..MAX_FILE && file.copyOfRange(0, 6).contentEquals(magic)) {
            "Berkas bukan VXENC versi 1."
        }
        val header = ByteBuffer.wrap(file)
        require(header.getShort(6).toInt() == 1 && header.getShort(8).toInt() == 0) {
            "Gunakan VXENC AES-256-GCM tanpa keyfile."
        }
        val iters = header.getInt(10)
        val length = header.getLong(62)
        require(iters in ITERATIONS..1_000_000 && length in 0L..MAX_CLEAR.toLong() &&
            file.size == storedSize(length.toInt())) { "Ukuran atau parameter berkas VXENC tidak valid." }
        val fileId = file.copyOfRange(46, 62)
        return withKeys(password, file.copyOfRange(14, 46), iters) { dataKey, headerKey ->
            if (!MessageDigest.isEqual(headerTag(headerKey, file), file.copyOfRange(70, 102))) {
                throw AuthenticationException()
            }
            val plain = ByteArray(length.toInt())
            try {
                var pos = 0
                var offset = HEADER
                while (pos < plain.size) {
                    val clearLength = minOf(CHUNK, plain.size - pos)
                    val cipher = chunkCipher(Cipher.DECRYPT_MODE, dataKey, fileId, pos / CHUNK)
                    cipher.doFinal(file, offset, clearLength + TAG, plain, pos)
                    offset += clearLength + TAG
                    pos += clearLength
                }
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString()
            } catch (_: AEADBadTagException) {
                throw AuthenticationException()
            } finally {
                plain.fill(0)
            }
        }
    }

    private fun storedSize(length: Int): Int = HEADER + length + ((length + CHUNK - 1) / CHUNK) * TAG

    private fun headerTag(key: ByteArray, header: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        update(header, 0, 70)
        doFinal()
    }

    private fun chunkCipher(mode: Int, key: ByteArray, fileId: ByteArray, index: Int): Cipher {
        val iv = ByteBuffer.allocate(12).put(fileId, 0, 8).putInt(index).array()
        val aad = ByteBuffer.allocate(24).put(fileId).putLong(index.toLong()).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(aad)
        }
    }

    private inline fun <T> withKeys(
        password: ByteArray, salt: ByteArray, iters: Int, block: (ByteArray, ByteArray) -> T
    ): T {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        // PBKDF2 (RFC 8018): VXENC uses blocks 1 and 7 of the derived bytes for AES-GCM.
        // HmacSHA256 is available on API 24–25, unlike PBKDF2WithHmacSHA256.
        val dataKey = deriveBlock(mac, salt, iters, 1)
        val headerKey = deriveBlock(mac, salt, iters, 7)
        return try { block(dataKey, headerKey) } finally { dataKey.fill(0); headerKey.fill(0) }
    }

    private fun deriveBlock(mac: Mac, salt: ByteArray, iters: Int, index: Int): ByteArray {
        var current = mac.doFinal(ByteBuffer.allocate(36).put(salt).putInt(index).array())
        var next = ByteArray(32)
        val result = current.copyOf()
        try {
            repeat(iters - 1) {
                mac.update(current)
                mac.doFinal(next, 0)
                for (i in result.indices) result[i] = (result[i].toInt() xor next[i].toInt()).toByte()
                val previous = current
                current = next
                next = previous
            }
            return result
        } finally {
            current.fill(0)
            next.fill(0)
        }
    }
}

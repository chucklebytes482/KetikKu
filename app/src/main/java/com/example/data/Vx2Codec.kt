package com.example.data

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Format VAULTX04 (berkas `.vx2`), kompatibel dengan app Neuron Encrypt dan CLI resminya.
 *
 * Tata letak: MAGIC(8) | salt(16) | stream nonce(7) | chunk...
 * Kunci: Argon2id(m=256 MiB, t=3, p=4, hash 64 B) -> HKDF-SHA512 (info khusus, 32 B).
 * Isi: AES-256-GCM-SIV per chunk 1 MiB; nonce = stream nonce(7) | counter BE32(4) | flag terakhir(1).
 * AAD setiap chunk = MAGIC | salt | stream nonce.
 */
object Vx2Codec {
    private val MAGIC = "VAULTX04".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val STREAM_NONCE_BYTES = 7
    private const val HEADER_BYTES = 8 + SALT_BYTES + STREAM_NONCE_BYTES
    private const val CHUNK = 1 shl 20
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val ARGON_MEMORY_KIB = 262144
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_LANES = 4
    private const val ARGON_OUTPUT_BYTES = 64
    private const val KEY_BYTES = 32
    private val HKDF_INFO = "VAULTX04_AES_256_GCM_SIV_STREAM_AAD".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    /** Mengenkripsi [plain] dengan [password] (UTF-8 bytes). Salt dan nonce acak setiap pemanggilan. */
    fun encrypt(plain: ByteArray, password: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val streamNonce = ByteArray(STREAM_NONCE_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        try {
            val aad = MAGIC + salt + streamNonce
            val out = ByteArrayOutputStream(HEADER_BYTES + plain.size + TAG_BYTES)
            out.write(MAGIC)
            out.write(salt)
            out.write(streamNonce)
            var offset = 0
            var counter = 0
            do {
                val len = minOf(CHUNK, plain.size - offset)
                val last = offset + len >= plain.size
                val chunk = plain.copyOfRange(offset, offset + len)
                out.write(process(true, key, nonce(streamNonce, counter, last), aad, chunk))
                offset += len
                counter++
            } while (!last)
            return out.toByteArray()
        } finally {
            key.fill(0)
        }
    }

    /**
     * Mendekripsi berkas VAULTX04. Melempar IllegalArgumentException jika formatnya salah,
     * dan IllegalStateException jika passphrase salah atau isi berkas rusak.
     */
    fun decrypt(data: ByteArray, password: ByteArray): ByteArray {
        require(data.size >= HEADER_BYTES + TAG_BYTES && data.copyOf(8).contentEquals(MAGIC)) {
            "Bukan berkas VAULTX04"
        }
        val salt = data.copyOfRange(8, 8 + SALT_BYTES)
        val streamNonce = data.copyOfRange(8 + SALT_BYTES, HEADER_BYTES)
        val key = deriveKey(password, salt)
        try {
            val aad = MAGIC + salt + streamNonce
            val body = data.size - HEADER_BYTES
            val piece = CHUNK + TAG_BYTES
            val pieces = (body + piece - 1) / piece
            val out = ByteArrayOutputStream(body)
            for (i in 0 until pieces) {
                val start = HEADER_BYTES + i * piece
                val end = minOf(data.size, start + piece)
                val last = i == pieces - 1
                val part = data.copyOfRange(start, end)
                val plainPart = try {
                    process(false, key, nonce(streamNonce, i, last), aad, part)
                } catch (_: Exception) {
                    error("Passphrase salah atau berkas rusak")
                }
                out.write(plainPart)
            }
            return out.toByteArray()
        } finally {
            key.fill(0)
        }
    }

    internal fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ARGON_ITERATIONS)
            .withMemoryAsKB(ARGON_MEMORY_KIB)
            .withParallelism(ARGON_LANES)
            .withSalt(salt)
            .build()
        val argon = Argon2BytesGenerator()
        argon.init(params)
        val intermediate = ByteArray(ARGON_OUTPUT_BYTES)
        try {
            argon.generateBytes(password, intermediate)
            val hkdf = HKDFBytesGenerator(SHA512Digest())
            hkdf.init(HKDFParameters(intermediate, null, HKDF_INFO))
            val key = ByteArray(KEY_BYTES)
            hkdf.generateBytes(key, 0, KEY_BYTES)
            return key
        } finally {
            intermediate.fill(0)
        }
    }

    private fun nonce(streamNonce: ByteArray, counter: Int, last: Boolean): ByteArray {
        val n = ByteArray(12)
        streamNonce.copyInto(n, 0)
        n[7] = (counter ushr 24).toByte()
        n[8] = (counter ushr 16).toByte()
        n[9] = (counter ushr 8).toByte()
        n[10] = counter.toByte()
        n[11] = if (last) 1 else 0
        return n
    }

    private fun process(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = GCMSIVBlockCipher()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        val processed = cipher.processBytes(input, 0, input.size, out, 0)
        val written = processed + cipher.doFinal(out, processed)
        return out.copyOf(written)
    }
}

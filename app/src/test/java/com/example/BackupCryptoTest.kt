package com.example

import com.example.data.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupCryptoTest {

    private val passphrase = "passphrase-uji-12".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }

    private fun key(pass: CharArray = passphrase) = BackupCrypto.deriveKey(pass.copyOf())

    @Test
    fun roundtripRestoresPlaintext() {
        val plain = "[{\"id\":\"1\",\"shortcut\":\"abc\"}]"
        val sealed = BackupCrypto.seal(key(), "umum", plain)
        assertTrue(BackupCrypto.isEnvelope(sealed))
        assertFalse(BackupCrypto.isLegacy(sealed))
        assertEquals("umum", BackupCrypto.kindOf(sealed))
        assertEquals(plain, BackupCrypto.open(key(), sealed, "umum"))
    }

    @Test
    fun keyIsSha256OfPassphrase() {
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(String(passphrase).toByteArray(Charsets.UTF_8))
        assertTrue(expected.contentEquals(key().encoded))
    }

    @Test
    fun wrongPassphraseFails() {
        val sealed = BackupCrypto.seal(key(), "umum", "[]")
        val wrong = "passphrase-salah-xx".toCharArray()
        assertThrows(IllegalStateException::class.java) {
            BackupCrypto.open(key(wrong), sealed, "umum")
        }
    }

    @Test
    fun wrongKindFails() {
        val sealed = BackupCrypto.seal(key(), "umum", "[]")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(key(), sealed, "kredensial")
        }
    }

    @Test
    fun tamperedCiphertextFails() {
        val sealed = BackupCrypto.seal(key(), "a2f_auto", "[]")
        val root = org.json.JSONObject(sealed)
        val bytes = android.util.Base64.decode(root.getString("ciphertext"), android.util.Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        root.put("ciphertext", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        assertThrows(IllegalStateException::class.java) {
            BackupCrypto.open(key(), root.toString(), "a2f_auto")
        }
    }

    @Test
    fun legacyEnvelopeStillReadable() {
        val plain = "[{\"id\":\"lama\"}]"
        val legacy = BackupCrypto.sealLegacy(passphrase.copyOf(), salt, "umum", plain)
        assertTrue(BackupCrypto.isLegacy(legacy))
        assertEquals(plain, BackupCrypto.openLegacy(passphrase.copyOf(), legacy, "umum"))
        assertThrows(IllegalStateException::class.java) {
            BackupCrypto.openLegacy("passphrase-salah-xx".toCharArray(), legacy, "umum")
        }
    }

    @Test
    fun plainJsonIsNotEnvelope() {
        assertFalse(BackupCrypto.isEnvelope("[]"))
        assertFalse(BackupCrypto.isEnvelope("{\"format\":\"lain\"}"))
    }

    @Test
    fun shortPassphraseRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.deriveKey("pendek".toCharArray())
        }
    }
}

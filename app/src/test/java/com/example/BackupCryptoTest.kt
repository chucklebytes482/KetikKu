package com.example

import com.example.data.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val passphrase = "passphrase-uji-12345".toCharArray()
    private val pw get() = BackupCrypto.passphraseBytes(passphrase.copyOf())

    @Test
    fun sealOpenRoundTrip() {
        val plain = "[{\"kata\":\"halo\"}]"
        val sealed = BackupCrypto.seal(pw, plain)
        assertTrue(BackupCrypto.isEnvelope(sealed))
        assertEquals(plain, BackupCrypto.open(pw, sealed))
    }

    @Test
    fun storageRoundTripIsLossless() {
        val bytes = ByteArray(256) { it.toByte() }
        assertArrayEquals(bytes, BackupCrypto.fromStorage(BackupCrypto.toStorage(bytes)))
    }

    @Test
    fun wrongPassphraseFails() {
        val sealed = BackupCrypto.seal(pw, "[]")
        val wrong = BackupCrypto.passphraseBytes("passphrase-salah-12".toCharArray())
        assertThrows(Exception::class.java) { BackupCrypto.open(wrong, sealed) }
    }

    @Test
    fun plainJsonIsNotEnvelope() {
        assertFalse(BackupCrypto.isEnvelope("[]"))
        assertFalse(BackupCrypto.isLegacyJsonEnvelope("[]"))
    }

    @Test
    fun legacyJsonEnvelopeIsDetected() {
        assertTrue(BackupCrypto.isLegacyJsonEnvelope("{\"format\":\"ketikku-enc-v2\",\"data\":\"x\"}"))
        assertFalse(BackupCrypto.isLegacyJsonEnvelope("{\"format\":\"lain\"}"))
    }

    @Test
    fun shortPassphraseRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.passphraseBytes("pendek".toCharArray())
        }
    }
}

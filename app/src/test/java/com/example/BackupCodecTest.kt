package com.example

import com.example.data.BackupCodec
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val password = "synthetic-🔒-test".toByteArray()

    @Test fun readsIndependentVaultExplorerFormatVector() {
        // Python hashlib PBKDF2 and cryptography AESGCM, using a fixed synthetic salt and file ID.
        val hex = "5658454e430100010000000186a0000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f0000000000000021075925f7e6b2280dfbb194c54eaafa463b40d37da05778264d790a3242ae935c00000000000000000000000000000000000000000000000000003236302d00710b2fb9256da1474cc429b047fa8c727effc7485b79ff11513b43b0dc43b9121b6aa4404d0bcff350570d89"
        val file = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("Uji interoperabilitas / Γ / 🔒", BackupCodec.decode(file, password))
    }

    @Test fun encryptsMultipleChunksWithFreshSaltAndNonce() {
        val text = "{" + "Uji 🔒".repeat(18000) + "}"
        val first = BackupCodec.encode(text, password)
        val second = BackupCodec.encode(text, password)
        assertEquals("VXENC", first.copyOfRange(0, 5).decodeToString())
        assertFalse(first.contentEquals(second))
        assertEquals(text, BackupCodec.decode(first, password))
        assertEquals(text, BackupCodec.decode(second, password))
    }

    @Test fun rejectsWrongPasswordTamperingTruncationAndPlaintext() {
        val file = BackupCodec.encode("{}", password)
        assertThrows(BackupCodec.AuthenticationException::class.java) {
            BackupCodec.decode(file, "wrong".toByteArray())
        }
        for (index in listOf(46, 71, 128, file.lastIndex)) {
            val modified = file.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertThrows(BackupCodec.AuthenticationException::class.java) { BackupCodec.decode(modified, password) }
        }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(file.copyOf(file.size - 1), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("{}".toByteArray(), password) }
    }

    @Test fun filtersUnicodeWhitespaceAndChecksUtf8Length() {
        assertEquals("ab🔒c", BackupCodec.withoutWhitespace("\t a\u00a0b🔒\u0085c\n"))
        assertFalse(BackupCodec.isValidPassword("a b"))
        assertFalse(BackupCodec.isValidPassword(""))
        assertTrue(BackupCodec.isValidPassword("🔒".repeat(32)))
        assertFalse(BackupCodec.isValidPassword("🔒".repeat(33)))
    }
}

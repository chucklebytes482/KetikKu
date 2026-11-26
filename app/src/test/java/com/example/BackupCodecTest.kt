package com.example

import com.example.data.BackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {
    private val json = "{\"format\":\"ketikku-dict\",\"words\":[\"halo\",\"dunia\"],\"catatan\":\"ÄÖÜ 日本\"}"

    @Test
    fun encodeProducesSingleLineBase64() {
        val encoded = BackupCodec.encode(json)
        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains('\r'))
        assertFalse(encoded.startsWith("{"))
    }

    @Test
    fun roundTripRestoresJsonExactly() {
        assertEquals(json, BackupCodec.decode(BackupCodec.encode(json)))
    }

    @Test
    fun decodeTrimsSurroundingWhitespace() {
        assertEquals(json, BackupCodec.decode("  " + BackupCodec.encode(json) + "\n"))
    }

    @Test
    fun legacyPlainJsonPassesThrough() {
        assertEquals("[]", BackupCodec.decode("[]"))
        assertEquals(json, BackupCodec.decode(json))
    }

    @Test
    fun legacyEncryptedEnvelopeIsDetected() {
        assertTrue(BackupCodec.isLegacyEncryptedEnvelope("{\"format\":\"ketikku-enc-v2\",\"data\":\"x\"}"))
        assertFalse(BackupCodec.isLegacyEncryptedEnvelope("{\"format\":\"lain\"}"))
        assertFalse(BackupCodec.isLegacyEncryptedEnvelope("[]"))
        assertFalse(BackupCodec.isLegacyEncryptedEnvelope(json))
    }
}

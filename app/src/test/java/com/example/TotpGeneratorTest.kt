package com.example

import com.example.data.TotpGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {
    // RFC 6238 Appendix B, secret ASCII "12345678901234567890" (SHA1), kode 6 digit = 8 digit terakhir
    private val rfcSecretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun decodeBase32_matchesRfcSecret() {
        val bytes = TotpGenerator.decodeBase32(rfcSecretBase32)
        assertNotNull(bytes)
        assertEquals("12345678901234567890", String(bytes!!, Charsets.US_ASCII))
    }

    @Test
    fun currentCode_matchesRfc6238Vectors() {
        assertEquals("287082", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 59_000L))
        assertEquals("081804", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 1_111_111_109_000L))
        assertEquals("050471", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 1_111_111_111_000L))
        assertEquals("005924", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 1_234_567_890_000L))
        assertEquals("279037", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 2_000_000_000_000L))
        assertEquals("353130", TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 20_000_000_000_000L))
    }

    @Test
    fun normalize_removesSpacesPaddingAndCase() {
        assertEquals(rfcSecretBase32, TotpGenerator.normalizeBase32("gezd gnbv-gy3t qojq gezd gnbv gy3t qojq==="))
    }

    @Test
    fun validation_rejectsBadInput() {
        assertTrue(TotpGenerator.isValidBase32Secret("gezd gnbv gy3t qojq"))
        assertFalse(TotpGenerator.isValidBase32Secret("ABC"))
        assertFalse(TotpGenerator.isValidBase32Secret("GEZDGNBV0Y3TQOJQ"))
    }

    @Test
    fun secondsRemaining_isWithinPeriod() {
        assertEquals(30, TotpGenerator.secondsRemaining(0L))
        assertEquals(1, TotpGenerator.secondsRemaining(29_000L))
        assertEquals(30, TotpGenerator.secondsRemaining(30_000L))
        assertEquals(1L, TotpGenerator.millisUntilNextPeriod(29_999L))
        assertEquals(30_000L, TotpGenerator.millisUntilNextPeriod(30_000L))
    }

    @Test
    fun invalidLengthAndNonZeroPaddingBits_areRejected() {
        org.junit.Assert.assertNull(TotpGenerator.decodeBase32("AAAAAAAAAAAAAAAAA"))
        org.junit.Assert.assertNull(TotpGenerator.decodeBase32("AAAAAAAAAAAAAAAAAB"))
        org.junit.Assert.assertNull(TotpGenerator.currentCodeFromBase32("ABC", 0L))
        org.junit.Assert.assertNull(TotpGenerator.currentCodeFromBase32(rfcSecretBase32, -1L))
        assertFalse(TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 29_999L) ==
            TotpGenerator.currentCodeFromBase32(rfcSecretBase32, 30_000L))
    }
}

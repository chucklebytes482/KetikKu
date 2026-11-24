package com.example

import android.util.Base64
import com.example.data.A2fBackup
import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class A2fBackupTest {
    private val seed = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private fun legacyEntry(): PersonalDictionaryEntry {
        val key = MessageDigest.getInstance("SHA-256").digest("KetikKu-A2F-seed-v1".toByteArray())
        val iv = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(seed.toByteArray())
        return PersonalDictionaryEntry(id = "rfc-test", shortcut = "gh", folder = SettingsRepository.FOLDER_A2F,
            secretCipher = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    @Test fun passwordBackup_roundTripsAndHidesMetadata() {
        val raw = A2fBackup.encode(listOf(legacyEntry()), "correct-password".toCharArray())
        assertFalse(raw.contains(seed))
        assertFalse(raw.contains("rfc-test"))
        val row = A2fBackup.decryptPayload(raw, "correct-password".toCharArray()).getJSONObject(0)
        assertEquals(seed, row.getString("seed"))
        assertEquals("gh", row.getString("shortcut"))
    }

    @Test fun wrongPasswordAndTampering_areRejected() {
        val raw = A2fBackup.encode(listOf(legacyEntry()), "correct-password".toCharArray())
        assertTrue(runCatching { A2fBackup.decryptPayload(raw, "wrong-password".toCharArray()) }.isFailure)
        val root = JSONObject(raw)
        val bytes = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        root.put("ciphertext", Base64.encodeToString(bytes, Base64.NO_WRAP))
        assertTrue(runCatching { A2fBackup.decryptPayload(root.toString(), "correct-password".toCharArray()) }.isFailure)
    }

    @Test fun excessiveIterationsAndShortExportPassword_areRejected() {
        assertTrue(runCatching { A2fBackup.encode(listOf(legacyEntry()), "short".toCharArray()) }.isFailure)
        val root = JSONObject(A2fBackup.encode(emptyList(), "correct-password".toCharArray()))
        root.put("iterations", Int.MAX_VALUE)
        assertTrue(runCatching { A2fBackup.decryptPayload(root.toString(), "correct-password".toCharArray()) }.isFailure)
    }
}
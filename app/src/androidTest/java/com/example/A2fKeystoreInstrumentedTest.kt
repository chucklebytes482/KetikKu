package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.BackupCodec
import com.example.data.PortableBackupEntry
import com.example.data.VxencBackupPayload
import com.example.data.SecretVault
import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on an Android device/emulator, not a JVM Keystore substitute. */
@RunWith(AndroidJUnit4::class)
class A2fKeystoreInstrumentedTest {
    @Test fun localVaultAndPortableBackup_roundTrip() {
        val seed = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val local = SecretVault.encrypt(seed)
        assertTrue(local.startsWith("k2:"))
        assertEquals(seed, SecretVault.decryptOrNull(local))
        assertNotEquals(local, SecretVault.encrypt(seed))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(store.getKey("ketikku.a2f.v2", null).encoded)
        val entry = PersonalDictionaryEntry(id = "test-only", word = "synthetic@example.test", shortcut = "autexample",
            folder = SettingsRepository.FOLDER_A2F, secretCipher = local)
        val payload = VxencBackupPayload(1, emptyList(), listOf(PortableBackupEntry.fromLocal(entry)))
        val password = "test-only-password".toByteArray()
        val file = BackupCodec.encode(payload.encode(), password)
        val restored = VxencBackupPayload.decode(BackupCodec.decode(file, password)).entries.single()
        assertEquals("", restored.entry.secretCipher)
        val restoredCipher = SecretVault.encrypt(requireNotNull(restored.seed))
        assertEquals(seed, SecretVault.decryptOrNull(restoredCipher))
        assertNotEquals(local, restoredCipher)
    }
}

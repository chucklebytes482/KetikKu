package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.BackupCodec
import com.example.data.BackupFolderException
import com.example.data.BackupPasswordCipher
import com.example.data.DeviceBackupStorage
import com.example.data.DeviceDictionaryBackupManager
import com.example.data.DeviceSyncResult
import com.example.data.SettingsRepository
import com.example.data.VxencBackupPayload
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pengganti Android Keystore: nilai "fake:<sandi>" bisa dibuka, selain itu dianggap rusak. */
internal object FakeCipher : BackupPasswordCipher {
    override fun encrypt(password: String): String = "fake:$password"
    override fun decryptOrNull(value: String): String? = value.takeIf { it.startsWith("fake:") }?.removePrefix("fake:")
}

private class FakeStorage(var bytes: ByteArray?) : DeviceBackupStorage {
    var replaceCalls = 0
    var failRead = false
    override fun read(folderUri: String): ByteArray? {
        if (failRead) throw BackupFolderException("Folder cadangan tidak dapat diakses.")
        return bytes
    }
    override fun replace(folderUri: String, bytes: ByteArray, expected: ByteArray?) {
        replaceCalls++
        this.bytes = bytes
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceBackupPasswordTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun TestScope.settings(): SettingsRepository {
        val dir = temporaryFolder.newFolder()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            dir.resolve("settings.preferences_pb")
        }
        return SettingsRepository(dataStore)
    }

    private fun vxencFile(password: String): ByteArray =
        BackupCodec.encode(VxencBackupPayload(1L, emptyList(), emptyList()).encode(), password.toByteArray())

    @Test
    fun autoUnlockOpensWithSavedPasswordAndKeepsIt() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("fake:pw-satu")
        val storage = FakeStorage(vxencFile("pw-satu"))
        val manager = DeviceDictionaryBackupManager(repo, storage, FakeCipher)

        assertTrue(manager.autoUnlock() is DeviceSyncResult.Synced)
        assertEquals("fake:pw-satu", repo.getDeviceBackupPasswordCipher())
    }

    @Test
    fun replacedFileWithOtherPasswordClearsSavedPasswordAndLeavesFileAlone() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("fake:pw-satu")
        val replacedFile = vxencFile("pw-dua")
        val storage = FakeStorage(replacedFile)
        val manager = DeviceDictionaryBackupManager(repo, storage, FakeCipher)

        assertTrue(manager.autoUnlock() is DeviceSyncResult.Error)
        assertNull(repo.getDeviceBackupPasswordCipher())
        assertEquals(0, storage.replaceCalls)
        assertArrayEquals(replacedFile, storage.bytes)
    }

    @Test
    fun nonVxencFileClearsSavedPasswordInsteadOfRetryingForever() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("fake:pw-satu")
        val storage = FakeStorage("bukan vxenc".toByteArray())
        val manager = DeviceDictionaryBackupManager(repo, storage, FakeCipher)

        val result = manager.autoUnlock()
        assertTrue(result is DeviceSyncResult.Error)
        assertEquals("Berkas cadangan tidak dapat dibaca sebagai VXENC. Cadangan tidak diubah.", (result as DeviceSyncResult.Error).message)
        assertNull(repo.getDeviceBackupPasswordCipher())
        assertEquals(0, storage.replaceCalls)
    }

    @Test
    fun brokenStoredValueIsForgottenAndStaysLocked() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("rusak-tanpa-awalan")
        val manager = DeviceDictionaryBackupManager(repo, FakeStorage(vxencFile("pw-satu")), FakeCipher)

        assertTrue(manager.autoUnlock() is DeviceSyncResult.Locked)
        assertNull(repo.getDeviceBackupPasswordCipher())
    }

    @Test
    fun folderErrorKeepsSavedPassword() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("fake:pw-satu")
        val storage = FakeStorage(vxencFile("pw-satu")).apply { failRead = true }
        val manager = DeviceDictionaryBackupManager(repo, storage, FakeCipher)

        assertTrue(manager.autoUnlock() is DeviceSyncResult.Error)
        assertEquals("fake:pw-satu", repo.getDeviceBackupPasswordCipher())
    }

    @Test
    fun successfulManualUnlockSavesPasswordEncrypted() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        val manager = DeviceDictionaryBackupManager(repo, FakeStorage(vxencFile("pw-satu")), FakeCipher)

        assertTrue(manager.unlockAndSync("pw-satu") is DeviceSyncResult.Synced)
        assertEquals("fake:pw-satu", repo.getDeviceBackupPasswordCipher())
    }

    @Test
    fun forgetPasswordClearsStoredValue() = runTest {
        val repo = settings()
        repo.saveDeviceBackupTreeUri("content://backup")
        repo.saveDeviceBackupPassword("fake:pw-satu")
        DeviceDictionaryBackupManager(repo, FakeStorage(null), FakeCipher).forgetPassword()
        assertNull(repo.getDeviceBackupPasswordCipher())
    }
}

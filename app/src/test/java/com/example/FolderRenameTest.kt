package com.example

import com.example.data.DeviceDictionaryBackupManager
import com.example.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRenameTest {
    @Test
    fun legacyFolderNamesMapToShortCodes() {
        assertEquals("gm", SettingsRepository.normalizeFolderPath("Gmail"))
        assertEquals("pw", SettingsRepository.normalizeFolderPath("Kata Sandi"))
        assertEquals("tras", SettingsRepository.normalizeFolderPath("Sampah"))
        assertEquals("aut", SettingsRepository.normalizeFolderPath("A2F"))
        assertEquals("bc", SettingsRepository.normalizeFolderPath("Keuangan"))
    }

    @Test
    fun customFolderClashingWithNewNameIsReported() {
        val clashes = SettingsRepository.customFoldersClashingWithPrimary(listOf("GM", "bc", "Kerja"))
        assertEquals(setOf("GM", "bc"), clashes)
    }

    @Test
    fun backupFileNamesUseNewPatternAndKeepLegacyFallback() {
        assertEquals("ketikku_pw.json", DeviceDictionaryBackupManager.BACKUP_FILE_KATA_SANDI)
        assertEquals("ketikku_kred.json", DeviceDictionaryBackupManager.BACKUP_FILE_KREDENSIAL)
        assertEquals("ketikku_aut.json", DeviceDictionaryBackupManager.BACKUP_FILE_A2F)
        assertEquals("ketikku_aut_terenkripsi.json", DeviceDictionaryBackupManager.BACKUP_FILE_A2F_AUTO)
        assertEquals("ketikku_kata_sandi.json", DeviceDictionaryBackupManager.LEGACY_BACKUP_FILE_NAMES[DeviceDictionaryBackupManager.BACKUP_FILE_KATA_SANDI])
        assertEquals("ketikku_a2f_terenkripsi.json", DeviceDictionaryBackupManager.LEGACY_BACKUP_FILE_NAMES[DeviceDictionaryBackupManager.BACKUP_FILE_A2F_AUTO])
        assertTrue(DeviceDictionaryBackupManager.LEGACY_BACKUP_FILE_NAMES.values.none { it == DeviceDictionaryBackupManager.BACKUP_FILE_UMUM })
    }
}

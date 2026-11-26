package com.example

import com.example.data.DeviceDictionaryBackupManager
import com.example.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRenameTest {
    @Test
    fun oldFolderNamesStayCustomAndPrimaryCodesStillMatch() {
        assertEquals("gm", SettingsRepository.normalizeFolderPath("GM"))
        assertEquals("pw", SettingsRepository.normalizeFolderPath("Pw"))
        assertEquals("Gmail", SettingsRepository.normalizeFolderPath("Gmail"))
        assertEquals("Kata Sandi", SettingsRepository.normalizeFolderPath("Kata Sandi"))
        assertEquals("Sampah", SettingsRepository.normalizeFolderPath("Sampah"))
        assertEquals("Keuangan", SettingsRepository.normalizeFolderPath("Keuangan"))
    }

    @Test
    fun customFolderClashingWithNewNameIsReported() {
        val clashes = SettingsRepository.customFoldersClashingWithPrimary(listOf("PW", "bc", "Kerja", "GM"))
        assertEquals(setOf("PW", "bc"), clashes)
    }

    @Test
    fun backupUsesOneVxencFile() {
        assertEquals("ketikku.vxenc", DeviceDictionaryBackupManager.FILE_NAME)
    }
}

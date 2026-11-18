package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.squareup.moshi.JsonClass

enum class KeyboardLanguage(val code: String, val displayName: String, val shortBadge: String) {
    INDONESIAN("id-ID", "Bahasa Indonesia", "ID"),
    ENGLISH("en-US", "English (US)", "EN");

    companion object {
        fun fromCode(code: String?): KeyboardLanguage =
            entries.firstOrNull { it.code == code || it.name == code } ?: INDONESIAN
    }
}

enum class KeyboardHeightPreset(val label: String, val keyHeightDp: Float) {
    SHORT("Pendek", 40f),
    MID_SHORT("Agak Pendek", 44f),
    NORMAL("Normal", 48f),
    MID_TALL("Agak Tinggi", 52f),
    TALL("Tinggi", 56f);

    companion object {
        fun fromName(raw: String?): KeyboardHeightPreset =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

@JsonClass(generateAdapter = true)
data class KeyboardSettings(
    val heightPreset: String = KeyboardHeightPreset.NORMAL.name,
    val hapticEnabled: Boolean = true,
    val clipboardHistoryEnabled: Boolean = true,
    val arialItalicEnabled: Boolean = false,
    val serifMiringEnabled: Boolean = false,
    val autoSyncDriveEnabled: Boolean = true,
    val driveFolderName: String = "KetikKu",
    val driveShortcutFolderId: String = "",
    val updatedAtMillis: Long = 0L
) {
    val heightEnum: KeyboardHeightPreset get() = KeyboardHeightPreset.fromName(heightPreset)
}

@JsonClass(generateAdapter = true)
data class PersonalDictionaryEntry(
    val id: String = "",
    val word: String = "",
    val shortcut: String = "",
    val language: String = KeyboardLanguage.INDONESIAN.code,
    val updatedAtMillis: Long = 0L,
    val deleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DictionaryBackupEnvelope(
    val schemaVersion: Int = 1,
    val updatedAtMillis: Long = 0L,
    val entries: List<PersonalDictionaryEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SettingsBackupEnvelope(
    val schemaVersion: Int = 1,
    val updatedAtMillis: Long = 0L,
    val settings: KeyboardSettings = KeyboardSettings()
)

@JsonClass(generateAdapter = true)
data class FullBackupEnvelope(
    val schemaVersion: Int = 1,
    val appName: String = "KetikKu",
    val exportedAtMillis: Long = 0L,
    val settings: KeyboardSettings = KeyboardSettings(),
    val dictionary: List<PersonalDictionaryEntry> = emptyList()
)

data class ClipboardItem(
    val id: String = "",
    val text: String = "",
    val timestampMillis: Long = 0L
)

data class SyncLocalState(
    val lastSyncedAtMillis: Long = 0L,
    val lastSyncMessage: String = "Belum pernah disinkronkan",
    val driveFolderId: String = "",
    val driveSettingsFileId: String = "",
    val driveDictFileId: String = "",
    val pendingChanges: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class UserSyncProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val autoSync: Boolean = true,
    val driveFolderId: String? = null,
    val driveFolderName: String = "KetikKu",
    val lastSyncedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BackupSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val driveFileId: String? = null,
    val settingsJson: String = "{}",
    val dictionaryCount: Long = 0L,
    val syncSource: String = "CLOUD_ONLY",
    val syncedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

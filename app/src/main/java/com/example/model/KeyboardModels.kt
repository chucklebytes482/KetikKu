package com.example.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.squareup.moshi.Json
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
    NORMAL("Normal", 48f);

    companion object {
        fun fromName(raw: String?): KeyboardHeightPreset = NORMAL
    }
}

@Immutable
@JsonClass(generateAdapter = true)
data class KeyboardSettings(
    val heightPreset: String = KeyboardHeightPreset.NORMAL.name,
    val floatingModeEnabled: Boolean = false,
    val floatingPosX: Float = 0.5f,
    val floatingPosY: Float = 0.85f,
    val hapticEnabled: Boolean = true,
    val clipboardHistoryEnabled: Boolean = true,
    val arialItalicEnabled: Boolean = false,
    val serifMiringEnabled: Boolean = false,
    val swapPeriodCommaEnabled: Boolean = false,
    val swapSymbolPanelsEnabled: Boolean = false,
    val autoSyncDriveEnabled: Boolean = false,
    val driveFolderName: String = "KetikKu",
    val driveShortcutFolderId: String = "",
    val updatedAtMillis: Long = 0L
) {
    val heightEnum: KeyboardHeightPreset get() = KeyboardHeightPreset.fromName(heightPreset)
}

@Immutable
@JsonClass(generateAdapter = true)
data class PersonalDictionaryEntry(
    val id: String = "",
    val word: String = "",
    val shortcut: String = "",
    val folder: String = "",
    @param:Json(ignore = true) val language: String = KeyboardLanguage.INDONESIAN.code,
    val updatedAtMillis: Long = 0L,
    val deleted: Boolean = false
)

@Immutable
@JsonClass(generateAdapter = true)
data class DictionaryBackupEnvelope(
    val schemaVersion: Int = 1,
    val updatedAtMillis: Long = 0L,
    val folders: List<String> = emptyList(),
    val entries: List<PersonalDictionaryEntry> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SettingsBackupEnvelope(
    val schemaVersion: Int = 1,
    val updatedAtMillis: Long = 0L,
    val settings: KeyboardSettings = KeyboardSettings()
)

@Immutable
@JsonClass(generateAdapter = true)
data class FullBackupEnvelope(
    val schemaVersion: Int = 1,
    val appName: String = "KetikKu",
    val exportedAtMillis: Long = 0L,
    val settings: KeyboardSettings = KeyboardSettings(),
    val folders: List<String> = emptyList(),
    val dictionary: List<PersonalDictionaryEntry> = emptyList()
)

@Immutable
data class ClipboardItem(
    val id: String = "",
    val preview: String = "",
    val charCount: Int = 0,
    val timestampMillis: Long = 0L,
    val contentHash: String = ""
) {
    fun dismissSignature(): String = if (contentHash.isNotBlank()) {
        "$charCount:$contentHash"
    } else {
        "preview:$charCount:$preview"
    }
}

@Immutable
@JsonClass(generateAdapter = true)
data class WordPersonalityStat(
    val word: String = "",
    val usageCount: Int = 1,
    val lastUsedMillis: Long = 0L
)

@Immutable
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

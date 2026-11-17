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

enum class KeyboardThemePreset(val label: String) {
    SLATE_CYAN("Slate Cyan"),
    OBSIDIAN_MONO("Obsidian Mono"),
    DAYLIGHT_CLEAN("Daylight Clean"),
    FOREST_SAGE("Forest Sage"),
    COBALT_NIGHT("Cobalt Night");

    companion object {
        fun fromName(raw: String?): KeyboardThemePreset =
            entries.firstOrNull { it.name == raw } ?: SLATE_CYAN
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

enum class OneHandedMode(val label: String) {
    OFF("Penuh"),
    LEFT("Tangan Kiri"),
    RIGHT("Tangan Kanan");

    companion object {
        fun fromName(raw: String?): OneHandedMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

@JsonClass(generateAdapter = true)
data class KeyboardSettings(
    val language: String = KeyboardLanguage.INDONESIAN.code,
    val themePreset: String = KeyboardThemePreset.SLATE_CYAN.name,
    val heightPreset: String = KeyboardHeightPreset.NORMAL.name,
    val oneHandedMode: String = OneHandedMode.OFF.name,
    val numberRowEnabled: Boolean = true,
    val keyBordersEnabled: Boolean = true,
    val keyPopupEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val autoCorrectEnabled: Boolean = true,
    val suggestionStripEnabled: Boolean = true,
    val autoCapitalizeEnabled: Boolean = true,
    val doubleSpacePeriodEnabled: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val spacebarTrackpadEnabled: Boolean = true,
    val swipeDeleteEnabled: Boolean = true,
    val clipboardHistoryEnabled: Boolean = true,
    val showLanguageSwitchKey: Boolean = true,
    val longPressDelayMs: Int = 280,
    val autoSyncDriveEnabled: Boolean = true,
    val driveFolderName: String = "KetikKu",
    val driveShortcutFolderId: String = "",
    val updatedAtMillis: Long = 0L
) {
    val languageEnum: KeyboardLanguage get() = KeyboardLanguage.fromCode(language)
    val themeEnum: KeyboardThemePreset get() = KeyboardThemePreset.fromName(themePreset)
    val heightEnum: KeyboardHeightPreset get() = KeyboardHeightPreset.fromName(heightPreset)
    val oneHandedEnum: OneHandedMode get() = OneHandedMode.fromName(oneHandedMode)
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

data class ClipboardItem(
    val id: String = "",
    val text: String = "",
    val timestampMillis: Long = 0L,
    val pinned: Boolean = false
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

package com.example.model

import androidx.compose.runtime.Immutable
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
    val deleted: Boolean = false,
    // Seed TOTP terenkripsi per perangkat (AES-GCM, Android Keystore). Format: k2:base64(iv):base64(ciphertext); cadangan portabel memakai VXENC.
    val secretCipher: String = "",
    // Folder asal saat entri dipindah ke Sampah; kosong jika tidak di Sampah.
    val trashedFrom: String = ""
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
    val deviceBackupTreeUri: String = "",
    val pendingChanges: Boolean = false
)

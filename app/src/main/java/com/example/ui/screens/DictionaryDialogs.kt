package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SettingsRepository
import com.example.data.TotpGenerator
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.model.PersonalDictionaryEntry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

@Composable
internal fun DictionaryEntryDialog(
    initialEntry: PersonalDictionaryEntry?,
    defaultFolderPath: String,
    isA2f: Boolean = false,
    isKred: Boolean = false,
    usedShortcuts: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (word: String, shortcut: String, language: String, folder: String, secretPlain: String?, email: String, website: String) -> Unit,
    onRestore: (() -> Unit)? = null,
    onPermanentDelete: (() -> Unit)? = null
) {
    val inTrash = initialEntry != null &&
        SettingsRepository.normalizeFolderPath(initialEntry.folder) == SettingsRepository.FOLDER_SAMPAH
    val folder = SettingsRepository.normalizeFolderPath(initialEntry?.folder ?: defaultFolderPath)
    val isNew = initialEntry == null
    val language = initialEntry?.language ?: "id-ID"
    // Entri A2F menyimpan "email\nwebsite" di kolom kata.
    val a2fParts = initialEntry?.word?.split('\n').orEmpty()
    // Entri kredensial menyimpan "kata\nwebsite" di kolom kata.
    val kredParts = if (isKred) initialEntry?.word?.split('\n').orEmpty() else emptyList()

    var email by remember(initialEntry) { mutableStateOf(if (isA2f) a2fParts.getOrElse(0) { "" } else "") }
    var website by remember(initialEntry) {
        mutableStateOf(
            when {
                isA2f -> a2fParts.getOrElse(1) { "" }
                isKred -> kredParts.getOrElse(1) { "" }
                else -> ""
            }
        )
    }
    var word by remember(initialEntry) {
        mutableStateOf(
            when {
                isA2f -> ""
                isKred -> kredParts.getOrElse(0) { "" }
                else -> initialEntry?.word.orEmpty()
            }
        )
    }
    var shortcut by remember(initialEntry) { mutableStateOf(initialEntry?.shortcut.orEmpty()) }
    var seed by remember(initialEntry) { mutableStateOf("") }

    // Entri yang sudah tersimpan terkunci: kata/email/website/seed tidak bisa diubah. Hanya pintasan non-A2F yang bisa diedit.
    val seedEditable = !inTrash && isNew
    // Folder umum (num, prib, bc): semua field bisa diubah, termasuk kata.
    val openFolder = SettingsRepository.normalizeFolderPath(folder) in SettingsRepository.OPEN_FIELD_FOLDERS
    val fieldsLocked = !isNew && !openFolder
    // Folder gm/pw/aut/kred: pintasan otomatis. Entri yang sudah tersimpan terkunci penuh.
    val shortcutLockedFolder = SettingsRepository.isShortcutLockedFolder(folder)
    val savedLockedFolder = !isNew && shortcutLockedFolder

    val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    // Field wajib kosong: menonaktifkan Simpan, tanpa pesan dan tanpa warna merah.
    val emailMissing = isA2f && isNew && email.isBlank()
    val siteMissing = (isA2f || isKred) && isNew && website.isBlank()
    val seedMissing = isA2f && isNew && seed.isBlank()
    // Pintasan A2F dibuat otomatis saat simpan (email + website), jadi tidak pernah wajib diisi di dialog.
    val shortcutMissing = false
    val wordMissing = !isA2f && isNew && word.isBlank()

    // Pesan format (bukan kosong) tetap ditampilkan.
    val emailError = when {
        !isA2f || !isNew -> null
        !email.trim().matches(emailRegex) -> "Email tidak valid"
        else -> null
    }
    val seedError = when {
        !isA2f || !seedEditable -> null
        seed.isBlank() -> null
        !TotpGenerator.isValidBase32Secret(seed) -> "Seed base32 tidak valid (min. 16 karakter A-Z, 2-7)"
        else -> null
    }
    val shortcutError = when {
        shortcutMissing -> null
        isA2f && (shortcut.trim().length > 30 || shortcut.trim().any { !it.isLetterOrDigit() }) ->
            "Pintasan A2F: maksimal 30 huruf/angka tanpa spasi"
        shortcut.isNotBlank() && usedShortcuts.any { it.equals(shortcut.trim(), true) } -> "Pintasan sudah dipakai"
        else -> null
    }
    val wordError = when {
        isA2f || !isNew -> null
        wordMissing -> null
        else -> null
    }
    val canSave = !inTrash && shortcutError == null && emailError == null &&
        seedError == null && wordError == null &&
        !emailMissing && !siteMissing && !seedMissing && !shortcutMissing && !wordMissing

    NoCopyProvider {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    inTrash -> "Entri di tras"
                    isA2f && isNew -> "Tambah Akun A2F"
                    isA2f -> "Akun A2F"
                    isKred && isNew -> "Tambah kred"
                    isNew -> "Tambah ke Kamus Pribadi"
                    savedLockedFolder -> "Entri Terkunci"
                    else -> "Edit Pintasan"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Website selalu paling atas untuk A2F dan kred (termasuk pratinjau baca).
                if (isA2f || isKred) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        enabled = !(fieldsLocked),
                        colors = dictFieldColors(),
                        label = { Text("Website") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(if (isA2f) "a2f_website_input" else "kred_website_input")
                    )
                    }
                }
                if (isA2f) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        enabled = !(fieldsLocked),
                        colors = dictFieldColors(),
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("a2f_email_input")
                    )
                    }
                    // Seed hanya ditampilkan saat membuat akun baru. Seed tidak pernah ditampilkan dalam mode baca.
                    if (seedEditable) {
                        // Seed A2F tidak bisa disalin: tidak ada overlay salin.
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it },
                            colors = dictFieldColors(),
                            label = { Text("Seed (secret base32)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("a2f_secret_input")
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        enabled = !(fieldsLocked),
                        colors = dictFieldColors(),
                        label = { Text("Kata atau Frasa") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().testTag("dictionary_word_input")
                    )
                    }
                }
                // Pintasan otomatis (pw, aut, kred) tidak ditampilkan. Hanya folder lain yang punya kolom pintasan.
                if (!shortcutLockedFolder) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        enabled = !inTrash,
                        colors = dictFieldColors(),
                        label = { Text("Pintasan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dictionary_shortcut_input")
                    )
                    }
                }
                if (inTrash) {
                    Text(
                        text = if (onPermanentDelete != null) {
                            "Bisa dihapus permanen, atau pulihkan untuk mengedit lagi."
                        } else if (isA2f) {
                            "Akun A2F hanya bisa dihapus permanen jika duplikat. Pulihkan untuk mengedit lagi."
                        } else {
                            "Pulihkan untuk mengedit lagi. Entri di tras tidak muncul di saran."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (inTrash) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onRestore?.invoke() },
                        enabled = onRestore != null,
                        modifier = Modifier.testTag("restore_dictionary_button")
                    ) {
                        Text("Pulihkan")
                    }
                    if (onPermanentDelete != null) {
                        TextButton(
                            onClick = onPermanentDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("permanent_delete_button")
                        ) {
                            Text("Hapus permanen")
                        }
                    }
                }
            } else if (savedLockedFolder) {
                // Entri tersimpan di folder terkunci: tidak ada yang bisa disimpan ulang.
                Spacer(Modifier.width(0.dp))
            } else {
                Button(
                    onClick = {
                        onSave(
                            word,
                            shortcut,
                            language,
                            folder,
                            if (isA2f && seedEditable && seed.isNotBlank()) TotpGenerator.normalizeBase32(seed) else null,
                            email,
                            website
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.testTag("save_dictionary_button")
                ) {
                    Text("Simpan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
    }
}


@Composable
internal fun FolderNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialValue) { mutableStateOf(initialValue) }
    NoCopyProvider {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_folder_name_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
    }
}

@Composable
internal fun ConfirmDeleteItemsDialog(
    entryCount: Int,
    folderCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val summary = buildList {
        if (folderCount > 0) add("$folderCount folder beserta isinya")
        if (entryCount > 0) add("$entryCount kata kamus")
    }.joinToString(" dan ")

    NoCopyProvider {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pindahkan ke tras?") },
        text = { Text("Pindahkan $summary ke folder tras? Bisa dipulihkan lagi.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_items_button")
            ) {
                Text("Pindahkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
    }
}


/** Menu salin/potong/tempel dimatikan: isi field di dialog Kamus Pribadi tidak bisa disalin. */
private object NoCopyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {}
    override fun hide() {}
}

@Composable
private fun NoCopyProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextToolbar provides NoCopyTextToolbar, content = content)
}

/**
 * Field mode baca (disabled): tidak bisa diketuk, tidak bisa ditekan lama, dan tidak bisa disalin.
 * Warna disamakan dengan mode aktif supaya tampilannya tidak berubah jadi abu-abu.
 */
@Composable
private fun dictFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = Color.Transparent,
)

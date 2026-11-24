package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry

@Composable
internal fun DictionaryEntryDialog(
    initialEntry: PersonalDictionaryEntry?,
    defaultFolderPath: String,
    onDismiss: () -> Unit,
    onSave: (word: String, shortcut: String, language: String, folder: String) -> Unit
) {
    var word by remember(initialEntry) { mutableStateOf(initialEntry?.word.orEmpty()) }
    var shortcut by remember(initialEntry) { mutableStateOf(initialEntry?.shortcut.orEmpty()) }
    val targetFolder = initialEntry?.folder ?: defaultFolderPath
    val language = initialEntry?.language ?: "id-ID"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialEntry == null) "Tambah ke Kamus Pribadi" else "Edit Kata & Pintasan")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Kata atau Frasa") },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_word_input")
                )
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Pintasan (misal: ttd)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_shortcut_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(word, shortcut, language, SettingsRepository.normalizeFolderPath(targetFolder)) },
                enabled = word.isNotBlank(),
                modifier = Modifier.testTag("save_dictionary_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
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

@Composable
internal fun MoveToFolderDialog(
    allFolders: List<String>,
    excludedFolderPrefixes: Set<String>,
    itemCountLabel: String,
    onDismiss: () -> Unit,
    onConfirmMove: (targetFolderPath: String) -> Unit
) {
    val validTargets = remember(allFolders, excludedFolderPrefixes) {
        allFolders.filterNot { candidate ->
            excludedFolderPrefixes.any { src ->
                SettingsRepository.isSameOrDescendantFolder(candidate, src)
            }
        }
    }
    var selectedTarget by remember { mutableStateOf("") }
    var customNewFolder by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pindahkan $itemCountLabel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pilih folder tujuan atau tulis nama folder baru:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = customNewFolder,
                    onValueChange = { customNewFolder = it },
                    label = { Text("Atau buat folder baru (mis: Kerja)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_custom_folder_input")
                )

                val useScrollList = validTargets.size > 5
                Column(
                    modifier = if (useScrollList) {
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MoveDestinationOption(
                        label = "Utama (Tanpa Folder)",
                        isRoot = true,
                        selected = customNewFolder.isBlank() && selectedTarget == "",
                        onClick = {
                            customNewFolder = ""
                            selectedTarget = ""
                        },
                        tag = "move_target_root"
                    )
                    validTargets.forEach { folderPath ->
                        MoveDestinationOption(
                            label = folderPath,
                            isRoot = false,
                            selected = customNewFolder.isBlank() && selectedTarget == folderPath,
                            onClick = {
                                customNewFolder = ""
                                selectedTarget = folderPath
                            },
                            tag = "move_target_${folderPath.replace('/', '_')}"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val dest = if (customNewFolder.isNotBlank()) {
                                SettingsRepository.normalizeFolderPath(customNewFolder)
                            } else {
                                selectedTarget
                            }
                            onConfirmMove(dest)
                        },
                        modifier = Modifier.testTag("confirm_move_button")
                    ) {
                        Text("Pindahkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveDestinationOption(
    label: String,
    isRoot: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isRoot) Icons.Default.Home else Icons.Default.Folder,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Dipilih",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus Item Terpilih?") },
        text = { Text("Yakin ingin menghapus $summary dari Kamus Pribadi?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_items_button")
            ) {
                Text("Hapus")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

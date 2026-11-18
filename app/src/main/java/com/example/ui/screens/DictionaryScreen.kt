package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry

@Composable
fun DictionaryScreen(
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    onUpsertEntry: (word: String, shortcut: String, language: String, existingId: String?) -> Unit,
    onDeleteEntry: (entryId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dictSearchQuery by rememberSaveable { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }

    val q = dictSearchQuery.trim().lowercase()
    val activeEntries = remember(dictionary, q) {
        dictionary
            .filter { !it.deleted }
            .filter { entry ->
                q.isEmpty() ||
                    entry.word.lowercase().contains(q) ||
                    entry.shortcut.lowercase().contains(q)
            }
            .sortedBy { it.word.lowercase() }
    }

    val totalCount = remember(dictionary) { dictionary.count { !it.deleted } }
    val shortcutCount = remember(dictionary) { dictionary.count { !it.deleted && it.shortcut.isNotBlank() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = dictSearchQuery,
                    onValueChange = { dictSearchQuery = it },
                    placeholder = { Text("Cari kata atau pintasan kamus...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cari kamus")
                    },
                    trailingIcon = {
                        if (dictSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { dictSearchQuery = "" },
                                modifier = Modifier.testTag("clear_dictionary_search_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian kamus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$totalCount kata • $shortcutCount pintasan",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Ketuk kata untuk mengedit kata & pintasan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_dictionary_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah")
                    }
                }
            }

            if (activeEntries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = if (q.isNotEmpty()) {
                                    "Tidak ada kata atau pintasan yang cocok dengan \"$dictSearchQuery\"."
                                } else {
                                    "Kamus pribadi masih kosong. Tekan Tambah untuk membuat pintasan cepat."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(activeEntries, key = { it.id }) { entry ->
                    DictionaryItemCard(
                        entry = entry,
                        onEdit = { editingEntry = entry },
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog || editingEntry != null) {
        DictionaryEntryDialog(
            initialEntry = editingEntry,
            onDismiss = {
                showAddDialog = false
                editingEntry = null
            },
            onSave = { word, shortcut, lang ->
                onUpsertEntry(word, shortcut, lang, editingEntry?.id)
                showAddDialog = false
                editingEntry = null
            }
        )
    }
}

@Composable
private fun DictionaryItemCard(
    entry: PersonalDictionaryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .testTag("dictionary_item_${entry.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.shortcut.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Pintasan: ${entry.shortcut}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Tanpa pintasan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_dictionary_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Hapus kata ${entry.word}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DictionaryEntryDialog(
    initialEntry: PersonalDictionaryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var word by remember(initialEntry) { mutableStateOf(initialEntry?.word.orEmpty()) }
    var shortcut by remember(initialEntry) { mutableStateOf(initialEntry?.shortcut.orEmpty()) }
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
                    label = { Text("Pintasan (opsional, misal: ttd)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_shortcut_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(word, shortcut, language) },
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

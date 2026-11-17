package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardHeightPreset
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemePreset
import com.example.model.OneHandedMode
import com.example.model.PersonalDictionaryEntry
import com.example.ui.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val q = searchQuery.trim().lowercase()
    fun matches(vararg terms: String): Boolean =
        q.isEmpty() || terms.any { it.lowercase().contains(q) }

    var showAddDictDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }

    val activeDict = remember(dictionary, q) {
        dictionary
            .filter { !it.deleted }
            .filter {
                q.isEmpty() ||
                    it.word.lowercase().contains(q) ||
                    it.shortcut.lowercase().contains(q)
            }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Cari setelan atau kata kamus...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
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
                        .testTag("settings_search_input")
                )
            }

            // Section 1: Pengetikan Cerdas
            if (matches("koreksi", "saran", "glide", "geser", "kapital", "spasi", "ketik")) {
                item {
                    SettingsSectionCard(title = "Pengetikan Cerdas") {
                        if (matches("koreksi", "autocorrect")) {
                            ToggleSettingRow(
                                title = "Koreksi Otomatis",
                                subtitle = "Perbaiki salah ketik secara otomatis saat menekan spasi",
                                checked = settings.autoCorrectEnabled,
                                testTag = "toggle_autocorrect",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCorrectEnabled = v) } }
                            )
                        }
                        if (matches("saran", "prediksi", "kata")) {
                            ToggleSettingRow(
                                title = "Bilah Saran Kata",
                                subtitle = "Tampilkan 3 kandidat kata & prediksi kata berikutnya",
                                checked = settings.suggestionStripEnabled,
                                testTag = "toggle_suggestions",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(suggestionStripEnabled = v) } }
                            )
                        }
                        if (matches("glide", "geser", "ketik")) {
                            ToggleSettingRow(
                                title = "Ketik Geser (Glide Typing)",
                                subtitle = "Geser jari melintasi huruf untuk menulis kata utuh",
                                checked = settings.glideTypingEnabled,
                                testTag = "toggle_glide",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(glideTypingEnabled = v) } }
                            )
                        }
                        if (matches("kapital", "huruf besar")) {
                            ToggleSettingRow(
                                title = "Kapitalisasi Otomatis",
                                subtitle = "Huruf besar otomatis di awal setiap kalimat",
                                checked = settings.autoCapitalizeEnabled,
                                testTag = "toggle_autocaps",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCapitalizeEnabled = v) } }
                            )
                        }
                        if (matches("spasi", "titik", "periode")) {
                            ToggleSettingRow(
                                title = "Spasi Ganda Jadi Titik",
                                subtitle = "Ketuk spasi dua kali untuk menyisipkan titik dan spasi",
                                checked = settings.doubleSpacePeriodEnabled,
                                testTag = "toggle_double_space",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(doubleSpacePeriodEnabled = v) } }
                            )
                        }
                    }
                }
            }

            // Section 2: Tata Letak, Bahasa & Tema
            if (matches("bahasa", "tema", "tinggi", "angka", "batas", "satu tangan", "tata letak")) {
                item {
                    SettingsSectionCard(title = "Tata Letak, Bahasa & Tema") {
                        if (matches("bahasa", "indonesia", "english")) {
                            Text(
                                text = "Bahasa Utama",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KeyboardLanguage.entries.forEach { lang ->
                                    FilterChip(
                                        selected = settings.languageEnum == lang,
                                        onClick = { viewModel.updateSettings { it.copy(language = lang.code) } },
                                        label = { Text(lang.displayName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        if (matches("tema", "warna", "tampilan")) {
                            Text(
                                text = "Tema Keyboard",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KeyboardThemePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = settings.themeEnum == preset,
                                        onClick = { viewModel.updateSettings { it.copy(themePreset = preset.name) } },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        if (matches("tinggi", "ukuran")) {
                            Text(
                                text = "Tinggi Keyboard",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KeyboardHeightPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = settings.heightEnum == preset,
                                        onClick = { viewModel.updateSettings { it.copy(heightPreset = preset.name) } },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        if (matches("satu tangan", "mode")) {
                            Text(
                                text = "Mode Satu Tangan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OneHandedMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.oneHandedEnum == mode,
                                        onClick = { viewModel.setOneHandedMode(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (matches("angka", "baris")) {
                            ToggleSettingRow(
                                title = "Baris Angka Khusus",
                                subtitle = "Tampilkan baris angka 1–0 permanen di atas huruf",
                                checked = settings.numberRowEnabled,
                                testTag = "toggle_number_row",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(numberRowEnabled = v) } }
                            )
                        }
                        if (matches("batas", "bingkai", "tombol")) {
                            ToggleSettingRow(
                                title = "Batas Tombol",
                                subtitle = "Garis tepi halus di sekitar setiap tombol",
                                checked = settings.keyBordersEnabled,
                                testTag = "toggle_key_borders",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(keyBordersEnabled = v) } }
                            )
                        }
                        if (matches("tombol", "ganti bahasa")) {
                            ToggleSettingRow(
                                title = "Tombol Ganti Bahasa",
                                subtitle = "Tampilkan ikon bola dunia di sebelah tombol koma",
                                checked = settings.showLanguageSwitchKey,
                                testTag = "toggle_lang_key",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(showLanguageSwitchKey = v) } }
                            )
                        }
                    }
                }
            }

            // Section 3: Gestur, Respons & Papan Klip
            if (matches("getar", "suara", "popup", "trackpad", "hapus", "papan klip", "clipboard")) {
                item {
                    SettingsSectionCard(title = "Gestur, Respons & Papan Klip") {
                        if (matches("trackpad", "kursor", "spasi")) {
                            ToggleSettingRow(
                                title = "Kontrol Kursor di Spasi",
                                subtitle = "Geser kiri/kanan pada spasi untuk memindahkan kursor",
                                checked = settings.spacebarTrackpadEnabled,
                                testTag = "toggle_spacebar_trackpad",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(spacebarTrackpadEnabled = v) } }
                            )
                        }
                        if (matches("hapus", "geser")) {
                            ToggleSettingRow(
                                title = "Geser Hapus Kata",
                                subtitle = "Geser ke kiri dari tombol hapus untuk menghapus per kata",
                                checked = settings.swipeDeleteEnabled,
                                testTag = "toggle_swipe_delete",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(swipeDeleteEnabled = v) } }
                            )
                        }
                        if (matches("clipboard", "papan klip")) {
                            ToggleSettingRow(
                                title = "Riwayat Papan Klip",
                                subtitle = "Simpan teks yang baru disalin dan sematkan klip penting",
                                checked = settings.clipboardHistoryEnabled,
                                testTag = "toggle_clipboard_history",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(clipboardHistoryEnabled = v) } }
                            )
                        }
                        if (matches("getar", "haptic")) {
                            ToggleSettingRow(
                                title = "Getar Saat Ditekan",
                                subtitle = "Umpan balik haptik ringan pada setiap ketukan",
                                checked = settings.hapticEnabled,
                                testTag = "toggle_haptic",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticEnabled = v) } }
                            )
                        }
                        if (matches("suara", "bunyi")) {
                            ToggleSettingRow(
                                title = "Suara Ketikan",
                                subtitle = "Efek bunyi klik halus saat mengetik",
                                checked = settings.soundEnabled,
                                testTag = "toggle_sound",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(soundEnabled = v) } }
                            )
                        }
                        if (matches("popup", "pratinjau")) {
                            ToggleSettingRow(
                                title = "Pop-up Tombol",
                                subtitle = "Perbesar karakter saat tombol ditekan lama",
                                checked = settings.keyPopupEnabled,
                                testTag = "toggle_key_popup",
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(keyPopupEnabled = v) } }
                            )
                        }
                    }
                }
            }

            // Section 4: Kamus Pribadi & Pintasan Teks (double-tap to edit per personal workflow!)
            item {
                SettingsSectionCard(
                    title = "Kamus Pribadi & Pintasan",
                    trailingAction = {
                        TextButton(
                            onClick = { showAddDictDialog = true },
                            modifier = Modifier.testTag("add_dictionary_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Tambah")
                        }
                    }
                ) {
                    Text(
                        text = "Ketuk dua kali item untuk mengedit cepat. Ikut dicadangkan ke Google Drive.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (activeDict.isEmpty()) {
                        Text(
                            text = "Belum ada kata atau pintasan pribadi yang cocok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeDict.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .pointerInput(entry.id) {
                                            detectTapGestures(
                                                onDoubleTap = { editingEntry = entry }
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.word,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (entry.shortcut.isNotBlank()) {
                                            Text(
                                                text = "Pintasan: ${entry.shortcut} • ${entry.language}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = entry.language,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteDictionaryEntry(entry.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus kata",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDictDialog || editingEntry != null) {
        DictionaryEntryDialog(
            initialEntry = editingEntry,
            defaultLanguage = settings.language,
            onDismiss = {
                showAddDictDialog = false
                editingEntry = null
            },
            onSave = { word, shortcut, lang ->
                viewModel.upsertDictionaryEntry(
                    word = word,
                    shortcut = shortcut,
                    language = lang,
                    existingId = editingEntry?.id
                )
                showAddDictDialog = false
                editingEntry = null
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                trailingAction?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}

@Composable
private fun DictionaryEntryDialog(
    initialEntry: PersonalDictionaryEntry?,
    defaultLanguage: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var word by remember { mutableStateOf(initialEntry?.word.orEmpty()) }
    var shortcut by remember { mutableStateOf(initialEntry?.shortcut.orEmpty()) }
    val language = initialEntry?.language ?: defaultLanguage

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialEntry == null) "Tambah ke Kamus Pribadi" else "Edit Entri Kamus")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Kata atau Frasa") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Pintasan (opsional, misal: ttd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(word, shortcut, language) },
                enabled = word.isNotBlank()
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

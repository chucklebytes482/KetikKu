package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardHeightPreset
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    searchQuery: String,
    onOpenDictionary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val q = searchQuery.trim().lowercase()
    fun matches(vararg terms: String): Boolean =
        q.isEmpty() || terms.any { it.lowercase().contains(q) }

    val activeWordCount = remember(dictionary) { dictionary.count { !it.deleted } }
    val activeShortcutCount = remember(dictionary) { dictionary.count { !it.deleted && it.shortcut.isNotBlank() } }

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
                    placeholder = { Text("Cari setelan keyboard...") },
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

            // Menu Khusus Kamus Pribadi
            if (matches("kamus", "pribadi", "pintasan", "shortcut", "kata", "frasa")) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenDictionary)
                            .testTag("open_dictionary_menu_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Kamus Pribadi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kamus Pribadi",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$activeWordCount kata • $activeShortcutCount pintasan • Cari & edit kata",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Buka Kamus Pribadi",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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

            // Section 2: Tata Letak & Bahasa
            if (matches("bahasa", "tinggi", "angka", "batas", "tata letak")) {
                item {
                    SettingsSectionCard(title = "Tata Letak & Bahasa") {
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
                    }
                }
            }

            // Section 3: Gestur, Respons & Papan Klip
            if (matches("getar", "suara", "popup", "trackpad", "hapus", "papan klip", "clipboard", "pilih keyboard", "spasi")) {
                item {
                    SettingsSectionCard(title = "Gestur, Respons & Papan Klip") {
                        if (matches("trackpad", "kursor", "spasi", "pilih keyboard")) {
                            ToggleSettingRow(
                                title = "Kontrol Kursor di Spasi",
                                subtitle = "Geser kiri/kanan spasi untuk kursor • Tahan spasi untuk pilih keyboard",
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
                                subtitle = "Simpan sementara teks yang disalin untuk ditempel cepat",
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

            item { Spacer(Modifier.height(24.dp)) }
        }
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

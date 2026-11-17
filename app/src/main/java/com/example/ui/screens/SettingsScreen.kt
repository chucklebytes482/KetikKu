package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardHeightPreset
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberPending
import com.example.ui.theme.MintSynced

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    searchQuery: String,
    imeStatus: ImeActivationStatus,
    onOpenDictionary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshImeStatus(context)
    }

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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // IME Activation Status Card (Unified directly into Settings)
            item {
                ImeStatusCard(
                    imeStatus = imeStatus,
                    onEnableInSettings = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    onSelectIme = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                        viewModel.refreshImeStatus(context)
                    }
                )
            }

            // Menu Khusus Kamus Pribadi
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenDictionary)
                        .testTag("open_dictionary_menu_button")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Kamus Pribadi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kamus Pribadi",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$activeWordCount kata • $activeShortcutCount pintasan",
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

            // Section 1: Tata Letak & Bahasa
            item {
                SettingsSectionCard(title = "Tata Letak & Bahasa") {
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
                }
            }

            // Section 2: Respons & Papan Klip
            item {
                SettingsSectionCard(title = "Respons & Papan Klip") {
                    ToggleSettingRow(
                        title = "Riwayat Papan Klip",
                        subtitle = "Simpan sementara teks yang disalin untuk ditempel cepat",
                        checked = settings.clipboardHistoryEnabled,
                        testTag = "toggle_clipboard_history",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(clipboardHistoryEnabled = v) } }
                    )
                    ToggleSettingRow(
                        title = "Getar Saat Ditekan",
                        subtitle = "Umpan balik haptik ringan pada setiap ketukan",
                        checked = settings.hapticEnabled,
                        testTag = "toggle_haptic",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticEnabled = v) } }
                    )
                }
            }

            // Section 3: Cadangan & Sinkronisasi
            item {
                SettingsSectionCard(title = "Cadangan & Sinkronisasi") {
                    ToggleSettingRow(
                        title = "Sinkronisasi Cloud / Drive",
                        subtitle = "Cadangkan kamus dan setelan secara otomatis ke cloud (matikan jika ingin offline penuh)",
                        checked = settings.autoSyncDriveEnabled,
                        testTag = "toggle_sync",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoSyncDriveEnabled = v) } }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ImeStatusCard(
    imeStatus: ImeActivationStatus,
    onEnableInSettings: () -> Unit,
    onSelectIme: () -> Unit
) {
    val dotColor = when (imeStatus) {
        ImeActivationStatus.ACTIVE_DEFAULT -> MintSynced
        ImeActivationStatus.ENABLED_NOT_DEFAULT -> AmberPending
        ImeActivationStatus.DISABLED -> MaterialTheme.colorScheme.error
    }
    val statusText = when (imeStatus) {
        ImeActivationStatus.ACTIVE_DEFAULT -> "KetikKu Aktif sebagai Keyboard Utama"
        ImeActivationStatus.ENABLED_NOT_DEFAULT -> "Sudah Diaktifkan • Pilih KetikKu sebagai Default"
        ImeActivationStatus.DISABLED -> "Aktifkan KetikKu di Setelan Sistem untuk Dipakai"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (imeStatus == ImeActivationStatus.ACTIVE_DEFAULT) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Aktif",
                        tint = MintSynced,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEnableInSettings,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("enable_ime_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("1. Aktifkan", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onSelectIme,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("select_ime_button")
                ) {
                    Icon(
                        imageVector = if (imeStatus == ImeActivationStatus.ACTIVE_DEFAULT) {
                            Icons.Default.Keyboard
                        } else {
                            Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("2. Pilih", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
                .padding(vertical = 6.dp),
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

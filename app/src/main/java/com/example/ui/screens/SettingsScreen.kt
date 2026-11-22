package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
import com.example.ui.DeviceSyncUiState
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberPending
import com.example.ui.theme.MintSynced
import kotlinx.coroutines.delay

internal const val KETIKKU_WEBSITE_URL = "https://chucklebytes482.github.io/KetikKu/"

internal const val KETIKKU_GENERAL_AI_PROMPT = """Struktur berkas JSON kamus pribadi KetikKu (ketikku_dictionary.json):
{
  "updatedAtMillis": 0,
  "folders": ["Gmail", "Nomor", "Pribadi", "Kata Sandi", "Kredensial", "Balasan Cepat"],
  "entries": [
    {
      "id": "string unik entri",
      "word": "teks atau nilai entri",
      "shortcut": "pintasan ketik (opsional; wajib untuk Kata Sandi & Kredensial)",
      "folder": "nama folder datar 1 tingkat tanpa subfolder, atau kosong untuk Utama",
      "updatedAtMillis": 0,
      "deleted": false
    }
  ]
}
Aturan struktur:
- Folder datar 1 tingkat saja (tanpa subfolder dan tanpa tanda '/').
- 6 folder utama: "Gmail", "Nomor", "Pribadi", "Kata Sandi", "Kredensial", "Balasan Cepat".
- Setiap entri memiliki 6 field: id, word, shortcut, folder, updatedAtMillis, deleted (tanpa field "language").
- Entri di folder "Kata Sandi" dan "Kredensial" hanya muncul di bilah saran jika pintasannya diketik."""

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    imeStatus: ImeActivationStatus,
    onOpenDictionary: () -> Unit,
    onOpenDeviceBackupSheet: () -> Unit = {},
    syncLocalState: SyncLocalState = SyncLocalState(),
    deviceSyncUiState: DeviceSyncUiState = DeviceSyncUiState.Idle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var testInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("settings_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Paling atas: Cadangan Perangkat Otomatis (ModalBottomSheet)
            item {
                val backupSubtitle = when (deviceSyncUiState) {
                    is DeviceSyncUiState.Syncing -> "Documents/KetikKu • Menyinkronkan..."
                    is DeviceSyncUiState.NeedsFolderAccess -> "Documents/KetikKu • Perlu izin folder"
                    is DeviceSyncUiState.Error -> "Documents/KetikKu • Gagal sinkronisasi"
                    is DeviceSyncUiState.Idle -> if (syncLocalState.lastSyncedAtMillis > 0L) {
                        "Documents/KetikKu • Tersinkronisasi"
                    } else {
                        "Documents/KetikKu • Otomatis saat dibuka"
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenDeviceBackupSheet)
                        .testTag("open_device_backup_sheet_button")
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
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Cadangan Perangkat",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cadangan Perangkat",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = backupSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Buka Cadangan Perangkat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Nomor 2: Web Panduan (GitHub Pages)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KETIKKU_WEBSITE_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .testTag("open_website_button")
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
                                imageVector = Icons.Default.Public,
                                contentDescription = "Web Panduan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Web Panduan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GitHub Pages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Buka Web Panduan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. Status Aktivasi Keyboard
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

            // 4. Kolom Uji Ketik
            item {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                ) {
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        label = { Text("Coba ketik di sini") },
                        placeholder = { Text("Ketik, tekan lama, lalu pilih teks") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_typing_field")
                    )
                }
            }

            // 5. Menu Kamus Pribadi (File Manager)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
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

            // 6. 1 Prompt AI Umum untuk edit ketikku_dictionary.json
            item {
                GeneralAiPromptCard(context = context)
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GeneralAiPromptCard(context: Context) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000L)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("ai_dictionary_prompt_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Prompt AI Umum",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prompt AI Umum",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Referensi struktur Documents/KetikKu/ketikku_dictionary.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = KETIKKU_GENERAL_AI_PROMPT,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Prompt AI KetikKu", KETIKKU_GENERAL_AI_PROMPT))
                    copied = true
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("copy_ai_dictionary_prompt_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (copied) "Prompt Tersalin" else "Salin Prompt AI",
                    fontWeight = FontWeight.SemiBold
                )
            }
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
                        .height(48.dp)
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
                        .height(48.dp)
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

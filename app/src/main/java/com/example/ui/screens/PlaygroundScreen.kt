package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ime.KetikKuKeyboardView
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberPending
import com.example.ui.theme.ArialFamily
import com.example.ui.theme.InterFamily
import com.example.ui.theme.MintSynced

@Composable
fun PlaygroundScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    clipboardItems: List<ClipboardItem>,
    imeStatus: ImeActivationStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshImeStatus(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // System IME Activation Status Card
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

            // Live Playground Text Field
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Uji Ketik Langsung",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ketuk ikon (I) untuk Arial Italic • Tahan spasi untuk pilih keyboard",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val txt = viewModel.playgroundText.text
                                    if (txt.isNotBlank()) {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("KetikKu", txt))
                                        viewModel.addClipboardText(txt)
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Salin ke papan klip",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearPlaygroundText() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("clear_playground_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Bersihkan teks",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.playgroundText,
                        onValueChange = { viewModel.updatePlaygroundText(it) },
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = if (settings.arialItalicEnabled) ArialFamily else InterFamily,
                            fontStyle = if (settings.arialItalicEnabled) FontStyle.Italic else FontStyle.Normal
                        ),
                        placeholder = {
                            Text(
                                "Ketuk tombol keyboard KetikKu di bawah untuk mulai mengetik...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playground_text_field")
                    )
                }
            }
        }

        // Embedded live KetikKu Keyboard for instant testing
        KetikKuKeyboardView(
            controller = viewModel.playgroundController,
            settings = settings,
            personalEntries = dictionary,
            clipboardItems = clipboardItems,
            onPasteClipboard = { text ->
                viewModel.playgroundController.pasteText(text, settings, dictionary)
            },
            onCopySelectionToClipboard = { selected ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("KetikKu", selected))
                viewModel.addClipboardText(selected)
            },
            onClearAllClipboard = { viewModel.clearAllClipboard() },
            onToggleArialItalic = { viewModel.toggleArialItalic() },
            onShowInputMethodPicker = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
                viewModel.refreshImeStatus(context)
            },
            modifier = Modifier.fillMaxWidth()
        )
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
        ImeActivationStatus.DISABLED -> "Aktifkan KetikKu di Setelan Sistem untuk Dipakai di Semua Aplikasi"
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

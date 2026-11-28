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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ime.KetikKuKeyboardView
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.GoogleYellow700
import com.example.ui.theme.InterFamily
import com.example.ui.theme.GoogleGreen700

import kotlinx.coroutines.launch

@Composable
fun PlaygroundScreen(
    viewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    clipboardItems: List<ClipboardItem>,
    dismissedClipboardId: String? = null,
    dismissedClipboardSignature: String? = null,
    imeStatus: ImeActivationStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshImeStatus(context)
    }

    val onPasteClipboard = remember(viewModel, settings, dictionary, coroutineScope) {
        { text: String ->
            coroutineScope.launch {
                viewModel.playgroundController.pasteTextChunked(text, settings, dictionary)
            }
            Unit
        }
    }
    val onPasteClipboardItem = remember(viewModel, settings, dictionary, coroutineScope) {
        { item: ClipboardItem ->
            coroutineScope.launch {
                try {
                    val fullText = viewModel.loadClipboardFullText(item.id) ?: item.preview
                    viewModel.playgroundController.pasteTextChunked(fullText, settings, dictionary)
                } catch (_: Throwable) {
                    viewModel.playgroundController.showClipboardStatus("Gagal menempel klip")
                }
            }
            Unit
        }
    }
    val onRequestCtrlPaste = remember(viewModel, settings, dictionary, context, coroutineScope) {
        {
            coroutineScope.launch {
                try {
                    val sysText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                        } else ""
                    }
                    val fullText = sysText.ifEmpty {
                        viewModel.loadLatestClipboardFullText().orEmpty()
                    }
                    viewModel.playgroundController.pasteTextChunked(fullText, settings, dictionary)
                } catch (_: Throwable) {
                    viewModel.playgroundController.showClipboardStatus("Gagal menempel teks")
                }
            }
            Unit
        }
    }
    val onDismissRecentClipboard = remember(viewModel) {
        { id: String -> viewModel.dismissRecentClipboard(id) }
    }
    val onCopySelectionToClipboard = remember(viewModel, context) {
        { selected: String ->
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("KetikKu", selected))
            }
            viewModel.addClipboardText(selected)
        }
    }
    val onToggleFloatingMode = remember(viewModel) {
        { viewModel.toggleFloatingMode() }
    }
    val onSaveFloatingPosition = remember(viewModel) {
        { x: Float, y: Float -> viewModel.saveFloatingPosition(x, y) }
    }
    val onShowInputMethodPicker = remember(viewModel, context) {
        {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
            viewModel.refreshImeStatus(context)
        }
    }
    val onEnableImeSettings = remember(context) {
        {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
    val onSelectIme = remember(viewModel, context) {
        {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
            viewModel.refreshImeStatus(context)
        }
    }

    if (settings.floatingModeEnabled) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaygroundEditorCard(
                    viewModel = viewModel,
                    onCopyText = onCopySelectionToClipboard
                )

                ImeStatusCard(
                    imeStatus = imeStatus,
                    onEnableInSettings = onEnableImeSettings,
                    onSelectIme = onSelectIme
                )

                Spacer(Modifier.height(320.dp))
            }

            KetikKuKeyboardView(
                controller = viewModel.playgroundController,
                settings = settings,
                personalEntries = dictionary,
                clipboardItems = clipboardItems,
                dismissedClipboardId = dismissedClipboardId,
                dismissedClipboardSignature = dismissedClipboardSignature,
                onPasteClipboard = onPasteClipboard,
                onPasteClipboardItem = onPasteClipboardItem,
                onRequestCtrlPaste = onRequestCtrlPaste,
                onDismissRecentClipboard = onDismissRecentClipboard,
                onCopySelectionToClipboard = onCopySelectionToClipboard,
                onToggleFloatingMode = onToggleFloatingMode,
                onSaveFloatingPosition = onSaveFloatingPosition,
                onShowInputMethodPicker = onShowInputMethodPicker,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaygroundEditorCard(
                    viewModel = viewModel,
                    onCopyText = onCopySelectionToClipboard
                )

                ImeStatusCard(
                    imeStatus = imeStatus,
                    onEnableInSettings = onEnableImeSettings,
                    onSelectIme = onSelectIme
                )
            }

            KetikKuKeyboardView(
                controller = viewModel.playgroundController,
                settings = settings,
                personalEntries = dictionary,
                clipboardItems = clipboardItems,
                dismissedClipboardId = dismissedClipboardId,
                dismissedClipboardSignature = dismissedClipboardSignature,
                onPasteClipboard = onPasteClipboard,
                onPasteClipboardItem = onPasteClipboardItem,
                onRequestCtrlPaste = onRequestCtrlPaste,
                onDismissRecentClipboard = onDismissRecentClipboard,
                onCopySelectionToClipboard = onCopySelectionToClipboard,
                onToggleFloatingMode = onToggleFloatingMode,
                onSaveFloatingPosition = onSaveFloatingPosition,
                onShowInputMethodPicker = onShowInputMethodPicker,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlaygroundEditorCard(
    viewModel: MainViewModel,
    onCopyText: (String) -> Unit
) {
    val tf = viewModel.playgroundText
    val selMin = tf.selection.min.coerceIn(0, tf.text.length)
    val selMax = tf.selection.max.coerceIn(0, tf.text.length)
    val hasSelection = selMax > selMin
    val selectedPreview = if (hasSelection) tf.text.substring(selMin, selMax) else ""
    val selectedCodePoints = if (hasSelection) Character.codePointCount(tf.text, selMin, selMax) else 0
    val selectionColors = remember(MaterialTheme.colorScheme.primary) {
        TextSelectionColors(
            handleColor = ColorSelectionHandle,
            backgroundColor = ColorSelectionBg
        )
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Uji Ketik Langsung",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tahan ⌫ untuk hapus terus • Gunakan Ctrl untuk Z/X/C/V",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val sample = "Halo KetikKu, uji hapus dan fungsi Ctrl."
                            viewModel.updatePlaygroundText(
                                TextFieldValue(text = sample, selection = TextRange(sample.length))
                            )
                        },
                        modifier = Modifier.testTag("fill_sample_playground_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Contoh")
                    }
                    IconButton(
                        onClick = {
                            val txt = viewModel.playgroundText.text
                            if (txt.isNotBlank()) {
                                onCopyText(txt)
                            }
                        },
                        modifier = Modifier.size(48.dp)
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
                            .size(48.dp)
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

            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                OutlinedTextField(
                    value = tf,
                    onValueChange = { viewModel.updatePlaygroundText(it) },
                    readOnly = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = InterFamily,
                        fontStyle = FontStyle.Normal
                    ),
                    placeholder = {
                        Text(
                            "Ketuk tombol keyboard KetikKu di bawah atau tekan Contoh untuk mulai...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
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

            // Live Selection / Cursor Highlight Banner
            if (tf.text.isNotEmpty()) {
                val cursorPos = tf.selection.end.coerceIn(0, tf.text.length)
                val focusChar = if (cursorPos < tf.text.length) {
                    val cp = Character.codePointAt(tf.text, cursorPos)
                    String(Character.toChars(cp))
                } else null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasSelection) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (hasSelection) {
                            "Menyorot $selectedCodePoints huruf: \"$selectedPreview\""
                        } else if (focusChar != null) {
                            "Kursor di posisi $cursorPos (menyorot huruf '$focusChar')"
                        } else {
                            "Kursor di akhir teks (posisi $cursorPos)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasSelection) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("playground_selection_status")
                    )
                }
            }
        }
    }
}

private val ColorSelectionHandle = androidx.compose.ui.graphics.Color(0xFF1A73E8)
private val ColorSelectionBg = androidx.compose.ui.graphics.Color(0xFF8AB4F8).copy(alpha = 0.35f)

@Composable
private fun ImeStatusCard(
    imeStatus: ImeActivationStatus,
    onEnableInSettings: () -> Unit,
    onSelectIme: () -> Unit
) {
    val dotColor = when (imeStatus) {
        ImeActivationStatus.ACTIVE_DEFAULT -> GoogleGreen700
        ImeActivationStatus.ENABLED_NOT_DEFAULT -> GoogleYellow700
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
                        tint = GoogleGreen700,
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

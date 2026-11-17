package com.example.ime

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.KeyPoint
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemePreset
import com.example.model.OneHandedMode
import com.example.model.PersonalDictionaryEntry
import com.example.ui.theme.InterFamily
import com.example.ui.theme.PlusJakartaSansFamily
import kotlin.math.roundToInt

private data class KeyboardPalette(
    val boardBg: Color,
    val stripBg: Color,
    val keyBg: Color,
    val keyPressedBg: Color,
    val specialKeyBg: Color,
    val accentBg: Color,
    val accentContent: Color,
    val keyText: Color,
    val keyHintText: Color,
    val borderColor: Color,
    val chipBg: Color,
    val chipHighlightBg: Color
)

private fun paletteFor(preset: KeyboardThemePreset): KeyboardPalette = when (preset) {
    KeyboardThemePreset.SLATE_CYAN -> KeyboardPalette(
        boardBg = Color(0xFF0E171E),
        stripBg = Color(0xFF13202A),
        keyBg = Color(0xFF1E2D39),
        keyPressedBg = Color(0xFF2D4456),
        specialKeyBg = Color(0xFF17242E),
        accentBg = Color(0xFF67D4E4),
        accentContent = Color(0xFF042B33),
        keyText = Color(0xFFF1F5F9),
        keyHintText = Color(0xFF8FA7B8),
        borderColor = Color(0xFF2A3D4C),
        chipBg = Color(0xFF1A2833),
        chipHighlightBg = Color(0xFF13414D)
    )
    KeyboardThemePreset.OBSIDIAN_MONO -> KeyboardPalette(
        boardBg = Color(0xFF0A0A0B),
        stripBg = Color(0xFF121214),
        keyBg = Color(0xFF212124),
        keyPressedBg = Color(0xFF36363B),
        specialKeyBg = Color(0xFF18181B),
        accentBg = Color(0xFFE2E8F0),
        accentContent = Color(0xFF0A0A0B),
        keyText = Color(0xFFFAFAFA),
        keyHintText = Color(0xFF94A3B8),
        borderColor = Color(0xFF2E2E33),
        chipBg = Color(0xFF1C1C1F),
        chipHighlightBg = Color(0xFF2C2C32)
    )
    KeyboardThemePreset.DAYLIGHT_CLEAN -> KeyboardPalette(
        boardBg = Color(0xFFE7ECF0),
        stripBg = Color(0xFFF2F5F8),
        keyBg = Color(0xFFFFFFFF),
        keyPressedBg = Color(0xFFD9E3EC),
        specialKeyBg = Color(0xFFD1DBE4),
        accentBg = Color(0xFF006978),
        accentContent = Color(0xFFFFFFFF),
        keyText = Color(0xFF111C24),
        keyHintText = Color(0xFF5D707E),
        borderColor = Color(0xFFC5D2DC),
        chipBg = Color(0xFFE1E8EE),
        chipHighlightBg = Color(0xFFCCEBF0)
    )
    KeyboardThemePreset.FOREST_SAGE -> KeyboardPalette(
        boardBg = Color(0xFF0E1915),
        stripBg = Color(0xFF13221D),
        keyBg = Color(0xFF1D3129),
        keyPressedBg = Color(0xFF2B493D),
        specialKeyBg = Color(0xFF162720),
        accentBg = Color(0xFF6EE7B7),
        accentContent = Color(0xFF052E1E),
        keyText = Color(0xFFECFDF5),
        keyHintText = Color(0xFF8FB8A8),
        borderColor = Color(0xFF284338),
        chipBg = Color(0xFF1A2C25),
        chipHighlightBg = Color(0xFF174535)
    )
    KeyboardThemePreset.COBALT_NIGHT -> KeyboardPalette(
        boardBg = Color(0xFF0D1324),
        stripBg = Color(0xFF121B33),
        keyBg = Color(0xFF1C2747),
        keyPressedBg = Color(0xFF2A3B69),
        specialKeyBg = Color(0xFF151E38),
        accentBg = Color(0xFF7AA2FF),
        accentContent = Color(0xFF081433),
        keyText = Color(0xFFEFF4FF),
        keyHintText = Color(0xFF93A6D6),
        borderColor = Color(0xFF27365E),
        chipBg = Color(0xFF18223D),
        chipHighlightBg = Color(0xFF203463)
    )
}

private val longPressAlternates: Map<String, List<String>> = mapOf(
    "q" to listOf("1"),
    "w" to listOf("2"),
    "e" to listOf("3", "é", "è"),
    "r" to listOf("4"),
    "t" to listOf("5"),
    "y" to listOf("6"),
    "u" to listOf("7", "ü"),
    "i" to listOf("8", "í"),
    "o" to listOf("9", "ó"),
    "p" to listOf("0"),
    "a" to listOf("@", "á", "à"),
    "s" to listOf("#", "$"),
    "d" to listOf("&"),
    "f" to listOf("_"),
    "g" to listOf("-"),
    "h" to listOf("+"),
    "j" to listOf("("),
    "k" to listOf(")"),
    "l" to listOf("/"),
    "z" to listOf("*"),
    "x" to listOf("\""),
    "c" to listOf("'", "ç"),
    "v" to listOf(":"),
    "b" to listOf(";"),
    "n" to listOf("!", "ñ"),
    "m" to listOf("?"),
    "." to listOf(",", "!", "?", "'", ":", ";", "/", "@", "#"),
    "," to listOf(";", ":", "\"", "'")
)

@Composable
fun KetikKuKeyboardView(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    clipboardItems: List<ClipboardItem>,
    onPasteClipboard: (String) -> Unit,
    onToggleClipboardPin: (String) -> Unit,
    onDeleteClipboardItem: (String) -> Unit,
    onClearUnpinnedClipboard: () -> Unit,
    onCopySelectionToClipboard: (String) -> Unit,
    onToggleOneHanded: (OneHandedMode) -> Unit,
    onCycleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = remember(settings.themePreset) { paletteFor(settings.themeEnum) }
    val context = LocalContext.current
    val view = LocalView.current

    fun feedback() {
        if (settings.hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (settings.soundEnabled) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.6f)
        }
    }

    var activePopupKey by remember { mutableStateOf<String?>(null) }
    var activePopupOffset by remember { mutableStateOf(Offset.Zero) }
    var longPressOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    val totalHeightDp = remember(settings.heightPreset, settings.numberRowEnabled) {
        val baseRow = settings.heightEnum.keyHeightDp
        val rows = if (settings.numberRowEnabled && controller.currentPage == KeyboardPage.QWERTY) 5 else 4
        (rows * baseRow + rows * 6f + 46f).dp
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.boardBg),
        color = palette.boardBg
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            val oneHanded = settings.oneHandedEnum
            val boardFraction = if (oneHanded == OneHandedMode.OFF) 1f else 0.84f
            val alignment = when (oneHanded) {
                OneHandedMode.LEFT -> Alignment.CenterStart
                OneHandedMode.RIGHT -> Alignment.CenterEnd
                OneHandedMode.OFF -> Alignment.Center
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (oneHanded == OneHandedMode.RIGHT) {
                    OneHandedSideRail(
                        palette = palette,
                        currentMode = oneHanded,
                        onExpandFull = { onToggleOneHanded(OneHandedMode.OFF) },
                        onFlipSide = { onToggleOneHanded(OneHandedMode.LEFT) },
                        modifier = Modifier
                            .weight(0.16f)
                            .height(totalHeightDp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(boardFraction)
                        .align(Alignment.CenterVertically)
                ) {
                    // Top Strip: Utility toolbar + Suggestions (strictly NO Emoji / GIF / Stickers)
                    TopUtilityAndSuggestionBar(
                        controller = controller,
                        palette = palette,
                        settings = settings,
                        personalEntries = personalEntries,
                        onFeedback = ::feedback,
                        onToggleOneHanded = {
                            val next = when (oneHanded) {
                                OneHandedMode.OFF -> OneHandedMode.RIGHT
                                OneHandedMode.RIGHT -> OneHandedMode.LEFT
                                OneHandedMode.LEFT -> OneHandedMode.OFF
                            }
                            onToggleOneHanded(next)
                        },
                        onCycleTheme = onCycleTheme
                    )

                    when (controller.utilityPanel) {
                        UtilityPanel.CLIPBOARD -> {
                            ClipboardPanel(
                                palette = palette,
                                items = clipboardItems,
                                enabled = settings.clipboardHistoryEnabled,
                                height = totalHeightDp - 46.dp,
                                onPaste = { text ->
                                    feedback()
                                    onPasteClipboard(text)
                                    controller.toggleUtilityPanel(UtilityPanel.CLIPBOARD)
                                },
                                onPinToggle = onToggleClipboardPin,
                                onDelete = onDeleteClipboardItem,
                                onClearUnpinned = onClearUnpinnedClipboard
                            )
                        }
                        UtilityPanel.TEXT_EDIT -> {
                            TextEditingPanel(
                                palette = palette,
                                controller = controller,
                                clipboardItems = clipboardItems,
                                height = totalHeightDp - 46.dp,
                                onFeedback = ::feedback,
                                onCopy = {
                                    val selected = controller.getSelectedText()
                                    if (selected.isNotEmpty()) {
                                        onCopySelectionToClipboard(selected)
                                    }
                                },
                                onCut = {
                                    val selected = controller.getSelectedText()
                                    if (selected.isNotEmpty()) {
                                        onCopySelectionToClipboard(selected)
                                        controller.onBackspaceTap(settings, personalEntries)
                                    }
                                },
                                onPaste = {
                                    val latest = clipboardItems.firstOrNull()?.text
                                    if (!latest.isNullOrEmpty()) {
                                        controller.pasteText(latest, settings, personalEntries)
                                    }
                                }
                            )
                        }
                        UtilityPanel.NONE -> {
                            when (controller.currentPage) {
                                KeyboardPage.QWERTY -> {
                                    QwertyKeyboardGrid(
                                        controller = controller,
                                        settings = settings,
                                        personalEntries = personalEntries,
                                        palette = palette,
                                        onFeedback = ::feedback,
                                        onKeyPopup = { label, offset, alts ->
                                            activePopupKey = label
                                            activePopupOffset = offset
                                            longPressOptions = alts
                                        },
                                        onClearPopup = {
                                            activePopupKey = null
                                            longPressOptions = emptyList()
                                        },
                                        onToggleLanguage = onToggleLanguage
                                    )
                                }
                                KeyboardPage.SYMBOLS_1 -> {
                                    SymbolsKeyboardGrid(
                                        pageTwo = false,
                                        controller = controller,
                                        settings = settings,
                                        personalEntries = personalEntries,
                                        palette = palette,
                                        onFeedback = ::feedback
                                    )
                                }
                                KeyboardPage.SYMBOLS_2 -> {
                                    SymbolsKeyboardGrid(
                                        pageTwo = true,
                                        controller = controller,
                                        settings = settings,
                                        personalEntries = personalEntries,
                                        palette = palette,
                                        onFeedback = ::feedback
                                    )
                                }
                                KeyboardPage.NUMPAD -> {
                                    NumpadKeyboardGrid(
                                        controller = controller,
                                        settings = settings,
                                        personalEntries = personalEntries,
                                        palette = palette,
                                        onFeedback = ::feedback
                                    )
                                }
                            }
                        }
                    }
                }

                if (oneHanded == OneHandedMode.LEFT) {
                    OneHandedSideRail(
                        palette = palette,
                        currentMode = oneHanded,
                        onExpandFull = { onToggleOneHanded(OneHandedMode.OFF) },
                        onFlipSide = { onToggleOneHanded(OneHandedMode.RIGHT) },
                        modifier = Modifier
                            .weight(0.16f)
                            .height(totalHeightDp)
                    )
                }
            }

            // Floating Keypress Popup & Long-Press Alternatives
            if (activePopupKey != null && settings.keyPopupEnabled) {
                Box(
                    modifier = Modifier
                        .zIndex(20f)
                        .offset {
                            IntOffset(
                                x = (activePopupOffset.x.roundToInt() - 48).coerceAtLeast(8),
                                y = (activePopupOffset.y.roundToInt() - 120).coerceAtLeast(0)
                            )
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.keyPressedBg)
                        .border(1.dp, palette.accentBg.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (longPressOptions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            longPressOptions.forEach { alt ->
                                Text(
                                    text = alt,
                                    color = palette.keyText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = PlusJakartaSansFamily,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            feedback()
                                            controller.onCharacterTap(alt, settings, personalEntries)
                                            activePopupKey = null
                                            longPressOptions = emptyList()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = activePopupKey.orEmpty(),
                            color = palette.keyText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = PlusJakartaSansFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneHandedSideRail(
    palette: KeyboardPalette,
    currentMode: OneHandedMode,
    onExpandFull: () -> Unit,
    onFlipSide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.stripBg),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onExpandFull,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenWith,
                contentDescription = "Perluas Keyboard Penuh",
                tint = palette.accentBg
            )
        }
        IconButton(
            onClick = onFlipSide,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (currentMode == OneHandedMode.RIGHT) {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = "Pindah Sisi Satu Tangan",
                tint = palette.keyText
            )
        }
    }
}

@Composable
private fun TopUtilityAndSuggestionBar(
    controller: KeyboardController,
    palette: KeyboardPalette,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    onFeedback: () -> Unit,
    onToggleOneHanded: () -> Unit,
    onCycleTheme: () -> Unit
) {
    val hasSuggestions = controller.suggestions.isNotEmpty() && !controller.showToolbarIcons

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(palette.stripBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle between quick utility icons and suggestion chips
        IconButton(
            onClick = {
                onFeedback()
                controller.toggleToolbarMode()
            },
            modifier = Modifier
                .size(40.dp)
                .testTag("ime_toolbar_toggle")
        ) {
            Icon(
                imageVector = if (controller.showToolbarIcons) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.Default.Tune
                },
                contentDescription = "Bilah Alat & Saran",
                tint = palette.accentBg,
                modifier = Modifier.size(20.dp)
            )
        }

        if (controller.showToolbarIcons && controller.suggestions.isEmpty() || controller.showToolbarIcons && controller.utilityPanel != UtilityPanel.NONE) {
            ToolbarIconRow(
                controller = controller,
                palette = palette,
                onFeedback = onFeedback,
                onToggleOneHanded = onToggleOneHanded,
                onCycleTheme = onCycleTheme,
                modifier = Modifier.weight(1f)
            )
        } else if (controller.showToolbarIcons && controller.suggestions.isNotEmpty()) {
            // Hybrid bar: compact utility actions + live suggestions
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChipsRow(
                    suggestions = controller.suggestions,
                    primaryAutoCorrect = controller.primaryAutoCorrect,
                    palette = palette,
                    onTap = { word ->
                        onFeedback()
                        controller.onSuggestionTap(word, settings, personalEntries)
                    },
                    modifier = Modifier.weight(1f)
                )
                UtilityIconButton(
                    icon = Icons.Outlined.ContentPaste,
                    contentDesc = "Papan Klip",
                    active = controller.utilityPanel == UtilityPanel.CLIPBOARD,
                    palette = palette,
                    onClick = {
                        onFeedback()
                        controller.toggleUtilityPanel(UtilityPanel.CLIPBOARD)
                    }
                )
                UtilityIconButton(
                    icon = Icons.Default.OpenWith,
                    contentDesc = "Pengeditan Teks",
                    active = controller.utilityPanel == UtilityPanel.TEXT_EDIT,
                    palette = palette,
                    onClick = {
                        onFeedback()
                        controller.toggleUtilityPanel(UtilityPanel.TEXT_EDIT)
                    }
                )
            }
        } else if (hasSuggestions || controller.suggestions.isNotEmpty()) {
            SuggestionChipsRow(
                suggestions = controller.suggestions,
                primaryAutoCorrect = controller.primaryAutoCorrect,
                palette = palette,
                onTap = { word ->
                    onFeedback()
                    controller.onSuggestionTap(word, settings, personalEntries)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            ToolbarIconRow(
                controller = controller,
                palette = palette,
                onFeedback = onFeedback,
                onToggleOneHanded = onToggleOneHanded,
                onCycleTheme = onCycleTheme,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToolbarIconRow(
    controller: KeyboardController,
    palette: KeyboardPalette,
    onFeedback: () -> Unit,
    onToggleOneHanded: () -> Unit,
    onCycleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityIconButton(
            icon = Icons.Outlined.ContentPaste,
            contentDesc = "Papan Klip",
            active = controller.utilityPanel == UtilityPanel.CLIPBOARD,
            palette = palette,
            onClick = {
                onFeedback()
                controller.toggleUtilityPanel(UtilityPanel.CLIPBOARD)
            }
        )
        UtilityIconButton(
            icon = Icons.Default.OpenWith,
            contentDesc = "Navigasi & Edit Teks",
            active = controller.utilityPanel == UtilityPanel.TEXT_EDIT,
            palette = palette,
            onClick = {
                onFeedback()
                controller.toggleUtilityPanel(UtilityPanel.TEXT_EDIT)
            }
        )
        UtilityIconButton(
            icon = Icons.Default.SpaceBar,
            contentDesc = "Mode Satu Tangan",
            active = false,
            palette = palette,
            onClick = {
                onFeedback()
                onToggleOneHanded()
            }
        )
        UtilityIconButton(
            icon = Icons.Default.Palette,
            contentDesc = "Ganti Tema Keyboard",
            active = false,
            palette = palette,
            onClick = {
                onFeedback()
                onCycleTheme()
            }
        )
        UtilityIconButton(
            icon = Icons.Default.Settings,
            contentDesc = "Buka Pengaturan KetikKu",
            active = false,
            palette = palette,
            onClick = {
                onFeedback()
                controller.onOpenSettings()
            }
        )
    }
}

@Composable
private fun UtilityIconButton(
    icon: ImageVector,
    contentDesc: String,
    active: Boolean,
    palette: KeyboardPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) palette.chipHighlightBg else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (active) palette.accentBg else palette.keyText.copy(alpha = 0.85f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SuggestionChipsRow(
    suggestions: List<String>,
    primaryAutoCorrect: String?,
    palette: KeyboardPalette,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.take(3).forEach { word ->
            val isHighlighted = primaryAutoCorrect != null && word == primaryAutoCorrect
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHighlighted) palette.chipHighlightBg else palette.chipBg)
                    .clickable { onTap(word) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    color = if (isHighlighted) palette.accentBg else palette.keyText,
                    fontSize = 13.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = InterFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QwertyKeyboardGrid(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    onFeedback: () -> Unit,
    onKeyPopup: (String, Offset, List<String>) -> Unit,
    onClearPopup: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val keyHeight = settings.heightEnum.keyHeightDp.dp
    val row1 = remember { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
    val row2 = remember { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
    val row3 = remember { listOf("z", "x", "c", "v", "b", "n", "m") }

    val glidePoints = remember { mutableStateListOf<Offset>() }
    var letterGridSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (settings.numberRowEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..9).map { it.toString() }.plus("0").forEach { digit ->
                    CharKey(
                        label = digit,
                        hint = null,
                        height = (keyHeight.value * 0.84f).dp,
                        palette = palette,
                        bordered = settings.keyBordersEnabled,
                        onTap = {
                            onFeedback()
                            controller.onCharacterTap(digit, settings, personalEntries)
                        },
                        onLongPress = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Letter rows wrapped in a Glide-Typing overlay Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { letterGridSize = it }
                .pointerInput(settings.glideTypingEnabled, settings.language, controller.shiftState) {
                    if (!settings.glideTypingEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            glidePoints.clear()
                            glidePoints.add(start)
                        },
                        onDrag = { change, _ ->
                            glidePoints.add(change.position)
                        },
                        onDragEnd = {
                            if (glidePoints.size >= 4 && letterGridSize.width > 0 && letterGridSize.height > 0) {
                                val normalized = glidePoints.map { pt ->
                                    KeyPoint(
                                        x = (pt.x / letterGridSize.width).coerceIn(0f, 1f),
                                        y = (pt.y / letterGridSize.height).coerceIn(0f, 1f)
                                    )
                                }
                                controller.onGlideGestureComplete(normalized, settings, personalEntries)
                                onFeedback()
                            }
                            glidePoints.clear()
                        },
                        onDragCancel = {
                            glidePoints.clear()
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: q..p
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row1.forEachIndexed { idx, ch ->
                        val display = if (controller.shiftState != ShiftState.OFF) ch.uppercase() else ch
                        val digitHint = if (!settings.numberRowEnabled) ((idx + 1) % 10).toString() else null
                        CharKey(
                            label = display,
                            hint = digitHint,
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            onTap = {
                                onFeedback()
                                controller.onCharacterTap(ch, settings, personalEntries)
                            },
                            onLongPress = {
                                val alts = longPressAlternates[ch].orEmpty()
                                onKeyPopup(display, Offset.Zero, alts)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 2: a..l
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row2.forEach { ch ->
                        val display = if (controller.shiftState != ShiftState.OFF) ch.uppercase() else ch
                        val hint = longPressAlternates[ch]?.firstOrNull()
                        CharKey(
                            label = display,
                            hint = hint,
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            onTap = {
                                onFeedback()
                                controller.onCharacterTap(ch, settings, personalEntries)
                            },
                            onLongPress = {
                                onKeyPopup(display, Offset.Zero, longPressAlternates[ch].orEmpty())
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 3: Shift + z..m + Backspace (with swipe-left delete word!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpecialActionKey(
                        height = keyHeight,
                        palette = palette,
                        bordered = settings.keyBordersEnabled,
                        highlighted = controller.shiftState != ShiftState.OFF,
                        contentDescription = "Shift",
                        onTap = {
                            onFeedback()
                            controller.onShiftTap()
                        },
                        modifier = Modifier.weight(1.45f)
                    ) {
                        Icon(
                            imageVector = if (controller.shiftState == ShiftState.CAPS_LOCK) {
                                Icons.Default.KeyboardCapslock
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = "Shift",
                            tint = if (controller.shiftState != ShiftState.OFF) palette.accentBg else palette.keyText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    row3.forEach { ch ->
                        val display = if (controller.shiftState != ShiftState.OFF) ch.uppercase() else ch
                        val hint = longPressAlternates[ch]?.firstOrNull()
                        CharKey(
                            label = display,
                            hint = hint,
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            onTap = {
                                onFeedback()
                                controller.onCharacterTap(ch, settings, personalEntries)
                            },
                            onLongPress = {
                                onKeyPopup(display, Offset.Zero, longPressAlternates[ch].orEmpty())
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    BackspaceKey(
                        height = keyHeight,
                        palette = palette,
                        bordered = settings.keyBordersEnabled,
                        swipeDeleteEnabled = settings.swipeDeleteEnabled,
                        onTap = {
                            onFeedback()
                            controller.onBackspaceTap(settings, personalEntries)
                        },
                        onSwipeDeleteWord = {
                            onFeedback()
                            controller.onDeleteWordSwipe(settings, personalEntries)
                        },
                        modifier = Modifier.weight(1.45f)
                    )
                }
            }

            // Glide Typing Cyan Trail Canvas
            if (glidePoints.size >= 2) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val path = Path().apply {
                        moveTo(glidePoints.first().x, glidePoints.first().y)
                        for (i in 1 until glidePoints.size) {
                            lineTo(glidePoints[i].x, glidePoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(palette.accentBg.copy(alpha = 0.45f), palette.accentBg)
                        ),
                        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // Row 4: ?123 | Globe/Lang | , | Spacebar (trackpad!) | . | Enter (NO EMOJI KEY!)
        BottomNavigationRow(
            leftModeLabel = "?123",
            onLeftModeTap = {
                onFeedback()
                controller.setPage(KeyboardPage.SYMBOLS_1)
            },
            keyHeight = keyHeight,
            controller = controller,
            settings = settings,
            personalEntries = personalEntries,
            palette = palette,
            onFeedback = onFeedback,
            onToggleLanguage = onToggleLanguage,
            onDotLongPress = {
                onKeyPopup(".", Offset.Zero, longPressAlternates["."].orEmpty())
            }
        )
    }
}

@Composable
private fun SymbolsKeyboardGrid(
    pageTwo: Boolean,
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    onFeedback: () -> Unit
) {
    val keyHeight = settings.heightEnum.keyHeightDp.dp
    val row1 = if (!pageTwo) listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    else listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ")
    val row2 = if (!pageTwo) listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
    else listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
    val row3 = if (!pageTwo) listOf("*", "\"", "'", ":", ";", "!", "?")
    else listOf("%", "©", "®", "™", "✓", "[", "]")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(row1, row2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { sym ->
                    CharKey(
                        label = sym,
                        hint = null,
                        height = keyHeight,
                        palette = palette,
                        bordered = settings.keyBordersEnabled,
                        onTap = {
                            onFeedback()
                            controller.onCharacterTap(sym, settings, personalEntries)
                        },
                        onLongPress = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpecialActionKey(
                height = keyHeight,
                palette = palette,
                bordered = settings.keyBordersEnabled,
                highlighted = pageTwo,
                contentDescription = "Simbol Lainnya",
                onTap = {
                    onFeedback()
                    controller.setPage(if (pageTwo) KeyboardPage.SYMBOLS_1 else KeyboardPage.SYMBOLS_2)
                },
                modifier = Modifier.weight(1.45f)
            ) {
                Text(
                    text = if (pageTwo) "?123" else "=\\<",
                    color = palette.keyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            row3.forEach { sym ->
                CharKey(
                    label = sym,
                    hint = null,
                    height = keyHeight,
                    palette = palette,
                    bordered = settings.keyBordersEnabled,
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(sym, settings, personalEntries)
                    },
                    onLongPress = {},
                    modifier = Modifier.weight(1f)
                )
            }

            BackspaceKey(
                height = keyHeight,
                palette = palette,
                bordered = settings.keyBordersEnabled,
                swipeDeleteEnabled = settings.swipeDeleteEnabled,
                onTap = {
                    onFeedback()
                    controller.onBackspaceTap(settings, personalEntries)
                },
                onSwipeDeleteWord = {
                    onFeedback()
                    controller.onDeleteWordSwipe(settings, personalEntries)
                },
                modifier = Modifier.weight(1.45f)
            )
        }

        BottomNavigationRow(
            leftModeLabel = "ABC",
            onLeftModeTap = {
                onFeedback()
                controller.setPage(KeyboardPage.QWERTY)
            },
            keyHeight = keyHeight,
            controller = controller,
            settings = settings,
            personalEntries = personalEntries,
            palette = palette,
            onFeedback = onFeedback,
            onToggleLanguage = {},
            onDotLongPress = {}
        )
    }
}

@Composable
private fun NumpadKeyboardGrid(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    onFeedback: () -> Unit
) {
    val keyHeight = (settings.heightEnum.keyHeightDp + 4f).dp
    val rows = listOf(
        listOf("1", "2", "3", "-"),
        listOf("4", "5", "6", ","),
        listOf("7", "8", "9", "."),
        listOf("ABC", "0", "⌫", "↵")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cell ->
                    when (cell) {
                        "ABC" -> SpecialActionKey(
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            highlighted = false,
                            contentDescription = "Kembali ke Huruf",
                            onTap = {
                                onFeedback()
                                controller.setPage(KeyboardPage.QWERTY)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ABC", color = palette.keyText, fontWeight = FontWeight.SemiBold)
                        }
                        "⌫" -> BackspaceKey(
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            swipeDeleteEnabled = settings.swipeDeleteEnabled,
                            onTap = {
                                onFeedback()
                                controller.onBackspaceTap(settings, personalEntries)
                            },
                            onSwipeDeleteWord = {
                                onFeedback()
                                controller.onDeleteWordSwipe(settings, personalEntries)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        "↵" -> SpecialActionKey(
                            height = keyHeight,
                            palette = palette,
                            bordered = false,
                            highlighted = true,
                            useAccentFill = true,
                            contentDescription = controller.enterActionType.label,
                            onTap = {
                                onFeedback()
                                controller.onEnterTap(settings, personalEntries)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                contentDescription = "Enter",
                                tint = palette.accentContent
                            )
                        }
                        else -> CharKey(
                            label = cell,
                            hint = null,
                            height = keyHeight,
                            palette = palette,
                            bordered = settings.keyBordersEnabled,
                            onTap = {
                                onFeedback()
                                controller.onCharacterTap(cell, settings, personalEntries)
                            },
                            onLongPress = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationRow(
    leftModeLabel: String,
    onLeftModeTap: () -> Unit,
    keyHeight: Dp,
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    onFeedback: () -> Unit,
    onToggleLanguage: () -> Unit,
    onDotLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpecialActionKey(
            height = keyHeight,
            palette = palette,
            bordered = settings.keyBordersEnabled,
            highlighted = false,
            contentDescription = leftModeLabel,
            onTap = onLeftModeTap,
            modifier = Modifier.weight(1.35f)
        ) {
            Text(
                text = leftModeLabel,
                color = palette.keyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (settings.showLanguageSwitchKey) {
            SpecialActionKey(
                height = keyHeight,
                palette = palette,
                bordered = settings.keyBordersEnabled,
                highlighted = false,
                contentDescription = "Ganti Bahasa Keyboard",
                onTap = {
                    onFeedback()
                    onToggleLanguage()
                    controller.onSwitchLanguageOrIme()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Ganti Bahasa",
                    tint = palette.keyText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        CharKey(
            label = ",",
            hint = null,
            height = keyHeight,
            palette = palette,
            bordered = settings.keyBordersEnabled,
            onTap = {
                onFeedback()
                controller.onCharacterTap(",", settings, personalEntries)
            },
            onLongPress = {},
            modifier = Modifier.weight(0.95f)
        )

        // Spacebar with horizontal Cursor Trackpad
        SpacebarTrackpadKey(
            height = keyHeight,
            label = settings.languageEnum.displayName,
            palette = palette,
            bordered = settings.keyBordersEnabled,
            trackpadEnabled = settings.spacebarTrackpadEnabled,
            onTap = {
                onFeedback()
                controller.onSpaceTap(settings, personalEntries)
            },
            onMoveCursorStep = { step ->
                controller.onSpacebarTrackpadStep(step)
            },
            modifier = Modifier.weight(4.2f)
        )

        CharKey(
            label = ".",
            hint = "?!",
            height = keyHeight,
            palette = palette,
            bordered = settings.keyBordersEnabled,
            onTap = {
                onFeedback()
                controller.onCharacterTap(".", settings, personalEntries)
            },
            onLongPress = onDotLongPress,
            modifier = Modifier.weight(0.95f)
        )

        SpecialActionKey(
            height = keyHeight,
            palette = palette,
            bordered = false,
            highlighted = true,
            useAccentFill = true,
            contentDescription = controller.enterActionType.label,
            onTap = {
                onFeedback()
                controller.onEnterTap(settings, personalEntries)
            },
            modifier = Modifier.weight(1.5f)
        ) {
            if (controller.enterActionType == EnterActionType.ENTER) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = palette.accentContent,
                    modifier = Modifier.size(21.dp)
                )
            } else {
                Text(
                    text = controller.enterActionType.label,
                    color = palette.accentContent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CharKey(
    label: String,
    hint: String?,
    height: Dp,
    palette: KeyboardPalette,
    bordered: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (pressed) palette.keyPressedBg else palette.keyBg)
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .testTag("key_$label"),
        contentAlignment = Alignment.Center
    ) {
        if (!hint.isNullOrEmpty()) {
            Text(
                text = hint,
                color = palette.keyHintText,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
        Text(
            text = label,
            color = palette.keyText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = PlusJakartaSansFamily
        )
    }
}

@Composable
private fun SpecialActionKey(
    height: Dp,
    palette: KeyboardPalette,
    bordered: Boolean,
    highlighted: Boolean,
    useAccentFill: Boolean = false,
    contentDescription: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        useAccentFill -> palette.accentBg
        highlighted -> palette.chipHighlightBg
        else -> palette.specialKeyBg
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (bordered && !useAccentFill) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .clickable(onClick = onTap)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun BackspaceKey(
    height: Dp,
    palette: KeyboardPalette,
    bordered: Boolean,
    swipeDeleteEnabled: Boolean,
    onTap: () -> Unit,
    onSwipeDeleteWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(palette.specialKeyBg)
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .pointerInput(swipeDeleteEnabled) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(swipeDeleteEnabled) {
                if (!swipeDeleteEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                        if (dragAccum <= -36f) {
                            dragAccum = 0f
                            onSwipeDeleteWord()
                        }
                    }
                )
            }
            .semantics { contentDescription = "Hapus" }
            .testTag("key_backspace"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Hapus",
            tint = palette.keyText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SpacebarTrackpadKey(
    height: Dp,
    label: String,
    palette: KeyboardPalette,
    bordered: Boolean,
    trackpadEnabled: Boolean,
    onTap: () -> Unit,
    onMoveCursorStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(palette.keyBg)
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(trackpadEnabled) {
                if (!trackpadEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                        val stepPx = 22f
                        if (dragAccum >= stepPx) {
                            val steps = (dragAccum / stepPx).toInt()
                            dragAccum -= steps * stepPx
                            onMoveCursorStep(steps)
                        } else if (dragAccum <= -stepPx) {
                            val steps = (dragAccum / stepPx).toInt()
                            dragAccum -= steps * stepPx
                            onMoveCursorStep(steps)
                        }
                    }
                )
            }
            .semantics { contentDescription = "Spasi" }
            .testTag("key_spacebar"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = palette.keyHintText,
            fontSize = 11.sp,
            fontFamily = InterFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ClipboardPanel(
    palette: KeyboardPalette,
    items: List<ClipboardItem>,
    enabled: Boolean,
    height: Dp,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearUnpinned: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Papan Klip",
                color = palette.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            if (items.any { !it.pinned }) {
                Text(
                    text = "Bersihkan",
                    color = palette.accentBg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearUnpinned)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (!enabled) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Riwayat papan klip dinonaktifkan di Setelan.",
                    color = palette.keyHintText,
                    fontSize = 13.sp
                )
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Belum ada teks tersalin. Teks yang disalin akan muncul di sini.",
                    color = palette.keyHintText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.keyBg)
                            .clickable { onPaste(item.text) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.text,
                            color = palette.keyText,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onPinToggle(item.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Sematkan",
                                tint = if (item.pinned) palette.accentBg else palette.keyHintText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Hapus klip",
                                tint = palette.keyHintText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEditingPanel(
    palette: KeyboardPalette,
    controller: KeyboardController,
    clipboardItems: List<ClipboardItem>,
    height: Dp,
    onFeedback: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Directional pad
        Column(
            modifier = Modifier.weight(1.3f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditPadButton("Awal", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onMoveEdge(toStart = true)
                }
                EditPadIconButton(Icons.Default.KeyboardArrowUp, "Atas", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onArrowMove(0, -1)
                }
                EditPadButton("Akhir", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onMoveEdge(toStart = false)
                }
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditPadIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Kiri", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onArrowMove(-1, 0)
                }
                EditPadButton(
                    label = if (controller.isSelectionMode) "Pilih ✓" else "Pilih",
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    active = controller.isSelectionMode
                ) {
                    onFeedback(); controller.toggleSelectionMode()
                }
                EditPadIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Kanan", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onArrowMove(1, 0)
                }
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditPadIconButton(Icons.Default.SelectAll, "Pilih Semua", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onSelectAll()
                }
                EditPadIconButton(Icons.Default.KeyboardArrowDown, "Bawah", palette, Modifier.weight(1f)) {
                    onFeedback(); controller.onArrowMove(0, 1)
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // Clipboard Actions column
        Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditActionRow(Icons.Default.ContentCopy, "Salin", palette, Modifier.weight(1f)) {
                onFeedback(); onCopy()
            }
            EditActionRow(Icons.Default.ContentCut, "Potong", palette, Modifier.weight(1f)) {
                onFeedback(); onCut()
            }
            EditActionRow(
                Icons.Default.ContentPaste,
                if (clipboardItems.isNotEmpty()) "Tempel" else "Tempel",
                palette,
                Modifier.weight(1f)
            ) {
                onFeedback(); onPaste()
            }
        }
    }
}

@Composable
private fun EditPadButton(
    label: String,
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.chipHighlightBg else palette.keyBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) palette.accentBg else palette.keyText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EditPadIconButton(
    icon: ImageVector,
    contentDesc: String,
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.keyBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = palette.keyText)
    }
}

@Composable
private fun EditActionRow(
    icon: ImageVector,
    label: String,
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.specialKeyBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = palette.accentBg, modifier = Modifier.size(18.dp))
        Text(text = label, color = palette.keyText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

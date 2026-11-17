package com.example.ime

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.theme.ArialFamily
import com.example.ui.theme.InterFamily
import com.example.ui.theme.PlusJakartaSansFamily
import kotlin.math.abs
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

private fun defaultKeyboardPalette(darkTheme: Boolean): KeyboardPalette = if (darkTheme) {
    KeyboardPalette(
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
} else {
    KeyboardPalette(
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
}

private val longPressAlternates: Map<String, List<String>> = mapOf(
    "q" to listOf("1"),
    "w" to listOf("2"),
    "e" to listOf("3"),
    "r" to listOf("4"),
    "t" to listOf("5"),
    "y" to listOf("6"),
    "u" to listOf("7"),
    "i" to listOf("8"),
    "o" to listOf("9"),
    "p" to listOf("0"),
    "a" to listOf("@"),
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
    "c" to listOf("'"),
    "v" to listOf(":"),
    "b" to listOf(";"),
    "n" to listOf("!"),
    "m" to listOf("?")
)

@Composable
fun KetikKuKeyboardView(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    clipboardItems: List<ClipboardItem>,
    onPasteClipboard: (String) -> Unit,
    onCopySelectionToClipboard: (String) -> Unit,
    onClearAllClipboard: () -> Unit = {},
    onToggleArialItalic: () -> Unit,
    onShowInputMethodPicker: () -> Unit = {},
    applySystemBarInsets: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val palette = remember(isDark) { defaultKeyboardPalette(isDark) }
    val keyFontFamily = if (settings.arialItalicEnabled) ArialFamily else PlusJakartaSansFamily
    val chipFontFamily = if (settings.arialItalicEnabled) ArialFamily else InterFamily
    val keyFontStyle = if (settings.arialItalicEnabled) FontStyle.Italic else FontStyle.Normal
    val view = LocalView.current

    fun feedback() {
        if (settings.hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val handleToggleArialItalic: () -> Unit = {
        controller.convertSelectionToArialItalic(!settings.arialItalicEnabled)
        onToggleArialItalic()
    }

    val density = LocalDensity.current
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var activePopupKey by remember { mutableStateOf<String?>(null) }
    var activePopupKeyBounds by remember { mutableStateOf<Rect?>(null) }
    var longPressOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAltIndex by remember { mutableIntStateOf(0) }
    var longPressAnchorX by remember { mutableFloatStateOf(0f) }

    val pillWidthDp = 42.dp
    val pillHeightDp = 38.dp
    val pillGapDp = 4.dp
    val popupPadHDp = 6.dp
    val popupPadVDp = 4.dp

    val pillWidthPx = with(density) { pillWidthDp.toPx() }
    val pillGapPx = with(density) { pillGapDp.toPx() }
    val popupPadHPx = with(density) { popupPadHDp.toPx() }
    val popupHeightPx = with(density) { (pillHeightDp + popupPadVDp * 2).toPx() }
    val gapAboveKeyPx = with(density) { 6.dp.toPx() }
    val sideMarginPx = with(density) { 6.dp.toPx() }
    val dragThresholdPx = with(density) { 8.dp.toPx() }

    val optionCount = longPressOptions.size.coerceAtLeast(1)
    val popupWidthPx = 2f * popupPadHPx + optionCount * pillWidthPx + (optionCount - 1) * pillGapPx
    val containerW = (containerCoords?.size?.width ?: 0).toFloat()
    val popupX = run {
        val bounds = activePopupKeyBounds
        if (bounds != null && containerW > 0f) {
            val desiredX = if (optionCount == 1) {
                bounds.center.x - popupWidthPx / 2f
            } else {
                bounds.center.x - popupPadHPx - pillWidthPx / 2f
            }
            desiredX.coerceIn(
                sideMarginPx,
                (containerW - popupWidthPx - sideMarginPx).coerceAtLeast(sideMarginPx)
            )
        } else {
            sideMarginPx
        }
    }
    val popupY = run {
        val bounds = activePopupKeyBounds
        if (bounds != null) {
            (bounds.top - popupHeightPx - gapAboveKeyPx).coerceAtLeast(with(density) { 2.dp.toPx() })
        } else {
            with(density) { 2.dp.toPx() }
        }
    }

    val totalHeightDp = remember(settings.heightPreset, controller.currentPage) {
        val baseRow = settings.heightEnum.keyHeightDp
        val rows = if (controller.currentPage == KeyboardPage.QWERTY) 5 else 4
        (rows * baseRow + rows * 6f + 46f).dp
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.boardBg
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applySystemBarInsets) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(bottom = 8.dp)
                .onGloballyPositioned { containerCoords = it }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Strip: Utility toolbar + Suggestions (strictly NO Emoji / GIF / Stickers)
                TopUtilityAndSuggestionBar(
                    controller = controller,
                    palette = palette,
                    settings = settings,
                    personalEntries = personalEntries,
                    chipFontFamily = chipFontFamily,
                    keyFontStyle = keyFontStyle,
                    onFeedback = ::feedback,
                    onToggleArialItalic = handleToggleArialItalic
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
                            onClearAll = {
                                feedback()
                                onClearAllClipboard()
                            }
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
                                    keyFontFamily = keyFontFamily,
                                    keyFontStyle = keyFontStyle,
                                    onFeedback = ::feedback,
                                    onShowInputMethodPicker = onShowInputMethodPicker,
                                    onKeyPopupStart = { label, keyCoords, downLocalOffset, alts ->
                                        if (alts.isEmpty()) return@QwertyKeyboardGrid
                                        val cCoords = containerCoords
                                        val bounds = if (
                                            cCoords != null && keyCoords != null &&
                                            cCoords.isAttached && keyCoords.isAttached
                                        ) {
                                            val topLeft = cCoords.localPositionOf(keyCoords, Offset.Zero)
                                            Rect(
                                                left = topLeft.x,
                                                top = topLeft.y,
                                                right = topLeft.x + keyCoords.size.width.toFloat(),
                                                bottom = topLeft.y + keyCoords.size.height.toFloat()
                                            )
                                        } else null
                                        activePopupKey = label
                                        activePopupKeyBounds = bounds
                                        longPressOptions = alts
                                        selectedAltIndex = 0
                                        longPressAnchorX = (bounds?.left ?: 0f) + downLocalOffset.x
                                    },
                                    onKeyPopupDrag = { localPos ->
                                        val bounds = activePopupKeyBounds
                                        if (bounds != null && longPressOptions.size > 1) {
                                            val pointerX = bounds.left + localPos.x
                                            if (abs(pointerX - longPressAnchorX) <= dragThresholdPx) {
                                                selectedAltIndex = 0
                                            } else {
                                                var bestIdx = 0
                                                var bestDist = Float.MAX_VALUE
                                                for (i in longPressOptions.indices) {
                                                    val pillCenterX = popupX + popupPadHPx +
                                                        i * (pillWidthPx + pillGapPx) + pillWidthPx / 2f
                                                    val dist = abs(pointerX - pillCenterX)
                                                    if (dist < bestDist) {
                                                        bestDist = dist
                                                        bestIdx = i
                                                    }
                                                }
                                                selectedAltIndex = bestIdx
                                            }
                                        }
                                    },
                                    onKeyPopupRelease = {
                                        val chosen = longPressOptions.getOrNull(selectedAltIndex)
                                        if (chosen != null) {
                                            feedback()
                                            controller.onCharacterTap(chosen, settings, personalEntries)
                                        }
                                        activePopupKey = null
                                        activePopupKeyBounds = null
                                        longPressOptions = emptyList()
                                        selectedAltIndex = 0
                                    },
                                    onClearPopup = {
                                        activePopupKey = null
                                        activePopupKeyBounds = null
                                        longPressOptions = emptyList()
                                        selectedAltIndex = 0
                                    }
                                )
                            }
                            KeyboardPage.SYMBOLS_1 -> {
                                SymbolsKeyboardGrid(
                                    pageTwo = false,
                                    controller = controller,
                                    settings = settings,
                                    personalEntries = personalEntries,
                                    palette = palette,
                                    keyFontFamily = keyFontFamily,
                                    keyFontStyle = keyFontStyle,
                                    onFeedback = ::feedback,
                                    onShowInputMethodPicker = onShowInputMethodPicker
                                )
                            }
                            KeyboardPage.SYMBOLS_2 -> {
                                SymbolsKeyboardGrid(
                                    pageTwo = true,
                                    controller = controller,
                                    settings = settings,
                                    personalEntries = personalEntries,
                                    palette = palette,
                                    keyFontFamily = keyFontFamily,
                                    keyFontStyle = keyFontStyle,
                                    onFeedback = ::feedback,
                                    onShowInputMethodPicker = onShowInputMethodPicker
                                )
                            }
                            KeyboardPage.NUMPAD -> {
                                NumpadKeyboardGrid(
                                    controller = controller,
                                    settings = settings,
                                    personalEntries = personalEntries,
                                    palette = palette,
                                    keyFontFamily = keyFontFamily,
                                    keyFontStyle = keyFontStyle,
                                    onFeedback = ::feedback
                                )
                            }
                        }
                    }
                }
            }

            // Floating Long-Press Symbol Shortcut Popup anchored right above the pressed key
            if (activePopupKey != null && longPressOptions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .zIndex(20f)
                        .testTag("key_longpress_popup")
                        .offset {
                            IntOffset(
                                x = popupX.roundToInt(),
                                y = popupY.roundToInt()
                            )
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = palette.keyPressedBg,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, palette.accentBg.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = popupPadHDp, vertical = popupPadVDp),
                        horizontalArrangement = Arrangement.spacedBy(pillGapDp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        longPressOptions.forEachIndexed { idx, alt ->
                            val isSelected = idx == selectedAltIndex
                            Box(
                                modifier = Modifier
                                    .size(width = pillWidthDp, height = pillHeightDp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) palette.accentBg else Color.Transparent)
                                    .clickable {
                                        feedback()
                                        controller.onCharacterTap(alt, settings, personalEntries)
                                        activePopupKey = null
                                        activePopupKeyBounds = null
                                        longPressOptions = emptyList()
                                        selectedAltIndex = 0
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = alt,
                                    color = if (isSelected) palette.accentContent else palette.keyText,
                                    fontSize = 18.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontFamily = keyFontFamily,
                                    fontStyle = keyFontStyle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopUtilityAndSuggestionBar(
    controller: KeyboardController,
    palette: KeyboardPalette,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    chipFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    onFeedback: () -> Unit,
    onToggleArialItalic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(palette.stripBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (controller.suggestions.isEmpty() || controller.utilityPanel != UtilityPanel.NONE) {
            ToolbarIconRow(
                controller = controller,
                palette = palette,
                arialItalicEnabled = settings.arialItalicEnabled,
                onFeedback = onFeedback,
                onToggleArialItalic = onToggleArialItalic,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SuggestionChipsRow(
                    suggestions = controller.suggestions,
                    primaryAutoCorrect = controller.primaryAutoCorrect,
                    palette = palette,
                    fontFamily = chipFontFamily,
                    fontStyle = keyFontStyle,
                    onTap = { word ->
                        onFeedback()
                        controller.onSuggestionTap(word, settings, personalEntries)
                    },
                    modifier = Modifier.weight(1f)
                )
                UtilityIconButton(
                    icon = Icons.Default.FormatItalic,
                    contentDesc = "Pintasan Font Arial Italic",
                    active = settings.arialItalicEnabled,
                    palette = palette,
                    modifier = Modifier.testTag("ime_arial_italic_shortcut"),
                    onClick = {
                        onFeedback()
                        onToggleArialItalic()
                    }
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
        }
    }
}

@Composable
private fun ToolbarIconRow(
    controller: KeyboardController,
    palette: KeyboardPalette,
    arialItalicEnabled: Boolean,
    onFeedback: () -> Unit,
    onToggleArialItalic: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityIconButton(
            icon = Icons.Default.FormatItalic,
            contentDesc = "Pintasan Font Arial Italic",
            active = arialItalicEnabled,
            palette = palette,
            modifier = Modifier.testTag("ime_arial_italic_shortcut"),
            onClick = {
                onFeedback()
                onToggleArialItalic()
            }
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
            contentDesc = "Navigasi & Edit Teks",
            active = controller.utilityPanel == UtilityPanel.TEXT_EDIT,
            palette = palette,
            onClick = {
                onFeedback()
                controller.toggleUtilityPanel(UtilityPanel.TEXT_EDIT)
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
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
    fontFamily: FontFamily,
    fontStyle: FontStyle,
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
                    fontFamily = fontFamily,
                    fontStyle = fontStyle,
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
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    onFeedback: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onKeyPopupStart: (String, LayoutCoordinates?, Offset, List<String>) -> Unit,
    onKeyPopupDrag: (Offset) -> Unit,
    onKeyPopupRelease: () -> Unit,
    onClearPopup: () -> Unit
) {
    val keyHeight = settings.heightEnum.keyHeightDp.dp
    val row1 = remember { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
    val row2 = remember { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
    val row3 = remember { listOf("z", "x", "c", "v", "b", "n", "m") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Default permanent number row (1..0)
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
                    bordered = true,
                    fontFamily = keyFontFamily,
                    fontStyle = keyFontStyle,
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(digit, settings, personalEntries)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 1: q..p
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row1.forEach { ch ->
                val display = if (controller.shiftState != ShiftState.OFF) ch.uppercase() else ch
                val alts = longPressAlternates[ch].orEmpty()
                CharKey(
                    label = display,
                    hint = null,
                    height = keyHeight,
                    palette = palette,
                    bordered = true,
                    fontFamily = keyFontFamily,
                    fontStyle = keyFontStyle,
                    hasLongPress = alts.isNotEmpty(),
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(ch, settings, personalEntries)
                    },
                    onLongPressStart = { coords, downOffset ->
                        onKeyPopupStart(display, coords, downOffset, alts)
                    },
                    onLongPressDrag = onKeyPopupDrag,
                    onLongPressRelease = onKeyPopupRelease,
                    onLongPressCancel = onClearPopup,
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
                val alts = longPressAlternates[ch].orEmpty()
                val hint = alts.firstOrNull()
                CharKey(
                    label = display,
                    hint = hint,
                    height = keyHeight,
                    palette = palette,
                    bordered = true,
                    fontFamily = keyFontFamily,
                    fontStyle = keyFontStyle,
                    hasLongPress = alts.isNotEmpty(),
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(ch, settings, personalEntries)
                    },
                    onLongPressStart = { coords, downOffset ->
                        onKeyPopupStart(display, coords, downOffset, alts)
                    },
                    onLongPressDrag = onKeyPopupDrag,
                    onLongPressRelease = onKeyPopupRelease,
                    onLongPressCancel = onClearPopup,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 3: Shift + z..m + Backspace (with swipe-left delete word)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialActionKey(
                height = keyHeight,
                palette = palette,
                bordered = true,
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
                val alts = longPressAlternates[ch].orEmpty()
                val hint = alts.firstOrNull()
                CharKey(
                    label = display,
                    hint = hint,
                    height = keyHeight,
                    palette = palette,
                    bordered = true,
                    fontFamily = keyFontFamily,
                    fontStyle = keyFontStyle,
                    hasLongPress = alts.isNotEmpty(),
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(ch, settings, personalEntries)
                    },
                    onLongPressStart = { coords, downOffset ->
                        onKeyPopupStart(display, coords, downOffset, alts)
                    },
                    onLongPressDrag = onKeyPopupDrag,
                    onLongPressRelease = onKeyPopupRelease,
                    onLongPressCancel = onClearPopup,
                    modifier = Modifier.weight(1f)
                )
            }

            BackspaceKey(
                height = keyHeight,
                palette = palette,
                bordered = true,
                onTap = {
                    onFeedback()
                    controller.onBackspaceReleaseOrTap(settings, personalEntries)
                },
                onSelectStep = { delta ->
                    controller.onBackspaceSelectStep(delta)
                },
                onCancelSelection = {
                    controller.cancelBackspaceSelection()
                },
                onReleaseDelete = {
                    onFeedback()
                    controller.onBackspaceReleaseOrTap(settings, personalEntries)
                },
                modifier = Modifier.weight(1.45f)
            )
        }

        // Row 4: ?123 | , | Spacebar (trackpad + tahan untuk pilih keyboard) | . | Enter
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
            keyFontFamily = keyFontFamily,
            keyFontStyle = keyFontStyle,
            onFeedback = onFeedback,
            onShowInputMethodPicker = onShowInputMethodPicker
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
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    onFeedback: () -> Unit,
    onShowInputMethodPicker: () -> Unit
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
                        bordered = true,
                        fontFamily = keyFontFamily,
                        fontStyle = keyFontStyle,
                        onTap = {
                            onFeedback()
                            controller.onCharacterTap(sym, settings, personalEntries)
                        },
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
                bordered = true,
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
                    bordered = true,
                    fontFamily = keyFontFamily,
                    fontStyle = keyFontStyle,
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(sym, settings, personalEntries)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            BackspaceKey(
                height = keyHeight,
                palette = palette,
                bordered = true,
                onTap = {
                    onFeedback()
                    controller.onBackspaceReleaseOrTap(settings, personalEntries)
                },
                onSelectStep = { delta ->
                    controller.onBackspaceSelectStep(delta)
                },
                onCancelSelection = {
                    controller.cancelBackspaceSelection()
                },
                onReleaseDelete = {
                    onFeedback()
                    controller.onBackspaceReleaseOrTap(settings, personalEntries)
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
            keyFontFamily = keyFontFamily,
            keyFontStyle = keyFontStyle,
            onFeedback = onFeedback,
            onShowInputMethodPicker = onShowInputMethodPicker
        )
    }
}

@Composable
private fun NumpadKeyboardGrid(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
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
                            bordered = true,
                            highlighted = false,
                            contentDescription = "Kembali ke Huruf",
                            onTap = {
                                onFeedback()
                                controller.setPage(KeyboardPage.QWERTY)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ABC",
                                color = palette.keyText,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = keyFontFamily,
                                fontStyle = keyFontStyle
                            )
                        }
                        "⌫" -> BackspaceKey(
                            height = keyHeight,
                            palette = palette,
                            bordered = true,
                            onTap = {
                                onFeedback()
                                controller.onBackspaceReleaseOrTap(settings, personalEntries)
                            },
                            onSelectStep = { delta ->
                                controller.onBackspaceSelectStep(delta)
                            },
                            onCancelSelection = {
                                controller.cancelBackspaceSelection()
                            },
                            onReleaseDelete = {
                                onFeedback()
                                controller.onBackspaceReleaseOrTap(settings, personalEntries)
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
                            bordered = true,
                            fontFamily = keyFontFamily,
                            fontStyle = keyFontStyle,
                            onTap = {
                                onFeedback()
                                controller.onCharacterTap(cell, settings, personalEntries)
                            },
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
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    onFeedback: () -> Unit,
    onShowInputMethodPicker: () -> Unit
) {
    val spaceLabel = if (settings.arialItalicEnabled) {
        "${settings.languageEnum.displayName} • Arial Italic"
    } else {
        settings.languageEnum.displayName
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpecialActionKey(
            height = keyHeight,
            palette = palette,
            bordered = true,
            highlighted = false,
            contentDescription = leftModeLabel,
            onTap = onLeftModeTap,
            modifier = Modifier.weight(1.45f)
        ) {
            Text(
                text = leftModeLabel,
                color = palette.keyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = keyFontFamily,
                fontStyle = keyFontStyle
            )
        }

        CharKey(
            label = ",",
            hint = null,
            height = keyHeight,
            palette = palette,
            bordered = true,
            fontFamily = keyFontFamily,
            fontStyle = keyFontStyle,
            onTap = {
                onFeedback()
                controller.onCharacterTap(",", settings, personalEntries)
            },
            modifier = Modifier.weight(1.0f)
        )

        // Spacebar with horizontal Cursor Trackpad + long-press Input Method Picker
        SpacebarTrackpadKey(
            height = keyHeight,
            label = spaceLabel,
            palette = palette,
            bordered = true,
            arialItalicEnabled = settings.arialItalicEnabled,
            fontFamily = keyFontFamily,
            fontStyle = keyFontStyle,
            onTap = {
                onFeedback()
                controller.onSpaceTap(settings, personalEntries)
            },
            onLongPress = {
                onFeedback()
                onShowInputMethodPicker()
            },
            onMoveCursorStep = { step ->
                controller.onSpacebarTrackpadStep(step)
            },
            modifier = Modifier.weight(4.8f)
        )

        CharKey(
            label = ".",
            hint = null,
            height = keyHeight,
            palette = palette,
            bordered = true,
            fontFamily = keyFontFamily,
            fontStyle = keyFontStyle,
            onTap = {
                onFeedback()
                controller.onCharacterTap(".", settings, personalEntries)
            },
            modifier = Modifier.weight(1.0f)
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
            modifier = Modifier.weight(1.6f)
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
    fontFamily: FontFamily = PlusJakartaSansFamily,
    fontStyle: FontStyle = FontStyle.Normal,
    hasLongPress: Boolean = false,
    onTap: () -> Unit,
    onLongPressStart: (LayoutCoordinates?, Offset) -> Unit = { _, _ -> },
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressRelease: () -> Unit = {},
    onLongPressCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var keyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentHasLongPress by rememberUpdatedState(hasLongPress)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val currentOnLongPressRelease by rememberUpdatedState(onLongPressRelease)
    val currentOnLongPressCancel by rememberUpdatedState(onLongPressCancel)

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (pressed) palette.keyPressedBg else palette.keyBg)
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .onGloballyPositioned { keyCoords = it }
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val touchSlop = viewConfiguration.touchSlop
                    var movedBeforeLongPress = false

                    val earlyOutcome = withTimeoutOrNull(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (!change.pressed) {
                                return@withTimeoutOrNull if (!movedBeforeLongPress) "TAP" else "CANCEL"
                            }
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                movedBeforeLongPress = true
                                return@withTimeoutOrNull "MOVED"
                            }
                        }
                        "CANCEL"
                    }

                    if (earlyOutcome == "TAP") {
                        pressed = false
                        currentOnTap()
                        return@awaitEachGesture
                    }
                    if (earlyOutcome == "MOVED" || earlyOutcome == "CANCEL") {
                        pressed = false
                        return@awaitEachGesture
                    }

                    // Long-press threshold reached without dragging away
                    if (!currentHasLongPress) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                        pressed = false
                        return@awaitEachGesture
                    }

                    var completed = false
                    currentOnLongPressStart(keyCoords, down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                currentOnLongPressRelease()
                                break
                            }
                            change.consume()
                            currentOnLongPressDrag(change.position)
                        }
                    } finally {
                        pressed = false
                        if (!completed) {
                            currentOnLongPressCancel()
                        }
                    }
                }
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
            fontFamily = fontFamily,
            fontStyle = fontStyle
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
    onTap: () -> Unit,
    onSelectStep: (Int) -> Unit,
    onCancelSelection: () -> Unit,
    onReleaseDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSelecting by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (isSelecting) palette.accentBg.copy(alpha = 0.28f) else palette.specialKeyBg)
            .then(if (bordered) Modifier.border(0.75.dp, if (isSelecting) palette.accentBg else palette.borderColor, shape) else Modifier)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isSelecting = false
                    var dragged = false
                    var lastDragX = down.position.x

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (isSelecting) {
                                    onReleaseDelete()
                                } else if (!dragged) {
                                    onTap()
                                }
                                break
                            }
                            val totalDeltaX = change.position.x - down.position.x
                            val stepDelta = change.position.x - lastDragX

                            if (abs(totalDeltaX) > 8f) {
                                dragged = true
                            }

                            if (totalDeltaX < -18f) {
                                if (!isSelecting) {
                                    isSelecting = true
                                }
                                if (abs(stepDelta) >= 14f) {
                                    onSelectStep(-1)
                                    lastDragX = change.position.x
                                }
                                change.consume()
                            } else if (totalDeltaX > 18f && isSelecting) {
                                isSelecting = false
                                onCancelSelection()
                                lastDragX = change.position.x
                                change.consume()
                            }
                        }
                    } finally {
                        if (isSelecting) {
                            onReleaseDelete()
                        }
                        isSelecting = false
                    }
                }
            }
            .semantics { contentDescription = "Hapus" }
            .testTag("key_backspace"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Hapus",
            tint = if (isSelecting) palette.accentBg else palette.keyText,
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
    arialItalicEnabled: Boolean = false,
    fontFamily: FontFamily = InterFamily,
    fontStyle: FontStyle = FontStyle.Normal,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
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
            .then(
                if (arialItalicEnabled) {
                    Modifier.border(1.dp, palette.accentBg.copy(alpha = 0.65f), shape)
                } else if (bordered) {
                    Modifier.border(0.75.dp, palette.borderColor, shape)
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
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
            color = if (arialItalicEnabled) palette.accentBg else palette.keyHintText,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            fontStyle = fontStyle,
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
    onClearAll: () -> Unit
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
            if (items.isNotEmpty()) {
                Text(
                    text = "Bersihkan semua",
                    color = palette.accentBg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("clipboard_clear_all")
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("clipboard_grid")
            ) {
                items(items, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.keyBg)
                            .border(0.75.dp, palette.borderColor, RoundedCornerShape(10.dp))
                            .clickable { onPaste(item.text) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = item.text,
                            color = palette.keyText,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
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

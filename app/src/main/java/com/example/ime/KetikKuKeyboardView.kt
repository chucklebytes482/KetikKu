package com.example.ime

import android.graphics.Rect as AndroidRect
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.theme.InterFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.data.TotpGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private class CoordsRef {
    var coords: LayoutCoordinates? = null
}

private class KeySlot(
    val id: String,
    var onTap: () -> Unit,
    var hasLongPress: Boolean = false,
    var isBackspaceRepeat: Boolean = false,
    var isTapReorderFlushable: Boolean = false,
    var onFeedback: () -> Unit = {},
    var onLongPressStart: (LayoutCoordinates?, Offset) -> Unit = { _, _ -> },
    var onLongPressDrag: (Offset) -> Unit = {},
    var onLongPressRelease: () -> Unit = {},
    var onLongPressCancel: () -> Unit = {},
    var coords: LayoutCoordinates? = null
)

/**
 * Multi-touch pointer tracker modeled after Julow/Unexpected-Keyboard's Pointers.java:
 * locks each finger's PointerId to its down key independently, preserves two-thumb
 * rollover in FIFO DOWN order without cancelling prior pressed keys, runs no per-key
 * pointer coroutine overhead on regular taps, and isolates visual highlights to draw-phase reads.
 */
private class KeyPointersTracker(
    private val scope: CoroutineScope,
    private val cancelMoveRadiusPx: Float,
    private val gapSnapSlopPx: Float
) {
    private class ActivePointer(
        val id: PointerId,
        val slot: KeySlot,
        val downPosInGrid: Offset,
        var job: Job? = null,
        var popupActive: Boolean = false,
        var repeated: Boolean = false,
        var committed: Boolean = false,
        var cancelled: Boolean = false
    )

    var gridCoords: LayoutCoordinates? = null
    private val slots = LinkedHashMap<String, KeySlot>(48)
    private val activePointers = LinkedHashMap<PointerId, ActivePointer>(8)
    val pressedCounts = mutableStateMapOf<String, Int>()

    fun registerSlot(slot: KeySlot) {
        slots[slot.id] = slot
    }

    fun unregisterSlot(slotId: String) {
        slots.remove(slotId)
        val iter = activePointers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val ap = entry.value
            if (ap.slot.id == slotId) {
                ap.job?.cancel()
                if (ap.popupActive) {
                    ap.slot.onLongPressCancel()
                }
                iter.remove()
            }
        }
        pressedCounts.remove(slotId)
    }

    fun hasPointer(id: PointerId): Boolean = activePointers.containsKey(id)

    fun findSlotAt(posInGrid: Offset, gCoords: LayoutCoordinates): KeySlot? {
        if (!gCoords.isAttached) return null
        var bestFallback: KeySlot? = null
        var bestFallbackDistSq = Float.MAX_VALUE
        val maxSnapDistSq = gapSnapSlopPx * gapSnapSlopPx
        for (slot in slots.values) {
            val kCoords = slot.coords ?: continue
            if (!kCoords.isAttached) continue
            val topLeft = try {
                gCoords.localPositionOf(kCoords, Offset.Zero)
            } catch (_: IllegalArgumentException) {
                continue
            }
            val right = topLeft.x + kCoords.size.width.toFloat()
            val bottom = topLeft.y + kCoords.size.height.toFloat()
            if (posInGrid.x >= topLeft.x && posInGrid.x <= right &&
                posInGrid.y >= topLeft.y && posInGrid.y <= bottom
            ) {
                return slot
            }
            val clampedX = posInGrid.x.coerceIn(topLeft.x, right)
            val clampedY = posInGrid.y.coerceIn(topLeft.y, bottom)
            val dx = posInGrid.x - clampedX
            val dy = posInGrid.y - clampedY
            val distSq = dx * dx + dy * dy
            if (distSq <= maxSnapDistSq && distSq < bestFallbackDistSq) {
                bestFallbackDistSq = distSq
                bestFallback = slot
            }
        }
        return bestFallback
    }

    private fun slotLocalOffset(slot: KeySlot, posInGrid: Offset): Offset {
        val gCoords = gridCoords
        val kCoords = slot.coords
        if (gCoords != null && kCoords != null && gCoords.isAttached && kCoords.isAttached) {
            val topLeft = try {
                gCoords.localPositionOf(kCoords, Offset.Zero)
            } catch (_: IllegalArgumentException) {
                Offset.Zero
            }
            return posInGrid - topLeft
        }
        return Offset.Zero
    }

    private fun incrementPressed(slotId: String) {
        pressedCounts[slotId] = (pressedCounts[slotId] ?: 0) + 1
    }

    private fun decrementPressed(slotId: String) {
        val cur = pressedCounts[slotId] ?: 0
        if (cur <= 1) {
            pressedCounts.remove(slotId)
        } else {
            pressedCounts[slotId] = cur - 1
        }
    }

    fun handleDown(id: PointerId, posInGrid: Offset, slot: KeySlot, longPressTimeoutMs: Long) {
        if (activePointers.containsKey(id)) {
            cancelPointer(id)
        }
        val ap = ActivePointer(id = id, slot = slot, downPosInGrid = posInGrid)
        activePointers[id] = ap
        incrementPressed(slot.id)

        if (slot.isBackspaceRepeat) {
            ap.job = scope.launch {
                delay(380L)
                if (!activePointers.containsKey(id) || ap.cancelled) return@launch
                ap.repeated = true
                while (activePointers.containsKey(id) && !ap.cancelled) {
                    ap.slot.onFeedback()
                    ap.slot.onTap()
                    delay(60L)
                }
            }
        } else if (slot.hasLongPress) {
            ap.job = scope.launch {
                delay(longPressTimeoutMs)
                if (!activePointers.containsKey(id) || ap.cancelled) return@launch
                ap.popupActive = true
                val localDown = slotLocalOffset(ap.slot, ap.downPosInGrid)
                ap.slot.onLongPressStart(ap.slot.coords, localDown)
            }
        }
    }

    fun handleMove(id: PointerId, posInGrid: Offset) {
        val ap = activePointers[id] ?: return
        if (ap.cancelled) return
        if (ap.popupActive) {
            ap.slot.onLongPressDrag(slotLocalOffset(ap.slot, posInGrid))
            return
        }
        if ((posInGrid - ap.downPosInGrid).getDistance() > cancelMoveRadiusPx) {
            ap.cancelled = true
            ap.job?.cancel()
            decrementPressed(ap.slot.id)
        }
    }

    fun handleUp(id: PointerId) {
        val target = activePointers[id]
        var toFlush: ArrayList<ActivePointer>? = null
        if (activePointers.size > 1 && target != null && !target.cancelled && !target.popupActive && !target.committed) {
            for (ap in activePointers.values) {
                if (ap.id == id) break
                if (ap.slot.isTapReorderFlushable && !ap.committed && !ap.cancelled && !ap.popupActive) {
                    val list = toFlush ?: ArrayList<ActivePointer>(2).also { toFlush = it }
                    list.add(ap)
                }
            }
        }
        if (toFlush != null) {
            for (earlier in toFlush!!) {
                earlier.committed = true
                earlier.job?.cancel()
                earlier.slot.onTap()
            }
        }

        val ap = activePointers.remove(id) ?: return
        ap.job?.cancel()
        if (!ap.cancelled) {
            decrementPressed(ap.slot.id)
            if (!ap.committed) {
                when {
                    ap.popupActive -> ap.slot.onLongPressRelease()
                    ap.slot.isBackspaceRepeat -> {
                        if (!ap.repeated) {
                            ap.slot.onFeedback()
                            ap.slot.onTap()
                        }
                    }
                    else -> ap.slot.onTap()
                }
            }
        }
    }

    fun cancelPointer(id: PointerId) {
        val ap = activePointers.remove(id) ?: return
        ap.job?.cancel()
        if (!ap.cancelled) {
            decrementPressed(ap.slot.id)
            if (ap.popupActive) {
                ap.slot.onLongPressCancel()
            }
        }
    }

    fun cancelAll() {
        val keys = activePointers.keys.toList()
        for (id in keys) {
            cancelPointer(id)
        }
    }
}

@Composable
private fun rememberKeyPointersTracker(): KeyPointersTracker {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cancelMoveRadiusPx = with(density) { 72.dp.toPx() }
    val gapSnapSlopPx = with(density) { 6.dp.toPx() }
    val tracker = remember(scope, cancelMoveRadiusPx, gapSnapSlopPx) {
        KeyPointersTracker(
            scope = scope,
            cancelMoveRadiusPx = cancelMoveRadiusPx,
            gapSnapSlopPx = gapSnapSlopPx
        )
    }
    DisposableEffect(tracker) {
        onDispose { tracker.cancelAll() }
    }
    return tracker
}

private fun Modifier.keyPointersSurface(tracker: KeyPointersTracker): Modifier =
    this
        .onGloballyPositioned { tracker.gridCoords = it }
        .pointerInput(tracker) {
            awaitPointerEventScope {
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val gCoords = tracker.gridCoords ?: continue
                        if (!gCoords.isAttached) continue
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        for (change in event.changes) {
                            if (change.changedToDown()) {
                                val hitSlot = tracker.findSlotAt(change.position, gCoords)
                                if (hitSlot != null) {
                                    change.consume()
                                    tracker.handleDown(change.id, change.position, hitSlot, longPressMs)
                                }
                            } else if (change.changedToUpIgnoreConsumed()) {
                                if (tracker.hasPointer(change.id)) {
                                    change.consume()
                                    tracker.handleUp(change.id)
                                }
                            } else if (change.pressed && tracker.hasPointer(change.id)) {
                                change.consume()
                                tracker.handleMove(change.id, change.position)
                            }
                        }
                    }
                } finally {
                    tracker.cancelAll()
                }
            }
        }

@Composable
private fun rememberRegisteredKeySlot(
    tracker: KeyPointersTracker?,
    slotId: String,
    onTap: () -> Unit,
    hasLongPress: Boolean = false,
    isBackspaceRepeat: Boolean = false,
    isTapReorderFlushable: Boolean = false,
    onFeedback: () -> Unit = {},
    onLongPressStart: (LayoutCoordinates?, Offset) -> Unit = { _, _ -> },
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressRelease: () -> Unit = {},
    onLongPressCancel: () -> Unit = {}
): KeySlot? {
    if (tracker == null) return null
    val slot = remember(tracker, slotId) { KeySlot(id = slotId, onTap = onTap) }
    SideEffect {
        slot.onTap = onTap
        slot.hasLongPress = hasLongPress
        slot.isBackspaceRepeat = isBackspaceRepeat
        slot.isTapReorderFlushable = isTapReorderFlushable
        slot.onFeedback = onFeedback
        slot.onLongPressStart = onLongPressStart
        slot.onLongPressDrag = onLongPressDrag
        slot.onLongPressRelease = onLongPressRelease
        slot.onLongPressCancel = onLongPressCancel
    }
    DisposableEffect(tracker, slotId) {
        tracker.registerSlot(slot)
        onDispose {
            tracker.unregisterSlot(slotId)
        }
    }
    return slot
}

/** Skala tekan tombol: tombol mengecil sedikit saat ditekan untuk memperjelas umpan balik. */
private const val KEY_PRESS_SCALE = 0.92f

private fun Modifier.keyPressScale(isPressed: () -> Boolean): Modifier = graphicsLayer {
    val scale = if (isPressed()) KEY_PRESS_SCALE else 1f
    scaleX = scale
    scaleY = scale
}

@Immutable
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
        boardBg = Color(0xFF202124),
        stripBg = Color(0xFF292A2D),
        keyBg = Color(0xFF3C4043),
        keyPressedBg = Color(0xFF80868B),
        specialKeyBg = Color(0xFF303134),
        accentBg = Color(0xFF8AB4F8),
        accentContent = Color(0xFF062E6F),
        keyText = Color(0xFFE8EAED),
        keyHintText = Color(0xFF9AA0A6),
        borderColor = Color(0xFF5F6368),
        chipBg = Color(0xFF3C4043),
        chipHighlightBg = Color(0xFF0842A0)
    )
} else {
    KeyboardPalette(
        boardBg = Color(0xFFF1F3F4),
        stripBg = Color(0xFFF8F9FA),
        keyBg = Color(0xFFFFFFFF),
        keyPressedBg = Color(0xFFBDC1C6),
        specialKeyBg = Color(0xFFDADCE0),
        accentBg = Color(0xFF1A73E8),
        accentContent = Color(0xFFFFFFFF),
        keyText = Color(0xFF202124),
        keyHintText = Color(0xFF5F6368),
        borderColor = Color(0xFFDADCE0),
        chipBg = Color(0xFFF1F3F4),
        chipHighlightBg = Color(0xFFD3E3FD)
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
    "s" to listOf("#"),
    "d" to listOf("$"),
    "f" to listOf("_"),
    "g" to listOf("&"),
    "h" to listOf("-"),
    "j" to listOf("+"),
    "k" to listOf("("),
    "l" to listOf(")", "/"),
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
    @Suppress("UNUSED_PARAMETER") dismissedClipboardId: String? = null,
    @Suppress("UNUSED_PARAMETER") dismissedClipboardSignature: String? = null,
    onPasteClipboard: (String) -> Unit,
    onPasteClipboardItem: ((ClipboardItem) -> Unit)? = null,
    onRequestCtrlPaste: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onDismissRecentClipboard: (String) -> Unit = {},
    onCopySelectionToClipboard: (String) -> Unit,
    onToggleSerifMiring: () -> Unit = {},
    onToggleArialItalic: () -> Unit = {},
    onShowInputMethodPicker: () -> Unit = {},
    onToggleFloatingMode: () -> Unit = {},
    onSaveFloatingPosition: (Float, Float) -> Unit = { _, _ -> },
    onFloatingBoundsChanged: (AndroidRect) -> Unit = {},
    applySystemBarInsets: Boolean = false,
    isImeFloatingOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val palette = remember(isDark) { defaultKeyboardPalette(isDark) }
    val keyFontFamily = PlusJakartaSansFamily
    val chipFontFamily = InterFamily
    val keyFontStyle = FontStyle.Normal
    val isFloating = settings.floatingModeEnabled
    val view = LocalView.current
    val context = LocalContext.current
    val currentHapticEnabled by rememberUpdatedState(settings.hapticEnabled)
    val onFeedbackStable = remember(view) {
        {
            if (currentHapticEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    val currentSettings by rememberUpdatedState(settings)
    val currentPersonalEntries by rememberUpdatedState(personalEntries)
    val currentClipboardItems by rememberUpdatedState(clipboardItems)
    val currentOnToggleSerifMiring by rememberUpdatedState(onToggleSerifMiring)
    val currentOnToggleArialItalic by rememberUpdatedState(onToggleArialItalic)
    val currentOnToggleFloatingMode by rememberUpdatedState(onToggleFloatingMode)
    val currentOnSaveFloatingPosition by rememberUpdatedState(onSaveFloatingPosition)
    val currentOnFloatingBoundsChanged by rememberUpdatedState(onFloatingBoundsChanged)
    val currentOnPasteClipboard by rememberUpdatedState(onPasteClipboard)
    val currentOnPasteClipboardItem by rememberUpdatedState(onPasteClipboardItem)
    val currentOnRequestCtrlPaste by rememberUpdatedState(onRequestCtrlPaste)
    val onToggleFloatingModeStable = remember {
        { currentOnToggleFloatingMode() }
    }
    val onPasteClipboardItemStable = remember {
        { item: ClipboardItem ->
            val itemHandler = currentOnPasteClipboardItem
            if (itemHandler != null) {
                itemHandler(item)
            } else {
                currentOnPasteClipboard(item.preview)
            }
        }
    }
    val onRequestCtrlPasteStable = remember(controller) {
        {
            val reqPaste = currentOnRequestCtrlPaste
            if (reqPaste != null) {
                reqPaste()
            } else {
                val latestPreview = currentClipboardItems.firstOrNull()?.preview.orEmpty()
                controller.onCtrlPaste(latestPreview, currentSettings, currentPersonalEntries)
            }
        }
    }

    LaunchedEffect(isFloating) {
        if (!isFloating) {
            currentOnFloatingBoundsChanged(AndroidRect())
        }
    }

    // Kode A2F berganti tiap 30 detik: selama pintasan A2F aktif, hitung ulang tepat di batas periode.
    LaunchedEffect(controller.isA2fActive) {
        while (controller.isA2fActive) {
            delay(TotpGenerator.millisUntilNextPeriod() + 50L)
            controller.refreshA2fCode()
        }
    }

    val density = LocalDensity.current
    val containerCoordsRef = remember { CoordsRef() }
    var activePopupKey by remember { mutableStateOf<String?>(null) }
    var activePopupKeyBounds by remember { mutableStateOf<Rect?>(null) }
    var longPressOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAltIndex by remember { mutableIntStateOf(0) }
    var longPressAnchorX by remember { mutableFloatStateOf(0f) }

    val pillWidthDp = if (isFloating) 36.dp else 42.dp
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
    val popupTopMinPx = with(density) { 2.dp.toPx() }

    val onKeyPopupStartStable = remember(controller) {
        { label: String, keyCoords: LayoutCoordinates?, downLocalOffset: Offset, alts: List<String> ->
            if (alts.isNotEmpty()) {
                val cCoords = containerCoordsRef.coords
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
            }
        }
    }
    val onKeyPopupDragStable = remember(pillWidthPx, pillGapPx, popupPadHPx, dragThresholdPx, sideMarginPx) {
        { localPos: Offset ->
            val bounds = activePopupKeyBounds
            val count = longPressOptions.size
            if (bounds != null && count > 1) {
                val popupWidthPx = 2f * popupPadHPx + count * pillWidthPx + (count - 1) * pillGapPx
                val pointerX = bounds.left + localPos.x
                if (abs(pointerX - longPressAnchorX) <= dragThresholdPx) {
                    selectedAltIndex = 0
                } else {
                    var bestIdx = 0
                    var bestDist = Float.MAX_VALUE
                    val cW = (containerCoordsRef.coords?.size?.width ?: 0).toFloat()
                    val currentPopupX = if (cW > 0f) {
                        val desiredX = bounds.center.x - popupPadHPx - pillWidthPx / 2f
                        desiredX.coerceIn(
                            sideMarginPx,
                            (cW - popupWidthPx - sideMarginPx).coerceAtLeast(sideMarginPx)
                        )
                    } else sideMarginPx
                    for (i in longPressOptions.indices) {
                        val pillCenterX = currentPopupX + popupPadHPx +
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
        }
    }
    val onKeyPopupReleaseStable = remember(controller) {
        {
            val chosen = longPressOptions.getOrNull(selectedAltIndex)
            if (chosen != null) {
                onFeedbackStable()
                controller.onCharacterTap(chosen, currentSettings, currentPersonalEntries)
            }
            activePopupKey = null
            activePopupKeyBounds = null
            longPressOptions = emptyList()
            selectedAltIndex = 0
        }
    }
    val onClearPopupStable = remember {
        {
            activePopupKey = null
            activePopupKeyBounds = null
            longPressOptions = emptyList()
            selectedAltIndex = 0
        }
    }

    val totalHeightDp = remember(settings.heightPreset, controller.currentPage) {
        val baseRow = settings.heightEnum.keyHeightDp
        val rows = 4
        (rows * baseRow + rows * 6f + 46f).dp
    }

    @Composable
    fun KeyboardInnerContent(innerModifier: Modifier = Modifier) {
        Box(
            modifier = innerModifier.onGloballyPositioned { containerCoordsRef.coords = it }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Strip: Left = Suggestions / blank, Right = Fixed-position & fixed-size Utility Toolbar
                TopUtilityAndSuggestionBar(
                    controller = controller,
                    palette = palette,
                    settings = settings,
                    personalEntries = personalEntries,
                    chipFontFamily = chipFontFamily,
                    keyFontStyle = keyFontStyle,
                    isFloating = isFloating,
                    onFeedback = onFeedbackStable,
                    onToggleFloatingMode = onToggleFloatingModeStable
                )

                KeyboardBodyContent(
                    controller = controller,
                    settings = settings,
                    personalEntries = personalEntries,
                    clipboardItems = clipboardItems,
                    palette = palette,
                    keyFontFamily = keyFontFamily,
                    keyFontStyle = keyFontStyle,
                    totalHeightDp = totalHeightDp,
                    isFloating = isFloating,
                    onFeedback = onFeedbackStable,
                    onPasteClipboardItem = onPasteClipboardItemStable,
                    onRequestCtrlPaste = onRequestCtrlPasteStable,
                    onCopySelectionToClipboard = onCopySelectionToClipboard,
                    onShowInputMethodPicker = onShowInputMethodPicker,
                    onKeyPopupStart = onKeyPopupStartStable,
                    onKeyPopupDrag = onKeyPopupDragStable,
                    onKeyPopupRelease = onKeyPopupReleaseStable,
                    onClearPopup = onClearPopupStable
                )
            }

            // Floating Long-Press Symbol Shortcut Popup anchored right above the pressed key
            if (activePopupKey != null && longPressOptions.isNotEmpty()) {
                val optionCount = longPressOptions.size.coerceAtLeast(1)
                val popupWidthPx = 2f * popupPadHPx + optionCount * pillWidthPx + (optionCount - 1) * pillGapPx
                val containerW = (containerCoordsRef.coords?.size?.width ?: 0).toFloat()
                val bounds = activePopupKeyBounds
                val popupX = if (bounds != null && containerW > 0f) {
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
                val popupY = if (bounds != null) {
                    (bounds.top - popupHeightPx - gapAboveKeyPx).coerceAtLeast(popupTopMinPx)
                } else {
                    popupTopMinPx
                }

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
                                        onFeedbackStable()
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

    if (!isFloating) {
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
            ) {
                KeyboardInnerContent(innerModifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        // Mode Mengambang: lebar tetap 70% dari lebar vertikal (portrait) Android, tidak dapat diubah ukuran,
        // posisi dapat digeser bebas dan diingat dari terakhir kali.
        val metrics = context.resources.displayMetrics
        val portraitWidthPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val rawFloatingWidthDp = with(density) { (portraitWidthPx * 0.70f).toDp() }
        val overlayRootModifier = if (isImeFloatingOverlay) {
            val fullScreenHeightDp = with(density) { metrics.heightPixels.toDp() }
            modifier
                .fillMaxWidth()
                .height(fullScreenHeightDp)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        } else {
            modifier.fillMaxSize()
        }

        BoxWithConstraints(modifier = overlayRootModifier) {
            val floatingWidthDp = rawFloatingWidthDp.coerceAtMost(maxWidth)
            val containerWidthPx = constraints.maxWidth.toFloat()
            val containerHeightPx = constraints.maxHeight.toFloat()
            var cardWidthPx by remember { mutableFloatStateOf(with(density) { floatingWidthDp.toPx() }) }
            var cardHeightPx by remember { mutableFloatStateOf(with(density) { (totalHeightDp + 28.dp).toPx() }) }

            var isDraggingFloating by remember { mutableStateOf(false) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var pendingNormX by remember { mutableStateOf<Float?>(null) }
            var pendingNormY by remember { mutableStateOf<Float?>(null) }

            LaunchedEffect(settings.floatingPosX, settings.floatingPosY) {
                pendingNormX = null
                pendingNormY = null
            }

            val maxX = (containerWidthPx - cardWidthPx).coerceAtLeast(0f)
            val maxY = (containerHeightPx - cardHeightPx).coerceAtLeast(0f)
            val currentMaxX by rememberUpdatedState(maxX)
            val currentMaxY by rememberUpdatedState(maxY)

            val activeNormX = (pendingNormX ?: settings.floatingPosX).coerceIn(0f, 1f)
            val activeNormY = (pendingNormY ?: settings.floatingPosY).coerceIn(0f, 1f)
            val displayX = if (isDraggingFloating) {
                dragOffsetX.coerceIn(0f, maxX)
            } else {
                activeNormX * maxX
            }
            val displayY = if (isDraggingFloating) {
                dragOffsetY.coerceIn(0f, maxY)
            } else {
                activeNormY * maxY
            }

            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = displayX.roundToInt(),
                            y = displayY.roundToInt()
                        )
                    }
                    .width(floatingWidthDp)
                    .wrapContentHeight()
                    .onSizeChanged { size ->
                        cardWidthPx = size.width.toFloat()
                        cardHeightPx = size.height.toFloat()
                    }
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        currentOnFloatingBoundsChanged(
                            AndroidRect(
                                b.left.roundToInt(),
                                b.top.roundToInt(),
                                b.right.roundToInt(),
                                b.bottom.roundToInt()
                            )
                        )
                    }
                    .testTag("floating_keyboard_container"),
                shape = RoundedCornerShape(18.dp),
                color = palette.boardBg,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, palette.borderColor)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    KeyboardInnerContent(innerModifier = Modifier.fillMaxWidth())

                    // Drag handle bar (fixed position-drag only, strictly NO resize handle)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(palette.stripBg)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDraggingFloating = true
                                        val startNormX = (pendingNormX ?: currentSettings.floatingPosX).coerceIn(0f, 1f)
                                        val startNormY = (pendingNormY ?: currentSettings.floatingPosY).coerceIn(0f, 1f)
                                        dragOffsetX = startNormX * currentMaxX
                                        dragOffsetY = startNormY * currentMaxY
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(0f, currentMaxX)
                                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, currentMaxY)
                                    },
                                    onDragEnd = {
                                        val normX = if (currentMaxX > 0f) {
                                            (dragOffsetX / currentMaxX).coerceIn(0f, 1f)
                                        } else {
                                            currentSettings.floatingPosX
                                        }
                                        val normY = if (currentMaxY > 0f) {
                                            (dragOffsetY / currentMaxY).coerceIn(0f, 1f)
                                        } else {
                                            currentSettings.floatingPosY
                                        }
                                        pendingNormX = normX
                                        pendingNormY = normY
                                        isDraggingFloating = false
                                        currentOnSaveFloatingPosition(normX, normY)
                                    },
                                    onDragCancel = {
                                        val normX = if (currentMaxX > 0f) {
                                            (dragOffsetX / currentMaxX).coerceIn(0f, 1f)
                                        } else {
                                            currentSettings.floatingPosX
                                        }
                                        val normY = if (currentMaxY > 0f) {
                                            (dragOffsetY / currentMaxY).coerceIn(0f, 1f)
                                        } else {
                                            currentSettings.floatingPosY
                                        }
                                        pendingNormX = normX
                                        pendingNormY = normY
                                        isDraggingFloating = false
                                        currentOnSaveFloatingPosition(normX, normY)
                                    }
                                )
                            }
                            .semantics { contentDescription = "Geser posisi keyboard mengambang" }
                            .testTag("floating_drag_handle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(palette.keyHintText.copy(alpha = 0.55f))
                            )
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = null,
                                tint = palette.keyHintText.copy(alpha = 0.85f),
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
private fun KeyboardBodyContent(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    clipboardItems: List<ClipboardItem>,
    palette: KeyboardPalette,
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    totalHeightDp: Dp,
    isFloating: Boolean,
    onFeedback: () -> Unit,
    onPasteClipboardItem: (ClipboardItem) -> Unit,
    onRequestCtrlPaste: () -> Unit,
    onCopySelectionToClipboard: (String) -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onKeyPopupStart: (String, LayoutCoordinates?, Offset, List<String>) -> Unit,
    onKeyPopupDrag: (Offset) -> Unit,
    onKeyPopupRelease: () -> Unit,
    onClearPopup: () -> Unit
) {
    when (controller.utilityPanel) {
        UtilityPanel.CLIPBOARD -> {
            ClipboardPanel(
                palette = palette,
                items = clipboardItems,
                enabled = settings.clipboardHistoryEnabled,
                statusMessage = controller.clipboardStatusMessage,
                height = totalHeightDp - 46.dp,
                onPasteItem = { item ->
                    onFeedback()
                    onPasteClipboardItem(item)
                    controller.closeUtilityPanel()
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
                        isFloating = isFloating,
                        onFeedback = onFeedback,
                        onCopySelectionToClipboard = onCopySelectionToClipboard,
                        onRequestCtrlPaste = onRequestCtrlPaste,
                        onShowInputMethodPicker = onShowInputMethodPicker,
                        onKeyPopupStart = onKeyPopupStart,
                        onKeyPopupDrag = onKeyPopupDrag,
                        onKeyPopupRelease = onKeyPopupRelease,
                        onClearPopup = onClearPopup
                    )
                }
                KeyboardPage.SYMBOLS_1, KeyboardPage.SYMBOLS_2 -> {
                    SymbolsKeyboardGrid(
                        controller = controller,
                        settings = settings,
                        personalEntries = personalEntries,
                        palette = palette,
                        keyFontFamily = keyFontFamily,
                        keyFontStyle = keyFontStyle,
                        isFloating = isFloating,
                        onFeedback = onFeedback,
                        onCopySelectionToClipboard = onCopySelectionToClipboard,
                        onRequestCtrlPaste = onRequestCtrlPaste,
                        onShowInputMethodPicker = onShowInputMethodPicker,
                        onKeyPopupStart = onKeyPopupStart,
                        onKeyPopupDrag = onKeyPopupDrag,
                        onKeyPopupRelease = onKeyPopupRelease,
                        onClearPopup = onClearPopup
                    )
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
    isFloating: Boolean,
    onFeedback: () -> Unit,
    onToggleFloatingMode: () -> Unit
) {
    val barHeight = if (isFloating) 40.dp else 44.dp
    val hPadding = if (isFloating) 4.dp else 6.dp
    val btnSize = if (isFloating) 28.dp else 34.dp
    val iconSize = if (isFloating) 15.dp else 18.dp
    val statusMsg = controller.clipboardStatusMessage
    val dividerColor = palette.keyHintText.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(palette.stripBg)
            .drawBehind {
                val strokePx = 1.dp.toPx()
                val halfStroke = strokePx / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, halfStroke),
                    end = Offset(size.width, halfStroke),
                    strokeWidth = strokePx
                )
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height - halfStroke),
                    end = Offset(size.width, size.height - halfStroke),
                    strokeWidth = strokePx
                )
            }
            .padding(horizontal = hPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isFloating) 4.dp else 6.dp)
    ) {
        // Left side: Suggestion area (copied text stays in Clipboard panel only; never replaces suggestions)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("suggestion_strip_slot"),
            contentAlignment = Alignment.CenterStart
        ) {
            when {
                !statusMsg.isNullOrBlank() -> {
                    Text(
                        text = statusMsg,
                        color = palette.accentBg,
                        fontSize = if (isFloating) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .testTag("clipboard_status_banner")
                    )
                }
                controller.utilityPanel == UtilityPanel.NONE && controller.isA2fActive -> {
                    A2fChipsRow(
                        chips = controller.a2fChips,
                        dividerColor = dividerColor,
                        palette = palette,
                        isFloating = isFloating,
                        onTap = { chip ->
                            onFeedback()
                            controller.onA2fChipTap(chip, personalEntries)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                controller.utilityPanel == UtilityPanel.NONE && controller.folderSuggestions.isNotEmpty() -> {
                    FolderWordsRow(
                        words = controller.folderSuggestions,
                        dividerColor = dividerColor,
                        palette = palette,
                        fontFamily = chipFontFamily,
                        fontStyle = keyFontStyle,
                        isFloating = isFloating,
                        onTap = { word ->
                            onFeedback()
                            controller.onFolderWordTap(word, settings, personalEntries)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                controller.utilityPanel == UtilityPanel.NONE -> {
                    SuggestionChipsRow(
                        suggestions = controller.suggestions,
                        primaryAutoCorrect = controller.primaryAutoCorrect,
                        dividerColor = dividerColor,
                        palette = palette,
                        fontFamily = chipFontFamily,
                        fontStyle = keyFontStyle,
                        isFloating = isFloating,
                        onTap = { word ->
                            onFeedback()
                            controller.onSuggestionTap(word, settings, personalEntries)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

        // Full-height divider separating suggestion/preview strip from fixed right utility toolbar
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .background(dividerColor)
                .testTag("suggestion_toolbar_divider")
        )

        // Right side: Fixed-position, fixed-size utility toolbar (never shifts or wraps)
        FixedToolbarIconRow(
            controller = controller,
            palette = palette,
            floatingModeEnabled = settings.floatingModeEnabled,
            btnSize = btnSize,
            iconSize = iconSize,
            onFeedback = onFeedback,
            onToggleFloatingMode = onToggleFloatingMode,
            modifier = Modifier
                .wrapContentWidth()
                .testTag("ime_fixed_toolbar")
        )
    }
}

@Composable
private fun FixedToolbarIconRow(
    controller: KeyboardController,
    palette: KeyboardPalette,
    floatingModeEnabled: Boolean,
    btnSize: Dp,
    iconSize: Dp,
    onFeedback: () -> Unit,
    onToggleFloatingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityIconButton(
            icon = Icons.Outlined.ContentPaste,
            contentDesc = "Papan Klip",
            active = controller.utilityPanel == UtilityPanel.CLIPBOARD,
            palette = palette,
            buttonSize = btnSize,
            iconSize = iconSize,
            modifier = Modifier.testTag("ime_clipboard_button"),
            onClick = {
                onFeedback()
                controller.toggleUtilityPanel(UtilityPanel.CLIPBOARD)
            }
        )
        UtilityIconButton(
            icon = Icons.Default.PictureInPictureAlt,
            contentDesc = "Mode Keyboard Mengambang",
            active = floatingModeEnabled,
            palette = palette,
            buttonSize = btnSize,
            iconSize = iconSize,
            modifier = Modifier.testTag("ime_floating_mode_toggle"),
            onClick = {
                onFeedback()
                onToggleFloatingMode()
            }
        )
        UtilityIconButton(
            icon = Icons.Default.Settings,
            contentDesc = "Buka Pengaturan",
            active = false,
            palette = palette,
            buttonSize = btnSize,
            iconSize = iconSize,
            modifier = Modifier.testTag("ime_settings_button"),
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
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(buttonSize)
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
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SuggestionColumnDivider(
    color: Color,
    tag: String
) {
    Box(
        modifier = Modifier
            .width(1.5.dp)
            .fillMaxHeight()
            .background(color)
            .testTag(tag)
    )
}

@Composable
private fun SuggestionChipsRow(
    suggestions: List<String>,
    primaryAutoCorrect: String?,
    dividerColor: Color,
    palette: KeyboardPalette,
    fontFamily: FontFamily,
    fontStyle: FontStyle,
    isFloating: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipHeight = if (isFloating) 30.dp else 32.dp
    val stripFontSize = if (isFloating) 11.sp else 13.sp

    // Tiga kotak saran. Entri pintasan yang sama tampil satu per kotak (maks. 3).
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(if (isFloating) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0 until 3).forEach { index ->
            if (index > 0) {
                SuggestionColumnDivider(
                    color = dividerColor,
                    tag = "suggestion_divider_${index}"
                )
            }
            val word = suggestions.getOrNull(index)
            if (word == null) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(chipHeight)
                )
                return@forEach
            }
            val isHighlighted = primaryAutoCorrect != null && word == primaryAutoCorrect
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chipHeight)
                    .testTag("suggestion_chip")
                    .clickable { onTap(word) }
                    .padding(horizontal = if (isFloating) 4.dp else 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    color = if (isHighlighted) palette.accentBg else palette.keyText,
                    fontSize = stripFontSize,
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

private const val SUGGESTION_COLUMNS_PER_PAGE = 3

/** Satu halaman memenuhi lebar bilah: satu geseran berpindah satu kelompok tiga kolom. */
@Composable
internal fun ThreeColumnSuggestionPager(
    itemCount: Int,
    contentKey: List<String>,
    dividerColor: Color,
    modifier: Modifier = Modifier,
    item: @Composable (Int) -> Unit
) {
    val columns = SUGGESTION_COLUMNS_PER_PAGE
    val pagerState = rememberPagerState(pageCount = { (itemCount + columns - 1) / columns })
    LaunchedEffect(contentKey) {
        pagerState.scrollToPage(0)
    }
    val snap = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.25f)
    val distanceOnlySnap = remember(snap) {
        object : TargetedFlingBehavior {
            override suspend fun ScrollScope.performFling(
                initialVelocity: Float,
                onRemainingDistanceUpdated: (Float) -> Unit
            ): Float = with(snap) {
                // Posisi jari saja menentukan halaman tujuan, bukan kecepatan pelepasannya.
                performFling(0f, onRemainingDistanceUpdated)
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxHeight().testTag("paged_suggestions"),
        flingBehavior = distanceOnlySnap
    ) { page ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            repeat(columns) { column ->
                if (column > 0) {
                    SuggestionColumnDivider(color = dividerColor, tag = "paged_suggestion_divider_$column")
                }
                val index = page * columns + column
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (index < itemCount) item(index)
                }
            }
        }
    }
}

@Composable
private fun FolderWordsRow(
    words: List<String>,
    dividerColor: Color,
    palette: KeyboardPalette,
    fontFamily: FontFamily,
    fontStyle: FontStyle,
    isFloating: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipHeight = if (isFloating) 30.dp else 32.dp
    val stripFontSize = if (isFloating) 11.sp else 13.sp

    ThreeColumnSuggestionPager(words.size, words, dividerColor, modifier) { index ->
        val word = words[index]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chipHeight)
                .testTag("folder_word_chip")
                .clickable { onTap(word) }
                .padding(horizontal = if (isFloating) 4.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word,
                color = palette.keyText,
                fontSize = stripFontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamily,
                fontStyle = fontStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Satu kolom per akun A2F, kode dan email masing-masing bisa disentuh. */
@Composable
private fun A2fChipsRow(
    chips: List<A2fChip>,
    dividerColor: Color,
    palette: KeyboardPalette,
    isFloating: Boolean,
    onTap: (A2fChip) -> Unit,
    modifier: Modifier = Modifier
) {
    // Perubahan kode tiap 30 detik tidak mereset halaman yang sedang dibuka.
    // Seluruh kolom bisa disentuh; yang ditempel selalu kode, bukan email.
    ThreeColumnSuggestionPager(chips.size, chips.map { it.entryId }, dividerColor, modifier) { index ->
        val chip = chips[index]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("a2f_chip")
                .clickable { onTap(chip) }
                .padding(horizontal = if (isFloating) 2.dp else 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chip.code,
                color = palette.accentBg,
                fontSize = if (isFloating) 12.sp else 13.sp,
                lineHeight = if (isFloating) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (chip.shortEmail.isNotEmpty()) {
                Text(
                    text = chip.shortEmail,
                    color = palette.keyText,
                    fontSize = if (isFloating) 8.sp else 9.sp,
                    lineHeight = if (isFloating) 9.sp else 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private val QWERTY_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val QWERTY_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val QWERTY_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m")

// Same geometry as QWERTY: 10 / 9 / (1.45 + 7 + 1.45) slots.
// Panel 1: angka, @#$_&*%/•, operator matematika (-+×÷=), dan panah kursor vertikal (↑ ↓).
private val SYMBOLS_1_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("@", "#", "$", "_", "&", "*", "%", "/", "•"),
    listOf("-", "+", "×", "÷", "=", "↑", "↓")
)

// Panel 2: simbol standar unik + simbol panah literal (← → ↑ ↓) tanpa simbol jarang dipakai (✗€£≠©®) maupun duplikat Panel 1 / pintasan QWERTY (10 / 9 / 7).
private val SYMBOLS_2_ROWS = listOf(
    listOf("✓", "Rp", "←", "→", "↑", "↓", "^", "§", "~", "|"),
    listOf("<", ">", "{", "}", "[", "]", "\\", "°", "±"),
    listOf("`", "…", "🔥", "😂", "😍", "👍", "🙏")
)

@Composable
private fun QwertyKeyboardGrid(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    isFloating: Boolean,
    onFeedback: () -> Unit,
    onCopySelectionToClipboard: (String) -> Unit,
    onRequestCtrlPaste: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onKeyPopupStart: (String, LayoutCoordinates?, Offset, List<String>) -> Unit,
    onKeyPopupDrag: (Offset) -> Unit,
    onKeyPopupRelease: () -> Unit,
    onClearPopup: () -> Unit
) {
    val keyHeight = settings.heightEnum.keyHeightDp.dp
    val gridPadH = if (isFloating) 3.dp else 4.dp
    val rowGap = if (isFloating) 2.5.dp else 4.dp
    val row2PadH = if (isFloating) 9.dp else 14.dp
    val keyFontSize = if (isFloating) 15.sp else 18.sp
    val hintFontSize = if (isFloating) 8.4.sp else 10.8.sp
    val pointerTracker = rememberKeyPointersTracker()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .keyPointersSurface(pointerTracker)
            .padding(horizontal = gridPadH, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: q..p (with small digit hints 1..0 at top-right corner for long-press)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            QWERTY_ROW_1.forEach { ch ->
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
                    fontSize = keyFontSize,
                    hintFontSize = hintFontSize,
                    hasLongPress = alts.isNotEmpty(),
                    pointerTracker = pointerTracker,
                    slotId = "qwerty_$ch",
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(
                            rawChar = ch,
                            settings = settings,
                            personalEntries = personalEntries,
                            onCopyResult = onCopySelectionToClipboard,
                            onPasteRequest = onRequestCtrlPaste
                        )
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
                .padding(horizontal = row2PadH),
            horizontalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            QWERTY_ROW_2.forEach { ch ->
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
                    fontSize = keyFontSize,
                    hintFontSize = hintFontSize,
                    hasLongPress = alts.isNotEmpty(),
                    pointerTracker = pointerTracker,
                    slotId = "qwerty_$ch",
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(
                            rawChar = ch,
                            settings = settings,
                            personalEntries = personalEntries,
                            onCopyResult = onCopySelectionToClipboard,
                            onPasteRequest = onRequestCtrlPaste
                        )
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

        // Row 3: Shift + z..m + Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialActionKey(
                height = keyHeight,
                palette = palette,
                bordered = true,
                highlighted = controller.shiftState != ShiftState.OFF,
                contentDescription = "Shift",
                pointerTracker = pointerTracker,
                slotId = "qwerty_shift",
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
                    modifier = Modifier.size(if (isFloating) 18.dp else 22.dp)
                )
            }

            QWERTY_ROW_3.forEach { ch ->
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
                    fontSize = keyFontSize,
                    hintFontSize = hintFontSize,
                    hasLongPress = alts.isNotEmpty(),
                    pointerTracker = pointerTracker,
                    slotId = "qwerty_$ch",
                    onTap = {
                        onFeedback()
                        controller.onCharacterTap(
                            rawChar = ch,
                            settings = settings,
                            personalEntries = personalEntries,
                            onCopyResult = onCopySelectionToClipboard,
                            onPasteRequest = onRequestCtrlPaste
                        )
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
                isFloating = isFloating,
                pointerTracker = pointerTracker,
                slotId = "qwerty_backspace",
                onFeedback = onFeedback,
                onDelete = {
                    controller.onBackspaceTap(settings, personalEntries)
                },
                modifier = Modifier.weight(1.45f)
            )
        }

        // Row 4: Ω | Ctrl | Spacebar (80%, trimmed left) | . or , (swappable) | Enter
        BottomNavigationRow(
            leftModeLabel = "Ω",
            leftModeContentDesc = "Simbol",
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
            isFloating = isFloating,
            pointerTracker = pointerTracker,
            onFeedback = onFeedback,
            onShowInputMethodPicker = onShowInputMethodPicker,
            onKeyPopupStart = onKeyPopupStart,
            onKeyPopupDrag = onKeyPopupDrag,
            onKeyPopupRelease = onKeyPopupRelease,
            onClearPopup = onClearPopup
        )
    }
}

@Composable
private fun SymbolsKeyboardGrid(
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    isFloating: Boolean,
    onFeedback: () -> Unit,
    onCopySelectionToClipboard: (String) -> Unit,
    onRequestCtrlPaste: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onKeyPopupStart: (String, LayoutCoordinates?, Offset, List<String>) -> Unit,
    onKeyPopupDrag: (Offset) -> Unit,
    onKeyPopupRelease: () -> Unit,
    onClearPopup: () -> Unit
) {
    val keyHeight = settings.heightEnum.keyHeightDp.dp
    val gridPadH = if (isFloating) 3.dp else 4.dp
    val rowGap = if (isFloating) 2.5.dp else 4.dp
    val baseFontSize = if (isFloating) 14.sp else 17.sp
    val pointerTracker = rememberKeyPointersTracker()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .keyPointersSurface(pointerTracker)
            .padding(horizontal = gridPadH, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("symbols_panel")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isPageTwoSelected = controller.currentPage == KeyboardPage.SYMBOLS_2
                val activeRows = if (isPageTwoSelected) SYMBOLS_2_ROWS else SYMBOLS_1_ROWS
                val showingSecondInOrder = isPageTwoSelected
                val nextPage = if (isPageTwoSelected) KeyboardPage.SYMBOLS_1 else KeyboardPage.SYMBOLS_2
                activeRows.forEachIndexed { rowIndex, rowSymbols ->
                    Row(
                        modifier = Modifier.fillMaxWidth().then(
                            if (rowIndex == 1 && rowSymbols.size < 10) {
                                Modifier.padding(horizontal = if (isFloating) 9.dp else 14.dp)
                            } else {
                                Modifier
                            }
                        ),
                        horizontalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        if (rowIndex == 2) {
                            SpecialActionKey(
                                height = keyHeight,
                                palette = palette,
                                bordered = true,
                                highlighted = showingSecondInOrder,
                                contentDescription = if (showingSecondInOrder) "Panel Simbol 1" else "Panel Simbol 2",
                                pointerTracker = pointerTracker,
                                slotId = "symbols_page_toggle",
                                onTap = {
                                    onFeedback()
                                    controller.setPage(nextPage)
                                },
                                modifier = Modifier
                                    .weight(1.45f)
                                    .testTag("key_symbols_page_toggle")
                            ) {
                                Text(
                                    text = if (showingSecondInOrder) "2/2" else "1/2",
                                    color = if (showingSecondInOrder) palette.accentBg else palette.keyText,
                                    fontSize = if (isFloating) 10.sp else 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        rowSymbols.forEach { sym ->
                            val isPanel1CursorArrow = !isPageTwoSelected && (sym == "↑" || sym == "↓")
                            if (isPanel1CursorArrow) {
                                val (dx, dy, arrowDesc) = when (sym) {
                                    "↑" -> Triple(0, -1, "Geser kursor atas")
                                    else -> Triple(0, 1, "Geser kursor bawah")
                                }
                                SpecialActionKey(
                                    height = keyHeight,
                                    palette = palette,
                                    bordered = true,
                                    highlighted = false,
                                    contentDescription = arrowDesc,
                                    pointerTracker = pointerTracker,
                                    slotId = "sym_cursor_$sym",
                                    onTap = {
                                        onFeedback()
                                        controller.onArrowMove(dx, dy)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("key_$sym")
                                ) {
                                    Text(
                                        text = sym,
                                        color = palette.keyText,
                                        fontSize = baseFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = keyFontFamily,
                                        fontStyle = keyFontStyle
                                    )
                                }
                            } else {
                                val symFontSize = if (sym.length > 1) {
                                    if (isFloating) 11.sp else 13.sp
                                } else {
                                    baseFontSize
                                }
                                CharKey(
                                    label = sym,
                                    hint = null,
                                    height = keyHeight,
                                    palette = palette,
                                    bordered = true,
                                    fontFamily = keyFontFamily,
                                    fontStyle = keyFontStyle,
                                    fontSize = symFontSize,
                                    pointerTracker = pointerTracker,
                                    slotId = "sym_${if (isPageTwoSelected) 2 else 1}_$sym",
                                    onTap = {
                                        onFeedback()
                                        controller.onCharacterTap(
                                            rawChar = sym,
                                            settings = settings,
                                            personalEntries = personalEntries,
                                            onCopyResult = onCopySelectionToClipboard,
                                            onPasteRequest = onRequestCtrlPaste
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (rowIndex == 2) {
                            BackspaceKey(
                                height = keyHeight, palette = palette, bordered = true,
                                isFloating = isFloating, pointerTracker = pointerTracker,
                                slotId = "symbols_backspace", onFeedback = onFeedback,
                                onDelete = { controller.onBackspaceTap(settings, personalEntries) },
                                modifier = Modifier.weight(1.45f)
                            )
                        }
                    }
                }
            }
        }

        BottomNavigationRow(
            leftModeLabel = "ABC",
            leftModeContentDesc = "Kembali ke Huruf",
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
            isFloating = isFloating,
            pointerTracker = pointerTracker,
            onFeedback = onFeedback,
            onShowInputMethodPicker = onShowInputMethodPicker,
            onKeyPopupStart = onKeyPopupStart,
            onKeyPopupDrag = onKeyPopupDrag,
            onKeyPopupRelease = onKeyPopupRelease,
            onClearPopup = onClearPopup
        )
    }
}

@Composable
private fun BottomNavigationRow(
    leftModeLabel: String,
    leftModeContentDesc: String,
    onLeftModeTap: () -> Unit,
    keyHeight: Dp,
    controller: KeyboardController,
    settings: KeyboardSettings,
    personalEntries: List<PersonalDictionaryEntry>,
    palette: KeyboardPalette,
    keyFontFamily: FontFamily,
    keyFontStyle: FontStyle,
    isFloating: Boolean,
    pointerTracker: KeyPointersTracker,
    onFeedback: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onKeyPopupStart: (String, LayoutCoordinates?, Offset, List<String>) -> Unit,
    onKeyPopupDrag: (Offset) -> Unit,
    onKeyPopupRelease: () -> Unit,
    onClearPopup: () -> Unit
) {
    val rowGap = if (isFloating) 2.5.dp else 4.dp
    val keyFontSize = if (isFloating) 15.sp else 18.sp
    val hintFontSize = if (isFloating) 8.4.sp else 10.8.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spacebar enlarged by removing small newline arrow (0.90f) and trimming 10% from Symbols key (2.146f -> 1.931f, freeing 0.215f)
        // Weights: Symbols=1.738f (Ω -10%), Ctrl=1.637f (+20%), Space=3.875f (absorbs net +0.08f), Period=1.0f, Enter=1.6f (Total=9.85f)
        SpecialActionKey(
            height = keyHeight,
            palette = palette,
            bordered = true,
            highlighted = controller.currentPage == KeyboardPage.SYMBOLS_1 ||
                controller.currentPage == KeyboardPage.SYMBOLS_2,
            contentDescription = leftModeContentDesc,
            pointerTracker = pointerTracker,
            slotId = "bottom_symbols",
            onTap = onLeftModeTap,
            modifier = Modifier
                .weight(1.738f)
                .testTag("key_symbols")
        ) {
            Text(
                text = leftModeLabel,
                color = palette.keyText,
                fontSize = if (leftModeLabel == "Ω") {
                    if (isFloating) 15.sp else 18.sp
                } else {
                    if (isFloating) 11.sp else 13.sp
                },
                fontWeight = FontWeight.SemiBold,
                fontFamily = keyFontFamily,
                fontStyle = keyFontStyle
            )
        }

        CtrlKey(
            ctrlState = controller.ctrlState,
            height = keyHeight,
            palette = palette,
            isFloating = isFloating,
            pointerTracker = pointerTracker,
            slotId = "bottom_ctrl",
            onTap = {
                onFeedback()
                controller.onCtrlTap()
            },
            modifier = Modifier.weight(1.637f)
        )

        // Enlarged Spacebar trackpad key
        SpacebarTrackpadKey(
            height = keyHeight,
            palette = palette,
            bordered = true,
            onTap = {
                onFeedback()
                controller.onSpaceTap(settings, personalEntries)
            },
            onLongPress = {
                onFeedback()
                onShowInputMethodPicker()
            },
            onMoveCursorStep = { step ->
                onFeedback()
                controller.onSpacebarTrackpadStep(step)
            },
            modifier = Modifier.weight(3.875f)
        )

        // Combined period / comma key
        CharKey(
            label = ".",
            hint = ",",
            height = keyHeight,
            palette = palette,
            bordered = true,
            fontFamily = keyFontFamily,
            fontStyle = keyFontStyle,
            fontSize = keyFontSize,
            hintFontSize = hintFontSize,
            hasLongPress = true,
            pointerTracker = pointerTracker,
            slotId = "bottom_period",
            onTap = {
                onFeedback()
                controller.onCharacterTap(".", settings, personalEntries)
            },
            onLongPressStart = { coords, downOffset ->
                onKeyPopupStart(".", coords, downOffset, listOf(","))
            },
            onLongPressDrag = onKeyPopupDrag,
            onLongPressRelease = onKeyPopupRelease,
            onLongPressCancel = onClearPopup,
            modifier = Modifier.weight(1.0f)
        )

        SpecialActionKey(
            height = keyHeight,
            palette = palette,
            bordered = false,
            highlighted = true,
            useAccentFill = true,
            contentDescription = controller.enterActionType.label,
            pointerTracker = pointerTracker,
            slotId = "bottom_enter",
            onTap = {
                onFeedback()
                controller.onEnterTap(settings, personalEntries)
            },
            modifier = Modifier
                .weight(1.6f)
                .testTag("key_enter")
        ) {
            if (controller.enterActionType == EnterActionType.ENTER) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = palette.accentContent,
                    modifier = Modifier.size(if (isFloating) 18.dp else 21.dp)
                )
            } else {
                Text(
                    text = controller.enterActionType.label,
                    color = palette.accentContent,
                    fontSize = if (isFloating) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CtrlKey(
    ctrlState: CtrlState,
    height: Dp,
    palette: KeyboardPalette,
    isFloating: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    pointerTracker: KeyPointersTracker? = null,
    slotId: String = "ctrl"
) {
    val shape = remember { RoundedCornerShape(8.dp) }
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val currentPalette by rememberUpdatedState(palette)
    val currentCtrlState by rememberUpdatedState(ctrlState)
    val currentOnTap by rememberUpdatedState(onTap)
    val slot = rememberRegisteredKeySlot(
        tracker = pointerTracker,
        slotId = slotId,
        onTap = onTap
    )
    val borderColor = when (ctrlState) {
        CtrlState.OFF -> palette.borderColor
        CtrlState.ARMED -> palette.accentBg
    }
    val borderWidth = if (ctrlState == CtrlState.OFF) 0.75.dp else 1.5.dp
    val contentColor = when (ctrlState) {
        CtrlState.OFF -> palette.keyText
        CtrlState.ARMED -> palette.accentBg
    }
    val statusDesc = when (ctrlState) {
        CtrlState.OFF -> "Ctrl nonaktif"
        CtrlState.ARMED -> "Ctrl aktif"
    }

    Box(
        modifier = modifier
            .height(height)
            .keyPressScale { (pointerTracker?.pressedCounts?.get(slotId) ?: 0) > 0 }
            .clip(shape)
            .drawBehind {
                val isPressed = (pointerTracker?.pressedCounts?.get(slotId) ?: 0) > 0
                val fill = when {
                    isPressed && currentCtrlState == CtrlState.OFF -> currentPalette.keyPressedBg
                    else -> when (currentCtrlState) {
                        CtrlState.OFF -> currentPalette.specialKeyBg
                        CtrlState.ARMED -> currentPalette.chipHighlightBg
                    }
                }
                drawRoundRect(
                    color = fill,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            .border(borderWidth, borderColor, shape)
            .onGloballyPositioned { coords ->
                slot?.coords = coords
            }
            .then(
                if (pointerTracker == null) {
                    Modifier.clickable(onClick = onTap)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = statusDesc
                onClick {
                    currentOnTap()
                    true
                }
            }
            .testTag("key_ctrl"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ctrl",
            color = contentColor,
            fontSize = if (isFloating) 11.sp else 13.sp,
            fontWeight = if (ctrlState == CtrlState.OFF) FontWeight.SemiBold else FontWeight.Bold,
            maxLines = 1
        )
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
    fontSize: TextUnit = 18.sp,
    hintFontSize: TextUnit = 10.8.sp,
    hasLongPress: Boolean = false,
    onTap: () -> Unit,
    onLongPressStart: (LayoutCoordinates?, Offset) -> Unit = { _, _ -> },
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressRelease: () -> Unit = {},
    onLongPressCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    pointerTracker: KeyPointersTracker? = null,
    slotId: String = "char_$label"
) {
    val pressedState = remember { mutableStateOf(false) }
    val keyCoordsRef = remember { CoordsRef() }
    val currentPalette by rememberUpdatedState(palette)
    val currentOnTap by rememberUpdatedState(onTap)
    val slot = rememberRegisteredKeySlot(
        tracker = pointerTracker,
        slotId = slotId,
        onTap = onTap,
        hasLongPress = hasLongPress,
        isTapReorderFlushable = true,
        onLongPressStart = onLongPressStart,
        onLongPressDrag = onLongPressDrag,
        onLongPressRelease = onLongPressRelease,
        onLongPressCancel = onLongPressCancel
    )

    val density = LocalDensity.current
    val shape = remember { RoundedCornerShape(8.dp) }
    val cornerRadiusPx = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .height(height)
            .keyPressScale { if (pointerTracker != null) (pointerTracker.pressedCounts[slotId] ?: 0) > 0 else pressedState.value }
            .clip(shape)
            .drawBehind {
                // Draw-phase read prevents full-key recomposition on every press/release
                val isPressed = if (pointerTracker != null) {
                    (pointerTracker.pressedCounts[slotId] ?: 0) > 0
                } else {
                    pressedState.value
                }
                drawRoundRect(
                    color = if (isPressed) currentPalette.keyPressedBg else currentPalette.keyBg,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .onGloballyPositioned { coords ->
                if (slot != null) {
                    slot.coords = coords
                } else {
                    keyCoordsRef.coords = coords
                }
            }
            .then(
                if (pointerTracker == null) {
                    Modifier.clickable(onClick = onTap)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                onClick {
                    currentOnTap()
                    true
                }
            }
            .testTag("key_$label"),
        contentAlignment = Alignment.Center
    ) {
        if (!hint.isNullOrEmpty()) {
            Text(
                text = hint,
                color = palette.keyHintText,
                fontSize = hintFontSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
        Text(
            text = label,
            color = palette.keyText,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            fontFamily = fontFamily,
            fontStyle = fontStyle,
            maxLines = 1,
            softWrap = false
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
    pointerTracker: KeyPointersTracker? = null,
    slotId: String = "action_$contentDescription",
    content: @Composable () -> Unit
) {
    val pressedState = remember { mutableStateOf(false) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentPalette by rememberUpdatedState(palette)
    val currentHighlighted by rememberUpdatedState(highlighted)
    val currentAccentFill by rememberUpdatedState(useAccentFill)
    val slot = rememberRegisteredKeySlot(
        tracker = pointerTracker,
        slotId = slotId,
        onTap = onTap
    )
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val shape = remember { RoundedCornerShape(8.dp) }

    Box(
        modifier = modifier
            .height(height)
            .keyPressScale { if (pointerTracker != null) (pointerTracker.pressedCounts[slotId] ?: 0) > 0 else pressedState.value }
            .clip(shape)
            .drawBehind {
                val isPressed = if (pointerTracker != null) {
                    (pointerTracker.pressedCounts[slotId] ?: 0) > 0
                } else {
                    pressedState.value
                }
                val bg = when {
                    currentAccentFill -> if (isPressed) currentPalette.accentBg.copy(alpha = 0.6f) else currentPalette.accentBg
                    isPressed -> currentPalette.keyPressedBg
                    currentHighlighted -> currentPalette.chipHighlightBg
                    else -> currentPalette.specialKeyBg
                }
                drawRoundRect(
                    color = bg,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            .then(if (bordered && !useAccentFill) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .onGloballyPositioned { coords ->
                slot?.coords = coords
            }
            .then(
                if (pointerTracker == null) {
                    Modifier.clickable(onClick = onTap)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                onClick {
                    currentOnTap()
                    true
                }
            },
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
    isFloating: Boolean = false,
    onFeedback: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    pointerTracker: KeyPointersTracker? = null,
    slotId: String = "backspace"
) {
    val pressedState = remember { mutableStateOf(false) }
    val currentPalette by rememberUpdatedState(palette)
    val currentOnFeedback by rememberUpdatedState(onFeedback)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val slot = rememberRegisteredKeySlot(
        tracker = pointerTracker,
        slotId = slotId,
        onTap = onDelete,
        isBackspaceRepeat = true,
        onFeedback = onFeedback
    )

    val density = LocalDensity.current
    val cancelMoveRadiusPx = with(density) { 40.dp.toPx() }
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val shape = remember { RoundedCornerShape(8.dp) }

    Box(
        modifier = modifier
            .height(height)
            .keyPressScale { if (pointerTracker != null) (pointerTracker.pressedCounts[slotId] ?: 0) > 0 else pressedState.value }
            .clip(shape)
            .drawBehind {
                val isPressed = if (pointerTracker != null) {
                    (pointerTracker.pressedCounts[slotId] ?: 0) > 0
                } else {
                    pressedState.value
                }
                drawRoundRect(
                    color = if (isPressed) currentPalette.keyPressedBg else currentPalette.specialKeyBg,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .onGloballyPositioned { coords ->
                slot?.coords = coords
            }
            .then(
                if (pointerTracker == null) {
                    Modifier.pointerInput(cancelMoveRadiusPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressedState.value = true
                            val initialDelayMs = 380L
                            val repeatIntervalMs = 60L

                            try {
                                val firstOutcome = withTimeoutOrNull(initialDelayMs) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if (!change.pressed) {
                                            change.consume()
                                            return@withTimeoutOrNull "TAP"
                                        }
                                        if ((change.position - down.position).getDistance() > cancelMoveRadiusPx) {
                                            return@withTimeoutOrNull "CANCEL"
                                        }
                                    }
                                    "CANCEL"
                                }

                                if (firstOutcome == "TAP") {
                                    currentOnFeedback()
                                    currentOnDelete()
                                    return@awaitEachGesture
                                }
                                if (firstOutcome == "CANCEL") {
                                    return@awaitEachGesture
                                }

                                while (true) {
                                    currentOnFeedback()
                                    currentOnDelete()

                                    val repeatOutcome = withTimeoutOrNull(repeatIntervalMs) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            if (!change.pressed) {
                                                change.consume()
                                                return@withTimeoutOrNull "STOP"
                                            }
                                            if ((change.position - down.position).getDistance() > cancelMoveRadiusPx) {
                                                return@withTimeoutOrNull "STOP"
                                            }
                                        }
                                        "STOP"
                                    }
                                    if (repeatOutcome == "STOP") break
                                }
                            } finally {
                                pressedState.value = false
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Hapus"
                onClick {
                    currentOnFeedback()
                    currentOnDelete()
                    true
                }
            }
            .testTag("key_backspace"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Hapus",
            tint = palette.keyText,
            modifier = Modifier.size(if (isFloating) 17.dp else 20.dp)
        )
    }
}

@Composable
private fun SpacebarTrackpadKey(
    height: Dp,
    palette: KeyboardPalette,
    bordered: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onMoveCursorStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pressedState = remember { mutableStateOf(false) }
    val currentPalette by rememberUpdatedState(palette)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnMoveCursorStep by rememberUpdatedState(onMoveCursorStep)
    val density = LocalDensity.current
    val dragStartSlopPx = with(density) { 12.dp.toPx() }
    val stepPx = with(density) { 10.dp.toPx() }
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val shape = remember { RoundedCornerShape(8.dp) }

    Box(
        modifier = modifier
            .height(height)
            .keyPressScale { pressedState.value }
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = if (pressedState.value) currentPalette.keyPressedBg else currentPalette.keyBg,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            .then(if (bordered) Modifier.border(0.75.dp, palette.borderColor, shape) else Modifier)
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressedState.value = true
                    var draggingTrackpad = false
                    var dragAccum = 0f
                    var lastX = down.position.x
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                    try {
                        val beforeLongPress = withTimeoutOrNull(longPressTimeout) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "ENDED"
                                if (!change.pressed) {
                                    change.consume()
                                    if (!draggingTrackpad) {
                                        currentOnTap()
                                    }
                                    return@withTimeoutOrNull "ENDED"
                                }
                                val curX = change.position.x
                                if (!draggingTrackpad && abs(curX - down.position.x) > dragStartSlopPx) {
                                    draggingTrackpad = true
                                    return@withTimeoutOrNull "DRAG"
                                }
                                lastX = curX
                            }
                            "ENDED"
                        }

                        if (beforeLongPress == "ENDED") {
                            return@awaitEachGesture
                        }

                        if (beforeLongPress == null && !draggingTrackpad) {
                            currentOnLongPress()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                            }
                            return@awaitEachGesture
                        }

                        // Horizontal trackpad sliding
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val deltaX = change.position.x - lastX
                            lastX = change.position.x
                            dragAccum += deltaX
                            if (dragAccum >= stepPx) {
                                val steps = (dragAccum / stepPx).toInt()
                                dragAccum -= steps * stepPx
                                currentOnMoveCursorStep(steps)
                            } else if (dragAccum <= -stepPx) {
                                val steps = (dragAccum / stepPx).toInt()
                                dragAccum -= steps * stepPx
                                currentOnMoveCursorStep(steps)
                            }
                            change.consume()
                        }
                    } finally {
                        pressedState.value = false
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "Spasi"
                onClick {
                    currentOnTap()
                    true
                }
                onLongClick {
                    currentOnLongPress()
                    true
                }
            }
            .testTag("key_spacebar"),
        contentAlignment = Alignment.Center
    ) {}
}

@Composable
private fun ClipboardPanel(
    palette: KeyboardPalette,
    items: List<ClipboardItem>,
    enabled: Boolean,
    statusMessage: String?,
    height: Dp,
    onPasteItem: (ClipboardItem) -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Papan Klip",
                    color = palette.keyText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage,
                        color = palette.accentBg,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.keyBg)
                            .border(0.75.dp, palette.borderColor, RoundedCornerShape(10.dp))
                            .clickable { onPasteItem(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("clipboard_item_${item.id}"),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = item.preview,
                            color = palette.keyText,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.charCount > 200) {
                            Text(
                                text = "${item.charCount} karakter",
                                color = palette.keyHintText,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

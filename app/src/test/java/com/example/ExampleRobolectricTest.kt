package com.example

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs
import androidx.test.core.app.ApplicationProvider
import com.example.data.FontStyleConverter
import com.example.data.SettingsRepository
import com.example.ime.KetikKuKeyboardView
import com.example.ime.KeyboardController
import com.example.ime.TextFieldImeTarget
import com.example.model.ClipboardItem
import com.example.model.KeyboardHeightPreset
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KetikKuTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.ui.findActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readAppNameAndSerializeDriveBackupJson() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KetikKu", appName)

        assertEquals(listOf("Normal"), KeyboardHeightPreset.entries.map { it.label })
        assertEquals(KeyboardHeightPreset.NORMAL, KeyboardHeightPreset.fromName("SHORT"))
        assertEquals(KeyboardHeightPreset.NORMAL, KeyboardHeightPreset.fromName("TALL"))

        val repo = SettingsRepository(context)
        val original = KeyboardSettings(
            heightPreset = KeyboardHeightPreset.NORMAL.name,
            floatingModeEnabled = true,
            floatingPosX = 0.35f,
            floatingPosY = 0.60f,
            autoSyncDriveEnabled = true,
            driveFolderName = "KetikKu",
            updatedAtMillis = 1794843619202L
        )
        val json = repo.encodeSettingsBackupJson(original)
        val decoded = repo.decodeSettingsBackupJson(json)
        assertNotNull(decoded)
        assertEquals(original.heightPreset, decoded?.settings?.heightPreset)
        assertEquals(original.updatedAtMillis, decoded?.updatedAtMillis)

        val entries = listOf(
            PersonalDictionaryEntry(
                id = "local_1",
                word = "KetikKu Offline",
                shortcut = "kko",
                updatedAtMillis = 999L
            )
        )
        val fullJson = repo.encodeFullBackupJson(original, entries, exportedAtMillis = 123456L)
        val decodedFull = repo.decodeFullBackupJson(fullJson)
        assertNotNull(decodedFull)
        assertEquals("KetikKu", decodedFull?.appName)
        assertEquals(1, decodedFull?.dictionary?.size)
        val importResult = repo.importLocalBackupJson(fullJson)
        assertTrue(importResult.isSuccess)
    }

    @Test
    fun keyboardView_rendersKeysWithoutEmojiOrGifAndTypesIntoTarget() {
        var value = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    applySystemBarInsets = true,
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Number row 1..0 removed; Q..P show digit hints and , shows '.' hint
        assertTrue(composeRule.onAllNodesWithTag("key_1").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("key_0").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("key_.").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("key_q").assertIsDisplayed()
        composeRule.onNodeWithTag("key_ctrl").assertIsDisplayed()
        composeRule.onNodeWithTag("key_spacebar").assertIsDisplayed()
        composeRule.onNodeWithTag("key_,").assertIsDisplayed()
        composeRule.onNodeWithTag("key_symbols").assertIsDisplayed()
        composeRule.onNodeWithTag("ime_fixed_toolbar").assertIsDisplayed()
        val clipToolbarBounds = composeRule.onNodeWithTag("ime_clipboard_button").fetchSemanticsNode().boundsInRoot
        val floatToolbarBounds = composeRule.onNodeWithTag("ime_floating_mode_toggle").fetchSemanticsNode().boundsInRoot
        val settingsToolbarBounds = composeRule.onNodeWithTag("ime_settings_button").fetchSemanticsNode().boundsInRoot
        assertTrue(clipToolbarBounds.right <= floatToolbarBounds.left + 1f)
        assertTrue(floatToolbarBounds.right <= settingsToolbarBounds.left + 1f)

        val chipNodes = composeRule.onAllNodesWithTag("suggestion_chip").fetchSemanticsNodes()
        assertEquals(3, chipNodes.size)
        val chipWidths = chipNodes.map { it.boundsInRoot.width }
        assertTrue(
            "All 3 suggestion slots should have equal width: $chipWidths",
            abs(chipWidths[0] - chipWidths[1]) < 2f && abs(chipWidths[1] - chipWidths[2]) < 2f
        )
        // Verify no Emoji, GIF, One-Handed, Theme, Language Switch, or top-bar chevron toggle controls exist
        assertTrue(composeRule.onAllNodesWithTag("ime_toolbar_toggle").fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Emoji", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("GIF", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Mode Satu Tangan", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Ganti Tema", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Ganti Bahasa", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(composeRule.onAllNodesWithText("?!").fetchSemanticsNodes().isEmpty())

        // Tap ',' outputs ',', Shift+Q outputs 'Q'
        composeRule.onNodeWithTag("key_,").performClick()
        composeRule.waitForIdle()
        assertEquals(",", value.text)

        composeRule.onNodeWithTag("key_q").performClick()
        composeRule.waitForIdle()
        assertEquals(",q", value.text)

        composeRule.onNodeWithContentDescription("Shift").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_Q").performClick()
        composeRule.waitForIdle()
        assertEquals(",qQ", value.text)
    }

    @Test
    fun keyboardView_longPressSymbolPopupAppearsNearKeyAndSelectsDigitsAndPeriod() {
        var value = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Long-press 'e' pops up '3' directly above key 'e'
        composeRule.onNodeWithTag("key_e").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_longpress_popup").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("é").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("è").fetchSemanticsNodes().isEmpty())
        val eBounds = composeRule.onNodeWithTag("key_e").fetchSemanticsNode().boundsInRoot
        val ePopupBounds = composeRule.onNodeWithTag("key_longpress_popup").fetchSemanticsNode().boundsInRoot
        assertTrue("Popup should sit directly above key e", ePopupBounds.bottom <= eBounds.top + 4f)
        assertTrue("Popup should stay horizontally near key e", abs(ePopupBounds.left - eBounds.left) < 100f)

        composeRule.onNodeWithTag("key_e").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("3", value.text)

        // Long-press ',' pops up '.' directly above ',' and inserts '.'
        composeRule.onNodeWithTag("key_,").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_longpress_popup").assertIsDisplayed()
        val commaBounds = composeRule.onNodeWithTag("key_,").fetchSemanticsNode().boundsInRoot
        val commaPopupBounds = composeRule.onNodeWithTag("key_longpress_popup").fetchSemanticsNode().boundsInRoot
        assertTrue("Popup should sit directly above key ','", commaPopupBounds.bottom <= commaBounds.top + 4f)

        composeRule.onNodeWithTag("key_,").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("3.", value.text)

        // Long-press 'a', 's', 'd' inserts '@', '%', '&'
        listOf("a" to "@", "s" to "%", "d" to "&").forEach { (keyChar, sym) ->
            composeRule.onNodeWithTag("key_$keyChar").performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(600L)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("key_longpress_popup").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText(sym).fetchSemanticsNodes().isNotEmpty())
            composeRule.onNodeWithTag("key_$keyChar").performTouchInput { up() }
            composeRule.waitForIdle()
        }
        assertEquals("3.@%&", value.text)
    }

    @Test
    fun keyboardView_monospaceToolIsRemovedAndSpacebarStaysBlank() {
        var value = TextFieldValue("")
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(serifMiringEnabled = true),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Monospace shortcut button is completely removed & spacebar displays NO text
        assertTrue(composeRule.onAllNodesWithTag("ime_arial_italic_shortcut").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Monospace", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("KetikKu", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Monospace", substring = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("key_q").performClick()
        composeRule.waitForIdle()
        assertEquals("q", value.text)
    }

    @Test
    fun keyboardView_longPressSpacebarTriggersInputMethodPicker() {
        var value = TextFieldValue("")
        var pickerShownCount = 0
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {},
                    onShowInputMethodPicker = { pickerShownCount++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_spacebar").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, pickerShownCount)
        assertEquals("", value.text)
    }

    @Test
    fun keyboardView_clipboardUsesGridModeAndClearAllButton() {
        var value = TextFieldValue("")
        var clearedAllCount = 0
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )
        val items = listOf(
            ClipboardItem(id = "c1", preview = "Klip Pertama", charCount = 12, timestampMillis = 200L),
            ClipboardItem(id = "c2", preview = "Klip Kedua", charCount = 10, timestampMillis = 100L)
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = items,
                    onPasteClipboard = { text -> controller.pasteText(text, KeyboardSettings(), emptyList()) },
                    onCopySelectionToClipboard = {},
                    onClearAllClipboard = { clearedAllCount++ },
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Papan Klip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clipboard_grid").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Klip Pertama").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Klip Kedua").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Sematkan").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Hapus klip").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("clipboard_clear_all").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clearedAllCount)
    }

    @Test
    fun dictionaryScreen_searchesAndEditsWordAndShortcut() {
        val entries = listOf(
            PersonalDictionaryEntry(
                id = "d1",
                word = "Terima kasih",
                shortcut = "ttd",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 10L
            ),
            PersonalDictionaryEntry(
                id = "d2",
                word = "Sedang di jalan",
                shortcut = "omw",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 20L
            )
        )
        var savedWord = ""
        var savedShortcut = ""
        var savedExistingId: String? = null

        composeRule.setContent {
            KetikKuTheme {
                DictionaryScreen(
                    settings = KeyboardSettings(),
                    dictionary = entries,
                    onUpsertEntry = { word, shortcut, _, existingId ->
                        savedWord = word
                        savedShortcut = shortcut
                        savedExistingId = existingId
                    },
                    onDeleteEntry = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("dict_filter_all").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Bahasa Indonesia").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("dictionary_search_input").performTextInput("omw")
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("dictionary_item_d1").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("dictionary_item_d2").assertIsDisplayed()

        // Tap item to open Edit Kata & Pintasan dialog
        composeRule.onNodeWithTag("dictionary_item_d2").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Bahasa Kamus").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("dictionary_word_input").performTextClearance()
        composeRule.onNodeWithTag("dictionary_word_input").performTextInput("Otw sekarang")
        composeRule.onNodeWithTag("dictionary_shortcut_input").performTextClearance()
        composeRule.onNodeWithTag("dictionary_shortcut_input").performTextInput("otw")
        composeRule.onNodeWithTag("save_dictionary_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Otw sekarang", savedWord)
        assertEquals("otw", savedShortcut)
        assertEquals("d2", savedExistingId)
    }

    @Test
    fun settingsScreen_showsOnlyImeStatusDictionaryAndDriveAndOmitsRemovedSections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = MainViewModel(SettingsRepository(context))

        composeRule.setContent {
            KetikKuTheme {
                SettingsScreen(
                    viewModel = vm,
                    settings = KeyboardSettings(),
                    dictionary = emptyList(),
                    imeStatus = ImeActivationStatus.ACTIVE_DEFAULT,
                    onOpenDictionary = {},
                    onOpenDriveSyncSheet = {}
                )
            }
        }

        composeRule.waitForIdle()
        val removedLabels = listOf(
            "Tata Letak Keyboard",
            "Tinggi Keyboard",
            "Mode Mengambang",
            "Respons & Papan Klip",
            "Riwayat Papan Klip",
            "Getar Saat Ditekan",
            "Panduan Gestur Cepat",
            "Tema Keyboard",
            "Mode Satu Tangan",
            "Tombol Ganti Bahasa",
            "Font Serif Miring",
            "Bahasa Utama",
            "Pendek",
            "Agak Pendek",
            "Agak Tinggi",
            "Metode 2"
        )
        removedLabels.forEach { label ->
            assertTrue(
                "Expected '$label' to be omitted from SettingsScreen",
                composeRule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty()
            )
        }
        assertTrue(composeRule.onAllNodesWithTag("height_chip_normal").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_floating_mode").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_clipboard_history").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_haptic").fetchSemanticsNodes().isEmpty())

        assertTrue(composeRule.onAllNodesWithTag("open_playground_button").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("enable_ime_button").assertIsDisplayed()
        composeRule.onNodeWithTag("select_ime_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_typing_field").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_typing_field").performTextInput("Uji pilih teks")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_dictionary_menu_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_drive_sync_sheet_button").assertIsDisplayed()
    }

    @Test
    fun keyboardView_backspaceTapDeletesOneAndHoldRepeatsContinuously() {
        val initial = "Halo KetikKu!"
        var value = TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // No swipe gesture pill anymore
        assertTrue(composeRule.onAllNodesWithTag("backspace_gesture_pill").fetchSemanticsNodes().isEmpty())

        // Single tap deletes 1 character
        composeRule.onNodeWithTag("key_backspace").performClick()
        composeRule.waitForIdle()
        assertEquals("Halo KetikKu", value.text)

        // Press and hold past initial delay deletes multiple characters at normal speed
        composeRule.onNodeWithTag("key_backspace").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(650L)
        composeRule.onNodeWithTag("key_backspace").performTouchInput {
            up()
        }
        composeRule.waitForIdle()
        assertTrue(
            "Expected continuous hold-to-delete to remove multiple characters, remaining: '${value.text}'",
            value.text.length <= "Halo Ketik".length
        )
    }

    @Test
    fun keyboardView_directCtrlCombosAndDoubleTapLockWorkOnMainKeyboard() {
        var value = TextFieldValue("Halo", androidx.compose.ui.text.TextRange(4))
        var latestCopied = ""
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = listOf(
                        ClipboardItem(id = "c1", preview = "KetikKu", charCount = 7, timestampMillis = 100L)
                    ),
                    onPasteClipboard = {},
                    onRequestCtrlPaste = {
                        controller.onCtrlPaste("KetikKu", KeyboardSettings(), emptyList())
                    },
                    onCopySelectionToClipboard = { latestCopied = it },
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Separate Ctrl panel button & shortcut panel do NOT exist
        assertTrue(composeRule.onAllNodesWithTag("ime_ctrl_panel_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("ctrl_undo_button").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("key_spacebar").performClick()
        composeRule.onNodeWithTag("key_a").performClick()
        composeRule.waitForIdle()
        assertEquals("Halo a", value.text)

        // Single tap Ctrl -> ARMED -> tap 'z' executes Undo and automatically returns Ctrl to OFF
        composeRule.onNodeWithTag("key_ctrl").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Ctrl aktif").assertIsDisplayed()
        composeRule.onNodeWithTag("ctrl_armed_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("key_z").performClick()
        composeRule.waitForIdle()
        assertEquals("Halo ", value.text)
        composeRule.onNodeWithContentDescription("Ctrl nonaktif").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("ctrl_armed_indicator", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Single tap Ctrl -> tap 'y' executes Redo
        composeRule.onNodeWithTag("key_ctrl").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_y").performClick()
        composeRule.waitForIdle()
        assertEquals("Halo a", value.text)

        // Double tap Ctrl locks Ctrl and keeps lock active across Select All + Copy, then third tap releases
        composeRule.onNodeWithTag("key_ctrl").performClick()
        composeRule.onNodeWithTag("key_ctrl").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Ctrl terkunci").assertIsDisplayed()
        composeRule.onNodeWithTag("ctrl_locked_indicator", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("key_a").performClick()
        composeRule.waitForIdle()
        assertEquals(0, value.selection.min)
        assertEquals("Halo a".length, value.selection.max)
        composeRule.onNodeWithTag("ctrl_locked_indicator", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("key_c").performClick()
        composeRule.waitForIdle()
        assertEquals("Halo a", latestCopied)
        composeRule.onNodeWithTag("ctrl_locked_indicator", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("key_x").performClick()
        composeRule.waitForIdle()
        assertEquals("", value.text)

        composeRule.onNodeWithTag("key_v").performClick()
        composeRule.waitForIdle()
        assertEquals("KetikKu", value.text)

        composeRule.onNodeWithTag("key_ctrl").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Ctrl nonaktif").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("ctrl_locked_indicator", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun appRoot_displaysSingleSimpleSettingsScreenAndDriveBottomSheetWithoutLogin() {
        composeRule.setContent {
            KetikKuTheme {
                KetikKuAppRoot()
            }
        }

        composeRule.waitForIdle()
        // No bottom navigation tabs and no mandatory login gate on initial screen
        assertTrue(composeRule.onAllNodesWithTag("nav_playground").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("nav_backup").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("drive_connect_google_button").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("enable_ime_button").assertIsDisplayed()
        composeRule.onNodeWithTag("select_ime_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_dictionary_menu_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_drive_sync_sheet_button").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("height_chip_normal").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_floating_mode").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_clipboard_history").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("toggle_haptic").fetchSemanticsNodes().isEmpty())

        // Tapping Drive sync card opens ModalBottomSheet with optional Google sign-in & no Method 2 local backup
        composeRule.onNodeWithTag("open_drive_sync_sheet_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_connect_google_button").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("local_export_json_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("local_import_json_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Metode 2", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun keyboardView_floatingModeHas70PercentWidthRemembersPositionAndTwoSuggestions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metrics = context.resources.displayMetrics
        val expectedWidthPx = minOf(metrics.widthPixels, metrics.heightPixels) * 0.70f

        var value = TextFieldValue("")
        var settingsState by androidx.compose.runtime.mutableStateOf(
            KeyboardSettings(
                floatingModeEnabled = true,
                floatingPosX = 0.20f,
                floatingPosY = 0.60f
            )
        )
        var savedPosX = -1f
        var savedPosY = -1f
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    KetikKuKeyboardView(
                        controller = controller,
                        settings = settingsState,
                        personalEntries = emptyList(),
                        clipboardItems = emptyList(),
                        onPasteClipboard = {},
                        onCopySelectionToClipboard = {},
                        onSaveFloatingPosition = { x, y ->
                            savedPosX = x
                            savedPosY = y
                            settingsState = settingsState.copy(floatingPosX = x, floatingPosY = y)
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val containerBoundsBefore = composeRule.onNodeWithTag("floating_keyboard_container")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Floating width ${containerBoundsBefore.width} should be 70% of vertical screen width ($expectedWidthPx)",
            abs(containerBoundsBefore.width - expectedWidthPx) < 4f
        )

        // Strictly 3 equal-width suggestion slots and NO resize handle
        assertEquals(3, composeRule.onAllNodesWithTag("suggestion_chip").fetchSemanticsNodes().size)
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Ubah ukuran", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Drag position handle up and right; verify position updates, persists, and width stays fixed
        composeRule.onNodeWithTag("floating_drag_handle").assertIsDisplayed().performTouchInput {
            down(center)
            moveBy(Offset(45f, -120f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue("Expected savedPosX >= 0f", savedPosX >= 0f)
        assertTrue("Expected savedPosY >= 0f", savedPosY >= 0f)
        assertTrue("Expected saved floatingPosX to increase", savedPosX > 0.20f)
        assertTrue("Expected saved floatingPosY to move upward", savedPosY < 0.60f)

        val containerBoundsAfter = composeRule.onNodeWithTag("floating_keyboard_container")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(containerBoundsAfter.left > containerBoundsBefore.left)
        assertTrue(containerBoundsAfter.top < containerBoundsBefore.top)
        assertTrue(
            "Floating width must remain unresizable",
            abs(containerBoundsAfter.width - expectedWidthPx) < 4f
        )
    }

    @Test
    fun googleDriveClient_syncsDictionaryOnlyWithoutSettings() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        val seenUploadPayloads = mutableListOf<String>()

        val remoteDictJson = repo.encodeDictionaryBackupJson(
            entries = listOf(
                PersonalDictionaryEntry(
                    id = "remote_1",
                    word = "Salam Drive",
                    shortcut = "sdr",
                    updatedAtMillis = 5000L
                )
            ),
            updatedAtMillis = 5000L
        )

        val mockHttp = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val (code, body) = when {
                    req.method == "GET" && url.contains("q=name") -> {
                        200 to """{"files":[{"id":"folder_1","name":"KetikKu","mimeType":"application/vnd.google-apps.folder"}]}"""
                    }
                    req.method == "GET" && url.contains("in+parents") -> {
                        200 to """{"files":[{"id":"dict_file_1","name":"ketikku_dictionary.json","mimeType":"application/json"}]}"""
                    }
                    req.method == "GET" && url.contains("/dict_file_1?alt=media") -> {
                        200 to remoteDictJson
                    }
                    req.method == "PATCH" || req.method == "POST" -> {
                        val buffer = okio.Buffer()
                        req.body?.writeTo(buffer)
                        val bodyText = buffer.readUtf8()
                        seenUploadPayloads.add(bodyText)
                        200 to """{"id":"dict_file_1"}"""
                    }
                    else -> 404 to "{}"
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(okhttp3.ResponseBody.Companion.let { _ -> body.let { b -> okhttp3.ResponseBody.create(null, b) } })
                    .build()
            }
            .build()

        val client = com.example.data.GoogleDriveClient(mockHttp)
        val localSettings = KeyboardSettings(
            heightPreset = KeyboardHeightPreset.NORMAL.name,
            serifMiringEnabled = true,
            updatedAtMillis = 999L
        )
        repo.upsertDictionaryEntry("Halo Lokal", "hl", KeyboardLanguage.INDONESIAN.code, existingId = "local_2")
        val localDict = repo.getCurrentDictionary()

        val outcome = client.syncTwoWay(
            accessToken = "fake-token",
            folderName = "KetikKu",
            shortcutFolderIdOverride = "",
            localSettings = localSettings,
            localDictionary = localDict,
            settingsRepository = repo
        ).getOrThrow()

        assertEquals(localSettings, outcome.syncedSettings)
        assertEquals("", outcome.settingsFileId)
        assertEquals("dict_file_1", outcome.dictionaryFileId)
        assertTrue(outcome.syncedDictionary.any { it.id == "remote_1" && it.word == "Salam Drive" })
        assertTrue(outcome.syncedDictionary.any { it.id == "local_2" && it.word == "Halo Lokal" })
        assertTrue(seenUploadPayloads.all { !it.contains("ketikku_settings.json") })
        assertTrue(seenUploadPayloads.any { it.contains("ketikku_dictionary.json") })
    }

    @Test
    fun settingsRepository_addClipboardText_autoSavesQualifyingGmailAndNumbersToDictionary() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)

        repo.addClipboardText("short@gmail.com")
        repo.addClipboardText("0812345678")
        val dictBefore = repo.getCurrentDictionary()
        assertTrue(dictBefore.none { it.word == "short@gmail.com" })
        assertTrue(dictBefore.none { it.word == "0812345678" })

        repo.addClipboardText("penggunapanjang2026@gmail.com")
        repo.addClipboardText("081234567890")
        repo.addClipboardText("penggunapanjang2026@gmail.com")

        val dictAfter = repo.getCurrentDictionary()
        assertEquals(1, dictAfter.count { !it.deleted && it.word == "penggunapanjang2026@gmail.com" })
        assertEquals(1, dictAfter.count { !it.deleted && it.word == "081234567890" })
    }

    @Test
    fun keyboardView_recentCopiedTextAppearsInSuggestionBarWithDismissXAndPasteAction() {
        var value = TextFieldValue("")
        var clipboardState by mutableStateOf(
            listOf(
                ClipboardItem(
                    id = "clip_1",
                    preview = "penggunapanjang2026@gmail.com",
                    charCount = 29,
                    timestampMillis = 500L
                )
            )
        )
        var dismissedIdState by mutableStateOf<String?>(null)
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = clipboardState,
                    dismissedClipboardId = dismissedIdState,
                    onPasteClipboard = { text -> controller.pasteText(text, KeyboardSettings(), emptyList()) },
                    onDismissRecentClipboard = { id -> dismissedIdState = id },
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Recently copied text shows inside suggestion bar alongside the X dismiss button
        composeRule.onNodeWithTag("recent_clipboard_suggestion_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("dismiss_recent_clipboard_button").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("penggunapanjang2026@gmail.com").fetchSemanticsNodes().isNotEmpty())

        // Tapping X dismisses the recently copied banner and restores regular suggestion chips
        composeRule.onNodeWithTag("dismiss_recent_clipboard_button").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("recent_clipboard_suggestion_chip").fetchSemanticsNodes().isEmpty())
        assertEquals(3, composeRule.onAllNodesWithTag("suggestion_chip").fetchSemanticsNodes().size)

        // Newly copied text with a fresh ID reappears in the suggestion column and tapping it pastes & dismisses
        clipboardState = listOf(
            ClipboardItem(id = "clip_2", preview = "081234567890", charCount = 12, timestampMillis = 600L)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recent_clipboard_suggestion_chip").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("081234567890", value.text)
        assertTrue(composeRule.onAllNodesWithTag("recent_clipboard_suggestion_chip").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun keyboardView_topBarSwappedAndFixedToolbarDoesNotShiftWhenSuggestionsChange() {
        var value = TextFieldValue("")
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        val slotBoundsWithSuggestions = composeRule.onNodeWithTag("suggestion_strip_slot")
            .fetchSemanticsNode().boundsInRoot
        val toolbarBoundsWithSuggestions = composeRule.onNodeWithTag("ime_fixed_toolbar")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Suggestions must be on the left and toolbar on the right",
            slotBoundsWithSuggestions.right <= toolbarBoundsWithSuggestions.left + 1f
        )

        // Hide suggestions (e.g. password field) and verify right toolbar stays at exact same coordinates
        controller.configureForEditor(
            isNumeric = false,
            isPassword = true,
            actionType = com.example.ime.EnterActionType.ENTER,
            settings = KeyboardSettings(),
            personalEntries = emptyList()
        )
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("suggestion_chip").fetchSemanticsNodes().size)

        val toolbarBoundsWithoutSuggestions = composeRule.onNodeWithTag("ime_fixed_toolbar")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(toolbarBoundsWithSuggestions.left - toolbarBoundsWithoutSuggestions.left) < 1f)
        assertTrue(abs(toolbarBoundsWithSuggestions.right - toolbarBoundsWithoutSuggestions.right) < 1f)
        assertTrue(abs(toolbarBoundsWithSuggestions.width - toolbarBoundsWithoutSuggestions.width) < 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun keyboardView_bottomRowOrderAndSpacebar80PercentTrimWithSymbolPanelGroups() {
        var value = TextFieldValue("")
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        val symBounds = composeRule.onNodeWithTag("key_symbols").fetchSemanticsNode().boundsInRoot
        val ctrlBounds = composeRule.onNodeWithTag("key_ctrl").fetchSemanticsNode().boundsInRoot
        val spaceBounds = composeRule.onNodeWithTag("key_spacebar").fetchSemanticsNode().boundsInRoot
        val commaBounds = composeRule.onNodeWithTag("key_,").fetchSemanticsNode().boundsInRoot
        val enterBounds = composeRule.onNodeWithTag("key_enter").fetchSemanticsNode().boundsInRoot

        assertTrue(symBounds.right <= ctrlBounds.left + 1f)
        assertTrue(ctrlBounds.right <= spaceBounds.left + 1f)
        assertTrue(spaceBounds.right <= commaBounds.left + 1f)
        assertTrue(commaBounds.right <= enterBounds.left + 1f)
        assertTrue(composeRule.onAllNodesWithText("Ω").fetchSemanticsNodes().isNotEmpty())
        // Spacebar trimmed 20% from its left (~3.84f weight vs total ~9.85f) and Ctrl occupies freed width
        assertTrue(ctrlBounds.width > symBounds.width)
        val totalRowWidth = enterBounds.right - symBounds.left
        val spaceRatio = spaceBounds.width / totalRowWidth
        assertTrue("Expected spacebar ratio ~0.39, got $spaceRatio", spaceRatio in 0.35f..0.43f)

        // Open Symbol panel ('Ω') and verify QWERTY-geometry rows (10 / 9 / ABC + 7 + Backspace), NO digits, NO removed symbols
        composeRule.onNodeWithTag("key_symbols").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("symbols_panel").assertIsDisplayed()
        listOf("Panah", "Centang", "Pemrograman", "Mata uang", "Matematika", "Emoji").forEach { removedTitle ->
            assertTrue(
                "Category header '$removedTitle' must NOT appear in symbols panel",
                composeRule.onAllNodesWithText(removedTitle).fetchSemanticsNodes().isEmpty()
            )
        }
        val row1 = listOf("←", "→", "↑", "↓", "✓", "✗", "×", "÷", "=", "#")
        val row2 = listOf("[", "]", "\\", "|", "<", ">", "Rp", "$", "€")
        val row3 = listOf("😂", "😍", "👍", "🙏", "!", "?", "@")
        assertEquals(10, row1.size)
        assertEquals(9, row2.size)
        assertEquals(7, row3.size)
        val all26 = row1 + row2 + row3
        assertEquals(26, all26.distinct().size)
        all26.forEach { sym ->
            composeRule.onNodeWithTag("key_$sym").assertIsDisplayed()
        }
        // Removed symbols °, ~, ^, }, { must NOT appear inside Symbol panel
        listOf("°", "~", "^", "}", "{").forEach { removedSym ->
            assertTrue(
                "Removed symbol $removedSym must not appear in symbol panel",
                composeRule.onAllNodesWithTag("key_$removedSym").fetchSemanticsNodes().isEmpty()
            )
        }
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Kembali ke Huruf")
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("key_backspace").assertIsDisplayed()

        // Verify row geometry (row1 above row2 above row3; row2 horizontally inset vs row1)
        val r1First = composeRule.onNodeWithTag("key_←").fetchSemanticsNode().boundsInRoot
        val r1Last = composeRule.onNodeWithTag("key_#").fetchSemanticsNode().boundsInRoot
        val r2First = composeRule.onNodeWithTag("key_[").fetchSemanticsNode().boundsInRoot
        val r3First = composeRule.onNodeWithTag("key_😂").fetchSemanticsNode().boundsInRoot
        val r3Last = composeRule.onNodeWithTag("key_@").fetchSemanticsNode().boundsInRoot
        assertTrue(r1First.right < r1Last.left)
        assertTrue(r2First.left > r1First.left + 2f)
        assertTrue(r1First.bottom <= r2First.top + 1f)
        assertTrue(r2First.bottom <= r3First.top + 1f)
        assertTrue(r3First.right < r3Last.left)

        for (digit in '0'..'9') {
            assertTrue(
                "Symbol panel must NOT contain digit $digit",
                composeRule.onAllNodesWithTag("key_$digit").fetchSemanticsNodes().isEmpty()
            )
        }

        // Tapping symbols (Rp, 😂) directly without scrolling inserts them, and tapping ABC in row 3 returns to QWERTY
        composeRule.onNodeWithTag("key_Rp").performClick()
        composeRule.waitForIdle()
        assertEquals("Rp", value.text)
        composeRule.onNodeWithTag("key_😂").performClick()
        composeRule.waitForIdle()
        assertEquals("Rp😂", value.text)
        composeRule.onAllNodesWithContentDescription("Kembali ke Huruf")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_q").assertIsDisplayed()
    }

    @Test
    fun keyboardView_longClipboardHidesFromSuggestionBarAndShowsPreviewPlusCountInGrid() {
        var value = TextFieldValue("")
        val longPreview = "Awal teks panjang " + "A".repeat(179) + "..."
        val items = listOf(
            ClipboardItem(id = "long_1", preview = longPreview, charCount = 25_000, timestampMillis = 999L)
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = KeyboardSettings(),
                    personalEntries = emptyList(),
                    clipboardItems = items,
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleSerifMiring = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Long copied text must NEVER be shown inside top suggestion bar
        assertTrue(composeRule.onAllNodesWithTag("recent_clipboard_suggestion_chip").fetchSemanticsNodes().isEmpty())
        assertEquals(3, composeRule.onAllNodesWithTag("suggestion_chip").fetchSemanticsNodes().size)

        // Open clipboard grid -> shows preview + character count badge
        composeRule.onNodeWithTag("ime_clipboard_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clipboard_item_long_1").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("25000 karakter").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun clipboardDatabaseHelper_storesAndLoadsMultiHundredThousandCharTextWithoutTruncation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.ClipboardDatabaseHelper(context)
        db.clearAll()

        val hugeText = "Awal teks super panjang 😀 " + "B".repeat(349_950) + " Akhir"
        val saved = db.saveClipboardText(rawText = hugeText, nowMillis = 12345L)
        assertNotNull(saved)

        val previews = db.getPreviews(limit = 15)
        assertEquals(1, previews.size)
        assertEquals(hugeText.length, previews.first().charCount)
        assertTrue(previews.first().preview.length <= com.example.data.TextChunker.PREVIEW_MAX_CHARS)
        assertTrue(previews.first().preview.endsWith("..."))

        val loadedFull = db.getFullText(previews.first().id)
        assertEquals(hugeText, loadedFull)
        db.clearAll()
        db.close()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun keyboardView_fastMultiTouchRolloverAndDriftDoNotDropKeys() {
        var value = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {}
                )
            }
        }

        composeRule.waitForIdle()
        val hNode = composeRule.onNodeWithTag("key_h").fetchSemanticsNode()
        val origin = hNode.boundsInRoot.topLeft
        val hPos = hNode.boundsInRoot.center - origin
        val aPos = composeRule.onNodeWithTag("key_a").fetchSemanticsNode().boundsInRoot.center - origin
        val lPos = composeRule.onNodeWithTag("key_l").fetchSemanticsNode().boundsInRoot.center - origin
        val oPos = composeRule.onNodeWithTag("key_o").fetchSemanticsNode().boundsInRoot.center - origin
        val spacePos = composeRule.onNodeWithTag("key_spacebar").fetchSemanticsNode().boundsInRoot.center - origin

        // Interleaved multi-touch rollover: finger 2 presses next key BEFORE finger 1 lifts,
        // plus realistic fast-thumb drift while lifting. Zero keys should be dropped.
        composeRule.onNodeWithTag("key_h").performTouchInput {
            down(pointerId = 0, position = hPos)
            down(pointerId = 1, position = aPos)
            up(pointerId = 0)
            down(pointerId = 2, position = lPos)
            moveTo(pointerId = 1, position = aPos + Offset(42f, 18f))
            up(pointerId = 1)
            down(pointerId = 3, position = oPos)
            up(pointerId = 2)
            up(pointerId = 3)
            // Rollover across spacebar too
            down(pointerId = 4, position = spacePos)
            up(pointerId = 4)
        }
        composeRule.waitForIdle()
        assertEquals("halo ", value.text)
    }

    @Test
    fun inputConnectionImeTarget_burstTypingSelectionUpdatesKeepLocalCacheWarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editable = android.text.Editable.Factory.getInstance().newEditable("")
        val dummyView = android.view.View(context)
        val ic = object : android.view.inputmethod.BaseInputConnection(dummyView, true) {
            override fun getEditable(): android.text.Editable = editable
        }
        val target = com.example.ime.KetikKuImeService.InputConnectionImeTarget(
            icProvider = { ic },
            onOpenSettings = {}
        )
        target.onSelectionUpdated(0, 0)

        // Rapid burst of 4 commits before any onUpdateSelection arrives back from app thread
        target.commitText("h")
        target.commitText("a")
        target.commitText("l")
        target.commitText("o")

        assertEquals(false, target.onSelectionUpdated(1, 1))
        assertEquals(false, target.onSelectionUpdated(2, 2))
        assertEquals(false, target.onSelectionUpdated(3, 3))
        assertEquals(false, target.onSelectionUpdated(4, 4))

        // Advance uptime past self-edit grace window -> genuine cursor jump invalidates cache
        org.robolectric.shadows.ShadowSystemClock.advanceBy(
            java.time.Duration.ofMillis(300)
        )
        assertEquals(true, target.onSelectionUpdated(0, 0))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun keyboardView_overlappingTwoFingerReversedUpCommitsInDownOrder() {
        var value = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { value },
                setValue = { value = it }
            )
        )

        composeRule.setContent {
            KetikKuTheme {
                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {}
                )
            }
        }

        composeRule.waitForIdle()
        val dNode = composeRule.onNodeWithTag("key_d").fetchSemanticsNode()
        val origin = dNode.boundsInRoot.topLeft
        val dPos = dNode.boundsInRoot.center - origin
        val iPos = composeRule.onNodeWithTag("key_i").fetchSemanticsNode().boundsInRoot.center - origin

        // Two-finger overlap where finger 1 releases BEFORE finger 0:
        // DOWN(d) -> DOWN(i) -> UP(i) -> UP(d) MUST commit "di", not "id".
        composeRule.onNodeWithTag("key_d").performTouchInput {
            down(pointerId = 0, position = dPos)
            down(pointerId = 1, position = iPos)
            up(pointerId = 1)
            up(pointerId = 0)
        }
        composeRule.waitForIdle()
        assertEquals("di", value.text)
    }

    @Test
    fun findActivity_unwrapsNestedContextWrappersAndReturnsNullForApplication() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val themedWrapper = ContextThemeWrapper(activity, android.R.style.Theme_DeviceDefault)
        val nestedWrapper = ContextWrapper(themedWrapper)

        assertSame(activity, activity.findActivity())
        assertSame(activity, themedWrapper.findActivity())
        assertSame(activity, nestedWrapper.findActivity())
        assertNull(ApplicationProvider.getApplicationContext<Context>().findActivity())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun driveBackupConnectedContent_rendersSimpleLayoutWithoutExtraButtonsOrHistory() {
        var autoSync = true
        var syncClicks = 0
        var disconnectClicks = 0

        composeRule.setContent {
            KetikKuTheme(darkTheme = false) {
                com.example.ui.screens.DriveBackupConnectedContent(
                    userEmail = "pengguna@gmail.com",
                    driveFolderName = "KetikKu",
                    autoSyncEnabled = autoSync,
                    isOnline = true,
                    pendingChanges = false,
                    statusHeadline = "Tersinkronisasi • Baru saja",
                    statusDotColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    isSyncing = false,
                    onToggleAutoSync = { autoSync = it },
                    onDisconnect = { disconnectClicks++ },
                    onSyncNow = { syncClicks++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_sync_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_auto_sync_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_disconnect_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_sync_now_button").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Folder Drive: KetikKu (Sinkron 2 arah)").fetchSemanticsNodes().isNotEmpty())

        // Removed clutter must not appear
        assertTrue(composeRule.onAllNodesWithText("Pulihkan Drive").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Lihat JSON").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Riwayat Sinkron Kamus").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("(Ubah)", substring = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("drive_sync_now_button").performClick()
        composeRule.onNodeWithTag("drive_disconnect_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, syncClicks)
        assertEquals(1, disconnectClicks)
    }
}


package com.example

import android.content.Context
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

        val repo = SettingsRepository(context)
        val original = KeyboardSettings(
            heightPreset = KeyboardHeightPreset.TALL.name,
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
                    onToggleArialItalic = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_1").assertIsDisplayed()
        composeRule.onNodeWithTag("key_0").assertIsDisplayed()
        composeRule.onNodeWithTag("key_q").assertIsDisplayed()
        composeRule.onNodeWithTag("key_spacebar").assertIsDisplayed()
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

        composeRule.onNodeWithTag("key_.").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("key_longpress_popup").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("key_q").performClick()
        composeRule.waitForIdle()
        assertEquals("q", value.text)

        composeRule.onNodeWithContentDescription("Shift").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_Q").performClick()
        composeRule.waitForIdle()
        assertEquals("qQ", value.text)
    }

    @Test
    fun keyboardView_longPressSymbolPopupAppearsNearKeyAndSlidesToSelect() {
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
                    onToggleArialItalic = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Verify rarely used accented symbols are absent when holding 'e'
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

        // Hold 's' and verify popup proximity + slide to select '$'
        composeRule.onNodeWithTag("key_s").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_longpress_popup").assertIsDisplayed()
        val sBounds = composeRule.onNodeWithTag("key_s").fetchSemanticsNode().boundsInRoot
        val sPopupBounds = composeRule.onNodeWithTag("key_longpress_popup").fetchSemanticsNode().boundsInRoot
        assertTrue("Popup should sit directly above key s", sPopupBounds.bottom <= sBounds.top + 4f)
        assertTrue("Popup should be close to key s horizontally", abs(sPopupBounds.left - sBounds.left) < 100f)

        // Slide right toward '$' (second option) and release
        composeRule.onNodeWithTag("key_s").performTouchInput {
            moveBy(Offset(46f, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("3$", value.text)
    }

    @Test
    fun keyboardView_arialItalicShortcutTogglesAndCommitsItalicText() {
        var value = TextFieldValue("")
        var settings = KeyboardSettings(arialItalicEnabled = false)
        var toggleCount = 0
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
                    settings = settings.copy(arialItalicEnabled = true),
                    personalEntries = emptyList(),
                    clipboardItems = emptyList(),
                    onPasteClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleArialItalic = {
                        toggleCount++
                        settings = settings.copy(arialItalicEnabled = !settings.arialItalicEnabled)
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ime_arial_italic_shortcut").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggleCount)

        assertTrue(
            composeRule.onAllNodesWithText("Bahasa Indonesia • Arial Italic")
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("key_q").performClick()
        composeRule.waitForIdle()
        assertEquals(FontStyleConverter.toArialItalic("q"), value.text)
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
                    onToggleArialItalic = {},
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
            ClipboardItem(id = "c1", text = "Klip Pertama", timestampMillis = 200L),
            ClipboardItem(id = "c2", text = "Klip Kedua", timestampMillis = 100L)
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
                    onToggleArialItalic = {}
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
    fun settingsScreen_omitsRemovedAndDefaultToggles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = MainViewModel(SettingsRepository(context))

        composeRule.setContent {
            KetikKuTheme {
                SettingsScreen(
                    viewModel = vm,
                    settings = KeyboardSettings(),
                    dictionary = emptyList(),
                    searchQuery = "",
                    imeStatus = ImeActivationStatus.ACTIVE_DEFAULT,
                    onOpenDictionary = {}
                )
            }
        }

        composeRule.waitForIdle()
        val removedLabels = listOf(
            "Tema Keyboard",
            "Mode Satu Tangan",
            "Tombol Ganti Bahasa",
            "Font Arial Italic",
            "Pengetikan Cerdas",
            "Koreksi Otomatis",
            "Bilah Saran Kata",
            "Ketik Geser",
            "Kapitalisasi Otomatis",
            "Spasi Ganda Jadi Titik",
            "Baris Angka Khusus",
            "Batas Tombol",
            "Kontrol Kursor di Spasi",
            "Geser Hapus Kata",
            "Suara Ketikan",
            "Pop-up Tombol"
        )
        removedLabels.forEach { label ->
            assertTrue(
                "Expected '$label' to be omitted from SettingsScreen",
                composeRule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty()
            )
        }

        composeRule.onNodeWithTag("open_dictionary_menu_button").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Bahasa Utama").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Tinggi Keyboard").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNode(hasScrollAction()).performScrollToIndex(3)
        composeRule.waitForIdle()

        removedLabels.forEach { label ->
            assertTrue(
                "Expected '$label' to be omitted from scrolled SettingsScreen",
                composeRule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty()
            )
        }
        composeRule.onNodeWithTag("toggle_clipboard_history").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_haptic").assertIsDisplayed()
    }
}

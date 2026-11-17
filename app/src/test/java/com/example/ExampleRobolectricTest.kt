package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsRepository
import com.example.ime.KetikKuKeyboardView
import com.example.ime.KeyboardController
import com.example.ime.TextFieldImeTarget
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemePreset
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
            themePreset = KeyboardThemePreset.COBALT_NIGHT.name,
            numberRowEnabled = false,
            autoSyncDriveEnabled = true,
            driveFolderName = "KetikKu",
            updatedAtMillis = 1794843619202L
        )
        val json = repo.encodeSettingsBackupJson(original)
        val decoded = repo.decodeSettingsBackupJson(json)
        assertNotNull(decoded)
        assertEquals(original.themePreset, decoded?.settings?.themePreset)
        assertEquals(original.numberRowEnabled, decoded?.settings?.numberRowEnabled)
        assertEquals(original.updatedAtMillis, decoded?.updatedAtMillis)
    }

    @Test
    fun keyboardView_rendersKeysWithoutEmojiOrGifAndTypesIntoTarget() {
        var value = TextFieldValue("")
        val settings = KeyboardSettings(numberRowEnabled = true)
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
                    onToggleClipboardPin = {},
                    onDeleteClipboardItem = {},
                    onClearUnpinnedClipboard = {},
                    onCopySelectionToClipboard = {},
                    onToggleOneHanded = {},
                    onCycleTheme = {},
                    onToggleLanguage = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_Q").assertIsDisplayed()
        composeRule.onNodeWithTag("key_spacebar").assertIsDisplayed()
        // Verify no Emoji or GIF controls exist
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Emoji", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("GIF", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithTag("key_Q").performClick()
        composeRule.waitForIdle()
        assertEquals("Q", value.text)
    }
}

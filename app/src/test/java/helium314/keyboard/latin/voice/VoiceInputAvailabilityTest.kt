// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.InputAttributes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class VoiceInputAvailabilityTest {
    private fun attributes(type: Int, options: String? = null): InputAttributes {
        val editor = EditorInfo().apply {
            inputType = type
            packageName = "example.notes"
            privateImeOptions = options
        }
        return InputAttributes(editor, false, "test.keyboard")
    }
    @Test fun localMicrophoneNeedsNoExternalVoiceIme() {
        assertTrue(attributes(InputType.TYPE_CLASS_TEXT).mShouldShowVoiceInputKey)
    }
    @Test fun passwordVariationsNeverShowMicrophone() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            assertFalse(attributes(InputType.TYPE_CLASS_TEXT or variation).mShouldShowVoiceInputKey)
        }
        assertFalse(attributes(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).mShouldShowVoiceInputKey)
    }
    @Test fun respectsApplicationsNoMicrophoneOption() {
        assertFalse(attributes(InputType.TYPE_CLASS_TEXT, "test.keyboard.noMicrophoneKey").mShouldShowVoiceInputKey)
    }
}

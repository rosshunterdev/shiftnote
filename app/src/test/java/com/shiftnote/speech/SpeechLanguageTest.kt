package com.shiftnote.speech

import android.speech.SpeechRecognizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {

    @Test
    fun `NZ English uses Australian English`() {
        assertEquals("en-AU", speechLanguageFor(Locale.forLanguageTag("en-NZ")))
    }

    @Test
    fun `other languages use the phone's own tag`() {
        assertEquals("en-US", speechLanguageFor(Locale.forLanguageTag("en-US")))
        assertEquals("en-GB", speechLanguageFor(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun `language not supported cannot be downloaded`() {
        assertEquals(
            SpeechProblem.LanguageMissing(canDownload = false),
            speechProblemFor(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, sdk = 36)
        )
    }

    @Test
    fun `language unavailable can be downloaded from Android 13`() {
        assertEquals(
            SpeechProblem.LanguageMissing(canDownload = true),
            speechProblemFor(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, sdk = 33)
        )
        assertEquals(
            SpeechProblem.LanguageMissing(canDownload = false),
            speechProblemFor(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, sdk = 32)
        )
    }

    @Test
    fun `missing permission and other errors map to their problems`() {
        assertEquals(
            SpeechProblem.NoPermission,
            speechProblemFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, sdk = 36)
        )
        assertEquals(
            SpeechProblem.Other,
            speechProblemFor(SpeechRecognizer.ERROR_CLIENT, sdk = 36)
        )
    }
}

package com.shiftnote.speech

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputTest {

    private val engine = FakeSpeechTranscriber()
    private var box = ""

    private fun TestScope.voiceInput() = VoiceInput(
        transcriber = engine,
        scope = backgroundScope,
        getText = { box },
        setText = { box = it }
    )

    @Test
    fun `appendSpoken into an empty box adds the words alone`() {
        assertEquals("went to the library", appendSpoken("", "went to the library"))
    }

    @Test
    fun `appendSpoken after text adds one space`() {
        assertEquals(
            "Picked up X at 9. went to the library",
            appendSpoken("Picked up X at 9.", "went to the library")
        )
    }

    @Test
    fun `appendSpoken after a space adds no extra space`() {
        assertEquals("Picked up X. went out", appendSpoken("Picked up X. ", "went out"))
    }

    @Test
    fun `appendSpoken after a new line adds no space`() {
        assertEquals("Picked up X.\nwent out", appendSpoken("Picked up X.\n", "went out"))
    }

    @Test
    fun `appendSpoken ignores blank words and trims spaces`() {
        assertEquals("Picked up X.", appendSpoken("Picked up X.", "   "))
        assertEquals("Picked up X. went out", appendSpoken("Picked up X.", "  went out  "))
    }

    @Test
    fun `appendSpoken handles several finished chunks in a row`() {
        val text = appendSpoken(appendSpoken("", "went to the library"), "then had lunch")
        assertEquals("went to the library then had lunch", text)
    }

    @Test
    fun `start turns listening on and starts the engine once`() = runTest {
        val voice = voiceInput()
        voice.start()
        assertTrue(voice.state.value.isListening)
        assertEquals(1, engine.startCalls)
    }

    @Test
    fun `start while listening is ignored`() = runTest {
        val voice = voiceInput()
        voice.start()
        voice.start()
        assertEquals(1, engine.startCalls)
    }

    @Test
    fun `live words show after existing text and are replaced by newer live words`() = runTest {
        box = "Picked up X at 9."
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Live("went to"))
        assertEquals("Picked up X at 9. went to", box)
        engine.emit(SpeechEvent.Live("went to the library"))
        assertEquals("Picked up X at 9. went to the library", box)
    }

    @Test
    fun `finished words are kept and new live words follow them`() = runTest {
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Live("went to the libary"))
        engine.emit(SpeechEvent.Finished("Went to the library."))
        engine.emit(SpeechEvent.Live("had"))
        assertEquals("Went to the library. had", box)
    }

    @Test
    fun `stop keeps the last words from the engine`() = runTest {
        val voice = voiceInput()
        voice.start()
        advanceTimeBy(1_000)
        engine.emit(SpeechEvent.Live("had lun"))
        voice.stopFromTap()
        assertEquals(1, engine.stopCalls)
        engine.emit(SpeechEvent.Finished("had lunch"))
        engine.emit(SpeechEvent.Ended)
        assertEquals("had lunch", box)
        assertFalse(voice.state.value.isListening)
        assertNull(voice.state.value.message)
    }

    @Test
    fun `stop finishes after the grace period if the engine never answers`() = runTest {
        val voice = voiceInput()
        voice.start()
        advanceTimeBy(1_000)
        engine.emit(SpeechEvent.Live("had lunch"))
        voice.stopFromTap()
        advanceTimeBy(VoiceInput.STOP_GRACE_MILLIS - 1)
        assertTrue(voice.state.value.isListening)
        advanceTimeBy(2)
        assertFalse(voice.state.value.isListening)
        assertEquals("had lunch", box)
    }

    @Test
    fun `a problem keeps the words and shows a message`() = runTest {
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Live("had lunch"))
        engine.emit(SpeechEvent.Problem(SpeechProblem.Other))
        assertEquals("had lunch", box)
        assertFalse(voice.state.value.isListening)
        assertEquals(VoiceMessage.STOPPED, voice.state.value.message)
    }

    @Test
    fun `each problem maps to its message`() = runTest {
        val cases = mapOf(
            SpeechProblem.NoPermission to VoiceMessage.NEED_MIC,
            SpeechProblem.LanguageMissing(canDownload = true) to VoiceMessage.NEED_DOWNLOAD,
            SpeechProblem.LanguageMissing(canDownload = false) to VoiceMessage.NOT_SET_UP,
            SpeechProblem.Other to VoiceMessage.STOPPED
        )
        val voice = voiceInput()
        for ((problem, message) in cases) {
            voice.start()
            engine.emit(SpeechEvent.Problem(problem))
            assertEquals(message, voice.state.value.message)
        }
    }

    @Test
    fun `sixty seconds with no words stops listening and keeps the words`() = runTest {
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Finished("had lunch"))
        advanceTimeBy(VoiceInput.SILENCE_TIMEOUT_MILLIS - 1)
        assertTrue(voice.state.value.isListening)
        advanceTimeBy(2)
        assertFalse(voice.state.value.isListening)
        assertEquals(VoiceMessage.SILENCE_STOP, voice.state.value.message)
        assertEquals("had lunch", box)
        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun `new words restart the silence clock`() = runTest {
        val voice = voiceInput()
        voice.start()
        advanceTimeBy(58_000)
        engine.emit(SpeechEvent.Live("had"))
        advanceTimeBy(58_000)
        assertTrue(voice.state.value.isListening)
    }

    @Test
    fun `a second tap straight after start keeps listening`() = runTest {
        val voice = voiceInput()
        voice.start()
        advanceTimeBy(100)
        voice.stopFromTap()
        assertTrue(voice.state.value.isListening)
        assertEquals(0, engine.stopCalls)
        advanceTimeBy(VoiceInput.DOUBLE_TAP_GUARD_MILLIS)
        voice.stopFromTap()
        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun `leaving the screen stops even straight after start`() = runTest {
        val voice = voiceInput()
        voice.start()
        voice.stop()
        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun `late events after the session ended are ignored`() = runTest {
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Finished("had lunch"))
        engine.emit(SpeechEvent.Problem(SpeechProblem.Other))
        engine.emit(SpeechEvent.Finished("had lunch"))
        engine.emit(SpeechEvent.Live("extra"))
        assertEquals("had lunch", box)
    }

    @Test
    fun `discard stops without writing more words`() = runTest {
        box = "Old note"
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Live("more words"))
        voice.discard()
        box = ""
        engine.emit(SpeechEvent.Finished("more words"))
        assertEquals("", box)
        assertFalse(voice.state.value.isListening)
        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun `denied mic shows the right message and the next start clears it`() = runTest {
        val voice = voiceInput()
        voice.onPermissionDenied(permanently = false)
        assertEquals(VoiceMessage.NEED_MIC, voice.state.value.message)
        voice.onPermissionDenied(permanently = true)
        assertEquals(VoiceMessage.NEED_MIC_SETTINGS, voice.state.value.message)
        voice.start()
        assertNull(voice.state.value.message)
    }

    @Test
    fun `download clears the message and asks the engine`() = runTest {
        val voice = voiceInput()
        voice.start()
        engine.emit(SpeechEvent.Problem(SpeechProblem.LanguageMissing(canDownload = true)))
        voice.requestLanguageDownload()
        assertNull(voice.state.value.message)
        assertEquals(1, engine.downloadCalls)
    }

    @Test
    fun `release stops listening and releases the engine`() = runTest {
        val voice = voiceInput()
        voice.start()
        voice.release()
        assertFalse(voice.state.value.isListening)
        assertTrue(engine.released)
    }
}

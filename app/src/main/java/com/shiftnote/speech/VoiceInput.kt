package com.shiftnote.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceMessage(val text: String) {
    NEED_MIC("ShiftNote needs the microphone to hear you."),
    NEED_MIC_SETTINGS("ShiftNote needs the microphone to hear you."),
    NEED_DOWNLOAD("Voice needs a one-time download of English speech."),
    NOT_SET_UP("Voice isn't set up on this phone. Use the keyboard mic instead."),
    SILENCE_STOP("Stopped listening. Tap to speak to carry on."),
    STOPPED("Voice stopped. Tap to speak to try again.")
}

data class VoiceState(
    val isListening: Boolean = false,
    val message: VoiceMessage? = null
)

/** Adds spoken words to the end of [existing], with one space between them when needed. */
fun appendSpoken(existing: String, spoken: String): String {
    val words = spoken.trim()
    if (words.isEmpty()) return existing
    if (existing.isEmpty() || existing.last().isWhitespace()) return existing + words
    return "$existing $words"
}

/**
 * Dictation into one text box: listening state, merging speech into the box,
 * the silence stop and the messages shown under the voice button.
 */
class VoiceInput(
    private val transcriber: SpeechTranscriber,
    private val scope: CoroutineScope,
    private val getText: () -> String,
    private val setText: (String) -> Unit
) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    // Box text plus finished words for this session
    private var committed = ""
    // Words still changing, shown after committed
    private var live = ""
    private var stopping = false

    private var silenceJob: Job? = null
    private var stopGraceJob: Job? = null
    private var tapGuardJob: Job? = null

    fun start() {
        if (_state.value.isListening) return
        committed = getText()
        live = ""
        stopping = false
        _state.value = VoiceState(isListening = true)
        tapGuardJob = scope.launch { delay(DOUBLE_TAP_GUARD_MILLIS) }
        restartSilenceTimer()
        transcriber.start { onEvent(it) }
    }

    /** Stop button tap. Ignored just after start, so a double tap keeps listening. */
    fun stopFromTap() {
        if (tapGuardJob?.isActive == true) return
        stop()
    }

    /** Asks the engine for its last words, then ends. Ends anyway if the engine doesn't answer. */
    fun stop() {
        if (!_state.value.isListening || stopping) return
        stopping = true
        silenceJob?.cancel()
        stopGraceJob = scope.launch {
            delay(STOP_GRACE_MILLIS)
            finish(null)
        }
        transcriber.stop()
    }

    /** Ends listening without writing anything more to the box. Used by New Note. */
    fun discard() {
        if (!_state.value.isListening) return
        endSession(null)
        transcriber.stop()
    }

    fun onPermissionDenied(permanently: Boolean) {
        _state.value = VoiceState(
            message = if (permanently) VoiceMessage.NEED_MIC_SETTINGS else VoiceMessage.NEED_MIC
        )
    }

    fun requestLanguageDownload() {
        _state.value = VoiceState()
        transcriber.requestLanguageDownload()
    }

    fun release() {
        discard()
        transcriber.release()
    }

    private fun onEvent(event: SpeechEvent) {
        if (!_state.value.isListening) return
        when (event) {
            is SpeechEvent.Live -> {
                live = event.text
                setText(appendSpoken(committed, live))
                if (!stopping) restartSilenceTimer()
            }
            is SpeechEvent.Finished -> {
                committed = appendSpoken(committed, event.text)
                live = ""
                setText(committed)
                if (!stopping) restartSilenceTimer()
            }
            is SpeechEvent.Problem -> finish(messageFor(event.problem))
            SpeechEvent.Ended -> finish(null)
        }
    }

    private fun finish(message: VoiceMessage?) {
        if (!_state.value.isListening) return
        committed = appendSpoken(committed, live)
        setText(committed)
        endSession(message)
    }

    private fun endSession(message: VoiceMessage?) {
        silenceJob?.cancel()
        stopGraceJob?.cancel()
        tapGuardJob?.cancel()
        live = ""
        stopping = false
        _state.value = VoiceState(message = message)
    }

    private fun restartSilenceTimer() {
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(SILENCE_TIMEOUT_MILLIS)
            finish(VoiceMessage.SILENCE_STOP)
            transcriber.stop()
        }
    }

    private fun messageFor(problem: SpeechProblem): VoiceMessage = when (problem) {
        SpeechProblem.NoPermission -> VoiceMessage.NEED_MIC
        is SpeechProblem.LanguageMissing ->
            if (problem.canDownload) VoiceMessage.NEED_DOWNLOAD else VoiceMessage.NOT_SET_UP
        SpeechProblem.Other -> VoiceMessage.STOPPED
    }

    companion object {
        const val SILENCE_TIMEOUT_MILLIS = 60_000L
        const val STOP_GRACE_MILLIS = 2_000L
        const val DOUBLE_TAP_GUARD_MILLIS = 600L
    }
}

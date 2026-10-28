package com.shiftnote.speech

sealed interface SpeechEvent {
    /** Words still changing. Replaces the previous Live text. */
    data class Live(val text: String) : SpeechEvent
    /** Final words for one chunk. Clears the Live text. */
    data class Finished(val text: String) : SpeechEvent
    /** The session stopped because of a problem. No Ended follows. */
    data class Problem(val problem: SpeechProblem) : SpeechEvent
    /** The session stopped normally. */
    data object Ended : SpeechEvent
}

sealed interface SpeechProblem {
    data object NoPermission : SpeechProblem
    data class LanguageMissing(val canDownload: Boolean) : SpeechProblem
    /** Busy, audio fault, anything else. */
    data object Other : SpeechProblem
}

/** Speech to text for dictation. Call every method on the main thread. */
interface SpeechTranscriber {
    fun isAvailable(): Boolean
    fun start(onEvent: (SpeechEvent) -> Unit)
    /** Delivers the last Finished words, then Ended. */
    fun stop()
    fun requestLanguageDownload()
    fun release()
}

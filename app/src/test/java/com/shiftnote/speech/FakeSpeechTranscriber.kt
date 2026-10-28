package com.shiftnote.speech

class FakeSpeechTranscriber(var available: Boolean = true) : SpeechTranscriber {
    var startCalls = 0
    var stopCalls = 0
    var downloadCalls = 0
    var released = false
    private var onEvent: ((SpeechEvent) -> Unit)? = null

    override fun isAvailable(): Boolean = available

    override fun start(onEvent: (SpeechEvent) -> Unit) {
        startCalls++
        this.onEvent = onEvent
    }

    override fun stop() {
        stopCalls++
    }

    override fun requestLanguageDownload() {
        downloadCalls++
    }

    override fun release() {
        released = true
    }

    /** Sends an event as the real engine would. */
    fun emit(event: SpeechEvent) {
        onEvent?.invoke(event)
    }
}

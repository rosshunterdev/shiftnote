package com.shiftnote.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Locale

/** On-device speech has no NZ English; Australian is the closest accent. */
fun speechLanguageFor(locale: Locale): String =
    if (locale.language == "en" && locale.country == "NZ") "en-AU" else locale.toLanguageTag()

fun speechProblemFor(error: Int, sdk: Int): SpeechProblem = when (error) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechProblem.NoPermission
    // Never available on-device, so a download can't help
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> SpeechProblem.LanguageMissing(canDownload = false)
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechProblem.LanguageMissing(
        canDownload = sdk >= Build.VERSION_CODES.TIRAMISU
    )
    else -> SpeechProblem.Other
}

/**
 * Speech to text with Android's strict on-device recogniser (Android 12+).
 * Keeps listening through pauses by starting a new session each time one ends,
 * until stop() is called. Call every method on the main thread.
 */
class AndroidSpeechTranscriber(
    private val context: Context,
    private val language: String = speechLanguageFor(Locale.getDefault()),
    useSegmentedSession: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
) : SpeechTranscriber {

    private val segmented =
        useSegmentedSession && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var recognizer: SpeechRecognizer? = null
    private var callback: ((SpeechEvent) -> Unit)? = null
    private var stopping = false

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun start(onEvent: (SpeechEvent) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isAvailable()) {
            onEvent(SpeechEvent.Problem(SpeechProblem.Other))
            return
        }
        callback = onEvent
        stopping = false
        obtainRecognizer().startListening(buildIntent())
    }

    override fun stop() {
        if (callback == null) return
        stopping = true
        recognizer?.stopListening()
    }

    override fun requestLanguageDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        obtainRecognizer().triggerModelDownload(buildIntent())
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
        callback = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun obtainRecognizer(): SpeechRecognizer =
        recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(
                RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
            )
            putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            if (segmented) {
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SEGMENT_SILENCE_MILLIS
                )
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { emit(SpeechEvent.Live(it)) }
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            firstResult(segmentResults)?.let { emit(SpeechEvent.Finished(it)) }
        }

        override fun onEndOfSegmentedSession() {
            continueOrEnd()
        }

        override fun onResults(results: Bundle?) {
            // In segmented mode the words already arrived through onSegmentResults.
            if (segmented) return
            firstResult(results)?.let { emit(SpeechEvent.Finished(it)) }
            continueOrEnd()
        }

        override fun onError(error: Int) {
            when {
                stopping -> end()
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> continueOrEnd()
                else -> {
                    Log.w(TAG, "speech error $error")
                    emit(SpeechEvent.Problem(speechProblemFor(error, Build.VERSION.SDK_INT)))
                    callback = null
                    // Start fresh next time: after errors such as the speech service
                    // restarting (e.g. after a language download) the old one stays broken
                    recognizer?.destroy()
                    recognizer = null
                }
            }
        }
    }

    private fun continueOrEnd() {
        if (stopping || callback == null) {
            end()
        } else {
            recognizer?.startListening(buildIntent())
        }
    }

    private fun end() {
        emit(SpeechEvent.Ended)
        callback = null
        stopping = false
    }

    private fun emit(event: SpeechEvent) {
        callback?.invoke(event)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "ShiftNoteSpeech"
        const val SEGMENT_SILENCE_MILLIS = 30_000L
    }
}

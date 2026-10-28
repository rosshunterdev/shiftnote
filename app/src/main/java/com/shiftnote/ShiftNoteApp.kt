package com.shiftnote

import android.app.Application
import com.shiftnote.data.db.AppDatabase
import com.shiftnote.data.prefs.UserPreferences
import com.shiftnote.inference.GeminiCloudRestructurer
import com.shiftnote.inference.GemmaRestructurer
import com.shiftnote.inference.NoteRestructurer
import com.shiftnote.inference.PromptAssembler
import com.shiftnote.speech.AndroidSpeechTranscriber
import com.shiftnote.speech.SpeechTranscriber

class ShiftNoteApp : Application() {
    lateinit var container: AppContainer
        private set

    private var cachedGemma: GemmaRestructurer? = null

    override fun onCreate() {
        super.onCreate()
        val userPreferences = UserPreferences(this)
        container = AppContainer(
            database = AppDatabase.getInstance(this),
            userPreferences = userPreferences,
            noteRestructurerFactory = { createRestructurer(userPreferences) },
            promptAssembler = PromptAssembler(),
            // Pixel 8a ignores segmented sessions (risk check 2026-10-21)
            speechTranscriberFactory = {
                AndroidSpeechTranscriber(this, useSegmentedSession = false)
            }
        )
    }

    private fun createRestructurer(prefs: UserPreferences): NoteRestructurer {
        return when (prefs.inferenceMode) {
            UserPreferences.MODE_CLOUD -> GeminiCloudRestructurer(prefs.apiKey)
            else -> cachedGemma ?: GemmaRestructurer(this).also { cachedGemma = it }
        }
    }
}

class AppContainer(
    val database: AppDatabase,
    val userPreferences: UserPreferences,
    val noteRestructurerFactory: () -> NoteRestructurer,
    val promptAssembler: PromptAssembler,
    val speechTranscriberFactory: () -> SpeechTranscriber
)

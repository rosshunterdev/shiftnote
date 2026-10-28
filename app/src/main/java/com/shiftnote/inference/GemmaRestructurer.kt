package com.shiftnote.inference

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaRestructurer(private val context: Context) : NoteRestructurer {

    private var llmInference: LlmInference? = null

    override suspend fun isAvailable(): Boolean {
        return getModelFile().exists()
    }

    // Model load and generation block for ~20s+, so keep them off the main thread.
    override suspend fun restructure(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            val inference = getOrCreateInference()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(TEMPERATURE)
                .build()
            val response = LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                session.generateResponse()
            }
            if (response.isNullOrBlank()) {
                Result.failure(IllegalStateException("Model returned empty response"))
            } else {
                Result.success(response.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getOrCreateInference(): LlmInference {
        llmInference?.let { return it }

        val modelPath = getModelFile().absolutePath
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()

        return LlmInference.createFromOptions(context, options).also {
            llmInference = it
        }
    }

    private fun getModelFile(): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    companion object {
        const val MODEL_FILENAME = "gemma3-1b.task"

        // Input + output combined. MediaPipe defaults to 512, which truncates
        // long notes. Must not exceed the KV cache size the .task was built with.
        const val MAX_TOKENS = 1024

        // MediaPipe defaults to 0.8, which invented and flipped facts in testing.
        // Low values keep the rewrite close to the original wording.
        const val TEMPERATURE = 0.2f
    }
}

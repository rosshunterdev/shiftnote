package com.shiftnote.inference

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

class GeminiCloudRestructurer(private val apiKey: String) : NoteRestructurer {

    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotBlank()
    }

    override suspend fun restructure(prompt: String): Result<String> {
        return try {
            val model = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = TEMPERATURE
                    maxOutputTokens = MAX_OUTPUT_TOKENS
                }
            )
            val response = model.generateContent(prompt)
            val text = response.text
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Cloud model returned empty response"))
            } else {
                Result.success(text.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        // gemini-2.0-flash was retired (404), so this moved to the current Flash model.
        const val MODEL_NAME = "gemini-3.8-flash"

        // Low values keep the rewrite close to the original wording.
        const val TEMPERATURE = 0.3f

        // This model spends output tokens on thinking before it answers.
        // Lower limits hit MAX_TOKENS even on short notes.
        const val MAX_OUTPUT_TOKENS = 8192
    }
}

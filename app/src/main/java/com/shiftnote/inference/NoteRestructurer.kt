package com.shiftnote.inference

interface NoteRestructurer {
    suspend fun restructure(prompt: String): Result<String>
    suspend fun isAvailable(): Boolean
}

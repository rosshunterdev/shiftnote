package com.shiftnote.ui.settings

import androidx.lifecycle.ViewModel
import com.shiftnote.data.prefs.UserPreferences
import com.shiftnote.inference.ImproveStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _role = MutableStateFlow(userPreferences.role)
    val role: StateFlow<String> = _role.asStateFlow()

    private val _generalContext = MutableStateFlow(userPreferences.generalContext)
    val generalContext: StateFlow<String> = _generalContext.asStateFlow()

    private val _improveStyle = MutableStateFlow(userPreferences.improveStyle)
    val improveStyle: StateFlow<ImproveStyle> = _improveStyle.asStateFlow()

    private val _extraInstructions = MutableStateFlow(userPreferences.extraInstructions)
    val extraInstructions: StateFlow<String> = _extraInstructions.asStateFlow()

    private val _inferenceMode = MutableStateFlow(userPreferences.inferenceMode)
    val inferenceMode: StateFlow<String> = _inferenceMode.asStateFlow()

    private val _apiKey = MutableStateFlow(userPreferences.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun saveRole(value: String) {
        _role.value = value
        userPreferences.role = value
    }

    fun saveGeneralContext(value: String) {
        _generalContext.value = value
        userPreferences.generalContext = value
    }

    fun saveImproveStyle(value: ImproveStyle) {
        _improveStyle.value = value
        userPreferences.improveStyle = value
    }

    fun saveExtraInstructions(value: String) {
        _extraInstructions.value = value
        userPreferences.extraInstructions = value
    }

    fun saveInferenceMode(value: String) {
        _inferenceMode.value = value
        userPreferences.inferenceMode = value
    }

    fun saveApiKey(value: String) {
        _apiKey.value = value
        userPreferences.apiKey = value
    }
}

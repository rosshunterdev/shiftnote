package com.shiftnote.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiftnote.data.db.ClientEntity
import com.shiftnote.inference.ImproveStyle
import com.shiftnote.inference.NoteRestructurer
import com.shiftnote.inference.PromptAssembler
import com.shiftnote.inference.Redactor
import com.shiftnote.speech.SpeechTranscriber
import com.shiftnote.speech.VoiceInput
import com.shiftnote.speech.VoiceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Idle : MainUiState
    data object Processing : MainUiState
    data class RedactedPreview(
        val redactedPrompt: String,
        val mapping: Map<String, String>
    ) : MainUiState
    data class Result(val text: String) : MainUiState
    data class Error(val message: String) : MainUiState
}

class MainViewModel(
    clientsFlow: Flow<List<ClientEntity>>,
    private val noteRestructurerFactory: () -> NoteRestructurer,
    private val promptAssembler: PromptAssembler,
    private val getRole: () -> String,
    private val getGeneralContext: () -> String,
    private val getImproveStyle: () -> ImproveStyle,
    private val getExtraInstructions: () -> String,
    private val getSelectedClientId: () -> Long,
    private val setSelectedClientId: (Long) -> Unit,
    private val getClientById: suspend (Long) -> ClientEntity?,
    private val isCloudMode: () -> Boolean = { false },
    speechTranscriber: SpeechTranscriber
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _clients = MutableStateFlow<List<ClientEntity>>(emptyList())
    val clients: StateFlow<List<ClientEntity>> = _clients.asStateFlow()

    private val _selectedClientId = MutableStateFlow(getSelectedClientId())
    val selectedClientId: StateFlow<Long> = _selectedClientId.asStateFlow()

    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText.asStateFlow()

    private val _coverContext = MutableStateFlow("")
    val coverContext: StateFlow<String> = _coverContext.asStateFlow()

    private val voiceInput = VoiceInput(
        transcriber = speechTranscriber,
        scope = viewModelScope,
        getText = { _noteText.value },
        setText = { _noteText.value = it }
    )
    val voiceState: StateFlow<VoiceState> = voiceInput.state
    val isVoiceAvailable: Boolean = speechTranscriber.isAvailable()

    private var pendingPrompt: String? = null
    private var pendingMapping: Map<String, String>? = null

    init {
        viewModelScope.launch {
            clientsFlow.collect { _clients.value = it }
        }
    }

    fun onNoteChanged(text: String) {
        _noteText.value = text
    }

    fun onClientSelected(id: Long) {
        _selectedClientId.value = id
        setSelectedClientId(id)
    }

    fun onCoverContextChanged(text: String) {
        _coverContext.value = text
    }

    fun startVoice() {
        voiceInput.start()
    }

    fun onVoiceStopTapped() {
        voiceInput.stopFromTap()
    }

    /** Leaving the screen or the app. Not guarded against double taps. */
    fun stopVoice() {
        voiceInput.stop()
    }

    fun onMicDenied(permanently: Boolean) {
        voiceInput.onPermissionDenied(permanently)
    }

    fun downloadVoiceLanguage() {
        voiceInput.requestLanguageDownload()
    }

    fun restructure() {
        if (voiceState.value.isListening) return
        viewModelScope.launch {
            _uiState.value = MainUiState.Processing

            val restructurer = noteRestructurerFactory()

            if (!restructurer.isAvailable()) {
                _uiState.value = MainUiState.Error(
                    if (isCloudMode()) "API key not set. Add it in Settings."
                    else "On-device model not found. Push the model file to the device."
                )
                return@launch
            }

            val clientId = _selectedClientId.value
            val client = if (clientId > 0) getClientById(clientId) else null

            val clientGoals = client?.activeGoals ?: ""
            val clientContext = if (clientId == COVER_CLIENT_ID) {
                _coverContext.value
            } else {
                client?.extraContext ?: ""
            }

            val note = _noteText.value
            val generalContext = getGeneralContext()
            val style = getImproveStyle()
            val extraInstructions = getExtraInstructions()

            if (isCloudMode()) {
                val clientName = client?.name ?: ""
                val redactor = Redactor(clientName)

                val redactedNote = redactor.redact(note)
                val redactedGoals = redactor.redact(clientGoals)
                val redactedContext = redactor.redact(clientContext)
                val redactedGeneral = redactor.redact(generalContext)
                val redactedExtras = redactor.redact(extraInstructions)

                val redactedPrompt = promptAssembler.assemble(
                    role = getRole(),
                    style = style,
                    generalContext = redactedGeneral,
                    clientGoals = redactedGoals,
                    clientContext = redactedContext,
                    extraInstructions = redactedExtras,
                    note = redactedNote,
                    keepPlaceholders = true
                )

                pendingPrompt = redactedPrompt
                pendingMapping = redactor.getMapping()
                _uiState.value = MainUiState.RedactedPreview(
                    redactedPrompt = redactedPrompt,
                    mapping = redactor.getMapping()
                )
            } else {
                val prompt = promptAssembler.assemble(
                    role = getRole(),
                    style = style,
                    generalContext = generalContext,
                    clientGoals = clientGoals,
                    clientContext = clientContext,
                    extraInstructions = extraInstructions,
                    note = note,
                    keepPlaceholders = false
                )
                executeRestructure(restructurer, prompt, emptyMap())
            }
        }
    }

    fun confirmRedactedSend() {
        val prompt = pendingPrompt ?: return
        val mapping = pendingMapping ?: return

        viewModelScope.launch {
            _uiState.value = MainUiState.Processing
            val restructurer = noteRestructurerFactory()
            executeRestructure(restructurer, prompt, mapping)
            pendingPrompt = null
            pendingMapping = null
        }
    }

    fun cancelRedactedSend() {
        pendingPrompt = null
        pendingMapping = null
        _uiState.value = MainUiState.Idle
    }

    private suspend fun executeRestructure(
        restructurer: NoteRestructurer,
        prompt: String,
        mapping: Map<String, String>
    ) {
        val result = restructurer.restructure(prompt)
        _uiState.value = result.fold(
            onSuccess = { text ->
                val finalText = if (mapping.isNotEmpty()) {
                    Redactor.restore(text, mapping)
                } else {
                    text
                }
                MainUiState.Result(finalText)
            },
            onFailure = { MainUiState.Error(it.message ?: "Unknown error") }
        )
    }

    fun clearResult() {
        voiceInput.discard()
        _uiState.value = MainUiState.Idle
        _noteText.value = ""
        _coverContext.value = ""
    }

    override fun onCleared() {
        voiceInput.release()
    }

    companion object {
        const val COVER_CLIENT_ID = -2L
    }
}

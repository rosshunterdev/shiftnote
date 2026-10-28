package com.shiftnote.ui.main

import com.shiftnote.data.db.ClientEntity
import com.shiftnote.inference.ImproveStyle
import com.shiftnote.inference.NoteRestructurer
import com.shiftnote.inference.PromptAssembler
import com.shiftnote.speech.FakeSpeechTranscriber
import com.shiftnote.speech.SpeechEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeClients = MutableStateFlow(
        listOf(
            ClientEntity(id = 1, name = "Alice", activeGoals = "Independence"),
            ClientEntity(id = 2, name = "Bob", activeGoals = "Community access")
        )
    )

    private val fakeRestructurer = object : NoteRestructurer {
        var shouldFail = false
        var lastPrompt: String? = null

        override suspend fun restructure(prompt: String): Result<String> {
            lastPrompt = prompt
            return if (shouldFail) {
                Result.failure(RuntimeException("Model error"))
            } else {
                Result.success("Restructured: professional version")
            }
        }

        override suspend fun isAvailable(): Boolean = !shouldFail
    }

    private val fakeSpeech = FakeSpeechTranscriber()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val vm = createViewModel()
        assertEquals(MainUiState.Idle, vm.uiState.first())
    }

    @Test
    fun `restructure transitions to Processing then Result on device`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Did some tasks")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state is MainUiState.Result)
        assertEquals("Restructured: professional version", (state as MainUiState.Result).text)
    }

    @Test
    fun `restructure shows error on failure`() = runTest {
        fakeRestructurer.shouldFail = true
        val vm = createViewModel()
        vm.onNoteChanged("Did some tasks")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state is MainUiState.Error)
    }

    @Test
    fun `cloud mode shows RedactedPreview before sending`() = runTest {
        val vm = createViewModel(cloudMode = true)
        vm.onClientSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onNoteChanged("Took Alice to the shops")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state is MainUiState.RedactedPreview)
        val preview = state as MainUiState.RedactedPreview
        assertTrue(preview.redactedPrompt.contains("[CLIENT]"))
    }

    @Test
    fun `confirmRedactedSend completes cloud restructure`() = runTest {
        val vm = createViewModel(cloudMode = true)
        vm.onClientSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onNoteChanged("Took Alice to the shops")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.confirmRedactedSend()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state is MainUiState.Result)
    }

    @Test
    fun `cancelRedactedSend returns to Idle`() = runTest {
        val vm = createViewModel(cloudMode = true)
        vm.onClientSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onNoteChanged("Took Alice to the shops")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.cancelRedactedSend()
        assertEquals(MainUiState.Idle, vm.uiState.first())
    }

    @Test
    fun `clearResult returns to Idle`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Did some tasks")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.clearResult()
        assertEquals(MainUiState.Idle, vm.uiState.first())
    }

    @Test
    fun `on-device prompt has no placeholder rule`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Did some tasks")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(fakeRestructurer.lastPrompt!!.contains("exactly as written"))
    }

    @Test
    fun `cloud prompt keeps placeholder rule`() = runTest {
        val vm = createViewModel(cloudMode = true)
        vm.onClientSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onNoteChanged("Took Alice to the shops")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        val preview = vm.uiState.first() as MainUiState.RedactedPreview
        assertTrue(preview.redactedPrompt.contains("exactly as written"))
    }

    @Test
    fun `style change in settings applies to the next improve`() = runTest {
        var style = ImproveStyle.PARAGRAPH
        val vm = createViewModel(getStyle = { style })
        vm.onNoteChanged("Did some tasks")

        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(fakeRestructurer.lastPrompt!!.contains("\"Wellbeing:\""))

        style = ImproveStyle.SECTIONS
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeRestructurer.lastPrompt!!.contains("\"Wellbeing:\""))
    }

    @Test
    fun `extra instructions reach the on-device prompt`() = runTest {
        val vm = createViewModel(extras = "Use NZ spelling.")
        vm.onNoteChanged("Did some tasks")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            fakeRestructurer.lastPrompt!!.contains("Also follow these instructions: Use NZ spelling.")
        )
    }

    @Test
    fun `extra instructions are redacted in cloud mode`() = runTest {
        val vm = createViewModel(cloudMode = true, extras = "Alice prefers short notes.")
        vm.onClientSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onNoteChanged("Took Alice to the shops")
        vm.restructure()
        testDispatcher.scheduler.advanceUntilIdle()
        val preview = vm.uiState.first() as MainUiState.RedactedPreview
        assertTrue(
            preview.redactedPrompt.contains(
                "Also follow these instructions: [CLIENT] prefers short notes."
            )
        )
        assertFalse(preview.redactedPrompt.contains("Alice"))
    }

    @Test
    fun `spoken words are added to the end of the note`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Picked up X at 9.")
        vm.startVoice()
        fakeSpeech.emit(SpeechEvent.Finished("went to the library"))
        assertEquals("Picked up X at 9. went to the library", vm.noteText.value)
        assertTrue(vm.voiceState.value.isListening)
    }

    @Test
    fun `improve does nothing while listening`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Did some tasks")
        vm.startVoice()
        vm.restructure()
        testDispatcher.scheduler.runCurrent()
        assertEquals(MainUiState.Idle, vm.uiState.value)
        assertNull(fakeRestructurer.lastPrompt)
    }

    @Test
    fun `new note while listening clears the box and stops listening`() = runTest {
        val vm = createViewModel()
        vm.onNoteChanged("Old note")
        vm.startVoice()
        fakeSpeech.emit(SpeechEvent.Live("more words"))
        vm.clearResult()
        fakeSpeech.emit(SpeechEvent.Finished("more words"))
        assertEquals("", vm.noteText.value)
        assertFalse(vm.voiceState.value.isListening)
    }

    @Test
    fun `voice availability comes from the engine`() = runTest {
        fakeSpeech.available = false
        assertFalse(createViewModel().isVoiceAvailable)
    }

    private fun createViewModel(
        cloudMode: Boolean = false,
        getStyle: () -> ImproveStyle = { ImproveStyle.PARAGRAPH },
        extras: String = ""
    ): MainViewModel {
        return MainViewModel(
            clientsFlow = fakeClients,
            noteRestructurerFactory = { fakeRestructurer },
            promptAssembler = PromptAssembler(),
            getRole = { "Support worker" },
            getGeneralContext = { "" },
            getImproveStyle = getStyle,
            getExtraInstructions = { extras },
            getSelectedClientId = { -1L },
            setSelectedClientId = {},
            getClientById = { id ->
                fakeClients.value.find { it.id == id }
            },
            isCloudMode = { cloudMode },
            speechTranscriber = fakeSpeech
        )
    }
}

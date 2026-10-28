package com.shiftnote.ui.main

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftnote.AppContainer
import com.shiftnote.R
import com.shiftnote.data.db.ClientEntity
import com.shiftnote.data.prefs.UserPreferences
import com.shiftnote.speech.VoiceMessage

private const val NONE_ID = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    container: AppContainer,
    onNavigateToSettings: () -> Unit
) {
    val viewModel: MainViewModel = viewModel {
        MainViewModel(
            clientsFlow = container.database.clientDao().getAllClients(),
            noteRestructurerFactory = container.noteRestructurerFactory,
            promptAssembler = container.promptAssembler,
            getRole = { container.userPreferences.role },
            getGeneralContext = { container.userPreferences.generalContext },
            getImproveStyle = { container.userPreferences.improveStyle },
            getExtraInstructions = { container.userPreferences.extraInstructions },
            getSelectedClientId = { container.userPreferences.selectedClientId },
            setSelectedClientId = { container.userPreferences.selectedClientId = it },
            getClientById = { container.database.clientDao().getById(it) },
            isCloudMode = {
                container.userPreferences.inferenceMode == UserPreferences.MODE_CLOUD
            },
            speechTranscriber = container.speechTranscriberFactory()
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val selectedClientId by viewModel.selectedClientId.collectAsStateWithLifecycle()
    val noteText by viewModel.noteText.collectAsStateWithLifecycle()
    val coverContext by viewModel.coverContext.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoice()
        } else {
            // False after a second denial: Android won't show the prompt again
            val canAskAgain = (context as? Activity)
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                ?: false
            viewModel.onMicDenied(permanently = !canAskAgain)
        }
    }

    // Leaving the screen or backgrounding the app turns the mic off
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.stopVoice() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ShiftNote") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClientDropdown(
                clients = clients,
                selectedId = selectedClientId,
                onSelected = viewModel::onClientSelected
            )

            if (selectedClientId == MainViewModel.COVER_CLIENT_ID) {
                OutlinedTextField(
                    value = coverContext,
                    onValueChange = viewModel::onCoverContextChanged,
                    label = { Text("Context for this client") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = viewModel::onNoteChanged,
                readOnly = voiceState.isListening,
                label = { Text("Paste or type your notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                maxLines = 20
            )

            if (viewModel.isVoiceAvailable) {
                VoiceButton(
                    isListening = voiceState.isListening,
                    enabled = uiState !is MainUiState.Processing,
                    onClick = {
                        when {
                            voiceState.isListening -> viewModel.onVoiceStopTapped()
                            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED -> viewModel.startVoice()
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                voiceState.message?.let { message ->
                    VoiceMessageRow(
                        message = message,
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        },
                        onDownload = viewModel::downloadVoiceLanguage
                    )
                }
            }

            Button(
                onClick = { viewModel.restructure() },
                enabled = noteText.isNotBlank() &&
                    uiState !is MainUiState.Processing &&
                    !voiceState.isListening,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState is MainUiState.Processing) "Improving..."
                    else "Improve"
                )
            }

            if (uiState is MainUiState.Processing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (val state = uiState) {
                is MainUiState.Result -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Result",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SelectionContainer {
                                Text(text = state.text)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("ShiftNote", state.text)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy to Clipboard")
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearResult() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("New Note")
                        }
                    }
                }
                is MainUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is MainUiState.RedactedPreview -> {
                    RedactedPreviewDialog(
                        redactedText = state.redactedPrompt,
                        onConfirm = { viewModel.confirmRedactedSend() },
                        onCancel = { viewModel.cancelRedactedSend() }
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoiceButton(
    isListening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = if (isListening) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Icon(
            painter = painterResource(
                if (isListening) R.drawable.ic_stop else R.drawable.ic_mic
            ),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isListening) "Listening... tap to stop" else "Tap to speak")
    }
}

@Composable
private fun VoiceMessageRow(
    message: VoiceMessage,
    onOpenSettings: () -> Unit,
    onDownload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
        when (message) {
            VoiceMessage.NEED_MIC_SETTINGS -> OutlinedButton(onClick = onOpenSettings) {
                Text("Open settings")
            }
            VoiceMessage.NEED_DOWNLOAD -> OutlinedButton(onClick = onDownload) {
                Text("Download")
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientDropdown(
    clients: List<ClientEntity>,
    selectedId: Long,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = when (selectedId) {
        MainViewModel.COVER_CLIENT_ID -> "Other / Cover"
        NONE_ID -> "No client selected"
        else -> clients.find { it.id == selectedId }?.name ?: "No client selected"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Client") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            clients.forEach { client ->
                DropdownMenuItem(
                    text = { Text(client.name) },
                    onClick = {
                        onSelected(client.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Other / Cover") },
                onClick = {
                    onSelected(MainViewModel.COVER_CLIENT_ID)
                    expanded = false
                }
            )
        }
    }
}

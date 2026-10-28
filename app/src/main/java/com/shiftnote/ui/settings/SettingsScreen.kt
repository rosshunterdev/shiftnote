package com.shiftnote.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftnote.AppContainer
import com.shiftnote.data.prefs.UserPreferences
import com.shiftnote.inference.ImproveStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigateToClients: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.userPreferences)
    }

    val role by viewModel.role.collectAsStateWithLifecycle()
    val generalContext by viewModel.generalContext.collectAsStateWithLifecycle()
    val improveStyle by viewModel.improveStyle.collectAsStateWithLifecycle()
    val extraInstructions by viewModel.extraInstructions.collectAsStateWithLifecycle()
    val inferenceMode by viewModel.inferenceMode.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Your Profile", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = role,
                onValueChange = viewModel::saveRole,
                label = { Text("Role") },
                placeholder = { Text("e.g. Community support worker") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "Improve style", style = MaterialTheme.typography.bodyLarge)

            Column(modifier = Modifier.selectableGroup()) {
                ImproveStyleOption(
                    label = "Paragraph",
                    selected = improveStyle == ImproveStyle.PARAGRAPH,
                    onClick = { viewModel.saveImproveStyle(ImproveStyle.PARAGRAPH) }
                )
                ImproveStyleOption(
                    label = "Sections",
                    selected = improveStyle == ImproveStyle.SECTIONS,
                    onClick = { viewModel.saveImproveStyle(ImproveStyle.SECTIONS) }
                )
            }

            OutlinedTextField(
                value = generalContext,
                onValueChange = viewModel::saveGeneralContext,
                label = { Text("General context") },
                placeholder = { Text("Standing context that applies to all clients") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(text = "Clients", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = onNavigateToClients,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Clients")
            }

            HorizontalDivider()

            Text(text = "Inference", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = inferenceMode == UserPreferences.MODE_ON_DEVICE,
                    onClick = { viewModel.saveInferenceMode(UserPreferences.MODE_ON_DEVICE) },
                    label = { Text("On-device") }
                )
                FilterChip(
                    selected = inferenceMode == UserPreferences.MODE_CLOUD,
                    onClick = { viewModel.saveInferenceMode(UserPreferences.MODE_CLOUD) },
                    label = { Text("Cloud (pseudonymised)") }
                )
            }

            if (inferenceMode == UserPreferences.MODE_CLOUD) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = viewModel::saveApiKey,
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste key from Google AI Studio") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Cloud mode strips all names before sending to Gemini Flash. " +
                        "Get a free API key from Google AI Studio (aistudio.google.com).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text(text = "Advanced", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = extraInstructions,
                onValueChange = viewModel::saveExtraInstructions,
                label = { Text("Extra instructions (optional)") },
                placeholder = { Text("e.g. Use NZ spelling.") },
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImproveStyleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

package com.shiftnote.ui.clients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftnote.AppContainer
import com.shiftnote.data.db.ClientEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditorScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit
) {
    val viewModel: ClientViewModel = viewModel {
        ClientViewModel(container.database.clientDao())
    }

    val clients by viewModel.clients.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var clientPendingDelete by remember { mutableStateOf<ClientEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Clients") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add client")
            }
        }
    ) { padding ->
        if (clients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "No clients yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clients, key = { it.id }) { client ->
                    ClientCard(
                        client = client,
                        onEdit = { editingClient = client },
                        onDelete = { clientPendingDelete = client }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ClientDialog(
            title = "Add Client",
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, goals, context ->
                viewModel.addClient(name, goals, context)
                showAddDialog = false
            }
        )
    }

    editingClient?.let { client ->
        ClientDialog(
            title = "Edit Client",
            initial = client,
            onDismiss = { editingClient = null },
            onSave = { name, goals, context ->
                viewModel.updateClient(
                    client.copy(
                        name = name.trim(),
                        activeGoals = goals.trim(),
                        extraContext = context.trim()
                    )
                )
                editingClient = null
            }
        )
    }

    clientPendingDelete?.let { client ->
        AlertDialog(
            onDismissRequest = { clientPendingDelete = null },
            title = { Text("Delete ${client.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteClient(client)
                        clientPendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { clientPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ClientCard(
    client: ClientEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            if (client.activeGoals.isNotBlank()) {
                Text(
                    text = "Goals: ${client.activeGoals}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (client.extraContext.isNotBlank()) {
                Text(
                    text = "Context: ${client.extraContext}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClientDialog(
    title: String,
    initial: ClientEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, goals: String, context: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var goals by remember { mutableStateOf(initial?.activeGoals ?: "") }
    var context by remember { mutableStateOf(initial?.extraContext ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = goals,
                    onValueChange = { goals = it },
                    label = { Text("Active goals") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text("Extra context") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, goals, context) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

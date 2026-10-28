package com.shiftnote.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiftnote.data.db.ClientDao
import com.shiftnote.data.db.ClientEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel(private val clientDao: ClientDao) : ViewModel() {

    private val _clients = MutableStateFlow<List<ClientEntity>>(emptyList())
    val clients: StateFlow<List<ClientEntity>> = _clients.asStateFlow()

    init {
        viewModelScope.launch {
            clientDao.getAllClients().collect { _clients.value = it }
        }
    }

    fun addClient(name: String, activeGoals: String, extraContext: String) {
        viewModelScope.launch {
            clientDao.insert(
                ClientEntity(
                    name = name.trim(),
                    activeGoals = activeGoals.trim(),
                    extraContext = extraContext.trim()
                )
            )
        }
    }

    fun updateClient(client: ClientEntity) {
        viewModelScope.launch {
            clientDao.update(client)
        }
    }

    fun deleteClient(client: ClientEntity) {
        viewModelScope.launch {
            clientDao.delete(client)
        }
    }
}

package com.inovatickets.validador.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.inovatickets.validador.data.database.entities.Evento
import com.inovatickets.validador.data.repository.EventoRepository
import com.inovatickets.validador.data.repository.SyncRepository

class MainViewModel(
    private val eventoRepository: EventoRepository,
    private val syncRepository: SyncRepository // Mantido para possível uso futuro, mas a sincronização principal agora é centralizada
) : ViewModel() {

    val eventosAtivos: LiveData<List<Evento>> = eventoRepository.getEventosAtivos()
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    private val _syncStatus = MutableLiveData<String>()
    val syncStatus: LiveData<String> = _syncStatus

    init {
        Log.d("MainViewModel", "MainViewModel initialized.")
        // A sincronização inicial pode ser acionada aqui ou pela UI
    }

    fun ativarEvento(eventoId: String) {
        viewModelScope.launch {
            try {
                eventoRepository.ativarEvento(eventoId)
                Log.d("MainViewModel", "ativarEvento: Evento $eventoId ativado com sucesso.")
            } catch (e: Exception) {
                _error.value = "Erro ao ativar evento: ${e.message}"
                Log.e("MainViewModel", "Erro em ativarEvento: ${e.message}", e)
            }
        }
    }

    fun sincronizarDados() {
        Log.d("MainViewModel", "sincronizarDados() CALLED.")
        viewModelScope.launch {
            try {
                _isLoading.value = true
//                _syncStatus.value = "Sincronizando dados..."
                
                // Chama a nova função de sincronização completa no repositório
                eventoRepository.syncAllData()
                
//                _syncStatus.value = "Sincronização completa"
                Log.d("MainViewModel", "Sincronização de dados concluída com sucesso.")
                
            } catch (e: Exception) {
                _error.value = "Erro na sincronização: ${e.message}"
                _syncStatus.value = "Erro na sincronização"
                Log.e("MainViewModel", "Erro em sincronizarDados: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun zerarCache() {
        Log.d("MainViewModel", "zerarCache() CALLED.")
        viewModelScope.launch {
            try {
                eventoRepository.limparTodosEventos()
                _syncStatus.value = "Cache limpo"
                Log.d("MainViewModel", "zerarCache: Cache limpo com sucesso.")
            } catch (e: Exception) {
                _error.value = "Erro ao limpar cache: ${e.message}"
                Log.e("MainViewModel", "Erro em zerarCache: ${e.message}", e)
            }
        }
    }
}
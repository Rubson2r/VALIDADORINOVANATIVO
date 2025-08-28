package com.inovatickets.validador.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.inovatickets.validador.data.remote.EventoRemoteDataSource
import kotlinx.coroutines.launch
import com.inovatickets.validador.data.database.entities.Codigo
import com.inovatickets.validador.data.database.entities.Evento
import com.inovatickets.validador.data.database.entities.LogEntry
import com.inovatickets.validador.data.database.entities.TipoLog
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.repository.CodigoRepository
import com.inovatickets.validador.data.repository.EventoRepository
import com.inovatickets.validador.data.repository.LogRepository
// Ensure SyncRepository import is present
import com.inovatickets.validador.data.repository.SyncRepository
import java.util.Date

// Setor Data Class
data class Setor(
    val id: String,
    val nome: String,
    val evento_id: String? // evento_id might be part of the sector details from Supabase
    // Add any other fields you expect from your 'setores' table
)

data class ValidationResult(
    val sucesso: Boolean,
    val mensagem: String?,
    val codigo: Codigo? = null
)

// --- THIS IS THE CORRECTED CONSTRUCTOR ---
class ScannerViewModel(
    private val database: AppDatabase,
    private val eventoRemoteDataSource: EventoRemoteDataSource,
    private val syncRepository: SyncRepository // The third parameter SyncRepository
) : ViewModel() {

    // Repositories are initialized here, using the constructor parameters
    private val codigoRepository = CodigoRepository(database)
    // EventoRepository now correctly uses database, eventoRemoteDataSource, and syncRepository
    private val eventoRepository = EventoRepository(database, eventoRemoteDataSource, syncRepository)
    private val logRepository = LogRepository(database)

    private val _validationResult = MutableLiveData<ValidationResult>()
    val validationResult: LiveData<ValidationResult> = _validationResult

    private val _eventoAtual = MutableLiveData<Evento?>()
    val eventoAtual: LiveData<Evento?> = _eventoAtual

    // LiveData for Setores
    private val _setores = MutableLiveData<List<Setor>>()
    val setores: LiveData<List<Setor>> = _setores

    private val _isLoadingSetores = MutableLiveData<Boolean>()
    val isLoadingSetores: LiveData<Boolean> = _isLoadingSetores

    init {
        carregarEventoAtivo()
    }

    private fun carregarEventoAtivo() {
        viewModelScope.launch {
            try {
                Log.d("ScannerViewModel", "Carregando evento ativo...")
                val evento = eventoRepository.getEventoAtivo()
                if (evento != null) {
                    Log.d("ScannerViewModel", "Evento ativo carregado: ID=${evento.id}, Nome=${evento.nome}")
                    _eventoAtual.value = evento
                    // Fetch sectors for the active event using the syncRepository instance
                    carregarSetoresDoEvento(evento.id)
                } else {
                    Log.d("ScannerViewModel", "Nenhum evento ativo encontrado ou retornado como null.")
                    _eventoAtual.value = null
                    _setores.value = emptyList() // Clear sectors if no event
                }
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "Erro ao carregar evento ativo", e)
                _eventoAtual.value = null
                _setores.value = emptyList()
            }
        }
    }

    // Function to fetch sectors using syncRepository
    private fun carregarSetoresDoEvento(eventoId: String) {
        viewModelScope.launch {
            _isLoadingSetores.value = true
            Log.d("ScannerViewModel", "Iniciando carregamento de setores para o evento ID: $eventoId")
            try {
                // syncRepository is now available as a class member passed via constructor
                val setoresMapList = syncRepository.syncSetores(eventoId)
                Log.d("ScannerViewModel", "Setores recebidos da API (SyncRepository): ${setoresMapList.size} setores.")

                val setoresList = setoresMapList.mapNotNull { setorMap ->
                    // Adapt this mapping based on the actual keys and types from your Supabase 'setores' table
                    val id = setorMap["id"]?.toString()
                    val nome = setorMap["nome"] as? String
                    val evId = setorMap["evento_id"]?.toString() // Assuming evento_id is also returned

                    if (id != null && nome != null) {
                        Setor(id = id, nome = nome, evento_id = evId)
                    } else {
                        Log.w("ScannerViewModel", "Setor com dados inválidos/faltando: $setorMap")
                        null
                    }
                }
                _setores.value = setoresList
                Log.d("ScannerViewModel", "Setores processados e atualizados no LiveData: ${setoresList.size} setores.")
            } catch (e: Exception) {
                Log.e("ScannerViewModel", "Erro ao carregar setores do evento ID: $eventoId", e)
                _setores.value = emptyList() // Clear sectors on error
            } finally {
                _isLoadingSetores.value = false
            }
        }
    }

    fun validarCodigo(codigoTexto: String) {
        viewModelScope.launch {
            try {
                val eventoAtivo = _eventoAtual.value
                if (eventoAtivo == null) {
                    _validationResult.value = ValidationResult(
                        sucesso = false,
                        mensagem = "Nenhum evento ativo selecionado"
                    )
                    registrarLog("ERRO", "Tentativa de validação sem evento ativo", codigoTexto)
                    return@launch
                }

                val codigo = codigoRepository.getCodigoByCodigo(codigoTexto)

                if (codigo == null) {
                    _validationResult.value = ValidationResult(
                        sucesso = false,
                        mensagem = "Código não encontrado"
                    )
                    registrarLog("BLOQUEADO", "Código não encontrado", codigoTexto)
                    return@launch
                }

                if (codigo.evento_id != eventoAtivo.id) {
                    _validationResult.value = ValidationResult(
                        sucesso = false,
                        mensagem = "Código não pertence a este evento"
                    )
                    registrarLog("BLOQUEADO", "Código de outro evento", codigoTexto)
                    return@launch
                }

                if (codigo.utilizado) {
                    _validationResult.value = ValidationResult(
                        sucesso = false,
                        mensagem = "Código já foi utilizado em ${codigo.timestamp_utilizacao}"
                    )
                    registrarLog("BLOQUEADO", "Código já utilizado", codigoTexto)
                    return@launch
                }

                val codigoAtualizado = codigo.copy(
                    utilizado = true,
                    timestamp_utilizacao = Date(),
                    usuario_validacao = "APP_ANDROID"
                )

                codigoRepository.updateCodigo(codigoAtualizado)

                _validationResult.value = ValidationResult(
                    sucesso = true,
                    mensagem = "Acesso liberado! ${codigo.setor_id ?: ""}",
                    codigo = codigoAtualizado
                )

                registrarLog("LIBERADO", "Código validado com sucesso", codigoTexto)

            } catch (e: Exception) {
                _validationResult.value = ValidationResult(
                    sucesso = false,
                    mensagem = "Erro na validação: ${e.message}"
                )
                registrarLog("ERRO", "Erro na validação: ${e.message}", codigoTexto)
                Log.e("ScannerViewModel", "Erro em validarCodigo", e)
            }
        }
    }

    private suspend fun registrarLog(status: String, detalhes: String, codigo: String) {
        try {
            val eventoAtivo = _eventoAtual.value
            val logEntry = LogEntry(
                acao = "VALIDACAO_QR",
                evento_id = eventoAtivo?.id,
                detalhes = detalhes,
                usuario = "APP_ANDROID",
                tipo = when (status) {
                    "LIBERADO" -> TipoLog.SUCCESS
                    "BLOQUEADO" -> TipoLog.WARNING
                    "ERRO" -> TipoLog.ERROR
                    else -> TipoLog.INFO
                }
            )
            logRepository.insertLog(logEntry)
        } catch (e: Exception) {
            Log.e("ScannerViewModel", "Erro ao registrar log", e)
        }
    }
}
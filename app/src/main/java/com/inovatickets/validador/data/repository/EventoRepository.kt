package com.inovatickets.validador.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.database.entities.Codigo
import com.inovatickets.validador.data.database.entities.Configuracao
import com.inovatickets.validador.data.database.entities.ConfigKeys
import com.inovatickets.validador.data.database.entities.Evento
import com.inovatickets.validador.data.database.entities.Setor
import com.inovatickets.validador.data.remote.EventoRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventoRepository(
    private val database: AppDatabase,
    private val remoteDataSource: EventoRemoteDataSource,
    private val syncRepository: SyncRepository // Adicionado SyncRepository
) {

    private val eventoDao = database.eventoDao()
    private val setorDao = database.setorDao()
    private val codigoDao = database.codigoDao()
    private val configuracaoDao = database.configuracaoDao()
    private val gson = Gson()

    suspend fun syncAllData() {
        withContext(Dispatchers.IO) {
            Log.d("EventoRepository", "Iniciando a sincronização completa de dados.")
            try {
                // (A) SUBIR PENDENTES
                val pendentes = codigoDao.getCodigosNaoSincronizados()
                if (pendentes.isNotEmpty()) {
                    Log.d("EventoRepository", "Encontradas ${pendentes.size} validações pendentes. Enviando para a API...")
                    val ok = syncRepository.enviarValidacoesPendentes(pendentes)
                    if (ok) {
                        codigoDao.marcarComoSincronizados(pendentes.map { it.id })
                        Log.d("EventoRepository", "Pendentes marcados como sincronizados localmente.")
                    } else {
                        Log.e("EventoRepository", "Falha ao enviar pendentes. Abortando sincronização para não perder dados.")
                        return@withContext
                    }
                } else {
                    Log.d("EventoRepository", "Não há validações pendentes para enviar.")
                }

                // (B) BUSCAR EVENTOS
                val eventos = syncRepository.syncEventos()
                if (eventos.isEmpty()) {
                    Log.d("EventoRepository", "Nenhum evento encontrado para sincronizar.")
                    return@withContext
                }

                Log.d("EventoRepository", "Eventos recebidos: ${eventos.size}. Limpando e salvando no banco de dados.")

                // (C) LIMPAR
                eventoDao.deleteAllEventos()
                setorDao.deleteAllSetores()
                codigoDao.deleteAllCodigos()

                // (D) INSERIR
                eventoDao.insertEventos(eventos)

                for (evento in eventos) {
                    val setoresMap = syncRepository.syncSetores(evento.id)
                    if (setoresMap.isNotEmpty()) {
                        val setores = gson.fromJson<List<Setor>>(gson.toJson(setoresMap), object : TypeToken<List<Setor>>() {}.type)
                        Log.d("EventoRepository", "Setores recebidos para o evento ${evento.id}: ${setores.size}. Salvando.")
                        setorDao.insertSetores(setores)
                    }

                    val codigosMap = syncRepository.syncCodigos(evento.id)
                    if (codigosMap.isNotEmpty()) {
                        val codigos = gson.fromJson<List<Codigo>>(gson.toJson(codigosMap), object : TypeToken<List<Codigo>>() {}.type)
                        Log.d("EventoRepository", "Códigos recebidos para o evento ${evento.id}: ${codigos.size}. Salvando.")
                        val normalizados = codigos.map { it.copy(sincronizado = true) }
                        codigoDao.insertCodigos(normalizados)
                    }
                }

                // (E) MARCAR última sync GLOBAL
                configuracaoDao.insertConfiguracao(
                    Configuracao(
                        chave = "LAST_SYNC_TS", // <- global
                        valor = System.currentTimeMillis().toString(),
                        descricao = "Epoch millis da última sincronização global"
                    )
                )
                Log.d("EventoRepository", "Última sincronização GLOBAL registrada.")

                Log.d("EventoRepository", "Sincronização completa finalizada com sucesso.")
            } catch (e: Exception) {
                Log.e("EventoRepository", "Erro durante a sincronização completa de dados.", e)
            }
        }
    }

    fun getEventosAtivos(): LiveData<List<Evento>> {
        return eventoDao.getEventosAtivos()
    }

    fun getAllEventos(): LiveData<List<Evento>> {
        return eventoDao.getAllEventos()
    }

    suspend fun getEventoById(id: String): Evento? {
        return withContext(Dispatchers.IO) {
            eventoDao.getEventoById(id)
        }
    }

    suspend fun getEventoAtivo(): Evento? {
        return withContext(Dispatchers.IO) {
            val eventoAtivoId = configuracaoDao.getValor(ConfigKeys.EVENTO_ATIVO)
            if (eventoAtivoId != null) {
                eventoDao.getEventoById(eventoAtivoId)
            } else {
                null
            }
        }
    }

    suspend fun insertEvento(evento: Evento) {
        withContext(Dispatchers.IO) {
            eventoDao.insertEvento(evento)
        }
    }

    suspend fun insertEventos(eventos: List<Evento>) {
        withContext(Dispatchers.IO) {
            eventoDao.insertEventos(eventos)
        }
    }

    suspend fun updateEvento(evento: Evento) {
        withContext(Dispatchers.IO) {
            eventoDao.updateEvento(evento)
        }
    }

    suspend fun ativarEvento(eventoId: String) {
        withContext(Dispatchers.IO) {
            eventoDao.desativarTodosEventos()
            eventoDao.ativarEvento(eventoId)
            configuracaoDao.insertConfiguracao(
                Configuracao(
                    chave = ConfigKeys.EVENTO_ATIVO,
                    valor = eventoId,
                    descricao = "ID do evento atualmente ativo"
                )
            )
        }
    }

    suspend fun limparTodosEventos() {
        withContext(Dispatchers.IO) {
            eventoDao.deleteAllEventos()
        }
    }

    suspend fun countEventosAtivos(): Int {
        return withContext(Dispatchers.IO) {
            eventoDao.countEventosAtivos()
        }
    }
}

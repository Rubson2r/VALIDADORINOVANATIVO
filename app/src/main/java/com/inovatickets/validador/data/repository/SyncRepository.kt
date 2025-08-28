package com.inovatickets.validador.data.repository

import android.util.Log
import com.inovatickets.validador.data.database.entities.Codigo
import com.inovatickets.validador.data.database.entities.Evento
import com.inovatickets.validador.data.remote.CodigoUpsertPayload
import com.inovatickets.validador.data.remote.EventoRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SyncRepository(private val eventoRemoteDataSource: EventoRemoteDataSource) {

    init { Log.d("SyncRepository", "SyncRepository instance CREATED.") }

    // ---------------------------
    // EVENTOS
    // ---------------------------
    suspend fun syncEventos(): List<Evento> = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando syncEventos (chamada API)...")
        try {
            val response = eventoRemoteDataSource.getApi().getEventos(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey()
            )
            Log.d("SyncRepository", "API getEventos SUCESSO. Recebidos ${response.size} eventos.")

            val filtered = response.filter { ev -> ev.nome.isNotEmpty() && !ev.data.isNullOrEmpty() }
            Log.d("SyncRepository", "Eventos após filtro: ${filtered.size} eventos.")
            filtered
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em syncEventos (chamada API)", e)
            emptyList()
        }
    }

    // ---------------------------
    // SETORES
    // ---------------------------
    suspend fun syncSetores(eventoId: String? = null): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando syncSetores (chamada API) para eventoId: $eventoId")
        try {
            val response = eventoRemoteDataSource.getApi().getSetores(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
                eventoId = if (eventoId.isNullOrBlank()) null else "eq.$eventoId",
                select = "*"
            )
            Log.d("SyncRepository", "API getSetores SUCESSO. Recebidos ${response.size} setores.")
            response
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em syncSetores (chamada API)", e)
            emptyList()
        }
    }

    suspend fun syncSetoresAll(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando syncSetoresAll (chamada API)")
        try {
            val response = eventoRemoteDataSource.getApi().getSetoresAll(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
                select = "*"
            )
            Log.d("SyncRepository", "API getSetoresAll SUCESSO. Recebidos ${response.size} setores.")
            response
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em syncSetoresAll (chamada API)", e)
            emptyList()
        }
    }

    // ---------------------------
    // CÓDIGOS
    // ---------------------------
    suspend fun syncCodigos(eventoId: String? = null): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando syncCodigos (chamada API) para eventoId: $eventoId")
        try {
            val response = eventoRemoteDataSource.getApi().getCodigos(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
                eventoId = if (eventoId.isNullOrBlank()) null else "eq.$eventoId",
                select = "id,codigo,utilizado,data_utilizacao,aparelho,setor_id,setores!inner(id,nome_setor,evento_id)"
            )
            Log.d("SyncRepository", "API getCodigos SUCESSO. Recebidos ${response.size} codigos.")
            response
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em syncCodigos (chamada API)", e)
            emptyList()
        }
    }
//    suspend fun syncCodigos(eventoId: String? = null): List<Map<String, Any>> = withContext(Dispatchers.IO) {
//        Log.d("SyncRepository", "Iniciando syncCodigos (paginado) para eventoId: $eventoId")
//        val pageSize = 1000
//        var start = 0
//        val acumulado = mutableListOf<Map<String, Any>>()
//
//        val filtroEvento = if (eventoId.isNullOrBlank()) null else "eq.$eventoId"
//        val select = "id,codigo,utilizado,data_utilizacao,aparelho,setor_id,setores!inner(id,nome_setor,evento_id)"
//
//        while (true) {
//            val end = start + pageSize - 1
//            val rangeHeader = "items=$start-$end"
//
//            val resp = try {
//                eventoRemoteDataSource.getApi().getCodigosPaged(
//                    apiKey = eventoRemoteDataSource.getSupabaseKey(),
//                    authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
//                    range = rangeHeader,
//                    prefer = "count=exact",
//                    eventoId = filtroEvento,
//                    select = select
//                )
//            } catch (e: Exception) {
//                Log.e("SyncRepository", "ERRO em getCodigosPaged (range=$rangeHeader)", e)
//                break
//            }
//
//            if (!resp.isSuccessful) {
//                Log.e("SyncRepository", "HTTP ${resp.code()} em getCodigosPaged (range=$rangeHeader) - ${resp.message()}")
//                break
//            }
//
//            val lote = resp.body().orEmpty()
//            acumulado.addAll(lote)
//
//            val contentRange = resp.headers()["Content-Range"] // ex: "0-999/35789" ou "0-999/*"
//            val total = contentRange?.substringAfter("/")?.toIntOrNull()
//
//            Log.d("SyncRepository", "Página recebida ($rangeHeader): ${lote.size} itens | Content-Range=$contentRange | total=$total | acumulado=${acumulado.size}")
//
//            // Parar se:
//            // 1) veio menos que pageSize (última página), ou
//            // 2) já atingimos o total informado
//            val ultimaPagina = lote.size < pageSize
//            val atingiuTotal = total != null && acumulado.size >= total
//
//            if (ultimaPagina || atingiuTotal) break
//
//            start += pageSize
//        }
//
//        Log.d("SyncRepository", "syncCodigos finalizado. Total acumulado: ${acumulado.size}")
//        return@withContext acumulado
//    }

    // (ainda disponível caso use tabela separada)
    suspend fun enviarValidacao(validacao: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando enviarValidacao (chamada API)")
        try {
            eventoRemoteDataSource.getApi().insertValidacao(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
                validacao = validacao
            )
            Log.d("SyncRepository", "API insertValidacao SUCESSO.")
            true
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em enviarValidacao (chamada API)", e)
            false
        }
    }

    suspend fun atualizarCodigo(codigoId: String, update: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        Log.d("SyncRepository", "Iniciando atualizarCodigo (chamada API) para codigoId: $codigoId")
        try {
            val resp = eventoRemoteDataSource.getApi().updateCodigo(
                apiKey = eventoRemoteDataSource.getSupabaseKey(),
                authorization = "Bearer " + eventoRemoteDataSource.getSupabaseKey(),
                id = "eq.$codigoId",
                update = update
            )
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()} ${resp.message()}")
            Log.d("SyncRepository", "API updateCodigo SUCESSO.")
            true
        } catch (e: Exception) {
            Log.e("SyncRepository", "ERRO em atualizarCodigo (chamada API)", e)
            false
        }
    }

    // ---------------------------
    // ⬇️ NOVO: Enviar pendentes usando payload SLIM (sem evento_id)
    // ---------------------------
    suspend fun enviarValidacoesPendentes(pendentes: List<Codigo>): Boolean = withContext(Dispatchers.IO) {
        if (pendentes.isEmpty()) return@withContext true
        Log.d("SyncRepository", "Enviando ${pendentes.size} validações pendentes via upsert (payload slim)...")

        try {
            val payload = pendentes.map { c ->
                CodigoUpsertPayload(
                    id = c.id,
                    codigo = c.codigo,
                    utilizado = true, // pendentes são as que já foram validadas
                    aparelho = c.usuario_validacao.ifBlank { null },
                    setor_id = c.setor_id,
                    data_utilizacao = toIsoUtc(c.timestamp_utilizacao)
                )
            }
            eventoRemoteDataSource.upsertCodigosSlim(payload)
            Log.d("SyncRepository", "Upsert de pendentes SUCESSO.")
            true
        } catch (t: Throwable) {
            Log.e("SyncRepository", "Falha no upsert de pendentes", t)
            false
        }
    }

    // ------------- utils -------------
    private fun toIsoUtc(date: Date?): String? {
        if (date == null) return null
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date) // ex: 2025-08-10T18:22:33.123+00:00
    }
}

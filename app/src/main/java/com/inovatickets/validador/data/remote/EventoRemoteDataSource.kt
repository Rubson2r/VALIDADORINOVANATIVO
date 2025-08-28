package com.inovatickets.validador.data.remote

import com.inovatickets.validador.data.database.entities.Evento
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Payload "slim" que bate com as colunas da tabela remota `codigos`
data class CodigoUpsertPayload(
    val id: String,
    val codigo: String,
    val utilizado: Boolean,
    val aparelho: String?,        // mapeia usuario_validacao -> aparelho
    val setor_id: String,
    val data_utilizacao: String?  // ISO-8601 UTC (string)
)

// API única do Supabase
interface SupabaseApi {
    @GET("rest/v1/eventos")
    suspend fun getEventos(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*"
    ): List<Evento>

    @GET("rest/v1/setores")
    suspend fun getSetores(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("evento_id") eventoId: String? = null,
        @Query("select") select: String = "*"
    ): List<Map<String, Any>>

    @GET("rest/v1/setores")
    suspend fun getSetoresAll(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*"
    ): List<Map<String, Any>>

    // Busca códigos com inner em setores para filtrar por evento
    @GET("rest/v1/codigos")
    suspend fun getCodigos(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("setores.evento_id") eventoId: String? = null,
        @Query("select") select: String = "id,codigo,utilizado,data_utilizacao,aparelho,setor_id,setores!inner(id,nome_setor,evento_id)"
    ): List<Map<String, Any>>

    // Se usar tabela separada de "validacoes"
    @POST("rest/v1/validacoes")
    suspend fun insertValidacao(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body validacao: Map<String, Any>
    )

    @PATCH("rest/v1/codigos")
    suspend fun updateCodigo(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("id") id: String,
        @Body update: Map<String, Any>
    ): Response<Unit>

    // ⬇️ NOVO: upsert em lote (merge por PK id) com payload SLIM
    @Headers(
        "Prefer: resolution=merge-duplicates",
        "Prefer: return=minimal"
    )
    @POST("rest/v1/codigos?on_conflict=id")
    suspend fun upsertCodigosSlim(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: List<CodigoUpsertPayload>
    ): Response<Unit>

    @GET("rest/v1/codigos")
    suspend fun getCodigosPaged(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Range") range: String,                      // ex: "items=0-999"
        @Header("Prefer") prefer: String = "count=exact",    // pra vir Content-Range com total
        @Query("setores.evento_id") eventoId: String? = null,
        @Query("select") select: String = "id,codigo,utilizado,data_utilizacao,aparelho,setor_id,setores!inner(id,nome_setor,evento_id)"
    ): retrofit2.Response<List<Map<String, Any>>>
}

class EventoRemoteDataSource(
    private val supabaseUrl: String,
    private val supabaseKey: String
) {

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(supabaseUrl) // supabaseUrl deve terminar com '/'
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val apiService: SupabaseApi by lazy {
        retrofit.create(SupabaseApi::class.java)
    }

    fun getApi(): SupabaseApi = apiService
    fun getSupabaseKey(): String = supabaseKey
    fun getSupabaseUrl(): String = supabaseUrl

    // Helper para upsert de pendentes com payload SLIM
    suspend fun upsertCodigosSlim(payload: List<CodigoUpsertPayload>) {
        val resp = apiService.upsertCodigosSlim(
            apiKey = supabaseKey,
            authorization = "Bearer $supabaseKey",
            body = payload
        )
        if (!resp.isSuccessful) {
            throw RuntimeException("Erro no upsert de códigos: HTTP ${resp.code()} ${resp.message()}")
        }
    }
}

package com.inovatickets.validador.data.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inovatickets.validador.data.database.entities.Codigo
import java.util.Date // ⬅️ IMPORTANTE

@Dao
interface CodigoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodigos(codigos: List<Codigo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodigo(codigo: Codigo)

    @Query("DELETE FROM codigos WHERE evento_id = :eventoId")
    suspend fun deleteCodigosByEventoId(eventoId: String)

    @Query("DELETE FROM codigos")
    suspend fun deleteAllCodigos()

    @Query("SELECT * FROM codigos WHERE evento_id = :eventoId")
    fun getCodigosByEvento(eventoId: String): LiveData<List<Codigo>>

    @Query("SELECT * FROM codigos WHERE codigo = :codigo LIMIT 1")
    suspend fun getCodigoByCodigo(codigo: String): Codigo?

    @Query("SELECT * FROM codigos WHERE id = :id LIMIT 1")
    suspend fun getCodigoById(id: String): Codigo?

    @Query("SELECT * FROM codigos WHERE evento_id = :eventoId AND utilizado = 1")
    fun getCodigosUsados(eventoId: String): LiveData<List<Codigo>>

    @Query("SELECT COUNT(*) FROM codigos WHERE evento_id = :eventoId")
    suspend fun countCodigosByEvento(eventoId: String): Int

    @Query("SELECT COUNT(*) FROM codigos WHERE evento_id = :eventoId AND utilizado = 1")
    suspend fun countCodigosUsados(eventoId: String): Int

    @Update
    suspend fun updateCodigo(codigo: Codigo)

    @Query("SELECT * FROM codigos WHERE sincronizado = 0")
    suspend fun getCodigosNaoSincronizados(): List<Codigo>

    @Query("UPDATE codigos SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun marcarComoSincronizados(ids: List<String>)

    // -------------------------
    // NOVOS MÉTODOS P/ VALIDAÇÃO
    // -------------------------

    // 1) Buscar pelo código + evento (mais preciso)
    @Query("SELECT * FROM codigos WHERE codigo = :codigo AND evento_id = :eventoId LIMIT 1")
    suspend fun findByCodigoAndEvento(codigo: String, eventoId: String): Codigo?

    // 2) Marcar como utilizado (grava data e usuário)
    @Query("""
        UPDATE codigos
        SET utilizado = 1,
            timestamp_utilizacao = :data,
            usuario_validacao = :usuario,
            sincronizado = 0
        WHERE id = :id
    """)
    suspend fun marcarUtilizado(id: String, data: Date, usuario: String?)

    @Query("""
    SELECT COUNT(*) 
    FROM codigos 
    WHERE evento_id = :eventoId 
      AND utilizado = 1 
      AND sincronizado = 0
""")
    suspend fun countValidacoesNaoSincronizadas(eventoId: String): Int

    @Query("""
    SELECT COUNT(*)
    FROM codigos
    WHERE utilizado = 1
      AND sincronizado = 0
""")
    suspend fun countValidacoesNaoSincronizadasGlobal(): Int

    @Query("""
    SELECT COUNT(*) 
    FROM codigos 
    WHERE evento_id = :eventoId 
      AND utilizado = 1
      AND usuario_validacao = :aparelho
""")
    suspend fun countValidadosPorEventoEAparelho(
        eventoId: String,
        aparelho: String
    ): Int

}

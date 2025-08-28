package com.inovatickets.validador.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inovatickets.validador.data.database.entities.Setor

@Dao
interface SetorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetores(setores: List<Setor>)

    @Query("DELETE FROM setores WHERE evento_id = :eventoId")
    suspend fun deleteSetoresByEventoId(eventoId: String)

    @Query("DELETE FROM setores")
    suspend fun deleteAllSetores()

    @Query("SELECT * FROM setores WHERE evento_id = :eventoId ORDER BY nome")
    suspend fun getByEventoId(eventoId: String): List<Setor>

    @Query("SELECT DISTINCT evento_id FROM setores")
    suspend fun distinctEventoIds(): List<String>
}
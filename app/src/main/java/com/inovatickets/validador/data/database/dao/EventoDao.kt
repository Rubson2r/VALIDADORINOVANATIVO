package com.inovatickets.validador.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.inovatickets.validador.data.database.entities.Evento

@Dao
interface EventoDao {
    
    @Query("SELECT * FROM eventos WHERE status = 'ativo' ORDER BY data ASC")
    fun getEventosAtivos(): LiveData<List<Evento>>
    
    @Query("SELECT * FROM eventos ORDER BY data DESC")
    fun getAllEventos(): LiveData<List<Evento>>
    
    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun getEventoById(id: String): Evento?
    
    @Query("SELECT * FROM eventos WHERE id = :id")
    fun getEventoByIdLive(id: String): LiveData<Evento?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvento(evento: Evento)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventos(eventos: List<Evento>)
    
    @Update
    suspend fun updateEvento(evento: Evento)
    
    @Delete
    suspend fun deleteEvento(evento: Evento)
    
    @Query("DELETE FROM eventos")
    suspend fun deleteAllEventos()
    
    @Query("UPDATE eventos SET status = 'inativo'")
    suspend fun desativarTodosEventos()
    
    @Query("UPDATE eventos SET status = 'ativo' WHERE id = :eventoId")
    suspend fun ativarEvento(eventoId: String)
    
    @Query("SELECT COUNT(*) FROM eventos WHERE status = 'ativo'")
    suspend fun countEventosAtivos(): Int
}
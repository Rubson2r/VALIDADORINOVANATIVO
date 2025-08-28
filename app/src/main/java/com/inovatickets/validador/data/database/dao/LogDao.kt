package com.inovatickets.validador.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.inovatickets.validador.data.database.entities.LogEntry
import com.inovatickets.validador.data.database.entities.TipoLog

@Dao
interface LogDao {
    
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): LiveData<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE evento_id = :eventoId ORDER BY timestamp DESC")
    fun getLogsByEvento(eventoId: String): LiveData<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE tipo = :tipo ORDER BY timestamp DESC")
    fun getLogsByTipo(tipo: TipoLog): LiveData<List<LogEntry>>
    
    @Insert
    suspend fun insertLog(log: LogEntry)
    
    @Insert
    suspend fun insertLogs(logs: List<LogEntry>)
    
    @Delete
    suspend fun deleteLog(log: LogEntry)
    
    @Query("DELETE FROM logs WHERE timestamp < :cutoffDate")
    suspend fun deleteOldLogs(cutoffDate: Long)
    
    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()
    
    @Query("SELECT COUNT(*) FROM logs WHERE tipo = :tipo")
    suspend fun countLogsByTipo(tipo: TipoLog): Int
    
    @Query("SELECT * FROM logs WHERE sincronizado = 0")
    suspend fun getLogsNaoSincronizados(): List<LogEntry>
    
    @Query("UPDATE logs SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun marcarComoSincronizados(ids: List<String>)
}
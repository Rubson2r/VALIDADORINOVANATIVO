package com.inovatickets.validador.data.repository

import androidx.lifecycle.LiveData
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.database.entities.LogEntry
import com.inovatickets.validador.data.database.entities.TipoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogRepository(private val database: AppDatabase) {
    
    private val logDao = database.logDao()
    
    fun getRecentLogs(): LiveData<List<LogEntry>> {
        return logDao.getRecentLogs()
    }
    
    fun getLogsByEvento(eventoId: String): LiveData<List<LogEntry>> {
        return logDao.getLogsByEvento(eventoId)
    }
    
    fun getLogsByTipo(tipo: TipoLog): LiveData<List<LogEntry>> {
        return logDao.getLogsByTipo(tipo)
    }
    
    suspend fun insertLog(log: LogEntry) {
        withContext(Dispatchers.IO) {
            logDao.insertLog(log)
        }
    }
    
    suspend fun insertLogs(logs: List<LogEntry>) {
        withContext(Dispatchers.IO) {
            logDao.insertLogs(logs)
        }
    }
    
    suspend fun deleteOldLogs(cutoffDate: Long) {
        withContext(Dispatchers.IO) {
            logDao.deleteOldLogs(cutoffDate)
        }
    }
    
    suspend fun deleteAllLogs() {
        withContext(Dispatchers.IO) {
            logDao.deleteAllLogs()
        }
    }
    
    suspend fun countLogsByTipo(tipo: TipoLog): Int {
        return withContext(Dispatchers.IO) {
            logDao.countLogsByTipo(tipo)
        }
    }
    
    suspend fun getLogsNaoSincronizados(): List<LogEntry> {
        return withContext(Dispatchers.IO) {
            logDao.getLogsNaoSincronizados()
        }
    }
    
    suspend fun marcarComoSincronizados(ids: List<String>) {
        withContext(Dispatchers.IO) {
            logDao.marcarComoSincronizados(ids)
        }
    }
}
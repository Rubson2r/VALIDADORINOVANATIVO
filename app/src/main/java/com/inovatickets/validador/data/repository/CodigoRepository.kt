package com.inovatickets.validador.data.repository

import androidx.lifecycle.LiveData
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.database.entities.Codigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CodigoRepository(private val database: AppDatabase) {
    
    private val codigoDao = database.codigoDao()
    
    fun getCodigosByEvento(eventoId: String): LiveData<List<Codigo>> {
        return codigoDao.getCodigosByEvento(eventoId)
    }
    
    suspend fun getCodigoByCodigo(codigo: String): Codigo? {
        return withContext(Dispatchers.IO) {
            codigoDao.getCodigoByCodigo(codigo)
        }
    }
    
    suspend fun getCodigoById(id: String): Codigo? {
        return withContext(Dispatchers.IO) {
            codigoDao.getCodigoById(id)
        }
    }
    
    fun getCodigosUsados(eventoId: String): LiveData<List<Codigo>> {
        return codigoDao.getCodigosUsados(eventoId)
    }
    
    suspend fun countCodigosByEvento(eventoId: String): Int {
        return withContext(Dispatchers.IO) {
            codigoDao.countCodigosByEvento(eventoId)
        }
    }
    
    suspend fun countCodigosUsados(eventoId: String): Int {
        return withContext(Dispatchers.IO) {
            codigoDao.countCodigosUsados(eventoId)
        }
    }
    
    suspend fun insertCodigo(codigo: Codigo) {
        withContext(Dispatchers.IO) {
            codigoDao.insertCodigo(codigo)
        }
    }
    
    suspend fun insertCodigos(codigos: List<Codigo>) {
        withContext(Dispatchers.IO) {
            codigoDao.insertCodigos(codigos)
        }
    }
    
    suspend fun updateCodigo(codigo: Codigo) {
        withContext(Dispatchers.IO) {
            codigoDao.updateCodigo(codigo)
        }
    }
    
    suspend fun deleteCodigosByEvento(eventoId: String) {
        withContext(Dispatchers.IO) {
            codigoDao.deleteCodigosByEventoId(eventoId)
        }
    }
    
    suspend fun getCodigosNaoSincronizados(): List<Codigo> {
        return withContext(Dispatchers.IO) {
            codigoDao.getCodigosNaoSincronizados()
        }
    }
    
    suspend fun marcarComoSincronizados(ids: List<String>) {
        withContext(Dispatchers.IO) {
            codigoDao.marcarComoSincronizados(ids)
        }
    }
}
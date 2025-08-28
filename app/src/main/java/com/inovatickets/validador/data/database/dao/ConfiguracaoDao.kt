package com.inovatickets.validador.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.inovatickets.validador.data.database.entities.Configuracao

@Dao
interface ConfiguracaoDao {
    
    @Query("SELECT * FROM configuracoes ORDER BY chave ASC")
    fun getAllConfiguracoes(): LiveData<List<Configuracao>>
    
    @Query("SELECT valor FROM configuracoes WHERE chave = :chave")
    suspend fun getValor(chave: String): String?
    
    @Query("SELECT valor FROM configuracoes WHERE chave = :chave")
    fun getValorLive(chave: String): LiveData<String?>
    
    @Query("SELECT * FROM configuracoes WHERE chave = :chave")
    suspend fun getConfiguracao(chave: String): Configuracao?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracao(configuracao: Configuracao)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracoes(configuracoes: List<Configuracao>)
    
    @Update
    suspend fun updateConfiguracao(configuracao: Configuracao)
    
    @Delete
    suspend fun deleteConfiguracao(configuracao: Configuracao)
    
    @Query("DELETE FROM configuracoes WHERE chave = :chave")
    suspend fun deleteByChave(chave: String)
}
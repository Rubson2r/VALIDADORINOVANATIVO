package com.inovatickets.validador.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "configuracoes")
data class Configuracao(
    @PrimaryKey
    val chave: String,
    val valor: String,
    val descricao: String? = null
) : Parcelable

// Chaves de configuração padrão
object ConfigKeys {
    const val SUPABASE_URL = "supabase_url"
    const val SUPABASE_KEY = "supabase_key"
    const val USUARIO_VALIDADOR = "usuario_validador"
    const val EVENTO_ATIVO = "evento_ativo"
    const val MODO_OFFLINE = "modo_offline"
    const val ULTIMA_SINCRONIZACAO = "ultima_sincronizacao"
    const val APARELHO_NOME = "APARELHO_NOME"
}
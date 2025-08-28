package com.inovatickets.validador.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.UUID

@Parcelize
@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val acao: String,
    val evento_id: String? = null,
    val codigo_id: String? = null,
    val detalhes: String? = null,
    val usuario: String? = null,
    val timestamp: Date = Date(),
    val tipo: TipoLog = TipoLog.INFO,
    val sincronizado: Boolean = false
) : Parcelable

enum class TipoLog {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}
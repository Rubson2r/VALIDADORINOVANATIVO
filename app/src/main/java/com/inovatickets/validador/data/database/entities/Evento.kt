package com.inovatickets.validador.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(tableName = "eventos")
data class Evento(
    @PrimaryKey
    val id: String,
    val nome: String,
    val data: String? = null,
    val hora: String? = null,
    val status: String = "ativo",
    val local_id: String? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
) : Parcelable
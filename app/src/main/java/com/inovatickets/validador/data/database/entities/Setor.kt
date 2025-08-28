package com.inovatickets.validador.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "setores",
    foreignKeys = [
        ForeignKey(
            entity = Evento::class,
            parentColumns = ["id"],
            childColumns = ["evento_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["evento_id"])]
)
data class Setor(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("evento_id")
    val evento_id: String,

    @SerializedName("nome_setor")
    val nome: String,

    @SerializedName("ativo")
    val ativo: Boolean = true
)
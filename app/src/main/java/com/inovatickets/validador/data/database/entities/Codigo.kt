package com.inovatickets.validador.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@JsonAdapter(Codigo.CodigoJsonAdapter::class)
@Entity(
    tableName = "codigos",
    foreignKeys = [
        ForeignKey(
            entity = Evento::class,
            parentColumns = ["id"],
            childColumns = ["evento_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Setor::class,
            parentColumns = ["id"],
            childColumns = ["setor_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["evento_id"]), Index(value = ["setor_id"])]
)
data class Codigo(
    @PrimaryKey
    val id: String,

    // Vem da raiz (evento_id) ou de setores.evento_id — o adapter resolve.
    @ColumnInfo(name = "evento_id")
    val evento_id: String,

    @ColumnInfo(name = "setor_id")
    val setor_id: String,

    @ColumnInfo(name = "codigo")
    val codigo: String,

    @ColumnInfo(name = "utilizado")
    val utilizado: Boolean = false,

    @ColumnInfo(name = "sincronizado")
    val sincronizado: Boolean = false, // local-only (Room)

    // Deixa NOT NULL no Room, com default "", pra evitar NPE ao inserir.
    @ColumnInfo(name = "usuario_validacao")
    val usuario_validacao: String = "",

    // ISO 8601 -> Date (parse no adapter)
    @ColumnInfo(name = "timestamp_utilizacao")
    val timestamp_utilizacao: Date? = null
) {

    class CodigoJsonAdapter :
        JsonDeserializer<Codigo>,
        JsonSerializer<Codigo> {

        private val sdf by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        private fun JsonObject.optString(name: String): String? =
            if (has(name) && !get(name).isJsonNull) get(name).asString else null

        private fun JsonObject.optObj(name: String): JsonObject? =
            if (has(name) && get(name).isJsonObject) getAsJsonObject(name) else null

        private fun JsonElement?.asBooleanSafe(): Boolean? = when {
            this == null || isJsonNull -> null
            isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
            isJsonPrimitive && asJsonPrimitive.isString -> asJsonPrimitive.asString.equals("true", ignoreCase = true)
            else -> null
        }

        private fun parseDate(s: String?): Date? =
            if (s.isNullOrBlank()) null
            else try { sdf.parse(s) } catch (_: Exception) { null }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Codigo {
            val obj = json.asJsonObject

            val id = obj.optString("id")
                ?: obj.optObj("codigos")?.optString("id")
                ?: obj.optString("codigo_id")
                ?: throw JsonParseException("Campo 'id' ausente no item recebido: $obj")

            val codigo = obj.optString("codigo") ?: throw JsonParseException("codigo ausente")
            val setorId = obj.optString("setor_id") ?: throw JsonParseException("setor_id ausente")

            // evento_id pode vir na raiz ou aninhado em setores.evento_id
            val eventoIdFromRoot = obj.optString("evento_id")
            val eventoIdFromNested = obj.optObj("setores")?.optString("evento_id")
            val eventoId = eventoIdFromRoot ?: eventoIdFromNested
            ?: throw JsonParseException("evento_id ausente (nem raiz nem setores.evento_id)")

            val utilizado = obj.get("utilizado").asBooleanSafe() ?: false
            val aparelho = obj.optString("aparelho") ?: "" // evita NPE no Room
            val dataUtilizacao = parseDate(obj.optString("data_utilizacao"))

            // >>> ALTERAÇÃO: se API não envia 'sincronizado', default = true (porque tudo da API já está sincronizado)
            val sincronizado = obj.get("sincronizado").asBooleanSafe() ?: true // <<

            return Codigo(
                id = id,
                evento_id = eventoId,
                setor_id = setorId,
                codigo = codigo,
                utilizado = utilizado,
                sincronizado = sincronizado,
                usuario_validacao = aparelho,
                timestamp_utilizacao = dataUtilizacao
            )
        }

        override fun serialize(src: Codigo, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            // Caso precise enviar pro backend (sem o campo 'sincronizado')
            val o = JsonObject()
            o.addProperty("id", src.id)
            o.addProperty("codigo", src.codigo)
            o.addProperty("utilizado", src.utilizado)
            o.addProperty("aparelho", src.usuario_validacao)
            o.addProperty("setor_id", src.setor_id)
            o.addProperty("evento_id", src.evento_id)
            src.timestamp_utilizacao?.let { o.addProperty("data_utilizacao", sdf.format(it)) }
            // o.addProperty("sincronizado", src.sincronizado) // << REMOVIDO do payload pra API
            return o
        }
    }
}
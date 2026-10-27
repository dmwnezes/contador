package com.dmwnezes.contador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Contador(val id: String, val nome: String, val data: LocalDate)

/** Positivo: dias que faltam. Negativo: dias que já passaram. Zero: é hoje. */
fun diasAte(data: LocalDate, hoje: LocalDate): Long = ChronoUnit.DAYS.between(hoje, data)

/** Futuros primeiro (o mais próximo no topo), depois passados (o mais recente no topo). */
fun ordenar(lista: List<Contador>, hoje: LocalDate): List<Contador> =
    lista.sortedWith(compareBy<Contador>({ diasAte(it.data, hoje) < 0 }, { kotlin.math.abs(diasAte(it.data, hoje)) }))

val PADRAO = listOf(Contador("gta6", "Lançamento do GTA 6", LocalDate.of(2026, 11, 19)))

data class Estado(val contadores: List<Contador>, val destaque: String?)

fun paraJson(e: Estado): String = JSONObject()
    .put("destaque", e.destaque ?: JSONObject.NULL)
    .put("contadores", JSONArray().apply {
        e.contadores.forEach { put(JSONObject().put("id", it.id).put("nome", it.nome).put("data", it.data.toString())) }
    })
    .toString()

fun deJson(s: String?): Estado {
    if (s.isNullOrBlank()) return Estado(PADRAO, PADRAO.first().id)
    return try {
        val o = JSONObject(s)
        val arr = o.getJSONArray("contadores")
        val lista = (0 until arr.length()).map {
            val c = arr.getJSONObject(it)
            Contador(c.getString("id"), c.getString("nome"), LocalDate.parse(c.getString("data")))
        }
        Estado(lista, if (o.isNull("destaque")) null else o.getString("destaque"))
    } catch (e: Exception) {
        Estado(PADRAO, PADRAO.first().id)
    }
}

class Armazem(context: Context) {
    private val prefs = context.getSharedPreferences("contador", Context.MODE_PRIVATE)
    fun ler(): Estado = deJson(prefs.getString("estado", null))
    fun salvar(e: Estado) = prefs.edit().putString("estado", paraJson(e)).apply()
}

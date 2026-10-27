package com.dmwnezes.contador

import android.content.Context

/** Preferências de aparência e de atualização. */
data class Config(
    val tema: String = "escuro", // escuro | claro | auto
    val cor: Int = 0, // índice em CORES
    val bolhas: Boolean = true,
    val horario: Boolean = false,
    val porContador: Boolean = true,
    val autoAtualizar: Boolean = true,
)

class Preferencias(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun ler() = Config(
        tema = prefs.getString("tema", "escuro") ?: "escuro",
        cor = prefs.getInt("cor", 0),
        bolhas = prefs.getBoolean("bolhas", true),
        horario = prefs.getBoolean("horario", false),
        porContador = prefs.getBoolean("porContador", true),
        autoAtualizar = prefs.getBoolean("autoAtualizar", true),
    )

    fun salvar(c: Config) = prefs.edit()
        .putString("tema", c.tema).putInt("cor", c.cor)
        .putBoolean("bolhas", c.bolhas).putBoolean("horario", c.horario)
        .putBoolean("porContador", c.porContador).putBoolean("autoAtualizar", c.autoAtualizar)
        .apply()
}

enum class Periodo { MANHA, TARDE, NOITE }

fun periodo(hora: Int) = when (hora) { in 5..11 -> Periodo.MANHA; in 12..17 -> Periodo.TARDE; else -> Periodo.NOITE }

/** "v1.0.12" → 12 (o número da build do GitHub Actions, que também é o versionCode). */
fun codigoDaTag(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()

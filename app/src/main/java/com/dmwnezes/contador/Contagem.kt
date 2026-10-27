package com.dmwnezes.contador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class Contador(
    val id: String,
    val nome: String,
    val data: LocalDate,
    val hora: LocalTime? = null,
    val criado: LocalDate = data,
    val cor: Int = 0,
    val icone: String = "evento",
)

/** Cores que o usuário pode escolher (ARGB). O contador guarda só o índice. */
val CORES = listOf(0xFFFF5D8F, 0xFFFFB34D, 0xFF3EE0CF, 0xFF8B7CFF, 0xFF5BC0FF, 0xFF7BE07B).map { it.toInt() }
val ICONES = listOf("evento", "jogo", "viagem", "aniversario", "amor", "festa", "estudo", "trabalho", "esporte", "estrela")

fun corArgb(c: Contador) = CORES[c.cor.coerceIn(CORES.indices)]

/** Positivo: dias que faltam. Negativo: dias que já passaram. Zero: é hoje. */
fun diasAte(data: LocalDate, hoje: LocalDate): Long = ChronoUnit.DAYS.between(hoje, data)

fun alvo(c: Contador): LocalDateTime = c.hora?.let { c.data.atTime(it) } ?: c.data.atStartOfDay()

/** Positivo: segundos até o momento do evento. Negativo: segundos desde. */
fun segundosAte(c: Contador, agora: LocalDateTime): Long = ChronoUnit.SECONDS.between(agora, alvo(c))

/** Futuros primeiro (o mais próximo no topo), depois passados (o mais recente no topo). */
fun ordenar(lista: List<Contador>, hoje: LocalDate): List<Contador> =
    lista.sortedWith(compareBy<Contador>({ diasAte(it.data, hoje) < 0 }, { abs(diasAte(it.data, hoje)) }))

/** Quantos sábados existem no intervalo [a, b). */
fun finsDeSemana(a: LocalDate, b: LocalDate): Long {
    val ini = minOf(a, b)
    val fim = maxOf(a, b)
    val primeiro = ini.plusDays(((DayOfWeek.SATURDAY.value - ini.dayOfWeek.value + 7) % 7).toLong())
    if (!primeiro.isBefore(fim)) return 0
    return (ChronoUnit.DAYS.between(primeiro, fim) - 1) / 7 + 1
}

enum class Unidade(val singular: String, val plural: String) {
    DIAS("dia", "dias"),
    SEMANAS("semana", "semanas"),
    HORAS("hora", "horas"),
    MINUTOS("minuto", "minutos"),
    FINS_DE_SEMANA("fim de semana", "fins de semana");

    fun rotulo(n: Long) = if (n == 1L) singular else plural
}

fun valorEm(u: Unidade, c: Contador, agora: LocalDateTime): Long {
    val hoje = agora.toLocalDate()
    val s = abs(segundosAte(c, agora))
    return when (u) {
        Unidade.DIAS -> abs(diasAte(c.data, hoje))
        Unidade.SEMANAS -> abs(diasAte(c.data, hoje)) / 7
        Unidade.HORAS -> s / 3600
        Unidade.MINUTOS -> s / 60
        Unidade.FINS_DE_SEMANA -> finsDeSemana(hoje, c.data)
    }
}

/** Fração do caminho entre a criação e a data (só para eventos futuros). */
fun progresso(c: Contador, hoje: LocalDate): Float? {
    if (diasAte(c.data, hoje) < 0) return null
    val total = ChronoUnit.DAYS.between(c.criado, c.data)
    if (total <= 0) return 1f
    return (ChronoUnit.DAYS.between(c.criado, hoje).toFloat() / total).coerceIn(0f, 1f)
}

/** Texto do aviso do dia, ou null se hoje não é um marco. */
fun aviso(c: Contador, hoje: LocalDate): String? = when (diasAte(c.data, hoje)) {
    30L -> "Faltam 30 dias para ${c.nome}"
    7L -> "Falta uma semana para ${c.nome}"
    1L -> "É amanhã: ${c.nome}"
    0L -> "É hoje: ${c.nome}"
    else -> null
}

/** "12 anos, 3 meses e 5 dias" a partir de dias corridos (aproximado para meses). */
fun tempoLongo(dias: Long): String {
    val anos = dias / 365
    val meses = dias % 365 / 30
    val d = dias % 365 % 30
    val partes = buildList {
        if (anos > 0) add("$anos ${if (anos == 1L) "ano" else "anos"}")
        if (meses > 0) add("$meses ${if (meses == 1L) "mês" else "meses"}")
        if (d > 0 || isEmpty()) add("$d ${if (d == 1L) "dia" else "dias"}")
    }
    return if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " e " + partes.last()
}

fun padrao(hoje: LocalDate) = listOf(Contador("gta6", "Lançamento do GTA 6", LocalDate.of(2026, 11, 19), criado = hoje, cor = 0, icone = "jogo"))

data class Estado(val contadores: List<Contador>, val destaque: String?)

fun paraJson(e: Estado): String = JSONObject()
    .put("destaque", e.destaque ?: JSONObject.NULL)
    .put("contadores", JSONArray().apply {
        e.contadores.forEach {
            put(
                JSONObject()
                    .put("id", it.id).put("nome", it.nome).put("data", it.data.toString())
                    .put("hora", it.hora?.toString() ?: JSONObject.NULL)
                    .put("criado", it.criado.toString()).put("cor", it.cor).put("icone", it.icone)
            )
        }
    })
    .toString()

/** Lê o estado salvo. Campos novos ausentes (versões antigas) recebem valores padrão. */
fun deJson(s: String?, hoje: LocalDate): Estado {
    val padrao = padrao(hoje)
    if (s.isNullOrBlank()) return Estado(padrao, padrao.first().id)
    return try {
        val o = JSONObject(s)
        val arr = o.getJSONArray("contadores")
        val lista = (0 until arr.length()).map {
            val c = arr.getJSONObject(it)
            val id = c.getString("id")
            Contador(
                id = id,
                nome = c.getString("nome"),
                data = LocalDate.parse(c.getString("data")),
                hora = if (c.isNull("hora") || !c.has("hora")) null else LocalTime.parse(c.getString("hora")),
                criado = if (c.has("criado")) LocalDate.parse(c.getString("criado")) else hoje,
                cor = c.optInt("cor", 0),
                icone = c.optString("icone", if (id == "gta6") "jogo" else "evento"),
            )
        }
        Estado(lista, if (o.isNull("destaque")) null else o.getString("destaque"))
    } catch (e: Exception) {
        Estado(padrao, padrao.first().id)
    }
}

class Armazem(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("contador", Context.MODE_PRIVATE)
    fun ler(): Estado = deJson(prefs.getString("estado", null), LocalDate.now())
    fun salvar(e: Estado) {
        prefs.edit().putString("estado", paraJson(e)).apply()
        ContadorWidget.atualizarTodos(ctx, e)
    }
}

/**
 * Relógio dos widgets: dias inteiros + o resto (segundos dentro do dia) que o Chronometer do Android mostra correndo.
 * Futuro: conta para baixo até o resto zerar. Passado: conta para cima desde o último "aniversário" do horário.
 * Null quando é exatamente o momento do evento.
 */
data class RelogioWidget(val dias: Long, val resto: Long, val regressivo: Boolean)

fun relogioWidget(c: Contador, agora: LocalDateTime): RelogioWidget? {
    val s = segundosAte(c, agora)
    if (s == 0L) return null
    val a = abs(s)
    return RelogioWidget(a / 86400, a % 86400, s > 0)
}

/** Próximo momento em que os widgets precisam ser redesenhados: virada do dia ou fim do resto do destaque. */
fun proximoTique(destaque: Contador?, agora: LocalDateTime): LocalDateTime {
    val meiaNoite = agora.toLocalDate().plusDays(1).atStartOfDay()
    val r = destaque?.let { relogioWidget(it, agora) } ?: return meiaNoite
    val fim = if (r.regressivo) agora.plusSeconds(r.resto) else agora.plusSeconds(86400 - r.resto)
    return minOf(meiaNoite, fim)
}

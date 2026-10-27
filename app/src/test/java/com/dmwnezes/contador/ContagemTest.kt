package com.dmwnezes.contador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ContagemTest {
    private val hoje = LocalDate.of(2026, 10, 27) // terça-feira
    private val gta = Contador("gta6", "GTA 6", LocalDate.of(2026, 11, 19), criado = LocalDate.of(2026, 10, 7))

    @Test fun diasFuturoPassadoHoje() {
        assertEquals(23L, diasAte(LocalDate.of(2026, 11, 19), hoje))
        assertEquals(0L, diasAte(hoje, hoje))
        assertEquals(-4788L, diasAte(LocalDate.of(2013, 9, 17), hoje))
    }

    @Test fun ordemFuturosDepoisPassados() {
        val a = Contador("a", "longe", LocalDate.of(2027, 1, 1))
        val b = Contador("b", "perto", LocalDate.of(2026, 11, 1))
        val c = Contador("c", "antigo", LocalDate.of(2020, 1, 1))
        val d = Contador("d", "recente", LocalDate.of(2026, 10, 1))
        assertEquals(listOf("b", "a", "d", "c"), ordenar(listOf(a, c, b, d), hoje).map { it.id })
    }

    @Test fun sabadosNoIntervalo() {
        assertEquals(0L, finsDeSemana(hoje, hoje))
        assertEquals(1L, finsDeSemana(hoje, LocalDate.of(2026, 11, 1))) // sáb 31/10
        assertEquals(0L, finsDeSemana(hoje, LocalDate.of(2026, 10, 31))) // fim exclusivo
        assertEquals(3L, finsDeSemana(hoje, gta.data)) // 31/10, 7/11 e 14/11
        assertEquals(3L, finsDeSemana(gta.data, hoje)) // ordem não importa
    }

    @Test fun unidades() {
        val agora = LocalDateTime.of(2026, 10, 27, 12, 0)
        assertEquals(23L, valorEm(Unidade.DIAS, gta, agora))
        assertEquals(3L, valorEm(Unidade.SEMANAS, gta, agora))
        assertEquals(22L * 24 + 12, valorEm(Unidade.HORAS, gta, agora))
        val comHora = gta.copy(hora = LocalTime.of(21, 30))
        assertEquals(23L * 24 + 9, valorEm(Unidade.HORAS, comHora, agora))
        assertEquals("fim de semana", Unidade.FINS_DE_SEMANA.rotulo(1))
    }

    @Test fun progressoDoCaminho() {
        assertEquals(20f / 43f, progresso(gta, hoje)!!, 0.0001f)
        assertEquals(1f, progresso(gta.copy(criado = gta.data), gta.data)!!, 0f)
        assertNull(progresso(gta, LocalDate.of(2026, 11, 20)))
    }

    @Test fun avisosNosMarcos() {
        assertEquals("Faltam 30 dias para GTA 6", aviso(gta, gta.data.minusDays(30)))
        assertEquals("Falta uma semana para GTA 6", aviso(gta, gta.data.minusDays(7)))
        assertEquals("É amanhã: GTA 6", aviso(gta, gta.data.minusDays(1)))
        assertEquals("É hoje: GTA 6", aviso(gta, gta.data))
        assertNull(aviso(gta, hoje))
    }

    @Test fun tempoLongoPorExtenso() {
        assertEquals("0 dias", tempoLongo(0))
        assertEquals("1 ano", tempoLongo(365))
        assertEquals("1 ano, 1 mês e 1 dia", tempoLongo(396))
        assertEquals("2 meses e 3 dias", tempoLongo(63))
    }

    @Test fun jsonIdaEVolta() {
        val e = Estado(listOf(gta.copy(nome = "Viagem \"SP\"", hora = LocalTime.of(8, 15), cor = 3, icone = "viagem")), "gta6")
        assertEquals(e, deJson(paraJson(e), hoje))
        assertEquals(Estado(listOf(), null), deJson(paraJson(Estado(listOf(), null)), hoje))
    }

    @Test fun jsonAntigoGanhaPadroes() {
        val antigo = """{"destaque":"gta6","contadores":[{"id":"gta6","nome":"GTA","data":"2026-11-19"},{"id":"1","nome":"x","data":"2024-04-21"}]}"""
        val e = deJson(antigo, hoje)
        assertEquals(Contador("gta6", "GTA", LocalDate.of(2026, 11, 19), null, hoje, 0, "jogo"), e.contadores[0])
        assertEquals("evento", e.contadores[1].icone)
    }

    @Test fun relogioDosWidgets() {
        val agora = LocalDateTime.of(2026, 10, 27, 17, 30, 15)
        assertEquals(RelogioWidget(22, 6 * 3600 + 29 * 60 + 45, true), relogioWidget(gta, agora))
        val passado = Contador("p", "x", LocalDate.of(2026, 10, 20))
        assertEquals(RelogioWidget(7, 17 * 3600 + 30 * 60 + 15, false), relogioWidget(passado, agora))
        assertNull(relogioWidget(gta, gta.data.atStartOfDay()))
    }

    @Test fun proximoTiqueDosWidgets() {
        val agora = LocalDateTime.of(2026, 10, 27, 17, 30, 15)
        assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0), proximoTique(gta, agora))
        val comHora = gta.copy(hora = LocalTime.of(21, 0))
        assertEquals(LocalDateTime.of(2026, 10, 27, 21, 0), proximoTique(comHora, agora))
        assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0), proximoTique(null, agora))
    }

    @Test fun versaoDaTag() {
        assertEquals(12, codigoDaTag("v1.0.12"))
        assertEquals(3, codigoDaTag("v1.0.3"))
        assertNull(codigoDaTag("sem-numero"))
    }

    @Test fun periodosDoDia() {
        assertEquals(Periodo.MANHA, periodo(5))
        assertEquals(Periodo.MANHA, periodo(11))
        assertEquals(Periodo.TARDE, periodo(12))
        assertEquals(Periodo.NOITE, periodo(18))
        assertEquals(Periodo.NOITE, periodo(2))
    }

    @Test fun vazioOuInvalidoVoltaAoPadrao() {
        assertEquals(padrao(hoje), deJson(null, hoje).contadores)
        assertEquals(padrao(hoje), deJson("lixo", hoje).contadores)
    }
}

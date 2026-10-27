package com.dmwnezes.contador

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContagemTest {
    private val hoje = LocalDate.of(2026, 10, 27)

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

    @Test fun jsonIdaEVolta() {
        val e = Estado(listOf(Contador("x", "Viagem \"SP\"", LocalDate.of(2027, 2, 3))), "x")
        assertEquals(e, deJson(paraJson(e)))
        assertEquals(Estado(listOf(), null), deJson(paraJson(Estado(listOf(), null))))
    }

    @Test fun vazioOuInvalidoVoltaAoPadrao() {
        assertEquals(PADRAO, deJson(null).contadores)
        assertEquals(PADRAO, deJson("lixo").contadores)
    }
}

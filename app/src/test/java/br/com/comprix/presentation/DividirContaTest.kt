package br.com.comprix.presentation

import br.com.comprix.presentation.comparacao.DivisorDeConta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do reparte da secao "Dividir a conta" (ResumoDaCompraConcluida).
 *
 * O contrato importa mais que o exemplo: a soma das partes SEMPRE devolve o
 * total e duas pessoas quaisquer diferem em no maximo 1 centavo.
 */
class DividirContaTest {

    @Test
    fun `divisao exata reparte igual`() {
        assertEquals(listOf(1500L, 1500L, 1500L, 1500L), DivisorDeConta.dividirConta(6000L, 4))
    }

    @Test
    fun `resto vira 1 centavo extra nas primeiras pessoas`() {
        // 1000 / 3 = 333 com resto 1 -> 334 + 333 + 333 = 1000.
        assertEquals(listOf(334L, 333L, 333L), DivisorDeConta.dividirConta(1000L, 3))
    }

    @Test
    fun `um centavo entre doze pessoas fica so na primeira`() {
        assertEquals(listOf(1L) + List(11) { 0L }, DivisorDeConta.dividirConta(1L, 12))
    }

    @Test
    fun `total zero devolve partes zeradas`() {
        assertEquals(listOf(0L, 0L, 0L), DivisorDeConta.dividirConta(0L, 3))
    }

    @Test
    fun `total negativo e tratado como zero`() {
        assertEquals(listOf(0L, 0L), DivisorDeConta.dividirConta(-500L, 2))
    }

    @Test
    fun `pessoas nao positivas devolve lista vazia`() {
        assertTrue(DivisorDeConta.dividirConta(1000L, 0).isEmpty())
        assertTrue(DivisorDeConta.dividirConta(1000L, -2).isEmpty())
    }

    @Test
    fun `soma sempre devolve o total e partes diferem de no maximo 1 centavo`() {
        // Varre a faixa real da tela (2 a 12 pessoas) por varios totais.
        for (pessoas in 2..12) {
            for (total in longArrayOf(0L, 1L, 199L, 10_000L, 123_457L, 987_654_321L)) {
                val partes = DivisorDeConta.dividirConta(total, pessoas)
                assertEquals(pessoas, partes.size)
                assertEquals(total, partes.sum())
                assertTrue(
                    "partes deviam diferir de no maximo 1 centavo",
                    (partes.max() - partes.min()) <= 1L,
                )
            }
        }
    }
}

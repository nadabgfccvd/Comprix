package br.com.comprix

import br.com.comprix.util.Formatadores
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros do rotulo de expiracao da Lixeira: a linha do cartao mostra
 * "Excluido permanentemente em dd/MM", entao a formatacao precisa vir SEM o
 * ano (o prazo maximo e 30 dias, o ano e redundante ali).
 */
class LixeiraFormatoTest {

    @Test
    fun `expiracao sai no formato dia e mes`() {
        val quando = LocalDateTime.of(2026, 11, 9, 14, 30)
        assertEquals("09/11", Formatadores.dataSemAno(quando))
    }

    @Test
    fun `dia e mes de um algarismo recebem zero a esquerda`() {
        val quando = LocalDateTime.of(2026, 3, 5, 7, 5)
        assertEquals("05/03", Formatadores.dataSemAno(quando))
    }

    @Test
    fun `virada de mes e mantida sem ano`() {
        val quando = LocalDateTime.of(2026, 12, 31, 23, 59)
        assertEquals("31/12", Formatadores.dataSemAno(quando))
    }

    @Test
    fun `valor nulo volta string vazia`() {
        assertEquals("", Formatadores.dataSemAno(null as LocalDateTime?))
    }
}

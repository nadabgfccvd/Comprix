package br.com.comprix.domain

import br.com.comprix.domain.lista.HistoricoDeAdicoes
import br.com.comprix.domain.lista.HistoricoDeAdicoes.AdicaoHistorica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contratos do historico de adicoes (desfazer/refazer por lista).
 *
 * O [HistoricoDeAdicoes] e um objeto de processo, portanto o estado vazaria
 * entre casos de teste: cada teste comeca limpando as pilhas das listas que
 * vai usar, para nao depender da ordem de execucao do JUnit.
 */
class HistoricoDeAdicoesTest {

    private val listaA = 101L
    private val listaB = 202L

    @Before
    fun limparPilhas() {
        HistoricoDeAdicoes.limpar(listaA)
        HistoricoDeAdicoes.limpar(listaB)
    }

    @Test
    fun `pilhas comecam vazias e sao criadas on demand`() {
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
        assertNull(HistoricoDeAdicoes.desfazer(listaA))
        assertNull(HistoricoDeAdicoes.refazer(listaA))
    }

    @Test
    fun `registrar acumula no desfazer na ordem`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("feijao", listOf(2)))

        assertEquals(
            listOf(
                AdicaoHistorica("arroz", listOf(1)),
                AdicaoHistorica("feijao", listOf(2)),
            ),
            HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value,
        )
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
    }

    @Test
    fun `registrar adicao nova limpa o refazer`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.desfazer(listaA))
        assertTrue(HistoricoDeAdicoes.pilhaDeRefazer(listaA).value.isNotEmpty())

        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("feijao", listOf(2)))

        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
        // O refazer foi limpo: desfazer a adicao nova empurra so ELA de volta,
        // e refazer devolve feijao - a antiga (arroz) se foi para sempre.
        assertEquals(AdicaoHistorica("feijao", listOf(2)), HistoricoDeAdicoes.desfazer(listaA))
        assertEquals(AdicaoHistorica("feijao", listOf(2)), HistoricoDeAdicoes.refazer(listaA))
        assertNull(HistoricoDeAdicoes.refazer(listaA))
    }

    @Test
    fun `desfazer tira o topo do desfazer e empurra no refazer`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("feijao", listOf(2)))

        assertEquals(AdicaoHistorica("feijao", listOf(2)), HistoricoDeAdicoes.desfazer(listaA))
        assertEquals(listOf(AdicaoHistorica("arroz", listOf(1))), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)
        assertEquals(listOf(AdicaoHistorica("feijao", listOf(2))), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)

        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.desfazer(listaA))
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)
        assertNull(HistoricoDeAdicoes.desfazer(listaA))
    }

    @Test
    fun `refazer tira o topo do refazer e nao mexe no desfazer`() {
        assertNull(HistoricoDeAdicoes.refazer(listaA))

        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.desfazer(listaA)
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)

        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.refazer(listaA))
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
        assertNull(HistoricoDeAdicoes.refazer(listaA))
    }

    @Test
    fun `desfazer e refazer alternam como fila LIFO em cadeia`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("feijao", listOf(2)))

        HistoricoDeAdicoes.desfazer(listaA)
        HistoricoDeAdicoes.desfazer(listaA)
        assertEquals(
            listOf(
                AdicaoHistorica("feijao", listOf(2)),
                AdicaoHistorica("arroz", listOf(1)),
            ),
            HistoricoDeAdicoes.pilhaDeRefazer(listaA).value,
        )

        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.refazer(listaA))
        assertEquals(AdicaoHistorica("feijao", listOf(2)), HistoricoDeAdicoes.refazer(listaA))
        assertNull(HistoricoDeAdicoes.refazer(listaA))
    }

    @Test
    fun `empilharNoDesfazer preserva o refazer para cadeia`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.desfazer(listaA)

        HistoricoDeAdicoes.empilharNoDesfazer(listaA, AdicaoHistorica("feijao", listOf(2)))

        assertEquals(listOf(AdicaoHistorica("feijao", listOf(2))), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)
        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.refazer(listaA))
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
    }

    @Test
    fun `pilhas sao isoladas por lista`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.registrar(listaB, AdicaoHistorica("cafe", listOf(9)))

        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.desfazer(listaA))
        assertNull(HistoricoDeAdicoes.refazer(listaB))

        assertEquals(
            listOf(AdicaoHistorica("cafe", listOf(9))),
            HistoricoDeAdicoes.pilhaDeDesfazer(listaB).value,
        )

        // O desfazer da lista A alimentou so o refazer DA PROPRIA lista A.
        assertEquals(AdicaoHistorica("arroz", listOf(1)), HistoricoDeAdicoes.refazer(listaA))
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaB).value)
    }

    @Test
    fun `limpar zera as duas pilhas da lista e nao afeta as outras`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.desfazer(listaA)
        HistoricoDeAdicoes.registrar(listaB, AdicaoHistorica("cafe", listOf(9)))

        HistoricoDeAdicoes.limpar(listaA)

        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value)
        assertEquals(emptyList<AdicaoHistorica>(), HistoricoDeAdicoes.pilhaDeRefazer(listaA).value)
        assertNull(HistoricoDeAdicoes.desfazer(listaA))
        assertNull(HistoricoDeAdicoes.refazer(listaA))

        assertEquals(
            listOf(AdicaoHistorica("cafe", listOf(9))),
            HistoricoDeAdicoes.pilhaDeDesfazer(listaB).value,
        )

        // Limpar id nunca usado nao pode falhar.
        HistoricoDeAdicoes.limpar(999L)
    }

    @Test
    fun `desfazer em pilha vazia nao cria entrada no refazer`() {
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))
        HistoricoDeAdicoes.desfazer(listaA)
        assertNull(HistoricoDeAdicoes.desfazer(listaA))

        // O desfazer sem topo devolve null e NAO empurra nada no refazer:
        // a pilha continua so com o que o desfazer anterior deixou.
        assertEquals(
            listOf(AdicaoHistorica("arroz", listOf(1))),
            HistoricoDeAdicoes.pilhaDeRefazer(listaA).value,
        )
    }

    @Test
    fun `os fluxos por lista sao sempre a mesma pilha`() {
        val pilha = HistoricoDeAdicoes.pilhaDeDesfazer(listaA)
        HistoricoDeAdicoes.registrar(listaA, AdicaoHistorica("arroz", listOf(1)))

        // On-demand significa: novas consultas devolvem a MESMA pilha viva,
        // com o novo conteudo visivel - nunca um fluxo novo e vazio.
        assertEquals(1, HistoricoDeAdicoes.pilhaDeDesfazer(listaA).value.size)
        assertEquals(1, pilha.value.size)
    }
}

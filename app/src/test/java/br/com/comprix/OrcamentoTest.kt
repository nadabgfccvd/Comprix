package br.com.comprix

import br.com.comprix.data.repositorio.ListaRepositorio
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros da classificacao de nivel do orcamento da lista - exercitam
 * a funcao pura [ListaRepositorio.nivelDoOrcamento] sem DAO, sem banco e sem
 * Android. A doca da tela usa o nivel para escolher a cor da barra: verde
 * (tranquilo), ambar (atencao) e vermelho (estourou), com legenda do excesso.
 */
class OrcamentoTest {

    @Test
    fun `nivel zero ate 79 porcento do orcamento`() {
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(0, 10_000))
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(5_000, 10_000))
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(7_900, 10_000))
    }

    @Test
    fun `nivel um de 80 a 99 porcento`() {
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(8_000, 10_000))
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(8_500, 10_000))
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(9_999, 10_000))
    }

    @Test
    fun `nivel dois a partir de cem porcento`() {
        assertEquals(2, ListaRepositorio.nivelDoOrcamento(10_000, 10_000))
        assertEquals(2, ListaRepositorio.nivelDoOrcamento(12_340, 10_000))
        assertEquals(2, ListaRepositorio.nivelDoOrcamento(25_000, 10_000))
    }

    @Test
    fun `fronteiras exatas entre os niveis`() {
        // 79,99% (7999) ainda e tranquilo; 80% exato ja e atencao; 99,99%
        // (9999) segue em atencao; 100% exato estoura. A divisao inteira
        // truncada e a regra - sem caso de borda duvidoso.
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(7_999, 10_000))
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(8_000, 10_000))
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(9_999, 10_000))
        assertEquals(2, ListaRepositorio.nivelDoOrcamento(10_000, 10_000))
    }

    @Test
    fun `total zero fica no nivel zero`() {
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(0, 10_000))
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(0, 1))
    }

    @Test
    fun `orcamento zero ou negativo nao tem nivel`() {
        // Sem orcamento valido a tela nem desenha a linha; a funcao nao pode
        // dividir por zero nem inventar estouro.
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(10_000, 0))
        assertEquals(0, ListaRepositorio.nivelDoOrcamento(10_000, -1))
    }

    @Test
    fun `valores fora da casa do exemplo tambem classificam`() {
        // Orcamento pequeno (R$ 25,00) com total de R$ 20,00 = 80% = atencao.
        assertEquals(1, ListaRepositorio.nivelDoOrcamento(2_000, 2_500))
        // Orcamento de R$ 1,00 com total de R$ 3,00 = 300% = estourou.
        assertEquals(2, ListaRepositorio.nivelDoOrcamento(300, 100))
    }
}

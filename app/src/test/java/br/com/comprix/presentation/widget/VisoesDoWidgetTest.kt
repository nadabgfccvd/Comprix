package br.com.comprix.presentation.widget

import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.ResumoDeLista
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM puros das regras de exibição do widget da tela inicial
 * ([VisoesDoWidget]): título do estado vazio, convite da contagem, limites
 * da barra de progresso e a linha do estimado que some sem preço anotado.
 *
 * O provider ([ListaWidgetProvider]) só traduz este estado em `RemoteViews`
 * — qualquer regressão nestes textos ou limites agora é presa antes do
 * merge, não só no aparelho.
 */
class VisoesDoWidgetTest {

    private fun resumo(
        nome: String = "Churrasco de sábado",
        totalDeItens: Int = 5,
        itensComprados: Int = 3,
        totalEstimado: BigDecimal = BigDecimal.ZERO,
    ) = ResumoDeLista(
        lista = ListaDeCompras(id = 1, nome = nome),
        totalDeItens = totalDeItens,
        itensComprados = itensComprados,
        totalEstimado = totalEstimado,
        itensSemPreco = 0,
    )

    // --- estado vazio -----------------------------------------------------------------

    @Test
    fun `widget recem-colocado sem resumo mostra o estado vazio`() {
        val estado = VisoesDoWidget.estado(null)
        assertEquals("Nenhuma lista aberta", estado.textoNome)
        assertEquals(0, estado.maximoDaBarra)
        assertEquals(0, estado.progressoDaBarra)
        assertEquals("Abra o app para comecar", estado.textoContagem)
        assertFalse(estado.estimadoVisivel)
    }

    @Test
    fun `lista com nome em branco recebe o titulo vazio mas mantem a contagem real`() {
        val estado = VisoesDoWidget.estado(resumo(nome = "   ", totalDeItens = 5, itensComprados = 2))
        assertEquals("Nenhuma lista aberta", estado.textoNome)
        assertEquals("2 de 5 comprados", estado.textoContagem)
    }

    @Test
    fun `lista sem itens convida a abrir o app e barra fica em 0`() {
        val estado = VisoesDoWidget.estado(resumo(totalDeItens = 0, itensComprados = 0))
        assertEquals("Churrasco de sábado", estado.textoNome)
        assertEquals("Abra o app para comecar", estado.textoContagem)
        assertEquals(0, estado.maximoDaBarra)
        assertEquals(0, estado.progressoDaBarra)
    }

    // --- contagem e barra -------------------------------------------------------------

    @Test
    fun `contagem formata comprados de total e barra recebe os limites`() {
        val estado = VisoesDoWidget.estado(resumo(totalDeItens = 5, itensComprados = 3))
        assertEquals("3 de 5 comprados", estado.textoContagem)
        assertEquals(5, estado.maximoDaBarra)
        assertEquals(3, estado.progressoDaBarra)
    }

    @Test
    fun `lista completa deixa a barra cheia`() {
        val estado = VisoesDoWidget.estado(resumo(totalDeItens = 4, itensComprados = 4))
        assertEquals("4 de 4 comprados", estado.textoContagem)
        assertEquals(4, estado.progressoDaBarra)
        assertEquals(estado.maximoDaBarra, estado.progressoDaBarra)
    }

    // --- linha do estimado ------------------------------------------------------------

    @Test
    fun `estimado positivo aparece formatado em moeda pt-BR`() {
        val estado = VisoesDoWidget.estado(
            resumo(totalEstimado = BigDecimal("214.90")),
        )
        assertTrue(estado.estimadoVisivel)
        assertEquals("≈ R$ 214,90 estimados", estado.textoEstimado)
    }

    @Test
    fun `estimado com milhar usa o separador pt-BR`() {
        val estado = VisoesDoWidget.estado(
            resumo(totalEstimado = BigDecimal("1234.50")),
        )
        assertTrue(estado.estimadoVisivel)
        assertEquals("≈ R$ 1.234,50 estimados", estado.textoEstimado)
    }

    @Test
    fun `estimado zero fica escondido para o widget caber em duas linhas`() {
        val estado = VisoesDoWidget.estado(resumo(totalEstimado = BigDecimal.ZERO))
        assertFalse(estado.estimadoVisivel)
    }

    @Test
    fun `sem resumo o estimado tambem fica escondido`() {
        val estado = VisoesDoWidget.estado(null)
        assertFalse(estado.estimadoVisivel)
    }

    // --- guardas de construção --------------------------------------------------------

    @Test
    fun `resumo preserva a data de criacao intacta`() {
        val criada = LocalDateTime.of(2026, 11, 6, 9, 30)
        val estado = VisoesDoWidget.estado(
            ResumoDeLista(
                lista = ListaDeCompras(id = 1, nome = "Lista", criadaEm = criada),
                totalDeItens = 1,
                itensComprados = 0,
                totalEstimado = BigDecimal.ZERO,
                itensSemPreco = 1,
            ),
        )
        // O widget não deve consumir campos além do estado pintável — nada de
        // mutação escondida no caminho de desenho.
        assertEquals("Lista", estado.textoNome)
        assertEquals("0 de 1 comprados", estado.textoContagem)
    }
}

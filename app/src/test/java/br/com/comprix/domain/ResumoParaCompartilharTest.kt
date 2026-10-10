package br.com.comprix.domain

import br.com.comprix.domain.compra.ResumoParaCompartilhar
import br.com.comprix.domain.modelo.TotalEstabelecimento
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM puros do [ResumoParaCompartilhar]: o texto que a folha de
 * celebração entrega ao ACTION_SEND. Garante a gramática do resumo — total,
 * economia condicionada, "Onde:" na loja única e "Lojas:" na compra mista —
 * porque esse texto sai do app e vira mensagem de WhatsApp.
 */
class ResumoParaCompartilharTest {

    private val quando = LocalDateTime.of(2026, 10, 10, 12, 30)

    private fun loja(
        nome: String = "Mercado Central",
        total: String = "187.42",
        itens: Int = 23,
    ) = TotalEstabelecimento(
        estabelecimentoId = 1L,
        nome = nome,
        corHex = "#10B981",
        total = BigDecimal(total),
        itensDisponiveis = itens,
        itensAusentes = emptyList(),
        itensSemPreco = emptyList(),
        cestaCompleta = true,
    )

    @Test
    fun `resumo de loja unica traz cabecalho, total e onde`() {
        val texto = ResumoParaCompartilhar.texto(
            nomeDaLista = "Compra do mês",
            quando = quando,
            totalPago = BigDecimal("187.42"),
            economia = BigDecimal.ZERO,
            economiaPercentual = BigDecimal.ZERO,
            quantidadeItens = 23,
            descricaoDoEstabelecimento = "Mercado Central",
            totaisPorLoja = listOf(loja()),
        )

        assertTrue(texto.startsWith("Compra concluída (Comprix) — Compra do mês"))
        assertTrue(texto.contains("10/10/2026"))
        assertTrue(texto.contains("Itens: 23"))
        assertTrue(texto.contains("Total: R$ 187,42"))
        assertTrue(texto.contains("Onde: Mercado Central"))
        assertFalse(texto.contains("Economia:"))
        assertFalse(texto.contains("Lojas:"))
    }

    @Test
    fun `economia aparece com valor e percentual`() {
        val texto = ResumoParaCompartilhar.texto(
            nomeDaLista = "Compra do mês",
            quando = quando,
            totalPago = BigDecimal("187.42"),
            economia = BigDecimal("12.30"),
            economiaPercentual = BigDecimal("6.2"),
            quantidadeItens = 23,
            descricaoDoEstabelecimento = "Mercado Central",
        )

        assertTrue(texto.contains("Economia: R$ 12,30 (6,2%)"))
    }

    @Test
    fun `compra mista lista o pedaco de cada loja e omite o onde`() {
        val texto = ResumoParaCompartilhar.texto(
            nomeDaLista = null,
            quando = quando,
            totalPago = BigDecimal("187.42"),
            economia = BigDecimal("12.30"),
            economiaPercentual = BigDecimal("6.2"),
            quantidadeItens = 23,
            descricaoDoEstabelecimento = "Compra mista em 2 lojas",
            totaisPorLoja = listOf(
                loja(nome = "Mercado Central", total = "98.20", itens = 12),
                loja(nome = "Atacado Bom", total = "89.22", itens = 11),
            ),
        )

        assertTrue(texto.startsWith("Compra concluída (Comprix)"))
        assertFalse(texto.contains(" — Compra"))
        assertTrue(texto.contains("Economia: R$ 12,30 (6,2%)"))
        assertTrue(texto.contains("Lojas:"))
        assertTrue(texto.contains("• Mercado Central — R$ 98,20 (12 itens)"))
        assertTrue(texto.contains("• Atacado Bom — R$ 89,22 (11 itens)"))
        assertFalse(texto.contains("Onde:"))
    }

    @Test
    fun `loja sem itens disponiveis nao entra na divisao`() {
        val texto = ResumoParaCompartilhar.texto(
            nomeDaLista = "Rapidinha",
            quando = quando,
            totalPago = BigDecimal("50.00"),
            economia = BigDecimal.ZERO,
            economiaPercentual = BigDecimal.ZERO,
            quantidadeItens = 5,
            descricaoDoEstabelecimento = "Compra mista em 2 lojas",
            totaisPorLoja = listOf(
                loja(nome = "Loja Vazia", total = "0", itens = 0),
                loja(nome = "Loja Cheia", total = "50.00", itens = 5),
            ),
        )

        assertFalse(texto.contains("Loja Vazia"))
        assertFalse(texto.contains("Lojas:"))
        assertTrue(texto.contains("Onde: Compra mista em 2 lojas"))
        assertTrue(texto.contains("Total: R$ 50,00"))
    }

    @Test
    fun `resumo nunca sai vazio nem com linha quebrada no cabecalho`() {
        val texto = ResumoParaCompartilhar.texto(
            nomeDaLista = "",
            quando = quando,
            totalPago = BigDecimal.ZERO,
            economia = BigDecimal.ZERO,
            economiaPercentual = BigDecimal.ZERO,
            quantidadeItens = 0,
            descricaoDoEstabelecimento = "",
        )

        assertEquals("Compra concluída (Comprix)", texto.split("\n").first())
        assertTrue(texto.contains("Itens: 0"))
    }
}

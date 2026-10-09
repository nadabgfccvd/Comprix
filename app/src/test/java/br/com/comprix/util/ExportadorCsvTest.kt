package br.com.comprix.util

import br.com.comprix.domain.modelo.CompraFinalizada
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM puros do [ExportadorCsv]: BOM para o Excel, cabecalho fixo,
 * formato pt-BR (data dd/MM/yyyy, dinheiro com virgula) e o escape RFC 4180
 * de campos com `;` ou aspas.
 */
class ExportadorCsvTest {

    private fun compra(
        nomeLista: String = "Compras de março",
        loja: String = "Mercado Central",
        total: String = "123.45",
        economia: String = "7.50",
        itens: Int = 4,
        data: LocalDateTime = LocalDateTime.of(2026, 3, 9, 15, 42),
    ) = CompraFinalizada(
        listaId = 1L,
        nomeLista = nomeLista,
        data = data,
        totalPago = BigDecimal(total),
        economia = BigDecimal(economia),
        quantidadeItens = itens,
        descricaoEstabelecimento = loja,
        estabelecimentoPrincipalId = 1L,
    )

    @Test
    fun `arquivo comeca com BOM e o cabecalho fixo`() {
        val csv = ExportadorCsv.comprasCsv(emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals("\uFEFF" + ExportadorCsv.CABECALHO, csv.trim())
        assertEquals(
            "data;lista;loja;itens;total;economia",
            ExportadorCsv.CABECALHO,
        )
    }

    @Test
    fun `linha simples sai na ordem do cabecalho`() {
        val csv = ExportadorCsv.comprasCsv(listOf(compra()))
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals(2, linhas.size)
        assertEquals(
            "09/03/2026;Compras de março;Mercado Central;4;123,45;7,50",
            linhas[1],
        )
    }

    @Test
    fun `dinheiro sai com virgula decimal e duas casas`() {
        val csv = ExportadorCsv.comprasCsv(
            listOf(compra(total = "1234.5", economia = "0.05")),
        )
        // moedaSemSimbolo mantem o separador de milhar pt-BR: "1.234,50".
        assertTrue(csv.contains(";1.234,50;0,05"))
    }

    @Test
    fun `campo com ponto e virgula e envolto em aspas`() {
        val csv = ExportadorCsv.comprasCsv(
            listOf(compra(loja = "Mercado; Atacado")),
        )
        val linha = csv.trim().split("\n")[1]
        assertEquals(
            "09/03/2026;Compras de março;\"Mercado; Atacado\";4;123,45;7,50",
            linha,
        )
    }

    @Test
    fun `aspas internas sao dobradas dentro do campo escapado`() {
        val csv = ExportadorCsv.comprasCsv(
            listOf(compra(nomeLista = "Lista \"bônus\"")),
        )
        val linha = csv.trim().split("\n")[1]
        assertTrue(linha.contains("\"Lista \"\"bônus\"\"\""))
    }

    @Test
    fun `campo sem caracteres especiais nao recebe aspas`() {
        val csv = ExportadorCsv.comprasCsv(listOf(compra()))
        val linha = csv.trim().split("\n")[1]
        assertTrue(linha.contains("Mercado Central"))
        assertTrue(!linha.contains("\""))
    }
}

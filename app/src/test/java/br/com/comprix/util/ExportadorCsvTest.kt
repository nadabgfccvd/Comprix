package br.com.comprix.util

import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.PrecoDoHistorico
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

    // ---- CSV de precos (historico global) --------------------------------

    private fun preco(
        produto: String = "Arroz tipo 1 5kg",
        loja: String = "Mercado Central",
        valor: String = "27.90",
        quantidade: String = "5 kg",
        data: LocalDateTime = LocalDateTime.of(2026, 3, 9, 10, 30),
    ) = PrecoDoHistorico(
        produto = produto,
        loja = loja,
        preco = BigDecimal(valor),
        quantidade = quantidade,
        quando = data,
    )

    @Test
    fun `csv de precos tem cabecalho fixo e linha pt-BR`() {
        val csv = ExportadorCsv.precosCsv(listOf(preco()))
        assertTrue(csv.startsWith("\uFEFF"))
        // O trim() do JVM NAO remove o BOM (U+FEFF vem depois do espaco na
        // tabela): tiramos na mao antes de fatiar, igual o Excel enxerga.
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals("produto;loja;preco;quantidade;data", linhas[0])
        assertEquals(
            "Arroz tipo 1 5kg;Mercado Central;27,90;5 kg;09/03/2026",
            linhas[1],
        )
    }

    @Test
    fun `csv de precos escapa produto com ponto e virgula e aspas`() {
        val csv = ExportadorCsv.precosCsv(
            listOf(preco(produto = "Óleo \"20L\"; bidão")),
        )
        val linha = csv.trim().split("\n")[1]
        assertTrue(linha.startsWith("\"Óleo \"\"20L\"\"; bidão\";Mercado Central;27,90"))
    }

    @Test
    fun `csv de precos sem registros sai so com o cabecalho`() {
        val csv = ExportadorCsv.precosCsv(emptyList())
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals(1, linhas.size)
        assertEquals(ExportadorCsv.CABECALHO_PRECOS, linhas[0])
    }

    @Test
    fun `csv de precos usa traco quando a quantidade vem vazia`() {
        val csv = ExportadorCsv.precosCsv(listOf(preco(quantidade = "")))
        assertTrue(csv.contains(";27,90;-;09/03/2026"))
    }

    // --- matriz de comparacao ------------------------------------------------------------

    @Test
    fun `csv da matriz sai com uma coluna por loja e melhor loja`() {
        val csv = ExportadorCsv.matrizCsv(
            nomesDasLojas = listOf("Mercado A", "Mercado B"),
            linhas = listOf(
                ExportadorCsv.LinhaDaMatrizCsv(
                    descricao = "Arroz tipo 1",
                    detalhe = "5 kg",
                    precoPorLoja = listOf(BigDecimal("27.90"), BigDecimal("26.50")),
                    indiceDoMelhor = 1,
                ),
            ),
        )
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals(2, linhas.size)
        assertEquals("produto;detalhe;Mercado A;Mercado B;melhor loja", linhas[0])
        assertEquals("Arroz tipo 1;5 kg;27,90;26,50;Mercado B", linhas[1])
    }

    @Test
    fun `csv da matriz usa traco para celula sem preco e sem melhor`() {
        val csv = ExportadorCsv.matrizCsv(
            nomesDasLojas = listOf("Mercado A", "Mercado B"),
            linhas = listOf(
                ExportadorCsv.LinhaDaMatrizCsv(
                    descricao = "Café torrado 500g",
                    detalhe = "",
                    precoPorLoja = listOf(null, BigDecimal.ZERO),
                ),
            ),
        )
        val linha = csv.removePrefix("\uFEFF").trim().split("\n")[1]
        assertEquals("Café torrado 500g;-;-;-;-", linha)
    }

    @Test
    fun `csv da matriz escapa loja com ponto e virgula no cabecalho e na melhor loja`() {
        val csv = ExportadorCsv.matrizCsv(
            nomesDasLojas = listOf("Mercado; Atacado"),
            linhas = listOf(
                ExportadorCsv.LinhaDaMatrizCsv(
                    descricao = "Leite integral",
                    detalhe = "1 L",
                    precoPorLoja = listOf(BigDecimal("5.49")),
                    indiceDoMelhor = 0,
                ),
            ),
        )
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals("produto;detalhe;\"Mercado; Atacado\";melhor loja", linhas[0])
        assertEquals("Leite integral;1 L;5,49;\"Mercado; Atacado\"", linhas[1])
    }

    @Test
    fun `csv da matriz sem linhas sai so com o cabecalho`() {
        val csv = ExportadorCsv.matrizCsv(nomesDasLojas = listOf("Mercado A"), linhas = emptyList())
        val linhas = csv.removePrefix("\uFEFF").trim().split("\n")
        assertEquals(1, linhas.size)
        assertEquals("produto;detalhe;Mercado A;melhor loja", linhas[0])
    }
}

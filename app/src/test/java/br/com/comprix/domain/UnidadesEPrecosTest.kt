package br.com.comprix.domain

import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.OpcaoDeCompra
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.domain.unidade.ConversorDeUnidades
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras de unidade e de preço (Seções 4.1 e 4.2) - o coração financeiro do app.
 *
 * Cada teste aqui corresponde a uma frase do documento: se um deles cair, o
 * app passou a mentir sobre dinheiro, que é o pior defeito possível.
 */
class UnidadesEPrecosTest {

    // --- conversão ---------------------------------------------------------------------

    @Test
    fun `quilo vira mil gramas na unidade base`() {
        val base = ConversorDeUnidades.paraUnidadeBase(BigDecimal("1.5"), Unidade.QUILO)
        assertEquals(0, base.compareTo(BigDecimal("1500")))
    }

    @Test
    fun `litro vira mil mililitros na unidade base`() {
        val base = ConversorDeUnidades.paraUnidadeBase(BigDecimal("2"), Unidade.LITRO)
        assertEquals(0, base.compareTo(BigDecimal("2000")))
    }

    @Test
    fun `duzia conta doze unidades`() {
        val base = ConversorDeUnidades.paraUnidadeBase(BigDecimal.ONE, Unidade.DUZIA)
        assertEquals(0, base.compareTo(BigDecimal("12")))
    }

    @Test
    fun `embalagem multipla multiplica o conteudo`() {
        // 12 latas de 350 mL = 4.200 mL
        val base = ConversorDeUnidades.paraUnidadeBase(BigDecimal("350"), Unidade.MILILITRO, itensPorEmbalagem = 12)
        assertEquals(0, base.compareTo(BigDecimal("4200")))
    }

    @Test
    fun `quantidade negativa nao vira base negativa`() {
        val base = ConversorDeUnidades.paraUnidadeBase(BigDecimal("-3"), Unidade.QUILO)
        assertEquals(0, base.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `nao se converte massa em volume`() {
        assertNull(ConversorDeUnidades.converter(BigDecimal.ONE, Unidade.QUILO, Unidade.LITRO))
        assertFalse(ConversorDeUnidades.saoComparaveis(Unidade.QUILO, Unidade.LITRO))
        assertTrue(ConversorDeUnidades.saoComparaveis(Unidade.GRAMA, Unidade.QUILO))
    }

    @Test
    fun `texto livre com peso vira quantidade interpretada`() {
        val interpretado = ConversorDeUnidades.interpretarTexto("arroz 5kg")
        assertNotNull(interpretado)
        assertEquals(Unidade.QUILO, interpretado!!.unidade)
        assertEquals(0, interpretado.quantidade.compareTo(BigDecimal("5")))
    }

    @Test
    fun `texto de pack traz conteudo e quantidade por embalagem`() {
        val interpretado = ConversorDeUnidades.interpretarTexto("cerveja 12x350ml")
        assertNotNull(interpretado)
        assertEquals(Unidade.MILILITRO, interpretado!!.unidade)
        assertEquals(12, interpretado.itensPorEmbalagem)
        assertEquals(0, interpretado.quantidade.compareTo(BigDecimal("350")))
    }

    @Test
    fun `quantidade total considera o numero de embalagens`() {
        val item = ItemDaLista(
            listaId = 1,
            produtoId = 1,
            quantidade = BigDecimal("2"),
            unidade = Unidade.GRAMA,
            pesoOuVolume = BigDecimal("500"),
        )
        assertEquals(0, ConversorDeUnidades.quantidadeTotalEmBase(item).compareTo(BigDecimal("1000")))
    }

    // --- preço por unidade-base --------------------------------------------------------

    @Test
    fun `preco por unidade base divide pelo conteudo total`() {
        // R$ 25,00 por 5 kg = R$ 0,005 por grama
        val item = ItemDaLista(
            listaId = 1,
            produtoId = 1,
            unidade = Unidade.QUILO,
            pesoOuVolume = BigDecimal("5"),
            modoComparacao = ModoComparacaoUnidade.POR_PESO,
        )
        val porBase = MotorDePrecos.precoPorUnidadeBase(BigDecimal("25.00"), item)
        assertNotNull(porBase)
        assertEquals(0, porBase!!.compareTo(BigDecimal("0.005")))
    }

    @Test
    fun `sem preco nao existe preco por unidade`() {
        val item = ItemDaLista(listaId = 1, produtoId = 1, unidade = Unidade.UNIDADE)
        assertNull(MotorDePrecos.precoPorUnidadeBase(null, item))
    }

    @Test
    fun `total da linha multiplica preco pela quantidade de embalagens`() {
        val item = ItemDaLista(listaId = 1, produtoId = 1, quantidade = BigDecimal("3"))
        val total = MotorDePrecos.totalDaLinha(BigDecimal("4.50"), item)
        assertEquals(0, total.compareTo(BigDecimal("13.50")))
    }

    // --- kit x avulso -------------------------------------------------------------------

    @Test
    fun `fardo mais barato por unidade vence o avulso`() {
        val fardo = OpcaoDeCompra(
            rotulo = "Fardo com 12",
            preco = BigDecimal("36.00"),
            quantidade = BigDecimal("350"),
            unidade = Unidade.MILILITRO,
            itensPorEmbalagem = 12,
        )
        val avulso = OpcaoDeCompra(
            rotulo = "Lata avulsa",
            preco = BigDecimal("3.50"),
            quantidade = BigDecimal("350"),
            unidade = Unidade.MILILITRO,
        )
        val veredito = MotorDePrecos.compararOpcoes(fardo, avulso)
        assertNotNull(veredito)
        assertEquals("Fardo com 12", veredito!!.melhor.rotulo)
        assertFalse(veredito.equivalentes)
        // 12 x 3,50 = 42,00 contra 36,00 -> economia de 6,00 (14,29%)
        assertEquals(0, veredito.economiaReais.compareTo(BigDecimal("6.00")))
        assertTrue(veredito.economiaPercentual > BigDecimal("14"))
        assertTrue(veredito.mensagem.isNotBlank())
    }

    @Test
    fun `diferenca abaixo de meio por cento e considerada empate`() {
        val kit = OpcaoDeCompra(
            rotulo = "Pack 2",
            preco = BigDecimal("10.00"),
            quantidade = BigDecimal("1"),
            unidade = Unidade.QUILO,
            itensPorEmbalagem = 2,
        )
        val unidade = OpcaoDeCompra(
            rotulo = "Avulso",
            preco = BigDecimal("5.01"),
            quantidade = BigDecimal("1"),
            unidade = Unidade.QUILO,
        )
        val veredito = MotorDePrecos.compararOpcoes(kit, unidade)
        assertNotNull(veredito)
        assertTrue("diferença de 0,2% deveria empatar", veredito!!.equivalentes)
    }

    @Test
    fun `opcoes de dimensoes diferentes nao sao comparaveis`() {
        val porPeso = OpcaoDeCompra("A", BigDecimal("10"), BigDecimal("1"), Unidade.QUILO)
        val porVolume = OpcaoDeCompra("B", BigDecimal("10"), BigDecimal("1"), Unidade.LITRO)
        assertNull(MotorDePrecos.compararOpcoes(porPeso, porVolume))
    }

    // --- matriz -------------------------------------------------------------------------

    private val categoria = Categoria(id = 1, nome = "Mercearia", icone = "mercearia", ordemPadrao = 10, chave = "mercearia")

    private fun item(id: Long, nome: String, quantidade: String = "1"): ItemComProduto = ItemComProduto(
        item = ItemDaLista(id = id, listaId = 1, produtoId = id, quantidade = BigDecimal(quantidade)),
        produto = Produto(id = id, nome = nome, nomeNormalizado = nome.lowercase(), categoriaId = 1),
    )

    private fun preco(itemId: Long, lojaId: Long, valor: String, disponivel: Boolean = true) = PrecoRegistrado(
        itemDaListaId = itemId,
        estabelecimentoId = lojaId,
        preco = BigDecimal(valor),
        disponivel = disponivel,
    )

    @Test
    fun `matriz marca o menor preco de cada linha e soma os totais`() {
        val itens = listOf(item(1, "Arroz"), item(2, "Feijão"))
        val lojas = listOf(Estabelecimento(1, "Mercado A"), Estabelecimento(2, "Mercado B"))
        val precos = listOf(
            preco(1, 1, "25.00"), preco(1, 2, "23.50"),
            preco(2, 1, "8.00"), preco(2, 2, "9.20"),
        )

        val matriz = MotorDePrecos.montarMatriz(itens, lojas, precos, mapOf(1L to categoria))

        assertTrue(matriz.temDados)
        val linhaArroz = matriz.linhas.first { it.itemId == 1L }
        assertEquals(2L, linhaArroz.melhorEstabelecimentoId)
        assertEquals(0, linhaArroz.economiaEntreLojas.compareTo(BigDecimal("1.50")))

        val totalA = matriz.totais.first { it.estabelecimentoId == 1L }
        val totalB = matriz.totais.first { it.estabelecimentoId == 2L }
        assertEquals(0, totalA.total.compareTo(BigDecimal("33.00")))
        assertEquals(0, totalB.total.compareTo(BigDecimal("32.70")))
        assertTrue(totalA.cestaCompleta)
        assertTrue(totalB.cestaCompleta)
    }

    @Test
    fun `item indisponivel deixa a cesta incompleta e aparece na lista de ausentes`() {
        val itens = listOf(item(1, "Arroz"), item(2, "Feijão"))
        val lojas = listOf(Estabelecimento(1, "Mercado A"), Estabelecimento(2, "Mercado B"))
        val precos = listOf(
            preco(1, 1, "25.00"), preco(1, 2, "23.50"),
            preco(2, 1, "8.00"), preco(2, 2, "0.00", disponivel = false),
        )

        val matriz = MotorDePrecos.montarMatriz(itens, lojas, precos, mapOf(1L to categoria))
        val totalB = matriz.totais.first { it.estabelecimentoId == 2L }

        assertFalse(totalB.cestaCompleta)
        assertTrue(totalB.itensAusentes.any { it.contains("Feijão") })
    }

    @Test
    fun `compra mista escolhe a loja mais barata de cada item`() {
        val itens = listOf(item(1, "Arroz"), item(2, "Feijão"))
        val lojas = listOf(Estabelecimento(1, "Mercado A"), Estabelecimento(2, "Mercado B"))
        val precos = listOf(
            preco(1, 1, "25.00"), preco(1, 2, "23.50"),
            preco(2, 1, "8.00"), preco(2, 2, "9.20"),
        )

        val mista = MotorDePrecos.montarMatriz(itens, lojas, precos, mapOf(1L to categoria)).compraMista
        assertNotNull(mista)
        // 23,50 (B) + 8,00 (A) = 31,50
        assertEquals(0, mista!!.total.compareTo(BigDecimal("31.50")))
        assertEquals(2L, mista.escolhaPorItem[1L])
        assertEquals(1L, mista.escolhaPorItem[2L])
        assertEquals(2, mista.lojasEnvolvidas)
        assertTrue(mista.economiaReais.signum() > 0)
    }

    @Test
    fun `uma loja so nao gera matriz comparavel`() {
        val matriz = MotorDePrecos.montarMatriz(
            itens = listOf(item(1, "Arroz")),
            estabelecimentos = listOf(Estabelecimento(1, "Única")),
            precos = listOf(preco(1, 1, "25.00")),
            categorias = mapOf(1L to categoria),
        )
        // Com uma loja nao ha "melhor preco" a destacar.
        val celula = matriz.linhas.first().celulas.first()
        assertFalse("sem concorrência não existe campeão", celula.melhorPreco)
    }

    @Test
    fun `item sem nenhum preco e contado a parte`() {
        val itens = listOf(item(1, "Arroz"), item(2, "Sal"))
        val lojas = listOf(Estabelecimento(1, "A"), Estabelecimento(2, "B"))
        val precos = listOf(preco(1, 1, "25.00"), preco(1, 2, "23.00"))

        val matriz = MotorDePrecos.montarMatriz(itens, lojas, precos, mapOf(1L to categoria))
        assertEquals(1, matriz.itensSemNenhumPreco)
    }

    // --- kit nos totais -----------------------------------------------------------------

    private fun itemKit(id: Long, nome: String, quantidade: String, unidadesPorKit: Int) = ItemComProduto(
        item = ItemDaLista(
            id = id,
            listaId = 1,
            produtoId = id,
            quantidade = BigDecimal(quantidade),
            ehKit = true,
            itensPorKit = unidadesPorKit,
        ),
        produto = Produto(id = id, nome = nome, nomeNormalizado = nome.lowercase(), categoriaId = 1),
    )

    @Test
    fun `preco por unidade de kit divide o fardo pelo numero de unidades`() {
        val porUnidade = MotorDePrecos.precoPorUnidadeDeKit(BigDecimal("30.00"), 12)
        assertNotNull(porUnidade)
        assertEquals(0, porUnidade!!.compareTo(BigDecimal("2.50")))
        assertNull(MotorDePrecos.precoPorUnidadeDeKit(BigDecimal("30.00"), 0))
        assertNull(MotorDePrecos.precoPorUnidadeDeKit(BigDecimal.ZERO, 12))
    }

    @Test
    fun `total da linha com kit multiplica o preco do fardo pelos fardos`() {
        // 2 fardos de R$ 30,00, cada um com 12 unidades = R$ 60,00 (e nao 2 x 12 x 30).
        val item = ItemDaLista(
            listaId = 1,
            produtoId = 1,
            quantidade = BigDecimal("2"),
            ehKit = true,
            itensPorKit = 12,
        )
        val total = MotorDePrecos.totalDaLinha(BigDecimal("30.00"), item)
        assertEquals(0, total.compareTo(BigDecimal("60.00")))
    }

    @Test
    fun `montar matriz totaliza o kit pelo preco da embalagem inteira`() {
        val itens = listOf(itemKit(1, "Ovos", quantidade = "2", unidadesPorKit = 12))
        val lojas = listOf(Estabelecimento(1, "Mercado A"))

        val matriz = MotorDePrecos.montarMatriz(
            itens = itens,
            estabelecimentos = lojas,
            precos = listOf(preco(1, 1, "30.00")),
            categorias = mapOf(1L to categoria),
        )

        assertEquals(0, matriz.totais.first().total.compareTo(BigDecimal("60.00")))
    }

    @Test
    fun `calculadora de compra com kit paga o fardo inteiro`() {
        val itens = listOf(itemKit(1, "Ovos", quantidade = "2", unidadesPorKit = 12))

        val resultado = CalculadoraDeCompra.calcular(
            itens = itens,
            precos = listOf(preco(1, 1, "30.00")),
            estabelecimentos = listOf(Estabelecimento(1, "Mercado A")),
            categorias = mapOf(1L to categoria),
        )

        assertEquals(0, resultado.totalPago.compareTo(BigDecimal("60.00")))
        assertEquals(0, resultado.totalSeComprasseNoMaisCaro.compareTo(BigDecimal("60.00")))
        assertEquals(0, resultado.economia.compareTo(BigDecimal.ZERO))
    }
}

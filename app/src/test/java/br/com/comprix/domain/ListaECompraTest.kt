package br.com.comprix.domain

import br.com.comprix.domain.categoria.CategorizadorAutomatico
import br.com.comprix.domain.categoria.DicionarioDeCategorias
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.lista.InterpretadorDeAdicaoRapida
import br.com.comprix.domain.lista.OrganizadorDeLista
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Organização da lista, categorização, auditoria de cobertura, fechamento da
 * compra e histórico (Seções 3, 4.4, 4.5 e jornada 5).
 */
class ListaECompraTest {

    private val hortifruti = Categoria(1, "Hortifrúti", "hortifruti", 10, chave = "hortifruti")
    private val acougue = Categoria(2, "Açougue", "acougue", 20, chave = "acougue")
    private val limpeza = Categoria(3, "Limpeza", "limpeza", 30, chave = "limpeza")
    private val outros = Categoria(99, "Outros", "outros", 999, chave = "outros")
    private val catalogo = mapOf(1L to hortifruti, 2L to acougue, 3L to limpeza, 99L to outros)

    /** Categorizador montado como o app monta: catálogo por chave + padrão. */
    private val categorizador = CategorizadorAutomatico(
        categoriasPorChave = mapOf(
            "hortifruti" to hortifruti,
            "acougue" to acougue,
            "limpeza" to limpeza,
            "outros" to outros,
        ),
        categoriaPadrao = outros,
    )

    private fun itemCom(
        id: Long,
        nome: String,
        categoriaId: Long,
        comprado: Boolean = false,
        quantidade: String = "1",
    ) = ItemComProduto(
        item = ItemDaLista(
            id = id,
            listaId = 1,
            produtoId = id,
            quantidade = BigDecimal(quantidade),
            comprado = comprado,
            ordemManual = (id * 10).toInt(),
        ),
        produto = Produto(id = id, nome = nome, nomeNormalizado = nome.lowercase(), categoriaId = categoriaId),
    )

    // --- categorização híbrida -----------------------------------------------------------

    @Test
    fun `dicionario reconhece itens comuns do mercado brasileiro`() {
        assertEquals("hortifruti", DicionarioDeCategorias.sugerirChave("tomate"))
        assertEquals("acougue", DicionarioDeCategorias.sugerirChave("picanha"))
        assertEquals("limpeza", DicionarioDeCategorias.sugerirChave("detergente"))
        assertEquals("padaria", DicionarioDeCategorias.sugerirChave("pão francês"))
    }

    @Test
    fun `palavra desconhecida cai em outros e nao inventa categoria`() {
        assertEquals(DicionarioDeCategorias.CHAVE_PADRAO, DicionarioDeCategorias.sugerirChave("xglptz"))
    }

    @Test
    fun `memoria do usuario tem prioridade sobre o dicionario`() {
        // O usuário já disse que "banana" é dele na categoria Limpeza (caso
        // absurdo de propósito: a memória manda, não o dicionário).
        val sugestao = categorizador.sugerir(
            nome = "banana",
            memoria = mapOf("banana" to 3L),
            categoriasPorId = catalogo,
        )
        assertEquals(limpeza, sugestao.categoria)
        assertEquals(CategorizadorAutomatico.Fonte.MEMORIA, sugestao.fonte)
        assertEquals(1f, sugestao.confianca, 0.001f)
    }

    @Test
    fun `nome parecido com algo ja corrigido usa memoria aproximada`() {
        val sugestao = categorizador.sugerir(
            nome = "detergentte",
            memoria = mapOf("detergente" to 3L),
            categoriasPorId = catalogo,
        )
        assertEquals(limpeza, sugestao.categoria)
        assertTrue(
            sugestao.fonte == CategorizadorAutomatico.Fonte.MEMORIA_APROXIMADA ||
                sugestao.fonte == CategorizadorAutomatico.Fonte.MEMORIA,
        )
    }

    @Test
    fun `nome vazio nao quebra a categorizacao`() {
        val sugestao = categorizador.sugerir("   ", emptyMap(), catalogo)
        assertNotNull(sugestao.categoria)
        assertTrue(sugestao.confianca < 0.5f)
    }

    // --- organização da lista --------------------------------------------------------------

    @Test
    fun `agrupar respeita a ordem de corredor das categorias`() {
        val itens = listOf(
            itemCom(1, "Sabão", 3),
            itemCom(2, "Alface", 1),
            itemCom(3, "Costela", 2),
        )
        val grupos = OrganizadorDeLista.agrupar(itens, catalogo)
        assertEquals(listOf("Hortifrúti", "Açougue", "Limpeza"), grupos.map { it.categoria.nome })
    }

    @Test
    fun `item comprado vai para o fim do grupo`() {
        val itens = listOf(
            itemCom(1, "Alface", 1, comprado = true),
            itemCom(2, "Tomate", 1),
        )
        val grupos = OrganizadorDeLista.agrupar(itens, catalogo)
        assertEquals("Tomate", grupos.first().itens.first().produto.nome)
        assertEquals(1, grupos.first().comprados)
        assertFalse(grupos.first().concluido)
    }

    @Test
    fun `filtro encontra por pedaco do nome e ignora acento`() {
        val itens = listOf(itemCom(1, "Açúcar", 1), itemCom(2, "Arroz", 1))
        assertEquals(1, OrganizadorDeLista.filtrar(itens, "acuc").size)
        assertEquals(2, OrganizadorDeLista.filtrar(itens, "").size)
    }

    @Test
    fun `progresso e a fracao de itens comprados`() {
        val itens = listOf(
            itemCom(1, "A", 1, comprado = true),
            itemCom(2, "B", 1, comprado = true),
            itemCom(3, "C", 1),
            itemCom(4, "D", 1),
        )
        assertEquals(0.5f, OrganizadorDeLista.progresso(itens), 0.001f)
        assertEquals(0f, OrganizadorDeLista.progresso(emptyList()), 0.001f)
    }

    // --- adição rápida: só o preço ------------------------------------------------------------

    @Test
    fun `extrair preco separa o valor do fim da linha`() {
        val resultado = InterpretadorDeAdicaoRapida.extrairPreco("arroz 5kg 24,90")
        assertNotNull(resultado.preco)
        assertEquals(0, resultado.preco!!.compareTo(BigDecimal("24.90")))
        assertEquals("arroz 5kg", resultado.restante)
    }

    @Test
    fun `extrair preco entende cifrao no meio da linha`() {
        val resultado = InterpretadorDeAdicaoRapida.extrairPreco("leite R$ 4,99")
        assertEquals(0, resultado.preco!!.compareTo(BigDecimal("4.99")))
        assertTrue(resultado.restante.startsWith("leite"))
    }

    @Test
    fun `linha sem preco volta intacta`() {
        val resultado = InterpretadorDeAdicaoRapida.extrairPreco("2kg de carne")
        assertNull(resultado.preco)
        assertEquals("2kg de carne", resultado.restante)
    }

    @Test
    fun `valor absurdo nao e aceito como preco`() {
        val resultado = InterpretadorDeAdicaoRapida.extrairPreco("arroz 99999,00")
        assertNull("acima do teto de R$ 9.999 não é preço", resultado.preco)
    }

    // --- auditoria de cobertura -----------------------------------------------------------------

    @Test
    fun `auditoria aponta o que falta e em qual loja`() {
        val itens = listOf(itemCom(1, "Arroz", 1), itemCom(2, "Feijão", 1))
        val lojas = listOf(Estabelecimento(1, "A"), Estabelecimento(2, "B"))
        val precos = listOf(
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25")),
            PrecoRegistrado(itemDaListaId = 2, estabelecimentoId = 1, preco = BigDecimal("8")),
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 2, preco = BigDecimal("23")),
        )

        val relatorio = AuditoriaDeCobertura.auditar(itens, lojas, precos)

        assertFalse(relatorio.completa)
        assertEquals(1, relatorio.pendencias.size)
        assertEquals("Feijão", relatorio.pendencias.first().nomeProduto)
        assertEquals("B", relatorio.pendencias.first().nomeEstabelecimento)
        assertEquals(1, relatorio.itensComPendencia)
    }

    @Test
    fun `cobertura completa quando toda celula tem resposta`() {
        val itens = listOf(itemCom(1, "Arroz", 1))
        val lojas = listOf(Estabelecimento(1, "A"), Estabelecimento(2, "B"))
        val precos = listOf(
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25")),
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 2, preco = BigDecimal("23")),
        )
        val relatorio = AuditoriaDeCobertura.auditar(itens, lojas, precos)
        assertTrue(relatorio.completa)
        assertTrue(relatorio.pendencias.isEmpty())
        assertEquals(1f, relatorio.porLoja.first().progresso, 0.001f)
    }

    @Test
    fun `marcar indisponivel conta como resposta, nao como lacuna`() {
        val itens = listOf(itemCom(1, "Arroz", 1))
        val lojas = listOf(Estabelecimento(1, "A"))
        val precos = listOf(
            PrecoRegistrado(
                itemDaListaId = 1,
                estabelecimentoId = 1,
                preco = BigDecimal.ZERO,
                disponivel = false,
            ),
        )
        val relatorio = AuditoriaDeCobertura.auditar(itens, lojas, precos)
        assertTrue("'não tem' é uma resposta", relatorio.completa)
        assertEquals(
            AuditoriaDeCobertura.Situacao.INDISPONIVEL,
            relatorio.itens.first().situacaoPorLoja[1L],
        )
    }

    // --- fechamento da compra ----------------------------------------------------------------------

    @Test
    fun `calculo da compra usa o menor preco e mede a economia contra o mais caro`() {
        val itens = listOf(itemCom(1, "Arroz", 1), itemCom(2, "Feijão", 1))
        val lojas = listOf(Estabelecimento(1, "A"), Estabelecimento(2, "B"))
        val precos = listOf(
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25.00")),
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 2, preco = BigDecimal("23.00")),
            PrecoRegistrado(itemDaListaId = 2, estabelecimentoId = 1, preco = BigDecimal("8.00")),
            PrecoRegistrado(itemDaListaId = 2, estabelecimentoId = 2, preco = BigDecimal("9.00")),
        )

        val resultado = CalculadoraDeCompra.calcular(itens, precos, lojas, catalogo)

        assertEquals(0, resultado.totalPago.compareTo(BigDecimal("31.00")))
        assertEquals(0, resultado.totalSeComprasseNoMaisCaro.compareTo(BigDecimal("34.00")))
        assertEquals(0, resultado.economia.compareTo(BigDecimal("3.00")))
        assertEquals(2, resultado.quantidadeItens)
        assertEquals(0, resultado.itensSemPreco)
    }

    @Test
    fun `escolha por item respeita a loja indicada na compra mista`() {
        val itens = listOf(itemCom(1, "Arroz", 1))
        val lojas = listOf(Estabelecimento(1, "A"), Estabelecimento(2, "B"))
        val precos = listOf(
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25.00")),
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 2, preco = BigDecimal("23.00")),
        )
        val resultado = CalculadoraDeCompra.calcular(
            itens, precos, lojas, catalogo,
            escolhaPorItem = mapOf(1L to 1L),
        )
        assertEquals(0, resultado.totalPago.compareTo(BigDecimal("25.00")))
    }

    @Test
    fun `economia nunca fica negativa`() {
        val itens = listOf(itemCom(1, "Arroz", 1))
        val lojas = listOf(Estabelecimento(1, "A"))
        val precos = listOf(PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25.00")))
        val resultado = CalculadoraDeCompra.calcular(itens, precos, lojas, catalogo)
        assertTrue(resultado.economia.signum() >= 0)
    }

    @Test
    fun `item sem preco e contado e nao derruba o total`() {
        val itens = listOf(itemCom(1, "Arroz", 1), itemCom(2, "Sal", 1))
        val lojas = listOf(Estabelecimento(1, "A"))
        val precos = listOf(PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25.00")))
        val resultado = CalculadoraDeCompra.calcular(itens, precos, lojas, catalogo)
        assertEquals(1, resultado.itensSemPreco)
        assertEquals(0, resultado.totalPago.compareTo(BigDecimal("25.00")))
    }

    @Test
    fun `estimativa parcial soma o menor preco de cada item`() {
        val itens = listOf(itemCom(1, "Arroz", 1, quantidade = "2"))
        val precos = listOf(
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 1, preco = BigDecimal("25.00")),
            PrecoRegistrado(itemDaListaId = 1, estabelecimentoId = 2, preco = BigDecimal("20.00")),
        )
        val estimativa = CalculadoraDeCompra.estimativaParcial(itens, precos)
        assertEquals(0, estimativa.compareTo(BigDecimal("40.00")))
    }

    // --- histórico -------------------------------------------------------------------------------------

    private fun compra(mesesAtras: Long, total: String, economia: String = "0") = CompraFinalizada(
        listaId = null,
        nomeLista = "Compra",
        data = LocalDateTime.of(2026, 6, 15, 10, 0).minusMonths(mesesAtras),
        totalPago = BigDecimal(total),
        economia = BigDecimal(economia),
        quantidadeItens = 10,
        descricaoEstabelecimento = "Mercado A",
        estabelecimentoPrincipalId = 1,
        gastosPorCategoria = mapOf("Hortifrúti" to BigDecimal(total)),
    )

    @Test
    fun `resumo soma apenas as compras do periodo escolhido`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val compras = listOf(compra(0, "100"), compra(1, "200"), compra(8, "500"))

        val tresMeses = AnalisadorDeHistorico.resumir(compras, AnalisadorDeHistorico.Periodo.TRES_MESES, hoje)
        assertEquals(0, tresMeses.totalGasto.compareTo(BigDecimal("300")))
        assertEquals(2, tresMeses.quantidadeCompras)

        val tudo = AnalisadorDeHistorico.resumir(compras, AnalisadorDeHistorico.Periodo.TUDO, hoje)
        assertEquals(0, tudo.totalGasto.compareTo(BigDecimal("800")))
        assertEquals(3, tudo.quantidadeCompras)
    }

    @Test
    fun `ticket medio e total dividido pela quantidade`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val resumo = AnalisadorDeHistorico.resumir(
            listOf(compra(0, "100"), compra(0, "300")),
            AnalisadorDeHistorico.Periodo.SEIS_MESES,
            hoje,
        )
        assertEquals(0, resumo.ticketMedio.compareTo(BigDecimal("200")))
    }

    @Test
    fun `historico vazio nao divide por zero`() {
        val resumo = AnalisadorDeHistorico.resumir(emptyList(), AnalisadorDeHistorico.Periodo.TUDO, LocalDate.now())
        assertTrue(resumo.vazio)
        assertEquals(0, resumo.ticketMedio.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `gasto por mercado soma por loja e ordena do maior para o menor`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val compras = listOf(
            compra(0, "100").copy(descricaoEstabelecimento = "Mercado A"),
            compra(0, "250").copy(descricaoEstabelecimento = "Mercado B"),
            compra(1, "50").copy(descricaoEstabelecimento = "Mercado A"),
        )

        val serie = AnalisadorDeHistorico.resumir(compras, AnalisadorDeHistorico.Periodo.TUDO, hoje)
            .gastosPorMercado

        assertEquals(2, serie.size)
        assertEquals("Mercado B", serie[0].rotulo)
        assertEquals(0, serie[0].valor.compareTo(BigDecimal("250")))
        assertEquals("Mercado A", serie[1].rotulo)
        assertEquals(0, serie[1].valor.compareTo(BigDecimal("150")))
    }

    @Test
    fun `compra sem loja identificada cai como nao informado no gasto por mercado`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val compras = listOf(compra(0, "80").copy(descricaoEstabelecimento = ""))

        val serie = AnalisadorDeHistorico.resumir(compras, AnalisadorDeHistorico.Periodo.TUDO, hoje)
            .gastosPorMercado

        assertEquals(listOf("Não informado"), serie.map { it.rotulo })
        assertEquals(0, serie[0].valor.compareTo(BigDecimal("80")))
    }

    @Test
    fun `gasto por mercado vazio quando nao ha compras no periodo`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val resumo = AnalisadorDeHistorico.resumir(
            listOf(compra(8, "500")),
            AnalisadorDeHistorico.Periodo.TRES_MESES,
            hoje,
        )
        assertTrue(resumo.vazio)
        assertTrue(resumo.gastosPorMercado.isEmpty())
    }

    @Test
    fun `variacao mensal compara o mes atual com o anterior`() {
        val hoje = LocalDate.of(2026, 6, 20)
        val compras = listOf(
            CompraFinalizada(
                listaId = null, nomeLista = "atual", data = LocalDateTime.of(2026, 6, 10, 9, 0),
                totalPago = BigDecimal("150"), economia = BigDecimal.ZERO, quantidadeItens = 5,
                descricaoEstabelecimento = "A", estabelecimentoPrincipalId = 1,
            ),
            CompraFinalizada(
                listaId = null, nomeLista = "anterior", data = LocalDateTime.of(2026, 5, 10, 9, 0),
                totalPago = BigDecimal("100"), economia = BigDecimal.ZERO, quantidadeItens = 5,
                descricaoEstabelecimento = "A", estabelecimentoPrincipalId = 1,
            ),
        )
        val variacao = AnalisadorDeHistorico.variacaoMensal(compras, hoje)
        assertNotNull(variacao)
        assertEquals(0, variacao!!.compareTo(BigDecimal("50")))
    }

    @Test
    fun `unidade padrao de produto novo nao vira unidade invalida`() {
        val produto = Produto(nome = "Carne", nomeNormalizado = "carne", categoriaId = 2)
        assertTrue(produto.unidadePadrao in Unidade.entries)
    }
}

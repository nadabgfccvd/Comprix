package br.com.comprix

import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.presentation.comum.filtrarEntradaDeMoeda
import br.com.comprix.presentation.tema.ComprixCores
import br.com.comprix.presentation.tema.TINTA_ESCURA
import br.com.comprix.presentation.tema.corDeTextoSobre
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.pow

/**
 * Testes de regressao dos cinco problemas relatados pelo usuario na versao
 * anterior. Cada bloco cita o problema em uma frase e prova o comportamento
 * correto - se alguem reintroduzir o defeito, o build quebra.
 */
class CorrecoesRelatadasTest {

    // =================================================================================
    // Cenario comum: lista mestra com 3 itens e 2 mercados
    // =================================================================================

    private val mercado1 = Estabelecimento(id = 1, nome = "Mercado 1")
    private val mercado2 = Estabelecimento(id = 2, nome = "Mercado 2")

    private fun produto(id: Long, nome: String) = Produto(
        id = id,
        nome = nome,
        nomeNormalizado = nome.lowercase(),
        categoriaId = 1,
    )

    private fun item(id: Long, produtoId: Long, conteudo: String, unidade: Unidade) = ItemDaLista(
        id = id,
        listaId = 1,
        produtoId = produtoId,
        quantidade = BigDecimal.ONE,
        unidade = unidade,
        pesoOuVolume = BigDecimal(conteudo),
    )

    private val oleo = ItemComProduto(item(10, 100, "900", Unidade.MILILITRO), produto(100, "Óleo de soja"))
    private val arroz = ItemComProduto(item(11, 101, "5", Unidade.QUILO), produto(101, "Arroz"))
    private val cafe = ItemComProduto(item(12, 102, "500", Unidade.GRAMA), produto(102, "Café"))
    private val itens = listOf(oleo, arroz, cafe)
    private val categorias = mapOf(1L to Categoria(id = 1, nome = "Mercearia", icone = "mercearia", ordemPadrao = 60))

    private fun preco(itemId: Long, lojaId: Long, valor: String, disponivel: Boolean = true) = PrecoRegistrado(
        id = itemId * 10 + lojaId,
        itemDaListaId = itemId,
        estabelecimentoId = lojaId,
        preco = BigDecimal(valor),
        disponivel = disponivel,
    )

    // =================================================================================
    // Problema 1 - "mudar o preco no Mercado 1 mudava tambem no Mercado 2"
    // =================================================================================

    @Test
    fun `preco e identificado por item mais loja, nunca so pelo item`() {
        val precos = listOf(preco(10, 1, "7.90"), preco(10, 2, "8.49"))
        val matriz = MotorDePrecos.montarMatriz(listOf(oleo), listOf(mercado1, mercado2), precos, categorias)

        val celulaMercado1 = matriz.linhas.first().celulas.first { it.estabelecimentoId == 1L }
        val celulaMercado2 = matriz.linhas.first().celulas.first { it.estabelecimentoId == 2L }

        assertEquals(BigDecimal("7.90"), celulaMercado1.preco)
        assertEquals(BigDecimal("8.49"), celulaMercado2.preco)
        assertTrue("o menor preco deve ser o do Mercado 1", celulaMercado1.melhorPreco)
        assertFalse(celulaMercado2.melhorPreco)
    }

    @Test
    fun `apagar o preco de uma loja preserva o mesmo item nas outras lojas`() {
        // Estado depois de remover apenas a celula do Mercado 1 (nao o item).
        val precosAposRemocao = listOf(preco(10, 2, "8.49"))
        val matriz = MotorDePrecos.montarMatriz(listOf(oleo), listOf(mercado1, mercado2), precosAposRemocao, categorias)

        assertEquals("o item continua na lista mestra", 1, matriz.linhas.size)
        val celulaMercado1 = matriz.linhas.first().celulas.first { it.estabelecimentoId == 1L }
        val celulaMercado2 = matriz.linhas.first().celulas.first { it.estabelecimentoId == 2L }
        assertNull("Mercado 1 ficou sem preco", celulaMercado1.preco)
        assertFalse("e marcado como nao registrado", celulaMercado1.registrado)
        assertEquals("Mercado 2 segue intacto", BigDecimal("8.49"), celulaMercado2.preco)
    }

    @Test
    fun `item com preco em uma loja so nao recebe destaque de melhor preco`() {
        val matriz = MotorDePrecos.montarMatriz(
            listOf(oleo),
            listOf(mercado1, mercado2),
            listOf(preco(10, 1, "7.90")),
            categorias,
        )
        assertFalse(
            "destacar 'mais barato' com uma loja so induziria a erro",
            matriz.linhas.first().celulas.any { it.melhorPreco },
        )
    }

    // =================================================================================
    // Problemas 2 e 3 - lista mestra: dizer o que falta, e onde
    // =================================================================================

    @Test
    fun `auditoria aponta exatamente qual item falta em qual loja`() {
        val precos = listOf(
            preco(10, 1, "7.90"), // oleo: so no Mercado 1
            preco(11, 1, "24.90"), preco(11, 2, "23.49"), // arroz nos dois
            preco(12, 2, "17.90"), // cafe: so no Mercado 2
        )
        val relatorio = AuditoriaDeCobertura.auditar(itens, listOf(mercado1, mercado2), precos)

        assertEquals(2, relatorio.pendencias.size)
        val mensagens = relatorio.pendencias.map { it.mensagem }
        assertTrue(mensagens.any { it.contains("Óleo de soja") && it.contains("Mercado 2") })
        assertTrue(mensagens.any { it.contains("Café") && it.contains("Mercado 1") })
        assertFalse(relatorio.completa)
    }

    @Test
    fun `aviso do painel resume as pendencias em uma frase`() {
        val relatorio = AuditoriaDeCobertura.auditar(
            itens,
            listOf(mercado1, mercado2),
            listOf(preco(10, 1, "7.90"), preco(11, 1, "24.90"), preco(11, 2, "23.49"), preco(12, 2, "17.90")),
        )
        assertEquals("2 itens sem preço em Mercado 2 e Mercado 1", relatorio.aviso)
    }

    @Test
    fun `nao ter o produto na loja e diferente de nao ter pesquisado`() {
        val precos = listOf(
            preco(10, 1, "7.90"),
            preco(10, 2, "0", disponivel = false), // pesquisei: a loja nao tem
        )
        val relatorio = AuditoriaDeCobertura.auditar(listOf(oleo), listOf(mercado1, mercado2), precos)

        assertEquals(
            AuditoriaDeCobertura.Situacao.INDISPONIVEL,
            relatorio.itens.first().situacaoPorLoja[2L],
        )
        assertTrue("item sem estoque nao conta como pendencia", relatorio.pendencias.isEmpty())
        assertTrue(relatorio.completa)
        assertEquals(listOf("Óleo de soja"), relatorio.porLoja.first { it.estabelecimento.id == 2L }.indisponiveis)
    }

    @Test
    fun `cobertura por loja mostra progresso e resumo`() {
        val relatorio = AuditoriaDeCobertura.auditar(
            itens,
            listOf(mercado1, mercado2),
            listOf(preco(10, 1, "7.90"), preco(11, 1, "24.90"), preco(12, 1, "17.90")),
        )
        val loja1 = relatorio.porLoja.first { it.estabelecimento.id == 1L }
        val loja2 = relatorio.porLoja.first { it.estabelecimento.id == 2L }

        assertTrue(loja1.completa)
        assertEquals(1f, loja1.progresso, 0.001f)
        assertEquals("Todos os 3 itens com preço", loja1.resumo)
        assertEquals("Faltam 3 de 3 itens", loja2.resumo)
        assertEquals(0f, loja2.progresso, 0.001f)
    }

    @Test
    fun `com uma loja so o painel nao acusa pendencia`() {
        val relatorio = AuditoriaDeCobertura.auditar(itens, listOf(mercado1), listOf(preco(10, 1, "7.90")))
        assertEquals(2, relatorio.pendencias.size)
        assertEquals(1, relatorio.porLoja.size)
    }

    @Test
    fun `itens sem nenhum preco sao listados a parte`() {
        val relatorio = AuditoriaDeCobertura.auditar(
            itens,
            listOf(mercado1, mercado2),
            listOf(preco(10, 1, "7.90"), preco(10, 2, "8.49")),
        )
        assertEquals(listOf("Arroz", "Café"), relatorio.itensSemNenhumPreco)
        assertTrue(relatorio.itens.first { it.itemId == 10L }.comparavel)
        assertFalse(relatorio.itens.first { it.itemId == 11L }.comparavel)
    }

    // =================================================================================
    // Problema 4 - contraste insuficiente sobre o fundo branco
    // =================================================================================

    /** Luminancia relativa segundo a WCAG 2.1. */
    private fun luminancia(cor: Color): Double {
        fun canal(valor: Float): Double {
            val v = valor.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal(cor.red) + 0.7152 * canal(cor.green) + 0.0722 * canal(cor.blue)
    }

    private fun contraste(a: Color, b: Color): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `tintas de texto do tema claro passam em 4,5 sobre o fundo`() {
        val claro = ComprixCores.CLARO
        val pares = listOf(
            "ação" to (claro.acao to claro.fundo),
            "verde de texto" to (claro.verdeTinta to claro.fundo),
            "âmbar de texto" to (claro.ambarTinta to claro.fundo),
            "vermelho de texto" to (claro.vermelhoTinta to claro.fundo),
            "lavanda de texto" to (claro.lavandaTinta to claro.fundo),
            "texto principal" to (claro.texto to claro.fundo),
            "texto apagado" to (claro.apagado to claro.fundo),
            "verde sobre verde suave" to (claro.verdeTinta to claro.verdeSuave),
            "âmbar sobre âmbar suave" to (claro.ambarTinta to claro.ambarSuave),
            "vermelho sobre vermelho suave" to (claro.vermelhoTinta to claro.vermelhoSuave),
            "lavanda sobre lavanda" to (claro.lavandaTinta to claro.lavanda),
            "torrada" to (claro.textoDaTorrada to claro.torrada),
        )
        pares.forEach { (nome, par) ->
            val razao = contraste(par.first, par.second)
            assertTrue("$nome rendeu $razao", razao >= 4.5)
        }
    }

    @Test
    fun `tintas de texto do tema escuro passam em 4,5`() {
        val escuro = ComprixCores.ESCURO
        val pares = listOf(
            "ação" to (escuro.acao to escuro.fundo),
            "verde de texto" to (escuro.verdeTinta to escuro.cartao),
            "âmbar de texto" to (escuro.ambarTinta to escuro.cartao),
            "vermelho de texto" to (escuro.vermelhoTinta to escuro.cartao),
            "lavanda de texto" to (escuro.lavandaTinta to escuro.cartao),
            "texto principal" to (escuro.texto to escuro.fundo),
            "texto apagado" to (escuro.apagado to escuro.fundo),
            "texto sobre ação" to (escuro.sobreAcao to escuro.acao),
            "verde sobre verde suave" to (escuro.verdeTinta to escuro.verdeSuave),
            "âmbar sobre âmbar suave" to (escuro.ambarTinta to escuro.ambarSuave),
        )
        pares.forEach { (nome, par) ->
            val razao = contraste(par.first, par.second)
            assertTrue("escuro, $nome rendeu $razao", razao >= 4.5)
        }
    }

    @Test
    fun `alto contraste nunca piora o que o tema normal ja entregava`() {
        val normal = ComprixCores.CLARO
        val forte = ComprixCores.CLARO_ALTO_CONTRASTE
        assertTrue(contraste(forte.acao, forte.fundo) >= contraste(normal.acao, normal.fundo))
        assertTrue(contraste(forte.apagado, forte.fundo) >= contraste(normal.apagado, normal.fundo))
        assertTrue(contraste(forte.texto, forte.fundo) >= contraste(normal.texto, normal.fundo))
        assertTrue("alto contraste desliga sombra", forte.semSombra)
    }

    @Test
    fun `botao principal tem texto legivel nos dois temas`() {
        assertTrue(
            "claro: ${contraste(ComprixCores.CLARO.sobreAcao, ComprixCores.CLARO.acao)}",
            contraste(ComprixCores.CLARO.sobreAcao, ComprixCores.CLARO.acao) >= 4.5,
        )
        assertTrue(
            "escuro: ${contraste(ComprixCores.ESCURO.sobreAcao, ComprixCores.ESCURO.acao)}",
            contraste(ComprixCores.ESCURO.sobreAcao, ComprixCores.ESCURO.acao) >= 4.5,
        )
    }

    @Test
    fun `ambar da marca so e usado com tinta escura em cima`() {
        val ambar = ComprixCores.CLARO.ambar
        // O ambar puro rende 1,8:1 com branco - o app nunca escreve branco nele.
        assertTrue(contraste(Color.White, ambar) < 3.0)
        assertTrue(
            "com tinta escura: ${contraste(TINTA_ESCURA, ambar)}",
            contraste(TINTA_ESCURA, ambar) >= 4.5,
        )
        assertEquals(TINTA_ESCURA, corDeTextoSobre(ambar))
    }

    @Test
    fun `marca esmeralda nunca e usada como cor de texto sobre fundo claro`() {
        val claro = ComprixCores.CLARO
        // E justamente por reprovar aqui que existe a variante [acao].
        assertTrue(contraste(claro.marca, claro.fundo) < 4.5)
        assertTrue(contraste(claro.acao, claro.fundo) >= 4.5)
    }

    @Test
    fun `tintas de categoria leem sobre o cartao nos dois temas`() {
        listOf(ComprixCores.CLARO, ComprixCores.ESCURO).forEach { tema ->
            repeat(12) { indice ->
                val razao = contraste(tema.tintaDaCategoria(indice), tema.cartao)
                assertTrue(
                    "categoria $indice (escuro=${tema.escuro}) rendeu $razao",
                    razao >= 3.0,
                )
            }
        }
    }

    @Test
    fun `cor de texto sobre chip de loja sempre tem contraste suficiente`() {
        val coresDeLoja = listOf(
            "#1C8454", "#A05E00", "#1565C0", "#8E24AA", "#C62828", "#00796B", "#5D4037", "#455A64",
        ).map { hex -> Color(hex.removePrefix("#").toLong(16) or 0xFF000000L) }

        coresDeLoja.forEach { fundo ->
            val razao = contraste(corDeTextoSobre(fundo), fundo)
            assertTrue("chip $fundo rendeu $razao", razao >= 4.5)
        }
    }

    // =================================================================================
    // Problema 5 - confirmar o preco ao tocar fora do campo
    // =================================================================================

    @Test
    fun `entrada de moeda aceita apenas digitos e uma virgula`() {
        assertEquals("12,90", filtrarEntradaDeMoeda("R$ 12,90"))
        assertEquals("12,90", filtrarEntradaDeMoeda("12.90"))
        assertEquals("1290", filtrarEntradaDeMoeda("12a9b0"))
        assertEquals("12,90", filtrarEntradaDeMoeda("12,90,55"))
        assertEquals("12,99", filtrarEntradaDeMoeda("12,999"))
        assertEquals("", filtrarEntradaDeMoeda(",,,"))
    }

    @Test
    fun `valor digitado sem virgula e interpretado como reais inteiros`() {
        val interpretado = br.com.comprix.util.TextoUtil.paraDecimal(filtrarEntradaDeMoeda("1290"))
        assertNotNull(interpretado)
        assertEquals(BigDecimal("1290"), interpretado)
    }
}

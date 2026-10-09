package br.com.comprix.domain

import br.com.comprix.domain.lista.InterpretadorDeAdicaoRapida
import br.com.comprix.domain.lista.OrganizadorDeLista
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.LinhaOcr
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.rotulo.ExtratorDeRotulo
import br.com.comprix.util.Formatadores
import br.com.comprix.util.JsonSimples
import br.com.comprix.util.TextoUtil
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
 * Texto, datas, formatação e ordenação.
 *
 * Esta classe cobre as engrenagens pequenas que todo o resto usa: se
 * `paraDecimal` erra uma vírgula ou `capitalizarTitulo` come um acento, o
 * estrago aparece em cinco telas diferentes.
 */
class TextoDatasEOrdemTest {

    // --- texto ---------------------------------------------------------------------------

    @Test
    fun `normalizar tira acento, caixa e pontuacao`() {
        assertEquals("acucar cristal", TextoUtil.normalizar("Açúcar  Cristal!"))
        assertEquals("", TextoUtil.normalizar("   "))
    }

    @Test
    fun `normalizar leve preserva virgula e cifrao`() {
        val leve = TextoUtil.normalizarLeve("R$ 24,90")
        assertTrue(leve.contains(","))
        assertTrue(leve.contains("r$"))
    }

    @Test
    fun `decimal aceita virgula e ponto de milhar brasileiros`() {
        assertEquals(0, TextoUtil.paraDecimal("1.234,56")!!.compareTo(BigDecimal("1234.56")))
        assertEquals(0, TextoUtil.paraDecimal("12,90")!!.compareTo(BigDecimal("12.90")))
        assertEquals(0, TextoUtil.paraDecimal("7")!!.compareTo(BigDecimal("7")))
        assertNull(TextoUtil.paraDecimal("abc"))
        assertNull(TextoUtil.paraDecimal(null))
    }

    @Test
    fun `capitalizar titulo respeita preposicoes`() {
        assertEquals("Leite de Coco", TextoUtil.capitalizarTitulo("leite de coco"))
        assertEquals("Arroz", TextoUtil.capitalizarTitulo("ARROZ"))
    }

    @Test
    fun `similaridade enxerga erro de digitacao e separa palavras diferentes`() {
        assertTrue(TextoUtil.similaridade("detergente", "detergentte") > 0.85f)
        assertTrue(TextoUtil.similaridade("arroz", "feijao") < 0.5f)
        assertEquals(1f, TextoUtil.similaridade("sal", "sal"), 0.001f)
    }

    @Test
    fun `distancia de edicao conta as trocas minimas`() {
        assertEquals(0, TextoUtil.distanciaEdicao("leite", "leite"))
        assertEquals(1, TextoUtil.distanciaEdicao("leite", "leit"))
        assertEquals(5, TextoUtil.distanciaEdicao("", "leite"))
    }

    @Test
    fun `contem palavra nao casa pedaco de outra palavra`() {
        assertTrue(TextoUtil.contemPalavra("leite de coco", "coco"))
        assertFalse(TextoUtil.contemPalavra("chocolate", "coco"))
    }

    @Test
    fun `codigo de barras so e valido com digito verificador correto`() {
        assertTrue(TextoUtil.codigoDeBarrasValido("7891000100103"))
        assertFalse(TextoUtil.codigoDeBarrasValido("7891000100104"))
        assertFalse(TextoUtil.codigoDeBarrasValido("123"))
        assertFalse(TextoUtil.codigoDeBarrasValido(null))
    }

    @Test
    fun `tokens separam o texto em palavras normalizadas`() {
        assertEquals(listOf("arroz", "integral", "5kg"), TextoUtil.tokens("Arroz Integral 5kg"))
    }

    // --- formatação ------------------------------------------------------------------------

    @Test
    fun `moeda usa o padrao brasileiro`() {
        val texto = Formatadores.moeda(BigDecimal("1234.5"))
        assertTrue(texto.contains("R$"))
        assertTrue(texto.contains("1.234,50"))
        assertTrue(Formatadores.moeda(null).isNotBlank())
    }

    @Test
    fun `quantidade nao mostra zeros a toa`() {
        assertEquals("2", Formatadores.quantidade(BigDecimal("2.000")))
        assertEquals("1,5", Formatadores.quantidade(BigDecimal("1.5")))
    }

    @Test
    fun `descricao da embalagem monta o texto que aparece na lista`() {
        assertEquals(
            "2 x 500 g",
            Formatadores.descricaoEmbalagem(BigDecimal("2"), Unidade.GRAMA, BigDecimal("500"), null),
        )
        assertEquals(
            "12 x 350 mL",
            Formatadores.descricaoEmbalagem(BigDecimal.ONE, Unidade.MILILITRO, BigDecimal("350"), 12),
        )
        assertEquals("un", Formatadores.descricaoEmbalagem(BigDecimal.ONE, Unidade.UNIDADE, null, null))
    }

    @Test
    fun `preco por unidade mostra a base certa`() {
        val texto = Formatadores.precoPorUnidade(BigDecimal("0.005"), Unidade.QUILO)
        assertTrue(texto.contains("g"))
    }

    @Test
    fun `lista de nomes vira frase legivel`() {
        assertEquals("Arroz", Formatadores.listaDeNomes(listOf("Arroz")))
        assertEquals("Arroz e Feijão", Formatadores.listaDeNomes(listOf("Arroz", "Feijão")))
        assertTrue(Formatadores.listaDeNomes(listOf("A", "B", "C")).contains(","))
    }

    @Test
    fun `datas saem no formato brasileiro`() {
        assertEquals("15/06/2026", Formatadores.data(LocalDate.of(2026, 6, 15)))
        assertTrue(Formatadores.dataHora(LocalDateTime.of(2026, 6, 15, 14, 30)).contains("15/06/2026"))
        assertTrue(Formatadores.carimboDeArquivo(LocalDateTime.of(2026, 6, 15, 14, 30)).contains("2026"))
    }

    // --- JSON simples (backup) ----------------------------------------------------------------

    @Test
    fun `json simples sobrevive a ida e volta com acento e aspas`() {
        val original = mapOf("nome" to "Açúcar \"cristal\"", "preco" to "12,90", "vazio" to null)
        val texto = JsonSimples.paraJson(original)
        val volta = JsonSimples.deJson(texto)
        assertEquals("Açúcar \"cristal\"", volta["nome"])
        assertEquals("12,90", volta["preco"])
    }

    @Test
    fun `json invalido devolve mapa vazio em vez de explodir`() {
        assertTrue(JsonSimples.deJson("isto não é json").isEmpty())
        assertTrue(JsonSimples.deJson(null).isEmpty())
    }

    @Test
    fun `lista de mapas tambem faz ida e volta`() {
        val itens = listOf(mapOf("a" to "1"), mapOf("a" to "2"))
        val volta = JsonSimples.listaDeJson(JsonSimples.listaParaJson(itens))
        assertEquals(2, volta.size)
        assertEquals("2", volta[1]["a"])
    }

    // --- datas e tabela no rótulo ------------------------------------------------------------------

    @Test
    fun `validade rotulada vence a data solta`() {
        val datas = ExtratorDeRotulo.extrairDatas(
            listOf(
                LinhaOcr("FAB 10/01/2026"),
                LinhaOcr("VAL 10/07/2026"),
            ),
        )
        assertEquals(LocalDate.of(2026, 7, 10), datas.validade)
        assertEquals(LocalDate.of(2026, 1, 10), datas.fabricacao)
        assertTrue(datas.confianca > 0f)
    }

    @Test
    fun `rotulo sem data nao inventa validade`() {
        val datas = ExtratorDeRotulo.extrairDatas(listOf(LinhaOcr("Arroz tipo 1")))
        assertNull(datas.validade)
        assertNull(datas.fabricacao)
    }

    @Test
    fun `ingredientes param na proxima secao do rotulo`() {
        val texto = "INGREDIENTES: farinha de trigo, açúcar, sal. " +
            "INFORMAÇÃO NUTRICIONAL porção de 30 g"
        val ingredientes = ExtratorDeRotulo.extrairIngredientes(texto)
        assertNotNull(ingredientes)
        assertTrue(ingredientes!!.contains("farinha"))
        assertFalse("a seção seguinte do rótulo não é ingrediente: <$ingredientes>", ingredientes.contains("30 g"))
    }

    @Test
    fun `tabela nutricional le os nutrientes da coluna da porcao`() {
        val info = ExtratorDeRotulo.extrairTabelaNutricional(
            """
            Porção de 30 g
            Valor energético 120 kcal 6% VD
            Proteínas 3,2 g 6% VD
            Sódio 85 mg 4% VD
            """.trimIndent(),
        )
        assertNotNull(info)
        assertEquals(0, info!!.valores[Nutriente.PROTEINAS]!!.compareTo(BigDecimal("3.2")))
        assertEquals(0, info.valores[Nutriente.SODIO]!!.compareTo(BigDecimal("85")))
        assertTrue(info.porcaoDescricao!!.contains("30"))
    }

    @Test
    fun `texto sem tabela devolve nulo`() {
        assertNull(ExtratorDeRotulo.extrairTabelaNutricional("apenas o nome do produto"))
    }

    @Test
    fun `unidade provavel usa volume para liquidos`() {
        // mL e a unidade-base de volume: o rotulo declara "1000 mL", nao "1 L".
        assertEquals(Unidade.MILILITRO, ExtratorDeRotulo.unidadeProvavel("Leite integral"))
        // Fora dos liquidos o palpite e massa: rotulo de solido declara gramas.
        assertEquals(Unidade.GRAMA, ExtratorDeRotulo.unidadeProvavel("Sabonete"))
        assertEquals(Unidade.GRAMA, ExtratorDeRotulo.unidadeProvavel(null))
    }

    // --- adição rápida completa -----------------------------------------------------------------------

    @Test
    fun `linha completa vira quantidade, conteudo e preco`() {
        val linha = InterpretadorDeAdicaoRapida.interpretar("2 arroz 5kg 24,90")
        assertNotNull(linha)
        assertEquals(0, linha!!.quantidade.compareTo(BigDecimal("2")))
        assertEquals(Unidade.QUILO, linha.unidade)
        assertEquals(0, linha.pesoOuVolume!!.compareTo(BigDecimal("5")))
        assertEquals(0, linha.preco!!.compareTo(BigDecimal("24.90")))
        assertTrue(linha.nome.contains("Arroz"))
    }

    @Test
    fun `linha minima assume uma embalagem`() {
        val linha = InterpretadorDeAdicaoRapida.interpretar("leite")
        assertNotNull(linha)
        assertEquals(0, linha!!.quantidade.compareTo(BigDecimal.ONE))
        assertEquals("Leite", linha.nome)
        assertFalse(linha.ehKit)
    }

    @Test
    fun `fardo declara itens por kit`() {
        val linha = InterpretadorDeAdicaoRapida.interpretar("fardo c/ 12 agua 1,5l 19,90")
        assertNotNull(linha)
        assertEquals(12, linha!!.itensPorKit)
        assertTrue(linha.ehKit)
    }

    @Test
    fun `linha em branco nao vira item`() {
        assertNull(InterpretadorDeAdicaoRapida.interpretar("   "))
    }

    @Test
    fun `varias linhas de uma vez viram varios itens`() {
        val itens = InterpretadorDeAdicaoRapida.interpretarVarias("arroz\nfeijao\nleite")
        assertEquals(3, itens.size)
    }

    // --- ordenação -------------------------------------------------------------------------------------

    private fun itemCom(id: Long, nome: String, categoriaId: Long, comprado: Boolean = false, ordem: Int = 0) =
        ItemComProduto(
            item = ItemDaLista(id = id, listaId = 1, produtoId = id, comprado = comprado, ordemManual = ordem),
            produto = Produto(id = id, nome = nome, nomeNormalizado = nome.lowercase(), categoriaId = categoriaId),
        )

    @Test
    fun `lista plana poe comprados no fim e respeita ordem de corredor`() {
        val categorias = mapOf(
            1L to Categoria(1, "Hortifrúti", "hortifruti", 10, chave = "hortifruti"),
            2L to Categoria(2, "Limpeza", "limpeza", 80, chave = "limpeza"),
        )
        val itens = listOf(
            itemCom(1, "Sabão", 2),
            itemCom(2, "Alface", 1, comprado = true),
            itemCom(3, "Tomate", 1),
        )
        val ordenados = OrganizadorDeLista.ordenarPlano(itens, categorias)
        assertEquals(listOf("Tomate", "Sabão", "Alface"), ordenados.map { it.produto.nome })
    }

    @Test
    fun `recalcular ordem usa passos de dez`() {
        val itens = listOf(itemCom(5, "A", 1), itemCom(9, "B", 1))
        val ordem = OrganizadorDeLista.recalcularOrdem(itens)
        assertEquals(10, ordem[5L])
        assertEquals(20, ordem[9L])
    }

    @Test
    fun `categoria desconhecida nao derruba o agrupamento`() {
        val grupos = OrganizadorDeLista.agrupar(listOf(itemCom(1, "Misterioso", 777)), emptyMap())
        assertEquals(1, grupos.size)
        assertEquals(1, grupos.first().itens.size)
    }
}

package br.com.comprix.parser

import br.com.comprix.domain.lista.InterpretadorDeAdicaoRapida
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.parser.ClasseDeItem
import br.com.comprix.domain.parser.Confianca
import br.com.comprix.domain.parser.FonteDaEntrada
import br.com.comprix.domain.parser.ItemInterpretado
import br.com.comprix.domain.parser.MemoriaDoParser
import br.com.comprix.domain.parser.OpcaoDeSugestao
import br.com.comprix.domain.parser.ParserDeLinhaDeCompra
import br.com.comprix.domain.parser.QuantidadeEmPosicaoNumerica
import br.com.comprix.domain.parser.SugestoesDoParser
import br.com.comprix.util.TextoUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

/**
 * Teste table-driven do parser de texto livre: le
 * `src/test/resources/comprix-parser-testes.json` (155 casos, distribuicao da
 * Secao 11.3) e confere cada campo declarado.
 *
 * O arquivo JSON e o contrato publicado; este teste e quem garante que o
 * codigo e o contrato nao se separam.
 */
class ParserDeTextoLivreTest {

    private val parser = ParserDeLinhaDeCompra()

    private val corpus: Map<String, Any?> by lazy {
        val texto = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("comprix-parser-testes.json"),
        ) { "corpus de casos nao encontrado em src/test/resources" }
            .bufferedReader()
            .use { it.readText() }
        JsonDeTeste.objeto(JsonDeTeste.ler(texto))
    }

    private fun casos(): List<Map<String, Any?>> =
        JsonDeTeste.lista(corpus["casos"]).map { JsonDeTeste.objeto(it) }

    // =================================================================================
    // 1. O corpus inteiro
    // =================================================================================

    @Test
    fun `corpus cobre a distribuicao minima exigida`() {
        val porTipo = casos().groupingBy { it["tipo"] as String }.eachCount()
        val minimos = mapOf(
            "coletivos" to 25,
            "popular_nao_animal" to 15,
            "popular_animal" to 15,
            "multiplos_itens" to 25,
            "colisao_lexico" to 10,
            "decimais_fracoes" to 15,
            "numerais_extenso" to 10,
            "negativos" to 10,
            "ambiguos" to 10,
            "sugestao" to 10,
            "ocr" to 10,
        )
        minimos.forEach { (tipo, minimo) ->
            val encontrados = porTipo[tipo] ?: 0
            assertTrue(
                "faltam casos de '$tipo': $encontrados de $minimo",
                encontrados >= minimo,
            )
        }
        assertTrue("o corpus precisa de ao menos 155 casos", casos().size >= 155)
    }

    @Test
    fun `todos os casos do corpus passam`() {
        val falhas = mutableListOf<String>()

        casos().forEach { caso ->
            val id = caso["id"] as String
            val entrada = caso["entrada"] as String
            val esperado = JsonDeTeste.objeto(caso["esperado"])
            try {
                if (caso["modo"] == "quantidade") {
                    verificarQuantidadeIsolada(id, entrada, esperado, falhas)
                } else {
                    verificarLinha(id, entrada, caso["fonte"] as String, esperado, falhas)
                }
            } catch (erro: Exception) {
                falhas += "[$id] \"$entrada\" lancou ${erro::class.simpleName}: ${erro.message}"
            }
        }

        if (falhas.isNotEmpty()) {
            throw AssertionError(
                "${falhas.size} de ${casos().size} casos falharam:\n" + falhas.joinToString("\n"),
            )
        }
    }

    private fun verificarQuantidadeIsolada(
        id: String,
        entrada: String,
        esperado: Map<String, Any?>,
        falhas: MutableList<String>,
    ) {
        val resultado = QuantidadeEmPosicaoNumerica.interpretar(entrada)
        val valorEsperado = esperado["valor"] as String?
        if (valorEsperado == null) {
            if (resultado != null) falhas += "[$id] \"$entrada\" deveria ser null, veio ${resultado.valor}"
            return
        }
        if (resultado == null) {
            falhas += "[$id] \"$entrada\" deveria valer $valorEsperado, veio null"
            return
        }
        if (resultado.valor.compareTo(BigDecimal(valorEsperado)) != 0) {
            falhas += "[$id] \"$entrada\" esperava $valorEsperado, veio ${resultado.valor}"
        }
    }

    private fun verificarLinha(
        id: String,
        entrada: String,
        fonte: String,
        esperado: Map<String, Any?>,
        falhas: MutableList<String>,
    ) {
        val origem = if (fonte == "ocr") FonteDaEntrada.OCR else FonteDaEntrada.TEXTO
        val obtidos = parser.interpretar(entrada, origem)
        val esperados = JsonDeTeste.lista(esperado["itens"]).map { JsonDeTeste.objeto(it) }

        if (obtidos.size != esperados.size) {
            falhas += "[$id] \"$entrada\" esperava ${esperados.size} item(ns), veio ${obtidos.size}: " +
                obtidos.joinToString(" | ") { resumo(it) }
            return
        }

        esperados.forEachIndexed { indice, campos ->
            val obtido = obtidos[indice]
            val prefixo = "[$id] \"$entrada\" item ${indice + 1}"

            campos.forEach { (campo, valor) ->
                when (campo) {
                    "nomeItem" -> comparar(
                        falhas, prefixo, "nome",
                        TextoUtil.normalizar(valor as String),
                        TextoUtil.normalizar(obtido.nomeItem),
                    )

                    "quantidade" -> {
                        val alvo = valor as String?
                        if (alvo == null) {
                            if (obtido.quantidade != null) {
                                falhas += "$prefixo: quantidade deveria ser null, veio ${obtido.quantidade}"
                            }
                        } else if (obtido.quantidade == null ||
                            obtido.quantidade!!.compareTo(BigDecimal(alvo)) != 0
                        ) {
                            falhas += "$prefixo: quantidade esperava $alvo, veio ${obtido.quantidade}"
                        }
                    }

                    "unidade" -> comparar(falhas, prefixo, "unidade", valor as String, obtido.unidade?.sigla)

                    "pesoOuVolume" -> {
                        val alvo = BigDecimal(valor as String)
                        if (obtido.pesoOuVolume == null || obtido.pesoOuVolume!!.compareTo(alvo) != 0) {
                            falhas += "$prefixo: pesoOuVolume esperava $alvo, veio ${obtido.pesoOuVolume}"
                        }
                    }

                    "ehKit" -> comparar(falhas, prefixo, "ehKit", valor.toString(), obtido.ehKit.toString())

                    "itensPorKit" -> comparar(
                        falhas, prefixo, "itensPorKit", valor.toString(), obtido.itensPorKit?.toString(),
                    )

                    "categoriaSugerida" -> comparar(
                        falhas, prefixo, "categoria", valor as String, obtido.categoriaSugerida,
                    )

                    "confianca" -> comparar(
                        falhas, prefixo, "confianca", (valor as String).uppercase(), obtido.confianca.name,
                    )

                    "gatilho" -> comparar(
                        falhas, prefixo, "gatilho", valor as String, obtido.sugestao?.gatilho?.name,
                    )

                    "alertaContem" -> conter(falhas, prefixo, "alerta", valor as String, obtido.alerta)
                    "motivoContem" -> conter(falhas, prefixo, "motivo", valor as String, obtido.motivo)
                    "observacaoContem" -> conter(falhas, prefixo, "observacao", valor as String, obtido.observacao)
                }
            }
        }
    }

    private fun comparar(
        falhas: MutableList<String>,
        prefixo: String,
        campo: String,
        esperado: String,
        obtido: String?,
    ) {
        if (esperado != obtido) falhas += "$prefixo: $campo esperava \"$esperado\", veio \"$obtido\""
    }

    private fun conter(
        falhas: MutableList<String>,
        prefixo: String,
        campo: String,
        trecho: String,
        obtido: String?,
    ) {
        val alvo = TextoUtil.normalizar(trecho)
        val texto = obtido?.let { TextoUtil.normalizar(it) }
        if (texto == null || !texto.contains(alvo)) {
            falhas += "$prefixo: $campo deveria conter \"$trecho\", veio \"$obtido\""
        }
    }

    private fun resumo(item: ItemInterpretado): String =
        "${item.nomeItem}=${item.quantidade ?: "null"}${item.unidade?.sigla ?: ""}"

    // =================================================================================
    // 2. Casos-prova da Secao 14 do documento, escritos a mao
    // =================================================================================

    @Test
    fun `duas quantidades na mesma linha viram dois itens`() {
        val itens = parser.interpretar("2kg de carne e meia duzia de ovo")
        assertEquals(2, itens.size)
        assertEquals(0, itens[0].quantidade!!.compareTo(BigDecimal(2)))
        assertEquals(Unidade.QUILO, itens[0].unidade)
        assertEquals(0, itens[1].quantidade!!.compareTo(BigDecimal(6)))
        assertEquals(Unidade.UNIDADE, itens[1].unidade)
    }

    @Test
    fun `um pacote e 1 mas uma duzia e 12`() {
        val pacote = parser.interpretar("um pacote de arroz").single()
        assertEquals(0, pacote.quantidade!!.compareTo(BigDecimal.ONE))
        assertEquals(Unidade.PACOTE, pacote.unidade)

        val duzia = parser.interpretar("uma duzia de ovos").single()
        assertEquals(0, duzia.quantidade!!.compareTo(BigDecimal(12)))
    }

    @Test
    fun `quantidade indefinida nao vira numero`() {
        val item = parser.interpretar("um pouco de sal").single()
        assertNull("'um pouco' nao pode virar 1", item.quantidade)
        assertNotNull(item.motivo)
        assertEquals("sal", TextoUtil.normalizar(item.nomeItem))
    }

    @Test
    fun `duzia de carne alerta mas nao bloqueia`() {
        val item = parser.interpretar("duzia de carne").single()
        assertEquals(0, item.quantidade!!.compareTo(BigDecimal(12)))
        assertNotNull("deveria alertar sobre peso", item.alerta)
        assertNull("alerta nao vira pergunta (G5)", item.sugestao)
        assertTrue("o item continua utilizavel", item.prontoParaAdicionar)
    }

    @Test
    fun `apelido de animal nunca vira numero em texto livre`() {
        listOf(
            "racao para cachorro",
            "quanto custa? cachorro",
            "queijo de cabra",
            "peru congelado",
            "carne de carneiro",
        ).forEach { linha ->
            parser.interpretar(linha).forEach { item ->
                val quantidade = item.quantidade
                if (quantidade != null) {
                    assertTrue(
                        "\"$linha\" nao pode render quantidade de apelido ($quantidade)",
                        quantidade.compareTo(BigDecimal.ONE) == 0,
                    )
                }
            }
        }
    }

    @Test
    fun `apelido de animal vira numero em posicao estritamente numerica`() {
        mapOf(
            "cachorro" to 5, "cabra" to 6, "carneiro" to 7, "peru" to 20,
            "coelho" to 10, "galo" to 13, "gato" to 14, "vaca" to 25,
            "avestruz" to 1, "touro" to 21,
        ).forEach { (palavra, valor) ->
            val lido = QuantidadeEmPosicaoNumerica.interpretar(palavra)
            assertNotNull("'$palavra' deveria ser lido no campo numerico", lido)
            assertEquals(palavra, 0, lido!!.valor.compareTo(BigDecimal(valor)))
        }
    }

    @Test
    fun `patinho sozinho e corte bovino e na expressao completa e 22`() {
        val corte = parser.interpretar("patinho").single()
        assertEquals("acougue", corte.categoriaSugerida)
        assertEquals(ClasseDeItem.PESAVEL, corte.classeDoItem)

        val expressao = parser.interpretar("dois patinhos na lagoa de arroz").single()
        assertEquals(0, expressao.quantidade!!.compareTo(BigDecimal(22)))
        assertEquals("arroz", TextoUtil.normalizar(expressao.nomeItem))
    }

    @Test
    fun `expressao popular sem de mais item nao converte`() {
        val item = parser.interpretar("dois patinhos na lagoa").single()
        assertEquals(
            "sem 'de' + item do lexico, a expressao fica literal",
            0,
            item.quantidade!!.compareTo(BigDecimal.ONE),
        )
    }

    @Test
    fun `kit declarado na linha preenche itensPorKit`() {
        val item = parser.interpretar("pacote com 2 de arroz").single()
        assertTrue(item.ehKit)
        assertEquals(2, item.itensPorKit)
    }

    @Test
    fun `capacidade desconhecida nao e chutada`() {
        val item = parser.interpretar("fardo de cerveja").single()
        assertNull("fardo de cerveja varia: nao pode inventar itensPorKit", item.itensPorKit)
        assertNotNull("mas precisa dizer por que", item.motivo)
    }

    @Test
    fun `linha irreconhecivel volta como item literal`() {
        val itens = parser.interpretar("zzz qqq")
        assertEquals(1, itens.size)
        assertEquals("zzz qqq", TextoUtil.normalizar(itens.single().nomeItem))
    }

    // =================================================================================
    // 2.1 Preco por segmento: a linha multi-item da adicao rapida
    // =================================================================================

    /**
     * A linha real do usuario (v1.2.0 perdia todos os preços e encolhia
     * "maça de peito" para "Maca"). Cada segmento precisa sair com nome,
     * quantidade, unidade e o proprio preco.
     */
    @Test
    fun `cada segmento da linha multi-item carrega o proprio preco`() {
        val itens = parser.interpretar(
            "Uva 1kg 3,99,limao 1kg 6,50,amaciante 2l 19,99,pacote de asa 750g 22,99, " +
                "maça de peito 5kg 89,90,suco de laranja natural 2l 19,99",
        )
        assertEquals(6, itens.size)

        val esperados = mapOf(
            "uva" to listOf("3.99", "1", "QUILO"),
            "limao" to listOf("6.50", "1", "QUILO"),
            "amaciante" to listOf("19.99", "2", "LITRO"),
            "asa" to listOf("22.99", "750", "GRAMA"),
            "maca de peito" to listOf("89.90", "5", "QUILO"),
            "suco de laranja natural" to listOf("19.99", "2", "LITRO"),
        )
        assertEquals(esperados.keys, itens.map { TextoUtil.normalizar(it.nomeItem) }.toSet())
        itens.forEach { item ->
            val chave = TextoUtil.normalizar(item.nomeItem)
            val precoEsperado = when (chave) {
                "uva" -> "3.99"
                "limao" -> "6.50"
                "amaciante" -> "19.99"
                "asa" -> "22.99"
                "maca de peito" -> "89.90"
                "suco de laranja natural" -> "19.99"
                else -> error("nome inesperado na linha multi-item: \"${item.nomeItem}\"")
            }
            val pesoOuVolumeEsperado = when (chave) {
                "uva", "limao" -> "1"
                "amaciante", "suco de laranja natural" -> "2"
                "asa" -> "750"
                "maca de peito" -> "5"
                else -> error("nome inesperado na linha multi-item: \"${item.nomeItem}\"")
            }
            val unidadeEsperada = when (chave) {
                "amaciante", "suco de laranja natural" -> Unidade.LITRO
                "asa" -> Unidade.GRAMA
                else -> Unidade.QUILO
            }
            assertEquals("[$chave] preco", 0, item.preco!!.compareTo(BigDecimal(precoEsperado)))
            assertEquals(
                "[$chave] quantidade",
                0,
                item.quantidade!!.compareTo(BigDecimal("1")),
            )
            assertEquals("[$chave] unidade", unidadeEsperada, item.unidade)
            assertEquals(
                "[$chave] pesoOuVolume",
                0,
                item.pesoOuVolume!!.compareTo(BigDecimal(pesoOuVolumeEsperado)),
            )
        }
    }

    @Test
    fun `leite com preco no fim continua single-item com o preco`() {
        val item = parser.interpretar("leite 4,99").single()
        assertEquals("leite", TextoUtil.normalizar(item.nomeItem))
        assertEquals(0, item.preco!!.compareTo(BigDecimal("4.99")))
        assertNull(item.observacao)
    }

    @Test
    fun `preco com prefixo de reais e extraido por segmento`() {
        val item = parser.interpretar("leite R$ 4,99").single()
        assertEquals("leite", TextoUtil.normalizar(item.nomeItem))
        assertEquals(0, item.preco!!.compareTo(BigDecimal("4.99")))
    }

    @Test
    fun `inteiro solto no fim e quantidade, nunca preco`() {
        val cafe = parser.interpretar("cafe 3").single()
        assertNull("inteiro sem separador nao e preco", cafe.preco)
        assertEquals(0, cafe.quantidade!!.compareTo(BigDecimal(3)))

        val arroz = parser.interpretar("arroz 3999").single()
        assertNull("3999 sem separador nao e preco", arroz.preco)
    }

    @Test
    fun `sem preco declarado o item fica com preco nulo`() {
        val arroz = parser.interpretar("2 kg de arroz").single()
        assertNull(arroz.preco)
        assertEquals(0, arroz.quantidade!!.compareTo(BigDecimal(2)))

        val carne = parser.interpretar("1,5 kg de carne").single()
        assertNull("o decimal de quantidade no inicio nao e preco", carne.preco)
        assertEquals(0, carne.quantidade!!.compareTo(BigDecimal("1.5")))
    }

    @Test
    fun `ovos com meia duzia no padrao P2 segue intacto`() {
        val item = parser.interpretar("ovos, meia duzia").single()
        assertEquals(0, item.quantidade!!.compareTo(BigDecimal(6)))
        assertNull(item.preco)
    }

    @Test
    fun `preco apos a virgula no padrao P2 e dinheiro, nao quantidade`() {
        val item = parser.interpretar("ovos, 12,50").single()
        assertEquals("ovos", TextoUtil.normalizar(item.nomeItem))
        assertEquals(0, item.preco!!.compareTo(BigDecimal("12.50")))
        assertEquals(
            "o decimal anexado nao entra na quantidade (fallback = 1 unidade)",
            0,
            item.quantidade!!.compareTo(BigDecimal.ONE),
        )
    }

    @Test
    fun `preco anexado com quantidade no prefixo mantem a quantidade`() {
        val item = parser.interpretar("ovos, meia duzia, 12,50").single()
        assertEquals(0, item.quantidade!!.compareTo(BigDecimal(6)))
        assertEquals(0, item.preco!!.compareTo(BigDecimal("12.50")))
    }

    @Test
    fun `inteiro anexado no P2 continua quantidade, nunca preco`() {
        val item = parser.interpretar("ovos, 12").single()
        assertNull(item.preco)
        assertEquals(0, item.quantidade!!.compareTo(BigDecimal(12)))
    }

    @Test
    fun `preco anexado no P2 convive com multi-item na mesma linha`() {
        val itens = parser.interpretar("ovos, 12,50, leite 2")
        assertEquals(2, itens.size)
        val ovos = itens[0]
        val leite = itens[1]
        assertEquals("ovos", TextoUtil.normalizar(ovos.nomeItem))
        assertEquals(0, ovos.preco!!.compareTo(BigDecimal("12.50")))
        assertEquals("leite", TextoUtil.normalizar(leite.nomeItem))
        assertEquals(0, leite.quantidade!!.compareTo(BigDecimal(2)))
        assertNull(leite.preco)
    }

    @Test
    fun `nome digitado com palavras alem do catalogo nao encolhe`() {
        val item = parser.interpretar("maça de peito").single()
        assertEquals("maca de peito", TextoUtil.normalizar(item.nomeItem))
        assertNull("nenhuma palavra propria sobrou para virar observacao", item.observacao)
    }

    @Test
    fun `embalagem como prefixo continua descartavel e preco vem junto`() {
        val item = parser.interpretar("pacote de asa 750g 22,99").single()
        assertEquals("asa", TextoUtil.normalizar(item.nomeItem))
        assertEquals(0, item.preco!!.compareTo(BigDecimal("22.99")))
        assertEquals(0, item.pesoOuVolume!!.compareTo(BigDecimal(750)))
    }

    @Test
    fun `extrairPreco nao come o preco do ultimo segmento multi-item`() {
        val linha = "Uva 1kg 3,99,limao 1kg 6,50"
        val multi = InterpretadorDeAdicaoRapida.extrairPreco(linha)
        assertNull("multi-item: quem extrai e o parser, por segmento", multi.preco)
        assertEquals("o restante segue intacto para o parser", linha, multi.restante)

        val single = InterpretadorDeAdicaoRapida.extrairPreco("arroz 5kg 24,90")
        assertEquals(0, single.preco!!.compareTo(BigDecimal("24.90")))
        assertEquals("arroz 5kg", single.restante)
    }

    // =================================================================================
    // 2.2 Itens colados por espaco, sem virgula (fronteira "preco -> nome")
    // =================================================================================

    @Test
    fun `dois itens colados por espaco com precos separam pelo nome apos o preco`() {
        val itens = parser.interpretar("Bolo de laranja 1kg 11,99 arroz 5kg 20,33")
        assertEquals(2, itens.size)

        val bolo = itens[0]
        val nomeBolo = TextoUtil.normalizar(bolo.nomeItem)
        assertTrue("nome do item 1 devia manter bolo: $nomeBolo", nomeBolo.contains("bolo"))
        assertTrue("nome do item 1 devia manter laranja: $nomeBolo", nomeBolo.contains("laranja"))
        assertEquals(0, bolo.pesoOuVolume!!.compareTo(BigDecimal(1)))
        assertEquals(0, bolo.preco!!.compareTo(BigDecimal("11.99")))

        val arroz = itens[1]
        assertEquals("arroz", TextoUtil.normalizar(arroz.nomeItem))
        assertEquals(0, arroz.pesoOuVolume!!.compareTo(BigDecimal(5)))
        assertEquals(0, arroz.preco!!.compareTo(BigDecimal("20.33")))
    }

    @Test
    fun `colagem com espaco funciona com embalagem iniciando o segundo item`() {
        val itens = parser.interpretar("leite 2l 6,49 pacote de asa 750g 22,99")
        assertEquals(2, itens.size)

        val leite = itens[0]
        assertEquals("leite", TextoUtil.normalizar(leite.nomeItem))
        assertEquals(0, leite.pesoOuVolume!!.compareTo(BigDecimal(2)))
        assertEquals(0, leite.preco!!.compareTo(BigDecimal("6.49")))

        val asa = itens[1]
        assertEquals("asa", TextoUtil.normalizar(asa.nomeItem))
        assertEquals(0, asa.pesoOuVolume!!.compareTo(BigDecimal(750)))
        assertEquals(0, asa.preco!!.compareTo(BigDecimal("22.99")))
    }

    @Test
    fun `tres itens colados por espaco viram tres itens com os precos certos`() {
        val itens = parser.interpretar("arroz 1kg 5,99 feijao 2kg 7,50 oleo 900ml 3,49")
        assertEquals(3, itens.size)
        assertEquals("arroz", TextoUtil.normalizar(itens[0].nomeItem))
        assertEquals(0, itens[0].preco!!.compareTo(BigDecimal("5.99")))
        assertEquals("feijao", TextoUtil.normalizar(itens[1].nomeItem))
        assertEquals(0, itens[1].preco!!.compareTo(BigDecimal("7.50")))
        assertEquals("oleo", TextoUtil.normalizar(itens[2].nomeItem))
        assertEquals(0, itens[2].preco!!.compareTo(BigDecimal("3.49")))
    }

    @Test
    fun `item unico com preco no fim nao se divide`() {
        val itens = parser.interpretar("arroz 5kg 20,33")
        assertEquals(1, itens.size)
        assertEquals("arroz", TextoUtil.normalizar(itens.single().nomeItem))
        assertEquals(0, itens.single().preco!!.compareTo(BigDecimal("20.33")))
    }

    @Test
    fun `quantidade na frente com unidade espacada nunca e fronteira de colagem`() {
        val itens = parser.interpretar("2 kg de arroz")
        assertEquals(1, itens.size)
        assertEquals(0, itens.single().quantidade!!.compareTo(BigDecimal(2)))
        assertEquals(Unidade.QUILO, itens.single().unidade)
        assertNull(itens.single().preco)
    }

    @Test
    fun `decimal de quantidade seguido de unidade nao divide a linha`() {
        val itens = parser.interpretar("1,5 kg de carne")
        assertEquals("o decimal e quantidade (P3), nao preco de item anterior", 1, itens.size)
        assertEquals(0, itens.single().quantidade!!.compareTo(BigDecimal("1.5")))
    }

    @Test
    fun `conectivo e entre itens colados continua dividindo como antes`() {
        val itens = parser.interpretar("cafe 3 e leite")
        assertEquals(2, itens.size)
        assertEquals("cafe", TextoUtil.normalizar(itens[0].nomeItem))
        assertEquals("leite", TextoUtil.normalizar(itens[1].nomeItem))
    }

    @Test
    fun `palavra apos preco sem outro item adiante nao divide`() {
        val itens = parser.interpretar("sabonete 3,99 dove")
        assertEquals("a marca solta e complemento do unico item", 1, itens.size)
        assertTrue(TextoUtil.normalizar(itens.single().nomeItem).contains("sabonete"))
    }

    // =================================================================================
    // 3. Sugestao nao bloqueante: desfechos
    // =================================================================================

    @Test
    fun `aplicar grava confirmacao e some com a sugestao`() {
        val item = parser.interpretar("cachorro").single()
        val sugestao = assertNotNull(item.sugestao).let { item.sugestao!! }
        assertEquals("G1", sugestao.gatilho.name)
        assertTrue("no maximo 2 opcoes", sugestao.opcoes.size <= 2)

        val resposta = SugestoesDoParser.responder(item, sugestao.opcoes.first(), MemoriaDoParser.VAZIA)
        assertNull(resposta.item.sugestao)
        assertEquals(0, resposta.item.quantidade!!.compareTo(BigDecimal(5)))
        assertEquals(Confianca.ALTA, resposta.item.confianca)
        assertTrue(resposta.memoria.confirmados.containsKey("cachorro"))
    }

    @Test
    fun `manter como esta grava rejeicao e nao pergunta de novo`() {
        val item = parser.interpretar("cachorro").single()
        val manter = item.sugestao!!.opcoes.first { it.manterComoEsta }
        val resposta = SugestoesDoParser.responder(item, manter, MemoriaDoParser.VAZIA)

        assertNull(resposta.item.sugestao)
        assertTrue(resposta.memoria.jaRejeitou("cachorro"))

        val segundaVez = ParserDeLinhaDeCompra(resposta.memoria).interpretar("cachorro").single()
        assertNull("termo rejeitado nao volta a sugerir", segundaVez.sugestao)
    }

    @Test
    fun `valor confirmado antes e aplicado direto`() {
        val memoria = MemoriaDoParser(confirmados = mapOf("cachorro" to BigDecimal(5)))
        val item = ParserDeLinhaDeCompra(memoria).interpretar("cachorro").single()
        assertNull("com valor aprendido nao se pergunta", item.sugestao)
        assertEquals(0, item.quantidade!!.compareTo(BigDecimal(5)))
    }

    @Test
    fun `ignorar deixa o item como esta e nada fica pendurado`() {
        val item = parser.interpretar("meia").single()
        assertEquals("G3", item.sugestao!!.gatilho.name)
        assertNull(SugestoesDoParser.ignorar(item).sugestao)
    }

    @Test
    fun `segunda duvida aplica a melhor hipotese sem nova pergunta`() {
        val item = parser.interpretar("meia").single()
        val depois = SugestoesDoParser.segundaDuvida(item, BigDecimal(6))
        assertNull(depois.sugestao)
        assertEquals(Confianca.BAIXA, depois.confianca)
        assertEquals(0, depois.quantidade!!.compareTo(BigDecimal(6)))
    }

    @Test
    fun `confianca alta nunca pergunta`() {
        listOf("2 kg de carne", "cafe 500g", "arroz 5kg", "meia duzia de ovos").forEach { linha ->
            parser.interpretar(linha).forEach { item ->
                assertNull("G7: \"$linha\" nao deveria perguntar nada", item.sugestao)
            }
        }
    }

    @Test
    fun `item fora do lexico vai para Outros sem perguntar`() {
        val item = parser.interpretar("biotina 5mg").single()
        assertEquals("outros", item.categoriaSugerida)
        assertNull("G8 nao pergunta", item.sugestao)
    }

    @Test
    fun `sugestao nunca passa de uma por item e de duas opcoes`() {
        casos()
            .filter { it["modo"] != "quantidade" }
            .forEach { caso ->
                val fonte = if (caso["fonte"] == "ocr") FonteDaEntrada.OCR else FonteDaEntrada.TEXTO
                parser.interpretar(caso["entrada"] as String, fonte).forEach { item ->
                    item.sugestao?.let { sugestao ->
                        assertTrue(
                            "\"${caso["entrada"]}\": no maximo 2 opcoes",
                            sugestao.opcoes.size in 1..2,
                        )
                        assertTrue(
                            "\"${caso["entrada"]}\": a sugestao precisa de texto legivel",
                            sugestao.pergunta.isNotBlank(),
                        )
                    }
                }
            }
    }

    @Test
    fun `ocr nunca sugere nada`() {
        casos().forEach { caso ->
            if (caso["modo"] == "quantidade") return@forEach
            val itens = parser.interpretar(caso["entrada"] as String, FonteDaEntrada.OCR)
            itens.forEach { item ->
                assertNull("OCR nao pergunta: \"${caso["entrada"]}\"", item.sugestao)
            }
        }
    }

    // =================================================================================
    // 4. Propriedades gerais
    // =================================================================================

    @Test
    fun `nenhuma linha e descartada`() {
        casos()
            .filter { it["modo"] != "quantidade" }
            .forEach { caso ->
                val entrada = caso["entrada"] as String
                val itens = parser.interpretar(entrada)
                assertTrue("\"$entrada\" nao pode sumir", itens.isNotEmpty())
                itens.forEach { item ->
                    assertTrue("\"$entrada\" produziu item sem nome", item.nomeItem.isNotBlank())
                }
            }
    }

    @Test
    fun `o parser e deterministico`() {
        casos().filter { it["modo"] != "quantidade" }.take(40).forEach { caso ->
            val entrada = caso["entrada"] as String
            val primeira = parser.interpretar(entrada).map { resumo(it) }
            val segunda = ParserDeLinhaDeCompra().interpretar(entrada).map { resumo(it) }
            assertEquals("\"$entrada\" mudou entre execucoes", primeira, segunda)
        }
    }

    @Test
    fun `unidade de saida fica sempre no conjunto canonico`() {
        val canonicas = Unidade.entries.toSet()
        casos().filter { it["modo"] != "quantidade" }.forEach { caso ->
            parser.interpretar(caso["entrada"] as String).forEach { item ->
                item.unidade?.let {
                    assertTrue("unidade fora do conjunto canonico: $it", it in canonicas)
                }
            }
        }
    }

    @Test
    fun `categoria de saida fica sempre entre as 14 padrao`() {
        val validas = setOf(
            "hortifruti", "padaria", "acougue", "laticinios", "congelados", "mercearia",
            "organicos", "bebidas", "limpeza", "higiene", "infantil", "pet", "bazar", "outros",
        )
        casos().filter { it["modo"] != "quantidade" }.forEach { caso ->
            parser.interpretar(caso["entrada"] as String).forEach { item ->
                item.categoriaSugerida?.let {
                    assertTrue("categoria invalida: $it (${caso["entrada"]})", it in validas)
                }
            }
        }
    }

    @Test
    fun `parse cabe folgado no orcamento de 16 ms`() {
        val linhaLonga = "arroz 5kg, feijao 2kg, cafe 500g, acucar 1kg, oleo de soja 900ml, " +
            "meia duzia de ovos, 2 kg de carne moida, leite integral 1l, pao frances 10, " +
            "detergente 500ml e papel higienico 12"
        require(linhaLonga.length in 150..200) { "a linha de teste tem ${linhaLonga.length} caracteres" }
        val linhaTipica = "cafe 500g"

        repeat(200) { parser.interpretar(linhaLonga); parser.interpretar(linhaTipica) }

        val mediaLonga = medir(linhaLonga)
        val mediaTipica = medir(linhaTipica)

        java.io.File("build/relatorios").mkdirs()
        java.io.File("build/relatorios/desempenho-parser.txt").writeText(
            buildString {
                appendLine("Desempenho do parser de texto livre (JVM do build)")
                appendLine("linha de ${linhaLonga.length} caracteres / 11 itens: " +
                    "${"%.3f".format(mediaLonga)} ms")
                appendLine("linha tipica de 1 item (\"$linhaTipica\"): ${"%.3f".format(mediaTipica)} ms")
                appendLine("orcamento do documento: bem abaixo de 16 ms no Snapdragon 425")
            },
        )

        // A linha de 11 itens e o pior caso possivel (a lista inteira numa linha
        // so). Mesmo com fator 8x de diferenca entre esta JVM e o Snapdragon
        // 425, 1 ms aqui equivale a ~8 ms la - dentro do quadro de 16 ms.
        //
        // Sob o agente do JaCoCo cada instrucao fica instrumentada e o tempo
        // triplica; medir ali seria medir o instrumento, nao o parser. Nesse
        // caso o limite e afrouxado na mesma proporcao (e o relatorio em
        // build/relatorios continua registrando o numero real do dia).
        // O JaCoCo injeta um campo estatico "$jacocoData" em toda classe que
        // instrumenta - e a deteccao exata, sem depender de java.lang.management
        // (ausente no classpath de teste do Android).
        val instrumentado = ParserDeLinhaDeCompra::class.java.declaredFields
            .any { campo -> campo.name.contains("jacoco", ignoreCase = true) }
        val fator = if (instrumentado) 3.0 else 1.0

        assertTrue(
            "pior caso levou ${"%.3f".format(mediaLonga)} ms por linha" +
                if (instrumentado) " (com JaCoCo ligado)" else "",
            mediaLonga < 1.0 * fator,
        )
        assertTrue(
            "linha tipica levou ${"%.3f".format(mediaTipica)} ms" +
                if (instrumentado) " (com JaCoCo ligado)" else "",
            mediaTipica < 0.15 * fator,
        )
    }

    private fun medir(linha: String): Double {
        val repeticoes = 300
        val inicio = System.nanoTime()
        repeat(repeticoes) { parser.interpretar(linha) }
        return (System.nanoTime() - inicio) / repeticoes / 1_000_000.0
    }

    private fun assertNotNull(valor: Any?): Any {
        org.junit.Assert.assertNotNull(valor)
        return valor!!
    }
}

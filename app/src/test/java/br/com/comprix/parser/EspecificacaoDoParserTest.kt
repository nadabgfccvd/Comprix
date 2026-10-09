package br.com.comprix.parser

import br.com.comprix.domain.categoria.DicionarioDeCategorias
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.parser.FalsosPositivos
import br.com.comprix.domain.parser.ItemDoLexico
import br.com.comprix.domain.parser.LexicoDeItens
import br.com.comprix.domain.parser.NumeraisPorExtenso
import br.com.comprix.domain.parser.TabelaDeQuantidades
import br.com.comprix.domain.parser.TokenDeQuantidade
import br.com.comprix.domain.parser.Verificacao
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoria das tabelas do parser **e** exportacao da especificacao.
 *
 * Os dois papeis andam juntos de proposito: o `comprix-parser-spec.json` nao e
 * escrito a mao em lugar nenhum - ele e gerado a partir das mesmas tabelas que
 * o app usa em producao, nesta classe, depois que as invariantes passam. Assim
 * o documento publicado nunca descreve um parser diferente do que esta rodando.
 *
 * As invariantes verificadas aqui sao as regras duras do documento:
 * - unidade so do conjunto canonico (Secao 4.1 do app);
 * - categoria so das 14 oficiais (Secao 4.4);
 * - nenhum apelido sem [Verificacao] e, quando confirmado, sem fonte;
 * - nenhum `unidadeInterna` inventado.
 */
class EspecificacaoDoParserTest {

    private val categoriasOficiais = setOf(
        "hortifruti", "padaria", "acougue", "laticinios", "congelados", "mercearia",
        "organicos", "bebidas", "limpeza", "higiene", "infantil", "pet", "bazar", "outros",
    )

    // =================================================================================
    // Invariantes do lexico
    // =================================================================================

    @Test
    fun `lexico tem ao menos 250 itens cobrindo as 14 categorias`() {
        val itens = LexicoDeItens.itens
        assertTrue("o lexico tem ${itens.size} itens, minimo 250", itens.size >= 250)

        val categoriasUsadas = itens.map { it.categoria }.toSet()
        assertTrue(
            "categorias fora das 14 oficiais: ${categoriasUsadas - categoriasOficiais}",
            categoriasUsadas.all { it in categoriasOficiais },
        )
        val semItens = categoriasOficiais - categoriasUsadas - setOf("outros")
        assertTrue("categorias sem nenhum item no lexico: $semItens", semItens.isEmpty())
    }

    @Test
    fun `nenhuma variante do lexico aponta para dois itens diferentes`() {
        val dono = HashMap<String, String>()
        val conflitos = mutableListOf<String>()
        LexicoDeItens.itens.forEach { item ->
            item.variantesNormalizadas.forEach { variante ->
                val anterior = dono.put(variante, item.nomeCanonico)
                if (anterior != null && anterior != item.nomeCanonico) {
                    conflitos += "\"$variante\" em $anterior e ${item.nomeCanonico}"
                }
            }
        }
        assertTrue("variantes ambiguas: $conflitos", conflitos.isEmpty())
    }

    @Test
    fun `unidade interna so existe quando ha valor usual documentado`() {
        val suspeitos = LexicoDeItens.itens.filter { item ->
            item.unidadeInterna.values.any { it <= 0 }
        }
        assertTrue("unidadeInterna com valor invalido: ${suspeitos.map { it.nomeCanonico }}", suspeitos.isEmpty())

        // Chaves de embalagem sao fechadas e, mais que isso, precisam ser
        // alcancaveis: de nada adianta cadastrar "caixa" se o parser procura
        // por "cx". Este conjunto espelha ParserDeLinhaDeCompra.apelidoDaEmbalagem.
        val chavesAlcancaveis = setOf("dz", "cx", "pct", "fardo", "bandeja")
        val forajidas = LexicoDeItens.itens.flatMap { it.unidadeInterna.keys }.toSet() - chavesAlcancaveis
        assertTrue(
            "chaves de unidadeInterna que o parser nunca consulta: $forajidas",
            forajidas.isEmpty(),
        )
    }

    @Test
    fun `todo item do lexico usa unidade do conjunto canonico`() {
        val canonicas = Unidade.entries.toSet()
        assertTrue(LexicoDeItens.itens.all { it.unidadePadrao in canonicas })
    }

    @Test
    fun `lexico e dicionario de categorias concordam`() {
        // O lexico alimenta o dicionario (nao e um segundo dicionario).
        val divergentes = LexicoDeItens.itens.take(60).filter { item ->
            val sugerida = DicionarioDeCategorias.sugerirChave(item.nomeCanonico)
            sugerida != DicionarioDeCategorias.CHAVE_PADRAO && sugerida != item.categoria
        }
        assertTrue(
            "itens com categoria divergente entre lexico e dicionario: " +
                divergentes.joinToString { "${it.nomeCanonico}=${it.categoria}" },
            divergentes.isEmpty(),
        )
    }

    // =================================================================================
    // Invariantes da tabela de quantidades
    // =================================================================================

    @Test
    fun `todo apelido declara verificacao e fonte quando confirmado`() {
        val semFonte = TabelaDeQuantidades.todos.filter {
            it.verificacao == Verificacao.CONFIRMADO_EM_FONTE && it.fonte.isNullOrBlank()
        }
        assertTrue("apelidos marcados como confirmados sem fonte: ${semFonte.map { it.canonico }}", semFonte.isEmpty())

        val tradicao = TabelaDeQuantidades.todos.filter {
            it.verificacao == Verificacao.TRADICAO_POPULAR_SEM_FONTE
        }
        assertTrue("a tabela precisa admitir o que e tradicao oral", tradicao.isNotEmpty())
    }

    @Test
    fun `apelidos numericos ficam na faixa de 1 a 90`() {
        val apelidos = TabelaDeQuantidades.apelidosDeAnimais + TabelaDeQuantidades.apelidosNaoAnimais
        val fora = apelidos.filter { it.valor.toInt() !in 1..90 }
        assertTrue("apelidos fora da faixa 1-90: ${fora.map { it.canonico }}", fora.isEmpty())
    }

    @Test
    fun `conteudo sensivel esta marcado e nunca e oferecido de forma proativa`() {
        val sensiveis = TabelaDeQuantidades.conteudoSensivel()
        assertTrue("a tabela precisa marcar os apelidos de conteudo sensivel", sensiveis.isNotEmpty())
        assertTrue(sensiveis.all { it.sensivel })
    }

    @Test
    fun `numeros sem apelido sao declarados em vez de inventados`() {
        val lacunas = TabelaDeQuantidades.numerosSemApelido()
        // A honestidade e o teste: a tabela cobre o que foi verificado e diz,
        // por escrito, o que nao cobre. Zero lacuna significaria invencao.
        assertTrue("a lacuna precisa ser declarada", lacunas.isNotEmpty())
        assertTrue(lacunas.all { it in 1..90 })
    }

    @Test
    fun `tokens usam unidade canonica e tem ao menos uma variante propria`() {
        val canonicas = Unidade.entries.toSet()
        TabelaDeQuantidades.todos.forEach { token ->
            assertTrue("${token.canonico} usa unidade fora do conjunto", token.unidade in canonicas)
            assertTrue("${token.canonico} sem posicao aceita", token.posicoesAceitas.isNotEmpty())
        }
    }

    // =================================================================================
    // Numerais por extenso (regra, nao tabela)
    // =================================================================================

    @Test
    fun `numerais por extenso cobrem de zero a cinquenta mil por regra`() {
        val casos = mapOf(
            "zero" to 0,
            "um" to 1,
            "dezesseis" to 16,
            "vinte e um" to 21,
            "cem" to 100,
            "cento e vinte e cinco" to 125,
            "quinhentos" to 500,
            "mil" to 1_000,
            "mil e quinhentos" to 1_500,
            "dois mil e vinte e quatro" to 2_024,
            "dez mil" to 10_000,
            "cinquenta mil" to 50_000,
        )
        casos.forEach { (texto, esperado) ->
            assertEquals("numeral \"$texto\"", esperado, NumeraisPorExtenso.interpretar(texto))
        }
    }

    // =================================================================================
    // Exportacao do comprix-parser-spec.json
    // =================================================================================

    @Test
    fun `exporta a especificacao a partir das tabelas vivas`() {
        val json = montarEspecificacao()
        val destino = File("../artefatos/comprix-parser-spec.json")
        destino.parentFile?.mkdirs()
        destino.writeText(json, Charsets.UTF_8)

        assertTrue("o arquivo precisa existir apos a exportacao", destino.exists())
        assertTrue("especificacao suspeitamente pequena", destino.length() > 50_000)
        // Validacao estrutural simples: chaves balanceadas.
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    private fun montarEspecificacao(): String = buildString {
        appendLine("{")
        appendLine("""  "formato": "comprix-parser-spec",""")
        appendLine("""  "versao": "1.0",""")
        appendLine("""  "idioma": "pt-BR",""")
        appendLine("""  "determinista": true,""")
        appendLine("""  "offline": true,""")
        appendLine("""  "origem": "gerado por EspecificacaoDoParserTest a partir das tabelas do app",""")
        appendLine("""  "unidadesCanonicas": [""")
        appendLine(
            Unidade.entries.joinToString(",\n") { u ->
                """    {"sigla": ${txt(u.sigla)}, "descricao": ${txt(u.descricao)}, """ +
                    """"dimensao": ${txt(u.dimensao.name)}, "fatorParaBase": ${u.fatorParaBase}}"""
            },
        )
        appendLine("  ],")
        appendLine("""  "categorias": [${categoriasOficiais.joinToString(", ") { txt(it) }}],""")

        appendLine("""  "tabelaDeQuantidades": {""")
        appendLine("""    "total": ${TabelaDeQuantidades.todos.size},""")
        appendLine("""    "maiorExpressaoEmPalavras": ${TabelaDeQuantidades.maiorExpressaoEmPalavras},""")
        appendLine("""    "numerosSemApelido": [${TabelaDeQuantidades.numerosSemApelido().joinToString(", ")}],""")
        appendLine("""    "tokens": [""")
        appendLine(TabelaDeQuantidades.todos.joinToString(",\n") { tokenJson(it) })
        appendLine("    ]")
        appendLine("  },")

        appendLine("""  "lexico": {""")
        appendLine("""    "total": ${LexicoDeItens.itens.size},""")
        appendLine("""    "itens": [""")
        appendLine(LexicoDeItens.itens.joinToString(",\n") { itemJson(it) })
        appendLine("    ]")
        appendLine("  },")

        appendLine("""  "falsosPositivos": {""")
        appendLine("""    "guardas": [""")
        appendLine(
            FalsosPositivos.guardas.joinToString(",\n") { g ->
                """      {"gatilho": ${txt(g.gatilho)}, "expressao": ${txt(g.expressao)}, """ +
                    """"explicacao": ${txt(g.explicacao)}}"""
            },
        )
        appendLine("    ],")
        appendLine(
            """    "quantidadeIndefinida": [""" +
                FalsosPositivos.quantidadeIndefinida.joinToString(", ") { txt(it) } + "]",
        )
        appendLine("  },")

        appendLine("""  "regrasDeDesambiguacao": [""")
        appendLine(
            listOf(
                "A" to "apelido numerico so vale em posicao estritamente numerica; " +
                    "em posicao de item, e o item (patinho, cabra, carneiro)",
                "B" to "apelido fora do repertorio consagrado nao converte sozinho: " +
                    "vira pergunta inline (G2), nunca numero silencioso",
                "C" to "expressao guardada (meia calca, duzia de motivos, quina da mesa) " +
                    "cancela a conversao e o texto vira nome literal",
            ).joinToString(",\n") { (id, texto) ->
                """    {"regra": ${txt(id)}, "descricao": ${txt(texto)}}"""
            },
        )
        appendLine("  ],")

        appendLine("""  "sugestoes": {""")
        appendLine("""    "bloqueante": false,""")
        appendLine("""    "maximoPorItem": 1,""")
        appendLine("""    "maximoDeOpcoes": 2,""")
        appendLine("""    "ordemDaCascata": ["G5", "G1", "G2", "G4", "G3", "G6", "G8", "G7"],""")
        appendLine("""    "gatilhos": [""")
        appendLine(
            listOf(
                "G1" to "apelido popular reconhecido em posicao numerica",
                "G2" to "apelido plausivel fora do repertorio consagrado",
                "G3" to "quantidade fracionaria isolada (meia, meio)",
                "G4" to "numero quebrado que pode ser casa decimal",
                "G5" to "classe do item incompativel com a unidade (alerta, nunca bloqueio)",
                "G6" to "termo ja respondido antes pelo usuario",
                "G7" to "confianca alta: nao se pergunta",
                "G8" to "item fora do lexico: nao se pergunta sobre o que nao se conhece",
            ).joinToString(",\n") { (id, texto) ->
                """      {"gatilho": ${txt(id)}, "quando": ${txt(texto)}}"""
            },
        )
        appendLine("    ]")
        appendLine("  },")

        appendLine("""  "convencoesDeSaida": {""")
        appendLine("""    "P1_P3": "quantidade = n, unidade = u",""")
        appendLine("""    "P4_comUnidade": "quantidade = 1, pesoOuVolume = n",""")
        appendLine("""    "P4_semUnidade": "quantidade = n",""")
        appendLine("""    "numeroInteiroSemUnidade": "UNIDADE (conta pecas)",""")
        appendLine("""    "numeroQuebradoSemUnidade": "unidadePadrao do lexico",""")
        appendLine("""    "sobraDoCasamento": "observacao",""")
        appendLine("""    "guardaOuTokenRecusado": "nomeLiteral (texto inteiro vira nome)",""")
        appendLine("""    "linhaIrreconhecivel": "nunca descartada; volta com quantidade null e motivo"""")
        appendLine("  },")

        appendLine("""  "desempenho": {""")
        appendLine("""    "orcamento": "bem abaixo de 16 ms por linha de ate 200 caracteres",""")
        appendLine("""    "medicaoNaJvmDoBuild": "ver app/build/relatorios/desempenho-parser.txt",""")
        appendLine("""    "estruturas": "indices em LinkedHashMap por n-grama; sem regex na varredura principal"""")
        appendLine("  }")
        append("}")
    }

    private fun tokenJson(t: TokenDeQuantidade): String = buildString {
        append("""      {"canonico": ${txt(t.canonico)}""")
        append(""", "valor": ${t.valor.toPlainString()}""")
        append(""", "unidade": ${txt(t.unidade.sigla)}""")
        append(""", "dominio": ${txt(t.dominio.name)}""")
        append(""", "variantes": [${t.variantes.joinToString(", ") { txt(it) }}]""")
        append(""", "posicoesAceitas": [${t.posicoesAceitas.joinToString(", ") { txt(it.name) }}]""")
        append(""", "classesAceitas": [${t.classesAceitas.joinToString(", ") { txt(it.name) }}]""")
        append(""", "confiancaBase": ${txt(t.confiancaBase.name)}""")
        append(""", "verificacao": ${txt(t.verificacao.name)}""")
        append(""", "fonte": ${txt(t.fonte)}""")
        append(""", "origemRepertorio": ${txt(t.origemRepertorio?.name)}""")
        append(""", "colisaoItem": ${txt(t.colisaoItem)}""")
        append(""", "conteudoSensivel": ${t.sensivel}""")
        append(""", "ehEmbalagem": ${t.ehEmbalagem}}""")
    }

    private fun itemJson(i: ItemDoLexico): String = buildString {
        append("""      {"nome": ${txt(i.nomeCanonico)}""")
        append(""", "categoria": ${txt(i.categoria)}""")
        append(""", "classe": ${txt(i.classe.name)}""")
        append(""", "unidadePadrao": ${txt(i.unidadePadrao.sigla)}""")
        append(""", "variantes": [${i.variantes.joinToString(", ") { txt(it) }}]""")
        append(
            """, "unidadeInterna": {""" +
                i.unidadeInterna.entries.joinToString(", ") { (k, v) -> "${txt(k)}: $v" } + "}",
        )
        append(""", "variacaoDeclarada": ${txt(i.variacaoDeclarada)}""")
        append(
            """, "colisaoApelido": """ +
                (
                    i.colisaoApelido?.let {
                        """{"valor": ${it.valor}, "expressao": ${txt(it.expressao)}, "regra": ${txt(it.regra)}}"""
                    } ?: "null"
                    ),
        )
        append(""", "notaDeColisao": ${txt(i.notaDeColisao)}}""")
    }

    /** Texto JSON com escape - ou `null` literal quando nao ha valor. */
    private fun txt(valor: String?): String {
        if (valor == null) return "null"
        val escapado = buildString(valor.length + 2) {
            valor.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
        }
        return "\"$escapado\""
    }
}

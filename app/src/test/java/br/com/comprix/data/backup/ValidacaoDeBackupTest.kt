package br.com.comprix.data.backup

import br.com.comprix.util.JsonSimples
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacaoDeBackupTest {
    private val layoutV1 = listOf(
        "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos",
        "compras", "memoria_categorias", "decisoes_parser",
    )
    private val layoutV1ComHistorico = listOf(
        "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos",
        "historico_precos", "compras", "memoria_categorias", "decisoes_parser",
    )

    /** Backup v1 no layout canônico, com registros enfiados na seção certa. */
    private fun backupV1(
        registros: Map<String, List<String>> = emptyMap(),
        layout: List<String> = layoutV1,
    ): List<String> = listOf("#COMPRIX-BACKUP v1") + layout.flatMap { tabela ->
        listOf("#TABELA $tabela") + registros[tabela].orEmpty()
    }

    /** Backup v2 com checksum correto (o rodapé cobre o corpo inteiro). */
    private fun backupV2(registros: Map<String, List<String>>): List<String> {
        val corpo = layoutV1ComHistorico.flatMap { tabela ->
            listOf("#TABELA $tabela") + registros[tabela].orEmpty()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        corpo.forEach { linha ->
            digest.update(linha.toByteArray(Charsets.UTF_8))
            digest.update('\n'.code.toByte())
        }
        val soma = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return listOf("#COMPRIX-BACKUP v2") + corpo + "#SHA256 $soma"
    }

    private val lista = """{"id":"1","nome":"Lista","criada":"1000","fim":"false","fav":"false"}"""
    private val loja = """{"id":"1","nome":"Loja","cor":"#1E8E5A"}"""
    private val item = """{"id":"1","lista":"1","prod":"1","qtd":"1","un":"UNIDADE","kit":"false","comp":"false","ordem":"0","modo":"POR_UNIDADE"}"""
    private val preco = """{"id":"1","item":"1","loja":"1","cent":"100","data":"1000","disp":"true"}"""

    @Test fun aceitaOsDoisLayoutsCanonicosComTabelasVazias() {
        assertNotNull(lerBackupV1(backupV1()))
        assertNotNull(lerBackupV1(backupV1(layout = layoutV1ComHistorico)))
    }

    @Test fun aceitaRegistrosCanonicosComEscapes() {
        val dados = mapOf("id" to "1", "nome" to "Leite \"especial\"\nIntegral\\")
        val resultado = lerBackupV1(backupV1(mapOf("compras" to listOf(JsonSimples.paraJson(dados)))))
        assertEquals(dados, resultado?.get("compras")?.single())
    }

    @Test fun rejeitaCadaCabecalhoEssencialAusente() {
        for (nome in layoutV1) {
            assertNull(nome, lerBackupV1(backupV1().filterNot { it == "#TABELA $nome" }))
        }
    }

    @Test fun rejeitaTruncamentoQuePerdeCabecalhosDaCauda() {
        // v1 nunca tem rodapé: truncar no meio perde os cabeçalhos que vêm depois.
        val truncadoAposCompras = backupV1().takeWhile { it != "#TABELA memoria_categorias" }
        assertNull(lerBackupV1(truncadoAposCompras))

        val truncadoAposMemoria = backupV1().takeWhile { it != "#TABELA decisoes_parser" }
        assertNull(lerBackupV1(truncadoAposMemoria))

        val truncadoAposHistorico = backupV1(layout = layoutV1ComHistorico)
            .takeWhile { it != "#TABELA compras" }
        assertNull(lerBackupV1(truncadoAposHistorico))
    }

    @Test fun rejeitaTabelaDesconhecidaDuplicadaOuReordenada() {
        assertNull(lerBackupV1(backupV1() + "#TABELA desconhecida"))
        assertNull(lerBackupV1(backupV1() + "#TABELA compras"))
        val reordenado = listOf(
            "categorias", "produtos", "itens", "estabelecimentos", "listas", "precos",
            "compras", "memoria_categorias", "decisoes_parser",
        )
        assertNull(lerBackupV1(backupV1(layout = reordenado)))
        // Seção fora da posição em que toda versão que escreveu v1 a gravava.
        assertNull(
            lerBackupV1(
                backupV1(
                    layout = layoutV1.filterNot { it == "memoria_categorias" } +
                        listOf("memoria_categorias"),
                ),
            ),
        )
    }

    @Test fun rejeitaDadosInvalidosSemAceitarObjetosParciais() {
        val invalidos = listOf("{\"id\":\"1\",}", "{\"id\":\"1\"", "{}", "lixo", "{\"id\":\"1\",\"id\":\"2\"}")
        for (linha in invalidos) assertNull(linha, lerBackupV1(backupV1(mapOf("compras" to listOf(linha)))))
        assertNull(lerBackupV1(backupV1() + "#TABELA compras"))
        assertNull(lerBackupV1(listOf("#COMPRIX-BACKUP v2") + backupV1().drop(1)))
        assertNull(lerBackupV1(listOf("#COMPRIX-BACKUP v1", "{\"id\":\"1\"}") + backupV1().drop(1)))
    }

    @Test fun rejeitaIdentidadesAusentesInvalidasOuDuplicadas() {
        assertNull(lerBackupV1(backupV1(mapOf("compras" to listOf("""{"nome":"Sem id"}""")))))
        assertNull(lerBackupV1(backupV1(mapOf("compras" to listOf("""{"id":"0"}""")))))
        assertNull(lerBackupV1(backupV1(mapOf("compras" to listOf("""{"id":"xyz"}""")))))
        assertNull(
            lerBackupV1(
                backupV1(
                    mapOf(
                        "compras" to listOf(
                            """{"id":"1","nome":"Lista","data":"1000","total":"1","econ":"0","itens":"1","estab":"Loja","cats":"{}","cats_raw":"{}"}""",
                            """{"id":"1","nome":"Outra","data":"1000","total":"1","econ":"0","itens":"1","estab":"Loja","cats":"{}","cats_raw":"{}"}""",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test fun rejeitaReferenciasDeItemEPrecoQuebradas() {
        // item apontando para lista inexistente
        assertNull(
            lerBackupV1(
                backupV1(
                    mapOf(
                        "listas" to listOf(lista),
                        "itens" to listOf("""{"id":"1","lista":"999","prod":"1","qtd":"1","un":"UNIDADE","kit":"false","comp":"false","ordem":"0","modo":"POR_UNIDADE"}"""),
                    ),
                ),
            ),
        )
        // preço apontando para item inexistente
        assertNull(
            lerBackupV1(
                backupV1(
                    mapOf(
                        "listas" to listOf(lista),
                        "itens" to listOf(item),
                        "precos" to listOf("""{"id":"1","item":"999","loja":"1","cent":"100","data":"1000","disp":"true"}"""),
                    ),
                ),
            ),
        )
        // preço apontando para loja inexistente (o Room não tem essa FOREIGN KEY)
        assertNull(
            lerBackupV1(
                backupV1(
                    mapOf(
                        "listas" to listOf(lista),
                        "itens" to listOf(item),
                        "precos" to listOf("""{"id":"1","item":"1","loja":"999","cent":"100","data":"1000","disp":"true"}"""),
                    ),
                ),
            ),
        )
    }

    @Test fun aceitaReferenciasQueOAppDeixaOrfasNaVidaReal() {
        // produto apagado deixa item em lista; categoria do usuário apagada deixa
        // produto e memória; loja apagada deixa histórico. Round-trip tem que voltar.
        val resultado = lerBackupV1(
            backupV1(
                mapOf(
                    "produtos" to listOf("""{"id":"1","nome":"Arroz","norm":"arroz","cat":"77","un":"UNIDADE","selos":"","gluten":"INDETERMINADO","alerg":"","att":"1000","fav":"false"}"""),
                    "estabelecimentos" to listOf(loja),
                    "listas" to listOf(lista),
                    "itens" to listOf(item),
                    "precos" to listOf(preco),
                    "memoria_categorias" to listOf("""{"nome":"arroz","cat":"77","vezes":"1","att":"1000"}"""),
                ),
            ),
        )
        assertNotNull(resultado)
        assertEquals(1, resultado?.get("itens")?.size)
        assertEquals(1, resultado?.get("precos")?.size)
    }

    @Test fun rejeitaReferenciasQuebradasTambemNoV2ComChecksumValido() {
        assertNull(
            lerBackup(
                backupV2(
                    mapOf(
                        "listas" to listOf(lista),
                        "itens" to listOf(item),
                        "precos" to listOf("""{"id":"1","item":"1","loja":"999","cent":"100","data":"1000","disp":"true"}"""),
                    ),
                ),
            ),
        )
    }

    @Test fun aceitaV2ComChecksumValidoEReferenciasIntegras() {
        assertNotNull(
            lerBackup(
                backupV2(
                    mapOf(
                        "estabelecimentos" to listOf(loja),
                        "listas" to listOf(lista),
                        "itens" to listOf(item),
                        "precos" to listOf(preco),
                        "historico_precos" to listOf("""{"id":"1","prod":"1","loja":"1","cent":"100","qtd":"1","data":"1000"}"""),
                    ),
                ),
            ),
        )
    }
}

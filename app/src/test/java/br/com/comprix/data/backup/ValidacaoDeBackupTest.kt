package br.com.comprix.data.backup

import br.com.comprix.util.JsonSimples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacaoDeBackupTest {
    private val nomes = listOf("categorias", "produtos", "estabelecimentos", "listas", "itens", "precos", "compras")
    private fun backup(vararg linhas: String) = listOf("#COMPRIX-BACKUP v1") +
        nomes.map { "#TABELA $it" } + linhas

    @Test fun aceitaBackupLegadoComTabelasVazias() {
        assertNotNull(lerBackupV1(backup()))
        assertNotNull(lerBackupV1(backup("#TABELA decisoes_parser")))
    }

    @Test fun aceitaRegistrosCanonicosComEscapes() {
        val dados = mapOf("id" to "1", "nome" to "Leite \"especial\"\nIntegral\\")
        val resultado = lerBackupV1(backup(JsonSimples.paraJson(dados)))
        assertEquals(dados, resultado?.get("compras")?.single())
    }

    @Test fun rejeitaCadaTabelaEssencialAusente() {
        for (nome in nomes) assertNull(lerBackupV1(backup().filterNot { it == "#TABELA $nome" }))
    }

    @Test fun rejeitaDadosInvalidosSemAceitarObjetosParciais() {
        val invalidos = listOf("{\"id\":\"1\",}", "{\"id\":\"1\"", "{}", "lixo", "{\"id\":\"1\",\"id\":\"2\"}")
        for (linha in invalidos) assertNull(linha, lerBackupV1(backup(linha)))
        assertNull(lerBackupV1(backup("#TABELA compras")))
        assertNull(lerBackupV1(listOf("#COMPRIX-BACKUP v2") + backup().drop(1)))
        assertNull(lerBackupV1(listOf("#COMPRIX-BACKUP v1", "{\"id\":\"1\"}") + backup().drop(1)))
    }
    @Test fun rejeitaIdentidadesAusentesInvalidasOuDuplicadas() {
        assertNull(lerBackupV1(backup("{\"nome\":\"Sem id\"}")))
        assertNull(lerBackupV1(backup("{\"id\":\"0\"}")))
        assertNull(lerBackupV1(backup("{\"id\":\"xyz\"}")))
        assertNull(lerBackupV1(backup("{\"id\":\"1\"}", "{\"id\":\"1\"}")))
    }

}

package br.com.comprix.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoDeBackupTest {
    private val essenciais = setOf("categorias", "produtos", "estabelecimentos", "listas", "itens", "precos", "compras")

    @Test fun aceitaBackupV1ComTabelasVazias() {
        assertTrue(backupPossuiTabelasEssenciais(essenciais))
        assertTrue(backupPossuiTabelasEssenciais(essenciais + "decisoes_parser"))
    }

    @Test fun rejeitaTabelasAusentesOuDesconhecidas() {
        assertFalse(backupPossuiTabelasEssenciais(emptySet()))
        assertFalse(backupPossuiTabelasEssenciais(setOf("desconhecida")))
        for (tabela in essenciais) assertFalse(backupPossuiTabelasEssenciais(essenciais - tabela))
    }
}

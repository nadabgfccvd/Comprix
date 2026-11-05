package br.com.comprix.data.backup

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.ListaEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GerenciadorDeBackupTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var gerenciador: GerenciadorDeBackup

    @Before fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java).build()
        gerenciador = GerenciadorDeBackup(contexto, banco)
    }

    @After fun fechar() = banco.close()

    private fun lista(nome: String) = ListaEntity(
        id = 1, nome = nome, criadaEm = 1000, finalizada = false, finalizadaEm = null,
    )

    @Test fun backupExportadoRestauraLista() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        banco.listaDao().atualizar(lista("Alterada"))
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
    }

    @Test fun backupInvalidoNaoApagaLista() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val invalido = "#COMPRIX-BACKUP v1\n#TABELA desconhecida\n"
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
    }

    @Test fun falhaDeChaveEstrangeiraReverteExclusoes() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        // A linha de item aponta para uma lista inexistente, causando falha
        // depois das exclusões e exercitando o rollback da transação real.
        val invalido = """
            #COMPRIX-BACKUP v1
            #TABELA categorias
            #TABELA produtos
            #TABELA estabelecimentos
            #TABELA listas
            #TABELA itens
            {"id":"1","lista":"999","prod":"1"}
            #TABELA precos
            #TABELA compras
        """.trimIndent()
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
    }
}

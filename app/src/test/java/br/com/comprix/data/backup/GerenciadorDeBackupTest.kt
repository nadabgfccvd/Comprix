package br.com.comprix.data.backup

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.HistoricoPrecoEntity
import br.com.comprix.data.local.PerfilRestricoesEntity
import br.com.comprix.data.local.AlergiaCustomizadaEntity
import br.com.comprix.data.local.ConfiguracoesEntity
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.data.local.MemoriaCategoriaEntity
import br.com.comprix.data.local.DecisaoDoParserEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.data.local.CompraEntity
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
    @Test fun backupPreservaHistoricoDePrecos() = runBlocking {
        val historico = HistoricoPrecoEntity(1, 7, 8, 1234, "500", 1000)
        banco.precoDao().registrarHistorico(historico)
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        banco.precoDao().registrarHistorico(HistoricoPrecoEntity(2, 7, 8, 999, "500", 2000))
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(listOf(historico), banco.precoDao().listarHistoricoCompleto())
    }

    @Test fun backupLegadoNaoApagaHistoricoAtual() = runBlocking {
        val historico = HistoricoPrecoEntity(1, 7, 8, 1234, "500", 1000)
        banco.listaDao().inserir(lista("Original"))
        banco.precoDao().registrarHistorico(historico)
        val legado = """
            #COMPRIX-BACKUP v1
            #TABELA categorias
            #TABELA produtos
            #TABELA estabelecimentos
            #TABELA listas
            #TABELA itens
            #TABELA precos
            #TABELA compras
        """.trimIndent()
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertEquals(listOf(historico), banco.precoDao().listarHistoricoCompleto())
    }

    @Test fun historicoInvalidoReverteRestauracao() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val invalido = """
            #COMPRIX-BACKUP v1
            #TABELA categorias
            #TABELA produtos
            #TABELA estabelecimentos
            #TABELA listas
            #TABELA itens
            #TABELA precos
            #TABELA historico_precos
            {"id":"1","prod":"invalido"}
            #TABELA compras
        """.trimIndent()
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
    }

    @Test fun backupV2DetectaTruncamentoEAlteracoes() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        val backup = saida.toString(Charsets.UTF_8.name())
        assertTrue(backup.startsWith("#COMPRIX-BACKUP v2\n"))
        assertTrue(backup.contains("#SHA256 "))
        val semRodape = backup.substringBefore("#SHA256 ")
        assertFalse(gerenciador.restaurar(semRodape.byteInputStream()).sucesso)
        assertFalse(gerenciador.restaurar(backup.replace("Original", "Alterado").byteInputStream()).sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
    }

    @Test fun backupPreservaRestricoesEAlergias() = runBlocking {
        val perfil = PerfilRestricoesEntity(alergenos = "LEITE|SOJA", semGluten = true)
        val alergia = AlergiaCustomizadaEntity(id = 1, nome = "Castanha", palavras = "castanha|nozes")
        banco.configuracoesDao().salvarPerfil(perfil)
        banco.alergiaCustomizadaDao().inserir(alergia)
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        banco.configuracoesDao().salvarPerfil(PerfilRestricoesEntity(alergenos = "", semGluten = false))
        banco.alergiaCustomizadaDao().inserir(AlergiaCustomizadaEntity(2, "Outro", "outro"))
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(perfil, banco.configuracoesDao().carregarPerfil())
        assertEquals(listOf(alergia), banco.alergiaCustomizadaDao().listarTodas())
    }

    @Test fun backupLegadoSemRestricoesPreservaDadosLocais() = runBlocking {
        val perfil = PerfilRestricoesEntity(alergenos = "LEITE", semGluten = true)
        val alergia = AlergiaCustomizadaEntity(1, "Castanha", "castanha")
        banco.configuracoesDao().salvarPerfil(perfil)
        banco.alergiaCustomizadaDao().inserir(alergia)
        val legado = """
            #COMPRIX-BACKUP v1
            #TABELA categorias
            #TABELA produtos
            #TABELA estabelecimentos
            #TABELA listas
            #TABELA itens
            #TABELA precos
            #TABELA compras
        """.trimIndent()
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(perfil, banco.configuracoesDao().carregarPerfil())
        assertEquals(listOf(alergia), banco.alergiaCustomizadaDao().listarTodas())
    }

    @Test fun backupRestauraConfiguracoesLixeiraEAprendizados() = runBlocking {
        val config = ConfiguracoesEntity(tema = "ESCURO", altoContraste = true, escalaDaFonte = 120,
            coresDinamicas = false, sons = true, vibracao = false, modoTecnico = true,
            onboardingConcluido = true, nutrientesComparados = "SODIO", dicasVistas = "DICA",
            ultimoBackupMs = 1234L, metaEconomiaCentavos = 500L)
        val lixo = RegistroDeLixeiraEntity(1, "LISTA", "Compras", null, 7, "{}", 1000)
        val memoria = MemoriaCategoriaEntity("arroz", 1, 2, 3)
        val decisao = DecisaoDoParserEntity("leite", "REJEITADO", null, 1, 4)
        banco.configuracoesDao().salvar(config)
        banco.lixeiraDao().inserir(lixo)
        banco.memoriaCategoriaDao().salvar(memoria)
        banco.decisaoDoParserDao().salvar(decisao)
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        banco.lixeiraDao().removerTudo()
        banco.memoriaCategoriaDao().salvar(MemoriaCategoriaEntity("outro", 2, 1, 5))
        banco.decisaoDoParserDao().salvar(DecisaoDoParserEntity("outro", "REJEITADO", null, 1, 5))
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(config.copy(ultimoBackupMs = 0), banco.configuracoesDao().carregar())
        assertEquals(listOf(lixo), banco.lixeiraDao().listarTodas())
        assertEquals(listOf(memoria), banco.memoriaCategoriaDao().listarTodas())
        assertEquals(listOf(decisao), banco.decisaoDoParserDao().listarTodas())
    }

    @Test fun backupLegadoPreservaConfiguracoesELixeiraAusentes() = runBlocking {
        val config = ConfiguracoesEntity(tema = "CLARO", altoContraste = false, escalaDaFonte = 100,
            coresDinamicas = false, sons = true, vibracao = true, modoTecnico = false,
            onboardingConcluido = true, nutrientesComparados = "", dicasVistas = "",
            ultimoBackupMs = 1234L, metaEconomiaCentavos = null)
        val lixo = RegistroDeLixeiraEntity(1, "LISTA", "Compras", null, 7, "{}", 1000)
        banco.configuracoesDao().salvar(config)
        banco.lixeiraDao().inserir(lixo)
        val legado = """
            #COMPRIX-BACKUP v1
            #TABELA categorias
            #TABELA produtos
            #TABELA estabelecimentos
            #TABELA listas
            #TABELA itens
            #TABELA precos
            #TABELA compras
        """.trimIndent()
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(config, banco.configuracoesDao().carregar())
        assertEquals(listOf(lixo), banco.lixeiraDao().listarTodas())
    }

    @Test fun backupPreservaAspasEApostrofosEmJsonAninhado() = runBlocking {
        val nutricao = "{\"porcao\":\"D'água\"}"
        val categorias = "{\"D'água\":\"123\"}"
        val produto = ProdutoEntity(id = 1, nome = "Produto", nomeNormalizado = "produto", categoriaId = 1,
            codigoBarras = null, unidadePadrao = "UNIDADE", infoNutricionalJson = nutricao,
            selosAltoEm = "", ingredientes = null, gluten = "INDETERMINADO", alergenos = "",
            dataValidade = null, dataFabricacao = null, pesoMedioEstimadoEmBase = null, atualizadoEm = 1)
        val compra = CompraEntity(id = 1, listaId = null, nomeLista = "Lista", data = 1,
            totalPagoCentavos = 123, economiaCentavos = 0, quantidadeItens = 1,
            descricaoEstabelecimento = "Loja", estabelecimentoPrincipalId = null,
            gastosPorCategoriaJson = categorias)
        banco.produtoDao().salvar(produto)
        banco.compraDao().inserir(compra)
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals(nutricao, banco.produtoDao().porId(1)?.infoNutricionalJson)
        assertEquals(categorias, banco.compraDao().porId(1)?.gastosPorCategoriaJson)
    }

}

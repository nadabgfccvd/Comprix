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
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    /** Layout canônico que as versões v1.4.0 a v1.6.0 gravavam (v1 sem histórico). */
    private val layoutV1 = listOf(
        "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos",
        "compras", "memoria_categorias", "decisoes_parser",
    )

    /** Layout v1 da janela pós-v1.6.0, entre o histórico entrar no backup e o checksum v2. */
    private val layoutV1ComHistorico = listOf(
        "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos",
        "historico_precos", "compras", "memoria_categorias", "decisoes_parser",
    )

    /** Backup v1 legítimo no layout canônico, com os registros enfiados nas seções certas. */
    private fun backupV1(
        registros: Map<String, List<String>> = emptyMap(),
        layout: List<String> = layoutV1,
    ): String = (listOf("#COMPRIX-BACKUP v1") + layout.flatMap { tabela ->
        listOf("#TABELA $tabela") + registros[tabela].orEmpty()
    }).joinToString("\n")

    private fun listaJson(id: Long, nome: String) =
        """{"id":"$id","nome":"$nome","criada":"1000","fim":"false","fav":"false"}"""

    private fun lojaJson(id: Long, nome: String) = """{"id":"$id","nome":"$nome","cor":"#1E8E5A"}"""

    private fun itemJson(id: Long, listaId: Long, produtoId: Long) =
        """{"id":"$id","lista":"$listaId","prod":"$produtoId","qtd":"1","un":"UNIDADE","kit":"false","comp":"false","ordem":"0","modo":"POR_UNIDADE"}"""

    private fun precoJson(id: Long, itemId: Long, lojaId: Long) =
        """{"id":"$id","item":"$itemId","loja":"$lojaId","cent":"100","data":"1000","disp":"true"}"""

    private fun copiasDeSeguranca(): List<File> =
        gerenciador.pastaDeBackups()
            .listFiles { arquivo -> arquivo.name.startsWith(GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA) }
            .orEmpty()
            .sortedBy { it.lastModified() }

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
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun backupV1TruncadoNoMeioEhRejeitadoSemApagar() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        // Truncado depois do cabeçalho de compras: perdeu os cabeçalhos de
        // memoria_categorias e decisoes_parser que todo v1 legítimo carrega.
        val truncado = backupV1(
            mapOf(
                "estabelecimentos" to listOf(lojaJson(1, "Loja")),
                "listas" to listOf(listaJson(2, "Nova")),
            ),
        ).lineSequence().takeWhile { it != "#TABELA memoria_categorias" }.joinToString("\n")
        val resultado = gerenciador.restaurar(truncado.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun backupComReferenciaDeListaQuebradaEhRejeitadoAntesDeApagar() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val invalido = backupV1(
            mapOf(
                "listas" to listOf(listaJson(2, "Nova")),
                "itens" to listOf(itemJson(1, 999, 1)),
            ),
        )
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun backupComPrecoDeLojaInexistenteEhRejeitadoSemApagar() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        // O esquema do Room NÃO tem FOREIGN KEY de preço para loja — sem a
        // validação semântica, esse arquivo restaurava "com sucesso" e deixava
        // preço apontando para loja fantasma.
        val invalido = backupV1(
            mapOf(
                "listas" to listOf(listaJson(1, "Lista")),
                "itens" to listOf(itemJson(1, 1, 1)),
                "precos" to listOf(precoJson(1, 1, 999)),
            ),
        )
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun falhaDeInsercaoNoMeioReverteExclusoes() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        // Duas lojas com o mesmo nome passam da validação (ids distintos) e
        // quebram o índice único de nome no meio da transação, depois das
        // exclusões — exercitando o rollback real com a cópia de segurança já
        // gravada (política legada: v1 válido até a validação aguentar).
        val invalido = backupV1(
            mapOf(
                "estabelecimentos" to listOf(lojaJson(1, "Loja"), lojaJson(2, "Loja")),
                "listas" to listOf(listaJson(2, "Nova")),
            ),
        )
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertEquals(1, copiasDeSeguranca().size)
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
        // v1.4.0–v1.6.0 de verdade: layout canônico sem a seção de histórico.
        val legado = backupV1()
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertEquals(listOf(historico), banco.precoDao().listarHistoricoCompleto())
        // Cancelado antes da cópia de segurança: restauração abortada não gera cópia.
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun historicoInvalidoReverteRestauracao() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val invalido = backupV1(
            mapOf("historico_precos" to listOf("""{"id":"1","prod":"invalido"}""")),
            layout = layoutV1ComHistorico,
        )
        val resultado = gerenciador.restaurar(invalido.byteInputStream())
        assertFalse(resultado.sucesso)
        assertEquals("Original", banco.listaDao().porId(1)?.nome)
        assertTrue(copiasDeSeguranca().isEmpty())
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
        val legado = backupV1()
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
        val legado = backupV1()
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

    @Test fun backupV1CompletoRestauraRegistrosDeTodasAsSecoes() = runBlocking {
        val legado = backupV1(
            mapOf(
                "categorias" to listOf("""{"id":"1","nome":"Hortifrúti","icone":"frutas","ordem":"1","origem":"PADRAO","chave":"hortifruti"}"""),
                "produtos" to listOf("""{"id":"1","nome":"Arroz","norm":"arroz","cat":"1","un":"UNIDADE","selos":"","gluten":"INDETERMINADO","alerg":"","att":"1000","fav":"false"}"""),
                "estabelecimentos" to listOf(lojaJson(1, "Loja")),
                "listas" to listOf(listaJson(1, "Lista")),
                "itens" to listOf(itemJson(1, 1, 1)),
                "precos" to listOf(precoJson(1, 1, 1)),
                "compras" to listOf("""{"id":"1","nome":"Lista","data":"1000","total":"100","econ":"0","itens":"1","estab":"Loja","cats":"{}","cats_raw":"{}"}"""),
                "memoria_categorias" to listOf("""{"nome":"arroz","cat":"1","vezes":"1","att":"1000"}"""),
                "decisoes_parser" to listOf("""{"termo":"leite","decisao":"REJEITADO","vezes":"1","att":"1000"}"""),
            ),
        )
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals("Lista", banco.listaDao().porId(1)?.nome)
        assertEquals("Arroz", banco.produtoDao().porId(1)?.nome)
        assertEquals(1, banco.itemDao().listarTodos().size)
        assertEquals(1, banco.precoDao().listarTodos().size)
        assertEquals(1, banco.compraDao().porId(1)?.quantidadeItens)
        assertEquals(1, banco.memoriaCategoriaDao().listarTodas().size)
        assertEquals(1, banco.decisaoDoParserDao().listarTodas().size)
        assertTrue(resultado.mensagem.contains("O estado anterior foi salvo"))
    }

    @Test fun restauracaoLegadaCriaCopiaDeSegurancaDoEstadoAnterior() = runBlocking {
        banco.listaDao().inserir(lista("Atual"))
        val legado = backupV1(mapOf("listas" to listOf(listaJson(2, "Legada"))))
        val resultado = gerenciador.restaurar(legado.byteInputStream())
        assertTrue(resultado.mensagem, resultado.sucesso)
        assertEquals("Legada", banco.listaDao().porId(2)?.nome)
        assertNull(banco.listaDao().porId(1))

        // A cópia guarda o estado ANTERIOR em v2 íntegro (com checksum)...
        val copias = copiasDeSeguranca()
        assertEquals(1, copias.size)
        val conteudo = copias.single().readText()
        assertTrue(conteudo.startsWith("#COMPRIX-BACKUP v2\n"))
        assertTrue(conteudo.contains("\"nome\":\"Atual\""))
        // ...e restaura de volta: o "desfazer" da restauração funciona de ponta a ponta.
        val volta = gerenciador.restaurar(copias.single().inputStream())
        assertTrue(volta.mensagem, volta.sucesso)
        assertEquals("Atual", banco.listaDao().porId(1)?.nome)
        assertNull(banco.listaDao().porId(2))
        assertEquals(1, copiasDeSeguranca().size) // a cópia continua lá (v2 não gera outra)
    }

    @Test fun restauracaoVerificadaNaoCriaCopiaDeSeguranca() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val saida = ByteArrayOutputStream()
        gerenciador.exportar(saida)
        val resultado = gerenciador.restaurar(ByteArrayInputStream(saida.toByteArray()))
        assertTrue(resultado.mensagem, resultado.sucesso)
        // O v2 tem checksum: integridade verificável não precisa de cópia extra.
        assertTrue(copiasDeSeguranca().isEmpty())
    }

    @Test fun copiaDeSegurancaMantemSomenteAsTresMaisRecentes() = runBlocking {
        val pasta = gerenciador.pastaDeBackups()
        repeat(4) { indice ->
            val antiga = File(pasta, "${GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA}antiga$indice.cbk")
            antiga.writeText("x")
            antiga.setLastModified(1_000L * (indice + 1))
        }
        banco.listaDao().inserir(lista("Atual"))
        val legado = backupV1(mapOf("listas" to listOf(listaJson(2, "Legada"))))
        assertTrue(gerenciador.restaurar(legado.byteInputStream()).sucesso)
        val nomes = copiasDeSeguranca().map { it.name }
        assertEquals(3, nomes.size)
        assertTrue(nomes.any { it == "${GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA}antiga3.cbk" })
        assertTrue(nomes.any { it == "${GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA}antiga2.cbk" })
        assertFalse(nomes.any { it.startsWith("${GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA}antiga0") })
        assertFalse(nomes.any { it.startsWith("${GerenciadorDeBackup.PREFIXO_COPIA_SEGURANCA}antiga1") })
    }

    @Test fun restauracaoLegadaAbortaSeNaoPuderSalvarCopiaDeSeguranca() = runBlocking {
        banco.listaDao().inserir(lista("Original"))
        val legado = backupV1(mapOf("listas" to listOf(listaJson(2, "Nova"))))
        val pasta = gerenciador.pastaDeBackups()
        assumeTrue("sem permissão de escrita na pasta de backups", pasta.setWritable(false))
        try {
            val resultado = gerenciador.restaurar(legado.byteInputStream())
            assertFalse(resultado.sucesso)
            assertTrue(resultado.mensagem, resultado.mensagem.contains("cópia de segurança"))
            assertEquals("Original", banco.listaDao().porId(1)?.nome)
        } finally {
            pasta.setWritable(true)
        }
    }
}

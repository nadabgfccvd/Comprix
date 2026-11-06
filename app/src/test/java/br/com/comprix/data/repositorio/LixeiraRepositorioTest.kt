package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.AlergiaCustomizadaEntity
import br.com.comprix.data.local.CategoriaEntity
import br.com.comprix.data.local.CompraEntity
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.PrecoEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.util.TextoUtil
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `LixeiraRepositorio`: o contrato de exclusão recuperável do app, de ponta a
 * ponta com Room em memória.
 *
 * Toda exclusão recuperável passa por aqui: `enviar*` serializa, grava na
 * tabela `lixeira` e apaga o original; `restaurar` reconstrói com IDs novos e
 * degradações deliberadas (produto sumido é recriado, nome em uso ganha
 * sufixo, preço anotado não volta). Fica pinado aqui o comportamento que a
 * documentação do backup depende: `enviarLoja` apaga os preços antes da loja,
 * o sucesso da restauração remove o registro da lixeira e a falha o mantém, e
 * a purga usa o prazo de 30 dias de forma inclusiva (`<=` no DAO).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LixeiraRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var lixeira: LixeiraRepositorio

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        lixeira = LixeiraRepositorio(
            lixeiraDao = banco.lixeiraDao(),
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            categoriaDao = banco.categoriaDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            precoDao = banco.precoDao(),
            compraDao = banco.compraDao(),
            alergiaDao = banco.alergiaCustomizadaDao(),
        )
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- auxiliares -------------------------------------------------------------------

    private suspend fun novoProduto(nome: String): Long = banco.produtoDao().salvar(
        ProdutoEntity(
            nome = nome,
            nomeNormalizado = TextoUtil.normalizar(nome),
            categoriaId = banco.categoriaDao().listarTodas().first().id,
            codigoBarras = null,
            unidadePadrao = "UNIDADE",
            infoNutricionalJson = null,
            selosAltoEm = "",
            ingredientes = null,
            gluten = "INDETERMINADO",
            alergenos = "",
            dataValidade = null,
            dataFabricacao = null,
            pesoMedioEstimadoEmBase = null,
            atualizadoEm = 1_000L,
        ),
    )

    private suspend fun novaLista(nome: String): Long = banco.listaDao().inserir(
        ListaEntity(nome = nome, criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null),
    )

    private suspend fun itemNa(listaId: Long, produtoId: Long): Long = banco.itemDao().inserir(
        ItemEntity(
            listaId = listaId,
            produtoId = produtoId,
            quantidade = "2",
            unidade = "UNIDADE",
            pesoOuVolume = null,
            ehKit = false,
            itensPorKit = null,
            comprado = false,
            ordemManual = 0,
            modoComparacao = "POR_UNIDADE",
            observacao = null,
        ),
    )

    private suspend fun novaLoja(nome: String, cor: String = "#1E8E5A"): Long =
        banco.estabelecimentoDao().inserir(EstabelecimentoEntity(nome = nome, corHex = cor))

    private suspend fun precoNo(itemDaListaId: Long, estabelecimentoId: Long) {
        banco.precoDao().salvar(
            PrecoEntity(
                itemDaListaId = itemDaListaId,
                estabelecimentoId = estabelecimentoId,
                precoCentavos = 1_250,
                dataRegistro = 1_700_000_000_000L,
                disponivel = true,
            ),
        )
    }

    private suspend fun registroNa(
        tipo: String,
        titulo: String,
        payload: String,
        enviadaEm: Long,
    ): Long = banco.lixeiraDao().inserir(
        RegistroDeLixeiraEntity(
            tipo = tipo,
            titulo = titulo,
            detalhe = null,
            refId = 0,
            payload = payload,
            enviadaEm = enviadaEm,
        ),
    )

    // --- enviar -----------------------------------------------------------------------

    @Test
    fun `enviarLista de id inexistente devolve false e nao grava na lixeira`() = runBlocking {
        assertFalse(lixeira.enviarLista(999L))
        assertEquals(0, lixeira.contar())
    }

    @Test
    fun `enviarLista grava o registro e apaga a lista com os itens`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        itemNa(listaId, produtoId)

        assertTrue(lixeira.enviarLista(listaId))

        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.LISTAS.name, registro.tipo)
        assertEquals("Semanal", registro.titulo)
        assertEquals(listaId, registro.refId)
        assertTrue(registro.detalhe!!.startsWith("1 itens · criada em "))
        assertNull(banco.listaDao().porId(listaId))
        assertTrue(banco.itemDao().listarDaLista(listaId).isEmpty())
    }

    @Test
    fun `preco anotado no item da lista enviada desaparece junto`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        val itemId = itemNa(listaId, produtoId)
        val lojaId = novaLoja("Mercado A")
        precoNo(itemId, lojaId)

        assertTrue(lixeira.enviarLista(listaId))

        assertTrue(banco.precoDao().listarTodos().isEmpty())
    }

    @Test
    fun `enviarLoja apaga os precos dela antes de apagar a loja`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        val itemId = itemNa(listaId, produtoId)
        val lojaId = novaLoja("Mercado A", cor = "#123456")
        precoNo(itemId, lojaId)

        assertTrue(lixeira.enviarLoja(lojaId))

        assertTrue(banco.precoDao().listarTodos().isEmpty())
        assertNull(banco.estabelecimentoDao().porId(lojaId))
        // A lista e o item sobrevivem: só a loja e os preços dela saem.
        assertNotNull(banco.itemDao().porId(itemId))
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.LOJAS.name, registro.tipo)
        assertTrue(registro.payload.contains("Mercado A"))
        assertTrue(registro.payload.contains("#123456"))
    }

    @Test
    fun `enviarCategoria recusa as padrao e aceita a do usuario`() = runBlocking {
        val padraoId = banco.categoriaDao().listarTodas().first { it.origem == "PADRAO" }.id
        val doUsuario = banco.categoriaDao().inserir(
            CategoriaEntity(
                nome = "Marcas próprias",
                icone = "bazar",
                ordemPadrao = 900,
                origem = "USUARIO",
                chave = "marcas-proprias",
            ),
        )

        assertFalse(lixeira.enviarCategoria(padraoId))
        assertTrue(lixeira.enviarCategoria(doUsuario))

        assertTrue(banco.categoriaDao().listarTodas().none { it.id == doUsuario })
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.CATEGORIAS.name, registro.tipo)
        assertEquals("Marcas próprias", registro.titulo)
    }

    @Test
    fun `enviarProduto grava e apaga do catalogo`() = runBlocking {
        val produtoId = novoProduto("Café")

        assertTrue(lixeira.enviarProduto(produtoId))

        assertNull(banco.produtoDao().porId(produtoId))
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.PRODUTOS.name, registro.tipo)
        assertEquals("Café", registro.titulo)
    }

    @Test
    fun `enviarCompra grava e apaga do historico`() = runBlocking {
        val compraId = banco.compraDao().inserir(
            CompraEntity(
                listaId = null,
                nomeLista = "Mensal",
                data = 1_700_000_000_000L,
                totalPagoCentavos = 12_345,
                economiaCentavos = 678,
                quantidadeItens = 9,
                descricaoEstabelecimento = "Mercado A",
                estabelecimentoPrincipalId = null,
                gastosPorCategoriaJson = "",
            ),
        )

        assertTrue(lixeira.enviarCompra(compraId))

        assertNull(banco.compraDao().porId(compraId))
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.COMPRAS.name, registro.tipo)
        assertEquals(compraId, registro.refId)
    }

    @Test
    fun `enviarAlergia grava o detalhe com as palavras separadas por virgula`() = runBlocking {
        val alergiaId = banco.alergiaCustomizadaDao().inserir(
            AlergiaCustomizadaEntity(nome = "Lactose", palavras = "leite|queijo|"),
        )

        assertTrue(lixeira.enviarAlergia(alergiaId))

        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals(LixeiraRepositorio.TipoDeLixeira.ALERGIAS.name, registro.tipo)
        assertEquals("leite, queijo", registro.detalhe)
        assertTrue(banco.alergiaCustomizadaDao().listarTodas().none { it.id == alergiaId })
    }

    // --- restaurar --------------------------------------------------------------------

    @Test
    fun `restaurar sem registro avisa que nao encontrou`() = runBlocking {
        assertEquals("Registro não encontrado na lixeira.", lixeira.restaurar(999L))
    }

    @Test
    fun `restaurar tipo desconhecido avisa que nao sabe`() = runBlocking {
        val registroId = registroNa(
            tipo = "ARQUIVO",
            titulo = "Mistério",
            payload = "{}",
            enviadaEm = 1_700_000_000_000L,
        )

        assertEquals("Não sei restaurar este tipo de registro.", lixeira.restaurar(registroId))
        assertNotNull(banco.lixeiraDao().porId(registroId))
    }

    @Test
    fun `restaurar lista recria sem tocar nas listas criadas depois`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        itemNa(listaId, produtoId)
        assertTrue(lixeira.enviarLista(listaId))
        val listaCriadaDepois = novaLista("Outra")

        val mensagem = lixeira.restaurar(banco.lixeiraDao().listarTodas().single { it.tipo == "LISTAS" }.id)

        assertTrue(mensagem.contains("Semanal"))
        assertEquals(0, lixeira.contar())
        val restaurada = banco.listaDao().listarTodas().single { it.nome == "Semanal" }
        // A restauração insere com id autogerado; a lista criada depois do
        // envio permanece intacta e com o seu próprio id.
        assertEquals("Outra", banco.listaDao().porId(listaCriadaDepois)!!.nome)
        val itens = banco.itemDao().listarDaLista(restaurada.id)
        assertEquals(1, itens.size)
        assertEquals(produtoId, itens.single().produtoId)
    }

    @Test
    fun `restaurar lista recria o produto sumido na primeira categoria`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        itemNa(listaId, produtoId)
        assertTrue(lixeira.enviarLista(listaId))
        banco.produtoDao().remover(produtoId)
        val primeiraCategoria = banco.categoriaDao().listarTodas().first().id

        assertTrue(lixeira.restaurar(banco.lixeiraDao().listarTodas().single().id).contains("Semanal"))

        val recriado = banco.produtoDao().porNomeNormalizado(TextoUtil.normalizar("Arroz"))
        assertNotNull(recriado)
        assertEquals(primeiraCategoria, recriado!!.categoriaId)
        val restaurada = banco.listaDao().listarTodas().single { it.nome == "Semanal" }
        assertEquals(1, banco.itemDao().listarDaLista(restaurada.id).size)
    }

    @Test
    fun `restaurar lista pula o item sem produto reencontravel e mantem a lista`() = runBlocking {
        val registroId = registroNa(
            tipo = LixeiraRepositorio.TipoDeLixeira.LISTAS.name,
            titulo = "Sem itens",
            payload = """{"dados":{"nome":"Semanal","criada":"1700000000000","finalizada":"false","favorita":"false"},"itens":[{"prodId":"0","nomeProduto":"","qtd":"2","un":"UNIDADE"}]}""",
            enviadaEm = 1_700_000_000_000L,
        )

        assertEquals("\"Sem itens\" restaurado(a).", lixeira.restaurar(registroId))

        val restaurada = banco.listaDao().listarTodas().single { it.nome == "Semanal" }
        assertTrue(banco.itemDao().listarDaLista(restaurada.id).isEmpty())
        assertEquals(0, lixeira.contar())
    }

    @Test
    fun `restaurar loja com nome em uso ganha o sufixo restaurada`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        val itemId = itemNa(listaId, produtoId)
        val lojaId = novaLoja("Mercado A", cor = "#123456")
        precoNo(itemId, lojaId)
        assertTrue(lixeira.enviarLoja(lojaId))
        novaLoja("Mercado A")

        assertTrue(lixeira.restaurar(banco.lixeiraDao().listarTodas().single().id).contains("Mercado A"))

        val nomes = banco.estabelecimentoDao().listarTodos().map { it.nome }.sorted()
        assertEquals(listOf("Mercado A", "Mercado A (restaurada)"), nomes)
        val restaurada = banco.estabelecimentoDao().listarTodos().single { it.nome == "Mercado A (restaurada)" }
        assertEquals("#123456", restaurada.corHex)
    }

    @Test
    fun `restaurar produto que ja voltou a existir falha e registro permanece`() = runBlocking {
        val produtoId = novoProduto("Café")
        assertTrue(lixeira.enviarProduto(produtoId))
        val registroId = banco.lixeiraDao().listarTodas().single().id
        novoProduto("Café")

        assertEquals("Não consegui restaurar \"Café\".", lixeira.restaurar(registroId))

        assertNotNull(banco.lixeiraDao().porId(registroId))
        assertEquals(1, banco.produtoDao().listarTodos().size)
    }

    @Test
    fun `restaurar categoria cuja chave ja existe falha e registro permanece`() = runBlocking {
        val doUsuario = banco.categoriaDao().inserir(
            CategoriaEntity(
                nome = "Marcas próprias",
                icone = "bazar",
                ordemPadrao = 900,
                origem = "USUARIO",
                chave = "marcas-proprias",
            ),
        )
        assertTrue(lixeira.enviarCategoria(doUsuario))
        val registroId = banco.lixeiraDao().listarTodas().single().id
        banco.categoriaDao().inserir(
            CategoriaEntity(
                nome = "Outra com a mesma chave",
                icone = "outros",
                ordemPadrao = 901,
                origem = "USUARIO",
                chave = TextoUtil.normalizar("Marcas próprias"),
            ),
        )

        assertEquals("Não consegui restaurar \"Marcas próprias\".", lixeira.restaurar(registroId))

        assertNotNull(banco.lixeiraDao().porId(registroId))
    }

    @Test
    fun `restaurar compra com payload minimo usa os valores padrao`() = runBlocking {
        val registroId = registroNa(
            tipo = LixeiraRepositorio.TipoDeLixeira.COMPRAS.name,
            titulo = "Mensal",
            payload = """{"nomeLista":"Compra de novembro"}""",
            enviadaEm = 1_700_000_000_000L,
        )

        assertEquals("\"Mensal\" restaurado(a).", lixeira.restaurar(registroId))

        val compra = banco.compraDao().listarTodas().single()
        assertEquals("Compra de novembro", compra.nomeLista)
        assertNull(compra.listaId)
        assertEquals(0, compra.totalPagoCentavos)
        assertEquals(0, compra.economiaCentavos)
        assertEquals(0, compra.quantidadeItens)
        assertEquals("", compra.descricaoEstabelecimento)
        assertTrue(compra.data > 0)
        assertEquals(0, lixeira.contar())
    }

    // --- esvaziar e purgar --------------------------------------------------------------

    @Test
    fun `purga apaga no prazo inclusive e mantem o que ainda cabe`() = runBlocking {
        val agora = 1_800_000_000_000L
        val limite = agora - LixeiraRepositorio.PRAZO_EM_MS
        val foraDoPrazo = registroNa("ARQUIVO", "fora do prazo", "{}", limite - 1)
        val noLimite = registroNa("ARQUIVO", "exatamente no prazo", "{}", limite)
        val dentroDoPrazo = registroNa("ARQUIVO", "ainda cabe", "{}", limite + 1)

        assertEquals(2, lixeira.purgarExpirados(agora))

        assertNull(banco.lixeiraDao().porId(foraDoPrazo))
        assertNull(banco.lixeiraDao().porId(noLimite))
        assertNotNull(banco.lixeiraDao().porId(dentroDoPrazo))
    }

    @Test
    fun `removerRegistro inexistente devolve false e existente true`() = runBlocking {
        assertFalse(lixeira.removerRegistro(999L))
        val registroId = registroNa("ARQUIVO", "qualquer", "{}", 1_700_000_000_000L)
        assertTrue(lixeira.removerRegistro(registroId))
        assertEquals(0, lixeira.contar())
    }

    @Test
    fun `esvaziarPorTipo so toca a secao pedida e esvaziarTudo limpa o resto`() = runBlocking {
        val deLista = registroNa(
            LixeiraRepositorio.TipoDeLixeira.LISTAS.name, "Semanal", "{}", 1_700_000_000_000L,
        )
        val deLoja = registroNa(
            LixeiraRepositorio.TipoDeLixeira.LOJAS.name, "Mercado A", "{}", 1_700_000_000_000L,
        )

        assertEquals(1, lixeira.esvaziarPorTipo(LixeiraRepositorio.TipoDeLixeira.LISTAS))
        assertNull(banco.lixeiraDao().porId(deLista))
        assertNotNull(banco.lixeiraDao().porId(deLoja))

        assertEquals(1, lixeira.esvaziarTudo())
        assertEquals(0, lixeira.contar())
    }

    @Test
    fun `expiraEm soma o prazo de 30 dias`() {
        val enviadaEm = 1_700_000_000_000L
        val registro = RegistroDeLixeiraEntity(
            tipo = "LISTAS", titulo = "Semanal", detalhe = null, refId = 1,
            payload = "{}", enviadaEm = enviadaEm,
        )

        val esperado = Instant.ofEpochMilli(enviadaEm + LixeiraRepositorio.PRAZO_EM_MS)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()

        assertEquals(esperado, lixeira.expiraEm(registro))
    }
}

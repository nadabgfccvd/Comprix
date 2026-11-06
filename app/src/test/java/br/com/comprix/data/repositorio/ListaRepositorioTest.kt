package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.local.PrecoEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
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
 * `ListaRepositorio`: o contrato das listas de compras, de ponta a ponta com
 * Room em memória.
 *
 * As funções puras do companion (`proximoNomeDeCopia` e `nivelDoOrcamento`)
 * já têm teste próprio em `ListasTest`/`OrcamentoTest`; aqui fica o que passa
 * pelo banco: criação com nome sugerido, numeração de ordem dos itens, a
 * duplicação que copia itens desmarcados e NÃO copia preços, o ciclo
 * favorita/finalizada/orçamento, o total estimado pelo menor preço disponível
 * e positivo, e a exclusão que desvia para a Lixeira quando ela existe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ListaRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var repositorio: ListaRepositorio

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        repositorio = repositorioComLixeira()
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- auxiliares -------------------------------------------------------------------

    /** Repositorio ligado a uma Lixeira real sobre o mesmo banco. */
    private fun repositorioComLixeira(): ListaRepositorio = ListaRepositorio(
        listaDao = banco.listaDao(),
        itemDao = banco.itemDao(),
        produtoDao = banco.produtoDao(),
        precoDao = banco.precoDao(),
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
        ),
    )

    private suspend fun novaLista(nome: String): Long = banco.listaDao().inserir(
        ListaEntity(nome = nome, criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null),
    )

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

    private suspend fun itemDa(listaId: Long, produtoId: Long, quantidade: BigDecimal = BigDecimal.ONE): Long =
        repositorio.adicionarItem(
            ItemDaLista(listaId = listaId, produtoId = produtoId, quantidade = quantidade),
        )

    private suspend fun precoNo(itemDaListaId: Long, estabelecimentoId: Long, centavos: Long, disponivel: Boolean = true) {
        banco.precoDao().salvar(
            PrecoEntity(
                itemDaListaId = itemDaListaId,
                estabelecimentoId = estabelecimentoId,
                precoCentavos = centavos,
                dataRegistro = 1_700_000_000_000L,
                disponivel = disponivel,
            ),
        )
    }

    private suspend fun loja(nome: String): Long =
        banco.estabelecimentoDao().inserir(EstabelecimentoEntity(nome = nome, corHex = "#1E8E5A"))

    private fun zeroCentavos(a: BigDecimal, b: BigDecimal) = assertEquals(0, a.compareTo(b))

    // --- criar e renomear ---------------------------------------------------------------

    @Test
    fun `criarLista usa o nome informado e o id existe`() = runBlocking {
        val id = repositorio.criarLista("Feira da semana")

        val lista = repositorio.lista(id)
        assertNotNull(lista)
        assertEquals("Feira da semana", lista!!.nome)
    }

    @Test
    fun `criarLista com null ou nome em branco cai no nome sugerido do mes`() = runBlocking {
        assertEquals(Formatadores.nomeSugeridoDeLista(), banco.listaDao().porId(repositorio.criarLista(null))!!.nome)
        assertEquals(Formatadores.nomeSugeridoDeLista(), banco.listaDao().porId(repositorio.criarLista("   "))!!.nome)
    }

    @Test
    fun `renomearLista apara os espacos antes de gravar`() = runBlocking {
        val id = novaLista("Velha")

        repositorio.renomearLista(ListaDeCompras(id = id, nome = "Velha"), "  Feira do mês  ")

        assertEquals("Feira do mês", banco.listaDao().porId(id)!!.nome)
    }

    // --- remover com e sem lixeira ------------------------------------------------------

    @Test
    fun `removerLista desvia para a lixeira quando existe e apaga direto quando nao`() = runBlocking {
        val idLixeira = novaLista("Com lixeira")
        val idDireto = novaLista("Sem lixeira")
        val semLixeira = ListaRepositorio(
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            precoDao = banco.precoDao(),
            lixeira = null,
        )

        repositorio.removerLista(idLixeira)
        semLixeira.removerLista(idDireto)

        assertNull(banco.listaDao().porId(idLixeira))
        assertNull(banco.listaDao().porId(idDireto))
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals("LISTAS", registro.tipo)
        assertEquals("Com lixeira", registro.titulo)
    }

    // --- favorita, finalizada e orcamento -----------------------------------------------

    @Test
    fun `favoritarLista marca e desmarca e id inexistente nao lanca`() = runBlocking {
        val id = novaLista("Feira")

        repositorio.favoritarLista(id, true)
        assertTrue(banco.listaDao().porId(id)!!.favorita)
        repositorio.favoritarLista(id, false)
        assertFalse(banco.listaDao().porId(id)!!.favorita)
        repositorio.favoritarLista(999L, true) // não deve lançar
    }

    @Test
    fun `marcarFinalizada grava o momento e reabrirLista limpa`() = runBlocking {
        val id = novaLista("Feira")
        val momento = LocalDateTime.of(2026, 11, 6, 8, 30)

        repositorio.marcarFinalizada(id, momento)
        val finalizada = banco.listaDao().porId(id)!!
        assertTrue(finalizada.finalizada)
        assertEquals(momento, Mapeadores.epochParaDataHora(finalizada.finalizadaEm!!))

        repositorio.reabrirLista(id)
        val reaberta = banco.listaDao().porId(id)!!
        assertFalse(reaberta.finalizada)
        assertNull(reaberta.finalizadaEm)
    }

    @Test
    fun `definirOrcamento grava, remove com null e ignora id inexistente`() = runBlocking {
        val id = novaLista("Feira")

        repositorio.definirOrcamento(id, 10_000L)
        assertEquals(10_000L, banco.listaDao().porId(id)!!.orcamentoCentavos)

        repositorio.definirOrcamento(id, null)
        assertNull(banco.listaDao().porId(id)!!.orcamentoCentavos)

        repositorio.definirOrcamento(999L, 5_000L) // não deve lançar
    }

    // --- itens --------------------------------------------------------------------------

    @Test
    fun `adicionarItem numera a ordem manual de 10 em 10`() = runBlocking {
        val listaId = novaLista("Feira")
        val produtoId = novoProduto("Arroz")

        itemDa(listaId, produtoId)
        itemDa(listaId, produtoId)
        itemDa(listaId, produtoId)

        assertEquals(listOf(10, 20, 30), banco.itemDao().listarDaLista(listaId).map { it.ordemManual })
    }

    @Test
    fun `listarItens casa o item com o produto e pula o orfao`() = runBlocking {
        val listaId = novaLista("Feira")
        val produtoId = novoProduto("Arroz")
        itemDa(listaId, produtoId)
        // Item órfão: gravado direto no DAO com produto que não existe.
        banco.itemDao().inserir(
            ItemEntity(
                listaId = listaId,
                produtoId = 999L,
                quantidade = "1",
                unidade = "UNIDADE",
                pesoOuVolume = null,
                ehKit = false,
                itensPorKit = null,
                comprado = false,
                ordemManual = 20,
                modoComparacao = "POR_UNIDADE",
                observacao = null,
            ),
        )

        val itens = repositorio.listarItens(listaId)

        assertEquals(1, itens.size)
        assertEquals(produtoId, itens.single().item.produtoId)
        assertEquals("Arroz", itens.single().produto.nome)
    }

    @Test
    fun `somarQuantidadeDoProduto soma na primeira ocorrencia e devolve false fora da lista`() = runBlocking {
        val listaId = novaLista("Feira")
        val produtoId = novoProduto("Leite")
        itemDa(listaId, produtoId, quantidade = BigDecimal("2"))
        itemDa(listaId, produtoId, quantidade = BigDecimal("5"))

        assertTrue(repositorio.somarQuantidadeDoProduto(listaId, produtoId))
        val quantidades = banco.itemDao().listarDaLista(listaId).map { it.quantidade }
        assertEquals("3", quantidades.first())
        assertEquals("5", quantidades.last())

        assertFalse(repositorio.somarQuantidadeDoProduto(listaId, novoProduto("Café")))
    }

    @Test
    fun `marcarComprado e desmarcarTodos afetam so a lista pedida e removerItem tira so o item`() = runBlocking {
        val listaA = novaLista("A")
        val listaB = novaLista("B")
        val produtoId = novoProduto("Arroz")
        val itemA = itemDa(listaA, produtoId)
        itemDa(listaB, produtoId)

        repositorio.marcarComprado(itemA, true)
        assertTrue(banco.itemDao().porId(itemA)!!.comprado)
        repositorio.desmarcarTodos(listaA)
        assertFalse(banco.itemDao().porId(itemA)!!.comprado)
        assertTrue(banco.itemDao().listarDaLista(listaB).single().comprado.not())

        val sobrou = banco.itemDao().listarDaLista(listaB).single()
        repositorio.removerItem(Mapeadores.paraDominio(sobrou))
        assertTrue(banco.itemDao().listarDaLista(listaB).isEmpty())
    }

    @Test
    fun `aplicarOrdem reordena, ignora id inexistente e mapa vazio`() = runBlocking {
        val listaId = novaLista("Feira")
        val produtoId = novoProduto("Arroz")
        val i1 = itemDa(listaId, produtoId)
        val i2 = itemDa(listaId, produtoId)
        val i3 = itemDa(listaId, produtoId)

        repositorio.aplicarOrdem(mapOf(i1 to 30, i2 to 10, i3 to 20, 999L to 5))
        assertEquals(listOf(i2, i3, i1), banco.itemDao().listarDaLista(listaId).map { it.id })

        repositorio.aplicarOrdem(emptyMap()) // não deve lançar nem alterar nada
        assertEquals(listOf(i2, i3, i1), banco.itemDao().listarDaLista(listaId).map { it.id })
    }

    // --- duplicar -------------------------------------------------------------------------

    @Test
    fun `duplicarLista copia itens desmarcados e nao copia precos`() = runBlocking {
        val listaId = novaLista("Semanal")
        val produtoId = novoProduto("Arroz")
        val itemComprado = itemDa(listaId, produtoId)
        val itemAberto = itemDa(listaId, produtoId)
        val lojaId = loja("Mercado A")
        precoNo(itemComprado, lojaId, 500)
        repositorio.marcarComprado(itemComprado, true)

        val novoId = repositorio.duplicarLista(listaId)

        assertNotNull(novoId)
        assertEquals("Semanal (cópia)", banco.listaDao().porId(novoId!!)!!.nome)
        val itens = banco.itemDao().listarDaLista(novoId)
        assertEquals(2, itens.size)
        assertTrue(itens.none { it.comprado }) // cópia nasce toda desmarcada
        assertTrue(banco.precoDao().listarDaLista(novoId).isEmpty()) // preço vencido não viaja
    }

    @Test
    fun `duplicarLista numera as copias sem acumular sufixo`() = runBlocking {
        val id = novaLista("Compra do Mês")

        val nomes = mutableListOf<String>()
        repeat(3) {
            nomes += banco.listaDao().porId(repositorio.duplicarLista(id)!!)!!.nome
        }

        assertEquals(listOf("Compra do Mês (cópia)", "Compra do Mês (cópia 2)", "Compra do Mês (cópia 3)"), nomes)
    }

    @Test
    fun `duplicarLista entra com nome do usuario puro e colisao ganha sufixo`() = runBlocking {
        val id = novaLista("Semanal")

        val livre = repositorio.duplicarLista(id, novoNome = "Minha cópia")
        assertEquals("Minha cópia", banco.listaDao().porId(livre!!)!!.nome)

        val colidiu = repositorio.duplicarLista(id, novoNome = "  Minha cópia  ")
        assertEquals("Minha cópia (cópia)", banco.listaDao().porId(colidiu!!)!!.nome)
    }

    @Test
    fun `duplicarLista de id inexistente devolve null`() = runBlocking {
        assertNull(repositorio.duplicarLista(999L))
    }

    // --- total estimado -------------------------------------------------------------------

    @Test
    fun `totalEstimado soma o menor preco disponivel e positivo de cada item`() = runBlocking {
        val listaId = novaLista("Feira")
        val produtoId = novoProduto("Arroz")
        val i1 = itemDa(listaId, produtoId, quantidade = BigDecimal("2"))
        val i2 = itemDa(listaId, produtoId, quantidade = BigDecimal.ONE)
        val lojaA = loja("Mercado A")
        val lojaB = loja("Mercado B")
        val lojaC = loja("Mercado C")
        precoNo(i1, lojaA, 500) // vence: menor disponível do item 1
        precoNo(i1, lojaB, 300)
        precoNo(i2, lojaC, 0) // zero não conta
        precoNo(i2, lojaB, 800, disponivel = false) // indisponível não conta
        precoNo(i2, lojaA, 900)

        zeroCentavos(BigDecimal("15.00"), repositorio.totalEstimado(listaId)) // 2 x 3,00 + 9,00
    }

    @Test
    fun `totalEstimado devolve zero sem itens ou sem precos`() = runBlocking {
        val vazia = novaLista("Vazia")

        zeroCentavos(BigDecimal.ZERO, repositorio.totalEstimado(vazia))

        val semPrecos = novaLista("Sem preços")
        itemDa(semPrecos, novoProduto("Arroz"))
        zeroCentavos(BigDecimal.ZERO, repositorio.totalEstimado(semPrecos))
    }

    // --- fluxos observados ----------------------------------------------------------------

    @Test
    fun `resumos conta itens e comprados por lista e resumosFavoritas filtra`() = runBlocking {
        val listaA = novaLista("A")
        val listaB = novaLista("B")
        val produtoId = novoProduto("Arroz")
        itemDa(listaA, produtoId)
        val comprado = itemDa(listaA, produtoId)
        itemDa(listaB, produtoId)
        repositorio.marcarComprado(comprado, true)
        repositorio.favoritarLista(listaB, true)

        val resumos = repositorio.resumos.first().associateBy { it.lista.id }
        assertEquals(2, resumos.getValue(listaA).totalDeItens)
        assertEquals(1, resumos.getValue(listaA).itensComprados)
        assertEquals(1, resumos.getValue(listaB).totalDeItens)

        val favoritas = repositorio.resumosFavoritas.first()
        assertEquals(listOf(listaB), favoritas.map { it.lista.id })
    }
}

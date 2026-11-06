package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.time.LocalDateTime
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
 * `PrecoRepositorio`: o contrato da anotacao de precos, de ponta a ponta com
 * Room em memoria.
 *
 * O preco pertence ao par item + loja (regravar substitui, nunca duplica) e
 * alimenta o **historico por produto**, que sobrevive a apagar a lista — e so
 * entra no historico o preco disponivel e maior que zero. O aviso de preco
 * alto considera o minimo ja registrado em QUALQUER loja (documentado no
 * proprio repositorio: a boa oferta do concorrente e a referencia util),
 * capturado ANTES da gravacao para o registro nao se auto-referenciar — regras
 * que a documentacao do backup tambem depende e que ficam pinadas aqui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrecoRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var precos: PrecoRepositorio
    private lateinit var lixeira: LixeiraRepositorio
    private val momento0: LocalDateTime = LocalDateTime.of(2026, 11, 6, 10, 0)

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
        precos = PrecoRepositorio(
            precoDao = banco.precoDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            lixeira = lixeira,
        )
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- auxiliares -------------------------------------------------------------------

    private suspend fun novoItem(): ItemDaLista {
        val produtoId = banco.produtoDao().salvar(
            ProdutoEntity(
                nome = "Arroz",
                nomeNormalizado = TextoUtil.normalizar("Arroz"),
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
        val listaId = banco.listaDao().inserir(
            br.com.comprix.data.local.ListaEntity(
                nome = "Semanal", criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null,
            ),
        )
        val itemId = banco.itemDao().inserir(
            ItemEntity(
                listaId = listaId,
                produtoId = produtoId,
                quantidade = "1",
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
        return ItemDaLista(id = itemId, listaId = listaId, produtoId = produtoId)
    }

    // --- estabelecimentos ---------------------------------------------------------------

    @Test
    fun `garantirEstabelecimento cria com a cor do rodizio`() = runBlocking {
        val primeira = precos.garantirEstabelecimento("Mercado A")
        val segunda = precos.garantirEstabelecimento("Mercado B")

        assertEquals(DadosIniciais.CORES_DE_LOJA[0], primeira.corHex)
        assertEquals(DadosIniciais.CORES_DE_LOJA[1], segunda.corHex)
    }

    @Test
    fun `garantirEstabelecimento nome em branco vira o padrao sem duplicar`() = runBlocking {
        val primeira = precos.garantirEstabelecimento("   ")
        val segunda = precos.garantirEstabelecimento("")

        assertEquals(DadosIniciais.ESTABELECIMENTO_PADRAO, primeira.nome)
        assertEquals(primeira.id, segunda.id)
        assertEquals(1, banco.estabelecimentoDao().listarTodos().size)
    }

    @Test
    fun `garantirEstabelecimento devolve a existente sem duplicar`() = runBlocking {
        val primeira = precos.garantirEstabelecimento("Mercado A")
        val segunda = precos.garantirEstabelecimento("Mercado A")

        assertEquals(primeira.id, segunda.id)
        assertEquals(1, banco.estabelecimentoDao().listarTodos().size)
    }

    // --- registrarPreco -----------------------------------------------------------------

    @Test
    fun `registrarPreco substitui o preco anterior do mesmo item na mesma loja`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")

        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("12.00"), momento = momento0.plusMinutes(1))

        val linhas = banco.precoDao().listarDaLista(item.listaId)
        assertEquals(1, linhas.size)
        assertEquals(1200L, linhas.single().precoCentavos)
    }

    @Test
    fun `registrarPreco alimenta o historico do produto`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")

        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)

        val serie = precos.historicoDoProduto(item.produtoId)
        assertEquals(1, serie.size)
        assertEquals(0, serie.single().preco.compareTo(BigDecimal("10.00")))
        assertEquals(loja.id, serie.single().estabelecimentoId)
    }

    @Test
    fun `registrarPreco indisponivel nao alimenta o historico`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")

        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), disponivel = false, momento = momento0)

        assertTrue(precos.historicoDoProduto(item.produtoId).isEmpty())
        assertFalse(banco.precoDao().listarDaLista(item.listaId).single().disponivel)
    }

    @Test
    fun `registrarPreco zero nao alimenta o historico`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")

        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("0.00"), momento = momento0)

        assertTrue(precos.historicoDoProduto(item.produtoId).isEmpty())
    }

    // --- registrarPrecoComAviso ---------------------------------------------------------

    @Test
    fun `aviso primeiro registro nao avisa`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")

        assertNull(precos.registrarPrecoComAviso(item, item.produtoId, loja.id, BigDecimal("21.90"), momento = momento0))
    }

    @Test
    fun `aviso empate com o minimo nao avisa`() = runBlocking {
        val item = novoItem()
        val lojaA = precos.garantirEstabelecimento("Mercado A")
        val lojaB = precos.garantirEstabelecimento("Mercado B")
        precos.registrarPreco(item, item.produtoId, lojaA.id, BigDecimal("21.90"), momento = momento0)

        assertNull(precos.registrarPrecoComAviso(item, item.produtoId, lojaB.id, BigDecimal("21.90"), momento = momento0.plusMinutes(1)))
    }

    @Test
    fun `aviso considera o minimo de qualquer loja e cita percentual e valor`() = runBlocking {
        val item = novoItem()
        val lojaA = precos.garantirEstabelecimento("Mercado A")
        val lojaB = precos.garantirEstabelecimento("Mercado B")
        precos.registrarPreco(item, item.produtoId, lojaA.id, BigDecimal("21.90"), momento = momento0)

        val aviso = precos.registrarPrecoComAviso(item, item.produtoId, lojaB.id, BigDecimal("27.38"), momento = momento0.plusMinutes(1))

        assertEquals(
            "Atenção: este preço está 25% acima do menor registrado para este produto (R$ 21,90).",
            aviso,
        )
    }

    @Test
    fun `aviso preco menor que o minimo nao avisa`() = runBlocking {
        val item = novoItem()
        val lojaA = precos.garantirEstabelecimento("Mercado A")
        val lojaB = precos.garantirEstabelecimento("Mercado B")
        precos.registrarPreco(item, item.produtoId, lojaA.id, BigDecimal("21.90"), momento = momento0)

        assertNull(precos.registrarPrecoComAviso(item, item.produtoId, lojaB.id, BigDecimal("19.90"), momento = momento0.plusMinutes(1)))
    }

    @Test
    fun `aviso indisponivel nao avisa e nao alimenta o historico`() = runBlocking {
        val item = novoItem()
        val lojaA = precos.garantirEstabelecimento("Mercado A")
        val lojaB = precos.garantirEstabelecimento("Mercado B")
        precos.registrarPreco(item, item.produtoId, lojaA.id, BigDecimal("21.90"), momento = momento0)

        assertNull(
            precos.registrarPrecoComAviso(
                item, item.produtoId, lojaB.id, BigDecimal("0.00"), disponivel = false,
                momento = momento0.plusMinutes(1),
            ),
        )
        assertEquals(1, precos.historicoDoProduto(item.produtoId).size)
    }

    // --- marcarIndisponivel e remocao -----------------------------------------------------

    @Test
    fun `marcarIndisponivel preserva o preco anterior e marca a flag`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)

        precos.marcarIndisponivel(item.id, loja.id)

        val linha = banco.precoDao().doItemNaLoja(item.id, loja.id)!!
        assertFalse(linha.disponivel)
        assertEquals(1000L, linha.precoCentavos)
    }

    @Test
    fun `removerEstabelecimento com lixeira manda para a lixeira sem os precos`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)

        precos.removerEstabelecimento(loja.id)

        assertNull(banco.estabelecimentoDao().porId(loja.id))
        assertTrue(banco.precoDao().listarDaLista(item.listaId).isEmpty())
        assertEquals(1, lixeira.contar())
    }

    @Test
    fun `removerEstabelecimento sem lixeira apaga direto`() = runBlocking {
        val item = novoItem()
        val direto = PrecoRepositorio(
            precoDao = banco.precoDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
        )
        val loja = direto.garantirEstabelecimento("Mercado A")
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)

        direto.removerEstabelecimento(loja.id)

        assertNull(banco.estabelecimentoDao().porId(loja.id))
        assertTrue(banco.precoDao().listarDaLista(item.listaId).isEmpty())
    }

    // --- consultas ----------------------------------------------------------------------

    @Test
    fun `menorPrecoRecente devolve o menor preco em reais`() = runBlocking {
        val item = novoItem()
        val lojaA = precos.garantirEstabelecimento("Mercado A")
        val lojaB = precos.garantirEstabelecimento("Mercado B")
        precos.registrarPreco(item, item.produtoId, lojaA.id, BigDecimal("25.00"), momento = momento0)
        precos.registrarPreco(item, item.produtoId, lojaB.id, BigDecimal("21.90"), momento = momento0.plusMinutes(1))

        val menor = precos.menorPrecoRecente(item.produtoId)

        assertNotNull(menor)
        assertEquals(0, menor!!.compareTo(BigDecimal("21.90")))
    }

    @Test
    fun `historicoDoProduto vem do mais recente para o mais antigo`() = runBlocking {
        val item = novoItem()
        val loja = precos.garantirEstabelecimento("Mercado A")
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("10.00"), momento = momento0)
        precos.registrarPreco(item, item.produtoId, loja.id, BigDecimal("12.00"), momento = momento0.plusHours(1))

        val serie = precos.historicoDoProduto(item.produtoId)

        assertEquals(2, serie.size)
        assertEquals(0, serie.first().preco.compareTo(BigDecimal("12.00")))
        assertEquals(0, serie.last().preco.compareTo(BigDecimal("10.00")))
    }
}

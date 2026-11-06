package br.com.comprix.presentation.produto

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.HistoricoPrecoEntity
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.catalogo.CatalogoSemente
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * `ProdutoViewModel`: o estado da ficha do produto (produto + categoria,
 * histórico de preços, menor preço recente, favorito, sinônimos do acervo e
 * alertas do perfil), com os repositórios REAIS sobre Room em memória.
 *
 * Contrato pinado aqui: a ficha de id inexistente carrega vazia sem travar;
 * o histórico vem ordenado do mais ANTIGO para o mais recente (a tela pede
 * `sortedBy quando` sobre a série do repositório, que é decrescente); o
 * menor preço recente sai em reais; `atualizar` recarrega depois de anotar
 * preço em outra tela; `alternarFavorito` é otimista e termina persistido; e
 * os sinônimos do acervo ("também buscar: ...") saem da semente mínima.
 *
 * Receita de infraestrutura: a mesma dos outros ViewModels — `Main` vira
 * `Dispatchers.Unconfined` e os efeitos assíncronos esperam com [espera].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProdutoViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var catalogo: CatalogoRepositorio
    private lateinit var precos: PrecoRepositorio
    private lateinit var configuracoes: ConfiguracoesRepositorio

    /** Semente mínima: Tangerina responde por mexerica e bergamota. */
    private val semente = CatalogoSemente(
        """
        SETOR|1|Mercado|#1E8E5A
        SUB|1|Hortifruti|1
        ITEM|Tangerina|1|mexerica,bergamota
        """.trimIndent(),
    )

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        val lixeira = LixeiraRepositorio(
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
        catalogo = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
            catalogoSemente = semente,
            lixeira = lixeira,
        )
        precos = PrecoRepositorio(
            precoDao = banco.precoDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            lixeira = lixeira,
        )
        configuracoes = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
            lixeira = lixeira,
        )
    }

    @After
    fun fechar() {
        Dispatchers.resetMain()
        banco.close()
    }

    /** Espera (com prazo real de 5 s) até a condição do efeito assíncrono valer. */
    private suspend fun espera(condicao: suspend () -> Boolean) {
        val prazo = System.currentTimeMillis() + 5_000
        while (!condicao()) {
            if (System.currentTimeMillis() > prazo) throw AssertionError("condição não atingida em 5 s")
            delay(10)
        }
    }

    private suspend fun produtoTangerina(): Long = banco.produtoDao().salvar(
        br.com.comprix.data.local.Mapeadores.paraEntidade(
            Produto(
                nome = "Tangerina",
                nomeNormalizado = "tangerina",
                categoriaId = banco.categoriaDao().listarTodas().first().id,
            ),
        ),
    )

    private fun teste(bloco: suspend (ProdutoViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val produtoId = produtoTangerina()
            val viewModel = ProdutoViewModel(produtoId, catalogo, precos, configuracoes)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }
            bloco(viewModel)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- carregamento ----------------------------------------------------------------------

    @Test
    fun `ficha carrega produto, categoria e sem alertas por padrao`() = teste { viewModel ->
        val estado = viewModel.estado.value
        assertEquals("Tangerina", estado.produto!!.nome)
        assertNotNull(estado.categoria)
        assertFalse(estado.favorito)
        assertFalse(estado.perfilAtivo)
        assertTrue(estado.alertasDeRestricao.isEmpty())
        assertEquals(listOf("mexerica", "bergamota"), estado.sinonimos) // do acervo
        assertNull(estado.menorPrecoRecente) // sem histórico ainda
        assertTrue(estado.historico.isEmpty())
    }

    @Test
    fun `ficha de id inexistente carrega vazia sem travar`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val viewModel = ProdutoViewModel(999L, catalogo, precos, configuracoes)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }

            val estado = viewModel.estado.value
            assertNull(estado.produto)
            assertNull(estado.categoria)
            assertTrue(estado.historico.isEmpty())
            assertTrue(estado.alertasDeRestricao.isEmpty())
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- historico e menor preco ------------------------------------------------------------

    @Test
    fun `historico vem do mais antigo para o mais recente e menorPreco em reais`() = teste { viewModel ->
        val produtoId = viewModel.estado.value.produto!!.id
        val loja = banco.estabelecimentoDao().inserir(br.com.comprix.data.local.EstabelecimentoEntity(nome = "Mercado A", corHex = "#1E8E5A"))
        val agora = System.currentTimeMillis()
        banco.precoDao().registrarHistorico(
            HistoricoPrecoEntity(
                produtoId = produtoId, estabelecimentoId = loja, precoCentavos = 500,
                quantidadeBase = "1 un", registradoEm = agora - 60_000,
            ),
        )
        banco.precoDao().registrarHistorico(
            HistoricoPrecoEntity(
                produtoId = produtoId, estabelecimentoId = loja, precoCentavos = 300,
                quantidadeBase = "1 un", registradoEm = agora,
            ),
        )

        viewModel.atualizar()
        espera { viewModel.estado.value.historico.size == 2 }

        val pontos = viewModel.estado.value.historico
        assertEquals(0, BigDecimal("5.00").compareTo(pontos.first().preco)) // mais ANTIGO primeiro
        assertEquals(0, BigDecimal("3.00").compareTo(pontos.last().preco))
        assertEquals(0, BigDecimal("3.00").compareTo(viewModel.estado.value.menorPrecoRecente!!))
    }

    // --- favorito -----------------------------------------------------------------------------

    @Test
    fun `alternarFavorito e otimista e termina persistido`() = teste { viewModel ->
        val produtoId = viewModel.estado.value.produto!!.id

        viewModel.alternarFavorito()
        espera { viewModel.estado.value.favorito } // otimista
        espera { banco.produtoDao().porId(produtoId)!!.favorito } // persistido

        viewModel.alternarFavorito()
        espera { !viewModel.estado.value.favorito }
        espera { !banco.produtoDao().porId(produtoId)!!.favorito }
    }

    // --- atualizar ----------------------------------------------------------------------------

    @Test
    fun `atualizar recarrega a ficha do banco`() = teste { viewModel ->
        val produtoId = viewModel.estado.value.produto!!.id

        banco.produtoDao().salvar(
            banco.produtoDao().porId(produtoId)!!.copy(nome = "Tangerina Hokkaido"),
        )

        viewModel.atualizar()
        espera { viewModel.estado.value.produto!!.nome == "Tangerina Hokkaido" }
    }

    @Test
    fun `categoria carregada e a do produto`() = teste { viewModel ->
        val esperada = banco.categoriaDao().listarTodas().single {
            it.id == viewModel.estado.value.produto!!.categoriaId
        }
        assertEquals(esperada.id, viewModel.estado.value.categoria!!.id)
    }
}

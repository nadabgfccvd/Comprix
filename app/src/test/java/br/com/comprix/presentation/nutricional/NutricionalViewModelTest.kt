package br.com.comprix.presentation.nutricional

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `NutricionalViewModel`: o estado da comparação nutricional da lista
 * (candidatos comparáveis, seleção com teto de 3 colunas, nutrientes
 * escolhidos persistidos e modo técnico), com os repositórios REAIS sobre
 * Room em memória — nenhum fake.
 *
 * Contrato pinado aqui: só produtos com tabela nutricional viram candidatos;
 **sem seleção, os primeiros 3 comparáveis entram por padrão** (teto
 * `MAXIMO_PRODUTOS_COMPARADOS`); a comparação só nasce com 2+ escolhidos;
 * selecionar um 4º empurra o primeiro para fora (drop(1) + novo); ligar/
 * desligar nutrientes grava nas configurações e **zerar volta à seleção
 * padrão**; e o modo técnico reflete a configuração.
 *
 * Receita de infraestrutura: a mesma dos outros ViewModels — `Main` vira
 * `Dispatchers.Unconfined` e os efeitos assíncronos esperam com [espera].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NutricionalViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var listas: ListaRepositorio
    private lateinit var configuracoes: ConfiguracoesRepositorio
    private var listaId: Long = 0L

    @Before
    fun preparar() = runBlocking {
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
        listas = ListaRepositorio(
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            precoDao = banco.precoDao(),
            lixeira = lixeira,
        )
        configuracoes = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
            lixeira = lixeira,
        )
        listaId = banco.listaDao().inserir(
            br.com.comprix.data.local.ListaEntity(
                nome = "L", criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null,
            ),
        )
    }

    @After
    fun fechar() {
        // Robolectric 4.17 drena a fila do Main no teardown do teste: um refresh de
        // invalidação do Room que sobe na fila executa DEPOIS do banco.close() e a
        // exceção morde o TestMainDispatcher — explodindo no PRÓXIMO teste
        // (IllegalStateException no runBlocking do helper, visto no CI de 2026-11-07).
        // Drenar ANTES do close (banco aberto = inofensivo) elimina o veneno na fonte.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
        banco.close()
    }

    /** Espera (com prazo real de 5 s) até a condição do efeito assíncrono valer. */
    private suspend fun espera(condicao: suspend () -> Boolean) {
        val prazo = System.currentTimeMillis() + 5_000
        while (!condicao()) {
            if (System.currentTimeMillis() > prazo) throw AssertionError("condição não atingida em 5 s")
            // O Room 2.8.x posta o refresh de invalidação no Handler principal; com o looper
            // do Robolectric pausado, os runnables só executam se drenados aqui.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(10)
        }
    }

    private suspend fun produtoComTabela(nome: String): Long {
        // Produto com tabela nutricional não vazia (porção + 1 valor).
        val produto = banco.produtoDao().salvar(
            br.com.comprix.data.local.Mapeadores.paraEntidade(
                Produto(
                    nome = nome,
                    nomeNormalizado = br.com.comprix.util.TextoUtil.normalizar(nome),
                    categoriaId = banco.categoriaDao().listarTodas().first().id,
                    infoNutricional = InfoNutricional(
                        porcaoDescricao = "100 g",
                        valores = mapOf(
                            Nutriente.VALOR_ENERGETICO to BigDecimal("100"),
                            Nutriente.PROTEINAS to BigDecimal("5"),
                        ),
                    ),
                ),
            ),
        )
        listas.adicionarItem(br.com.comprix.domain.modelo.ItemDaLista(listaId = listaId, produtoId = produto))
        return produto
    }

    private suspend fun produtoSemTabela(nome: String) {
        val produto = banco.produtoDao().salvar(
            br.com.comprix.data.local.Mapeadores.paraEntidade(
                Produto(
                    nome = nome,
                    nomeNormalizado = br.com.comprix.util.TextoUtil.normalizar(nome),
                    categoriaId = banco.categoriaDao().listarTodas().first().id,
                ),
            ),
        )
        listas.adicionarItem(br.com.comprix.domain.modelo.ItemDaLista(listaId = listaId, produtoId = produto))
    }

    private fun teste(bloco: suspend (NutricionalViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(
            // event loop do runBlocking: single-thread e FIFO — evita retomadas
            // Unconfined inline em threads do executor do Room (corrida da suíte)
            coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        )
        try {
            val viewModel = NutricionalViewModel(listaId, listas, configuracoes)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.candidatos.isEmpty() || viewModel.estado.value.comparacao == null && viewModel.estado.value.candidatos.isEmpty() }
            // estado assentou quando os itens da lista já atravessaram o combine
            bloco(viewModel)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- candidatos e comparacao -----------------------------------------------------------

    @Test
    fun `sem itens a comparacao fica vazia`() = teste { viewModel ->
        assertTrue(viewModel.estado.value.candidatos.isEmpty())
        assertNull(viewModel.estado.value.comparacao)
        assertEquals(Nutriente.selecaoPadrao(), viewModel.estado.value.nutrientesEscolhidos)
        assertFalse(viewModel.estado.value.modoTecnicoAtivo)
    }

    @Test
    fun `so produtos com tabela viram candidatos e a comparacao nasce com 2`() = teste { viewModel ->
        produtoComTabela("Iogurte A")
        produtoComTabela("Iogurte B")
        produtoSemTabela("Sal")
        // Condição TOTAL: cobre o que os asserts abaixo leem (nomes, seleção, comparação).
        espera {
            val estado = viewModel.estado.value
            estado.candidatos.size == 2 &&
                estado.candidatos.map { it.nome }.toSet() == setOf("Iogurte A", "Iogurte B") &&
                estado.selecionados.size == 2 &&
                estado.comparacao != null
        }

        val estado = viewModel.estado.value
        assertEquals(setOf("Iogurte A", "Iogurte B"), estado.candidatos.map { it.nome }.toSet())
        assertEquals(2, estado.selecionados.size) // padrão: os primeiros 3 (tem só 2)
        assertTrue(estado.comparacao != null)
    }

    @Test
    fun `selecionar o quarto produto empurra o primeiro para fora`() = teste { viewModel ->
        val ids = listOf("Iogurte A", "Iogurte B", "Iogurte C").map { produtoComTabela(it) }
        espera { viewModel.estado.value.candidatos.size == 3 }

        val quarto = produtoComTabela("Iogurte D")
        viewModel.alternarProduto(quarto)
        espera { viewModel.estado.value.selecionados == setOf(ids[1], ids[2], quarto) }
    }

    @Test
    fun `desmarcar abaixo de dois encerra a comparacao`() = teste { viewModel ->
        val a = produtoComTabela("Iogurte A")
        produtoComTabela("Iogurte B")
        espera { viewModel.estado.value.candidatos.size == 2 }

        viewModel.alternarProduto(a) // desmarca o primeiro
        // Condição TOTAL: comparacao nula e seleção encolhida chegam em fluxos distintos.
        espera {
            viewModel.estado.value.comparacao == null &&
                viewModel.estado.value.selecionados.size == 1
        }
        assertEquals(1, viewModel.estado.value.selecionados.size)
    }

    // --- nutrientes -------------------------------------------------------------------------

    @Test
    fun `alternarNutriente grava na configuracao e zero volta ao padrao`() = teste { viewModel ->
        viewModel.alternarNutriente(Nutriente.FERRO)
        espera { viewModel.estado.value.nutrientesEscolhidos.contains(Nutriente.FERRO) }
        assertEquals(
            Nutriente.selecaoPadrao() + Nutriente.FERRO,
            configuracoes.configuracoes.first().nutrientesComparados,
        )

        viewModel.alternarNutriente(Nutriente.FERRO)
        espera { !viewModel.estado.value.nutrientesEscolhidos.contains(Nutriente.FERRO) }

        // Desligar TODOS os nutrientes volta para a seleção de fábrica.
        Nutriente.entries.forEach { if (it != Nutriente.FERRO) viewModel.alternarNutriente(it) }
        espera { viewModel.estado.value.nutrientesEscolhidos == Nutriente.selecaoPadrao() }
    }

    @Test
    fun `modo tecnico reflete a configuracao`() = teste { viewModel ->
        configuracoes.definirModoTecnico(true)
        espera { viewModel.estado.value.modoTecnicoAtivo }
    }
}

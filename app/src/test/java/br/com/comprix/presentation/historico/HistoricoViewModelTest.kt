package br.com.comprix.presentation.historico

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.HistoricoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.modelo.PontoGrafico
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
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
 * `HistoricoViewModel`: o estado da tela de histórico (cabeçalho por período,
 * dica, busca + pastilha de loja e torradas), com os repositórios REAIS sobre
 * Room em memória — nenhum fake.
 *
 * Contrato pinado aqui: o PERÍODO afina o resumo do cabeçalho mas **nunca
 * reescreve a lista** (`compras` segue inteira — busca e loja afinam a lista,
 * nunca a estatística); a dica aparece com compras e some com
 * `dispensarDica`; a meta de economia vira reais; remover manda a compra para
 * a lixeira com a torrada congelada; e `limparFiltros` é a saída do "Nada
 * encontrado".
 *
 * Receita de infraestrutura: a mesma do `ListasViewModelTest` — `Main` vira
 * `Dispatchers.Unconfined` e os efeitos assíncronos esperam com [espera]
 * (`delay`, nunca `Thread.sleep`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HistoricoViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var historico: HistoricoRepositorio
    private lateinit var configuracoes: ConfiguracoesRepositorio

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
        historico = HistoricoRepositorio(compraDao = banco.compraDao(), lixeira = lixeira)
        configuracoes = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
            lixeira = lixeira,
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

    private fun resultado(totalPago: String, descricao: String) = CalculadoraDeCompra.ResultadoDaCompra(
        totalPago = BigDecimal(totalPago),
        economia = BigDecimal("2.00"),
        economiaPercentual = BigDecimal("5.0"),
        quantidadeItens = 9,
        itensSemPreco = 0,
        totalSeComprasseNoMaisCaro = BigDecimal("50.00"),
        gastosPorCategoria = listOf(PontoGrafico("Feira", BigDecimal(totalPago))),
        descricaoEstabelecimento = descricao,
        estabelecimentoPrincipalId = null,
    )

    private suspend fun registrarCompra(totalPago: String, descricao: String, momento: LocalDateTime) =
        historico.registrar(null, "Lista", resultado(totalPago, descricao), momento)

    private fun teste(bloco: suspend (HistoricoViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(
            // event loop do runBlocking: single-thread e FIFO — evita retomadas
            // Unconfined inline em threads do executor do Room (corrida da suíte)
            coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        )
        try {
            val catalogo = CatalogoRepositorio(
                categoriaDao = banco.categoriaDao(),
                produtoDao = banco.produtoDao(),
                memoriaDao = banco.memoriaCategoriaDao(),
                decisaoDoParserDao = banco.decisaoDoParserDao(),
                catalogoSemente = null,
                lixeira = null,
            )
            val viewModel = HistoricoViewModel(historico, configuracoes, catalogo)
            val coletores = listOf(
                launch { viewModel.estado.collect {} },
                launch { viewModel.comprasVisiveis.collect {} },
                launch { viewModel.lojasDisponiveis.collect {} },
            )
            espera { !viewModel.estado.value.carregando }
            bloco(viewModel)
            coletores.forEach { it.cancel() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- estado -------------------------------------------------------------------------

    @Test
    fun `sem compras o estado fica vazio e sem dica`() = teste { viewModel ->
        assertTrue(viewModel.estado.value.vazio)
        assertFalse(viewModel.estado.value.carregando)
        assertFalse(viewModel.estado.value.mostrarDica)
    }

    @Test
    fun `dica, economia do mes e meta aparecem no estado`() = teste { viewModel ->
        val agora = LocalDateTime.now()
        registrarCompra("100.00", "Mercado do Bairro", agora)
        registrarCompra("40.00", "Mercearia da esquina", agora.minusYears(1))
        configuracoes.definirMetaEconomia(5_000L) // R$ 50,00
        // Condição TOTAL: mostrarDica pode atrasar em relação à lista de compras.
        espera { viewModel.estado.value.compras.size == 2 && viewModel.estado.value.mostrarDica }

        val estado = viewModel.estado.value
        assertTrue(estado.mostrarDica)
        assertEquals(0, BigDecimal("2.00").compareTo(estado.economiaDoMes)) // só a compra do mês
        // Condição TOTAL: meta não-nula ainda pode ser o valor intermediário.
        espera {
            val meta = viewModel.estado.value.metaEconomia
            meta != null && BigDecimal("50.00").compareTo(meta) == 0
        }
        assertEquals(0, BigDecimal("50.00").compareTo(viewModel.estado.value.metaEconomia!!))
        assertFalse(estado.vazio)
    }

    @Test
    fun `o periodo afina o resumo mas a lista segue inteira`() = teste { viewModel ->
        val agora = LocalDateTime.now()
        registrarCompra("100.00", "Mercado do Bairro", agora)
        registrarCompra("40.00", "Mercearia da esquina", agora.minusYears(1))
        // Lista e resumo são correntes separadas: espera os DOIS assentarem
        // (com o Main no event loop do runBlocking, eles chegam em ticks distintos).
        espera {
            viewModel.estado.value.compras.size == 2 &&
                viewModel.estado.value.resumo.quantidadeCompras == 1
        }

        // Padrão SEIS_MESES: o resumo do cabeçalho vê só a compra do mês atual.
        assertEquals(1, viewModel.estado.value.resumo.quantidadeCompras)
        assertEquals(2, viewModel.estado.value.compras.size) // a lista NÃO é reescrita

        viewModel.definirPeriodo(AnalisadorDeHistorico.Periodo.TUDO)
        // Condição TOTAL: resumo e lista são fluxos distintos — espera cobre os dois.
        espera {
            viewModel.estado.value.resumo.quantidadeCompras == 2 &&
                viewModel.estado.value.compras.size == 2
        }
        assertEquals(2, viewModel.estado.value.compras.size)
    }

    @Test
    fun `dispensarDica faz o selo desaparecer`() = teste { viewModel ->
        registrarCompra("100.00", "Mercado do Bairro", LocalDateTime.now())
        espera { viewModel.estado.value.mostrarDica }

        viewModel.dispensarDica()
        espera { !viewModel.estado.value.mostrarDica }
    }

    // --- filtros -------------------------------------------------------------------------

    @Test
    fun `busca sem acento e pastilha de loja afinam a lista e limparFiltros restaura`() = teste { viewModel ->
        val mercado = registrarCompra("100.00", "Mercado do Bairro", LocalDateTime.now())
        registrarCompra("40.00", "Mercearia da esquina", LocalDateTime.now())
        espera { viewModel.estado.value.compras.size == 2 }

        viewModel.definirConsulta("MERCADO b")
        espera { viewModel.comprasVisiveis.value.map { it.id } == listOf(mercado) }

        viewModel.limparFiltros()
        espera { viewModel.comprasVisiveis.value.size == 2 }

        viewModel.definirLoja("Mercearia da esquina")
        // guarda de tamanho: o tick anterior (2 compras) não pode derrubar o predicado
        espera {
            val visiveis = viewModel.comprasVisiveis.value
            visiveis.size == 1 &&
                visiveis.single().descricaoEstabelecimento == "Mercearia da esquina"
        }

        viewModel.definirLoja(null)
        espera { viewModel.comprasVisiveis.value.size == 2 }
    }

    @Test
    fun `lojasDisponiveis lista rotulos distintos com contagem`() = teste { viewModel ->
        registrarCompra("100.00", "Mercado do Bairro", LocalDateTime.now())
        registrarCompra("40.00", "Mercado do Bairro", LocalDateTime.now())
        registrarCompra("40.00", "Mercearia da esquina", LocalDateTime.now())
        espera { viewModel.estado.value.compras.size == 3 }

        // lojasDisponiveis é corrente própria: o snapshot congelado fora da espera
        // enxergaria estado velho (ou vazio) — lê DENTRO da condição, os dois juntos
        espera {
            val lojas = viewModel.lojasDisponiveis.value.associate { it.rotulo to it.quantidade }
            lojas["Mercado do Bairro"] == 2 && lojas["Mercearia da esquina"] == 1
        }
    }

    // --- acoes e torradas -------------------------------------------------------------------

    @Test
    fun `remover manda a compra para a lixeira com a torrada congelada`() = teste { viewModel ->
        val id = registrarCompra("100.00", "Mercado do Bairro", LocalDateTime.now())
        espera { viewModel.estado.value.compras.size == 1 }
        val compra = viewModel.estado.value.compras.single()

        viewModel.remover(compra)
        espera { viewModel.mensagem.value == "Compra removida do histórico — foi para a lixeira (30 dias)." }
        espera { viewModel.estado.value.compras.isEmpty() }
        espera { banco.lixeiraDao().porId(id) != null }
    }

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel ->
        val compraId = registrarCompra("100.00", "Mercado do Bairro", LocalDateTime.now())
        espera { viewModel.estado.value.compras.size == 1 }

        viewModel.remover(viewModel.estado.value.compras.single())
        espera { viewModel.mensagem.value != null }

        viewModel.mensagemExibida()
        assertNull(viewModel.mensagem.value)
    }
}

package br.com.comprix.presentation.listas

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.util.Formatadores
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
 * `ListasViewModel`: o estado da tela inicial (seções Favoritas/Abertas/
 * Finalizadas, dica, seleção em massa e mensagens de torrada), com os
 * repositórios REAIS sobre Room em memória — nenhum fake.
 *
 * O contrato da tela inicial que fica pinado aqui: a seção Favoritas é
 * disjunta das outras (a favorita sobe e não repete); a dica só aparece
 * quando há listas E ainda não foi vista; renomear em branco é ignorado;
 * excluir em massa manda tudo para a lixeira com mensagem no singular/plural;
 * finalizar em massa pula as já finalizadas sem registrar a compra duas
 * vezes; e favoritar em massa decide pelo voto da maioria e sempre avança
 * o estado do grupo.
 *
 * Detalhe de infraestrutura (lição desta bateria): o Room emite e retoma
 * corrotinas em executores REAIS de IO, que o relógio virtual do
 * `runTest`/`advanceUntilIdle` não controla — a combinação é de corrida. E o
 * `UnconfinedTestDispatcher` reagendaria as retomadas para o scheduler de
 * teste, que fora do `runTest` nunca é bombeado. Aqui o `Main` do
 * `viewModelScope` vira `Dispatchers.Unconfined` de verdade (o `launch`
 * executa inline e as retomadas acontecem na própria thread do Room) e os
 * efeitos assíncronos são esperados com [espera] — polling de condição com
 * prazo real de 5 s que usa `delay` (NUNCA `Thread.sleep`, que bloquearia o
 * event-loop do `runBlocking` e mataria de fome o coletor do estado).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ListasViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var repositorio: ListaRepositorio
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
        repositorio = ListaRepositorio(
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
    }

    @After
    fun fechar() {
        // corrida demonstrada (corrida pós-reset 2026-11-06): o Room posta o refresh
        // de invalidação no Main a partir do executor dele — um dispatch em voo morda
        // o NonConcurrentlyModifiable do TestMainDispatcher. O Robolectric 4.17 (#79)
        // agravou a janela: ele drena a fila do Main no teardown, e um refresh que
        // sobe nela executa DEPOIS do banco.close(), explodindo no PRÓXIMO teste
        // (IllegalStateException no runBlocking do helper — CI 2026-11-07). Dreno
        // ANTES do close (banco aberto = inofensivo) elimina o veneno na fonte; o
        // retry limitado tolera dispatch em voo remanescente sem mascarar outra
        // falha (só IllegalStateException é tolerada).
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        var tentativa = 0
        while (true) {
            try {
                Dispatchers.resetMain()
                break
            } catch (e: IllegalStateException) {
                if (++tentativa >= 5) throw e
                Thread.sleep(25)
            }
        }
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
            delay(10) // suspende (cede o event-loop); Thread.sleep bloquearia
        }
    }

    /**
     * Monta o ViewModel com o Main trocado por um dispatcher não confinado
     * (launches inline) e o `estado` já subscrito — o `WhileSubscribed` só
     * sobe com observador vivo. Entrega o ViewModel pronto para o bloco.
     */
    private fun teste(bloco: suspend (ListasViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(
            // event loop do runBlocking: single-thread e FIFO — evita retomadas
            // Unconfined inline em threads do executor do Room (corrida da suíte)
            coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        )
        try {
            val viewModel = ListasViewModel(repositorio, configuracoes)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando } // estado inicial assentou
            bloco(viewModel)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun novaLista(nome: String, favorita: Boolean = false): Long {
        // favorita vai no INSERT (um write só): gravar false e depois favoritar
        // abria uma janela em que o fluxo de resumos emitia C em "abertas" — e a
        // decisão de maioria do VM via C como não favorita (msg "3 listas...").
        return banco.listaDao().inserir(
            ListaEntity(
                nome = nome, criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null,
                favorita = favorita,
            ),
        )
    }

    // --- estado -------------------------------------------------------------------------

    @Test
    fun `sem listas o estado fica vazio e sem dica`() = teste { viewModel ->
        assertTrue(viewModel.estado.value.vazio)
        assertFalse(viewModel.estado.value.carregando)
        assertFalse(viewModel.estado.value.mostrarDica)
    }

    @Test
    fun `secoes sao disjuntas e a favorita sobe sem repetir`() = teste { viewModel ->
        val favorita = novaLista("Favorita", favorita = true)
        val aberta = novaLista("Aberta")
        val finalizada = novaLista("Encerrada")
        repositorio.marcarFinalizada(finalizada)
        espera { viewModel.estado.value.finalizadas.map { it.lista.id } == listOf(finalizada) }

        val estado = viewModel.estado.value
        assertEquals(listOf(favorita), estado.favoritas.map { it.lista.id })
        assertEquals(listOf(aberta), estado.abertas.map { it.lista.id })
        assertFalse(estado.vazio)
        assertTrue(estado.mostrarDica) // há listas e a dica ainda não foi vista
    }

    @Test
    fun `dispensarDica faz o selo desaparecer do estado`() = teste { viewModel ->
        novaLista("Aberta")
        espera { viewModel.estado.value.mostrarDica }

        viewModel.dispensarDica()
        espera { !viewModel.estado.value.mostrarDica }
    }

    // --- criar e renomear -----------------------------------------------------------------

    @Test
    fun `criarLista com nome em branco usa o sugerido do mes e devolve o id`() = teste { viewModel ->
        var capturado = 0L

        viewModel.criarLista("   ") { capturado = it }
        espera { capturado > 0 }

        assertEquals(Formatadores.nomeSugeridoDeLista(), banco.listaDao().porId(capturado)!!.nome)
    }

    @Test
    fun `criarLista usa o nome informado`() = teste { viewModel ->
        var capturado = 0L

        viewModel.criarLista("Feira") { capturado = it }
        espera { capturado > 0 }

        assertEquals("Feira", banco.listaDao().porId(capturado)!!.nome)
    }

    @Test
    fun `renomear grava o aparado e ignora nome em branco`() = teste { viewModel ->
        val id = novaLista("Velha")
        espera { viewModel.estado.value.abertas.any { it.lista.id == id } }
        val resumo = viewModel.estado.value.abertas.single { it.lista.id == id }

        viewModel.renomear(resumo, "   ")
        delay(50) // renomear em branco nem lança coroutine: nada deve mudar
        assertEquals("Velha", banco.listaDao().porId(id)!!.nome)

        viewModel.renomear(resumo, "  Nova  ")
        espera { banco.listaDao().porId(id)!!.nome == "Nova" }
    }

    // --- mensagens de torrada --------------------------------------------------------------

    @Test
    fun `duplicar publica a mensagem e cria a copia`() = teste { viewModel ->
        val id = novaLista("Semanal")
        espera { viewModel.estado.value.abertas.any { it.lista.id == id } }
        val resumo = viewModel.estado.value.abertas.single { it.lista.id == id }

        viewModel.duplicar(resumo)
        espera { viewModel.mensagem.value == "Lista duplicada sem os preços antigos." }
        espera { banco.listaDao().contar() == 2 }
    }

    @Test
    fun `remover publica a mensagem e a lista vai para a lixeira`() = teste { viewModel ->
        val id = novaLista("Semanal")
        espera { viewModel.estado.value.abertas.any { it.lista.id == id } }
        val resumo = viewModel.estado.value.abertas.single { it.lista.id == id }

        viewModel.remover(resumo)
        espera { viewModel.mensagem.value == "Lista \"Semanal\" foi para a lixeira (30 dias)." }
        espera { banco.lixeiraDao().contar() == 1 }
    }

    @Test
    fun `alternarFavorita move a lista para o topo e publica a mensagem`() = teste { viewModel ->
        val id = novaLista("Semanal")
        espera { viewModel.estado.value.abertas.any { it.lista.id == id } }
        val resumo = viewModel.estado.value.abertas.single { it.lista.id == id }

        viewModel.alternarFavorita(resumo)
        espera { viewModel.mensagem.value == "Lista \"Semanal\" marcada como favorita." }
        espera { viewModel.estado.value.favoritas.map { it.lista.id } == listOf(id) }
        espera { viewModel.estado.value.abertas.isEmpty() }
    }

    @Test
    fun `reabrir publica a mensagem e a lista volta para abertas`() = teste { viewModel ->
        val id = novaLista("Encerrada")
        repositorio.marcarFinalizada(id)
        espera { viewModel.estado.value.finalizadas.map { it.lista.id } == listOf(id) }
        val resumo = viewModel.estado.value.finalizadas.single { it.lista.id == id }

        viewModel.reabrir(resumo)
        espera { viewModel.mensagem.value == "Lista reaberta para edição." }
        espera { viewModel.estado.value.abertas.map { it.lista.id } == listOf(id) }
    }

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel ->
        val id = novaLista("Semanal")
        espera { viewModel.estado.value.abertas.any { it.lista.id == id } }

        viewModel.remover(viewModel.estado.value.abertas.single { it.lista.id == id })
        espera { viewModel.mensagem.value != null }

        viewModel.mensagemExibida()
        assertNull(viewModel.mensagem.value)
    }

    // --- selecao em massa -------------------------------------------------------------------

    @Test
    fun `entrarEmSelecao marca a lista tocada e alternar a ultima sai do modo`() = teste { viewModel ->
        val id = novaLista("A")

        viewModel.entrarEmSelecao(id)
        assertTrue(viewModel.modoSelecao.value)
        assertEquals(setOf(id), viewModel.idsSelecionados.value)

        viewModel.alternarSelecao(id)
        assertFalse(viewModel.modoSelecao.value)
        assertTrue(viewModel.idsSelecionados.value.isEmpty())
    }

    @Test
    fun `alternarSelecao adiciona outra mantendo o modo`() = teste { viewModel ->
        val a = novaLista("A")
        val b = novaLista("B")

        viewModel.entrarEmSelecao(a)
        viewModel.alternarSelecao(b)

        assertTrue(viewModel.modoSelecao.value)
        assertEquals(setOf(a, b), viewModel.idsSelecionados.value)
    }

    @Test
    fun `selecionarTodas respeita as visiveis e ignora lista vazia`() = teste { viewModel ->
        val a = novaLista("A")

        viewModel.selecionarTodas(emptyList())
        assertFalse(viewModel.modoSelecao.value)

        viewModel.selecionarTodas(listOf(a))
        assertEquals(setOf(a), viewModel.idsSelecionados.value)
        assertTrue(viewModel.modoSelecao.value)
    }

    @Test
    fun `excluirSelecionadas manda todas para a lixeira com mensagem no plural`() = teste { viewModel ->
        val a = novaLista("A")
        val b = novaLista("B")
        viewModel.selecionarTodas(listOf(a, b))

        viewModel.excluirSelecionadas()
        espera { viewModel.mensagem.value == "2 listas foram para a lixeira (30 dias)." }
        espera { banco.lixeiraDao().contar() == 2 }
        espera { viewModel.idsSelecionados.value.isEmpty() }
        assertFalse(viewModel.modoSelecao.value)
    }

    @Test
    fun `excluirSelecionadas sem nada selecionado nao faz nada`() = teste { viewModel ->
        viewModel.excluirSelecionadas()
        delay(50)

        assertNull(viewModel.mensagem.value)
        assertEquals(0, banco.lixeiraDao().contar())
    }

    @Test
    fun `finalizarSelecionadas finaliza as abertas e pula as ja finalizadas`() = teste { viewModel ->
        val aberta = novaLista("Aberta")
        val encerrada = novaLista("Encerrada")
        repositorio.marcarFinalizada(encerrada)
        viewModel.selecionarTodas(listOf(aberta, encerrada))

        viewModel.finalizarSelecionadas()
        espera { viewModel.mensagem.value == "1 lista finalizada, 1 já estavam." }
        espera { banco.listaDao().listarTodas().all { it.finalizada } }
    }

    @Test
    fun `alternarFavoritas decide pelo voto da maioria e sempre avanca o grupo`() = teste { viewModel ->
        suspend fun depurar(rotulo: String, cond: suspend () -> Boolean) {
            try {
                espera { cond() }
            } catch (e: AssertionError) {
                val e0 = viewModel.estado.value
                System.err.println("!!TRAVOU EM: $rotulo")
                System.err.println("  fav=${e0.favoritas.map { it.lista.nome }} abertas=${e0.abertas.map { it.lista.nome }} fin=${e0.finalizadas.map { it.lista.nome }}")
                System.err.println("  msg=${viewModel.mensagem.value} total=${e0.favoritas.size + e0.abertas.size + e0.finalizadas.size}")
                throw e
            }
        }
        val a = novaLista("A")
        val b = novaLista("B")
        val c = novaLista("C", favorita = true)
        // o estado precisa refletir as tres listas (C fica na secao Favoritas)
        depurar("inicial-total-3") {
            viewModel.estado.value.let { it.favoritas.size + it.abertas.size + it.finalizadas.size } == 3
        }
        viewModel.selecionarTodas(listOf(a, b, c))

        viewModel.alternarFavoritas() // A e B nao sao favoritas: maioria -> favorita
        depurar("msg-2-favoritas") { viewModel.mensagem.value == "2 listas marcadas como favoritas." }
        depurar("dao-todas-favoritas") { banco.listaDao().listarTodas().all { it.favorita } }
        // o estado precisa refletir a fase 1 (as tres saem de abertas/finalizadas)
        depurar("abertas-finalizadas-vazias") { (viewModel.estado.value.abertas + viewModel.estado.value.finalizadas).isEmpty() }

        viewModel.selecionarTodas(listOf(a, b, c))
        viewModel.alternarFavoritas() // quirk real: a contagem olha so abertas/finalizadas (0),
        // mas o forEach aplica a decisao a TODAS as ids selecionadas.
        depurar("msg-0-tiradas") { viewModel.mensagem.value == "0 listas tiradas dos favoritos." }
        depurar("dao-nenhuma-favorita") { banco.listaDao().listarTodas().none { it.favorita } }
    }
}

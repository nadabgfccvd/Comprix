package br.com.comprix.presentation.lojas

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `LojasViewModel`: o estado da tela "Minhas lojas" (cartões de loja com
 * contagens de preços, adicionar/renomear/remover e torradas), com os
 * repositórios REAIS sobre Room em memória — nenhum fake.
 *
 * Contrato pinado aqui: a adição deduplica por nome normalizado (a torrada
 * muda de "adicionada" para "já está na sua rede" e a loja NÃO duplica);
 * renomear bloqueia colisão sem acento/caixa; remover manda a loja para a
 * lixeira; e as contagens do cartão seguem a semântica documentada —
 * `quantidadeDePrecos` soma todos os registros válidos (disponível e > 0) e
 * `itensDistintos`/`produtosDaLoja` deduplicam por NOME de produto (o mesmo
 * produto anotado em 2 listas conta 1 vez).
 *
 * Receita de infraestrutura: a mesma dos outros ViewModels — `Main` vira
 * `Dispatchers.Unconfined` e os efeitos assíncronos esperam com [espera].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LojasViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var precos: PrecoRepositorio
    private lateinit var listas: ListaRepositorio

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
        precos = PrecoRepositorio(
            precoDao = banco.precoDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            lixeira = lixeira,
        )
        listas = ListaRepositorio(
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            precoDao = banco.precoDao(),
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

    /**
     * Espera o CONTEUDO das lojas com re-tic do `atualizar()`: o tick do
     * pedido de re-leitura pode chegar antes de o fluxo de estabelecimentos
     * (Room, thread real) re-emitir, e o `combine` grava o estado velho —
     * em produção o próximo tick corrige; aqui o polling faz o papel.
     */
    private suspend fun esperaLojas(
        viewModel: LojasViewModel,
        condicao: suspend (List<LojasViewModel.LojaDaRede>) -> Boolean,
    ) {
        val prazo = System.currentTimeMillis() + 5_000
        while (!condicao(viewModel.estado.value.lojas)) {
            if (System.currentTimeMillis() > prazo) throw AssertionError("condição não atingida em 5 s")
            viewModel.atualizar()
            // O Room 2.8.x posta o refresh de invalidação no Handler principal; com o looper
            // do Robolectric pausado, os runnables só executam se drenados aqui.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(20)
        }
    }

    private fun teste(bloco: suspend (LojasViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(
            // event loop do runBlocking: single-thread e FIFO — evita retomadas
            // Unconfined inline em threads do executor do Room (corrida da suíte)
            coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        )
        try {
            val viewModel = LojasViewModel(precos, listas)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }
            bloco(viewModel)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Lista com um produto e um item, para anotar preços. */
    private suspend fun listaComItem(nomeProduto: String): Pair<Long, ItemDaLista> {
        val listaId = banco.listaDao().inserir(
            ListaEntity(nome = "L", criadaEm = 1_700_000_000_000L, finalizada = false, finalizadaEm = null),
        )
        val produto = catalogoDaLista().salvarProduto(Produto(nome = nomeProduto, nomeNormalizado = "", categoriaId = 1L))
        val item = ItemDaLista(listaId = listaId, produtoId = produto.id)
        val itemId = listas.adicionarItem(item)
        return listaId to listas.item(itemId)!!
    }

    private fun catalogoDaLista() = CatalogoRepositorio(
        categoriaDao = banco.categoriaDao(),
        produtoDao = banco.produtoDao(),
        memoriaDao = banco.memoriaCategoriaDao(),
        decisaoDoParserDao = banco.decisaoDoParserDao(),
        catalogoSemente = null,
        lixeira = null,
    )

    // --- adicionar ----------------------------------------------------------------------

    @Test
    fun `adicionar publica a mensagem e a loja entra no estado`() = teste { viewModel ->
        viewModel.adicionar("  Mercado A  ")
        espera { viewModel.estado.value.mensagem == "Loja \"Mercado A\" adicionada." }
        // guarda de tamanho: o estado anterior (0 ou 2 lojas) não pode derrubar o predicado
        esperaLojas(viewModel) { it.size == 1 && it.single().estabelecimento.nome == "Mercado A" }
    }

    @Test
    fun `adicionar duplicada com o mesmo nome deduplica`() = teste { viewModel ->
        viewModel.adicionar("Mercado A")
        espera { viewModel.estado.value.lojas.size == 1 }

        viewModel.adicionar("  Mercado A  ")
        espera { viewModel.estado.value.mensagem == "Loja \"Mercado A\" já está na sua rede." }
        espera { viewModel.estado.value.lojas.size == 1 } // nunca duplica
        // QUIRK/BUG conhecido (issue #86, pinado no teste seguinte): digitar
        // com caixa diferente ("mercado a") CRIA uma segunda loja — a checagem
        // do VM é normalizada mas o `garantirEstabelecimento` deduplica por
        // nome EXATO. Consertar é mudança de produção; aqui o comportamento de
        // hoje fica documentado.
    }

    @Test
    fun `adicionar variacao equivalente de nome cria segunda loja bug conhecido`() = teste { viewModel ->
        // Bug conhecido documentado na issue #86 (não corrigido aqui — mudança
        // de produção é decisão do mantenedor): a identidade da loja no app é
        // o nome NORMALIZADO (TextoUtil.normalizar — sem caixa, acento ou
        // pontuação), tanto na checagem do adicionar quanto do renomear; mas a
        // deduplicação real do garantirEstabelecimento é por nome EXATO
        // (Daos.kt: porNome — WHERE nome = :nome) e o índice UNIQUE do banco
        // (Entidades.kt:155) é sobre o texto CRU. Uma variação equivalente
        // ("mercado á" ≡ "Mercado A") passa pela checagem do VM, exibe a
        // mensagem "já está na sua rede" e AINDA ASSIM cria uma segunda loja.
        // Este teste PINA o comportamento de hoje; quando a identidade da loja
        // virar a chave normalizada (ou o índice UNIQUE cobrir a chave), é
        // aqui que o teste vira.
        viewModel.adicionar("Mercado A")
        espera { viewModel.estado.value.lojas.size == 1 }

        viewModel.adicionar("mercado á") // equivalente sem acento/caixa, cru é diferente
        espera { viewModel.estado.value.mensagem == "Loja \"mercado á\" já está na sua rede." }
        esperaLojas(viewModel) { it.size == 2 } // ...e a segunda loja FOI criada
        assertEquals(
            listOf("Mercado A", "mercado á"),
            banco.estabelecimentoDao().listarTodos().map { it.nome }.sorted(),
        )
    }

    @Test
    fun `adicionar em branco e ignorado`() = teste { viewModel ->
        viewModel.adicionar("   ")
        delay(50)
        assertTrue(viewModel.estado.value.lojas.isEmpty())
    }

    // --- renomear -----------------------------------------------------------------------

    @Test
    fun `renomear grava e colisao sem acento e bloqueada`() = teste { viewModel ->
        precos.garantirEstabelecimento("Mercado A")
        precos.garantirEstabelecimento("Mercado B")
        viewModel.atualizar()
        espera { viewModel.estado.value.lojas.size == 2 }

        val a = viewModel.estado.value.lojas.first { it.estabelecimento.nome == "Mercado A" }.estabelecimento
        viewModel.renomear(a, "  Mercado C  ")
        espera { viewModel.estado.value.mensagem == "Loja renomeada para \"Mercado C\"." }
        esperaLojas(viewModel) { it.any { l -> l.estabelecimento.nome == "Mercado C" } }

        val c = viewModel.estado.value.lojas.first { it.estabelecimento.nome == "Mercado C" }.estabelecimento
        viewModel.renomear(c, "mercado b") // colide com B sem acento/caixa
        espera { viewModel.estado.value.mensagem == "Já existe uma loja com esse nome" }
    }

    // --- remover ------------------------------------------------------------------------

    @Test
    fun `remover manda a loja para a lixeira e ela sai do estado`() = teste { viewModel ->
        val lojaId = banco.estabelecimentoDao().inserir(EstabelecimentoEntity(nome = "Mercado A", corHex = "#123456"))
        viewModel.atualizar()
        espera { viewModel.estado.value.lojas.any { it.estabelecimento.id == lojaId } }

        viewModel.remover(Estabelecimento(id = lojaId, nome = "Mercado A"))
        espera { viewModel.estado.value.mensagem == "A loja foi para a lixeira (30 dias)." }
        esperaLojas(viewModel) { it.isEmpty() }
        espera { banco.lixeiraDao().contar() == 1 }
    }

    // --- contagens ------------------------------------------------------------------------

    @Test
    fun `contagens somam precos validos e deduplicam produtos por nome`() = teste { viewModel ->
        val lojaId = precos.garantirEstabelecimento("Mercado A").id
        val (_, item1) = listaComItem("Arroz")
        val (_, item2) = listaComItem("Arroz") // mesmo produto em outra lista
        val (_, item3) = listaComItem("Feijão")
        precos.registrarPreco(item1, item1.produtoId, lojaId, BigDecimal("5.00"))
        precos.registrarPreco(item2, item2.produtoId, lojaId, BigDecimal("6.00"))
        // Um terceiro registro indisponível (não entra na contagem); registrar
        // no mesmo item SUBSTITUI o preço anterior — por isso é item próprio.
        precos.registrarPreco(item3, item3.produtoId, lojaId, BigDecimal("0.00"), disponivel = false)
        viewModel.atualizar()

        // guarda de tamanho: o estado inicial vazio não pode derrubar o predicado
        espera {
            val l = viewModel.estado.value.lojas
            l.size == 1 && l.single().quantidadeDePrecos == 2
        } // só os válidos
        val cartao = viewModel.estado.value.lojas.single()
        assertEquals(1, cartao.itensDistintos) // Arroz (2 listas) conta 1; Feijão indisponível nem entra
        assertEquals(listOf("Arroz"), cartao.produtosDaLoja)
    }

    // --- torradas -----------------------------------------------------------------------

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel ->
        viewModel.adicionar("Mercado A")
        espera { viewModel.estado.value.mensagem != null }

        viewModel.mensagemExibida()
        espera { viewModel.estado.value.mensagem == null }
    }
}

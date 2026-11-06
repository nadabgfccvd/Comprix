package br.com.comprix.presentation.precos

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.HistoricoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `PrecosViewModel`: o estado da matriz de preços (lista mestra + colunas de
 * loja), o ÚLTIMO dos ViewModels sem contrato próprio — com os repositórios
 * REAIS sobre Room em memória, a mesma receita estabilizada dos outros oito.
 *
 * Contratos pinados aqui:
 * - **Preço é por (item, loja)**: anotar numa loja não mexe na outra; valor
 *   zero/null LIMPA a célula (não vira preço zero);
 * - **aviso de preço alto** só quando o digitado fica acima do menor já
 *   registrado em QUALQUER loja, capturado ANTES da gravação (regravar o
 *   mínimo não avisa);
 * - **apagar preço != tirar item da lista**: limparPrecoDaLoja remove só a
 *   célula e arma desfazer de preço; removerItemDaListaMestra tira o produto
 *   de todas as lojas e arma desfazer de item com TODOS os preços — e o
 *   desfazer reinserindo volta com ID NOVO e preços re-anexados;
 * - **"não tinha" é dado legítimo**: indisponível não é pendente e não vira
 *   preço zero na cópia entre lojas (copiar leva só o disponível);
 * - **finalizarCompra** grava no histórico, finaliza a lista e tem guarda
 *   contra registro duplicado (segunda chamada não grava nem conclui);
 * - **previaDaCompra** considera só os itens marcados; sem nenhum marcado,
 *   considera a lista inteira.
 *
 * `exportarMatrizCsv` fica de fora: exige SAF (ContentResolver + Uri), que é
 * território de instrumentação — a montagem do CSV já é coberta pelo contrato
 * do ExportadorCsv.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrecosViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var listas: ListaRepositorio
    private lateinit var precos: PrecoRepositorio
    private var listaId: Long = 0L
    private lateinit var vm: PrecosViewModel

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
        listas = ListaRepositorio(
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            precoDao = banco.precoDao(),
            lixeira = lixeira,
        )
        precos = PrecoRepositorio(
            precoDao = banco.precoDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            lixeira = lixeira,
        )
        val catalogo = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
            lixeira = lixeira,
        )
        val configuracoes = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
            lixeira = lixeira,
        )
        val historico = HistoricoRepositorio(
            compraDao = banco.compraDao(),
            lixeira = lixeira,
        )
        listaId = runBlocking {
            banco.listaDao().inserir(
                br.com.comprix.data.local.ListaEntity(
                    nome = "Compra da semana",
                    criadaEm = 1_700_000_000_000L,
                    finalizada = false,
                    finalizadaEm = null,
                ),
            )
        }
        vm = PrecosViewModel(
            listaId = listaId,
            listaRepositorio = listas,
            precoRepositorio = precos,
            catalogoRepositorio = catalogo,
            configuracoesRepositorio = configuracoes,
            historicoRepositorio = historico,
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
            // O Room 2.8.x posta o refresh de invalidação no Handler principal; com o looper
            // do Robolectric pausado, os runnables só executam se drenados aqui.
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(10)
        }
    }

    private suspend fun itemDe(nome: String): Long {
        val produto = banco.produtoDao().salvar(
            Mapeadores.paraEntidade(
                Produto(
                    nome = nome,
                    nomeNormalizado = br.com.comprix.util.TextoUtil.normalizar(nome),
                    categoriaId = banco.categoriaDao().listarTodas().first().id,
                ),
            ),
        )
        return listas.adicionarItem(ItemDaLista(listaId = listaId, produtoId = produto))
    }

    private suspend fun loja(nome: String): Long = precos.garantirEstabelecimento(nome).id

    private fun assertMoeda(real: BigDecimal?, esperado: String) {
        assertNotNull("esperava R$ $esperado, veio null", real)
        assertEquals(0, real!!.compareTo(BigDecimal(esperado)))
    }

    private fun teste(bloco: suspend () -> Unit): Unit = runBlocking {
        Dispatchers.setMain(
            // event loop do runBlocking: single-thread e FIFO — evita retomadas
            // Unconfined inline em threads do executor do Room (corrida da suíte)
            coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        )
        try {
            val coletor = launch { vm.estado.collect {} }
            espera { !vm.estado.value.carregando }
            bloco()
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------------------------
    // estado inicial e loja ativa
    // ------------------------------------------------------------------

    @Test
    fun `estado assenta com lista carregada e sem loja`() = teste {
        assertEquals("Compra da semana", vm.estado.value.lista?.nome)
        assertTrue(vm.estado.value.itens.isEmpty())
        assertTrue(vm.estado.value.estabelecimentos.isEmpty())
        assertNull(vm.estado.value.lojaAtiva)
    }

    @Test
    fun `lojaAtiva cai na primeira loja criada`() = teste {
        val a = loja("Mercado A")
        espera { vm.estado.value.lojaAtivaId == a }
        assertEquals("Mercado A", vm.estado.value.lojaAtiva?.nome)
    }

    @Test
    fun `selecionarLoja troca a coluna ativa`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        espera { vm.estado.value.estabelecimentos.size == 2 }
        vm.selecionarLoja(b)
        espera { vm.estado.value.lojaAtivaId == b }
        assertEquals("Mercado B", vm.estado.value.lojaAtiva?.nome)
        vm.selecionarLoja(a)
        espera { vm.estado.value.lojaAtivaId == a }
    }

    @Test
    fun `criarOuSelecionarLoja cria ativa e reaproveita pelo mesmo nome`() = teste {
        vm.criarOuSelecionarLoja("Supermercado X")
        espera { vm.estado.value.lojaAtiva?.nome == "Supermercado X" }
        assertEquals(1, vm.estado.value.estabelecimentos.size)
        vm.criarOuSelecionarLoja("supermercado x ") // caixa e espaço: mesma loja
        espera { vm.estado.value.lojaAtivaId != null }
        assertEquals(1, vm.estado.value.estabelecimentos.size)
        assertEquals("Supermercado X", vm.estado.value.lojaAtiva?.nome)
    }

    // ------------------------------------------------------------------
    // preço por (item, loja)
    // ------------------------------------------------------------------

    @Test
    fun `anotarPreco grava a celula da loja sem tocar nas outras`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val item = itemDe("Arroz")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("7.90"))
        espera { vm.estado.value.precoDe(item, a) != null }
        assertMoeda(vm.estado.value.precoDe(item, a), "7.90")
        assertNull(vm.estado.value.precoDe(item, b))
        assertEquals(REGISTRADO, vm.estado.value.situacaoDe(item, a))
        assertEquals(PENDENTE, vm.estado.value.situacaoDe(item, b))
    }

    @Test
    fun `anotarPreco com zero ou null limpa a celula`() = teste {
        val a = loja("Mercado A")
        val item = itemDe("Feijão")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(item, a) != null }
        vm.anotarPreco(item, a, BigDecimal.ZERO)
        espera { vm.estado.value.precoDe(item, a) == null }
        assertEquals(PENDENTE, vm.estado.value.situacaoDe(item, a))
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(item, a) != null }
        vm.anotarPreco(item, a, null)
        espera { vm.estado.value.precoDe(item, a) == null }
    }

    @Test
    fun `anotarPreco acima do minimo avisa e regravar o minimo nao avisa`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val item = itemDe("Café")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(item, a) != null }
        assertNull("primeira gravação não tem referência", vm.mensagem.value)
        vm.anotarPreco(item, b, BigDecimal("7.00"))
        espera { vm.mensagem.value != null }
        assertTrue(vm.mensagem.value!!.contains("acima do menor registrado"))
        assertTrue(vm.mensagem.value!!.contains("R$"))
        vm.mensagemExibida()
        assertNull(vm.mensagem.value)
        // 4.00 passa a ser o mínimo — regravar 4.00 na A não pode avisar.
        vm.anotarPreco(item, a, BigDecimal("4.00"))
        espera { (vm.estado.value.precoDe(item, a) ?: BigDecimal.ZERO).compareTo(BigDecimal("4.00")) == 0 }
        delay(50)
        assertNull("regravar o mínimo não deve avisar", vm.mensagem.value)
    }

    // ------------------------------------------------------------------
    // limpar preço e desfazer de preço
    // ------------------------------------------------------------------

    @Test
    fun `limparPrecoDaLoja apaga so a celula e arma desfazer de preço`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val item = itemDe("Açúcar")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        vm.anotarPreco(item, b, BigDecimal("6.00"))
        espera { vm.estado.value.precoDe(item, b) != null }
        vm.limparPrecoDaLoja(item, a)
        espera { vm.estado.value.precoDe(item, a) == null }
        assertMoeda(vm.estado.value.precoDe(item, b), "6.00")
        assertEquals(1, vm.estado.value.itens.size) // produto continua na lista
        val desfazer = vm.desfazer.value
        assertNotNull(desfazer)
        assertEquals(PrecosViewModel.AcaoDesfazivel.Tipo.PRECO_REMOVIDO, desfazer!!.tipo)
        assertTrue(desfazer.descricao.contains("Mercado A"))
        assertMoeda(desfazer.precos.single().preco, "5.00")
    }

    @Test
    fun `desfazerUltimaAcao devolve o preço apagado`() = teste {
        val a = loja("Mercado A")
        val item = itemDe("Sal")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("2.50"))
        espera { vm.estado.value.precoDe(item, a) != null }
        vm.limparPrecoDaLoja(item, a)
        espera { vm.estado.value.precoDe(item, a) == null }
        vm.desfazerUltimaAcao()
        espera { vm.estado.value.precoDe(item, a) != null }
        assertMoeda(vm.estado.value.precoDe(item, a), "2.50")
        assertNull(vm.desfazer.value)
    }

    // ------------------------------------------------------------------
    // indisponível é dado legítimo
    // ------------------------------------------------------------------

    @Test
    fun `marcarIndisponivel registra situacao propria e avisa com o nome`() = teste {
        val a = loja("Mercado A")
        val item = itemDe("Leite de coco")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.marcarIndisponivel(item, a)
        espera { vm.estado.value.situacaoDe(item, a) == INDISPONIVEL }
        assertTrue(vm.mensagem.value!!.contains("Leite de coco"))
        assertTrue(vm.mensagem.value!!.contains("indisponível"))
    }

    // ------------------------------------------------------------------
    // remover da lista mestra e desfazer reinserindo
    // ------------------------------------------------------------------

    @Test
    fun `removerItemDaListaMestra tira o item de todas as lojas e arma desfazer`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val item = itemDe("Macarrão")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        vm.anotarPreco(item, b, BigDecimal("6.00"))
        espera { vm.estado.value.precoDe(item, b) != null }
        vm.removerItemDaListaMestra(item)
        espera { vm.estado.value.itens.isEmpty() }
        val desfazer = vm.desfazer.value
        assertNotNull(desfazer)
        assertEquals(PrecosViewModel.AcaoDesfazivel.Tipo.ITEM_REMOVIDO, desfazer!!.tipo)
        assertTrue(desfazer.descricao.contains("Macarrão"))
        assertEquals(2, desfazer.precos.size)
    }

    @Test
    fun `desfazerUltimaAcao reinserindo devolve o item com novo id e os preços`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val item = itemDe("Óleo")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        vm.anotarPreco(item, b, BigDecimal("6.00"))
        espera { vm.estado.value.precoDe(item, b) != null }
        vm.removerItemDaListaMestra(item)
        espera { vm.estado.value.itens.isEmpty() }
        vm.desfazerUltimaAcao()
        espera { vm.estado.value.itens.size == 1 }
        val novoId = vm.estado.value.itens.single().item.id
        assertFalse("restauração cria linha nova", novoId == item)
        // os preços re-anexados chegam no flow depois do item — esperar de verdade
        espera { vm.estado.value.precoDe(novoId, a) != null }
        assertMoeda(vm.estado.value.precoDe(novoId, a), "5.00")
        espera { vm.estado.value.precoDe(novoId, b) != null }
        assertMoeda(vm.estado.value.precoDe(novoId, b), "6.00")
    }

    // ------------------------------------------------------------------
    // cópia entre lojas
    // ------------------------------------------------------------------

    @Test
    fun `copiarPrecosEntreLojas leva só o disponível e nunca preço zero`() = teste {
        val a = loja("Mercado A")
        val b = loja("Mercado B")
        val arroz = itemDe("Arroz")
        val cafe = itemDe("Café")
        espera { vm.estado.value.itens.size == 2 }
        vm.anotarPreco(arroz, a, BigDecimal("5.00"))
        vm.marcarIndisponivel(cafe, a)
        espera { vm.estado.value.situacaoDe(cafe, a) == INDISPONIVEL }
        vm.copiarPrecosEntreLojas(deId = a, paraId = b)
        espera { vm.estado.value.precoDe(arroz, b) != null }
        assertMoeda(vm.estado.value.precoDe(arroz, b), "5.00")
        assertNull("indisponível não vira preço zero", vm.estado.value.precoDe(cafe, b))
        assertTrue(vm.mensagem.value!!.contains("copiados"))
        vm.mensagemExibida()
        val c = loja("Mercado C") // origem de verdade sem nada
        espera { vm.estado.value.estabelecimentos.size == 3 }
        vm.copiarPrecosEntreLojas(deId = c, paraId = a)
        espera { vm.mensagem.value != null }
        assertTrue(vm.mensagem.value!!.contains("Nenhum preço para copiar"))
    }

    // ------------------------------------------------------------------
    // matriz
    // ------------------------------------------------------------------

    @Test
    fun `matriz so existe com duas lojas e itens`() = teste {
        val a = loja("Mercado A")
        val item = itemDe("Arroz")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(item, a) != null }
        assertNull("uma loja não monta matriz", vm.estado.value.matriz)
        val b = loja("Mercado B")
        vm.anotarPreco(item, b, BigDecimal("6.00"))
        // o melhor preço da linha chega no flow junto com o segundo preço
        // singleOrNull: matriz com linhas ainda vazia NÃO pode estourar dentro do espera
        espera { vm.estado.value.matriz?.linhas?.singleOrNull()?.melhorEstabelecimentoId == a }
        assertEquals(1, vm.estado.value.matriz!!.linhas.size)
        assertEquals(a, vm.estado.value.matriz!!.linhas.single().melhorEstabelecimentoId)
    }

    // ------------------------------------------------------------------
    // prévia e finalização da compra
    // ------------------------------------------------------------------

    @Test
    fun `previaDaCompra sem marcados conta a lista inteira - com marcados so os marcados`() = teste {
        val a = loja("Mercado A")
        val arroz = itemDe("Arroz")
        val cafe = itemDe("Café")
        espera { vm.estado.value.itens.size == 2 }
        vm.anotarPreco(arroz, a, BigDecimal("5.00"))
        vm.anotarPreco(cafe, a, BigDecimal("20.00"))
        espera { vm.estado.value.precoDe(cafe, a) != null }
        assertEquals(2, vm.previaDaCompra().quantidadeItens)
        assertMoeda(vm.previaDaCompra().totalPago, "25.00")
        listas.marcarComprado(arroz, true)
        espera { vm.estado.value.itens.first { it.item.id == arroz }.item.comprado }
        assertEquals(1, vm.previaDaCompra().quantidadeItens)
        assertMoeda(vm.previaDaCompra().totalPago, "5.00")
    }

    @Test
    fun `finalizarCompra grava finaliza e guarda contra duplicado`() = teste {
        val a = loja("Mercado A")
        val arroz = itemDe("Arroz")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(arroz, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(arroz, a) != null }
        val concluidos = mutableListOf<Long>()
        vm.finalizarCompra { concluidos.add(it) }
        espera { vm.estado.value.lista?.finalizada == true }
        assertEquals(1, concluidos.size)
        assertTrue(vm.mensagem.value!!.contains("Compra registrada"))
        // Guarda: segunda chamada não grava de novo nem conclui de novo.
        vm.finalizarCompra { concluidos.add(it) }
        espera { vm.mensagem.value == "Esta conta já foi finalizada." }
        assertEquals(1, concluidos.size)
        // Os preços continuam na lista — reabrir não perde nada.
        assertMoeda(vm.estado.value.precoDe(arroz, a), "5.00")
    }

    @Test
    fun `mensagemExibida limpa a torrada e descartarDesfazer limpa o desfazer`() = teste {
        val a = loja("Mercado A")
        val item = itemDe("Arroz")
        espera { vm.estado.value.itens.isNotEmpty() }
        vm.anotarPreco(item, a, BigDecimal("5.00"))
        espera { vm.estado.value.precoDe(item, a) != null }
        vm.anotarPreco(item, a, BigDecimal("9.00")) // aviso: acima do mínimo
        espera { vm.mensagem.value != null }
        vm.mensagemExibida()
        assertNull(vm.mensagem.value)
        vm.limparPrecoDaLoja(item, a)
        espera { vm.desfazer.value != null }
        vm.descartarDesfazer()
        assertNull(vm.desfazer.value)
    }

    private companion object {
        val REGISTRADO = br.com.comprix.domain.preco.AuditoriaDeCobertura.Situacao.REGISTRADO
        val PENDENTE = br.com.comprix.domain.preco.AuditoriaDeCobertura.Situacao.PENDENTE
        val INDISPONIVEL = br.com.comprix.domain.preco.AuditoriaDeCobertura.Situacao.INDISPONIVEL
    }
}

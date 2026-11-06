package br.com.comprix.presentation.catalogo

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.domain.catalogo.CatalogoSemente
import br.com.comprix.domain.modelo.Produto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * `CatalogoViewModel`: o estado da tela de exploração (busca no acervo-semente,
 * filtro por setor, seção "Seus produtos" e o caminho de `adicionar`), com o
 * repositório REAL sobre Room em memória e o acervo de fábrica construído em
 * texto — nenhum fake.
 *
 * Contratos pinados: a busca resolve apelido de fábrica ("tang" acha Tangerina);
 * a seção local só existe com consulta digitada E sem filtro de setor (o setor
 * filtra o acervo, não o acervo pessoal — e sinônimos bidirecionais fazem
 * "mexerica" achar a Tangerina gravada pelo usuário); o teto de 120 da busca
 * liga o aviso `buscaLimitada` só quando NENHUM filtro de setor está ativo;
 * `adicionar` sem lista registra no catálogo local e DIZ ISSO em vez de fingir
 * sucesso; nome em branco é ignorado sem mensagem nem produto.
 *
 * Infraestrutura (receita das iterações 13-19): Room emite em executores REAIS
 * de IO, fora do alcance do relógio virtual — o `Main` do `viewModelScope` vira
 * `Dispatchers.Unconfined` de verdade e os efeitos assíncronos são esperados com
 * [espera] (polling com `delay`; NUNCA `Thread.sleep`, que bloqueia o
 * event-loop do `runBlocking` e mata de fome o coletor do estado).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogoViewModelTest {
    private lateinit var banco: ComprixDatabase

    /** Semente mínima: Tangerina responde por mexerica, bergamota e o alias tang. */
    private val sementePadrao =
        """
        SETOR|1|Mercado|#1E8E5A
        SUB|1|Hortifruti|1
        ITEM|Tangerina|1|mexerica,bergamota
        ALIAS|tang|Tangerina
        """.trimIndent()

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- auxiliares -------------------------------------------------------------------

    private fun novoRepositorio(semente: String): CatalogoRepositorio = CatalogoRepositorio(
        categoriaDao = banco.categoriaDao(),
        produtoDao = banco.produtoDao(),
        memoriaDao = banco.memoriaCategoriaDao(),
        decisaoDoParserDao = banco.decisaoDoParserDao(),
        catalogoSemente = CatalogoSemente(semente),
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

    /** Espera (com prazo real de 5 s) até a condição do efeito assíncrono valer. */
    private suspend fun espera(condicao: suspend () -> Boolean) {
        val prazo = System.currentTimeMillis() + 5_000
        while (!condicao()) {
            if (System.currentTimeMillis() > prazo) throw AssertionError("condição não atingida em 5 s")
            delay(10) // suspende (cede o event-loop); Thread.sleep bloquearia
        }
    }

    /**
     * Monta o ViewModel com o Main trocado por um dispatcher não confinado
     * (launches inline) e o `estado` já subscrito — o `WhileSubscribed` só
     * sobe com observador vivo. Entrega o ViewModel, o repositório e a
     * lista de inserções capturada pelo callback injetado.
     */
    private fun teste(
        semente: String = sementePadrao,
        bloco: suspend (
            viewModel: CatalogoViewModel,
            repositorio: CatalogoRepositorio,
            adicionados: MutableList<Pair<Long, Produto>>,
        ) -> Unit,
    ): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val repositorio = novoRepositorio(semente)
            val adicionados = mutableListOf<Pair<Long, Produto>>()
            val viewModel = CatalogoViewModel(repositorio) { listaId, produto ->
                adicionados += listaId to produto
            }
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando } // estado inicial assentou
            bloco(viewModel, repositorio, adicionados)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Cria um produto no acervo LOCAL pelo mesmo caminho que o `adicionar` usa. */
    private suspend fun CatalogoRepositorio.criarProdutoLocal(nome: String): Produto {
        val sugestao = sugerirCategoria(nome)
        return encontrarOuCriar(nome = nome, categoriaId = sugestao.categoria.id)
    }

    // --- estado -----------------------------------------------------------------------

    @Test
    fun `estado inicial mostra o acervo agrupado com setores e sem carregando`() = teste { viewModel, _, _ ->
        val estado = viewModel.estado.value
        assertFalse(estado.carregando)
        assertEquals(1, estado.setores.size)
        assertEquals("Mercado", estado.setores.single().nome)
        assertEquals(1, estado.grupos.size)
        val grupo = estado.grupos.single()
        assertEquals("Hortifruti", grupo.subcategoria.nome)
        assertEquals(listOf("Tangerina"), grupo.itens.map { it.nome })
        assertEquals(1, estado.total)
        assertTrue(estado.produtosLocais.isEmpty())
        assertFalse(estado.buscaLimitada)
    }

    @Test
    fun `busca por apelido de fabrica acha o item do acervo`() = teste { viewModel, _, _ ->
        viewModel.definirConsulta("tang")
        espera { viewModel.estado.value.total == 1 && viewModel.estado.value.consulta == "tang" }
        val estado = viewModel.estado.value
        assertEquals(listOf("Tangerina"), estado.grupos.single().itens.map { it.nome })
        assertFalse(estado.buscaLimitada)
    }

    @Test
    fun `secao local so existe com consulta e sem filtro de setor`() = teste { viewModel, repositorio, _ ->
        val produto = repositorio.criarProdutoLocal("Mexerica da feira")

        // Sem consulta: a seção local NÃO existe (a exploração aberta é do acervo).
        espera { viewModel.estado.value.grupos.isNotEmpty() }
        assertTrue(viewModel.estado.value.produtosLocais.isEmpty())

        // Consulta pelo nome canônico do acervo: os sinônimos se expandem
        // (tangerina -> mexerica, bergamota) e o produto local casado aparece.
        viewModel.definirConsulta("tangerina")
        espera { viewModel.estado.value.produtosLocais.map { it.id } == listOf(produto.id) }
    }

    @Test
    fun `filtro de setor recolhe o acervo e esconde a secao local`() = teste { viewModel, repositorio, _ ->
        repositorio.criarProdutoLocal("Tangerina")
        viewModel.definirConsulta("tangerina")
        espera { viewModel.estado.value.produtosLocais.size == 1 }

        // Setor existente: acervo do setor continua, seção local some (o setor
        // filtra o acervo, não o acervo pessoal).
        viewModel.selecionarSetor(1)
        espera { viewModel.estado.value.produtosLocais.isEmpty() }
        val comSetor = viewModel.estado.value
        assertEquals(1, comSetor.grupos.size)
        assertEquals(1, comSetor.total)
        assertFalse(comSetor.buscaLimitada) // com filtro, o aviso de teto não ajuda

        // Setor sem subcategorias: acervo some e a contagem vai a zero.
        viewModel.selecionarSetor(99)
        espera { viewModel.estado.value.total == 0 }
        assertTrue(viewModel.estado.value.grupos.isEmpty())
        assertTrue(viewModel.estado.value.produtosLocais.isEmpty())
    }

    @Test
    fun `teto de 120 resultados liga o aviso so sem filtro de setor`() = teste(
        semente = buildString {
            appendLine("SETOR|1|Mercado|#1E8E5A")
            appendLine("SUB|1|Hortifruti|1")
            repeat(125) { indice -> appendLine("ITEM|Item de teste ${indice + 1}|1|") }
        },
    ) { viewModel, _, _ ->
        viewModel.definirConsulta("item")
        espera { viewModel.estado.value.total == 120 }
        val estado = viewModel.estado.value
        assertEquals(120, estado.grupos.single().itens.size)
        assertTrue(estado.buscaLimitada)

        // Com filtro de setor ativo a contagem já é um recorte menor: o aviso some.
        viewModel.selecionarSetor(1)
        espera { !viewModel.estado.value.buscaLimitada }
        assertEquals(120, viewModel.estado.value.total)
    }

    // --- adicionar ----------------------------------------------------------------------

    @Test
    fun `adicionar com lista insere via callback e avisa`() = teste { viewModel, repositorio, adicionados ->
        viewModel.adicionar("Tangerina", listaId = 5L)
        espera { adicionados.isNotEmpty() }
        val (listaId, produto) = adicionados.single()
        assertEquals(5L, listaId)
        assertEquals("Tangerina", produto.nome)
        assertEquals("Tangerina adicionado à lista.", viewModel.mensagem.value)
        // O produto realmente foi parar no acervo local (encontrar ou criar).
        espera { repositorio.produtos.first().any { it.id == produto.id } }
    }

    @Test
    fun `adicionar sem lista registra no catalogo local e diz isso`() = teste { viewModel, repositorio, adicionados ->
        viewModel.adicionar("Tangerina", listaId = 0L)
        espera { viewModel.mensagem.value != null }
        assertTrue(adicionados.isEmpty())
        assertEquals("Tangerina registrado no catálogo local.", viewModel.mensagem.value)
        espera { repositorio.produtos.first().any { it.nome == "Tangerina" } }
    }

    @Test
    fun `adicionar em branco e ignorado sem mensagem nem produto`() = teste { viewModel, repositorio, adicionados ->
        viewModel.adicionar("   ", listaId = 5L)
        delay(100) // tempo de sobra para um launch indevido aparecer
        assertTrue(adicionados.isEmpty())
        assertNull(viewModel.mensagem.value)
        assertTrue(repositorio.produtos.first().isEmpty())
    }

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel, _, _ ->
        viewModel.adicionar("Tangerina", listaId = 0L)
        espera { viewModel.mensagem.value != null }
        viewModel.mensagemExibida()
        assertNull(viewModel.mensagem.value)
    }

    // --- sinônimos -----------------------------------------------------------------------

    @Test
    fun `sinonimosDoProduto vem do acervo e volta vazio para desconhecido`() = teste { viewModel, _, _ ->
        val sinonimos = viewModel.sinonimosDoProduto("Tangerina")
        assertTrue("mexerica" in sinonimos)
        assertTrue("bergamota" in sinonimos)
        assertTrue(viewModel.sinonimosDoProduto("Fruta inexistente").isEmpty())
    }
}

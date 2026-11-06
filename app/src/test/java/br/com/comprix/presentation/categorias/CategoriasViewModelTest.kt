package br.com.comprix.presentation.categorias

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.Produto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * `CategoriasViewModel`: o estado da tela de categorias (lista ordenada,
 * contagem de produtos, aprendizado, reordenação acessível e torradas), com
 * o catálogo REAL sobre Room em memória — nenhum fake.
 *
 * Contrato pinado aqui: as 14 categorias de fábrica não são removíveis (a
 * torrada avisa); criar/renomear/remove-do-usuário têm mensagens congeladas;
 * `aprendidos` reflete a memória de correções e `esquecerAprendizado` zera;
 * mover (setas acessíveis) e reordenar (arrasto) gravam múltiplos de 10 no
 * banco; e restaurar a ordem de fábrica devolve "Hortifrúti" ao topo.
 *
 * Receita de infraestrutura: a mesma dos outros ViewModels — `Main` vira
 * `Dispatchers.Unconfined` e os efeitos assíncronos esperam com [espera]
 * (`delay`, nunca `Thread.sleep`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CategoriasViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var catalogo: CatalogoRepositorio

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
            catalogoSemente = null,
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

    private fun teste(bloco: suspend (CategoriasViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val viewModel = CategoriasViewModel(catalogo)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }
            bloco(viewModel)
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun produtoNa(nome: String, categoriaId: Long) {
        banco.produtoDao().salvar(
            Produto(nome = nome, nomeNormalizado = "", categoriaId = categoriaId).let(
                br.com.comprix.data.local.Mapeadores::paraEntidade,
            ),
        )
    }

    // --- estado -------------------------------------------------------------------------

    @Test
    fun `estado inicial tem as 14 de fabrica e zero aprendidos`() = teste { viewModel ->
        val estado = viewModel.estado.value
        assertEquals(14, estado.categorias.size)
        assertEquals("Hortifrúti", estado.categorias.first().nome)
        assertEquals(0, estado.aprendidos)
        assertEquals(0, estado.totalDeProdutos)
        assertFalse(estado.reordenacaoManual)
    }

    @Test
    fun `contagem de produtos por categoria alimenta o total`() = runBlocking {
        val hortifruti = banco.categoriaDao().listarTodas().single { it.chave == "hortifruti" }.id
        produtoNa("Banana", hortifruti)
        produtoNa("Mamão", hortifruti)
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val viewModel = CategoriasViewModel(catalogo)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }
            espera { viewModel.estado.value.totalDeProdutos == 2 }
            assertEquals(2, viewModel.estado.value.produtosPorCategoria.getValue(hortifruti))
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- criar, renomear, remover ---------------------------------------------------------

    @Test
    fun `criar publica a mensagem e a categoria entra no fim`() = teste { viewModel ->
        viewModel.criar("Bazar")
        espera { viewModel.mensagem.value == "Categoria “Bazar” criada." }
        espera { viewModel.estado.value.categorias.any { it.nome == "Bazar" } }
        assertEquals(15, viewModel.estado.value.categorias.size)
    }

    @Test
    fun `criar em branco e ignorado`() = teste { viewModel ->
        viewModel.criar("   ")
        delay(50)
        assertNull(viewModel.mensagem.value)
        assertEquals(14, viewModel.estado.value.categorias.size)
    }

    @Test
    fun `renomear publica a mensagem e grava o aparado`() = teste { viewModel ->
        val bazar = Categoria(nome = "Bazar", icone = "outros", ordemPadrao = 950, origem = OrigemCategoria.USUARIO, chave = "")
        val id = banco.categoriaDao().inserir(br.com.comprix.data.local.Mapeadores.paraEntidade(bazar))
        espera { viewModel.estado.value.categorias.any { it.id == id } }

        viewModel.renomear(viewModel.estado.value.categorias.single { it.id == id }, "  Limpeza  ")
        espera { viewModel.mensagem.value == "Categoria renomeada para “Limpeza”." }
        espera { banco.categoriaDao().listarTodas().single { it.id == id }.nome == "Limpeza" }
    }

    @Test
    fun `renomear em branco ou igual e ignorado`() = teste { viewModel ->
        val padrao = viewModel.estado.value.categorias.first()

        viewModel.renomear(padrao, "   ")
        viewModel.renomear(padrao, padrao.nome)
        delay(50)
        assertNull(viewModel.mensagem.value)
    }

    @Test
    fun `remover do usuario vai para a lixeira e padrao e recusado`() = teste { viewModel ->
        viewModel.criar("Bazar")
        espera { viewModel.estado.value.categorias.any { it.nome == "Bazar" } }
        val bazar = viewModel.estado.value.categorias.single { it.nome == "Bazar" }

        viewModel.remover(bazar)
        espera { viewModel.mensagem.value == "Categoria “Bazar” foi para a lixeira (30 dias)." }
        espera { viewModel.estado.value.categorias.none { it.nome == "Bazar" } }
        espera { banco.lixeiraDao().contar() == 1 }

        val hortifruti = viewModel.estado.value.categorias.first()
        viewModel.remover(hortifruti)
        assertEquals("As categorias originais não podem ser removidas, só renomeadas.", viewModel.mensagem.value)
        espera { viewModel.estado.value.categorias.any { it.nome == "Hortifrúti" } }
    }

    // --- aprendizado ------------------------------------------------------------------------

    @Test
    fun `aprendidos reflete a memoria e esquecerAprendizado zera`() = runBlocking {
        // O init recarrega as contagens: o aprendizado precisa existir antes.
        catalogo.lembrarCategoria("banana prata", 3L)
        catalogo.lembrarCategoria("detergente", 6L)
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val viewModel = CategoriasViewModel(catalogo)
            val coletor = launch { viewModel.estado.collect {} }
            espera { !viewModel.estado.value.carregando }
            espera { viewModel.estado.value.aprendidos == 2 }

            viewModel.esquecerAprendizado()
            espera { viewModel.mensagem.value == "Correções de categoria esquecidas." }
            espera { viewModel.estado.value.aprendidos == 0 }
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- reordenacao ------------------------------------------------------------------------

    @Test
    fun `alternarReordenacaoManual liga a flag no estado`() = teste { viewModel ->
        viewModel.alternarReordenacaoManual(true)
        espera { viewModel.estado.value.reordenacaoManual }
        viewModel.alternarReordenacaoManual(false)
        espera { !viewModel.estado.value.reordenacaoManual }
    }

    @Test
    fun `mover com setas reordena e grava multiplos de 10 no banco`() = teste { viewModel ->
        val ids = viewModel.estado.value.categorias.take(2).map { it.id }

        viewModel.mover(ids[0], 1) // primeira desce uma posicao
        espera { viewModel.estado.value.categorias.take(2).map { it.id } == listOf(ids[1], ids[0]) }
        espera { banco.categoriaDao().listarTodas().single { it.id == ids[1] }.ordemPadrao == 10 }
        assertEquals(20, banco.categoriaDao().listarTodas().single { it.id == ids[0] }.ordemPadrao)
    }

    @Test
    fun `reordenar aplica o arrasto e id fora dos limites e ignorado`() = teste { viewModel ->
        val ids = viewModel.estado.value.categorias.take(3).map { it.id }

        viewModel.reordenar(0, 2) // primeiro vai para a terceira posicao
        espera { viewModel.estado.value.categorias.take(3).map { it.id } == listOf(ids[1], ids[2], ids[0]) }

        viewModel.reordenar(-1, 99) // fora dos limites: nada muda
        delay(50)
        assertEquals(listOf(ids[1], ids[2], ids[0]), viewModel.estado.value.categorias.take(3).map { it.id })
    }

    @Test
    fun `restaurarOrdemPadrao devolve hortifruti ao topo com a torrada`() = teste { viewModel ->
        val ids = viewModel.estado.value.categorias.take(3).map { it.id }
        viewModel.reordenar(0, 2)
        espera { viewModel.estado.value.categorias.take(3).map { it.id } == listOf(ids[1], ids[2], ids[0]) }

        viewModel.restaurarOrdemPadrao()
        espera { viewModel.mensagem.value == "Ordem do mercado restaurada." }
        espera { viewModel.estado.value.categorias.first().nome == "Hortifrúti" }
        assertEquals(10, banco.categoriaDao().listarTodas().single { it.chave == "hortifruti" }.ordemPadrao)
    }

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel ->
        viewModel.criar("Bazar")
        espera { viewModel.mensagem.value != null }

        viewModel.mensagemExibida()
        assertNull(viewModel.mensagem.value)
    }
}

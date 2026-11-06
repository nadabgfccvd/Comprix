package br.com.comprix.presentation.lixeira

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * `LixeiraViewModel`: o estado da tela da lixeira (lista observada, contagens
 * dos chips, filtro por categoria + busca e as torradas das ações), com os
 * repositórios REAIS sobre Room em memória — nenhum fake.
 *
 * Contrato pinado aqui: a purga de 30 dias roda no ARRANQUE do ViewModel (o
 * que passou do prazo nem aparece); as contagens por categoria derivam dos
 * registros; o filtro por categoria e a busca se COMBINAM (e a busca é sem
 * acento e sem maiúscula); e as mensagens das quatro ações estão congeladas.
 *
 * A receita de infraestrutura é a mesma do `ListasViewModelTest`: `Main`
 * trocado por `Dispatchers.Unconfined` e efeitos assíncronos esperados com
 * [espera] (polling com `delay` — nunca `Thread.sleep`, que bloquearia o
 * event-loop do `runBlocking` e mataria de fome o coletor).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LixeiraViewModelTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var lixeira: LixeiraRepositorio

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
            delay(10) // suspende (cede o event-loop); Thread.sleep bloquearia
        }
    }

    private fun registro(tipo: String, titulo: String, enviadaEm: Long = 1_700_000_000_000L): Long =
        runBlocking {
            banco.lixeiraDao().inserir(
                RegistroDeLixeiraEntity(
                    tipo = tipo, titulo = titulo, detalhe = null, refId = 0,
                    payload = """{"dados":{"nome":"$titulo","criada":"1700000000000","finalizada":"false","favorita":"false"}}""",
                    enviadaEm = enviadaEm,
                ),
            )
        }

    private fun teste(bloco: suspend (LixeiraViewModel) -> Unit): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val catalogo = CatalogoRepositorio(
                categoriaDao = banco.categoriaDao(),
                produtoDao = banco.produtoDao(),
                memoriaDao = banco.memoriaCategoriaDao(),
                decisaoDoParserDao = banco.decisaoDoParserDao(),
                catalogoSemente = null,
                lixeira = lixeira,
            )
            val viewModel = LixeiraViewModel(lixeira, catalogo)
            val coletores = listOf(
                launch { viewModel.registros.collect {} },
                launch { viewModel.contagens.collect {} },
                launch { viewModel.registrosVisiveis.collect {} },
            )
            espera { viewModel.contagens.value.isNotEmpty() }
            bloco(viewModel)
            coletores.forEach { it.cancel() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- arranque -----------------------------------------------------------------------

    @Test
    fun `purga roda no arranque e o registro vencido nem aparece`() = runBlocking {
        // Os registros precisam existir ANTES do ViewModel: a purga roda no init.
        val fora = registro("LISTAS", "velha demais", enviadaEm = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000)
        val dentro = registro("LISTAS", "ainda cabe", enviadaEm = System.currentTimeMillis())
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val catalogo = CatalogoRepositorio(
                categoriaDao = banco.categoriaDao(),
                produtoDao = banco.produtoDao(),
                memoriaDao = banco.memoriaCategoriaDao(),
                decisaoDoParserDao = banco.decisaoDoParserDao(),
                catalogoSemente = null,
                lixeira = lixeira,
            )
            val viewModel = LixeiraViewModel(lixeira, catalogo)
            val coletor = launch { viewModel.registros.collect {} }

            espera { banco.lixeiraDao().porId(fora) == null }
            assertTrue(banco.lixeiraDao().porId(dentro) != null)
            espera { viewModel.registros.value.map { it.id } == listOf(dentro) }
            coletor.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- listas observadas ----------------------------------------------------------------

    @Test
    fun `registros vem do mais recente para o mais antigo`() = teste { viewModel ->
        val antigo = registro("LISTAS", "Antigo", enviadaEm = 1_700_000_000_000L)
        val recente = registro("PRODUTOS", "Recente", enviadaEm = 1_800_000_000_000L)

        espera { viewModel.registros.value.map { it.id } == listOf(recente, antigo) }
    }

    @Test
    fun `contagens por categoria derivam dos registros`() = teste { viewModel ->
        registro("LISTAS", "A")
        registro("LISTAS", "B")
        registro("PRODUTOS", "C")

        espera { viewModel.contagens.value[LixeiraRepositorio.TipoDeLixeira.LISTAS] == 2 }
        espera { viewModel.contagens.value[LixeiraRepositorio.TipoDeLixeira.PRODUTOS] == 1 }
    }

    // --- filtros -------------------------------------------------------------------------

    @Test
    fun `selecionarTipo filtra os registros visiveis`() = teste { viewModel ->
        val lista = registro("LISTAS", "Semanal")
        registro("PRODUTOS", "Arroz")

        viewModel.selecionarTipo(LixeiraRepositorio.TipoDeLixeira.LISTAS)
        espera { viewModel.registrosVisiveis.value.map { it.id } == listOf(lista) }

        viewModel.selecionarTipo(null) // Todas
        espera { viewModel.registrosVisiveis.value.size == 2 }
    }

    @Test
    fun `busca casa sem acento e sem maiuscula e vazio desliga`() = teste { viewModel ->
        registro("LISTAS", "Tangerina da feira")

        viewModel.definirBusca("TANGERINA")
        espera { viewModel.registrosVisiveis.value.size == 1 }

        viewModel.definirBusca("")
        espera { viewModel.registrosVisiveis.value.size == 1 }
    }

    @Test
    fun `tipo e busca se combinam`() = teste { viewModel ->
        registro("LISTAS", "Tangerina da feira")
        registro("LOJAS", "Tangerina mercado")

        viewModel.definirBusca("tangerina")
        viewModel.selecionarTipo(LixeiraRepositorio.TipoDeLixeira.LOJAS)
        espera { viewModel.registrosVisiveis.value.size == 1 }
        assertEquals("Tangerina mercado", viewModel.registrosVisiveis.value.single().titulo)
    }

    // --- acoes e torradas -------------------------------------------------------------------

    @Test
    fun `restaurar devolve a mensagem do repositorio e o registro sai`() = teste { viewModel ->
        val id = registro("LISTAS", "Semanal")

        viewModel.restaurar(id)
        espera { viewModel.mensagem.value == "\"Semanal\" restaurado(a)." }
        espera { banco.lixeiraDao().porId(id) == null }
    }

    @Test
    fun `removerRegistro apaga e mensagem singular ou avisa inexistente`() = teste { viewModel ->
        val id = registro("LISTAS", "Semanal")

        viewModel.removerRegistro(id)
        espera { viewModel.mensagem.value == "Registro apagado definitivamente." }
        espera { banco.lixeiraDao().porId(id) == null }

        viewModel.mensagemExibida()
        viewModel.removerRegistro(999L)
        espera { viewModel.mensagem.value == "Registro não encontrado na lixeira." }
    }

    @Test
    fun `esvaziarCategoria apaga so a secao pedida com mensagem no plural`() = teste { viewModel ->
        registro("LISTAS", "A")
        registro("LISTAS", "B")
        val deLoja = registro("LOJAS", "Mercado A")

        viewModel.esvaziarCategoria(LixeiraRepositorio.TipoDeLixeira.LISTAS)
        espera { viewModel.mensagem.value == "2 registros apagados definitivamente." }
        espera { banco.lixeiraDao().porId(deLoja) != null }
        espera { viewModel.contagens.value[LixeiraRepositorio.TipoDeLixeira.LISTAS] == 0 }
    }

    @Test
    fun `esvaziarCategoria vazia avisa que nao havia registros`() = teste { viewModel ->
        viewModel.esvaziarCategoria(LixeiraRepositorio.TipoDeLixeira.CATEGORIAS)
        espera { viewModel.mensagem.value == "Não havia registros nesta categoria." }
    }

    @Test
    fun `esvaziarTudo apaga tudo e segunda vez avisa que ja estava vazia`() = teste { viewModel ->
        registro("LISTAS", "A")
        registro("LOJAS", "Mercado A")

        viewModel.esvaziarTudo()
        espera { viewModel.mensagem.value == "2 registros apagados definitivamente." }
        espera { viewModel.registros.value.isEmpty() }

        viewModel.esvaziarTudo()
        espera { viewModel.mensagem.value == "A lixeira já estava vazia." }
    }

    @Test
    fun `mensagemExibida limpa a torrada`() = teste { viewModel ->
        registro("LISTAS", "Semanal")

        viewModel.esvaziarCategoria(LixeiraRepositorio.TipoDeLixeira.LISTAS)
        espera { viewModel.mensagem.value != null }

        viewModel.mensagemExibida()
        espera { viewModel.mensagem.value == null }
    }

    @Test
    fun `expiraEm soma o prazo de 30 dias a partir da exclusao`() = teste { viewModel ->
        val enviadaEm = 1_700_000_000_000L
        val id = registro("LISTAS", "Semanal", enviadaEm = enviadaEm)

        val esperado = java.time.Instant.ofEpochMilli(enviadaEm + LixeiraRepositorio.PRAZO_EM_MS)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

        assertEquals(esperado, viewModel.expiraEm(banco.lixeiraDao().porId(id)!!))
    }
}

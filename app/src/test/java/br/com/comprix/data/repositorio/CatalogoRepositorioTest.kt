package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.MemoriaCategoriaEntity
import br.com.comprix.domain.catalogo.CatalogoSemente
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
 * `CatalogoRepositorio`: o contrato do catálogo (categorias, produtos, favoritos,
 * validade) e do APRENDIZADO (memória de categorização e decisões do parser),
 * de ponta a ponta com Room em memória.
 *
 * A semente usada aqui é mínima e construída em texto (mesmo formato do acervo
 * embutido): um item "Tangerina" com os sinônimos "mexerica" e "bergamota" e um
 * alias "tang" — o suficiente para pinar a resolução de apelidos de fábrica na
 * busca, no `encontrarOuCriar` e nas sugestões de digitação.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogoRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var repositorio: CatalogoRepositorio

    /** Semente mínima: Tangerina responde por mexerica, bergamota e o alias tang. */
    private val semente = CatalogoSemente(
        """
        SETOR|1|Mercado|#1E8E5A
        SUB|1|Hortifruti|1
        ITEM|Tangerina|1|mexerica,bergamota
        ALIAS|tang|Tangerina
        """.trimIndent(),
    )

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        repositorio = repositorioComLixeira()
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- auxiliares -------------------------------------------------------------------

    private fun repositorioComLixeira(): CatalogoRepositorio = CatalogoRepositorio(
        categoriaDao = banco.categoriaDao(),
        produtoDao = banco.produtoDao(),
        memoriaDao = banco.memoriaCategoriaDao(),
        decisaoDoParserDao = banco.decisaoDoParserDao(),
        catalogoSemente = semente,
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

    private suspend fun produto(
        nome: String,
        categoriaId: Long,
        codigoBarras: String? = null,
        validade: LocalDate? = null,
    ): Produto = repositorio.salvarProduto(
        Produto(
            nome = nome,
            nomeNormalizado = "",
            categoriaId = categoriaId,
            codigoBarras = codigoBarras,
            dataValidade = validade,
        ),
    )

    // --- categorias ---------------------------------------------------------------------

    @Test
    fun `criarCategoria empilha a ordem a partir da maior existente e nasce do usuario`() = runBlocking {
        val maiorPadrao = banco.categoriaDao().listarTodas().maxOf { it.ordemPadrao }

        val primeira = repositorio.criarCategoria("  Marcas próprias  ")
        val segunda = repositorio.criarCategoria("Importados")

        val nomes = banco.categoriaDao().listarTodas().associateBy { it.id }
        // O máximo de fábrica é 999, então a ordem nova bate no TETO de 998
        // (coerceAtMost) - duas categorias criadas podem dividir o mesmo teto.
        assertEquals(minOf(maiorPadrao + 5, 998), nomes.getValue(primeira).ordemPadrao)
        assertEquals(minOf(maiorPadrao + 10, 998), nomes.getValue(segunda).ordemPadrao)
        assertEquals("Marcas próprias", nomes.getValue(primeira).nome) // aparado
        assertEquals(OrigemCategoria.USUARIO.name, nomes.getValue(primeira).origem)
    }

    @Test
    fun `renomearCategoria apara o nome antes de gravar`() = runBlocking {
        val id = repositorio.criarCategoria("Bazar")

        val categoria = repositorio.listarCategorias().single { it.id == id }
        repositorio.renomearCategoria(categoria, "  Limpeza  ")

        assertEquals("Limpeza", banco.categoriaDao().listarTodas().single { it.id == id }.nome)
    }

    @Test
    fun `removerCategoriaDoUsuario com lixeira envia e sem apaga direto, padrao fica`() = runBlocking {
        val padraoId = banco.categoriaDao().listarTodas().first { it.origem == "PADRAO" }.id
        val doUsuario = repositorio.criarCategoria("Bazar")
        val outra = repositorio.criarCategoria("Importados")

        repositorio.removerCategoriaDoUsuario(doUsuario) // com lixeira

        assertTrue(banco.categoriaDao().listarTodas().none { it.id == doUsuario })
        assertEquals(1, banco.lixeiraDao().contar())
        val semLixeira = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
            catalogoSemente = semente,
            lixeira = null,
        )
        semLixeira.removerCategoriaDoUsuario(outra) // sem lixeira
        assertTrue(banco.categoriaDao().listarTodas().none { it.id == outra })
        assertEquals(1, banco.lixeiraDao().contar()) // a direta não passa pela lixeira

        semLixeira.removerCategoriaDoUsuario(padraoId) // padrão é imutável
        assertNotNull(banco.categoriaDao().listarTodas().single { it.id == padraoId })
    }

    @Test
    fun `reordenarCategorias grava multiplos de 10 na ordem recebida`() = runBlocking {
        val ids = banco.categoriaDao().listarTodas().take(3).map { it.id }
        val invertidos = ids.reversed()

        repositorio.reordenarCategorias(invertidos)

        val ordens = banco.categoriaDao().listarTodas().associateBy { it.id }
        invertidos.forEachIndexed { posicao, id -> assertEquals((posicao + 1) * 10, ordens.getValue(id).ordemPadrao) }
    }

    @Test
    fun `restaurarOrdemPadrao devolve as padroes a fabrica e manda usuario pro fim`() = runBlocking {
        val ids = banco.categoriaDao().listarTodas().map { it.id }
        repositorio.reordenarCategorias(ids.reversed())
        val hortifruti = banco.categoriaDao().listarTodas().single { it.chave == "hortifruti" }.id
        val doUsuario = repositorio.criarCategoria("Bazar")

        repositorio.restaurarOrdemPadrao()

        assertEquals(10, banco.categoriaDao().listarTodas().single { it.id == hortifruti }.ordemPadrao) // 1ª de fábrica
        assertTrue(banco.categoriaDao().listarTodas().single { it.id == doUsuario }!!.ordemPadrao >= 900)
    }

    // --- produtos -----------------------------------------------------------------------

    @Test
    fun `salvarProduto normaliza o nome e devolve o id definitivo`() = runBlocking {
        val salvo = repositorio.salvarProduto(Produto(nome = "Arroz Integral", nomeNormalizado = "", categoriaId = 1L))

        assertTrue(salvo.id > 0)
        assertEquals("arroz integral", salvo.nomeNormalizado)
        assertEquals(salvo.id, repositorio.produtoPorId(salvo.id)!!.id)
    }

    @Test
    fun `marcarProdutoFavorito marca, desmarca e ignora id inexistente`() = runBlocking {
        val produto = produto("Arroz", categoriaId = 1L)

        repositorio.marcarProdutoFavorito(produto.id, true)
        assertEquals(1, repositorio.produtosFavoritos.first().size)
        repositorio.marcarProdutoFavorito(produto.id, false)
        assertTrue(repositorio.produtosFavoritos.first().isEmpty())
        repositorio.marcarProdutoFavorito(999L, true) // não deve lançar
    }

    @Test
    fun `contagemPorCategoria conta os produtos de cada uma`() = runBlocking {
        produto("Arroz", categoriaId = 1L)
        produto("Feijão", categoriaId = 1L)
        produto("Sabão", categoriaId = 2L)

        val contagem = repositorio.contagemPorCategoria()

        assertEquals(2, contagem.getValue(1L))
        assertEquals(1, contagem.getValue(2L))
    }

    @Test
    fun `definirPesoMedioEstimado grava, apaga com null e ignora inexistente`() = runBlocking {
        val produto = produto("Banana", categoriaId = 1L)

        repositorio.definirPesoMedioEstimado(produto.id, BigDecimal("0.180"))
        assertEquals(0, BigDecimal("0.180").compareTo(repositorio.produtoPorId(produto.id)!!.pesoMedioEstimadoEmBase!!))

        repositorio.definirPesoMedioEstimado(produto.id, null)
        assertNull(repositorio.produtoPorId(produto.id)!!.pesoMedioEstimadoEmBase)
        repositorio.definirPesoMedioEstimado(999L, BigDecimal("1.0")) // não deve lançar
    }

    @Test
    fun `produtosPorIds de lista vazia devolve vazio e com ids devolve os produtos`() = runBlocking {
        val a = produto("Arroz", categoriaId = 1L)
        val b = produto("Feijão", categoriaId = 1L)

        assertTrue(repositorio.produtosPorIds(emptyList()).isEmpty())
        assertEquals(setOf(a.id, b.id), repositorio.produtosPorIds(listOf(a.id, b.id)).map { it.id }.toSet())
    }

    @Test
    fun `produtoPorCodigoBarras acha pelo codigo e devolve null para codigo desconhecido`() = runBlocking {
        val salvo = produto("Chocolate", categoriaId = 1L, codigoBarras = "7891234567890")

        assertEquals(salvo.id, repositorio.produtoPorCodigoBarras("7891234567890")!!.id)
        assertNull(repositorio.produtoPorCodigoBarras("000"))
    }

    @Test
    fun `validadesProximas inclui vencidos e dentro da janela e exclui longe e sem data`() = runBlocking {
        val hoje = LocalDate.now()
        produto("Iogurte vencido", categoriaId = 1L, validade = hoje.minusDays(1))
        val perto = produto("Iogurte", categoriaId = 1L, validade = hoje.plusDays(10))
        produto("Macarrão", categoriaId = 1L, validade = hoje.plusDays(60))
        produto("Sal", categoriaId = 1L, validade = null)

        val nomes = repositorio.validadesProximas(30).map { it.nome }.toSet()

        assertTrue("Iogurte vencido" in nomes)
        assertTrue(perto.nome in nomes)
        assertFalse("Macarrão" in nomes)
        assertFalse("Sal" in nomes)
    }

    // --- busca e encontrarOuCriar ---------------------------------------------------------

    @Test
    fun `buscarProdutos de termo em branco devolve vazio`() = runBlocking {
        assertTrue(repositorio.buscarProdutos("   ").isEmpty())
    }

    @Test
    fun `buscarProdutos acha pelo nome e respeita o limite`() = runBlocking {
        produto("Leite integral", categoriaId = 1L)
        produto("Leite desnatado", categoriaId = 1L)
        produto("Leite condensado", categoriaId = 1L)

        assertEquals(2, repositorio.buscarProdutos("leite", limite = 2).size)
        assertEquals(3, repositorio.buscarProdutos("leite", limite = 20).size)
    }

    @Test
    fun `buscarProdutos completa com o produto local quando o termo e sinonimo do acervo`() = runBlocking {
        val tangerina = produto("Tangerina", categoriaId = 1L)

        val achados = repositorio.buscarProdutos("mexerica")

        assertEquals(listOf(tangerina.id), achados.map { it.id })
    }

    @Test
    fun `encontrarOuCriar acha primeiro pelo codigo de barras`() = runBlocking {
        val comCodigo = produto("Chocolate", categoriaId = 1L, codigoBarras = "789")

        val achado = repositorio.encontrarOuCriar("Outro nome", categoriaId = 2L, codigoBarras = "789")

        assertEquals(comCodigo.id, achado.id)
        assertEquals(1, repositorio.contarProdutos())
    }

    @Test
    fun `encontrarOuCriar reusa o produto pelo nome normalizado`() = runBlocking {
        val primeiro = repositorio.encontrarOuCriar("Arroz", categoriaId = 1L)
        val segundo = repositorio.encontrarOuCriar("arroz", categoriaId = 1L)

        assertEquals(primeiro.id, segundo.id)
        assertEquals(1, repositorio.contarProdutos())
    }

    @Test
    fun `encontrarOuCriar nasce com o nome canonico do acervo pelo apelido e depois reusa`() = runBlocking {
        val peloApelido = repositorio.encontrarOuCriar("mexerica", categoriaId = 1L)
        assertEquals("Tangerina", peloApelido.nome)
        assertEquals("tangerina", peloApelido.nomeNormalizado)

        val peloNome = repositorio.encontrarOuCriar("tangerina", categoriaId = 1L)
        assertEquals(peloApelido.id, peloNome.id) // nunca duplica o item do mercado
        assertEquals(1, repositorio.contarProdutos())
    }

    @Test
    fun `encontrarOuCriar sem semente cria com o nome digitado capitalizado`() = runBlocking {
        val semSemente = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
            catalogoSemente = null,
            lixeira = null,
        )

        val criado = semSemente.encontrarOuCriar("arroz integral", categoriaId = 1L)

        assertEquals("Arroz Integral", criado.nome)
        assertEquals("arroz integral", criado.nomeNormalizado)
    }

    @Test
    fun `removerProduto com lixeira envia e sem lixeira apaga direto`() = runBlocking {
        val a = produto("Arroz", categoriaId = 1L)
        val b = produto("Feijão", categoriaId = 1L)

        repositorio.removerProduto(a.id) // com lixeira
        assertNull(repositorio.produtoPorId(a.id))
        assertEquals(1, banco.lixeiraDao().contar())

        val semLixeira = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
            catalogoSemente = semente,
            lixeira = null,
        )
        semLixeira.removerProduto(b.id) // sem lixeira
        assertNull(repositorio.produtoPorId(b.id))
        assertEquals(1, banco.lixeiraDao().contar()) // a direta não passa pela lixeira
    }

    // --- memorizacao ---------------------------------------------------------------------

    @Test
    fun `lembrarCategoria grava normalizado, reforco incrementa as vezes e branco ignora`() = runBlocking {
        repositorio.lembrarCategoria("Banana Prata", categoriaId = 3L)
        repositorio.lembrarCategoria("banana prata", categoriaId = 3L)

        val memoria = repositorio.memoriaDeCategorias()
        assertEquals(3L, memoria.getValue("banana prata"))
        assertEquals(2, banco.memoriaCategoriaDao().porNome("banana prata")!!.vezes)

        repositorio.lembrarCategoria("   ", categoriaId = 3L) // chave em branco não grava
        assertEquals(1, banco.memoriaCategoriaDao().listarTodas().size)
    }

    @Test
    fun `esquecerCategorias limpa a memoria`() = runBlocking {
        repositorio.lembrarCategoria("Banana", categoriaId = 3L)

        repositorio.esquecerCategorias()

        assertTrue(repositorio.memoriaDeCategorias().isEmpty())
    }

    @Test
    fun `lembrarDecisaoDoParser grava confirmado com valor e rejeitado sem, sempre minusculo`() = runBlocking {
        repositorio.lembrarDecisaoDoParser("LEITE", BigDecimal("4.50"))
        repositorio.lembrarDecisaoDoParser("Ovos", null)

        val memoria = repositorio.memoriaDoParser()
        assertEquals(0, BigDecimal("4.50").compareTo(memoria.confirmados.getValue("leite")))
        assertTrue("ovos" in memoria.rejeitados)
    }

    @Test
    fun `reforco de decisao incrementa as vezes e esquecer remove`() = runBlocking {
        repositorio.lembrarDecisaoDoParser("leite", BigDecimal("4.50"))
        repositorio.lembrarDecisaoDoParser("leite", BigDecimal("4.50"))
        repositorio.lembrarDecisaoDoParser("ovos", null)

        assertEquals(2, repositorio.decisoesDoParser().single { it.termo == "leite" }.vezes)

        repositorio.esquecerDecisaoDoParser("LEITE") // chaves sempre minusculas
        assertTrue(repositorio.decisoesDoParser().none { it.termo == "leite" })
        repositorio.esquecerDecisoesDoParser()
        assertTrue(repositorio.decisoesDoParser().isEmpty())
    }

    @Test
    fun `decisoesDoParser vem do mais recente para o mais antigo`() = runBlocking {
        repositorio.lembrarDecisaoDoParser("leite", BigDecimal("4.50"))
        Thread.sleep(10) // garante atualizadoEm distinto
        repositorio.lembrarDecisaoDoParser("ovos", null)

        assertEquals("ovos", repositorio.decisoesDoParser().first().termo)
        assertEquals("leite", repositorio.decisoesDoParser().last().termo)
    }

    // --- sugestoes -----------------------------------------------------------------------

    @Test
    fun `sugestoesDeDigitacao de termo em branco devolve vazio`() = runBlocking {
        assertTrue(repositorio.sugestoesDeDigitacao("   ").isEmpty())
    }

    @Test
    fun `sugestoesDeDigitacao une Room e acervo sem duplicar pelo nome normalizado`() = runBlocking {
        produto("Tangerina", categoriaId = 1L) // já existe na Room com o nome canônico

        val sugestoes = repositorio.sugestoesDeDigitacao("tang")

        assertEquals(listOf("Tangerina"), sugestoes) // Room não acha "tang"; acervo traz 1x só
    }

    @Test
    fun `sinonimosDe resolve pelo nome oficial e pelo apelido sem citar o proprio nome`() = runBlocking {
        assertEquals(listOf("mexerica", "bergamota"), repositorio.sinonimosDe("Tangerina"))
        assertEquals(listOf("bergamota"), repositorio.sinonimosDe("Mexerica")) // apelido não vira sinônimo de si
        assertTrue(repositorio.sinonimosDe("Arroz desconhecido").isEmpty())
    }
}

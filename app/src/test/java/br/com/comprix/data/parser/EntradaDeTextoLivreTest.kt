package br.com.comprix.data.parser

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.parser.ClasseDeItem
import br.com.comprix.domain.parser.Confianca
import br.com.comprix.domain.parser.GatilhoDeSugestao
import br.com.comprix.domain.parser.ItemInterpretado
import br.com.comprix.domain.parser.OpcaoDeSugestao
import br.com.comprix.domain.parser.SugestaoNaoBloqueante
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * `EntradaDeTextoLivre`: a ponte parser -> banco, o fluxo nº 1 do app ("digitei,
 * entrou na lista"), testado de ponta a ponta com Room em memória.
 *
 * O parser puro já tem a especificação JSON (`EspecificacaoDoParserTest`); o que
 * fica aqui é o que só existe na ponte: texto em branco, a gravação do produto
 * no catálogo e do item na lista, a quantidade nula que vira 1 com o motivo na
 * observação, o modo de comparação do item pesável, a resposta do usuário à
 * sugestão gravada na tabela `decisoes_parser` e a adição em lote que ignora
 * item sem nome. As entradas do parser reutilizam casos exatos da especificação
 * (`uma duzia de ovos` é o caso `coletivos-01`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EntradaDeTextoLivreTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var catalogo: CatalogoRepositorio
    private lateinit var listas: ListaRepositorio
    private lateinit var entrada: EntradaDeTextoLivre

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // A semeadura das 14 categorias vive no callback do builder de ARQUIVO
        // (ComprixDatabase.construir); no in-memory entra pela mesma via manual.
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        catalogo = CatalogoRepositorio(
            categoriaDao = banco.categoriaDao(),
            produtoDao = banco.produtoDao(),
            memoriaDao = banco.memoriaCategoriaDao(),
            decisaoDoParserDao = banco.decisaoDoParserDao(),
        )
        listas = ListaRepositorio(
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            precoDao = banco.precoDao(),
        )
        entrada = EntradaDeTextoLivre(catalogo, listas)
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- interpretar -----------------------------------------------------------------

    @Test
    fun `texto em branco nao produz item`() = runBlocking {
        assertTrue(entrada.interpretar("").isEmpty())
        assertTrue(entrada.interpretar("   \n  \n\t").isEmpty())
    }

    @Test
    fun `cada linha e uma entrada independente`() = runBlocking {
        val itens = entrada.interpretar("uma duzia de ovos\nmeia duzia de ovo")
        assertEquals(2, itens.size)
        assertTrue("nome era ${itens[0].nomeItem}", itens[0].nomeItem.equals("ovos", ignoreCase = true))
        assertEquals(0, itens[0].quantidade!!.compareTo(BigDecimal(12)))
        assertTrue("nome era ${itens[1].nomeItem}", itens[1].nomeItem.equals("ovo", ignoreCase = true))
        assertEquals(0, itens[1].quantidade!!.compareTo(BigDecimal(6)))
    }

    @Test
    fun `interpretarLinha devolve o primeiro item ou nulo`() = runBlocking {
        assertEquals("Ovos", entrada.interpretarLinha("uma duzia de ovos")?.nomeItem)
        assertNull(entrada.interpretarLinha("   "))
    }

    // --- adicionar -------------------------------------------------------------------

    @Test
    fun `adicionar grava o produto no catalogo e o item na lista`() = runBlocking {
        val listaId = listas.criarLista("Feira")
        val item = entrada.interpretarLinha("uma duzia de ovos")!!
        val itemId = entrada.adicionar(listaId, item)

        assertTrue("itemId era $itemId", itemId > 0)
        val gravado = banco.itemDao().listarDaLista(listaId).single()
        assertEquals(listaId, gravado.listaId)
        assertTrue("produtoId era ${gravado.produtoId}", gravado.produtoId > 0)
        assertEquals(0, BigDecimal(gravado.quantidade).compareTo(BigDecimal(12)))

        val produto = banco.produtoDao().porId(gravado.produtoId)!!
        // O nome chega capitalizado no catalogo; a categoria, semeada do acervo.
        assertTrue("nome era '${produto.nome}'", produto.nome.isNotBlank())
        assertTrue("categoriaId era ${produto.categoriaId} (categorias: " +
            banco.categoriaDao().listarTodas().joinToString(";") { "${it.id}:${it.chave}" } + ")",
            produto.categoriaId > 0)
    }

    @Test
    fun `adicionar o mesmo texto de novo reusa o produto existente`() = runBlocking {
        val listaId = listas.criarLista("Feira")
        val item = entrada.interpretarLinha("uma duzia de ovos")!!
        entrada.adicionar(listaId, item)
        entrada.adicionar(listaId, item)

        val itens = banco.itemDao().listarDaLista(listaId)
        assertEquals(2, itens.size)
        assertEquals(itens[0].produtoId, itens[1].produtoId)
        assertEquals(1, banco.produtoDao().listarTodos().size)
    }

    @Test
    fun `quantidade nula vira um e o motivo vai para a observacao`() = runBlocking {
        val listaId = listas.criarLista("Feira")
        val item = ItemInterpretado(
            nomeItem = "sal grosso", textoOriginal = "sal grosso", quantidade = null,
            confianca = Confianca.BAIXA, motivo = "nao deu para cravar a quantidade",
        )
        entrada.adicionar(listaId, item)

        val gravado = banco.itemDao().listarDaLista(listaId).single()
        assertEquals(0, gravado.quantidade.toBigDecimal().compareTo(BigDecimal.ONE))
        assertEquals("nao deu para cravar a quantidade", gravado.observacao)
    }

    @Test
    fun `item pesavel nasce comparando por peso e o conteudo por unidade`() = runBlocking {
        val listaId = listas.criarLista("Feira")
        entrada.adicionar(listaId, ItemInterpretado(
            nomeItem = "carne moida", textoOriginal = "carne moida",
            quantidade = BigDecimal(2), unidade = Unidade.QUILO,
            classeDoItem = ClasseDeItem.PESAVEL,
        ))
        entrada.adicionar(listaId, ItemInterpretado(
            nomeItem = "ovos", textoOriginal = "ovos",
            quantidade = BigDecimal(12), unidade = Unidade.DUZIA,
        ))

        val itens = banco.itemDao().listarDaLista(listaId)
        assertEquals(ModoComparacaoUnidade.POR_PESO.name, itens[0].modoComparacao)
        assertEquals(ModoComparacaoUnidade.POR_UNIDADE.name, itens[1].modoComparacao)
    }

    @Test
    fun `adicionarTodos ignora item sem nome e conta os que entraram`() = runBlocking {
        val listaId = listas.criarLista("Feira")
        val itens = listOf(
            entrada.interpretarLinha("uma duzia de ovos")!!,
            ItemInterpretado(nomeItem = "   ", textoOriginal = "   "),
        )
        assertEquals(1, entrada.adicionarTodos(listaId, itens))
        assertEquals(1, banco.itemDao().listarDaLista(listaId).size)
    }

    // --- responder e ignorar -----------------------------------------------------------

    private fun itemComSugestao(): ItemInterpretado = ItemInterpretado(
        nomeItem = "ovos", textoOriginal = "ovos", quantidade = null,
        sugestao = SugestaoNaoBloqueante(
            gatilho = GatilhoDeSugestao.G1, pergunta = "Quantas unidades?",
            opcoes = listOf(
                OpcaoDeSugestao(rotulo = "12", quantidade = BigDecimal(12)),
                OpcaoDeSugestao(rotulo = "Manter como está", manterComoEsta = true),
            ),
            termo = "duzia",
        ),
    )

    @Test
    fun `responder sem sugestao devolve o item e nao grava decisao`() = runBlocking {
        val item = ItemInterpretado(nomeItem = "ovos", textoOriginal = "ovos")
        val devolvido = entrada.responder(item, OpcaoDeSugestao(rotulo = "x"))
        assertEquals(item, devolvido)
        assertTrue(banco.decisaoDoParserDao().listarTodas().isEmpty())
    }

    @Test
    fun `manter como esta limpa a sugestao e grava rejeicao sem valor`() = runBlocking {
        val devolvido = entrada.responder(
            itemComSugestao(),
            OpcaoDeSugestao(rotulo = "Manter como está", manterComoEsta = true),
        )
        assertNull(devolvido.sugestao)
        val decisao = banco.decisaoDoParserDao().porTermo("duzia")
        assertNotNull(decisao)
        assertNull(decisao!!.valor)
    }

    @Test
    fun `confirmar a quantidade grava o valor aceito`() = runBlocking {
        entrada.responder(itemComSugestao(), OpcaoDeSugestao(rotulo = "12", quantidade = BigDecimal(12)))
        val decisao = banco.decisaoDoParserDao().porTermo("duzia")
        assertNotNull(decisao)
        assertTrue(decisao!!.valor != null && decisao.valor!!.contains("12"))
    }

    @Test
    fun `ignorar nao aprende nada`() {
        val devolvido = entrada.ignorar(itemComSugestao())
        assertNull(devolvido.sugestao)
    }
}

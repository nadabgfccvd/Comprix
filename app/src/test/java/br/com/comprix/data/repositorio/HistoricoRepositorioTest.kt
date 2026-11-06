package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.PontoGrafico
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * `HistoricoRepositorio`: o contrato do acervo de compras finalizadas e dos
 * números dos gráficos, de ponta a ponta com Room em memória.
 *
 * `registrar` é o fechamento da lista virando história: total pago, economia,
 * quantidade e o mapa de gastos por categoria (que viaja como JSON e volta
 * como `Map`). `remover` desvia para a Lixeira quando ela existe — a compra
 * apagada é recuperável por 30 dias como qualquer outro registro. `resumo`
 * é o `AnalisadorDeHistorico.resumir` aplicado ao fluxo observado (a janela
 * de meses filtra pelo mês da compra).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HistoricoRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var repositorio: HistoricoRepositorio

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

    private fun repositorioComLixeira(): HistoricoRepositorio = HistoricoRepositorio(
        compraDao = banco.compraDao(),
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

    /** Resultado da Calculadora com valores coerentes entre si. */
    private fun resultado(
        totalPago: String = "123.45",
        economia: String = "6.78",
        quantidadeItens: Int = 9,
        descricao: String = "Mercado A",
        estabelecimentoId: Long? = null,
        gastos: List<PontoGrafico> = listOf(
            PontoGrafico("Feira", BigDecimal("100.00")),
            PontoGrafico("Bazar", BigDecimal("23.45")),
        ),
    ) = CalculadoraDeCompra.ResultadoDaCompra(
        totalPago = BigDecimal(totalPago),
        economia = BigDecimal(economia),
        economiaPercentual = BigDecimal("5.2"),
        quantidadeItens = quantidadeItens,
        itensSemPreco = 1,
        totalSeComprasseNoMaisCaro = BigDecimal("130.23"),
        gastosPorCategoria = gastos,
        descricaoEstabelecimento = descricao,
        estabelecimentoPrincipalId = estabelecimentoId,
    )

    private fun zero(a: BigDecimal, b: BigDecimal) = assertEquals(0, a.compareTo(b))

    // --- registrar ----------------------------------------------------------------------

    @Test
    fun `registrar grava a compra com os dados do resultado e devolve o id`() = runBlocking {
        val momento = LocalDateTime.of(2026, 11, 6, 9, 40)

        val id = repositorio.registrar(null, "Mensal", resultado(), momento)

        val compra = repositorio.compra(id)!!
        assertNull(compra.listaId)
        assertEquals("Mensal", compra.nomeLista)
        assertEquals(momento, compra.data)
        zero(BigDecimal("123.45"), compra.totalPago)
        zero(BigDecimal("6.78"), compra.economia)
        assertEquals(9, compra.quantidadeItens)
        assertEquals("Mercado A", compra.descricaoEstabelecimento)
        assertNull(compra.estabelecimentoPrincipalId)
        assertEquals(2, compra.gastosPorCategoria.size)
        zero(BigDecimal("100.00"), compra.gastosPorCategoria.getValue("Feira"))
        zero(BigDecimal("23.45"), compra.gastosPorCategoria.getValue("Bazar"))
    }

    @Test
    fun `registrar com listaId grava a origem e o id da loja principal`() = runBlocking {
        val id = repositorio.registrar(42L, "Semanal", resultado(descricao = "Mercado B", estabelecimentoId = 7L))

        val compra = repositorio.compra(id)!!
        assertEquals(42L, compra.listaId)
        assertEquals(7L, compra.estabelecimentoPrincipalId)
        assertEquals("Mercado B", compra.descricaoEstabelecimento)
    }

    // --- leituras -----------------------------------------------------------------------

    @Test
    fun `compra de id inexistente devolve null`() = runBlocking {
        assertNull(repositorio.compra(999L))
    }

    @Test
    fun `listarCompras devolve todas do mais recente para o mais antigo`() = runBlocking {
        repositorio.registrar(null, "Antiga", resultado(), LocalDateTime.of(2026, 9, 1, 10, 0))
        repositorio.registrar(null, "Recente", resultado(), LocalDateTime.of(2026, 11, 1, 10, 0))

        val nomes = repositorio.listarCompras().map { it.nomeLista }

        assertEquals(listOf("Recente", "Antiga"), nomes)
    }

    @Test
    fun `compras emite o acervo observado`() = runBlocking {
        repositorio.registrar(null, "Mensal", resultado())

        val nomes = repositorio.compras.first().map { it.nomeLista }

        assertEquals(listOf("Mensal"), nomes)
    }

    // --- resumo -------------------------------------------------------------------------

    @Test
    fun `resumo sem compras devolve o resumo vazio`() = runBlocking {
        val resumo = repositorio.resumo(AnalisadorDeHistorico.Periodo.TUDO).first()

        assertTrue(resumo.vazio)
        assertEquals(0, resumo.quantidadeCompras)
        zero(BigDecimal.ZERO, resumo.totalGasto)
    }

    @Test
    fun `resumo soma gasto e ticket medio e a janela de meses filtra`() = runBlocking {
        val agora = LocalDateTime.now()
        repositorio.registrar(null, "No periodo", resultado(totalPago = "100.00"), agora)
        repositorio.registrar(null, "Fora", resultado(totalPago = "50.00"), agora.minusYears(1))

        val tudo = repositorio.resumo(AnalisadorDeHistorico.Periodo.TUDO).first()
        assertEquals(2, tudo.quantidadeCompras)
        zero(BigDecimal("150.00"), tudo.totalGasto)
        zero(BigDecimal("75.00"), tudo.ticketMedio)

        val tresMeses = repositorio.resumo(AnalisadorDeHistorico.Periodo.TRES_MESES).first()
        assertEquals(1, tresMeses.quantidadeCompras)
        zero(BigDecimal("100.00"), tresMeses.totalGasto)
    }

    @Test
    fun `resumo monta os graficos de categoria e mercado`() = runBlocking {
        repositorio.registrar(null, "Mensal", resultado(), LocalDateTime.now())

        val resumo = repositorio.resumo(AnalisadorDeHistorico.Periodo.TUDO).first()

        val porCategoria = resumo.gastosPorCategoria.associate { it.rotulo to it.valor }
        assertEquals(2, porCategoria.size)
        zero(BigDecimal("100.00"), porCategoria.getValue("Feira"))
        zero(BigDecimal("23.45"), porCategoria.getValue("Bazar"))
        val porMercado = resumo.gastosPorMercado.associate { it.rotulo to it.valor }
        zero(BigDecimal("123.45"), porMercado.getValue("Mercado A"))
    }

    // --- remover ------------------------------------------------------------------------

    @Test
    fun `remover com lixeira manda a compra para recuperacao`() = runBlocking {
        val id = repositorio.registrar(null, "Mensal", resultado())

        repositorio.remover(id)

        assertNull(repositorio.compra(id))
        val registro = banco.lixeiraDao().listarTodas().single()
        assertEquals("COMPRAS", registro.tipo)
        assertEquals(id, registro.refId)
        assertEquals("Mensal", registro.titulo)
    }

    @Test
    fun `remover sem lixeira apaga direto`() = runBlocking {
        val id = repositorio.registrar(null, "Mensal", resultado())
        val semLixeira = HistoricoRepositorio(compraDao = banco.compraDao(), lixeira = null)

        semLixeira.remover(id)

        assertNull(repositorio.compra(id))
        assertEquals(0, banco.lixeiraDao().contar())
    }

    // --- contrato de modelo -------------------------------------------------------------

    @Test
    fun `compra reconstruida preserva listaId, gastos e descricao`() = runBlocking {
        val id = repositorio.registrar(42L, "Semanal", resultado())

        val compra: CompraFinalizada = repositorio.compra(id)!!
        assertEquals(42L, compra.listaId)
        assertEquals(setOf("Feira", "Bazar"), compra.gastosPorCategoria.keys)
        assertTrue(compra.gastosPorCategoria.values.none { it.signum() < 0 })
        assertFalse(compra.descricaoEstabelecimento.isBlank())
    }
}

package br.com.comprix.domain.historico

import br.com.comprix.domain.historico.FiltroDeHistorico.ROTULO_SEM_LOJA
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do filtro de lojas do Historico: montagem das pastilhas, casamento
 * por rotulo e a intersecao com a busca amigavel. Tudo na JVM, sem Android -
 * a logica inteira mora em [FiltroDeHistorico], objeto puro.
 */
class FiltroDeHistoricoTest {

    private fun compra(
        nomeLista: String = "Compras da semana",
        descricao: String,
    ) = CompraFinalizada(
        listaId = 1L,
        nomeLista = nomeLista,
        data = LocalDateTime.of(2026, 11, 1, 10, 0),
        totalPago = BigDecimal("100.00"),
        economia = BigDecimal("10.00"),
        quantidadeItens = 5,
        descricaoEstabelecimento = descricao,
        estabelecimentoPrincipalId = 1L,
    )

    // ---------------------------------------------------------------------------------
    // lojas: pastilhas da linha
    // ---------------------------------------------------------------------------------

    @Test
    fun `pastilhas contam as compras de cada loja e ordenam por quantidade`() {
        val compras = listOf(
            compra(descricao = "Mercado do Bairro"),
            compra(descricao = "Mercado do Bairro"),
            compra(descricao = "Mercado do Bairro"),
            compra(descricao = "Hortifruti da Esquina"),
            compra(descricao = "Hortifruti da Esquina"),
            compra(descricao = "Açougue Central"),
        )
        val lojas = FiltroDeHistorico.lojas(compras)

        assertEquals(
            listOf(
                FiltroDeHistorico.LojaDoHistorico("Mercado do Bairro", 3),
                FiltroDeHistorico.LojaDoHistorico("Hortifruti da Esquina", 2),
                FiltroDeHistorico.LojaDoHistorico("Açougue Central", 1),
            ),
            lojas,
        )
    }

    @Test
    fun `empate de quantidade ordena em ordem alfabetica`() {
        val compras = listOf(
            compra(descricao = "Zeca Mercados"),
            compra(descricao = "Alpha Mercados"),
        )
        val rotulos = FiltroDeHistorico.lojas(compras).map { it.rotulo }

        assertEquals(listOf("Alpha Mercados", "Zeca Mercados"), rotulos)
    }

    @Test
    fun `compra multi-loja fica agrupada no rotulo combinado do cartao`() {
        val compras = listOf(
            compra(descricao = "Mercado do Bairro"),
            compra(descricao = "Mercado do Bairro + 2 loja(s)"),
        )
        val lojas = FiltroDeHistorico.lojas(compras).map { it.rotulo }

        // Rotulos distintos: o combinado e uma pastilha propria, fiel ao
        // que o cartao mostra.
        assertEquals(listOf("Mercado do Bairro", "Mercado do Bairro + 2 loja(s)"), lojas)
    }

    @Test
    fun `descricao vazia cai no rotulo de nao informado`() {
        val compras = listOf(compra(descricao = ""))
        val lojas = FiltroDeHistorico.lojas(compras)

        assertEquals(listOf(FiltroDeHistorico.LojaDoHistorico(ROTULO_SEM_LOJA, 1)), lojas)
    }

    @Test
    fun `lista vazia nao gera pastilhas`() {
        assertTrue(FiltroDeHistorico.lojas(emptyList()).isEmpty())
    }

    // ---------------------------------------------------------------------------------
    // correspondeLoja: casamento por rotulo exato
    // ---------------------------------------------------------------------------------

    @Test
    fun `loja nula passa todas as compras`() {
        val compras = listOf(
            compra(descricao = "Mercado do Bairro"),
            compra(descricao = "Hortifruti da Esquina"),
        )
        compras.forEach { assertTrue(FiltroDeHistorico.correspondeLoja(it, null)) }
    }

    @Test
    fun `loja selecionada casa so com o rotulo identico`() {
        val compra = compra(descricao = "Mercado do Bairro")

        assertTrue(FiltroDeHistorico.correspondeLoja(compra, "Mercado do Bairro"))
        assertFalse(FiltroDeHistorico.correspondeLoja(compra, "Mercado do Centro"))
        // Casamento e exato: nao e busca, o rotulo e um valor fechado.
        assertFalse(FiltroDeHistorico.correspondeLoja(compra, "mercado do bairro"))
    }

    @Test
    fun `descricao vazia casa com o rotulo de nao informado`() {
        val compra = compra(descricao = "")

        assertTrue(FiltroDeHistorico.correspondeLoja(compra, ROTULO_SEM_LOJA))
        assertFalse(FiltroDeHistorico.correspondeLoja(compra, "Mercado do Bairro"))
    }

    // ---------------------------------------------------------------------------------
    // comprasVisiveis: intersecao loja E busca
    // ---------------------------------------------------------------------------------

    @Test
    fun `sem filtros a lista inteira passa`() {
        val compras = listOf(
            compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro"),
            compra(nomeLista = "Churrasco", descricao = "Açougue Central"),
        )

        assertEquals(compras, FiltroDeHistorico.comprasVisiveis(compras, "", null))
    }

    @Test
    fun `so a loja selecionada passa`() {
        val mercado = compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro")
        val acougue = compra(nomeLista = "Churrasco", descricao = "Açougue Central")

        val visiveis = FiltroDeHistorico.comprasVisiveis(
            listOf(mercado, acougue),
            consulta = "",
            loja = "Mercado do Bairro",
        )

        assertEquals(listOf(mercado), visiveis)
    }

    @Test
    fun `so a busca filtra - mesma receita da Lixeira, sem acento e sem maiuscula`() {
        val mercado = compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro")
        val acougue = compra(nomeLista = "Churrasco", descricao = "Açougue Central")

        // "mercado" casa com a loja; "açougue" casa com a outra, mesmo sem acento.
        assertEquals(
            listOf(mercado),
            FiltroDeHistorico.comprasVisiveis(listOf(mercado, acougue), "mercado", null),
        )
        assertEquals(
            listOf(acougue),
            FiltroDeHistorico.comprasVisiveis(listOf(mercado, acougue), "acougue", null),
        )
        // Palavra do nome da lista tambem casa ("chur" acha "Churrasco").
        assertEquals(
            listOf(acougue),
            FiltroDeHistorico.comprasVisiveis(listOf(mercado, acougue), "chur", null),
        )
    }

    @Test
    fun `loja e busca se combinam com E`() {
        val compraDoMercado = compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro")
        val outraDoMercado = compra(nomeLista = "Limpeza", descricao = "Mercado do Bairro")
        val acougue = compra(nomeLista = "Churrasco", descricao = "Açougue Central")

        val visiveis = FiltroDeHistorico.comprasVisiveis(
            listOf(compraDoMercado, outraDoMercado, acougue),
            consulta = "semana",
            loja = "Mercado do Bairro",
        )

        assertEquals(listOf(compraDoMercado), visiveis)
    }

    @Test
    fun `combinacao sem resultado devolve lista vazia, nao erro`() {
        val compras = listOf(compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro"))

        assertTrue(
            FiltroDeHistorico.comprasVisiveis(compras, consulta = "inexistente", loja = "Mercado do Bairro")
                .isEmpty(),
        )
        assertTrue(
            FiltroDeHistorico.comprasVisiveis(compras, consulta = "", loja = "Loja Fantasma").isEmpty(),
        )
    }

    // ---------------------------------------------------------------------------------
    // comprasVisiveis: sinonimos (expansao da consulta)
    // Lambda local imita o contrato do CatalogoRepositorio.sinonimosDe:
    // bidirecional ("mexerica" <-> "tangerina") e vazia para termo sem acervo.
    // ---------------------------------------------------------------------------------

    /** Sinonimos de teste, no mesmo espírito do acervo de fabrica. */
    private val sinonimosDeTeste: (String) -> List<String> = { consulta ->
        when (TextoUtil.normalizar(consulta)) {
            "mexerica" -> listOf("tangerina", "bergamota")
            "tangerina" -> listOf("mexerica", "bergamota")
            else -> emptyList()
        }
    }

    @Test
    fun `consulta pelo sinonimo acha a compra pelo nome oficial`() {
        val tangerina = compra(nomeLista = "Tangerina para o suco", descricao = "Mercado do Bairro")
        val outra = compra(nomeLista = "Limpeza", descricao = "Mercado do Bairro")

        val visiveis = FiltroDeHistorico.comprasVisiveis(
            listOf(tangerina, outra),
            consulta = "mexerica",
            loja = null,
            candidatos = sinonimosDeTeste,
        )

        assertEquals(listOf(tangerina), visiveis)
    }

    @Test
    fun `consulta pelo nome oficial acha a compra gravada pelo sinonimo`() {
        val mexerica = compra(nomeLista = "Mexerica da feira", descricao = "Hortifruti da Esquina")

        val visiveis = FiltroDeHistorico.comprasVisiveis(
            listOf(mexerica),
            consulta = "tangerina",
            loja = null,
            candidatos = sinonimosDeTeste,
        )

        assertEquals(listOf(mexerica), visiveis)
    }

    @Test
    fun `consulta sem sinonimo no acervo se comporta como antes`() {
        val mercado = compra(nomeLista = "Compras da semana", descricao = "Mercado do Bairro")

        // Termo sem sinônimo: a busca direta segue valendo ("mercado" acha),
        // e a expansão devolve vazia sem atrapalhar.
        assertEquals(
            listOf(mercado),
            FiltroDeHistorico.comprasVisiveis(listOf(mercado), "mercado", null, sinonimosDeTeste),
        )
        assertTrue(
            FiltroDeHistorico.comprasVisiveis(
                listOf(mercado),
                "feijao",
                null,
                sinonimosDeTeste,
            ).isEmpty(),
        )
    }

    @Test
    fun `candidatos nulos mantem o comportamento original - regressao zero`() {
        val tangerina = compra(nomeLista = "Tangerina para o suco", descricao = "Mercado do Bairro")

        // Sem injetar candidatos, o sinônimo NÃO expande (comportamento de antes).
        assertTrue(
            FiltroDeHistorico.comprasVisiveis(listOf(tangerina), "mexerica", null).isEmpty(),
        )
        // E a busca direta continua igual.
        assertEquals(
            listOf(tangerina),
            FiltroDeHistorico.comprasVisiveis(listOf(tangerina), "tangerina", null),
        )
    }

    @Test
    fun `expansao e pela consulta inteira, nao por palavra solta`() {
        val tangerina = compra(nomeLista = "Tangerina para o suco", descricao = "Mercado do Bairro")

        // "suco de mexerica" não resolve como frase inteira no acervo, e a
        // regra deliberada é NÃO expandir palavra solta: a compra não aparece
        // (previsibilidade - a interseção com a loja segue "afinando").
        assertTrue(
            FiltroDeHistorico.comprasVisiveis(
                listOf(tangerina),
                "suco de mexerica",
                null,
                sinonimosDeTeste,
            ).isEmpty(),
        )
    }

    @Test
    fun `sinonimo e loja se combinam com E`() {
        val noMercado = compra(nomeLista = "Tangerina para o suco", descricao = "Mercado do Bairro")
        val noHortifruti = compra(nomeLista = "Tangerina para o suco", descricao = "Hortifruti da Esquina")

        val visiveis = FiltroDeHistorico.comprasVisiveis(
            listOf(noMercado, noHortifruti),
            consulta = "mexerica",
            loja = "Mercado do Bairro",
            candidatos = sinonimosDeTeste,
        )

        assertEquals(listOf(noMercado), visiveis)
    }
}

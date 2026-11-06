package br.com.comprix.data.local

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.domain.modelo.TipoTema
import br.com.comprix.domain.modelo.Unidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `Mapeadores`: o contrato da fronteira Room <-> dominio - a "sujeira" de
 * persistencia que o dominio nunca ve.
 *
 * Tudo em JVM puro: as entidades do Room sao data classes simples e o
 * `Mapeadores` nao toca em nada de Android. O que os testes congelam:
 *
 * 1. Dinheiro: centavos <-> reais com arredondamento HALF_EVEN (empate vai
 *    para o par, igual ao caixa, nunca "para cima" a favor do lojista).
 * 2. Datas: epoch <-> LocalDateTime/LocalDate, com null preservado.
 * 3. A decisao central do objeto: enum invalido (backup de versao futura)
 *    cai num valor SEGURO em vez de lancar - o app abre mesmo com dado
 *    estranho.
 * 4. Listas de enum em texto `|`: desconhecidos e vazios sao descartados.
 * 5. Decimal em texto: zeros a direita somem, ilegivel vira null (e a
 *    quantidade ilegivel do item vira 1, nunca 0 nem excecao).
 * 6. Tabela nutricional e gastos por categoria: JSON plano com chaves
 *    desconhecidas e valores ilegiveis descartados em silencio.
 * 7. Ida e volta completa de cada modelo (o que sai do dominio volta igual).
 */
class MapeadoresTest {

    // --- dinheiro --------------------------------------------------------------------

    @Test
    fun `centavos viram reais com duas casas`() {
        assertEquals(0, Mapeadores.centavosParaReais(1234L).compareTo(BigDecimal("12.34")))
        assertEquals(0, Mapeadores.centavosParaReais(0L).compareTo(BigDecimal("0.00")))
        assertEquals(0, Mapeadores.centavosParaReais(1L).compareTo(BigDecimal("0.01")))
        assertEquals(2, Mapeadores.centavosParaReais(1234L).scale())
    }

    @Test
    fun `reais viram centavos com arredondamento half even`() {
        // Empate exato vai para o digito PAR: 12.345 -> 1234 (nao 1235).
        assertEquals(1234L, Mapeadores.reaisParaCentavos(BigDecimal("12.345")))
        assertEquals(1236L, Mapeadores.reaisParaCentavos(BigDecimal("12.355")))
        assertEquals(0L, Mapeadores.reaisParaCentavos(BigDecimal("0.005")))
        assertEquals(2L, Mapeadores.reaisParaCentavos(BigDecimal("0.015")))
        assertEquals(268L, Mapeadores.reaisParaCentavos(BigDecimal("2.675")))
    }

    @Test
    fun `ida e volta de dinheiro preserva o valor`() {
        listOf("3.99", "0.01", "0.00", "999999.99", "12.50").forEach { texto ->
            val valor = BigDecimal(texto)
            assertEquals(valor, Mapeadores.centavosParaReais(Mapeadores.reaisParaCentavos(valor)))
        }
    }

    // --- datas -----------------------------------------------------------------------

    @Test
    fun `data nula continua nula dos dois lados`() {
        assertNull(Mapeadores.dataParaEpoch(null))
        assertNull(Mapeadores.epochParaData(null))
        assertEquals(LocalDate.of(1970, 1, 1), Mapeadores.epochParaData(0L))
    }

    @Test
    fun `ida e volta de data preserva o dia`() {
        val dia = LocalDate.of(2024, 2, 29) // bissexto
        assertEquals(dia, Mapeadores.epochParaData(Mapeadores.dataParaEpoch(dia)))
    }

    @Test
    fun `ida e volta de data e hora com precisao de milissegundo`() {
        val momento = LocalDateTime.of(2026, 11, 6, 14, 30, 15, 123_000_000)
        assertEquals(momento, Mapeadores.epochParaDataHora(Mapeadores.dataHoraParaEpoch(momento)))
    }

    // --- categoria -------------------------------------------------------------------

    @Test
    fun `categoria ida e volta`() {
        val categoria = Categoria(id = 4, nome = "Hortifruti", icone = "cesta", ordemPadrao = 2,
            origem = OrigemCategoria.PADRAO, chave = "hortifruti")
        assertEquals(categoria, Mapeadores.paraDominio(Mapeadores.paraEntidade(categoria)))
    }

    @Test
    fun `origem desconhecida cai em usuario`() {
        val entidade = Mapeadores.paraEntidade(Categoria(nome = "X", icone = "i", ordemPadrao = 1))
            .copy(origem = "FUTURA")
        assertEquals(OrigemCategoria.USUARIO, Mapeadores.paraDominio(entidade).origem)
        assertEquals(OrigemCategoria.PADRAO,
            Mapeadores.paraDominio(entidade.copy(origem = "PADRAO")).origem)
    }

    // --- produto ---------------------------------------------------------------------

    @Test
    fun `produto completo ida e volta`() {
        val produto = Produto(
            id = 7, nome = "Arroz integral", nomeNormalizado = "arroz integral", categoriaId = 3,
            codigoBarras = "7891234567890", unidadePadrao = Unidade.QUILO,
            infoNutricional = InfoNutricional("30 g", mapOf(Nutriente.SODIO to BigDecimal("0.30"))),
            selosAltoEm = listOf(SeloAltoEm.SODIO, SeloAltoEm.ACUCAR_ADICIONADO),
            ingredientes = "arroz, agua", gluten = IndicacaoGluten.CONTEM,
            alergenos = listOf(Alergeno.GLUTEN), dataValidade = LocalDate.of(2027, 3, 1),
            dataFabricacao = LocalDate.of(2026, 10, 1), pesoMedioEstimadoEmBase = BigDecimal("0.5"),
            favorito = true,
        )
        assertEquals(produto, Mapeadores.paraDominio(Mapeadores.paraEntidade(produto, atualizadoEm = 5L)))
        assertEquals(5L, Mapeadores.paraEntidade(produto, atualizadoEm = 5L).atualizadoEm)
    }

    @Test
    fun `peso medio perde zeros a direita ao virar texto`() {
        assertEquals("1.5", Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p",
            categoriaId = 1, pesoMedioEstimadoEmBase = BigDecimal("1.500"))).pesoMedioEstimadoEmBase)
        assertEquals("150", Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p",
            categoriaId = 1, pesoMedioEstimadoEmBase = BigDecimal("150"))).pesoMedioEstimadoEmBase)
        assertNull(Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p", categoriaId = 1))
            .pesoMedioEstimadoEmBase)
    }

    @Test
    fun `peso medio ilegivel vira nulo no dominio`() {
        val base = Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p", categoriaId = 1))
        assertNull(Mapeadores.paraDominio(base.copy(pesoMedioEstimadoEmBase = "abc")).pesoMedioEstimadoEmBase)
        assertNull(Mapeadores.paraDominio(base.copy(pesoMedioEstimadoEmBase = "")).pesoMedioEstimadoEmBase)
    }

    @Test
    fun `enums desconhecidos do produto caem no valor seguro`() {
        val estranho = Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p", categoriaId = 1))
            .copy(unidadePadrao = "JARDAS", gluten = "TALVEZ")
        val dominio = Mapeadores.paraDominio(estranho)
        assertEquals(Unidade.UNIDADE, dominio.unidadePadrao)
        assertEquals(IndicacaoGluten.INDETERMINADO, dominio.gluten)
    }

    @Test
    fun `selos e alergenos descartam nomes desconhecidos e vazios`() {
        val base = Mapeadores.paraEntidade(Produto(nome = "p", nomeNormalizado = "p", categoriaId = 1))
        val comSelos = base.copy(selosAltoEm = "SODIO||FANTASMA|GORDURA_SATURADA")
        assertEquals(listOf(SeloAltoEm.SODIO, SeloAltoEm.GORDURA_SATURADA),
            Mapeadores.paraDominio(comSelos).selosAltoEm)
        val comAlergenos = base.copy(alergenos = "GLUTEN|FANTASMA")
        assertEquals(listOf(Alergeno.GLUTEN), Mapeadores.paraDominio(comAlergenos).alergenos)
        assertTrue(Mapeadores.paraDominio(base.copy(alergenos = "|")).alergenos.isEmpty())
    }

    // --- lista e item ----------------------------------------------------------------

    @Test
    fun `lista ida e volta com e sem finalizadaEm`() {
        val base = ListaDeCompras(nome = "Feira",
            criadaEm = LocalDateTime.of(2026, 11, 1, 9, 0, 0, 123_000_000),
            favorita = true, orcamentoCentavos = 15000L)
        assertEquals(base, Mapeadores.paraDominio(Mapeadores.paraEntidade(base)))
        val finalizada = base.copy(finalizada = true,
            finalizadaEm = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 456_000_000))
        assertEquals(finalizada, Mapeadores.paraDominio(Mapeadores.paraEntidade(finalizada)))
    }

    @Test
    fun `item da lista ida e volta`() {
        val item = ItemDaLista(id = 9, listaId = 1, produtoId = 2, quantidade = BigDecimal("2.50"),
            unidade = Unidade.QUILO, pesoOuVolume = BigDecimal("0.5"), ehKit = true, itensPorKit = 12,
            comprado = true, ordemManual = 3, modoComparacao = ModoComparacaoUnidade.POR_PESO,
            observacao = null)
        assertEquals(item, Mapeadores.paraDominio(Mapeadores.paraEntidade(item)))
    }

    @Test
    fun `quantidade ilegivel vira um`() {
        val entidade = Mapeadores.paraEntidade(ItemDaLista(listaId = 1, produtoId = 2))
        assertEquals(0, Mapeadores.paraDominio(entidade.copy(quantidade = "abc")).quantidade
            .compareTo(BigDecimal.ONE))
        assertEquals(0, Mapeadores.paraDominio(entidade.copy(quantidade = "")).quantidade
            .compareTo(BigDecimal.ONE))
        assertEquals(0, Mapeadores.paraDominio(entidade.copy(quantidade = "0")).quantidade
            .compareTo(BigDecimal.ZERO))
        assertEquals(Unidade.UNIDADE,
            Mapeadores.paraDominio(entidade.copy(unidade = "PEDRA")).unidade)
        assertEquals(ModoComparacaoUnidade.POR_UNIDADE,
            Mapeadores.paraDominio(entidade.copy(modoComparacao = "X")).modoComparacao)
    }

    // --- estabelecimento e preco -------------------------------------------------------

    @Test
    fun `estabelecimento e preco ida e volta`() {
        val loja = Estabelecimento(id = 2, nome = "Mercado do Ze", corHex = "#1E8E5A")
        assertEquals(loja, Mapeadores.paraDominio(Mapeadores.paraEntidade(loja)))
        val preco = PrecoRegistrado(id = 8, itemDaListaId = 9, estabelecimentoId = 2,
            preco = BigDecimal("3.99"),
            dataRegistro = LocalDateTime.of(2026, 11, 5, 8, 0, 0, 789_000_000),
            disponivel = false)
        assertEquals(preco, Mapeadores.paraDominio(Mapeadores.paraEntidade(preco)))
    }

    // --- compra e gastos por categoria -------------------------------------------------

    @Test
    fun `compra ida e volta com gastos por categoria`() {
        val compra = CompraFinalizada(id = 5, listaId = null, nomeLista = "Mes",
            data = LocalDateTime.of(2026, 11, 5, 18, 0, 0, 123_000_000),
            totalPago = BigDecimal("123.45"), economia = BigDecimal("0.00"), quantidadeItens = 10,
            descricaoEstabelecimento = "2 lojas", estabelecimentoPrincipalId = null,
            gastosPorCategoria = mapOf("Frutas" to BigDecimal("12.50"), "Limpeza" to BigDecimal("0.01")))
        assertEquals(compra, Mapeadores.paraDominio(Mapeadores.paraEntidade(compra)))
    }

    @Test
    fun `gastos por categoria com valor ilegivel sao descartados`() {
        val compra = Mapeadores.paraDominio(
            Mapeadores.paraEntidade(
                CompraFinalizada(listaId = 1, nomeLista = "x",
                    data = LocalDateTime.of(2026, 11, 5, 18, 0), totalPago = BigDecimal.ONE,
                    economia = BigDecimal.ZERO, quantidadeItens = 1, descricaoEstabelecimento = "loja",
                    estabelecimentoPrincipalId = null),
            ).copy(gastosPorCategoriaJson = "{\"Frutas\":\"abc\",\"Limpeza\":\"250\"}"),
        )
        assertEquals(mapOf("Limpeza" to BigDecimal("2.50")), compra.gastosPorCategoria)
    }

    // --- tabela nutricional ------------------------------------------------------------

    @Test
    fun `tabela nula ou vazia vira json nulo`() {
        assertNull(Mapeadores.infoNutricionalParaJson(null))
        assertNull(Mapeadores.infoNutricionalParaJson(InfoNutricional()))
    }

    @Test
    fun `tabela nutricional ida e volta`() {
        val tabela = InfoNutricional("30 g", mapOf(
            Nutriente.VALOR_ENERGETICO to BigDecimal("12.50"),
            Nutriente.SODIO to BigDecimal("0.30")))
        val json = Mapeadores.infoNutricionalParaJson(tabela)
        assertNotNull(json)
        assertEquals(tabela, Mapeadores.infoNutricionalDeJson(json))
    }

    @Test
    fun `tabela com chaves desconhecidas e valores ilegiveis caem fora`() {
        val json = "{\"porcao\":\"30 g\",\"oxigenio\":\"5\",\"energia\":\"abc\",\"carboidratos\":\"10\"}"
        val tabela = Mapeadores.infoNutricionalDeJson(json)
        assertEquals("30 g", tabela?.porcaoDescricao)
        assertEquals(mapOf(Nutriente.CARBOIDRATOS to BigDecimal("10")), tabela?.valores)
        assertEquals(InfoNutricional("30 g"), Mapeadores.infoNutricionalDeJson("{\"porcao\":\"30 g\"}"))
        assertNull(Mapeadores.infoNutricionalDeJson("isto nao e json"))
        assertNull(Mapeadores.infoNutricionalDeJson(null))
        assertNull(Mapeadores.infoNutricionalDeJson("{\"energia\":\"abc\"}"))
    }

    // --- configuracoes -----------------------------------------------------------------

    @Test
    fun `configuracoes nulas viram padroes`() {
        val nula: ConfiguracoesEntity? = null
        val padroes = Mapeadores.paraDominio(nula)
        assertEquals(TipoTema.SISTEMA, padroes.tema)
        assertFalse(padroes.altoContraste)
        assertEquals(100, padroes.escalaDaFonte)
        assertFalse(padroes.coresDinamicas)
        assertTrue(padroes.sons)
        assertTrue(padroes.vibracao)
        assertFalse(padroes.modoTecnico)
        assertFalse(padroes.onboardingConcluido)
        assertEquals(Nutriente.selecaoPadrao(), padroes.nutrientesComparados)
        assertTrue(padroes.dicasVistas.isEmpty())
        assertEquals(0L, padroes.ultimoBackupMs)
        assertNull(padroes.metaEconomiaCentavos)
    }

    @Test
    fun `escala da fonte e presa entre 100 e 160 nos dois sentidos`() {
        assertEquals(160, Mapeadores.paraEntidade(ConfiguracoesApp(escalaDaFonte = 200)).escalaDaFonte)
        assertEquals(100, Mapeadores.paraEntidade(ConfiguracoesApp(escalaDaFonte = 50)).escalaDaFonte)
        val acima = Mapeadores.paraEntidade(ConfiguracoesApp()).copy(escalaDaFonte = 180)
        assertEquals(160, Mapeadores.paraDominio(acima).escalaDaFonte)
        val abaixo = Mapeadores.paraEntidade(ConfiguracoesApp()).copy(escalaDaFonte = 10)
        assertEquals(100, Mapeadores.paraDominio(abaixo).escalaDaFonte)
    }

    @Test
    fun `tema desconhecido e nutrientes vazios voltam ao padrao`() {
        val entidade = Mapeadores.paraEntidade(ConfiguracoesApp(tema = TipoTema.ESCURO))
            .copy(tema = "NEON", nutrientesComparados = "")
        val dominio = Mapeadores.paraDominio(entidade)
        assertEquals(TipoTema.SISTEMA, dominio.tema)
        assertEquals(Nutriente.selecaoPadrao(), dominio.nutrientesComparados)
        // A coluna guarda NOME de enum ("VALOR_ENERGETICO|SODIO"), nao a chave
        // do JSON ("energia"): nomes validos passam, desconhecidos caem fora.
        val parcial = entidade.copy(nutrientesComparados = "VALOR_ENERGETICO|FANTASMA|SODIO")
        assertEquals(listOf(Nutriente.VALOR_ENERGETICO, Nutriente.SODIO),
            Mapeadores.paraDominio(parcial).nutrientesComparados)
        // So nome desconhecido (ou chave do JSON, que nao e nome) -> padrao.
        val soDesconhecidos = entidade.copy(nutrientesComparados = "energia|fantasma")
        assertEquals(Nutriente.selecaoPadrao(), Mapeadores.paraDominio(soDesconhecidos).nutrientesComparados)
    }

    @Test
    fun `configuracoes ida e volta`() {
        val config = ConfiguracoesApp(tema = TipoTema.ESCURO, altoContraste = true, escalaDaFonte = 125,
            coresDinamicas = true, sons = false, vibracao = false, modoTecnico = true,
            onboardingConcluido = true,
            nutrientesComparados = listOf(Nutriente.FERRO, Nutriente.SODIO),
            dicasVistas = setOf("dica1", "dica2"), ultimoBackupMs = 1234567890L,
            metaEconomiaCentavos = 5000L)
        assertEquals(config, Mapeadores.paraDominio(Mapeadores.paraEntidade(config)))
    }

    // --- perfil e alergias customizadas -------------------------------------------------

    @Test
    fun `perfil nulo e alergenos desconhecidos caem fora`() {
        val nula: PerfilRestricoesEntity? = null
        assertEquals(PerfilRestricoes(), Mapeadores.paraDominio(nula))
        val entidade = Mapeadores.paraEntidade(PerfilRestricoes())
            .copy(alergenos = "GLUTEN|FANTASMA|LEITE", semGluten = true)
        assertEquals(PerfilRestricoes(alergenos = setOf(Alergeno.GLUTEN, Alergeno.LEITE), semGluten = true),
            Mapeadores.paraDominio(entidade))
        assertTrue(Mapeadores.paraDominio(Mapeadores.paraEntidade(PerfilRestricoes())
            .copy(alergenos = "FANTASMA")).alergenos.isEmpty())
    }

    @Test
    fun `alergia customizada apara o nome e descarta palavras vazias`() {
        // As conversoes de alergia customizada sao extensoes-membro: no
        // producao e no teste entram via with(Mapeadores).
        val modelo = AlergiaCustomizada(id = 3, nome = " Leite ",
            palavras = listOf("ovos", "", "castanha"))
        val entidade = with(Mapeadores) { modelo.paraEntidade() }
        assertEquals("Leite", entidade.nome)
        assertEquals("ovos||castanha", entidade.palavras)
        val deVolta = with(Mapeadores) { entidade.copy(id = 3).paraModelo() }
        assertEquals(AlergiaCustomizada(id = 3, nome = "Leite", palavras = listOf("ovos", "castanha")),
            deVolta)
        val comVazias = with(Mapeadores) {
            AlergiaCustomizada(nome = "x").paraEntidade().copy(palavras = "a||b").paraModelo()
        }
        assertEquals(listOf("a", "b"), comVazias.palavras)
    }
}

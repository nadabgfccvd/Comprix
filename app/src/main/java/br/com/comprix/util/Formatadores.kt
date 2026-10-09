package br.com.comprix.util

import br.com.comprix.domain.modelo.Dimensao
import br.com.comprix.domain.modelo.Unidade
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatacao pt-BR de dinheiro, quantidades, percentuais e datas.
 *
 * Tudo com separador decimal virgula e milhar ponto, independentemente do
 * locale do aparelho - o app e monolingue por decisao de escopo.
 */
object Formatadores {

    private val LOCALE_BR: Locale = Locale.forLanguageTag("pt-BR")
    private val SIMBOLOS = DecimalFormatSymbols(LOCALE_BR).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    private val MOEDA = DecimalFormat("#,##0.00", SIMBOLOS)
    private val QUANTIDADE = DecimalFormat("#,##0.###", SIMBOLOS)
    private val PERCENTUAL_INTEIRO = DecimalFormat("#,##0", SIMBOLOS)
    private val PERCENTUAL_DECIMAL = DecimalFormat("#,##0.0", SIMBOLOS)
    private val PRECO_UNITARIO = DecimalFormat("#,##0.00##", SIMBOLOS)

    private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_BR)
    private val FORMATO_DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yy", LOCALE_BR)
    private val FORMATO_DATA_SEM_ANO = DateTimeFormatter.ofPattern("dd/MM", LOCALE_BR)
    private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", LOCALE_BR)
    private val FORMATO_MES_ANO = DateTimeFormatter.ofPattern("MMM/yy", LOCALE_BR)
    private val FORMATO_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", LOCALE_BR)

    /** "R$ 1.234,50" */
    fun moeda(valor: BigDecimal?): String {
        val seguro = valor ?: BigDecimal.ZERO
        return "R$ " + MOEDA.format(seguro.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN))
    }

    /** "1.234,50" (sem o prefixo R$ - usado dentro de campos de edicao). */
    fun moedaSemSimbolo(valor: BigDecimal?): String =
        MOEDA.format((valor ?: BigDecimal.ZERO).setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN))

    /** "2" em vez de "2,000"; "1,5" em vez de "1,500". */
    fun quantidade(valor: BigDecimal?): String {
        val seguro = valor ?: return ""
        return QUANTIDADE.format(seguro.stripTrailingZeros())
    }

    /** "12%" acima de 10, "7,5%" abaixo - precisao onde ela importa. */
    fun percentual(valor: BigDecimal?): String {
        val seguro = valor ?: BigDecimal.ZERO
        val formatado = if (seguro.abs() < BigDecimal.TEN) {
            PERCENTUAL_DECIMAL.format(seguro)
        } else {
            PERCENTUAL_INTEIRO.format(seguro.setScale(0, RoundingMode.HALF_UP))
        }
        return "$formatado%"
    }

    /**
     * Converte o preco por unidade-base para a unidade que o usuario entende:
     * guardamos R$/g, mas mostramos "R$ 5,00/kg".
     */
    fun precoPorUnidade(precoPorUnidadeBase: BigDecimal?, unidade: Unidade): String {
        if (precoPorUnidadeBase == null) return ""
        val exibicao = unidade.unidadeDeExibicao
        val convertido = precoPorUnidadeBase.multiply(exibicao.fatorParaBase)
        return "R$ " + PRECO_UNITARIO.format(convertido.setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros()) +
            "/" + exibicao.sigla
    }

    /** "6 x 350 mL", "5 kg", "2 un" - a descricao curta da embalagem. */
    fun descricaoEmbalagem(
        quantidade: BigDecimal,
        unidade: Unidade,
        pesoOuVolume: BigDecimal?,
        itensPorKit: Int?,
    ): String {
        val partes = StringBuilder()
        val qtd = quantidade.stripTrailingZeros()
        val mostraMultiplicador = qtd.compareTo(BigDecimal.ONE) != 0
        if (mostraMultiplicador) partes.append(quantidade(qtd)).append(" x ")

        if (itensPorKit != null && itensPorKit > 1) {
            partes.append(itensPorKit).append(" x ")
        }
        if (pesoOuVolume != null && pesoOuVolume.signum() > 0) {
            partes.append(quantidade(pesoOuVolume)).append(" ").append(unidade.sigla)
        } else {
            partes.append(unidade.sigla)
        }
        return partes.toString()
    }

    fun data(valor: LocalDate?): String = valor?.format(FORMATO_DATA) ?: ""

    fun dataCurta(valor: LocalDate?): String = valor?.format(FORMATO_DATA_CURTA) ?: ""

    /**
     * "dd/MM" para LocalDate - versao curta do painel de validades
     * ("Venceu em 09/11"): a data esta sempre a 30 dias da de hoje, o ano
     * quase nunca importa na leitura rapida.
     */
    fun dataSemAno(valor: LocalDate?): String = valor?.format(FORMATO_DATA_SEM_ANO) ?: ""

    /**
     * "dd/MM" (dia e mes, SEM ano) - rotulo do prazo da Lixeira
     * ("Excluido permanentemente em 09/11"). A exclusao e sempre no maximo
     * 30 dias a frente, entao o ano e redundante na linha do cartao.
     */
    fun dataSemAno(valor: LocalDateTime?): String = valor?.format(FORMATO_DATA_SEM_ANO) ?: ""

    fun dataHora(valor: LocalDateTime?): String = valor?.format(FORMATO_DATA_HORA) ?: ""

    fun data(valor: LocalDateTime?): String = valor?.toLocalDate()?.format(FORMATO_DATA) ?: ""

    fun mesAno(valor: LocalDateTime?): String = valor?.format(FORMATO_MES_ANO) ?: ""

    /** "out/25" a partir de um mes calendario (graficos do historico). */
    fun mesAno(valor: YearMonth): String = valor.atDay(1).format(FORMATO_MES_ANO)

    fun carimboDeArquivo(valor: LocalDateTime = LocalDateTime.now()): String = valor.format(FORMATO_ARQUIVO)

    /**
     * Junta nomes em texto corrido: "Mercado 1", "Mercado 1 e Mercado 2",
     * "Mercado 1, Mercado 2 e Atacadão". Usado nos avisos de pendencia.
     */
    fun listaDeNomes(nomes: List<String>): String = when (nomes.size) {
        0 -> ""
        1 -> nomes.first()
        2 -> "${nomes[0]} e ${nomes[1]}"
        else -> nomes.dropLast(1).joinToString(", ") + " e " + nomes.last()
    }

    /** "Compras de outubro" - nome sugerido ao criar uma lista. */
    fun nomeSugeridoDeLista(agora: LocalDateTime = LocalDateTime.now()): String {
        val mes = when (agora.monthValue) {
            1 -> "janeiro"; 2 -> "fevereiro"; 3 -> "março"; 4 -> "abril"
            5 -> "maio"; 6 -> "junho"; 7 -> "julho"; 8 -> "agosto"
            9 -> "setembro"; 10 -> "outubro"; 11 -> "novembro"; else -> "dezembro"
        }
        return "Compras de $mes"
    }

    fun unidadeBase(dimensao: Dimensao): String = dimensao.unidadeBase
}

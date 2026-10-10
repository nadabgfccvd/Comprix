package br.com.comprix.domain.compra

import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.PontoGrafico
import br.com.comprix.domain.modelo.ResumoHistorico
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Transforma o historico de compras nos numeros e series dos graficos
 * (Secao 3.5): gasto mensal, divisao por categoria, ticket medio e economia
 * acumulada.
 *
 * Tudo e calculado em memoria a partir das compras ja finalizadas - nao ha
 * servico, nuvem nem agregacao remota.
 */
object AnalisadorDeHistorico {

    /** Janelas de tempo oferecidas na tela de historico. */
    enum class Periodo(val rotulo: String, val meses: Int) {
        TRES_MESES("3 meses", 3),
        SEIS_MESES("6 meses", 6),
        DOZE_MESES("12 meses", 12),
        TUDO("Tudo", 0),
    }

    /**
     * @param compras historico completo, em qualquer ordem.
     * @param periodo janela a considerar.
     * @param hoje injetavel para manter os testes deterministicos.
     */
    fun resumir(
        compras: List<CompraFinalizada>,
        periodo: Periodo = Periodo.SEIS_MESES,
        hoje: LocalDate = LocalDate.now(),
    ): ResumoHistorico {
        val limite = if (periodo.meses == 0) null else YearMonth.from(hoje).minusMonths((periodo.meses - 1).toLong())
        val consideradas = compras.filter { compra ->
            limite == null || !YearMonth.from(compra.data).isBefore(limite)
        }
        if (consideradas.isEmpty()) return ResumoHistorico()

        val totalGasto = consideradas.fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.totalPago) }
        val totalEconomizado = consideradas.fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.economia) }
        val ticketMedio = totalGasto.divide(
            BigDecimal(consideradas.size),
            Constantes.ESCALA_MOEDA,
            RoundingMode.HALF_EVEN,
        )

        return ResumoHistorico(
            totalGasto = totalGasto.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            totalEconomizado = totalEconomizado.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            quantidadeCompras = consideradas.size,
            ticketMedio = ticketMedio,
            gastosPorPeriodo = seriePorMes(consideradas, periodo, hoje),
            gastosPorCategoria = seriePorCategoria(consideradas),
            gastosPorMercado = seriePorMercado(consideradas),
        )
    }

    /**
     * Serie mensal continua: meses sem compra entram com zero, para o grafico
     * de barras nao "mentir" escondendo o mes em que nada foi gasto.
     */
    private fun seriePorMes(
        compras: List<CompraFinalizada>,
        periodo: Periodo,
        hoje: LocalDate,
    ): List<PontoGrafico> {
        val porMes = compras.groupBy { YearMonth.from(it.data) }
            .mapValues { (_, doMes) -> doMes.fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.totalPago) } }
        if (porMes.isEmpty()) return emptyList()

        val mesAtual = YearMonth.from(hoje)
        val primeiro = if (periodo.meses == 0) porMes.keys.min() else mesAtual.minusMonths((periodo.meses - 1).toLong())
        val meses = mutableListOf<YearMonth>()
        var cursor = if (primeiro.isAfter(mesAtual)) mesAtual else primeiro
        while (!cursor.isAfter(mesAtual)) {
            meses += cursor
            cursor = cursor.plusMonths(1)
        }

        val maior = porMes.values.maxOrNull() ?: BigDecimal.ZERO
        return meses.map { mes ->
            val valor = porMes[mes] ?: BigDecimal.ZERO
            PontoGrafico(
                rotulo = Formatadores.mesAno(mes),
                valor = valor.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
                destaque = valor.signum() > 0 && valor.compareTo(maior) == 0,
            )
        }
    }

    /** Divisao por categoria, da maior fatia para a menor. */
    private fun seriePorCategoria(compras: List<CompraFinalizada>): List<PontoGrafico> {
        val acumulado = LinkedHashMap<String, BigDecimal>()
        compras.forEach { compra ->
            compra.gastosPorCategoria.forEach { (categoria, valor) ->
                acumulado[categoria] = (acumulado[categoria] ?: BigDecimal.ZERO).add(valor)
            }
        }
        if (acumulado.isEmpty()) return emptyList()
        val maior = acumulado.values.max()
        return acumulado.entries
            .sortedByDescending { it.value }
            .map { (categoria, valor) ->
                PontoGrafico(
                    rotulo = categoria,
                    valor = valor.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
                    destaque = valor.compareTo(maior) == 0,
                )
            }
    }

    /**
     * Divisao por mercado ("onde o dinheiro foi"): soma o total pago por
     * [CompraFinalizada.descricaoEstabelecimento] - a mesma rotulagem que os
     * cartoes do historico mostram, inclusive o rotulo da compra mista.
     * Empate de rotulo em branco cai como "Não informado" para nao sumir do
     * grafico. Da maior fatia para a menor.
     */
    private fun seriePorMercado(compras: List<CompraFinalizada>): List<PontoGrafico> =
        compras
            .groupBy { it.descricaoEstabelecimento.ifBlank { "Não informado" } }
            .map { (mercado, grupo) ->
                PontoGrafico(
                    rotulo = mercado,
                    valor = grupo
                        .fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.totalPago) }
                        .setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
                )
            }
            .sortedWith(compareByDescending<PontoGrafico> { it.valor }.thenBy { it.rotulo })

    /** Variacao percentual do mes corrente contra o anterior (null se nao der). */
    fun variacaoMensal(compras: List<CompraFinalizada>, hoje: LocalDate = LocalDate.now()): BigDecimal? {
        val mesAtual = YearMonth.from(hoje)
        val mesPassado = mesAtual.minusMonths(1)
        val gastoAtual = compras.filter { YearMonth.from(it.data) == mesAtual }
            .fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.totalPago) }
        val gastoAnterior = compras.filter { YearMonth.from(it.data) == mesPassado }
            .fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.totalPago) }
        if (gastoAnterior.signum() <= 0) return null
        return gastoAtual.subtract(gastoAnterior)
            .multiply(BigDecimal("100"))
            .divide(gastoAnterior, 1, RoundingMode.HALF_EVEN)
    }

    /**
     * Economia somada das compras do MES CORRENTE, base do card "Meta do mes"
     * das Analiticas. O resumo por periodo ([resumir]) nao serve aqui porque a
     * meta e sempre calendario: independe do filtro escolhido na tela.
     */
    fun economiaDoMes(
        compras: List<CompraFinalizada>,
        hoje: LocalDate = LocalDate.now(),
    ): BigDecimal =
        compras.filter { YearMonth.from(it.data) == YearMonth.from(hoje) }
            .fold(BigDecimal.ZERO) { soma, compra -> soma.add(compra.economia) }
            .setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
}

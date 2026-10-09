package br.com.comprix.domain.compra

import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.PontoGrafico
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.util.Constantes
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fechamento da compra (Secao 4.5): quanto foi pago, quanto foi economizado e
 * para onde o dinheiro foi.
 *
 * ## Como a economia e medida
 * ```
 * economia = Σ (maior preço visto do item × quantidade) − total pago
 * ```
 * Ou seja: "se eu tivesse comprado tudo na loja mais cara que pesquisei,
 * gastaria X; gastei Y; economizei X − Y". Itens com preco em uma loja so nao
 * geram economia (nao houve comparacao). O resultado nunca e negativo: se o
 * usuario escolheu conscientemente um item mais caro, a economia daquele item
 * e zero, nunca um numero vermelho acusando a escolha.
 */
object CalculadoraDeCompra {

    data class ResultadoDaCompra(
        val totalPago: BigDecimal,
        val economia: BigDecimal,
        val economiaPercentual: BigDecimal,
        val quantidadeItens: Int,
        val itensSemPreco: Int,
        val totalSeComprasseNoMaisCaro: BigDecimal,
        val gastosPorCategoria: List<PontoGrafico>,
        val descricaoEstabelecimento: String,
        val estabelecimentoPrincipalId: Long?,
    )

    /**
     * @param itens itens marcados como comprados.
     * @param precos todos os precos registrados da lista (de todas as lojas).
     * @param escolhaPorItem loja escolhida para cada item; quando ausente, usa
     * o menor preco disponivel.
     */
    fun calcular(
        itens: List<ItemComProduto>,
        precos: List<PrecoRegistrado>,
        estabelecimentos: List<Estabelecimento>,
        categorias: Map<Long, Categoria>,
        escolhaPorItem: Map<Long, Long> = emptyMap(),
    ): ResultadoDaCompra {
        val precosPorItem = precos.groupBy { it.itemDaListaId }
        val porCategoria = LinkedHashMap<String, BigDecimal>()
        val lojasUsadas = LinkedHashMap<Long, Int>()

        var totalPago = BigDecimal.ZERO
        var totalMaisCaro = BigDecimal.ZERO
        var semPreco = 0

        itens.forEach { itemComProduto ->
            val disponiveis = precosPorItem[itemComProduto.item.id].orEmpty()
                .filter { it.disponivel && it.preco.signum() > 0 }
            if (disponiveis.isEmpty()) {
                semPreco++
                return@forEach
            }

            val escolhido = escolhaPorItem[itemComProduto.item.id]
                ?.let { lojaId -> disponiveis.firstOrNull { it.estabelecimentoId == lojaId } }
                ?: disponiveis.minByOrNull { it.preco }!!

            val pago = MotorDePrecos.totalDaLinha(escolhido.preco, itemComProduto.item)
            val maisCaro = MotorDePrecos.totalDaLinha(
                disponiveis.maxByOrNull { it.preco }!!.preco,
                itemComProduto.item,
            )

            totalPago = totalPago.add(pago)
            totalMaisCaro = totalMaisCaro.add(maisCaro)
            lojasUsadas[escolhido.estabelecimentoId] = (lojasUsadas[escolhido.estabelecimentoId] ?: 0) + 1

            val nomeCategoria = categorias[itemComProduto.produto.categoriaId]?.nome ?: "Outros"
            porCategoria[nomeCategoria] = (porCategoria[nomeCategoria] ?: BigDecimal.ZERO).add(pago)
        }

        val economia = totalMaisCaro.subtract(totalPago).max(BigDecimal.ZERO)
            .setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
        val percentual = if (totalMaisCaro.signum() > 0) {
            economia.multiply(BigDecimal("100")).divide(totalMaisCaro, 2, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }

        val nomesDasLojas = estabelecimentos.associate { it.id to it.nome }
        val principal = lojasUsadas.maxByOrNull { it.value }?.key
        val descricao = when {
            lojasUsadas.isEmpty() -> "Sem estabelecimento"
            lojasUsadas.size == 1 -> nomesDasLojas[principal] ?: "Estabelecimento"
            else -> "${nomesDasLojas[principal] ?: "Estabelecimento"} + ${lojasUsadas.size - 1} loja(s)"
        }

        return ResultadoDaCompra(
            totalPago = totalPago.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            economia = economia,
            economiaPercentual = percentual,
            quantidadeItens = itens.size,
            itensSemPreco = semPreco,
            totalSeComprasseNoMaisCaro = totalMaisCaro.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            gastosPorCategoria = porCategoria.entries
                .sortedByDescending { it.value }
                .map { PontoGrafico(it.key, it.value.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)) },
            descricaoEstabelecimento = descricao,
            estabelecimentoPrincipalId = principal,
        )
    }

    /**
     * Estimativa mostrada enquanto a compra ainda esta em andamento: usa o menor
     * preco conhecido de cada item (todos, nao so os marcados).
     */
    fun estimativaParcial(itens: List<ItemComProduto>, precos: List<PrecoRegistrado>): BigDecimal {
        val precosPorItem = precos.groupBy { it.itemDaListaId }
        return itens.fold(BigDecimal.ZERO) { acumulado, itemComProduto ->
            val menor = precosPorItem[itemComProduto.item.id].orEmpty()
                .filter { it.disponivel && it.preco.signum() > 0 }
                .minByOrNull { it.preco } ?: return@fold acumulado
            acumulado.add(MotorDePrecos.totalDaLinha(menor.preco, itemComProduto.item))
        }.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
    }
}

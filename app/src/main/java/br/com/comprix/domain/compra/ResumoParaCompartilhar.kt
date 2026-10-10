package br.com.comprix.domain.compra

import br.com.comprix.domain.modelo.TotalEstabelecimento
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Texto plano do resumo de uma compra concluída, pronto para compartilhar
 * (ACTION_SEND). É o complemento do "compartilhar lista" que existia antes da
 * ida ao mercado: agora a volta também sai em texto que cola direto no
 * WhatsApp ou na família, sem foto nem arquivo.
 *
 * Decisões de formato (as mesmas do texto da lista):
 * - texto puro, bullets "•", nada de emoji — legível em qualquer app;
 * - dinheiro e porcentagem pt-BR via [Formatadores];
 * - compra numa loja só mostra o nome da loja em "Onde:"; compra mista lista
 *   o pedaço de cada loja em "Lojas:", porque é a informação que ninguém
 *   consegue recontar de cabeça depois.
 *
 * Função PURA (sem Android): testável em JVM e reutilizável fora da folha de
 * celebração.
 */
object ResumoParaCompartilhar {

    /**
     * Monta o resumo.
     *
     * @param nomeDaLista nome da lista comprada; nulo/branco omite a linha.
     * @param quando momento da conclusão da compra.
     * @param totalPago total efetivamente pago.
     * @param economia o quanto saiu mais barato que a cesta no mercado mais caro.
     * @param economiaPercentual economia em porcentagem (ex.: 6.2).
     * @param quantidadeItens quantidade de itens da compra.
     * @param descricaoDoEstabelecimento rotulagem da compra (uma loja ou "compra mista").
     * @param totaisPorLoja pedaço por loja; entra como "Lojas:" quando há mais de uma.
     */
    fun texto(
        nomeDaLista: String?,
        quando: LocalDateTime,
        totalPago: BigDecimal,
        economia: BigDecimal,
        economiaPercentual: BigDecimal,
        quantidadeItens: Int,
        descricaoDoEstabelecimento: String,
        totaisPorLoja: List<TotalEstabelecimento> = emptyList(),
    ): String = buildString {
        append("Compra concluída (Comprix)")
        if (!nomeDaLista.isNullOrBlank()) append(" — ").append(nomeDaLista)
        append('\n').append(Formatadores.data(quando))
        append('\n').append('\n')
        append("Itens: ").append(quantidadeItens)
        append('\n')
        append("Total: ").append(Formatadores.moeda(totalPago))
        if (economia.signum() > 0) {
            append('\n')
            append("Economia: ").append(Formatadores.moeda(economia))
            append(" (").append(Formatadores.percentual(economiaPercentual)).append(')')
        }

        val lojas = totaisPorLoja.filter { it.itensDisponiveis > 0 }
        if (lojas.size > 1) {
            append('\n').append('\n').append("Lojas:")
            lojas.forEach { loja ->
                append('\n').append("• ").append(loja.nome)
                    .append(" — ").append(Formatadores.moeda(loja.total))
                    .append(" (").append(loja.itensDisponiveis).append(" itens)")
            }
        } else if (descricaoDoEstabelecimento.isNotBlank()) {
            append('\n').append("Onde: ").append(descricaoDoEstabelecimento)
        }
    }
}

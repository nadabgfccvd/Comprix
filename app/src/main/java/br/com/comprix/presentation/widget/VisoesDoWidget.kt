package br.com.comprix.presentation.widget

import br.com.comprix.domain.modelo.ResumoDeLista
import br.com.comprix.util.Formatadores
import java.math.BigDecimal

/**
 * Decisões puras de exibição do [ListaWidgetProvider] — o que o widget mostra
 * dado o resumo da primeira lista aberta (ou nada).
 *
 * Extraído do provider para poder ser testado na JVM sem RemoteViews: os
 * textos e regras aqui são decisões de produto visíveis na tela inicial do
 * aparelho (estado vazio, contagem, barra e linha do estimado), e uma
 * regressão neles era invisível para o CI. O provider só traduz o
 * [EstadoDoWidget] em chamadas de `RemoteViews` — nenhuma regra fica lá.
 *
 * Regras (estabelecidas no widget original, preservadas intactas):
 * - Sem lista aberta **ou** lista com nome em branco: título "Nenhuma lista
 *   aberta" (a contagem segue real, porque a lista existe);
 * - Lista sem itens: contagem vira convite ("Abra o app para comecar") e a
 *   barra fica em 0/0;
 * - Barra: máximo = total de itens, progresso = itens comprados;
 * - Linha do estimado: visível só com valor **positivo** (sem preço anotado
 *   o widget fica com duas linhas limpas, não três) e formatado pt-BR.
 */
object VisoesDoWidget {

    /** Retrato estático do widget, pronto para o provider pintar. */
    data class EstadoDoWidget(
        val textoNome: String,
        val maximoDaBarra: Int,
        val progressoDaBarra: Int,
        val textoContagem: String,
        val estimadoVisivel: Boolean,
        val textoEstimado: String,
    )

    private const val SEM_LISTA = "Nenhuma lista aberta"
    private const val CONVITE = "Abra o app para comecar"

    /** Calcula o estado do widget a partir do resumo (null = widget recém-colocado). */
    fun estado(resumo: ResumoDeLista?): EstadoDoWidget {
        val nome = resumo?.lista?.nome.orEmpty()
        val total = resumo?.totalDeItens ?: 0
        val comprados = resumo?.itensComprados ?: 0
        val estimado: BigDecimal = resumo?.totalEstimado ?: BigDecimal.ZERO
        val estimadoVisivel = estimado.signum() > 0

        return EstadoDoWidget(
            textoNome = if (nome.isBlank()) SEM_LISTA else nome,
            maximoDaBarra = total,
            progressoDaBarra = comprados,
            textoContagem = if (total == 0) CONVITE else "$comprados de $total comprados",
            estimadoVisivel = estimadoVisivel,
            textoEstimado = "≈ ${Formatadores.moeda(estimado)} estimados",
        )
    }
}

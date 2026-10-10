package br.com.comprix.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import br.com.comprix.R
import br.com.comprix.di.ServiceLocator
import br.com.comprix.domain.modelo.ResumoDeLista
import br.com.comprix.presentation.MainActivity
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Widget da tela inicial: progresso da **primeira lista nao finalizada**.
 *
 * ## Como o widget le os dados
 * O provider roda FORA de qualquer Activity - o lancador pode ate iniciar o
 * processo so para desenhar o widget. Por isso o caminho e direto ao banco:
 *
 * 1. [ServiceLocator.preparar] e idempotente, entao pode (e deve) ser chamado
 *    com o contexto recebido no `onUpdate` antes de tocar no locator - cobre o
 *    caso do processo ter nascido pelo widget, sem passar pelo `ComprixApp`;
 * 2. uma consulta unica dentro de `runBlocking` (aceitavel num componente
 *    remoto, padrao comum para widgets) le o resumo pelo
 *    `listaRepositorio.resumos`: contagem de itens e de comprados sai do
 *    Room, sem rede nem ViewModel - o widget funciona offline por construcao;
 * 3. as `RemoteViews` recebem nome, barra, contagem e o total estimado da
 *    lista (a linha do estimado some quando nao ha preco anotado), e o widget
 *    inteiro abre o app ao toque (o toque numa view remota nao roda codigo do
 *    app - so PendingIntent).
 *
 * Quando o app esta aberto e muda qualquer lista/item, o `ComprixApp` coleta o
 * mesmo Flow de resumos e pede o redesenho via [atualizarTudo] - a logica de
 * desenho vive aqui no companion para `onUpdate` e o app reusarem o mesmo
 * caminho, sem duplicar nada.
 */
class ListaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        contexto: Context,
        gerenciador: AppWidgetManager,
        idsDeWidget: IntArray,
    ) {
        atualizar(contexto, gerenciador, idsDeWidget)
    }

    companion object {

        /** Re-desenha todos os widgets da lista ja colocados no lancador. */
        fun atualizarTudo(contexto: Context) {
            val gerenciador = AppWidgetManager.getInstance(contexto) ?: return
            val ids = gerenciador.getAppWidgetIds(
                ComponentName(contexto, ListaWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            atualizar(contexto, gerenciador, ids)
        }

        private fun atualizar(contexto: Context, gerenciador: AppWidgetManager, ids: IntArray) {
            ServiceLocator.preparar(contexto)
            val resumo = runCatching {
                runBlocking {
                    ServiceLocator.listaRepositorio.resumos.first()
                        .firstOrNull { !it.lista.finalizada }
                }
            }.getOrNull()
            ids.forEach { id -> gerenciador.updateAppWidget(id, visoes(contexto, resumo)) }
        }

        /** Monta as RemoteViews do widget com o resumo (ou o estado vazio). */
        private fun visoes(contexto: Context, resumo: ResumoDeLista?): RemoteViews {
            val visoes = RemoteViews(contexto.packageName, R.layout.lista_widget)

            val nome = resumo?.lista?.nome.orEmpty()
            visoes.setTextViewText(
                R.id.texto_nome,
                if (nome.isBlank()) "Nenhuma lista aberta" else nome,
            )

            val total = resumo?.totalDeItens ?: 0
            val comprados = resumo?.itensComprados ?: 0
            // ProgressBar em RemoteViews: setProgressBar(id, max, progresso, indeterminado).
            visoes.setProgressBar(R.id.barra_progresso, total, comprados, false)
            visoes.setTextViewText(
                R.id.texto_contagem,
                if (total == 0) "Abra o app para comecar" else "$comprados de $total comprados",
            )

            // Total estimado da lista: some quando nao ha precos anotados ainda,
            // para o widget vazio ficar com duas linhas limpas, nao tres.
            val estimado: BigDecimal = resumo?.totalEstimado ?: BigDecimal.ZERO
            visoes.setViewVisibility(
                R.id.texto_estimado,
                if (estimado.signum() > 0) View.VISIBLE else View.GONE,
            )
            if (estimado.signum() > 0) {
                visoes.setTextViewText(
                    R.id.texto_estimado,
                    "≈ ${Formatadores.moeda(estimado)} estimados",
                )
            }

            // Widget inteiro clicavel: abre o app (unica Activity, MainActivity).
            val intencao = Intent(contexto, MainActivity::class.java)
            val painel = PendingIntent.getActivity(
                contexto,
                0,
                intencao,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            visoes.setOnClickPendingIntent(R.id.raiz_do_widget, painel)
            return visoes
        }
    }
}

package br.com.comprix.presentation.historico

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * **Historico e economia** (tela 06 da referencia).
 *
 * Tres blocos, nesta ordem:
 *
 * 1. dois numeros grandes - economia acumulada e ticket medio;
 * 2. analiticas - gasto por periodo (barras com grade e media movel) e por
 *    categoria (barra segmentada);
 * 3. compras realizadas - cada uma com data, lojas, total e economia.
 *
 * Nenhum numero aqui e estimado: tudo vem de compra efetivamente finalizada.
 */
@Composable
fun HistoricoScreen(
    viewModel: HistoricoViewModel,
    aoVoltar: () -> Unit,
    aoAbrirListas: () -> Unit,
    navegacao: @Composable () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var confirmandoRemocao by remember { mutableStateOf<CompraFinalizada?>(null) }

    // Mesmo padrao do CreateDocument do backup (ConfiguracoesScreen), agora
    // com MIME de planilha: o CSV sai pelo seletor do sistema, sem permissao.
    val escolherDestinoCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { destino -> destino?.let { viewModel.exportarCsv(contexto, it) } }

    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = { BarraSimples("Histórico e economia", aoVoltar = aoVoltar) },
        navegacao = navegacao,
        sobreposicao = {
            if (mensagem != null) {
                Torrada(
                    mensagem.orEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            item {
                TituloDaTela("Histórico e economia")
                EspacoVertical(14.dp)
            }

            if (estado.vazio) {
                item {
                    EstadoVazio(
                        icone = Icones.historico,
                        titulo = "Seu histórico começa na próxima compra.",
                        descricao = "Finalize uma comparação para guardar aqui o total pago, " +
                            "a economia e os gráficos de gasto.",
                    ) {
                        BotaoComprix("Ir para Minhas Listas", aoAbrirListas, icone = Icones.listas)
                    }
                }
                return@LazyColumn
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CartaoDeNumero(
                        rotulo = "Economia acumulada",
                        valor = Formatadores.moeda(estado.resumo.totalEconomizado),
                        icone = Icones.trofeu,
                        destacado = true,
                        modifier = Modifier.weight(1f),
                    )
                    CartaoDeNumero(
                        rotulo = "Gasto médio por compra",
                        valor = Formatadores.moeda(estado.resumo.ticketMedio),
                        icone = Icones.etiqueta,
                        modifier = Modifier.weight(1f),
                    )
                }
                EspacoVertical(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CartaoDeNumero(
                        rotulo = "Total gasto no período",
                        valor = Formatadores.moeda(estado.resumo.totalGasto),
                        icone = Icones.grafico,
                        modifier = Modifier.weight(1f),
                    )
                    CartaoDeNumero(
                        rotulo = "Compras registradas",
                        valor = estado.resumo.quantidadeCompras.toString(),
                        icone = Icones.cesta,
                        modifier = Modifier.weight(1f),
                    )
                }

                EspacoVertical(16.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    AnalisadorDeHistorico.Periodo.entries.forEach { periodo ->
                        PastilhaSelecionavel(
                            texto = periodo.rotulo,
                            selecionada = estado.periodo == periodo,
                            aoTocar = { viewModel.definirPeriodo(periodo) },
                        )
                    }
                }
            }

            item {
                EspacoVertical(18.dp)
                Text("Analíticas", style = MaterialTheme.typography.titleMedium, color = cores.texto)
                Legenda("Gasto por mês, no período escolhido")
                EspacoVertical(12.dp)
                CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                    GraficoDeBarras(estado.resumo.gastosPorPeriodo)
                }
                estado.variacaoMensal?.let { variacao ->
                    EspacoVertical(9.dp)
                    val subiu = variacao.signum() > 0
                    Selo(
                        texto = if (subiu) {
                            "Gasto ${Formatadores.percentual(variacao)} maior que o mês anterior"
                        } else {
                            "Gasto ${Formatadores.percentual(variacao.abs())} menor que o mês anterior"
                        },
                        tom = if (subiu) TomDoSelo.OURO else TomDoSelo.VERDE,
                        icone = if (subiu) Icones.alerta else Icones.confirmarCirculo,
                    )
                }
            }

            item {
                EspacoVertical(18.dp)
                Text(
                    "Gasto por categoria",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                Legenda("Proporção do gasto no período escolhido")
                EspacoVertical(12.dp)
                CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                    BarraDeCategorias(estado.resumo.gastosPorCategoria)
                }
            }

            item {
                EspacoVertical(18.dp)
                Text("Meta do mês", style = MaterialTheme.typography.titleMedium, color = cores.texto)
                Legenda("Economia das compras do mês corrente")
                EspacoVertical(12.dp)
                CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                    CartaoDaMetaDoMes(economia = estado.economiaDoMes, meta = estado.metaEconomia)
                }
            }

            item {
                EspacoVertical(18.dp)
                Text(
                    "Compras realizadas",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(12.dp)
            }

            items(estado.compras, key = { it.id }) { compra ->
                CartaoDeCompra(
                    compra = compra,
                    aoRemover = { confirmandoRemocao = compra },
                )
                EspacoVertical(10.dp)
            }

            item {
                EspacoVertical(12.dp)
                BotaoComprix(
                    "Exportar compras (CSV)",
                    {
                        escolherDestinoCsv.launch(
                            "comprix-compras-${Formatadores.carimboDeArquivo()}.csv",
                        )
                    },
                    bloco = true,
                    estilo = EstiloDeBotao.CONTORNADO,
                    icone = Icones.enviar,
                )
                EspacoVertical(10.dp)
                Legenda(
                    "O CSV abre no Excel e no Calc com uma linha por compra. " +
                        "O histórico fica só neste aparelho — para levá-lo para " +
                        "outro celular, exporte um backup em Ajustes.",
                )
                EspacoVertical(16.dp)
            }
        }
    }

    confirmandoRemocao?.let { compra ->
        br.com.comprix.presentation.comum.DialogoComprix(
            titulo = "Remover do histórico?",
            aoFechar = { confirmandoRemocao = null },
            icone = Icones.excluir,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { confirmandoRemocao = null },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Remover",
                        {
                            viewModel.remover(compra)
                            confirmandoRemocao = null
                        },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            Text(
                "“${compra.nomeLista}” de ${Formatadores.data(compra.data)} sai dos gráficos " +
                    "e da economia acumulada. A lista original não é afetada.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tema.cores.apagado,
            )
        }
    }
}

@Composable
private fun CartaoDeNumero(
    rotulo: String,
    valor: String,
    icone: Int,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
) {
    val cores = Tema.cores
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (destacado) cores.verdeSuave else cores.cartao)
            .border(
                1.dp,
                if (destacado) cores.acao.copy(alpha = 0.35f) else cores.contorno,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        IconeComprix(
            icone,
            null,
            tamanho = TamanhoDeIcone.pequeno,
            tinta = if (destacado) cores.verdeTinta else cores.apagado,
        )
        EspacoVertical(7.dp)
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = if (destacado) cores.verdeTinta else cores.apagado,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            valor,
            style = MaterialTheme.typography.headlineMedium,
            color = if (destacado) cores.verdeTinta else cores.texto,
            maxLines = 1,
        )
    }
}

@Composable
private fun CartaoDeCompra(
    compra: CompraFinalizada,
    aoRemover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    CartaoComprix(modifier.fillMaxWidth(), preenchimento = PaddingValues(14.dp)) {
        // Centralizado: o chip de data e o X de remover acompanham o meio do
        // bloco de texto, em vez de pendurarem no topo.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .size(width = 54.dp, height = 58.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(cores.verdeSuave)
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    compra.data.dayOfMonth.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineSmall,
                    color = cores.verdeTinta,
                )
                Text(
                    mesAbreviado(compra.data.monthValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = cores.verdeTinta,
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    compra.nomeLista,
                    style = MaterialTheme.typography.titleSmall,
                    color = cores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Legenda(compra.descricaoEstabelecimento, maximoDeLinhas = 2)
                EspacoVertical(5.dp)
                Text(
                    "Total pago: ${Formatadores.moeda(compra.totalPago)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = cores.verdeTinta,
                )
                Legenda("${compra.quantidadeItens} itens • ${Formatadores.data(compra.data)}")
            }
            br.com.comprix.presentation.comum.BotaoDeIcone(
                Icones.excluir,
                "Remover “${compra.nomeLista}” do histórico",
                aoRemover,
                tinta = cores.apagado,
                tamanhoDoIcone = 18.dp,
            )
        }
        if (compra.economia.signum() > 0) {
            EspacoVertical(10.dp)
            Selo(
                texto = "Economizou ${Formatadores.moeda(compra.economia)}" +
                    percentualDaEconomia(compra),
                tom = TomDoSelo.OURO,
                icone = Icones.trofeu,
            )
        }
    }
}

private fun percentualDaEconomia(compra: CompraFinalizada): String {
    val referencia = compra.totalPago + compra.economia
    if (referencia.signum() <= 0) return ""
    val pct = compra.economia
        .multiply(BigDecimal(100))
        .divide(referencia, 0, java.math.RoundingMode.HALF_UP)
    return " ($pct%)"
}

private fun mesAbreviado(mes: Int) = listOf(
    "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
    "JUL", "AGO", "SET", "OUT", "NOV", "DEZ",
)[(mes - 1).coerceIn(0, 11)]

/**
 * Card "Meta do mes" das Analiticas: economia das compras do mes corrente
 * contra a meta definida em Ajustes, com barra de progresso fina.
 *
 * A barra e estatica (sem animacao) de proposito: ela acompanha numeros que
 * so mudam quando uma compra e finalizada ou removida, nunca durante o uso.
 */
@Composable
private fun CartaoDaMetaDoMes(economia: BigDecimal, meta: BigDecimal?) {
    val cores = Tema.cores
    if (meta == null || meta.signum() <= 0) {
        Legenda("Nenhuma meta definida por enquanto.")
        EspacoVertical(3.dp)
        Legenda("Defina uma meta em Ajustes.")
        return
    }

    val percentual = if (meta.signum() > 0) {
        economia.multiply(BigDecimal(100)).divide(meta, 0, RoundingMode.HALF_UP).toInt()
    } else {
        0
    }
    val alcancou = percentual >= 100
    val fracao = (percentual.coerceIn(0, 100)) / 100f

    Text(
        "${Formatadores.moeda(economia)} de ${Formatadores.moeda(meta)} economizados ($percentual%)",
        style = MaterialTheme.typography.titleSmall,
        color = cores.texto,
    )
    EspacoVertical(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.contorno),
    ) {
        if (fracao > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fracao)
                    .fillMaxHeight()
                    .background(if (alcancou) cores.verdeTinta else cores.acao),
            )
        }
    }
    EspacoVertical(8.dp)
    if (alcancou) {
        Selo(
            texto = "Meta atingida!",
            tom = TomDoSelo.VERDE,
            icone = Icones.confirmarCirculo,
        )
    } else {
        val restante = maxOf(meta.subtract(economia), BigDecimal.ZERO)
        Legenda("Faltam ${Formatadores.moeda(restante)} para bater a meta.")
    }
}

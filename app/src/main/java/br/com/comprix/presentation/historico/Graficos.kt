package br.com.comprix.presentation.historico

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.PontoGrafico
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Graficos do historico, desenhados em [Canvas] proprio.
 *
 * ## Por que nao uma biblioteca
 *
 * Sao tres formas (barras, rosca e linha) sobre dezenas de pontos. Uma
 * biblioteca de graficos custaria megabytes no APK e ciclos de desenho que um
 * Snapdragon 425 nao tem de sobra. O `Canvas` do Compose resolve em algumas
 * centenas de linhas e desenha exatamente o que a referencia mostra.
 *
 * ## Acessibilidade
 *
 * Todo grafico declara `contentDescription` com os valores em texto, e cada um
 * vem acompanhado de uma legenda escrita. Quem usa leitor de tela recebe o
 * mesmo conteudo - nunca "imagem sem rotulo".
 */

/** Largura fixa da coluna de rotulos do eixo Y, a esquerda do desenho. */
private val LARGURA_DO_EIXO_Y = 56.dp

/**
 * Barras verticais com grade horizontal, eixo Y compacto e media movel.
 *
 * A escala vai do zero ate o maximo do periodo. Linhas tracejadas marcam 1/4,
 * 1/2, 3/4 e o topo, cada uma com o valor compacto do nivel a esquerda
 * ("R$ 850", "R$ 1,2 mil"). Rotulos de valor sobre as barras aparecem so para
 * barras maiores que zero; com 12 ou mais pontos, apenas o maximo e o ultimo
 * ficam, para nao clipar.
 *
 * A linha verde e a **media movel simples de 3 pontos**: cada ponto vira a
 * media dele com o vizinho anterior e o seguinte (janela de 2 nas pontas).
 * Suaviza picos isolados para mostrar a direcao do gasto - nao e previsao.
 */
@Composable
fun GraficoDeBarras(
    pontos: List<PontoGrafico>,
    modifier: Modifier = Modifier,
    altura: androidx.compose.ui.unit.Dp = 148.dp,
    mostrarTendencia: Boolean = true,
) {
    val cores = Tema.cores
    if (pontos.isEmpty()) {
        Legenda("Sem dados suficientes para o gráfico ainda.")
        return
    }
    val maximo = pontos.maxOf { it.valor }.coerceAtLeast(BigDecimal.ONE)
    val descricao = pontos.joinToString("; ") {
        "${it.rotulo}: ${Formatadores.moeda(it.valor)}"
    }
    // Com muitas barras, rotulo por barra vira ruido clipado: so os extremos.
    val mostraSoExtremos = pontos.size >= 12
    val indiceDoMaximo = pontos.indices.maxBy { pontos[it].valor }
    val temValor = pontos.any { it.valor.signum() > 0 }

    Column(modifier.fillMaxWidth()) {
        if (temValor) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(LARGURA_DO_EIXO_Y))
                pontos.forEachIndexed { indice, ponto ->
                    val mostra = ponto.valor.signum() > 0 &&
                        (!mostraSoExtremos ||
                            indice == indiceDoMaximo ||
                            indice == pontos.lastIndex)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (mostra) {
                            Text(
                                moedaCompacta(ponto.valor),
                                style = MaterialTheme.typography.labelSmall,
                                color = cores.apagado,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            EspacoVertical(4.dp)
        }
        Row(Modifier.fillMaxWidth()) {
            // Eixo Y: um Box por nivel da grade; o rotulo fica com o centro na
            // altura da propria linha (o offset compensa meia altura do texto).
            Column(Modifier.width(LARGURA_DO_EIXO_Y).height(altura)) {
                repeat(4) { nivel ->
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        Text(
                            moedaCompacta(
                                maximo
                                    .multiply(BigDecimal(4 - nivel))
                                    .divide(BigDecimal(4), 0, RoundingMode.HALF_UP),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = cores.apagado,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(y = (-8).dp),
                        )
                        if (nivel == 3) {
                            Text(
                                moedaCompacta(BigDecimal.ZERO),
                                style = MaterialTheme.typography.labelSmall,
                                color = cores.apagado,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomEnd).offset(y = 8.dp),
                            )
                        }
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(altura)
                    .clearAndSetSemantics {
                        contentDescription =
                            "Gráfico de barras. Maior gasto ${Formatadores.moeda(maximo)}. $descricao"
                    },
            ) {
                // Grade leve: tracejadas em 1/4, 1/2, 3/4 e topo; a base (zero)
                // e uma linha cheia que ancora as barras.
                val efeitoTracejado = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
                repeat(4) { nivel ->
                    val y = size.height * nivel / 4f
                    drawLine(
                        color = cores.contorno.copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = efeitoTracejado,
                    )
                }
                drawLine(
                    color = cores.contorno,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5f,
                )

                val vaos = pontos.size
                val larguraDoVao = size.width / vaos
                val larguraBarra = larguraDoVao * 0.62f
                val sobra = (larguraDoVao - larguraBarra) / 2f

                pontos.forEachIndexed { indice, ponto ->
                    val fracao = ponto.valor
                        .divide(maximo, 4, RoundingMode.HALF_UP)
                        .toFloat()
                        .coerceIn(0.02f, 1f)
                    val alturaBarra = size.height * fracao
                    drawRoundRect(
                        color = if (ponto.destaque) cores.ambar else cores.marca,
                        topLeft = Offset(indice * larguraDoVao + sobra, size.height - alturaBarra),
                        size = Size(larguraBarra, alturaBarra),
                        cornerRadius = CornerRadius(7f, 7f),
                    )
                }

                if (mostrarTendencia && pontos.size > 2) {
                // Media movel simples de 3 pontos: media do ponto com o anterior
                // e o seguinte (janela de 2 nas pontas). Suaviza picos isolados;
                // nao e a linha dos proprios valores.
                val medias = pontos.indices.map { indice ->
                    var soma = BigDecimal.ZERO
                    var divisao = 0
                    if (indice > 0) {
                        soma = soma + pontos[indice - 1].valor
                        divisao++
                    }
                    soma = soma + pontos[indice].valor
                    divisao++
                    if (indice < pontos.lastIndex) {
                        soma = soma + pontos[indice + 1].valor
                        divisao++
                    }
                    soma.divide(BigDecimal(divisao), 2, RoundingMode.HALF_UP)
                }
                val caminho = Path()
                medias.forEachIndexed { indice, valor ->
                    val fracao = valor.divide(maximo, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                    val x = indice * larguraDoVao + larguraDoVao / 2f
                    val y = size.height - size.height * fracao
                    if (indice == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
                }
                drawPath(
                    path = caminho,
                    color = cores.verdeTinta,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Spacer(Modifier.width(LARGURA_DO_EIXO_Y))
            pontos.forEach { ponto ->
                Text(
                    ponto.rotulo.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cores.apagado,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (mostrarTendencia && pontos.size > 2) {
            LegendaDoGrafico(
                listOf(
                    cores.marca to "Gasto por período",
                    cores.verdeTinta to "Média móvel (3 pontos)",
                ),
            )
        }
    }
}

/** Rotulo compacto de dinheiro para eixos e topo de barras: "R$ 850", "R$ 1,2 mil". */
private fun moedaCompacta(valor: BigDecimal): String {
    val v = valor.toDouble()
    val corpo = when {
        v >= 1_000_000 -> compactaFracao(v / 1_000_000) + " mi"
        v >= 1_000 -> compactaFracao(v / 1_000) + " mil"
        else -> valor.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString()
    }
    return "R$ $corpo"
}

/** 1,2 em vez de 1,2345 - so uma casa decimal quando o numero e curto. */
private fun compactaFracao(n: Double): String {
    val inteiro = n.toLong()
    val decis = ((n - inteiro) * 10).toLong()
    return if (n < 10 && decis > 0) "$inteiro,$decis" else inteiro.toString()
}

/**
 * Barra horizontal segmentada do gasto por categoria.
 *
 * A referencia usa essa forma em vez de uma rosca porque ela compara
 * proporcoes lado a lado e cabe numa tela estreita sem encolher os rotulos.
 */
@Composable
fun BarraDeCategorias(
    pontos: List<PontoGrafico>,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    if (pontos.isEmpty()) {
        Legenda("Finalize uma compra para ver o gasto por categoria.")
        return
    }
    val total = pontos.fold(BigDecimal.ZERO) { soma, p -> soma + p.valor }
    if (total.signum() <= 0) {
        Legenda("Finalize uma compra para ver o gasto por categoria.")
        return
    }
    val maiores = pontos.sortedByDescending { it.valor }.take(6)
    val paleta = maiores.indices.map { cores.tintaDaCategoria(it) }
    val descricao = maiores.joinToString("; ") { ponto ->
        val pct = ponto.valor.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP)
        "${ponto.rotulo}: $pct por cento"
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .clearAndSetSemantics { contentDescription = "Gasto por categoria. $descricao" },
        ) {
            maiores.forEachIndexed { indice, ponto ->
                val peso = ponto.valor.divide(total, 4, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0.01f)
                Box(
                    Modifier
                        .weight(peso)
                        .fillMaxWidth()
                        .background(paleta[indice]),
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            maiores.forEachIndexed { indice, ponto ->
                val pct = ponto.valor.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP)
                Row(
                    // Largura fixa por entrada: o percentual termina sempre na
                    // mesma coluna, mesmo com rotulos de tamanhos diferentes.
                    Modifier.fillMaxWidth(0.44f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(paleta[indice]))
                    Text(
                        ponto.rotulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = cores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        color = cores.apagado,
                    )
                }
            }
        }
    }
}

/**
 * Linha simples da variacao de preco de um produto ao longo do tempo.
 *
 * Usada na ficha do produto; com um ponto so, vira um ponto - e isso e a
 * resposta correta, nao um grafico vazio.
 */
@Composable
fun GraficoDeLinha(
    pontos: List<PontoGrafico>,
    modifier: Modifier = Modifier,
    altura: androidx.compose.ui.unit.Dp = 112.dp,
) {
    val cores = Tema.cores
    if (pontos.isEmpty()) {
        Legenda("Ainda não há histórico de preço deste produto.")
        return
    }
    val maximo = pontos.maxOf { it.valor }.coerceAtLeast(BigDecimal.ONE)
    val minimo = pontos.minOf { it.valor }
    val amplitude = (maximo - minimo).coerceAtLeast(BigDecimal.ONE)
    val descricao = pontos.joinToString("; ") { "${it.rotulo}: ${Formatadores.moeda(it.valor)}" }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(altura)
                .clearAndSetSemantics { contentDescription = "Variação de preço. $descricao" },
        ) {
            if (pontos.size == 1) {
                drawCircle(cores.marca, radius = 7f, center = Offset(size.width / 2f, size.height / 2f))
                return@Canvas
            }
            val passo = size.width / (pontos.size - 1)
            val caminho = Path()
            pontos.forEachIndexed { indice, ponto ->
                val fracao = (ponto.valor - minimo)
                    .divide(amplitude, 4, RoundingMode.HALF_UP)
                    .toFloat()
                    .coerceIn(0f, 1f)
                val x = indice * passo
                val y = size.height - (size.height * 0.84f * fracao) - size.height * 0.08f
                if (indice == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
                drawCircle(
                    color = if (ponto.destaque) cores.ambar else cores.marca,
                    radius = 5f,
                    center = Offset(x, y),
                )
            }
            drawPath(caminho, cores.marca, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Legenda(pontos.first().rotulo)
            Legenda(pontos.last().rotulo)
        }
    }
}

/** Legenda em texto de um grafico - obrigatoria sempre que houver cor. */
@Composable
fun LegendaDoGrafico(itens: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        itens.forEach { (cor, rotulo) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(cor))
                Text(
                    rotulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = Tema.cores.apagado,
                )
            }
        }
    }
}

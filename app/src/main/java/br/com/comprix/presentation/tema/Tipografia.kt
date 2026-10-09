package br.com.comprix.presentation.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Escala tipografica do Comprix, transcrita da referencia visual.
 *
 * ## Por que a fonte do sistema
 *
 * A referencia pede Roboto Flex com `Roboto` como alternativa declarada
 * (`font-family:'Roboto Flex',Roboto,Arial`). O arquivo entregue
 * (`roboto-flex-latin.woff2`) e uma instancia **estatica de peso 400**: os
 * eixos variaveis foram achatados na exportacao para a web. Embarcar so o 400
 * obrigaria o Compose a falsificar negrito em tudo que a referencia escreve em
 * 600-740 - justamente os titulos, botoes e precos.
 *
 * O Roboto do sistema e a mesma familia de onde o Flex nasceu, traz Medium e
 * Bold reais em qualquer aparelho desde a API 21, carrega instantaneamente num
 * Moto E5 e nao soma bytes ao APK. Entao a escala abaixo reproduz corpo, peso,
 * entrelinha e espacamento da referencia sobre o Roboto do sistema. O `.ttf`
 * convertido fica em `artefatos/` para conferencia.
 *
 * ## Conversao de medidas
 *
 * O prototipo e desenhado numa tela de 390 px de largura logica, que e
 * exatamente a largura em dp de um celular comum - entao `15px` da referencia
 * vira `15.sp` aqui, sem fator de correcao. `letter-spacing` em `em` passa
 * direto, porque `em` tambem e relativo ao corpo no Compose.
 */
private val FAMILIA = FontFamily.Default

private val ENTRELINHA = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun estilo(
    corpo: Int,
    peso: FontWeight,
    alturaDaLinha: Float,
    espacamento: Float = 0f,
) = TextStyle(
    fontFamily = FAMILIA,
    fontWeight = peso,
    fontSize = corpo.sp,
    lineHeight = (corpo * alturaDaLinha).sp,
    letterSpacing = espacamento.em,
    lineHeightStyle = ENTRELINHA,
)

/**
 * Tipografia do app.
 *
 * | Slot do Material | Uso no Comprix | Referencia |
 * |---|---|---|
 * | `displayLarge` | total pago no resumo da compra | `.checkout-total strong` 32 |
 * | `displayMedium` | titulo da tela | `.screen-title` 27/650/-.035em |
 * | `displaySmall` | palavra "Comprix" na barra | `.brand-word` 26/740/-.045em |
 * | `headlineLarge` | titulo da celebracao | `.checkout-title` 25 |
 * | `headlineMedium` | numero grande (economia) | `.home-savings strong` 21/680 |
 * | `headlineSmall` | nome da lista no cartao | `.list-open h2` 18/650 |
 * | `titleLarge` | titulo da barra superior | `.appbar h1` 18/600 |
 * | `titleMedium` | cabecalho de secao | `.section-header h2` 15/650 |
 * | `titleSmall` | nome do item | `.inline-name` 14/620 |
 * | `bodyLarge` | corpo | `body` 15/1.45 |
 * | `bodyMedium` | corpo curto, celula da matriz | 13 |
 * | `bodySmall` | legenda | `.caption` 12 |
 * | `labelLarge` | texto de botao | `.btn` 15/650 |
 * | `labelMedium` | selo e pastilha | `.badge` 12/600 |
 * | `labelSmall` | antetitulo e rotulo da navegacao | `.kicker` 10/700/.14em |
 */
val TipografiaComprix = Typography(
    displayLarge = estilo(32, FontWeight.W700, 1.12f, -0.03f),
    displayMedium = estilo(27, FontWeight.W700, 1.16f, -0.035f),
    displaySmall = estilo(26, FontWeight.W700, 1.15f, -0.045f),
    headlineLarge = estilo(25, FontWeight.W700, 1.14f, -0.025f),
    headlineMedium = estilo(21, FontWeight.W700, 1.2f, -0.02f),
    headlineSmall = estilo(18, FontWeight.W600, 1.22f, -0.015f),
    titleLarge = estilo(18, FontWeight.W600, 1.25f, -0.01f),
    titleMedium = estilo(15, FontWeight.W600, 1.3f, -0.005f),
    titleSmall = estilo(14, FontWeight.W600, 1.3f),
    bodyLarge = estilo(15, FontWeight.W400, 1.45f),
    bodyMedium = estilo(13, FontWeight.W400, 1.45f),
    bodySmall = estilo(12, FontWeight.W400, 1.4f),
    labelLarge = estilo(15, FontWeight.W600, 1.3f),
    labelMedium = estilo(12, FontWeight.W600, 1.35f),
    labelSmall = estilo(10, FontWeight.W700, 1.3f, 0.14f),
)

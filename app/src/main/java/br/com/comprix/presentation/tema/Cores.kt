package br.com.comprix.presentation.tema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta do Comprix, transcrita da referencia visual entregue pelo usuario
 * (`comprix_referencia/src/design.css`).
 *
 * ## Uma identidade, dois contextos
 *
 * A marca esmeralda assina a tela inicial e os dados positivos. As telas
 * operacionais usam barra clara e conteudo neutro, deixando a cor para acoes e
 * sinalizacoes. O lavanda aparece **so** no modo tecnico (comparacao
 * nutricional), nunca como segunda cor de marca.
 *
 * ## Por que [marca] e [acao] sao cores diferentes
 *
 * `#1E8E5A` sobre branco rende 3,33:1 - passa em area grande, reprova em texto
 * corrido (AA pede 4,5:1). Entao a marca fica restrita a preenchimento,
 * ilustracao e area de 24 dp para cima, e todo texto/icone usa [acao]
 * `#18794D` (4,96:1) ou [verdeTinta] `#14633E` (6,97:1). O mesmo vale para o
 * ambar: `#F4B400` so preenche, e o texto por cima dele e [ambarTinta].
 *
 * ## A cor nunca e o unico indicador
 *
 * Menor preco leva trofeu **e** texto; ausencia leva icone **e** rotulo;
 * restricao leva aviso escrito; selecao leva marca de confirmacao. Quem nao
 * distingue verde de ambar continua lendo a tela inteira.
 */
@Immutable
data class ComprixCores(
    /** Esmeralda da marca. Preenchimento e ilustracao - nunca texto pequeno. */
    val marca: Color,
    /** Derivado acessivel da marca: botao principal, icone e link. */
    val acao: Color,
    /** [acao] pressionada/realcada. */
    val acaoRealce: Color,
    /** Texto sobre [acao] (branco no claro, verde-escuro no escuro). */
    val sobreAcao: Color,
    /** Verde escuro para texto sobre [verdeSuave]. */
    val verdeTinta: Color,
    /** Verde claro de container: chip selecionado, faixa de economia, destaque. */
    val verdeSuave: Color,
    /** Ambar do melhor preco. Preenchimento de celula e selo - nunca texto. */
    val ambar: Color,
    /** Texto sobre [ambar] e sobre [ambarSuave]. */
    val ambarTinta: Color,
    /** Ambar claro de container. */
    val ambarSuave: Color,
    /** Vermelho de alerta. Preenchimento de faixa - nunca texto pequeno. */
    val vermelho: Color,
    /** Texto de alerta e fundo de botao destrutivo. */
    val vermelhoTinta: Color,
    /** Vermelho claro de container. */
    val vermelhoSuave: Color,
    /** Fundo do app. */
    val fundo: Color,
    /** Fundo de cartao, folha e barra inferior. */
    val cartao: Color,
    /** Texto principal. */
    val texto: Color,
    /** Texto secundario (legenda, dica, unidade). */
    val apagado: Color,
    /** Traco de separacao e borda de cartao. */
    val contorno: Color,
    /** Borda de campo, chip e controle - precisa de 3:1 contra o fundo. */
    val contornoForte: Color,
    /** Lavanda do modo tecnico: fundo da barra e dos filtros. */
    val lavanda: Color,
    /** Tinta do modo tecnico: texto, icone e botao da nutricao. */
    val lavandaTinta: Color,
    /** Texto sobre [lavandaTinta]. */
    val sobreLavanda: Color,
    /** Fundo da torrada (snackbar). */
    val torrada: Color,
    /** Texto da torrada. */
    val textoDaTorrada: Color,
    /** Acao da torrada (desfazer). */
    val acaoDaTorrada: Color,
    /** Elevacao desenhada: `true` desliga sombras no alto contraste. */
    val semSombra: Boolean,
    /** `true` quando a superficie e escura - usado para a barra de status. */
    val escuro: Boolean,
) {
    /** Cor de preenchimento da categoria, estavel por posicao na lista. */
    fun tintaDaCategoria(indice: Int): Color = PALETA_DE_CATEGORIAS[indice.mod(PALETA_DE_CATEGORIAS.size)]

    /**
     * Versao suave da mesma cor, para a pastilha do icone.
     *
     * No alto contraste ([semSombra]) a pastilha ganha mais corpo (0,20 no
     * claro, 0,34 no escuro) para o ladrilho nao sumir do cartao. Alpha 1f
     * aqui quebraria o desenho: o icone por cima usa a mesma [tintaDaCategoria]
     * e desapareceria. Nos alfas escolhidos o pior par icone x pastilha medido
     * fica em 3,6:1 (claro) e 4,9:1 (escuro) - acima dos 3:1 de graficos da
     * WCAG 1.4.11.
     */
    fun fundoDaCategoria(indice: Int): Color {
        val alpha = when {
            semSombra && escuro -> 0.34f
            semSombra -> 0.20f
            escuro -> 0.26f
            else -> 0.14f
        }
        return tintaDaCategoria(indice).copy(alpha = alpha)
    }

    private val PALETA_DE_CATEGORIAS: List<Color>
        get() = if (escuro) CATEGORIAS_ESCURO else CATEGORIAS_CLARO

    companion object {
        /**
         * Tintas de categoria da tela 14 da referencia.
         *
         * Cada uma foi escolhida com 4,5:1 ou mais contra [fundo] claro, porque
         * o nome da categoria e escrito nessa cor, nao so a pastilha.
         */
        private val CATEGORIAS_CLARO = listOf(
            Color(0xFF1C8454), // hortifruti - verde
            Color(0xFF9A5B00), // padaria - caramelo
            Color(0xFF1565C0), // laticinios - azul
            Color(0xFFC62828), // acougue - vermelho
            Color(0xFF8A5A2B), // mercearia - marrom
            Color(0xFF00796B), // bebidas - petroleo
            Color(0xFF0277BD), // limpeza - azul claro
            Color(0xFF7B1FA2), // higiene - roxo
            Color(0xFF00838F), // congelados - ciano
            Color(0xFF5D4037), // pet - terra
            Color(0xFF2E7D32), // organicos - verde folha
            Color(0xFF455A64), // bazar - chumbo
        )

        private val CATEGORIAS_ESCURO = listOf(
            Color(0xFF6ED49E),
            Color(0xFFE8B059),
            Color(0xFF7FB6F5),
            Color(0xFFF08F8A),
            Color(0xFFD2A77A),
            Color(0xFF63C9BC),
            Color(0xFF6FC2EE),
            Color(0xFFD3A0E8),
            Color(0xFF66C8D4),
            Color(0xFFC3A294),
            Color(0xFF86D08A),
            Color(0xFFA7BDC8),
        )

        /** Tokens claros da referencia (`:root` do design.css). */
        val CLARO = ComprixCores(
            marca = Color(0xFF1E8E5A),
            acao = Color(0xFF18794D),
            acaoRealce = Color(0xFF12633E),
            sobreAcao = Color(0xFFFFFFFF),
            verdeTinta = Color(0xFF14633E),
            verdeSuave = Color(0xFFE5F2EA),
            ambar = Color(0xFFF4B400),
            ambarTinta = Color(0xFF624700),
            ambarSuave = Color(0xFFFFF4CD),
            vermelho = Color(0xFFD93025),
            vermelhoTinta = Color(0xFFAE251D),
            vermelhoSuave = Color(0xFFFDEBE9),
            fundo = Color(0xFFF8FAF9),
            cartao = Color(0xFFFFFFFF),
            texto = Color(0xFF1C1B1F),
            apagado = Color(0xFF606963),
            contorno = Color(0xFFD8E1DA),
            contornoForte = Color(0xFF758179),
            lavanda = Color(0xFFEAE5F4),
            lavandaTinta = Color(0xFF60507E),
            sobreLavanda = Color(0xFFFFFFFF),
            torrada = Color(0xFF26382D),
            textoDaTorrada = Color(0xFFFFFFFF),
            acaoDaTorrada = Color(0xFFB6ECC8),
            semSombra = false,
            escuro = false,
        )

        /** Tokens escuros (`body[data-theme=dark] .app-surface`). */
        val ESCURO = ComprixCores(
            marca = Color(0xFF2FA86C),
            acao = Color(0xFF8DD7AE),
            acaoRealce = Color(0xFFABE5C4),
            sobreAcao = Color(0xFF0E2518),
            verdeTinta = Color(0xFFADDEBE),
            verdeSuave = Color(0xFF263E2F),
            ambar = Color(0xFFF4B400),
            ambarTinta = Color(0xFFF4C649),
            ambarSuave = Color(0xFF3E3519),
            vermelho = Color(0xFFE15950),
            vermelhoTinta = Color(0xFFFFB4AB),
            vermelhoSuave = Color(0xFF452521),
            fundo = Color(0xFF151B18),
            cartao = Color(0xFF202822),
            texto = Color(0xFFE6E1E5),
            apagado = Color(0xFFB9C5BD),
            contorno = Color(0xFF3E4C42),
            contornoForte = Color(0xFF91A298),
            lavanda = Color(0xFF362E44),
            lavandaTinta = Color(0xFFCDBAE8),
            sobreLavanda = Color(0xFF21152E),
            torrada = Color(0xFFD7E6DB),
            textoDaTorrada = Color(0xFF16231B),
            acaoDaTorrada = Color(0xFF14633E),
            semSombra = false,
            escuro = true,
        )

        /**
         * Alto contraste claro (`body.high-contrast .app-surface`), nivel AAA.
         *
         * Meta WCAG: 7:1 em texto e 4,5:1 em bordas/componentes. Razoes
         * medidas pela formula WCAG 2.1 (L = 0,2126R + 0,7152G + 0,0722B com
         * linearizacao sRGB; razao = (L1 + 0,05) / (L2 + 0,05)), antes ->
         * depois, nos pares principais:
         *
         * - texto vs fundo: 21,0:1 (preto puro sobre branco puro, mantido)
         * - acao `0xFF0A3D20` vs fundo: 7,5 -> 12,3:1; o branco de [sobreAcao]
         *   sobre o botao rende os mesmos 12,3:1
         * - acaoRealce/verdeTinta `0xFF063018` vs branco: 14,6:1
         * - apagado `0xFF24352B` vs fundo: 9,0 -> 13,0:1
         * - verdeTinta `0xFF063018` vs verdeSuave `0xFFD5F2E1`: 8,7 -> 12,2:1
         * - ambarTinta `0xFF3D2C00` vs ambarSuave `0xFFFFF4CD`: 10,5 -> 12,2:1;
         *   contra o preenchimento ambar `0xFFF4B400`: 7,3:1
         * - vermelhoTinta `0xFF7A140C` vs vermelhoSuave `0xFFFDEBE9`:
         *   8,0 -> 9,4:1; vs fundo branco (texto de alerta): 10,9:1
         * - lavandaTinta `0xFF362B57` vs lavanda `0xFFEAE5F4`: 8,0 -> 10,4:1
         * - contorno `0xFF43554A` vs fundo: 5,2 -> 8,0:1; contornoForte
         *   `0xFF14231A` vs fundo: 12,1 -> 16,3:1 - a borda de 2 dp do
         *   CartaoComprix fica evidente
         * - torrada `0xFF26382D` (herdada): textoDaTorrada branco 12,5:1;
         *   acaoDaTorrada `0xFFD5F2E1`: 9,4 -> 10,5:1
         * - marca `0xFF0E6B3D` vs branco: 4,1 -> 6,6:1; a marca normal
         *   `0xFF1E8E5A` nao sustenta texto branco em AAA, entao a barra
         *   esmeralda recebe esta versao mais funda
         */
        val CLARO_ALTO_CONTRASTE = CLARO.copy(
            marca = Color(0xFF0E6B3D),
            acao = Color(0xFF0A3D20),
            acaoRealce = Color(0xFF063018),
            sobreAcao = Color(0xFFFFFFFF),
            verdeTinta = Color(0xFF063018),
            verdeSuave = Color(0xFFD5F2E1),
            ambarTinta = Color(0xFF3D2C00),
            vermelhoTinta = Color(0xFF7A140C),
            fundo = Color(0xFFFFFFFF),
            cartao = Color(0xFFFFFFFF),
            texto = Color(0xFF000000),
            apagado = Color(0xFF24352B),
            contorno = Color(0xFF43554A),
            contornoForte = Color(0xFF14231A),
            lavandaTinta = Color(0xFF362B57),
            acaoDaTorrada = Color(0xFFD5F2E1),
            semSombra = true,
        )

        /**
         * Alto contraste escuro, nivel AAA.
         *
         * A tela 10 da referencia mostra fundo praticamente preto; aqui ele
         * vai a preto puro. Razoes medidas pela formula WCAG 2.1 (L = 0,2126R
         * + 0,7152G + 0,0722B com linearizacao sRGB; razao = (L1 + 0,05) /
         * (L2 + 0,05)), antes -> depois, nos pares principais:
         *
         * - texto branco vs fundo: 19,9 -> 21,0:1 (preto puro)
         * - apagado `0xFFF2FBF5` vs fundo: 17,6 -> 19,9:1; vs cartao: 18,0:1
         * - acao `0xFFB9F6D2` vs fundo: 15,1 -> 17,2:1; o sobreAcao herdado
         *   `0xFF0E2518` sobre o botao rende 13,3:1
         * - verdeTinta `0xFFD9FFEA` vs verdeSuave escuro (herdado
         *   `0xFF263E2F`): 10,7:1 - verificado, passa em 7:1 sem tocar no
         *   verdeSuave, que continua fundo escuro-esverdeado
         * - ambarTinta `0xFFFFE28A` vs ambarSuave `0xFF3E3519`: 8,7 -> 9,6:1
         * - vermelhoTinta `0xFFFFD3CC` vs vermelhoSuave `0xFF452521`:
         *   8,9 -> 10,0:1
         * - lavandaTinta `0xFFEDE2FF` vs lavanda `0xFF362E44`: 9,0 -> 10,4:1;
         *   sobreLavanda herdado sobre o botao lavanda: 14,0:1
         * - contorno `0xFFB7CFC0` vs fundo: 8,7 -> 12,7:1 (e 11,5:1 contra o
         *   cartao); contornoForte branco puro vs fundo: 15,8 -> 21,0:1
         * - cartao `0xFF0C120D` sobre o fundo preto: separacao sutil (1,1:1);
         *   quem demarca o cartao de fato e a borda branca de 2 dp
         * - torrada `0xFFD7E6DB` (herdada): textoDaTorrada 12,6:1;
         *   acaoDaTorrada `0xFF063018`: 5,6 -> 11,2:1 (o valor antigo
         *   `0xFF14633E` reprovava em 7:1)
         * - marca `0xFF12814E` vs branco: 3,0 -> 4,9:1; contra o fundo preto
         *   a barra ainda rende 4,3:1, entao nao desaparece no escuro
         */
        val ESCURO_ALTO_CONTRASTE = ESCURO.copy(
            fundo = Color(0xFF000000),
            cartao = Color(0xFF0C120D).copy(alpha = 1f),
            texto = Color(0xFFFFFFFF),
            apagado = Color(0xFFF2FBF5),
            contorno = Color(0xFFB7CFC0),
            contornoForte = Color(0xFFFFFFFF),
            marca = Color(0xFF12814E),
            acao = Color(0xFFB9F6D2),
            acaoRealce = Color(0xFFD9FFEA),
            verdeTinta = Color(0xFFD9FFEA),
            ambarTinta = Color(0xFFFFE28A),
            vermelhoTinta = Color(0xFFFFD3CC),
            lavandaTinta = Color(0xFFEDE2FF),
            acaoDaTorrada = Color(0xFF063018),
            semSombra = true,
        )
    }
}

/**
 * Luminancia relativa de uma cor, pela formula da WCAG 2.1.
 *
 * Usada para escolher tinta por calculo em vez de por palpite - e para o
 * teste automatizado de contraste poder reprovar uma combinacao antes de ela
 * chegar na tela de alguem.
 */
fun luminanciaRelativa(cor: Color): Double {
    fun canal(valor: Float): Double {
        val v = valor.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * canal(cor.red) + 0.7152 * canal(cor.green) + 0.0722 * canal(cor.blue)
}

/** Razao de contraste entre duas cores (1:1 a 21:1), pela WCAG 2.1. */
fun razaoDeContraste(a: Color, b: Color): Double {
    val la = luminanciaRelativa(a)
    val lb = luminanciaRelativa(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Tinta escura padrao do app, usada sobre preenchimentos claros. */
val TINTA_ESCURA = Color(0xFF1C1B1F)

/**
 * Escolhe entre branco e [TINTA_ESCURA] a cor que le melhor sobre [fundo].
 *
 * Existe para os preenchimentos de cor variavel - pastilha de categoria,
 * etiqueta de loja, celula de melhor preco - onde fixar "sempre branco" ou
 * "sempre escuro" deixaria algum caso ilegivel.
 */
fun corDeTextoSobre(fundo: Color): Color =
    if (razaoDeContraste(Color.White, fundo) >= razaoDeContraste(TINTA_ESCURA, fundo)) {
        Color.White
    } else {
        TINTA_ESCURA
    }

/** Converte `#RRGGBB` ou `#AARRGGBB` em [Color], caindo na marca se o texto for invalido. */
fun corDeHex(hex: String, padrao: Color = Color(0xFF1E8E5A)): Color = runCatching {
    val limpo = hex.removePrefix("#")
    when (limpo.length) {
        6 -> Color(limpo.toLong(16) or 0xFF000000L)
        8 -> Color(limpo.toLong(16))
        else -> padrao
    }
}.getOrDefault(padrao)

/**
 * Projeta os tokens do Comprix no [ColorScheme] do Material 3.
 *
 * Os componentes proprios do app leem [ComprixCores] direto; o esquema aqui
 * existe para que o que vem pronto do Material (campo de texto, menu suspenso,
 * ondulacao de toque) caia na mesma paleta em vez do roxo padrao.
 */
fun ComprixCores.paraEsquemaMaterial(): ColorScheme {
    val construtor = if (escuro) ::darkColorScheme else ::lightColorScheme
    return construtor(
        /* primary = */ acao,
        /* onPrimary = */ sobreAcao,
        /* primaryContainer = */ verdeSuave,
        /* onPrimaryContainer = */ verdeTinta,
        /* inversePrimary = */ marca,
        /* secondary = */ verdeTinta,
        /* onSecondary = */ cartao,
        /* secondaryContainer = */ verdeSuave,
        /* onSecondaryContainer = */ verdeTinta,
        /* tertiary = */ lavandaTinta,
        /* onTertiary = */ sobreLavanda,
        /* tertiaryContainer = */ lavanda,
        /* onTertiaryContainer = */ lavandaTinta,
        /* background = */ fundo,
        /* onBackground = */ texto,
        /* surface = */ fundo,
        /* onSurface = */ texto,
        /* surfaceVariant = */ cartao,
        /* onSurfaceVariant = */ apagado,
        /* surfaceTint = */ acao,
        /* inverseSurface = */ torrada,
        /* inverseOnSurface = */ textoDaTorrada,
        /* error = */ vermelhoTinta,
        /* onError = */ cartao,
        /* errorContainer = */ vermelhoSuave,
        /* onErrorContainer = */ vermelhoTinta,
        /* outline = */ contornoForte,
        /* outlineVariant = */ contorno,
        /* scrim = */ Color(0xFF0C1917),
        /* surfaceBright = */ cartao,
        /* surfaceDim = */ fundo,
        /* surfaceContainer = */ cartao,
        /* surfaceContainerHigh = */ cartao,
        /* surfaceContainerHighest = */ cartao,
        /* surfaceContainerLow = */ fundo,
        /* surfaceContainerLowest = */ fundo,
    )
}

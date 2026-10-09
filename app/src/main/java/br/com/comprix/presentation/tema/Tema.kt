package br.com.comprix.presentation.tema

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.TipoTema

/** Tokens de cor da superficie atual. Sempre preenchido por [TemaComprix]. */
val LocalCores = staticCompositionLocalOf { ComprixCores.CLARO }

/**
 * Raios de canto da referencia: `--radius:20px` no cartao, 14-17 px nos
 * elementos internos, 30 px no botao (pilula) e 27 px no topo da folha.
 */
val FormasComprix = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(27.dp),
)

/**
 * Tema do Comprix.
 *
 * Resolve, nesta ordem:
 *
 * 1. **claro ou escuro** - preferencia gravada; `SISTEMA` segue o aparelho;
 * 2. **alto contraste** - interruptor independente do claro/escuro, como na
 *    referencia, trocando o conjunto de tokens por inteiro (tracos mais
 *    fortes, texto mais escuro, sombra desligada);
 * 3. **cores dinamicas** (Material You) - desligadas por padrao para preservar
 *    a identidade; quando ligadas, so reescrevem o [MaterialTheme.colorScheme],
 *    nunca os tokens de marca, senao o esmeralda sumiria do proprio app; com
 *    alto contraste ativo as dinamicas sao ignoradas por inteiro, para nao
 *    diluirem o reforco de contraste dos componentes prontos do Material;
 * 4. **escala tipografica** - 100 a 160 %, aplicada multiplicando o
 *    `fontScale` da [Density]. Assim todo `sp` da arvore cresce junto,
 *    inclusive o que vem pronto do Material, e a escala do sistema continua
 *    valendo por cima.
 *
 * @param configuracoes preferencias atuais; o padrao serve para pre-visualizar.
 */
@Composable
fun TemaComprix(
    configuracoes: ConfiguracoesApp = ConfiguracoesApp(),
    conteudo: @Composable () -> Unit,
) {
    val escuroPeloSistema = isSystemInDarkTheme()
    val escuro = when (configuracoes.tema) {
        TipoTema.CLARO -> false
        TipoTema.ESCURO -> true
        TipoTema.SISTEMA -> escuroPeloSistema
    }

    // Ordem deliberada: alto contraste e checado antes de qualquer outra
    // combinacao, entao nem o claro/escuro normal nem nada abaixo dele pode
    // sobrescrever as paletas reforcadas.
    val cores = when {
        escuro && configuracoes.altoContraste -> ComprixCores.ESCURO_ALTO_CONTRASTE
        escuro -> ComprixCores.ESCURO
        configuracoes.altoContraste -> ComprixCores.CLARO_ALTO_CONTRASTE
        else -> ComprixCores.CLARO
    }

    val contexto = LocalContext.current
    val esquema = when {
        // Alto contraste vence o Material You: as cores dinamicas nao podem
        // reescrever onSurface/outline do esquema pronto, senao campos e menus
        // perdem o reforco exatamente onde o usuario pediu. Os tokens de
        // [LocalCores] ja seguem as paletas de alto contraste (bloco acima);
        // aqui o esquema Material acompanha.
        configuracoes.altoContraste -> cores.paraEsquemaMaterial()

        // Material You so entra se a pessoa pedir, se o aparelho suportar
        // (API 31+) e se o alto contraste estiver desligado.
        configuracoes.coresDinamicas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)

        else -> cores.paraEsquemaMaterial()
    }

    val densidade = LocalDensity.current
    val densidadeEscalada = Density(
        density = densidade.density,
        fontScale = densidade.fontScale * (configuracoes.escalaDaFonte.coerceIn(100, 160) / 100f),
    )

    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val janela = (vista.context as? Activity)?.window ?: return@SideEffect
            // Barras transparentes com icones legiveis: a cor real quem pinta e
            // o conteudo, entao o claro/escuro dos icones segue o tema.
            WindowCompat.getInsetsController(janela, vista).isAppearanceLightStatusBars = !escuro
            WindowCompat.getInsetsController(janela, vista).isAppearanceLightNavigationBars = !escuro
        }
    }

    CompositionLocalProvider(
        LocalCores provides cores,
        LocalDensity provides densidadeEscalada,
    ) {
        MaterialTheme(
            colorScheme = esquema,
            typography = TipografiaComprix,
            shapes = FormasComprix,
            content = conteudo,
        )
    }
}

/** Atalho de leitura dos tokens: `Tema.cores.acao`. */
object Tema {
    val cores: ComprixCores
        @Composable get() = LocalCores.current
}

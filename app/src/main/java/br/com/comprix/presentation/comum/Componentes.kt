package br.com.comprix.presentation.comum

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Feedback
import br.com.comprix.util.Feedback.TipoDeSom
import br.com.comprix.util.Feedback.TipoDeVibracao

/**
 * Biblioteca de componentes do Comprix.
 *
 * Cada funcao aqui corresponde a uma familia do catalogo da referencia visual
 * (`.btn`, `.chip`, `.badge`, `.switch`, `.check-button`, `.segmented`,
 * `.field`, `.card`, `.progress-track`). O contrato comum:
 *
 * - **alvo de toque de 48 dp** em tudo que e tocavel, mesmo quando o desenho e
 *   menor - o alvo cresce, o pixel nao;
 * - **estado tambem sem cor**: selecionado leva marca de confirmacao, alerta
 *   leva icone, ausencia leva rotulo escrito;
 * - **nome acessivel** obrigatorio em botao de icone;
 * - **nada de cor de marca crua em texto**: o preenchimento usa o tom da
 *   marca, o texto por cima usa a variante escurecida.
 */

/** Altura minima de qualquer alvo tocavel. */
val ALVO_MINIMO: Dp = 48.dp

// ---------------------------------------------------------------------------
// Botoes
// ---------------------------------------------------------------------------

/** Aparencias de botao do catalogo (`.btn.primary`, `.tonal`, ...). */
enum class EstiloDeBotao { PRINCIPAL, TONAL, CONTORNADO, TEXTO, PERIGO, AMBAR, TECNICO }

/**
 * Botao do Comprix.
 *
 * @param estilo papel do botao na tela. Use [EstiloDeBotao.PRINCIPAL] uma vez
 *   por tela, para a acao que conclui a tarefa.
 * @param icone desenho opcional a esquerda do rotulo.
 * @param bloco ocupa toda a largura disponivel (`.btn.block`).
 * @param compacto corpo menor para barras e cartoes (`.btn.small`), mantendo
 *   os 48 dp de alvo.
 */
@Composable
fun BotaoComprix(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloDeBotao = EstiloDeBotao.PRINCIPAL,
    @DrawableRes icone: Int? = null,
    bloco: Boolean = false,
    compacto: Boolean = false,
    habilitado: Boolean = true,
) {
    val cores = Tema.cores
    val fundo = when (estilo) {
        EstiloDeBotao.PRINCIPAL -> cores.acao
        EstiloDeBotao.TONAL -> cores.verdeSuave
        EstiloDeBotao.PERIGO -> cores.vermelhoTinta
        EstiloDeBotao.AMBAR -> cores.ambar
        EstiloDeBotao.TECNICO -> cores.lavandaTinta
        else -> Color.Transparent
    }
    val tinta = when (estilo) {
        EstiloDeBotao.PRINCIPAL -> cores.sobreAcao
        EstiloDeBotao.TONAL -> cores.verdeTinta
        EstiloDeBotao.PERIGO -> if (cores.escuro) Color(0xFF351211) else Color.White
        EstiloDeBotao.AMBAR -> cores.ambarTinta
        EstiloDeBotao.TECNICO -> cores.sobreLavanda
        else -> cores.acao
    }
    val borda = if (estilo == EstiloDeBotao.CONTORNADO) cores.contornoForte else Color.Transparent

    Row(
        modifier = modifier
            .then(if (bloco) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = ALVO_MINIMO)
            .clip(CircleShape)
            .background(if (habilitado) fundo else fundo.copy(alpha = 0.45f))
            .border(if (borda == Color.Transparent) 0.dp else 1.dp, borda, CircleShape)
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoTocar)
            .padding(
                horizontal = if (estilo == EstiloDeBotao.TEXTO) 12.dp else if (compacto) 14.dp else 20.dp,
                vertical = if (compacto) 9.dp else 11.dp,
            )
            .alpha(if (habilitado) 1f else 0.6f),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            IconeComprix(icone, null, tamanho = if (compacto) TamanhoDeIcone.pequeno else 20.dp, tinta = tinta)
        }
        Text(
            text = texto,
            style = if (compacto) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = tinta,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Botao so de icone (`.iconbtn`): desenho de 24 dp dentro de alvo de 48 dp.
 *
 * @param descricao nome lido pelo leitor de tela. Nunca `null` aqui - um botao
 *   sem nome e um beco sem saida para quem usa TalkBack.
 */
@Composable
fun BotaoDeIcone(
    @DrawableRes icone: Int,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tinta: Color = LocalContentColor.current,
    fundo: Color = Color.Transparent,
    habilitado: Boolean = true,
    tamanhoDoIcone: Dp = TamanhoDeIcone.padrao,
) {
    Box(
        modifier = modifier
            .size(ALVO_MINIMO)
            .clip(CircleShape)
            .background(fundo)
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        IconeComprix(
            icone,
            descricao,
            tamanho = tamanhoDoIcone,
            tinta = if (habilitado) tinta else tinta.copy(alpha = 0.4f),
        )
    }
}

/** Botao flutuante da tela de listas (`.btn.primary.fab`). */
@Composable
fun BotaoFlutuante(texto: String, @DrawableRes icone: Int, aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    val cores = Tema.cores
    Row(
        modifier = modifier
            .heightIn(min = 55.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cores.acao)
            .clickable(role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeComprix(icone, null, tinta = cores.sobreAcao)
        Text(texto, style = MaterialTheme.typography.labelLarge, color = cores.sobreAcao)
    }
}

// ---------------------------------------------------------------------------
// Selos, pastilhas e cartoes
// ---------------------------------------------------------------------------

/** Tons de selo do catalogo (`.badge.gold`, `.alert`, `.green`). */
enum class TomDoSelo { OURO, ALERTA, VERDE, NEUTRO, TECNICO }

/**
 * Selo curto de estado (`.badge`).
 *
 * Sempre acompanhado de icone, porque e frequente ele carregar a unica
 * informacao de "melhor preco" ou "contem alergeno" da linha.
 */
@Composable
fun Selo(
    texto: String,
    modifier: Modifier = Modifier,
    tom: TomDoSelo = TomDoSelo.NEUTRO,
    @DrawableRes icone: Int? = null,
) {
    val cores = Tema.cores
    val fundo = when (tom) {
        TomDoSelo.OURO -> cores.ambarSuave
        TomDoSelo.ALERTA -> cores.vermelhoSuave
        TomDoSelo.VERDE -> cores.verdeSuave
        TomDoSelo.TECNICO -> cores.lavanda
        TomDoSelo.NEUTRO -> Color.Transparent
    }
    val tinta = when (tom) {
        TomDoSelo.OURO -> cores.ambarTinta
        TomDoSelo.ALERTA -> cores.vermelhoTinta
        TomDoSelo.VERDE -> cores.verdeTinta
        TomDoSelo.TECNICO -> cores.lavandaTinta
        TomDoSelo.NEUTRO -> cores.apagado
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fundo)
            .then(
                if (tom == TomDoSelo.NEUTRO) {
                    Modifier.border(1.dp, cores.contorno, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) IconeComprix(icone, null, tamanho = 15.dp, tinta = tinta)
        Text(texto, style = MaterialTheme.typography.labelMedium, color = tinta)
    }
}

/**
 * Faixa cheia de destaque - usada no "melhor preco" e nos avisos de restricao.
 *
 * Diferente do [Selo], o fundo e saturado: serve quando a informacao precisa
 * competir com a foto do produto ou com a matriz de precos.
 */
@Composable
fun FaixaDeDestaque(
    texto: String,
    modifier: Modifier = Modifier,
    tom: TomDoSelo = TomDoSelo.OURO,
    @DrawableRes icone: Int? = null,
) {
    val cores = Tema.cores
    val fundo = when (tom) {
        TomDoSelo.ALERTA -> cores.vermelho
        TomDoSelo.VERDE -> cores.verdeSuave
        TomDoSelo.TECNICO -> cores.lavanda
        else -> cores.ambar
    }
    val tinta = when (tom) {
        TomDoSelo.ALERTA -> Color.White
        TomDoSelo.VERDE -> cores.verdeTinta
        TomDoSelo.TECNICO -> cores.lavandaTinta
        else -> Color(0xFF251D05)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fundo)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) IconeComprix(icone, null, tamanho = TamanhoDeIcone.pequeno, tinta = tinta)
        Text(texto, style = MaterialTheme.typography.labelMedium, color = tinta)
    }
}

/** Cartao branco com borda e sombra leve (`.card`). */
@Composable
fun CartaoComprix(
    modifier: Modifier = Modifier,
    preenchimento: PaddingValues = PaddingValues(18.dp),
    forma: Shape = RoundedCornerShape(20.dp),
    corDaBorda: Color? = null,
    aoTocar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val cores = Tema.cores
    Column(
        modifier = modifier
            .clip(forma)
            .background(cores.cartao)
            .border(
                width = if (corDaBorda != null || cores.semSombra) 2.dp else 1.dp,
                color = corDaBorda ?: cores.contorno,
                shape = forma,
            )
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .padding(preenchimento),
        content = conteudo,
    )
}

/** Traco de separacao (`.divider`). */
@Composable
fun Separador(modifier: Modifier = Modifier, espacoVertical: Dp = 15.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = espacoVertical)
            .height(1.dp)
            .background(Tema.cores.contorno),
    )
}

/** Barra de progresso de 5 dp (`.progress-track`). */
@Composable
fun BarraDeProgresso(
    fracao: Float,
    modifier: Modifier = Modifier,
    cor: Color = Tema.cores.marca,
    fundo: Color = Tema.cores.verdeSuave,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fundo),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fracao.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(cor),
        )
    }
}

// ---------------------------------------------------------------------------
// Selecao
// ---------------------------------------------------------------------------

/**
 * Pastilha selecionavel (`.chip` / `.chip.selected`).
 *
 * Selecionada soma tres sinais: fundo, borda e marca de confirmacao. O estado
 * vai tambem para a semantica, entao o leitor de tela anuncia "selecionado".
 *
 * @param dieta usa o tom de restricao (vermelho) em vez do verde - para
 *   alergenos, onde verde significaria "liberado" e confundiria.
 */
@Composable
fun PastilhaSelecionavel(
    texto: String,
    selecionada: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    dieta: Boolean = false,
    habilitada: Boolean = true,
) {
    val cores = Tema.cores
    val fundo = when {
        !selecionada -> cores.cartao
        dieta -> cores.vermelhoSuave
        else -> cores.verdeSuave
    }
    val tinta = when {
        !selecionada -> cores.texto
        dieta -> cores.vermelhoTinta
        else -> cores.verdeTinta
    }
    val borda = when {
        !selecionada -> cores.contorno
        dieta -> cores.vermelhoTinta
        else -> cores.acao
    }
    Row(
        modifier = modifier
            .heightIn(min = ALVO_MINIMO)
            .clip(RoundedCornerShape(14.dp))
            .background(fundo)
            .border(1.dp, borda, RoundedCornerShape(14.dp))
            .toggleable(
                value = selecionada,
                enabled = habilitada,
                role = Role.Checkbox,
                onValueChange = { aoTocar() },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecionada) {
            IconeComprix(Icones.confirmar, null, tamanho = TamanhoDeIcone.pequeno, tinta = tinta)
        } else if (icone != null) {
            IconeComprix(icone, null, tamanho = TamanhoDeIcone.pequeno, tinta = tinta)
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = tinta)
    }
}

/** Opcao de um controle segmentado. */
data class OpcaoSegmentada(val rotulo: String, @DrawableRes val icone: Int? = null)

/**
 * Controle segmentado (`.segmented`) - um valor selecionado por grupo.
 *
 * Usado para tema, modo de comparacao de unidade e modo do scanner. Cada
 * segmento e `Role.RadioButton`, entao o leitor de tela le "1 de 3".
 */
@Composable
fun Segmentado(
    opcoes: List<OpcaoSegmentada>,
    indiceSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, cores.contornoForte, RoundedCornerShape(26.dp)),
    ) {
        opcoes.forEachIndexed { indice, opcao ->
            val ativa = indice == indiceSelecionado
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ALVO_MINIMO)
                    .background(if (ativa) cores.verdeSuave else Color.Transparent)
                    .selectable(selected = ativa, role = Role.RadioButton) { aoSelecionar(indice) }
                    .padding(horizontal = 7.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tinta = if (ativa) cores.verdeTinta else cores.texto
                if (opcao.icone != null) {
                    IconeComprix(opcao.icone, null, tamanho = TamanhoDeIcone.pequeno, tinta = tinta)
                }
                Text(
                    opcao.rotulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tinta,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (indice < opcoes.lastIndex) {
                Box(Modifier.width(1.dp).heightIn(min = ALVO_MINIMO).background(cores.contornoForte))
            }
        }
    }
}

/**
 * Chave liga/desliga (`.switch`), desenhada a mao para seguir a referencia:
 * trilho de 50 x 30 com borda de 2 dp e botao de 20 dp centrado (5 dp de folga
 * por lado e curso de 20 dp, entao o desenho termina simetrico nos dois
 * estados), dentro de 48 dp de alvo. Em uma [LinhaDeAjuste] o slot do controle
 * tem 52 dp fixos no fim do row - nada do desenho sai do cartao.
 */
@Composable
fun ChaveComprix(
    ativa: Boolean,
    aoAlternar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    habilitada: Boolean = true,
    nomeAcessivel: String? = null,
) {
    val cores = Tema.cores
    val deslocamento by animateDpAsState(if (ativa) 20.dp else 0.dp, label = "chave")
    val corDoTrilho by animateColorAsState(if (ativa) cores.acao else cores.contorno, label = "trilho")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = ALVO_MINIMO)
            .toggleable(
                value = ativa,
                enabled = habilitada,
                role = Role.Switch,
                onValueChange = aoAlternar,
            )
            .then(
                if (nomeAcessivel != null) {
                    Modifier.semantics {
                        contentDescription = nomeAcessivel
                        stateDescription = if (ativa) "ligado" else "desligado"
                    }
                } else {
                    Modifier
                },
            )
            .alpha(if (habilitada) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 50.dp, height = 30.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(corDoTrilho)
                .border(2.dp, if (ativa) cores.acao else cores.contornoForte, RoundedCornerShape(18.dp)),
        ) {
            // Botao centrado no trilho: (50-20)/2 = 5 dp de folga em cada lado.
            // Antes ficava a 3 dp do topo/esquerda e a 7 dp do fim - o cursor
            // parecia desalinhado e o curso ficava curto quando ligado.
            Box(
                Modifier
                    .padding(start = 5.dp, top = 5.dp)
                    .offset(x = deslocamento)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (ativa) cores.sobreAcao else cores.apagado),
            )
        }
    }
}

/**
 * Marcacao de item comprado (`.check-button`): quadrado de 22 dp arredondado
 * dentro de um alvo circular de 48 dp.
 */
@Composable
fun MarcaDeComprado(
    marcado: Boolean,
    aoAlternar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    nomeAcessivel: String = "Marcar como comprado",
) {
    val cores = Tema.cores
    Box(
        modifier = modifier
            .size(ALVO_MINIMO)
            .clip(CircleShape)
            .toggleable(
                value = marcado,
                role = Role.Checkbox,
                onValueChange = { novoEstado ->
                    // Feedback imediato ao tocar: confirmacao curta (som + vibracao)
                    // ao marcar como comprado; toque seco, sem som, ao desmarcar.
                    if (novoEstado) {
                        Feedback.vibrar(TipoDeVibracao.CONFIRMACAO)
                        Feedback.som(TipoDeSom.CONFIRMACAO)
                    } else {
                        Feedback.vibrar(TipoDeVibracao.TOQUE)
                    }
                    aoAlternar(novoEstado)
                },
            )
            .semantics { contentDescription = nomeAcessivel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (marcado) cores.acao else Color.Transparent)
                .border(2.dp, if (marcado) cores.acao else cores.contornoForte, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (marcado) {
                IconeComprix(Icones.confirmar, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.sobreAcao)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Texto auxiliar
// ---------------------------------------------------------------------------

/** Antetitulo em caixa alta (`.kicker`). */
@Composable
fun Antetitulo(texto: String, modifier: Modifier = Modifier, cor: Color = Tema.cores.verdeTinta) {
    Text(texto.uppercase(), style = MaterialTheme.typography.labelSmall, color = cor, modifier = modifier)
}

/** Legenda apagada (`.caption`). */
@Composable
fun Legenda(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color = Tema.cores.apagado,
    maximoDeLinhas: Int = Int.MAX_VALUE,
) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = cor,
        modifier = modifier,
        maxLines = maximoDeLinhas,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Pastilha "100% Offline" da barra esmeralda.
 *
 * E uma afirmacao verificavel: o app nao declara `INTERNET` no manifesto.
 */
@Composable
fun SeloOffline(modifier: Modifier = Modifier, sobreMarca: Boolean = true) {
    val cores = Tema.cores
    val tinta = if (sobreMarca) Color.White else cores.verdeTinta
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (sobreMarca) Color.White.copy(alpha = 0.14f) else cores.verdeSuave)
            .border(
                1.dp,
                if (sobreMarca) Color.White.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "Funciona cem por cento offline" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeComprix(Icones.offline, null, tamanho = 15.dp, tinta = tinta)
        Text("100% Offline", style = MaterialTheme.typography.labelMedium, color = tinta)
    }
}

/**
 * Pastilha do icone de uma categoria: quadrado colorido de 35-42 dp.
 *
 * A cor vem de [Icones.tomDaCategoria], estavel por chave, entao a mesma
 * categoria tem sempre a mesma cor mesmo depois de reordenar o mercado.
 */
@Composable
fun PastilhaDeCategoria(chave: String, modifier: Modifier = Modifier, lado: Dp = 35.dp) {
    val cores = Tema.cores
    val tom = Icones.tomDaCategoria(chave)
    Box(
        modifier
            .size(lado)
            .clip(RoundedCornerShape(10.dp))
            .background(cores.fundoDaCategoria(tom)),
        contentAlignment = Alignment.Center,
    ) {
        IconeComprix(
            Icones.daCategoria(chave),
            null,
            tamanho = lado * 0.57f,
            tinta = cores.tintaDaCategoria(tom),
        )
    }
}

/** Area clicavel sem ondulacao, para tocar fora de um campo e confirmar. */
@Composable
fun Modifier.tocarSemRealce(aoTocar: () -> Unit): Modifier {
    val interacao = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interacao, indication = null, onClick = aoTocar)
}

/** Espaco vertical nomeado, para o codigo das telas ficar legivel. */
@Composable
fun EspacoVertical(altura: Dp) = Spacer(Modifier.height(altura))

/** Fornece [LocalContentColor] para uma subarvore inteira. */
@Composable
fun ComTinta(cor: Color, conteudo: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides cor, content = conteudo)

/** Largura minima util em celulas estreitas da matriz de precos. */
val LARGURA_MINIMA_DE_CELULA: Dp = 104.dp

/** Modificador de celula da matriz, com largura fixa e alvo de toque cheio. */
fun Modifier.celulaDaMatriz(largura: Dp = LARGURA_MINIMA_DE_CELULA): Modifier =
    this.widthIn(min = largura).defaultMinSize(minHeight = ALVO_MINIMO)

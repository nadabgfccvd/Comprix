package br.com.comprix.presentation.comum

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema

/**
 * Esqueleto das telas: barras, navegacao, doca, folhas e torradas.
 *
 * A referencia visual separa tres tratamentos de barra superior. Seguir essa
 * separacao e o que faz o app parecer um produto so, e nao uma colecao de
 * telas:
 *
 * | Barra | Onde | Por que |
 * |---|---|---|
 * | [BarraDaMarca] esmeralda | tela inicial de listas | assina o produto |
 * | [BarraSimples] clara | telas operacionais | a cor fica para o conteudo |
 * | [BarraTecnica] lavanda | comparacao nutricional | avisa que o contexto mudou |
 */

/** Altura da barra superior clara (`.appbar`). */
private val ALTURA_DA_BARRA: Dp = 58.dp

/** Barra superior esmeralda com a palavra "Comprix" (`.appbar.emerald`). */
@Composable
fun BarraDaMarca(modifier: Modifier = Modifier, acoes: @Composable RowScope.() -> Unit = {}) {
    val cores = Tema.cores
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cores.marca)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 67.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Comprix",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        ComTinta(Color.White) {
            SeloOffline()
            acoes()
        }
    }
}

/**
 * Barra superior clara das telas operacionais (`.appbar`).
 *
 * Regra de alinhamento do app: o titulo fica SEMPRE alinhado a esquerda -
 * ao lado da seta quando a tela tem voltar, sobre o mesmo recuo quando nao
 * tem. Nenhuma tela centraliza o titulo da barra.
 *
 * @param aoVoltar desenha a seta de voltar quando informado.
 */
@Composable
fun BarraSimples(
    titulo: String,
    modifier: Modifier = Modifier,
    aoVoltar: (() -> Unit)? = null,
    tecnica: Boolean = false,
    acoes: @Composable RowScope.() -> Unit = {},
) {
    val cores = Tema.cores
    val fundo = if (tecnica) cores.lavanda else cores.fundo
    val tinta = if (tecnica) cores.lavandaTinta else cores.texto
    Column(
        modifier
            .fillMaxWidth()
            .background(fundo)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTURA_DA_BARRA)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (aoVoltar != null) {
                BotaoDeIcone(Icones.voltar, "Voltar", aoVoltar, tinta = tinta)
            } else {
                Spacer(Modifier.width(10.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                color = tinta,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ComTinta(tinta) { acoes() }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))
    }
}

/** Barra superior do modo tecnico (`.appbar.technical`), em lavanda. */
@Composable
fun BarraTecnica(
    titulo: String,
    modifier: Modifier = Modifier,
    aoVoltar: (() -> Unit)? = null,
    acoes: @Composable RowScope.() -> Unit = {},
) = BarraSimples(titulo, modifier, aoVoltar, tecnica = true, acoes = acoes)

// ---------------------------------------------------------------------------
// Navegacao inferior
// ---------------------------------------------------------------------------

/** Um destino da navegacao inferior. */
data class DestinoInferior(
    val rotulo: String,
    @DrawableRes val icone: Int,
    val rota: String,
)

/**
 * Navegacao inferior de cinco destinos (`.bottom-nav`).
 *
 * O destino ativo ganha pastilha verde atras do icone **e** rotulo em negrito
 * **e** `Role.Tab` selecionado - tres sinais, como manda a referencia. Nenhum
 * destino some em tela pequena: o rotulo encolhe, o alvo de 56 dp nao.
 */
@Composable
fun NavegacaoInferior(
    destinos: List<DestinoInferior>,
    rotaAtual: String?,
    aoNavegar: (DestinoInferior) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .background(cores.cartao),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp, bottom = 5.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            destinos.forEach { destino ->
                val ativo = rotaAtual?.startsWith(destino.rota.substringBefore("/")) == true
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = ativo, role = Role.Tab) { aoNavegar(destino) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier
                            .size(width = 52.dp, height = 32.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (ativo) cores.verdeSuave else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconeComprix(
                            destino.icone,
                            null,
                            tamanho = 22.dp,
                            tinta = if (ativo) cores.verdeTinta else cores.apagado,
                        )
                    }
                    Text(
                        destino.rotulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ativo) cores.verdeTinta else cores.apagado,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        // Ellipsis em vez de Clip: rotulo longo com fonte grande
                        // cortado no meio parece bug - as reticencias avisam.
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Doca inferior fixa (`.app-dock`): resumo curto + acao principal da tela.
 *
 * Fica acima da navegacao e nunca rola junto com o conteudo, porque carrega o
 * total da compra - o numero que a pessoa confere o tempo todo.
 */
@Composable
fun DocaInferior(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .background(cores.fundo)
            .border(1.dp, cores.contorno, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .padding(horizontal = 17.dp, vertical = 12.dp),
        content = conteudo,
    )
}

// ---------------------------------------------------------------------------
// Estrutura de tela
// ---------------------------------------------------------------------------

/**
 * Molde das telas: barra, conteudo rolavel, doca e navegacao, nessa ordem.
 *
 * Nao usa `Scaffold` de proposito. A referencia prende doca **e** navegacao no
 * rodape com fundos diferentes, e o `Scaffold` sobrepoe os dois no mesmo slot;
 * com uma `Column` o encaixe fica exato e sobra um `Box` para a torrada e o
 * botao flutuante flutuarem sobre o conteudo.
 */
@Composable
fun TelaComprix(
    modifier: Modifier = Modifier,
    barra: @Composable () -> Unit = {},
    doca: (@Composable () -> Unit)? = null,
    navegacao: (@Composable () -> Unit)? = null,
    sobreposicao: @Composable BoxScope.() -> Unit = {},
    conteudo: @Composable BoxScope.() -> Unit,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxSize()
            .background(cores.fundo),
    ) {
        barra()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            conteudo()
            sobreposicao()
        }
        doca?.invoke()
        if (navegacao != null) {
            navegacao()
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

/** Preenchimento padrao do corpo de uma tela (`.screen`). */
val PREENCHIMENTO_DA_TELA = PaddingValues(start = 17.dp, end = 17.dp, top = 20.dp, bottom = 22.dp)

/** Titulo grande no topo do conteudo (`.screen-title`). */
@Composable
fun TituloDaTela(texto: String, modifier: Modifier = Modifier, subtitulo: String? = null) {
    Column(modifier) {
        Text(texto, style = MaterialTheme.typography.displayMedium, color = Tema.cores.texto)
        if (subtitulo != null) {
            Spacer(Modifier.heightIn(min = 6.dp))
            Legenda(subtitulo)
        }
    }
}

/** Cabecalho de secao com contagem opcional (`.section-header`). */
@Composable
fun CabecalhoDeSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    contagem: String? = null,
    acao: @Composable RowScope.() -> Unit = {},
) {
    val cores = Tema.cores
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = cores.texto)
        if (contagem != null) {
            Text(
                contagem,
                style = MaterialTheme.typography.labelSmall,
                color = cores.verdeTinta,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(cores.verdeSuave)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        acao()
    }
}

/**
 * Estado vazio (`.empty-state`): ilustracao, explicacao e **uma saida**.
 *
 * A saida e obrigatoria no parametro - e o que impede a tela de virar beco sem
 * saida, item proibido pela especificacao.
 */
@Composable
fun EstadoVazio(
    @DrawableRes icone: Int,
    titulo: String,
    descricao: String,
    modifier: Modifier = Modifier,
    acao: @Composable ColumnScope.() -> Unit,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centrado tambem no eixo vertical quando o pai da altura fixa
        // (weight/fillMaxSize); com altura livre fica igual a antes.
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(cores.verdeSuave),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(icone, null, tamanho = TamanhoDeIcone.enorme, tinta = cores.acao)
        }
        Text(
            titulo,
            style = MaterialTheme.typography.headlineSmall,
            color = cores.texto,
            textAlign = TextAlign.Center,
        )
        Text(
            descricao,
            style = MaterialTheme.typography.bodyMedium,
            color = cores.apagado,
            textAlign = TextAlign.Center,
        )
        acao()
    }
}

// ---------------------------------------------------------------------------
// Folhas, dialogos e torradas
// ---------------------------------------------------------------------------

/**
 * Folha inferior (`.sheet`): cabo, cabecalho, corpo rolavel e rodape fixo.
 *
 * A sugestao do parser **nunca** usa isto: ela e inline e nao bloqueante.
 * Folha fica para edicao completa, revisao do scanner e confirmacoes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaComprix(
    titulo: String,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    rodape: (@Composable () -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val cores = Tema.cores
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = aoFechar,
        sheetState = estado,
        containerColor = cores.fundo,
        contentColor = cores.texto,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cores.contornoForte.copy(alpha = 0.6f)),
                )
            }
        },
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 7.dp, end = 7.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Espelho do botao fechar: reserva a esquerda o mesmo espaco de
            // 48 dp, para o titulo ficar centrado na largura da folha e nao
            // deslocado pelo botao da direita.
            Spacer(Modifier.width(ALVO_MINIMO))
            Text(
                titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = cores.texto,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BotaoDeIcone(Icones.fechar, "Fechar", aoFechar, tinta = cores.texto)
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 19.dp)
                .padding(bottom = 19.dp),
            content = conteudo,
        )
        if (rodape != null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(cores.fundo)
                    .padding(horizontal = 19.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) { rodape() }
        }
    }
}

/**
 * Dialogo central (`.sheet.dialog`) para confirmacoes e avisos.
 *
 * Fecha com Esc/voltar e com toque fora, como a referencia pede.
 */
@Composable
fun DialogoComprix(
    titulo: String,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    tomDoIcone: TomDoSelo = TomDoSelo.TECNICO,
    rodape: @Composable ColumnScope.() -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val cores = Tema.cores
    Dialog(onDismissRequest = aoFechar) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(cores.fundo)
                .padding(22.dp),
        ) {
            if (icone != null) {
                val fundo = if (tomDoIcone == TomDoSelo.TECNICO) cores.lavanda else cores.verdeSuave
                val tinta = if (tomDoIcone == TomDoSelo.TECNICO) cores.lavandaTinta else cores.acao
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(43.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(fundo),
                        contentAlignment = Alignment.Center,
                    ) { IconeComprix(icone, null, tinta = tinta) }
                }
                Spacer(Modifier.heightIn(min = 15.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = cores.texto,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            conteudo()
            Spacer(Modifier.heightIn(min = 18.dp))
            rodape()
        }
    }
}

/**
 * Torrada (`.toast`): confirmacao curta com desfazer opcional.
 *
 * Fica dentro do `Box` de conteudo da [TelaComprix], flutuando acima da doca.
 *
 * @param aoDescartar quando informado, desenha o X de dispensar a direita -
 *   para o usuario limpar o aviso sem esperar o timeout da tela.
 */
@Composable
fun Torrada(
    texto: String,
    modifier: Modifier = Modifier,
    rotuloDaAcao: String? = null,
    aoAcionar: (() -> Unit)? = null,
    aoDescartar: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(cores.torrada)
            .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = cores.textoDaTorrada,
            modifier = Modifier.weight(1f),
        )
        if (rotuloDaAcao != null && aoAcionar != null) {
            Text(
                rotuloDaAcao,
                style = MaterialTheme.typography.labelMedium,
                color = cores.acaoDaTorrada,
                modifier = Modifier
                    .heightIn(min = ALVO_MINIMO)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = false, role = Role.Button) { aoAcionar() }
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
        if (aoDescartar != null) {
            BotaoDeIcone(Icones.fechar, "Dispensar", aoDescartar, tinta = cores.textoDaTorrada)
        }
    }
}

/** Preenchimento que evita a barra de gestos quando nao ha navegacao inferior. */
@Composable
fun preenchimentoDoSistema(): PaddingValues = WindowInsets.systemBars.asPaddingValues()

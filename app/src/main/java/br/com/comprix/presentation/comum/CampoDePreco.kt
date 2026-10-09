package br.com.comprix.presentation.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Constantes
import br.com.comprix.util.Feedback
import br.com.comprix.util.Feedback.TipoDeSom
import br.com.comprix.util.Feedback.TipoDeVibracao
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Campos de formulario do Comprix, no desenho da referencia (`.field`):
 * rotulo visivel acima, caixa de 52 dp com borda forte, dica abaixo.
 *
 * O rotulo e sempre visivel - nunca so um `placeholder` que some quando a
 * pessoa comeca a digitar e deixa o campo anonimo no meio de um formulario
 * longo.
 */

/**
 * Campo de texto generico.
 *
 * @param sufixo texto curto colado a direita do valor (unidade, por exemplo).
 * @param dica explicacao sob o campo; vira a conversao calculada ao vivo nos
 *   campos de preco e peso.
 * @param erro quando preenchido, pinta a borda de alerta e substitui a dica.
 */
@Composable
fun CampoComprix(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    dica: String? = null,
    erro: String? = null,
    prefixo: String? = null,
    sufixo: String? = null,
    habilitado: Boolean = true,
    linhaUnica: Boolean = true,
    minimoDeLinhas: Int = 1,
    tipoDeTeclado: KeyboardType = KeyboardType.Text,
    acaoDoTeclado: ImeAction = ImeAction.Done,
    alinhamento: TextAlign = TextAlign.Start,
    aoConcluir: (() -> Unit)? = null,
    modificadorDoCampo: Modifier = Modifier,
) {
    val cores = Tema.cores
    val gerenciadorDeFoco = LocalFocusManager.current
    val corDaBorda = if (erro != null) cores.vermelhoTinta else cores.contornoForte

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rotulo.isNotBlank()) {
            Text(
                rotulo,
                style = MaterialTheme.typography.labelMedium,
                color = cores.texto,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (habilitado) cores.cartao else cores.fundo)
                .border(1.dp, corDaBorda, RoundedCornerShape(12.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefixo != null) {
                Text(prefixo, style = MaterialTheme.typography.bodyLarge, color = cores.apagado)
                Spacer(Modifier.width(3.dp))
            }
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                modifier = modificadorDoCampo.weight(1f),
                enabled = habilitado,
                singleLine = linhaUnica,
                minLines = minimoDeLinhas,
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(
                        color = cores.texto,
                        textAlign = alinhamento,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                ),
                cursorBrush = SolidColor(cores.acao),
                keyboardOptions = KeyboardOptions(keyboardType = tipoDeTeclado, imeAction = acaoDoTeclado),
                keyboardActions = KeyboardActions(
                    onDone = { gerenciadorDeFoco.clearFocus(); aoConcluir?.invoke() },
                    onNext = { gerenciadorDeFoco.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
                ),
            )
            if (sufixo != null) {
                Spacer(Modifier.width(6.dp))
                Text(sufixo, style = MaterialTheme.typography.bodyMedium, color = cores.apagado)
            }
        }
        val auxiliar = erro ?: dica
        if (auxiliar != null) {
            Text(
                auxiliar,
                style = MaterialTheme.typography.bodySmall,
                color = if (erro != null) cores.vermelhoTinta else cores.apagado,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/**
 * Campo de preco usado na matriz, na doca e na edicao completa.
 *
 * ## Confirmacao: tres caminhos, um comportamento
 *
 * A pessoa registra 60 precos seguidos andando pelo corredor. Exigir que ela
 * acerte a tecla "pronto" do teclado a cada item e perder a compra. Aqui o
 * valor e confirmado quando:
 *
 * 1. ela toca **fora** do campo, em qualquer lugar da tela;
 * 2. ela aperta **Enter/Pronto** no teclado;
 * 3. o campo perde o foco por qualquer outro motivo - rolar ate outro campo,
 *    voltar da camera, trocar de aba.
 *
 * Tecnicamente os tres convergem no mesmo ponto: "Pronto" apenas chama
 * `clearFocus()`, e **toda** gravacao acontece no `onFocusChanged`. Assim nao
 * existe caminho em que o valor digitado se perca, nem risco de gravar duas
 * vezes - [aoConfirmar] so dispara quando o texto mudou desde a ultima
 * confirmacao.
 *
 * Para o toque fora funcionar, a tela precisa estar embrulhada em
 * [areaQueConfirmaAoTocarFora].
 *
 * @param aoDigitar espelho do texto vivo, chamado a cada tecla com o valor ja
 *   filtrado. Quem chama pode reagir na hora (habilitar botao, limpar aviso)
 *   sem depender do evento de foco - que chega DEPOIS de um toque num botao.
 * @param aoConfirmar recebe o valor digitado; `null` significa campo limpo.
 */
@Composable
fun CampoDePreco(
    valorInicial: BigDecimal?,
    rotulo: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    descricaoAcessibilidade: String? = null,
    mostrarRotulo: Boolean = true,
    aoDigitar: ((String) -> Unit)? = null,
    aoConfirmar: (BigDecimal?) -> Unit,
) {
    val cores = Tema.cores
    val confirmarAtual by rememberUpdatedState(aoConfirmar)
    val aoDigitarAtual by rememberUpdatedState(aoDigitar)
    val gerenciadorDeFoco = LocalFocusManager.current

    var texto by remember(valorInicial) {
        mutableStateOf(valorInicial?.let { Formatadores.moedaSemSimbolo(it) } ?: "")
    }
    var ultimoConfirmado by remember(valorInicial) {
        mutableStateOf(valorInicial?.let { Formatadores.moedaSemSimbolo(it) } ?: "")
    }
    var tinhaFoco by remember { mutableStateOf(false) }

    val interpretado = TextoUtil.paraDecimal(texto)
    val acimaDoLimite = interpretado != null && interpretado > BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)
    val suspeito = interpretado != null && !acimaDoLimite && interpretado > BigDecimal(Constantes.PRECO_SUSPEITO)

    val dica = when {
        acimaDoLimite -> null
        suspeito -> "Confirme: ${Formatadores.moeda(interpretado!!)}"
        descricaoAcessibilidade != null -> descricaoAcessibilidade
        else -> null
    }
    val erro = if (acimaDoLimite) {
        "Acima de ${Formatadores.moeda(BigDecimal(Constantes.PRECO_MAXIMO_ACEITO))} — confira os dígitos."
    } else {
        null
    }

    CampoComprix(
        valor = texto,
        aoMudar = { entrada ->
            val filtrado = filtrarEntradaDeMoeda(entrada)
            texto = filtrado
            // Espelho do texto vivo: o chamador reage a cada tecla, sem
            // esperar o evento de foco (que chega tarde demais depois de um
            // toque em botao).
            aoDigitarAtual?.invoke(filtrado)
        },
        rotulo = if (mostrarRotulo) rotulo else "",
        modifier = modifier,
        dica = dica,
        erro = erro,
        prefixo = "R$",
        habilitado = habilitado,
        tipoDeTeclado = KeyboardType.Decimal,
        alinhamento = TextAlign.End,
        modificadorDoCampo = Modifier
            .onFocusChanged { estado ->
                if (tinhaFoco && !estado.isFocused && texto != ultimoConfirmado) {
                    // Perdeu o foco: confirma, venha o toque de onde vier.
                    ultimoConfirmado = texto
                    if (acimaDoLimite) {
                        // Preco rejeitado: acima do limite aceito.
                        Feedback.vibrar(TipoDeVibracao.ERRO)
                        Feedback.som(TipoDeSom.ERRO)
                    } else if (interpretado != null && interpretado > BigDecimal.ZERO) {
                        // Preco valido gravado.
                        Feedback.vibrar(TipoDeVibracao.SUCESSO)
                        Feedback.som(TipoDeSom.SUCESSO)
                    }
                    confirmarAtual(if (acimaDoLimite) null else interpretado)
                }
                tinhaFoco = estado.isFocused
            }
            .semantics { contentDescription = descricaoAcessibilidade ?: rotulo },
        aoConcluir = { gerenciadorDeFoco.clearFocus() },
    )
}

/**
 * Mantem no texto apenas o que faz sentido num preco: digitos e um unico
 * separador decimal. Ponto vira virgula - o teclado numerico do Android
 * oferece os dois e quem digita em portugues espera virgula.
 */
fun filtrarEntradaDeMoeda(entrada: String): String {
    val semEspacos = entrada.replace(" ", "").replace("R$", "").replace('.', ',')
    val construtor = StringBuilder()
    var jaTemVirgula = false
    var casasDecimais = 0
    semEspacos.forEach { caractere ->
        when {
            caractere.isDigit() -> {
                if (jaTemVirgula) {
                    if (casasDecimais < 2) {
                        construtor.append(caractere)
                        casasDecimais++
                    }
                } else if (construtor.length < 7) {
                    construtor.append(caractere)
                }
            }

            caractere == ',' && !jaTemVirgula && construtor.isNotEmpty() -> {
                jaTemVirgula = true
                construtor.append(',')
            }
        }
    }
    return construtor.toString()
}

/**
 * Embrulha a tela para que **tocar em qualquer area vazia confirme** o campo
 * em edicao, tirando o foco dele. Sem isto o caminho 1 da regra de
 * confirmacao nao existe.
 */
fun Modifier.areaQueConfirmaAoTocarFora(gerenciadorDeFoco: FocusManager): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onTap = { gerenciadorDeFoco.clearFocus() }) }

/** Versao composable, para usar direto em `Modifier.areaQueConfirmaAoTocarFora()`. */
@Composable
fun Modifier.areaQueConfirmaAoTocarFora(): Modifier =
    this.areaQueConfirmaAoTocarFora(LocalFocusManager.current)

/**
 * Seletor suspenso no desenho de [CampoComprix].
 *
 * Usado para unidade, categoria e estabelecimento: conjuntos fechados, onde
 * digitar livremente so produziria valor invalido.
 */
@Composable
fun <T> SeletorComprix(
    rotulo: String,
    selecionado: T,
    opcoes: List<T>,
    aoSelecionar: (T) -> Unit,
    modifier: Modifier = Modifier,
    rotuloDaOpcao: (T) -> String = { it.toString() },
    habilitado: Boolean = true,
    dica: String? = null,
) {
    val cores = Tema.cores
    var aberto by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rotulo.isNotBlank()) {
            Text(
                rotulo,
                style = MaterialTheme.typography.labelMedium,
                color = cores.texto,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cores.cartao)
                    .border(1.dp, cores.contornoForte, RoundedCornerShape(12.dp))
                    .tocarSemRealce { if (habilitado) aberto = true }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    rotuloDaOpcao(selecionado),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cores.texto,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconeComprix(
                    Icones.descer,
                    "Abrir opções de $rotulo",
                    tamanho = TamanhoDeIcone.pequeno,
                    tinta = cores.apagado,
                )
            }
            DropdownMenu(
                expanded = aberto,
                onDismissRequest = { aberto = false },
                containerColor = cores.cartao,
            ) {
                opcoes.forEach { opcao ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                rotuloDaOpcao(opcao),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cores.texto,
                            )
                        },
                        trailingIcon = {
                            if (opcao == selecionado) {
                                IconeComprix(
                                    Icones.confirmar,
                                    null,
                                    tamanho = TamanhoDeIcone.pequeno,
                                    tinta = cores.acao,
                                )
                            }
                        },
                        onClick = { aoSelecionar(opcao); aberto = false },
                    )
                }
            }
        }
        if (dica != null) {
            Text(
                dica,
                style = MaterialTheme.typography.bodySmall,
                color = cores.apagado,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/**
 * Controle deslizante com valor escrito ao lado.
 *
 * O numero aparece sempre, porque "115%" e uma informacao, nao um detalhe de
 * interacao - e quem usa leitor de tela precisa dele sem arrastar nada.
 */
@Composable
fun DeslizanteComprix(
    valor: Float,
    aoMudar: (Float) -> Unit,
    faixa: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    passos: Int = 0,
    descricao: String = "",
) {
    val cores = Tema.cores
    androidx.compose.material3.Slider(
        value = valor,
        onValueChange = aoMudar,
        valueRange = faixa,
        steps = passos,
        modifier = modifier.heightIn(min = ALVO_MINIMO).semantics { contentDescription = descricao },
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = cores.acao,
            activeTrackColor = cores.acao,
            inactiveTrackColor = cores.contorno,
        ),
    )
}

/**
 * Linha de ajuste com icone, titulo, explicacao e controle a direita.
 *
 * Contrato de alinhamento: a coluna de texto usa weight(1f) e o controle e o
 * ultimo elemento do row - encostado na borda direita INTERNA do cartao (a
 * folga vem do preenchimento de quem chama, 16 dp nas Configuracoes) e
 * centrado no eixo vertical. O slot do controle tem a largura natural dele
 * (52 dp da ChaveComprix, por exemplo) e nunca vaza do cartao, mesmo com
 * descricao longa ou fonte ampliada, porque o texto que cede espaco.
 */
@Composable
fun LinhaDeAjuste(
    titulo: String,
    modifier: Modifier = Modifier,
    descricao: String? = null,
    icone: Int? = null,
    destacada: Boolean = false,
    controle: @Composable () -> Unit,
) {
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (destacada) {
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(cores.verdeSuave)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icone != null) {
            IconeComprix(icone, null, tinta = if (destacada) cores.verdeTinta else cores.apagado)
        }
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                color = if (destacada) cores.verdeTinta else cores.texto,
            )
            if (descricao != null) Legenda(descricao, cor = if (destacada) cores.verdeTinta else cores.apagado)
        }
        controle()
    }
}

/** Linha de navegacao com seta: abre outra tela ou folha. */
@Composable
fun LinhaNavegavel(
    titulo: String,
    resumo: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: Int? = null,
) {
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO)
            .clip(RoundedCornerShape(13.dp))
            .background(cores.cartao)
            .border(1.dp, cores.contorno, RoundedCornerShape(13.dp))
            .tocarSemRealce(aoTocar)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icone != null) IconeComprix(icone, null, tinta = cores.apagado)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = cores.texto)
            Legenda(resumo, maximoDeLinhas = 2)
        }
        IconeComprix(Icones.expandir, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.apagado)
    }
}

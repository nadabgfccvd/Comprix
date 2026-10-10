package br.com.comprix.presentation.lista

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.BarraDeProgresso
import br.com.comprix.presentation.comum.BarraDeRolagem
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.ChaveComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.DocaInferior
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PainelDePendencias
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.comum.areaQueConfirmaAoTocarFora
import br.com.comprix.presentation.comum.filtrarEntradaDeMoeda
import br.com.comprix.presentation.comum.tocarSemRealce
import br.com.comprix.presentation.precos.PrecosViewModel
import br.com.comprix.presentation.scanner.ModoScanner
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Feedback
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * Aumento de escala do MODO COMPRAS aplicado SOMENTE aos cartoes de item:
 * densidade e escala de fonte 15% maiores deixam o texto legivel a um braco
 * de distancia, com a mao ocupada pelo carrinho. A barra, o banner e a doca
 * ficam do tamanho normal - quem orienta a compra e o numero, nao a lista.
 */
private const val FATOR_DE_AUMENTO_DO_MODO_COMPRAS = 1.15f

/**
 * **Edicao da lista de compras** (tela 03 da referencia).
 *
 * Estrutura fixa, de cima para baixo:
 *
 * 1. barra clara com o nome da lista e o menu da lista;
 * 2. adicao rapida em texto livre + atalho do scanner;
 * 3. seletor da loja em que a pessoa esta agora;
 * 4. **painel de pendencias**, fixo, dizendo o que falta e em qual loja;
 * 5. itens agrupados por categoria, na ordem do mercado;
 * 6. doca com contagem, total estimado e "Comparar estabelecimentos".
 *
 * O preco mostrado em cada cartao e o da **loja aberta** - trocar de loja
 * troca a coluna inteira, nunca o produto.
 */
@Composable
fun ListaScreen(
    viewModel: ListaViewModel,
    precos: PrecosViewModel,
    aoVoltar: () -> Unit,
    aoComparar: () -> Unit,
    aoEscanear: (ModoScanner) -> Unit,
    aoAbrirNutricional: () -> Unit,
    aoAbrirProduto: (Long) -> Unit,
    aoAbrirCategorias: () -> Unit,
    aoFinalizarCompra: () -> Unit,
    aoAbrirCatalogo: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    val contexto = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val estadoDePrecos by precos.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val mensagemDePreco by precos.mensagem.collectAsStateWithLifecycle()
    val itemRemovido by viewModel.itemRemovido.collectAsStateWithLifecycle()
    val sugestoesDeDigitacao by viewModel.sugestoesDeDigitacao.collectAsStateWithLifecycle()
    val favoritosDoCatalogo by viewModel.favoritosDoCatalogo.collectAsStateWithLifecycle()
    val modoCompras by viewModel.modoCompras.collectAsStateWithLifecycle()
    val ocultarComprados by viewModel.ocultarComprados.collectAsStateWithLifecycle()
    val sugestoesParaComprarDeNovo by viewModel.sugestoesParaComprarDeNovo.collectAsStateWithLifecycle()
    val gerenciadorDeFoco = LocalFocusManager.current

    var textoDeAdicao by remember { mutableStateOf("") }
    var menuAberto by remember { mutableStateOf(false) }
    // Aviso local (sem passar pelo ViewModel) para mensagens instantaneas do
    // menu, como o guard de conta ja finalizada.
    var avisoLocal by remember { mutableStateOf<String?>(null) }
    var renomeando by remember { mutableStateOf(false) }
    var trocandoDeLoja by remember { mutableStateOf(false) }
    var folhaDeFavoritos by remember { mutableStateOf(false) }
    var dialogoDeOrcamento by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<ItemComProduto?>(null) }
    var comparandoKit by remember { mutableStateOf<ItemComProduto?>(null) }
    var ajustandoPeso by remember { mutableStateOf<ItemComProduto?>(null) }
    var itemAlvo by remember { mutableStateOf<ItemComProduto?>(null) }
    var pendenciaAlvo by remember { mutableStateOf<AuditoriaDeCobertura.Pendencia?>(null) }

    // --- desfazer/refazer da ultima adicao ----------------------------------------------
    val podeDesfazer by viewModel.podeDesfazer.collectAsStateWithLifecycle()
    val podeRefazer by viewModel.podeRefazer.collectAsStateWithLifecycle()
    val textoParaRestaurar by viewModel.textoParaRestaurar.collectAsStateWithLifecycle()
    LaunchedEffect(textoParaRestaurar) {
        textoParaRestaurar?.let { texto ->
            textoDeAdicao = texto
            viewModel.textoRestauradoConsumido()
        }
    }

    // "Comprar de novo": recalcula quando a composicao da lista muda (item
    // entra ou sai) para quem ja esta na lista sair do cartao de sugestoes.
    LaunchedEffect(estado.itens.size) {
        viewModel.atualizarSugestoesDeRecompra()
    }

    // --- arrastar para reordenar ---------------------------------------------------------
    // O puxador de cada cartao dispara o gesto por toque longo; a tela guarda
    // o item em arrasto, o deslocamento vertical e o topo de cada cartao (em
    // coordenadas de janela). Os cabecalhos de categoria tambem tem ancora
    // propria (posicaoDosCabecalhos), o que permite achar o GRUPO-ALVO quando
    // o cartao e solto sobre outra categoria.
    //
    // O grupo-alvo SO e calculado no onDragEnd: durante o arrasto nada e
    // gravado (a categoria mora no produto, e troca-la recomporia a lista no
    // meio do gesto, fazendo o cartao sumir do grupo de origem). O destaque
    // do cabecalho alvo e apenas visual, e o resultado final e aplicado de
    // uma vez ao soltar.
    var idEmArrasto by remember { mutableStateOf<Long?>(null) }
    var deslocamentoDoArrasto by remember { mutableStateOf(0f) }
    val posicaoDosItens = remember { mutableStateMapOf<Long, Int>() }
    val posicaoDosCabecalhos = remember { mutableStateMapOf<Long, Int>() }
    var categoriaSobArrasto by remember { mutableStateOf<Long?>(null) }

    // Categoria que contem uma posicao Y da janela: cada categoria vai do
    // topo do proprio cabecalho ate o topo do proximo cabecalho (ordenado por
    // Y). Acima do primeiro cabecalho cai no primeiro grupo; cabecalhos fora
    // da tela entram com a ultima posicao conhecida, o que nao engana a busca
    // pelo mais recente cabecalho acima do ponto solto.
    fun categoriaAlvoPelaPosicao(topoFinal: Float): Long? {
        val ordenados = posicaoDosCabecalhos.entries.sortedBy { it.value }
        return ordenados.lastOrNull { it.value <= topoFinal }?.key
            ?: ordenados.firstOrNull()?.key
    }

    fun modificadorDeArrasto(item: ItemComProduto): Modifier {
        val id = item.item.id
        // Com busca ativa a lista esta filtrada - as posicoes nao representam
        // a lista inteira, entao arrastar fica desligado. O mesmo vale quando
        // o modo compras esta OCULTANDO COMPRADOS: os grupos visiveis nao sao
        // o grupo completo. Com busca aberta a ocultacao e suspensa (a busca
        // precisa achar tudo), mas quando ela atua sozinha o arrasto descansa.
        if (estado.consulta.isNotBlank() || (modoCompras && ocultarComprados)) return Modifier
        return Modifier.pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    idEmArrasto = id
                    deslocamentoDoArrasto = 0f
                    categoriaSobArrasto = null
                    Feedback.vibrar(Feedback.TipoDeVibracao.CONFIRMACAO)
                },
                onDrag = { _, mudanca ->
                    deslocamentoDoArrasto += mudanca.y
                    val idMovido = idEmArrasto
                    if (idMovido != null) {
                        val topoFinal = (posicaoDosItens[idMovido] ?: 0) + deslocamentoDoArrasto
                        categoriaSobArrasto = categoriaAlvoPelaPosicao(topoFinal)
                    }
                },
                onDragEnd = {
                    val idMovido = idEmArrasto
                    if (idMovido != null) {
                        val grupoOrigem = estado.grupos.firstOrNull { alvo ->
                            alvo.itens.any { it.item.id == idMovido }
                        }
                        if (grupoOrigem != null) {
                            val topoFinal = (posicaoDosItens[idMovido] ?: 0) + deslocamentoDoArrasto
                            val idDoAlvo = categoriaAlvoPelaPosicao(topoFinal)
                            val grupoAlvo = estado.grupos.firstOrNull { it.categoria.id == idDoAlvo }
                            if (grupoAlvo != null && grupoAlvo.categoria.id != grupoOrigem.categoria.id) {
                                val indiceNoDestino = grupoAlvo.itens.count {
                                    (posicaoDosItens[it.item.id] ?: Int.MAX_VALUE) < topoFinal
                                }
                                viewModel.moverItemEntreGrupos(grupoOrigem, grupoAlvo, idMovido, indiceNoDestino)
                            } else {
                                // Soltou sobre a propria categoria: reordenar
                                // dentro do grupo, como sempre fez.
                                val outros = grupoOrigem.itens.filter { it.item.id != idMovido }
                                val indice = outros.count {
                                    (posicaoDosItens[it.item.id] ?: Int.MAX_VALUE) < topoFinal
                                }
                                viewModel.moverItemNoGrupo(grupoOrigem, idMovido, indice)
                            }
                        }
                    }
                    idEmArrasto = null
                    deslocamentoDoArrasto = 0f
                    categoriaSobArrasto = null
                },
                onDragCancel = {
                    idEmArrasto = null
                    deslocamentoDoArrasto = 0f
                    categoriaSobArrasto = null
                },
            )
        }
    }

    // Limpeza das ancoras de posicao: itens removidos e categorias que sairam
    // da lista (ou do filtro atual) nao precisam de posicao guardada - sem
    // isso o mapa cresce indefinidamente e pode devolver posicao de fantasma.
    // Ancoras apagadas sao recriadas pelo onGloballyPositioned assim que o
    // item/cabecalho volta a ser composto.
    LaunchedEffect(estado.itens, estado.grupos) {
        posicaoDosItens.keys.retainAll(estado.itens.mapTo(HashSet()) { it.item.id })
        posicaoDosCabecalhos.keys.retainAll(estado.grupos.mapTo(HashSet()) { it.categoria.id })
    }

    val lojaAtiva = estadoDePrecos.lojaAtiva
    val aviso = mensagem ?: mensagemDePreco

    LaunchedEffect(aviso) {
        if (aviso != null) {
            kotlinx.coroutines.delay(3_400)
            viewModel.mensagemExibida()
            precos.mensagemExibida()
        }
    }

    LaunchedEffect(avisoLocal) {
        if (avisoLocal != null) {
            kotlinx.coroutines.delay(3_400)
            avisoLocal = null
        }
    }

    // Autocomplete com debounce: 180 ms de silencio antes de consultar o
    // catalogo. A sugestao recem-aplicada nao gera nova busca (evita a caixa
    // de sugestoes voltar logo depois de escolher); texto em branco limpa.
    var sugestaoAplicada by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(textoDeAdicao) {
        val termoAtual = textoDeAdicao.trim()
        if (termoAtual.isBlank()) {
            viewModel.atualizarSugestoes("")
            sugestaoAplicada = null
        } else if (termoAtual != sugestaoAplicada?.trim()) {
            kotlinx.coroutines.delay(180)
            viewModel.atualizarSugestoes(termoAtual)
        }
    }

    fun adicionar() {
        if (textoDeAdicao.isBlank()) return
        viewModel.adicionarTextoLivre(textoDeAdicao)
        textoDeAdicao = ""
        gerenciadorDeFoco.clearFocus()
    }

    // --- entrada por voz -----------------------------------------------------------------
    // O RecognizerIntent nao pede permissao de microfone no manifesto: o
    // dialogo do sistema cuida do pedido (e de guardar a gravacao). O texto
    // reconhecido vai PARA O CAMPO de adicao, nunca direto para a lista - a
    // pessoa confere e aciona o +, e ganha desfazer/refazer/sugestoes de graca,
    // como uma digitacao normal.
    val lancadorDeVoz = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { resultado ->
        val textos = resultado.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val reconhecido = textos?.firstOrNull()?.trim().orEmpty()
        if (resultado.resultCode == Activity.RESULT_OK && reconhecido.isNotEmpty()) {
            textoDeAdicao = reconhecido
        } else {
            // Erro, cancelamento ou vazio: mesma torrada (aviso local da tela,
            // o mesmo canal do menu - o VM de lista nao precisa saber de voz).
            avisoLocal = MENSAGEM_VOZ_INDISPONIVEL
        }
    }

    fun ouvir() {
        val intencao = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga seus itens: \"arroz 5kg, feijão 2 quilos\"")
        }
        try {
            lancadorDeVoz.launch(intencao)
        } catch (semReconhecimento: ActivityNotFoundException) {
            avisoLocal = MENSAGEM_VOZ_INDISPONIVEL
        }
    }

    // Modo compras com "Ocultar comprados": itens marcados somem dos grupos e
    // o grupo que fica vazio some junto. SUSPENSO com busca ativa - a busca
    // precisa achar tudo, inclusive o comprado (mesma regra do arrasto). O
    // estado do VM segue inteiro: doca, pendencias e totais contam a lista
    // completa; so a RENDERIZACAO dos grupos e filtrada aqui.
    val ocultandoComprados = modoCompras && ocultarComprados && estado.consulta.isBlank()
    val gruposVisiveis = if (ocultandoComprados) {
        estado.grupos.mapNotNull { grupo ->
            val restantes = grupo.itens.filter { !it.item.comprado }
            if (restantes.isEmpty()) null else grupo.copy(itens = restantes)
        }
    } else {
        estado.grupos
    }
    val densidade = LocalDensity.current

    TelaComprix(
        modifier = Modifier.areaQueConfirmaAoTocarFora(gerenciadorDeFoco),
        barra = {
            BarraSimples(estado.lista?.nome.orEmpty(), aoVoltar = aoVoltar) {
                Box {
                    BotaoDeIcone(Icones.maisOpcoes, "Mais ações da lista", { menuAberto = true })
                    DropdownMenu(
                        expanded = menuAberto,
                        onDismissRequest = { menuAberto = false },
                        containerColor = cores.cartao,
                    ) {
                        ItemDeMenu("Renomear lista", Icones.editar) {
                            menuAberto = false
                            renomeando = true
                        }
                        if (aoAbrirCatalogo != null) {
                            ItemDeMenu("Explorar catálogo", Icones.catalogo) {
                                menuAberto = false
                                aoAbrirCatalogo()
                            }
                        }
                        ItemDeMenu("Favoritos", Icones.estrela) {
                            menuAberto = false
                            folhaDeFavoritos = true
                        }
                        ItemDeMenu("Definir orçamento", Icones.etiqueta) {
                            menuAberto = false
                            dialogoDeOrcamento = true
                        }
                        ItemDeMenu("Compartilhar lista", Icones.compartilhar) {
                            menuAberto = false
                            val intencao = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.textoParaCompartilhar(lojaAtiva?.nome))
                            }
                            contexto.startActivity(Intent.createChooser(intencao, "Compartilhar lista"))
                        }
                        ItemDeMenu("Desmarcar tudo", Icones.recomecar) {
                            menuAberto = false
                            viewModel.desmarcarTodos()
                        }
                        ItemDeMenu("Organizar categorias", Icones.camadas) {
                            menuAberto = false
                            aoAbrirCategorias()
                        }
                        ItemDeMenu("Comparar nutrição", Icones.nutricao) {
                            menuAberto = false
                            aoAbrirNutricional()
                        }
                        ItemDeMenu("Modo compras", Icones.cesta, marcado = modoCompras) {
                            menuAberto = false
                            viewModel.alternarModoCompras()
                        }
                        ItemDeMenu("Finalizar compra", Icones.confirmarCirculo) {
                            menuAberto = false
                            if (estado.lista?.finalizada == true) {
                                // Conta ja fechada: aviso no lugar de navegar
                                // (o VM tambem protege o registro historico).
                                avisoLocal = "Esta conta já foi finalizada."
                            } else {
                                aoFinalizarCompra()
                            }
                        }
                    }
                }
            }
            // Banner do MODO COMPRAS: fixo no topo (slot da barra, FORA do
            // LazyColumn) - nao rola junto com a lista, porque e o numero que
            // a pessoa confere a cada corredor, alem de carregar a chave de
            // ocultar comprados.
            if (modoCompras && estado.totalDeItens > 0) {
                BannerDoModoCompras(
                    comprados = estado.comprados,
                    total = estado.totalDeItens,
                    progresso = estado.progresso,
                    ocultandoComprados = ocultandoComprados,
                    aoAlternarOcultar = viewModel::alternarOcultarComprados,
                    modifier = Modifier.padding(start = 17.dp, end = 17.dp, top = 10.dp),
                )
            }
        },
        doca = {
            DocaInferior {
                // O Total continua sendo o mesmo totalEstimado; o orcamento,
                // quando existe, entra como linha propria com barra e alerta.
                val totalAtual = totalEstimado(estado.itens, estadoDePrecos)
                // Total ANIMADO: ao marcar item ou gravar preco, o numero caminha
                // ate o novo valor em vez de piscar - o olho acompanha a mudança.
                val totalAnimado by animateIntAsState(
                    targetValue = centavosDoTotal(totalAtual).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    animationSpec = tween(280),
                    label = "totalEstimadoAnimado",
                )
                val totalExibido = centavosParaReais(totalAnimado.toLong())
                val orcamentoCentavos = estado.orcamentoCentavos?.takeIf { it > 0 }
                val estourou = orcamentoCentavos != null && ListaRepositorio.nivelDoOrcamento(
                    centavosDoTotal(totalAtual),
                    orcamentoCentavos,
                ) == 2
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${estado.totalDeItens} ${if (estado.totalDeItens == 1) "item" else "itens"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = cores.texto,
                    )
                    Text("•", color = cores.contornoForte)
                    Text(
                        "Total: ${Formatadores.moeda(totalExibido)}",
                        style = MaterialTheme.typography.titleSmall,
                        // Estourou o orcamento: o Total grita em vermelho.
                        color = if (estourou) cores.vermelhoTinta else cores.verdeTinta,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Legenda("${estado.comprados}/${estado.totalDeItens} no carrinho")
                }
                if (orcamentoCentavos != null) {
                    EspacoVertical(7.dp)
                    LinhaDoOrcamento(
                        totalCentavos = totalAnimado.toLong(),
                        orcamentoCentavos = orcamentoCentavos,
                    )
                }
                EspacoVertical(9.dp)
                BotaoComprix(
                    "Comparar estabelecimentos",
                    aoComparar,
                    bloco = true,
                    icone = Icones.comparar,
                    habilitado = estado.podeComparar,
                )
            }
        },
        sobreposicao = {
            if (itemRemovido != null) {
                Torrada(
                    texto = "Item removido da lista.",
                    rotuloDaAcao = "Desfazer",
                    aoAcionar = viewModel::desfazerRemocao,
                    aoDescartar = viewModel::descartarDesfazer,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            } else if (aviso != null) {
                Torrada(
                    aviso.orEmpty(),
                    aoDescartar = {
                        viewModel.mensagemExibida()
                        precos.mensagemExibida()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            } else if (avisoLocal != null) {
                Torrada(
                    avisoLocal.orEmpty(),
                    aoDescartar = { avisoLocal = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        // BarraDeRolagem: o Box envolve a lista para sobrepor a barra fina no
        // canto direito; o conteudo segue com a indentacao de sempre.
        val rolagemDaLista = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA, state = rolagemDaLista) {
            if (estado.carregando) {
                // Mesmo padrao da LojasScreen: corpo centrado enquanto o banco
                // emite o primeiro estado (barra e doca ficam no lugar).
                item {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Legenda("Abrindo…")
                    }
                }
                return@LazyColumn
            }

            item {
                TituloDaTela(estado.lista?.nome.orEmpty())
                EspacoVertical(14.dp)

                // --- adicao rapida -------------------------------------------------
                // Rotulo e dica ficam fora da Row para que o botao redondo alinhe
                // com a caixa do campo em qualquer escala de fonte - alinhar pelo
                // rodape da coluna inteira desencaixaria assim que a dica quebrasse
                // em duas linhas.
                Text(
                    "Adição rápida",
                    style = MaterialTheme.typography.labelMedium,
                    color = cores.texto,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CampoComprix(
                        valor = textoDeAdicao,
                        aoMudar = { textoDeAdicao = it },
                        rotulo = "",
                        modifier = Modifier.weight(1f),
                        acaoDoTeclado = ImeAction.Done,
                        aoConcluir = ::adicionar,
                    )
                    // Microfone: ditado dos itens (o texto volta para o campo).
                    BotaoDeIcone(
                        icone = Icones.microfone,
                        descricao = "Adicionar itens falando",
                        aoTocar = ::ouvir,
                        modifier = Modifier.size(52.dp),
                        tinta = cores.verdeTinta,
                        fundo = cores.verdeSuave,
                    )
                    BotaoDeIcone(
                        icone = if (textoDeAdicao.isBlank()) Icones.codigoDeBarras else Icones.adicionar,
                        descricao = if (textoDeAdicao.isBlank()) {
                            "Escanear preço ou código de barras"
                        } else {
                            "Adicionar à lista"
                        },
                        aoTocar = {
                            if (textoDeAdicao.isBlank()) aoEscanear(ModoScanner.FOTO) else adicionar()
                        },
                        modifier = Modifier.size(52.dp),
                        tinta = cores.sobreAcao,
                        fundo = cores.acao,
                    )
                }

                // --- comprar de novo -------------------------------------------------
                // Produtos mais anotados no historico e que AINDA nao estao na
                // lista: um toque no + coloca o produto aqui (se ja existia, a
                // quantidade e somada, nunca duplicada - mesmo caminho da folha
                // de Favoritos). Sem sugestao, o cartao nao existe.
                if (sugestoesParaComprarDeNovo.isNotEmpty()) {
                    EspacoVertical(10.dp)
                    CartaoDeRecompra(
                        sugestoes = sugestoesParaComprarDeNovo,
                        aoAdicionar = viewModel::adicionarFavorito,
                    )
                }

                // --- desfazer/refazer da ultima adicao ------------------------------
                // Desfazer devolve TUDO que estava escrito a caixa de adicao;
                // refazer reaplica a adicao e limpa a caixa de novo.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BotaoDeIcone(
                        icone = Icones.desfazer,
                        descricao = "Desfazer a última adição à lista",
                        aoTocar = viewModel::desfazerUltimaAdicao,
                        habilitado = podeDesfazer,
                    )
                    BotaoDeIcone(
                        icone = Icones.refazer,
                        descricao = "Refazer a última adição à lista",
                        aoTocar = viewModel::refazerUltimaAdicao,
                        habilitado = podeRefazer,
                    )
                    Legenda(
                        "Desfaz ou refaz a última adição — desfazer devolve o texto à caixa.",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                // --- autocomplete ---------------------------------------------------
                // So aparece com texto digitado; tocar numa sugestao preenche o
                // campo (nao adiciona direto, para o usuario conferir quantidade).
                if (textoDeAdicao.isNotBlank() && sugestoesDeDigitacao.isNotEmpty()) {
                    EspacoVertical(6.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(cores.cartao)
                            .border(1.dp, cores.contorno, RoundedCornerShape(13.dp))
                            .verticalScroll(rememberScrollState()),
                    ) {
                        sugestoesDeDigitacao.forEach { sugestao ->
                            Text(
                                sugestao,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cores.texto,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ALVO_MINIMO)
                                    .tocarSemRealce {
                                        textoDeAdicao = sugestao
                                        sugestaoAplicada = sugestao
                                        viewModel.atualizarSugestoes("")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                EspacoVertical(6.dp)
                Legenda("Ex.: “2 kg de arroz, meia dúzia de ovos, 3 leite”")

                EspacoVertical(12.dp)

                // --- loja aberta ---------------------------------------------------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconeComprix(
                        Icones.loja,
                        null,
                        tamanho = TamanhoDeIcone.pequeno,
                        tinta = cores.apagado,
                    )
                    Legenda("Anotando preços em:")
                    PastilhaSelecionavel(
                        texto = lojaAtiva?.nome ?: "Escolher loja",
                        selecionada = lojaAtiva != null,
                        aoTocar = { trocandoDeLoja = true },
                        icone = Icones.adicionar,
                    )
                }

                EspacoVertical(12.dp)

                // --- painel fixo de pendencias -------------------------------------
                PainelDePendencias(
                    relatorio = estado.relatorio,
                    totalDeLojas = estado.estabelecimentos.size,
                    aoAdicionarLoja = { trocandoDeLoja = true },
                    // A pendencia agora abre a folha de preco faltando (gravar o
                    // valor ou marcar "nao tinha") - nao mais o editor completo.
                    aoAbrirPendencia = { pendencia -> pendenciaAlvo = pendencia },
                )

                if (estado.mostrarDica) {
                    EspacoVertical(10.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(cores.verdeSuave)
                            .padding(11.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeComprix(
                            Icones.brilho,
                            null,
                            tamanho = TamanhoDeIcone.pequeno,
                            tinta = cores.verdeTinta,
                        )
                        Text(
                            "Toque no nome de um item para abrir a edição completa. " +
                                "Toque fora de um campo de preço para confirmar o valor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cores.verdeTinta,
                            modifier = Modifier.weight(1f),
                        )
                        BotaoComprix(
                            "Ok",
                            viewModel::dispensarDica,
                            estilo = EstiloDeBotao.TEXTO,
                            compacto = true,
                        )
                    }
                }

                if (estado.itens.isNotEmpty()) {
                    EspacoVertical(10.dp)
                    CampoComprix(
                        valor = estado.consulta,
                        aoMudar = viewModel::definirConsulta,
                        rotulo = "Procurar na lista",
                    )
                }
            }

            if (estado.vazia) {
                item {
                    EstadoVazio(
                        icone = Icones.listas,
                        titulo = "Lista vazia por enquanto.",
                        descricao = "Digite o primeiro item acima — pode escrever do seu jeito, " +
                            "que o Comprix separa quantidade, unidade e categoria.",
                    ) {
                        BotaoComprix(
                            "Escanear um produto",
                            { aoEscanear(ModoScanner.FOTO) },
                            icone = Icones.escanear,
                            estilo = EstiloDeBotao.CONTORNADO,
                        )
                    }
                }
            }

            gruposVisiveis.forEach { grupo ->
                item(key = "cab-${grupo.categoria.id}") {
                    // Ancora de posicao do cabecalho (para achar o grupo-alvo
                    // do arrasto) + destaque suave quando e a categoria sobre
                    // a qual o cartao esta passando. Sem mudanca de layout:
                    // so fundo recortado, para o gesto nao tremer.
                    val alvoDoArrasto = idEmArrasto != null && categoriaSobArrasto == grupo.categoria.id
                    CabecalhoDeCategoria(
                        grupo.categoria,
                        grupo.itens.size,
                        grupo.concluido,
                        modifier = Modifier
                            .onGloballyPositioned { coordenadas ->
                                posicaoDosCabecalhos[grupo.categoria.id] =
                                    coordenadas.positionInWindow().y.roundToInt()
                            }
                            .then(
                                if (alvoDoArrasto) {
                                    Modifier
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(cores.verdeSuave)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                items(grupo.itens, key = { "item-${it.item.id}" }) { itemComProduto ->
                    val preco = lojaAtiva?.let {
                        estadoDePrecos.precoDe(itemComProduto.item.id, it.id)
                    }
                    val emArrasto = idEmArrasto == itemComProduto.item.id
                    Box(
                        Modifier
                            .onGloballyPositioned { coordenadas ->
                                posicaoDosItens[itemComProduto.item.id] =
                                    coordenadas.positionInWindow().y.roundToInt()
                            }
                            .graphicsLayer {
                                if (emArrasto) {
                                    translationY = deslocamentoDoArrasto
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 16f
                                } else {
                                    translationY = 0f
                                    scaleX = 1f
                                    scaleY = 1f
                                    shadowElevation = 0f
                                }
                            },
                    ) {
                        // Modo compras: densidade aumentada SO nos cartoes de
                        // item (texto e controles 15% maiores). A barra, o
                        // banner e a doca ficam do tamanho normal. Desligado,
                        // fator 1 - o cartao volta exatamente como era.
                        val fator = if (modoCompras) FATOR_DE_AUMENTO_DO_MODO_COMPRAS else 1f
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                densidade.density * fator,
                                densidade.fontScale * fator,
                            ),
                        ) {
                            CartaoDeItem(
                                itemComProduto = itemComProduto,
                                categoria = grupo.categoria,
                                preco = preco,
                                nomeDaLoja = lojaAtiva?.nome,
                                sugestao = estado.sugestoes[itemComProduto.item.id],
                                alertaDeRestricao = MotorDePrecos.alertaDeRestricao(
                                    itemComProduto,
                                    estadoDePrecos.perfil,
                                ),
                                aoAlternarComprado = { viewModel.alternarComprado(itemComProduto) },
                                aoAbrirEdicao = { editando = itemComProduto },
                                aoResponderSugestao = { opcao ->
                                    viewModel.responderSugestao(itemComProduto.item.id, opcao)
                                },
                                aoIgnorarSugestao = { viewModel.ignorarSugestao(itemComProduto.item.id) },
                                aoCompararKit = { comparandoKit = itemComProduto },
                                aoAbrirAcoes = { itemAlvo = itemComProduto },
                                modificadorDeArrasto = modificadorDeArrasto(itemComProduto),
                                sinonimos = viewModel.sinonimosDo(itemComProduto.produto.nome),
                            )
                        }
                    }
                    EspacoVertical(8.dp)
                }
            }

            item {
                EspacoVertical(14.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BotaoComprix(
                        "Escanear preço",
                        { aoEscanear(ModoScanner.FOTO) },
                        estilo = EstiloDeBotao.CONTORNADO,
                        compacto = true,
                        icone = Icones.escanear,
                    )
                    BotaoComprix(
                        "Vídeo de 30 s",
                        { aoEscanear(ModoScanner.VIDEO) },
                        estilo = EstiloDeBotao.CONTORNADO,
                        compacto = true,
                        icone = Icones.video,
                    )
                    BotaoComprix(
                        "Código de barras",
                        { aoEscanear(ModoScanner.CODIGO) },
                        estilo = EstiloDeBotao.CONTORNADO,
                        compacto = true,
                        icone = Icones.codigoDeBarras,
                    )
                }
                EspacoVertical(18.dp)
            }
        }
        BarraDeRolagem(rolagemDaLista, Modifier.align(Alignment.CenterEnd))
        }
    }

    // --- folhas ------------------------------------------------------------------

    editando?.let { alvo ->
        val precoAtual = lojaAtiva?.let { estadoDePrecos.precoDe(alvo.item.id, it.id) }
        EditorDeItem(
            itemComProduto = alvo,
            categorias = estado.categorias.values.sortedBy { it.ordemPadrao },
            nomeDaLoja = lojaAtiva?.nome,
            precoNaLoja = precoAtual,
            aoFechar = { editando = null },
            aoSalvar = { item, nome, categoriaId ->
                viewModel.salvarEdicao(alvo, item, nome, categoriaId)
            },
            aoGravarPreco = { valor ->
                lojaAtiva?.let { precos.anotarPreco(alvo.item.id, it.id, valor) }
            },
            aoRemoverDaLista = { viewModel.removerItem(alvo) },
            aoMarcarIndisponivel = {
                lojaAtiva?.let { precos.marcarIndisponivel(alvo.item.id, it.id) }
            },
            aoAbrirComparadorDeKit = {
                editando = null
                comparandoKit = alvo
            },
            aoAbrirPesoEstimado = {
                editando = null
                ajustandoPeso = alvo
            },
            aoAbrirProduto = {
                editando = null
                aoAbrirProduto(alvo.produto.id)
            },
            aoRecapturar = {
                editando = null
                aoEscanear(ModoScanner.FOTO)
            },
        )
    }

    // Folha de acoes do item (toque longo no cartao): disponibilidade por loja,
    // tirar da lista inteira e atalho para a edicao completa. Marcar/reativar
    // loja NAO fecha a folha - as linhas se atualizam ao vivo pelo estado, e a
    // pessoa pode revisar varias lojas seguidas.
    itemAlvo?.let { alvo ->
        FolhaDeAcoesDoItem(
            item = alvo,
            estabelecimentos = estado.estabelecimentos,
            precos = estado.precos,
            aoFechar = { itemAlvo = null },
            aoTirarDaLista = {
                viewModel.removerItem(alvo)
                Feedback.vibrar(Feedback.TipoDeVibracao.CONFIRMACAO)
                itemAlvo = null
            },
            aoIndisponibilizar = { lojaId ->
                viewModel.marcarIndisponivel(alvo.item, lojaId)
            },
            aoReativar = { lojaId ->
                viewModel.reativarDisponibilidade(alvo.item, lojaId)
            },
            aoEditar = {
                itemAlvo = null
                editando = alvo
            },
            produtoFavorito = alvo.produto.favorito,
            aoFavoritar = { viewModel.marcarFavorito(alvo.produto.id) },
        )
    }

    // Folha da pendencia: grava o preco faltante (ou marca "nao tinha") sem
    // sair da lista. O item e resolvido aqui, e nao mais no editor completo.
    pendenciaAlvo?.let { pendencia ->
        estado.itens.firstOrNull { it.item.id == pendencia.itemId }?.let { itemDaPendencia ->
            FolhaDePrecoDaPendencia(
                nomeProduto = pendencia.nomeProduto,
                nomeDaLoja = pendencia.nomeEstabelecimento,
                aoFechar = { pendenciaAlvo = null },
                aoSalvarPreco = { valor ->
                    viewModel.salvarPrecoDaPendencia(
                        itemDaPendencia.item,
                        pendencia.produtoId,
                        pendencia.estabelecimentoId,
                        valor,
                    )
                    pendenciaAlvo = null
                },
                aoMarcarIndisponivel = {
                    viewModel.marcarIndisponivel(itemDaPendencia.item, pendencia.estabelecimentoId)
                    pendenciaAlvo = null
                },
            )
        }
    }

    comparandoKit?.let { alvo ->
        val precoAtual = lojaAtiva?.let { estadoDePrecos.precoDe(alvo.item.id, it.id) }
        if (precoAtual == null) {
            comparandoKit = null
        } else {
            FolhaDeKitOuAvulso(
                itemComProduto = alvo,
                precoDoKit = precoAtual,
                vereditoDaLista = viewModel.vereditoDeEmbalagem(alvo),
                aoFechar = { comparandoKit = null },
                aoAplicar = { ehKit ->
                    viewModel.salvarEdicao(
                        alvo,
                        alvo.item.copy(ehKit = ehKit, itensPorKit = if (ehKit) alvo.item.itensPorKit else null),
                        alvo.produto.nome,
                        alvo.produto.categoriaId,
                    )
                },
            )
        }
    }

    ajustandoPeso?.let { alvo ->
        FolhaDePesoEstimado(
            itemComProduto = alvo,
            aoFechar = { ajustandoPeso = null },
            aoAplicar = { peso -> viewModel.definirPesoEstimado(alvo, peso) },
        )
    }

    if (trocandoDeLoja) {
        FolhaDeLojas(
            lojas = estado.estabelecimentos,
            lojaAtivaId = lojaAtiva?.id,
            aoFechar = { trocandoDeLoja = false },
            aoSelecionar = precos::selecionarLoja,
            aoCriar = precos::criarOuSelecionarLoja,
        )
    }

    if (renomeando) {
        FolhaDeRenomear(
            nomeAtual = estado.lista?.nome.orEmpty(),
            aoFechar = { renomeando = false },
            aoSalvar = { novo ->
                viewModel.renomearLista(novo)
                renomeando = false
            },
        )
    }

    // Folha de Favoritos (menu da lista): 1 toque no + coloca o produto nesta
    // lista; a estrela desfavorita sem sair daqui.
    if (folhaDeFavoritos) {
        FolhaDeFavoritos(
            favoritos = favoritosDoCatalogo,
            aoFechar = { folhaDeFavoritos = false },
            aoAdicionar = viewModel::adicionarFavorito,
            aoDesfavoritar = { produto -> viewModel.marcarFavorito(produto.id) },
            aoAbrirCatalogo = aoAbrirCatalogo?.let { abrir ->
                {
                    folhaDeFavoritos = false
                    abrir()
                }
            },
        )
    }

    // Dialogo "Definir orçamento" (menu da lista): valor em REAIS (aceita
    // virgula); campo vazio ao salvar REMOVE o orcamento e, quando ele ja
    // existe, o dialogo tambem traz o botao dedicado de remover.
    if (dialogoDeOrcamento) {
        DialogoDeOrcamento(
            orcamentoAtual = estado.orcamentoCentavos,
            aoFechar = { dialogoDeOrcamento = false },
            aoSalvar = { centavos ->
                viewModel.definirOrcamento(centavos)
                dialogoDeOrcamento = false
            },
            aoRemover = {
                viewModel.definirOrcamento(null)
                dialogoDeOrcamento = false
            },
        )
    }
}

@Composable
private fun FolhaDeRenomear(nomeAtual: String, aoFechar: () -> Unit, aoSalvar: (String) -> Unit) {
    var nome by remember { mutableStateOf(nomeAtual) }
    br.com.comprix.presentation.comum.FolhaComprix(
        titulo = "Renomear lista",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Salvar",
                { aoSalvar(nome) },
                bloco = true,
                habilitado = nome.isNotBlank(),
                icone = Icones.confirmar,
            )
        },
    ) {
        CampoComprix(nome, { nome = it }, "Nome da lista")
        EspacoVertical(10.dp)
    }
}

/**
 * Folha de **Favoritos do catalogo**, aberta pelo menu da lista.
 *
 * Cada linha tem duas acoes de um toque: a estrela DESFAVORITA o produto e o
 * (+) manda ele para a lista aberta - que soma a quantidade quando o produto
 * ja estava la, em vez de duplicar o item. Sem favorito nenhum, o estado
 * vazio ensina o caminho (toque longo no item) e oferece o catalogo.
 */
@Composable
private fun FolhaDeFavoritos(
    favoritos: List<Produto>,
    aoFechar: () -> Unit,
    aoAdicionar: (Produto) -> Unit,
    aoDesfavoritar: (Produto) -> Unit,
    aoAbrirCatalogo: (() -> Unit)?,
) {
    val cores = Tema.cores
    FolhaComprix(
        titulo = "Favoritos",
        aoFechar = aoFechar,
    ) {
        if (favoritos.isEmpty()) {
            EstadoVazio(
                icone = Icones.estrela,
                titulo = "Nenhum favorito ainda.",
                descricao = "Toque e segure um item da lista e escolha " +
                    "“Favoritar este produto” — ele volta aqui para reusar em um toque.",
            ) {
                if (aoAbrirCatalogo != null) {
                    BotaoComprix(
                        "Explorar catálogo",
                        aoAbrirCatalogo,
                        icone = Icones.catalogo,
                        estilo = EstiloDeBotao.CONTORNADO,
                    )
                }
            }
        } else {
            Legenda("Toque no + para colocar o produto nesta lista.")
            EspacoVertical(8.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                favoritos.forEach { produto ->
                    LinhaDoFavorito(
                        produto = produto,
                        aoAdicionar = { aoAdicionar(produto) },
                        aoDesfavoritar = { aoDesfavoritar(produto) },
                    )
                    EspacoVertical(2.dp)
                }
            }
            EspacoVertical(8.dp)
            Legenda(
                "Estrela verde = produto favorito; tocá-la tira o produto dos favoritos.",
                cor = cores.apagado,
            )
        }
    }
}

/** Linha da folha de Favoritos: nome do produto + estrela (desfavoritar) + (+) adicionar. */
@Composable
private fun LinhaDoFavorito(
    produto: Produto,
    aoAdicionar: () -> Unit,
    aoDesfavoritar: () -> Unit,
) {
    val cores = Tema.cores
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            produto.nome,
            style = MaterialTheme.typography.bodyLarge,
            color = cores.texto,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BotaoDeIcone(
            Icones.estrela,
            "Remover dos favoritos",
            aoDesfavoritar,
            tinta = cores.verdeTinta,
        )
        BotaoDeIcone(
            Icones.adicionar,
            "Adicionar à lista atual",
            aoAdicionar,
            tinta = cores.verdeTinta,
            fundo = cores.verdeSuave,
        )
    }
}

@Composable
private fun ItemDeMenu(texto: String, icone: Int, marcado: Boolean = false, aoTocar: () -> Unit) {
    val cores = Tema.cores
    DropdownMenuItem(
        text = { Text(texto, style = MaterialTheme.typography.bodyLarge, color = cores.texto) },
        leadingIcon = { IconeComprix(icone, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.apagado) },
        // Estado marcado do menu (ex.: "Modo compras" ligado): marca de
        // confirmacao a direita, sem depender de cor sozinha.
        trailingIcon = {
            if (marcado) {
                IconeComprix(Icones.confirmar, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.acao)
            }
        },
        onClick = aoTocar,
    )
}

/**
 * Total estimado da lista pela loja aberta, caindo para o menor preco
 * conhecido quando aquela loja ainda nao tem o item.
 *
 * E uma estimativa declarada: a doca mostra "Total", e a comparacao completa
 * - com compra mista - fica na tela de comparacao.
 */
private fun totalEstimado(
    itens: List<ItemComProduto>,
    estado: PrecosViewModel.EstadoDePrecos,
): BigDecimal {
    val loja = estado.lojaAtiva
    return itens.fold(BigDecimal.ZERO) { soma, itemComProduto ->
        val daLoja = loja?.let { estado.precoDe(itemComProduto.item.id, it.id) }
        val melhor = daLoja ?: estado.estabelecimentos
            .mapNotNull { estado.precoDe(itemComProduto.item.id, it.id) }
            .minOrNull()
        soma + MotorDePrecos.totalDaLinha(melhor, itemComProduto.item)
    }
}

/** Torrada do microfone: aparelho sem reconhecimento de voz (ou falhou). */
private const val MENSAGEM_VOZ_INDISPONIVEL = "Reconhecimento de voz indisponível neste aparelho."

// =====================================================================================
// Orcamento da lista (doca + dialogo)
// =====================================================================================

/** Converte centavos (Long do orcamento) para o BigDecimal de moeda da UI. */
private fun centavosParaReais(centavos: Long): BigDecimal =
    BigDecimal(centavos).movePointLeft(2)

/**
 * Total estimado em centavos, para comparar com o orcamento (que e um Long
 * em centavos) sem arredondamento decimal na conta do percentual.
 */
private fun centavosDoTotal(total: BigDecimal): Long =
    total.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_EVEN).toLong()

/**
 * Linha do orcamento na doca: "Orçamento: R$ X de R$ Y (72%)", barra fina na
 * cor do nivel (verde ate 79%, ambar de 80% a 99%, vermelho em 100% ou mais)
 * e, quando o total passa do orcamento, a legenda vermelha com o valor do
 * excesso. O percentual escrito garante que a informacao nao mora so na cor.
 */
@Composable
private fun LinhaDoOrcamento(totalCentavos: Long, orcamentoCentavos: Long) {
    val cores = Tema.cores
    val nivel = ListaRepositorio.nivelDoOrcamento(totalCentavos, orcamentoCentavos)
    val percentual = if (orcamentoCentavos > 0) totalCentavos * 100 / orcamentoCentavos else 0L
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Orçamento: ${Formatadores.moeda(centavosParaReais(totalCentavos))} de " +
                "${Formatadores.moeda(centavosParaReais(orcamentoCentavos))} ($percentual%)",
            style = MaterialTheme.typography.titleSmall,
            color = if (nivel == 2) cores.vermelhoTinta else cores.texto,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        EspacoVertical(5.dp)
        BarraDeProgresso(
            fracao = if (orcamentoCentavos > 0) totalCentavos.toFloat() / orcamentoCentavos else 0f,
            cor = when (nivel) {
                2 -> cores.vermelho
                1 -> cores.ambar
                else -> cores.marca
            },
            fundo = cores.contorno,
        )
        if (nivel == 2) {
            EspacoVertical(4.dp)
            Legenda(
                "${Formatadores.moeda(centavosParaReais(totalCentavos - orcamentoCentavos))} acima do orçamento",
                cor = cores.vermelhoTinta,
            )
        }
    }
}

/**
 * Dialogo "Definir orçamento" do menu da lista.
 *
 * O valor e digitado em REAIS (aceita virgula via TextoUtil.paraDecimal) e
 * vira centavos inteiros ao salvar. Campo vazio ao salvar (ou valor menor ou
 * igual a zero) REMOVE o orcamento; quando ele ja existe, o dialogo tambem
 * traz o botao dedicado "Remover orçamento".
 */
@Composable
private fun DialogoDeOrcamento(
    orcamentoAtual: Long?,
    aoFechar: () -> Unit,
    aoSalvar: (Long?) -> Unit,
    aoRemover: () -> Unit,
) {
    var texto by remember {
        mutableStateOf(
            orcamentoAtual?.let { Formatadores.moedaSemSimbolo(centavosParaReais(it)) } ?: "",
        )
    }
    DialogoComprix(
        titulo = "Definir orçamento",
        aoFechar = aoFechar,
        icone = Icones.etiqueta,
        rodape = {
            if (orcamentoAtual != null) {
                BotaoComprix(
                    "Remover orçamento",
                    { aoRemover() },
                    estilo = EstiloDeBotao.TEXTO,
                    bloco = true,
                    icone = Icones.excluir,
                )
                EspacoVertical(6.dp)
            }
            BotaoComprix(
                "Salvar",
                {
                    // Reais (aceita virgula) -> centavos inteiros; vazio ou
                    // invalido = remover. Zero/negativo tambem remove: um
                    // orcamento de R$ 0,00 nao existe.
                    val valor = TextoUtil.paraDecimal(texto)
                    val centavos = valor
                        ?.multiply(BigDecimal(100))
                        ?.setScale(0, RoundingMode.HALF_EVEN)
                        ?.toLong()
                    aoSalvar(centavos?.takeIf { it > 0 })
                },
                bloco = true,
                icone = Icones.confirmar,
            )
        },
    ) {
        CampoComprix(
            valor = texto,
            aoMudar = { entrada -> texto = filtrarEntradaDeMoeda(entrada) },
            rotulo = "Valor do orçamento",
            prefixo = "R$",
            dica = "Quanto você planeja gastar nesta lista. Vazio ao salvar remove o orçamento.",
            tipoDeTeclado = KeyboardType.Decimal,
        )
    }
}

// =====================================================================================
// Modo compras (banner + cartao de sugestoes de recompra)
// =====================================================================================

/**
 * Banner do MODO COMPRAS, fixo no topo do conteudo: progresso grande da
 * compra ("12 de 20 itens · 60%") e a chave "Ocultar comprados".
 *
 * Fica fora do LazyColumn (slot da barra da tela), entao nao rola junto com a
 * lista; a doca continua com o total estimado e a acao de comparar.
 */
@Composable
private fun BannerDoModoCompras(
    comprados: Int,
    total: Int,
    progresso: Float,
    ocultandoComprados: Boolean,
    aoAlternarOcultar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    val percentual = (progresso * 100).roundToInt()
    CartaoComprix(modifier = modifier.fillMaxWidth(), preenchimento = PaddingValues(13.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$comprados de $total itens · $percentual%",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(7.dp)
                BarraDeProgresso(fracao = progresso)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChaveComprix(
                    ativa = ocultandoComprados,
                    aoAlternar = { aoAlternarOcultar() },
                    nomeAcessivel = "Ocultar itens comprados",
                )
                Legenda("Ocultar comprados", maximoDeLinhas = 1)
            }
        }
    }
}

/**
 * Cartao "Comprar de novo" sob a caixa de adicao: chips horizontais rolaveis
 * com os produtos mais registrados no historico de precos que AINDA nao estao
 * nesta lista. O + adiciona com 1 toque pelo mesmo caminho da folha de
 * Favoritos - produto repetido SOMA a quantidade, nunca duplica o item.
 */
@Composable
private fun CartaoDeRecompra(
    sugestoes: List<Produto>,
    aoAdicionar: (Produto) -> Unit,
) {
    val cores = Tema.cores
    CartaoComprix(preenchimento = PaddingValues(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            IconeComprix(Icones.recomecar, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.acao)
            Text("Comprar de novo", style = MaterialTheme.typography.titleSmall, color = cores.texto)
            Legenda("mais anotados no seu histórico", maximoDeLinhas = 1)
        }
        EspacoVertical(6.dp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            sugestoes.forEach { produto ->
                ChipDeRecompra(produto = produto, aoAdicionar = { aoAdicionar(produto) })
            }
        }
    }
}

/** Chip do cartao "Comprar de novo": nome do produto + botao de adicao. */
@Composable
private fun ChipDeRecompra(produto: Produto, aoAdicionar: () -> Unit) {
    val cores = Tema.cores
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cores.verdeSuave)
            .border(1.dp, cores.contorno, RoundedCornerShape(14.dp))
            .padding(start = 13.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            produto.nome,
            style = MaterialTheme.typography.bodyMedium,
            color = cores.verdeTinta,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        BotaoDeIcone(
            Icones.adicionar,
            "Adicionar ${produto.nome} à lista",
            aoAdicionar,
            tinta = cores.verdeTinta,
            tamanhoDoIcone = TamanhoDeIcone.pequeno,
        )
    }
}

package br.com.comprix.presentation.listas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.ResumoDeLista
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.Antetitulo
import br.com.comprix.presentation.comum.BarraDaMarca
import br.com.comprix.presentation.comum.BarraDeProgresso
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.BotaoFlutuante
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FolhaComprix
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

/**
 * Tela inicial: **Minhas Listas** (telas 02 e 11 da referencia).
 *
 * E a unica tela com a barra esmeralda - ela assina o produto. Dai para
 * dentro, as telas sao claras e a cor fica reservada para acao e sinalizacao.
 *
 * Cada cartao de lista carrega, nesta ordem: nome, contagem de itens e lojas,
 * selo de economia estimada quando ja ha comparacao, atalho de duplicar e a
 * barra de progresso do que ja foi para o carrinho.
 */
@Composable
fun ListasScreen(
    viewModel: ListasViewModel,
    aoAbrirLista: (Long) -> Unit,
    navegacao: @Composable () -> Unit,
    aoAbrirLojas: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val modoSelecao by viewModel.modoSelecao.collectAsStateWithLifecycle()
    val idsSelecionados by viewModel.idsSelecionados.collectAsStateWithLifecycle()
    var criando by remember { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf<ResumoDeLista?>(null) }
    var excluindo by remember { mutableStateOf<ResumoDeLista?>(null) }
    var busca by remember { mutableStateOf<String?>(null) }
    var excluindoEmMassa by remember { mutableStateOf(false) }
    var finalizandoEmMassa by remember { mutableStateOf(false) }

    // A busca filtra o que ja esta em memoria: sao dezenas de listas, nao
    // milhares, entao uma consulta nova ao banco a cada tecla seria desperdicio.
    val termo = busca?.trim()?.lowercase().orEmpty()
    fun filtrar(origem: List<ResumoDeLista>) =
        if (termo.isBlank()) origem else origem.filter { it.lista.nome.lowercase().contains(termo) }

    val abertas = filtrar(estado.abertas)
    val finalizadas = filtrar(estado.finalizadas)
    val favoritas = filtrar(estado.favoritas)
    val semResultado = termo.isNotBlank() && favoritas.isEmpty() && abertas.isEmpty() && finalizadas.isEmpty()

    TelaComprix(
        barra = {
            BarraDaMarca {
                // No modo selecao a busca some (visual limpo) - a barra de
                // selecao toma o lugar dela no conteudo da tela.
                if (!modoSelecao) {
                    BotaoDeIcone(
                        icone = if (busca == null) Icones.buscar else Icones.fechar,
                        descricao = if (busca == null) "Buscar listas" else "Fechar busca",
                        aoTocar = { busca = if (busca == null) "" else null },
                    )
                }
                if (aoAbrirLojas != null) {
                    BotaoDeIcone(
                        icone = Icones.loja,
                        descricao = "Minhas lojas",
                        aoTocar = aoAbrirLojas,
                    )
                }
            }
        },
        navegacao = navegacao,
        sobreposicao = {
            if (!modoSelecao &&
                (estado.favoritas.isNotEmpty() || estado.abertas.isNotEmpty() || estado.finalizadas.isNotEmpty())
            ) {
                BotaoFlutuante(
                    texto = "Criar nova lista",
                    icone = Icones.adicionar,
                    aoTocar = { criando = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 17.dp, bottom = 17.dp),
                )
            }
            if (mensagem != null) {
                Torrada(
                    mensagem.orEmpty(),
                    aoDescartar = { viewModel.mensagemExibida() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp),
                )
            }
        },
    ) {
        LaunchedEffect(mensagem) {
            if (mensagem != null) {
                kotlinx.coroutines.delay(3_200)
                viewModel.mensagemExibida()
            }
        }

        if (estado.carregando) {
            // Mesmo padrao da LojasScreen: nada de LazyColumn vazia piscando
            // enquanto o banco emite o primeiro resumo.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Legenda("Abrindo…")
            }
        } else if (estado.vazio) {
            Column(Modifier.fillMaxSize().padding(PREENCHIMENTO_DA_TELA)) {
                TituloDaTela("Minhas listas")
                EstadoVazio(
                    icone = Icones.cesta,
                    titulo = "Nenhuma lista ainda.",
                    descricao = "Comece criando sua primeira lista de compras agora.",
                    modifier = Modifier.weight(1f),
                ) {
                    BotaoComprix("Nova lista", { criando = true }, icone = Icones.adicionar)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PREENCHIMENTO_DA_TELA,
            ) {
                item {
                    TituloDaTela("Minhas listas")
                    EspacoVertical(14.dp)
                }

                // No modo selecao a dica e a busca somem: a barra de selecao
                // ocupa o lugar delas e o visual fica limpo para a acao em massa.
                if (estado.mostrarDica && !modoSelecao) {
                    item {
                        CartaoComprix(preenchimento = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
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
                                    "Dica: toque nos botões do card para renomear, duplicar, " +
                                        "reabrir, favoritar ou excluir, e segure o dedo no card " +
                                        "para selecionar várias listas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cores.texto,
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
                        EspacoVertical(12.dp)
                    }
                }

                if (busca != null && !modoSelecao) {
                    item {
                        CampoComprix(
                            valor = busca.orEmpty(),
                            aoMudar = { busca = it },
                            rotulo = "Buscar pelo nome da lista",
                        )
                        EspacoVertical(14.dp)
                    }
                }

                if (modoSelecao) {
                    item {
                        BarraDeSelecao(
                            quantidade = idsSelecionados.size,
                            aoFechar = viewModel::limparSelecao,
                            aoSelecionarTodas = {
                                viewModel.selecionarTodas((favoritas + abertas + finalizadas).map { it.lista.id })
                            },
                            aoFavoritar = viewModel::alternarFavoritas,
                            aoFinalizar = { finalizandoEmMassa = true },
                            aoExcluir = { excluindoEmMassa = true },
                        )
                        EspacoVertical(12.dp)
                    }
                }

                if (semResultado) {
                    item {
                        EstadoVazio(
                            icone = Icones.buscar,
                            titulo = "Nenhuma lista com esse nome.",
                            descricao = "Tente outra palavra ou crie uma lista nova com esse título.",
                        ) {
                            BotaoComprix("Criar “${busca.orEmpty().trim()}”", {
                                viewModel.criarLista(busca?.trim()) { id -> aoAbrirLista(id) }
                            }, icone = Icones.adicionar)
                        }
                    }
                }

                // Secao Favoritas: qualquer lista com estrela (aberta ou
                // finalizada) sobe para ca e sai das secoes de baixo. No modo
                // selecao ela continua listada - o cartao e o mesmo.
                if (favoritas.isNotEmpty()) {
                    item {
                        EspacoVertical(4.dp)
                        Antetitulo("Favoritas")
                        EspacoVertical(10.dp)
                    }
                    items(favoritas, key = { "fav-${it.lista.id}" }) { resumo ->
                        CartaoDeLista(
                            resumo = resumo,
                            aoAbrir = { aoAbrirLista(resumo.lista.id) },
                            aoDuplicar = { viewModel.duplicar(resumo) },
                            aoRenomear = { renomeando = resumo },
                            aoExcluir = { excluindo = resumo },
                            aoFavoritar = { viewModel.alternarFavorita(resumo) },
                            aoReabrir = if (resumo.lista.finalizada) {
                                { viewModel.reabrir(resumo) }
                            } else {
                                null
                            },
                            emSelecao = modoSelecao,
                            selecionado = resumo.lista.id in idsSelecionados,
                            aoAlternarSelecao = { viewModel.alternarSelecao(resumo.lista.id) },
                            aoManterPressionado = { viewModel.entrarEmSelecao(resumo.lista.id) },
                        )
                        EspacoVertical(12.dp)
                    }
                }

                items(abertas, key = { it.lista.id }) { resumo ->
                    CartaoDeLista(
                        resumo = resumo,
                        aoAbrir = { aoAbrirLista(resumo.lista.id) },
                        aoDuplicar = { viewModel.duplicar(resumo) },
                        aoRenomear = { renomeando = resumo },
                        aoExcluir = { excluindo = resumo },
                        aoFavoritar = { viewModel.alternarFavorita(resumo) },
                        emSelecao = modoSelecao,
                        selecionado = resumo.lista.id in idsSelecionados,
                        aoAlternarSelecao = { viewModel.alternarSelecao(resumo.lista.id) },
                        aoManterPressionado = { viewModel.entrarEmSelecao(resumo.lista.id) },
                    )
                    EspacoVertical(12.dp)
                }

                if (finalizadas.isNotEmpty()) {
                    item {
                        EspacoVertical(10.dp)
                        Antetitulo("Compras finalizadas")
                        EspacoVertical(10.dp)
                    }
                    items(finalizadas, key = { "fim-${it.lista.id}" }) { resumo ->
                        CartaoDeLista(
                            resumo = resumo,
                            aoAbrir = { aoAbrirLista(resumo.lista.id) },
                            aoDuplicar = { viewModel.duplicar(resumo) },
                            aoRenomear = { renomeando = resumo },
                            aoExcluir = { excluindo = resumo },
                            aoFavoritar = { viewModel.alternarFavorita(resumo) },
                            aoReabrir = { viewModel.reabrir(resumo) },
                            emSelecao = modoSelecao,
                            selecionado = resumo.lista.id in idsSelecionados,
                            aoAlternarSelecao = { viewModel.alternarSelecao(resumo.lista.id) },
                            aoManterPressionado = { viewModel.entrarEmSelecao(resumo.lista.id) },
                        )
                        EspacoVertical(12.dp)
                    }
                }

                item {
                    EspacoVertical(14.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconeComprix(
                            Icones.offline,
                            null,
                            tamanho = 14.dp,
                            tinta = cores.apagado,
                        )
                        Legenda("Seus dados ficam só neste aparelho. Nenhum login, nenhuma nuvem.")
                    }
                    // Espaco para o botao flutuante nao cobrir o ultimo cartao.
                    EspacoVertical(78.dp)
                }
            }
        }
    }

    if (criando) {
        FolhaDeNovaLista(
            aoFechar = { criando = false },
            aoCriar = { nome ->
                criando = false
                viewModel.criarLista(nome) { id -> aoAbrirLista(id) }
            },
        )
    }

    renomeando?.let { alvo ->
        DialogoDeRenomear(
            nomeAtual = alvo.lista.nome,
            aoFechar = { renomeando = null },
            aoSalvar = { novo ->
                viewModel.renomear(alvo, novo)
                renomeando = null
            },
        )
    }

    excluindo?.let { alvo ->
        DialogoDeExclusao(
            nomeDaLista = alvo.lista.nome,
            aoFechar = { excluindo = null },
            aoConfirmar = {
                viewModel.remover(alvo)
                excluindo = null
            },
        )
    }

    if (excluindoEmMassa) {
        DialogoDeExclusaoEmMassa(
            quantidade = idsSelecionados.size,
            aoFechar = { excluindoEmMassa = false },
            aoConfirmar = {
                excluindoEmMassa = false
                viewModel.excluirSelecionadas()
            },
        )
    }

    if (finalizandoEmMassa) {
        DialogoDeFinalizacaoEmMassa(
            quantidade = idsSelecionados.size,
            aoFechar = { finalizandoEmMassa = false },
            aoConfirmar = {
                finalizandoEmMassa = false
                viewModel.finalizarSelecionadas()
            },
        )
    }
}

/** Dialogo de renomear no cartao da lista: campo preenchido + Cancelar/Salvar. */
@Composable
private fun DialogoDeRenomear(
    nomeAtual: String,
    aoFechar: () -> Unit,
    aoSalvar: (String) -> Unit,
) {
    var nome by remember { mutableStateOf(nomeAtual) }
    DialogoComprix(
        titulo = "Renomear lista",
        aoFechar = aoFechar,
        icone = Icones.editar,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Cancelar",
                    aoFechar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix(
                    "Salvar",
                    { aoSalvar(nome) },
                    modifier = Modifier.weight(1f),
                    habilitado = nome.isNotBlank(),
                    icone = Icones.confirmar,
                )
            }
        },
    ) {
        CampoComprix(
            valor = nome,
            aoMudar = { nome = it },
            rotulo = "Nome da lista",
        )
    }
}

/** Dialogo de exclusao da lista: confirmacao obrigatoria antes do remover. */
@Composable
private fun DialogoDeExclusao(
    nomeDaLista: String,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val cores = Tema.cores
    DialogoComprix(
        titulo = "Excluir \"$nomeDaLista\"?",
        aoFechar = aoFechar,
        icone = Icones.excluir,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Cancelar",
                    aoFechar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix(
                    "Excluir",
                    aoConfirmar,
                    estilo = EstiloDeBotao.PERIGO,
                    modifier = Modifier.weight(1f),
                    icone = Icones.excluir,
                )
            }
        },
    ) {
        Text(
            "Os itens e os preços registrados nela serão apagados. " +
                "Essa ação não pode ser desfeita.",
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
        )
    }
}

/**
 * Barra do modo selecao em massa: contagem e acoes de grupo, no lugar da
 * busca e da dica. Fechar sai do modo; as acoes destrutivas (finalizar,
 * excluir) abrem confirmacao antes de tocar no ViewModel.
 */
@Composable
private fun BarraDeSelecao(
    quantidade: Int,
    aoFechar: () -> Unit,
    aoSelecionarTodas: () -> Unit,
    aoFavoritar: () -> Unit,
    aoFinalizar: () -> Unit,
    aoExcluir: () -> Unit,
) {
    val cores = Tema.cores
    CartaoComprix(preenchimento = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BotaoDeIcone(Icones.fechar, "Sair da seleção", aoFechar)
            Text(
                "$quantidade ${if (quantidade == 1) "selecionada" else "selecionadas"}",
                style = MaterialTheme.typography.titleSmall,
                color = cores.verdeTinta,
                modifier = Modifier.weight(1f),
            )
            BotaoDeIcone(Icones.confirmar, "Selecionar todas", aoSelecionarTodas)
            BotaoDeIcone(Icones.estrela, "Favoritar ou desfavoritar", aoFavoritar)
            BotaoDeIcone(Icones.confirmarCirculo, "Finalizar listas abertas", aoFinalizar)
            BotaoDeIcone(Icones.excluir, "Excluir seleção", aoExcluir, tinta = cores.vermelhoTinta)
        }
    }
}

/** Confirmacao de exclusao em massa: o numero de listas entra no titulo e o PERIGO no botao. */
@Composable
private fun DialogoDeExclusaoEmMassa(
    quantidade: Int,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val cores = Tema.cores
    DialogoComprix(
        titulo = "Excluir $quantidade ${if (quantidade == 1) "lista" else "listas"}?",
        aoFechar = aoFechar,
        icone = Icones.excluir,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Cancelar",
                    aoFechar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix(
                    "Excluir",
                    aoConfirmar,
                    estilo = EstiloDeBotao.PERIGO,
                    modifier = Modifier.weight(1f),
                    icone = Icones.excluir,
                )
            }
        },
    ) {
        Text(
            "Elas vão para a lixeira e ficam recuperáveis por 30 dias. " +
                "Depois disso, itens e preços são apagados para sempre.",
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
        )
    }
}

/** Confirmacao de finalizacao em massa: so listas abertas, as finalizadas sao puladas. */
@Composable
private fun DialogoDeFinalizacaoEmMassa(
    quantidade: Int,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val cores = Tema.cores
    DialogoComprix(
        titulo = "Finalizar $quantidade ${if (quantidade == 1) "lista" else "listas"}?",
        aoFechar = aoFechar,
        icone = Icones.confirmarCirculo,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Cancelar",
                    aoFechar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix(
                    "Finalizar",
                    aoConfirmar,
                    modifier = Modifier.weight(1f),
                    icone = Icones.confirmarCirculo,
                )
            }
        },
    ) {
        Text(
            "Cada lista aberta selecionada é gravada no histórico, como na compra individual. " +
                "As que já estão finalizadas são puladas.",
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
        )
    }
}

/**
 * Cartao de uma lista na tela inicial (`.list-card`).
 *
 * Selecao em massa: o TOQUE LONGO em qualquer area livre do cartao entra no
 * modo ([aoManterPressionado]); dentro do modo, o toque simples alterna a
 * marcacao ([aoAlternarSelecao]) e o cartao ganha marca de check + borda
 * verde. Fora do modo o toque simples nao faz nada (abrir segue no botao,
 * como sempre) e nenhum visual muda.
 *
 * Favorita: a estrela ao lado do nome aparece quando a lista e favorita
 * ([resumo.lista.favorita]) e o botao de estrela da linha de acoes alterna -
 * o repositorio grava a flag e a secao "Favoritas" se move sozinha.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartaoDeLista(
    resumo: ResumoDeLista,
    aoAbrir: () -> Unit,
    aoDuplicar: () -> Unit,
    modifier: Modifier = Modifier,
    aoRenomear: (() -> Unit)? = null,
    aoExcluir: (() -> Unit)? = null,
    aoFavoritar: (() -> Unit)? = null,
    aoReabrir: (() -> Unit)? = null,
    emSelecao: Boolean = false,
    selecionado: Boolean = false,
    aoAlternarSelecao: (() -> Unit)? = null,
    aoManterPressionado: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    // combinedClickable so entra com gesto de selecao pedido; sem indication
    // para manter o visual sem realce (a marcacao de selecao e desenhada aqui).
    val modificadorDeSelecao = if (aoManterPressionado != null || aoAlternarSelecao != null) {
        Modifier.combinedClickable(
            interactionSource = null,
            indication = null,
            onClick = { if (emSelecao) aoAlternarSelecao?.invoke() },
            onLongClick = { aoManterPressionado?.invoke() },
            onLongClickLabel = "Selecionar lista",
        )
    } else {
        Modifier
    }
    CartaoComprix(
        modifier.fillMaxWidth().then(modificadorDeSelecao),
        preenchimento = androidx.compose.foundation.layout.PaddingValues(17.dp),
        corDaBorda = if (selecionado) cores.verdeTinta else null,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ALVO_MINIMO),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emSelecao) {
                // Marca de selecao: circulo vazio vira verde com o check quando
                // o cartao entra no conjunto (mesma linguagem da pastilha de
                // categoria selecionavel).
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selecionado) cores.verdeSuave else Color.Transparent)
                        .border(
                            if (selecionado) 2.dp else 1.8.dp,
                            if (selecionado) cores.verdeTinta else cores.contornoForte,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selecionado) {
                        IconeComprix(Icones.confirmar, null, tamanho = 16.dp, tinta = cores.verdeTinta)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        resumo.lista.nome,
                        style = MaterialTheme.typography.headlineSmall,
                        color = cores.texto,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (resumo.lista.favorita) {
                        // Estrela preenchida (verde) ao lado do nome: o mesmo
                        // simbolo do botao de favoritar, so que de leitura.
                        IconeComprix(
                            Icones.estrela,
                            "Lista favorita",
                            tamanho = TamanhoDeIcone.pequeno,
                            tinta = cores.verdeTinta,
                        )
                    }
                }
                EspacoVertical(4.dp)
                Legenda(descricaoDaLista(resumo))
            }
            Box(
                Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cores.verdeSuave),
                contentAlignment = Alignment.Center,
            ) {
                IconeComprix(
                    if (resumo.lista.finalizada) Icones.confirmarCirculo else Icones.calendario,
                    null,
                    tamanho = 20.dp,
                    tinta = cores.verdeTinta,
                )
            }
        }

        if (resumo.totalEstimado.signum() > 0) {
            EspacoVertical(10.dp)
            Selo(
                texto = "Total estimado: ${Formatadores.moeda(resumo.totalEstimado)}",
                tom = TomDoSelo.OURO,
                icone = Icones.etiqueta,
            )
        }
        if (resumo.itensSemPreco > 0) {
            EspacoVertical(8.dp)
            Selo(
                texto = "${resumo.itensSemPreco} ${plural(resumo.itensSemPreco, "item sem preço", "itens sem preço")}",
                tom = TomDoSelo.NEUTRO,
                icone = Icones.informacao,
            )
        }

        EspacoVertical(11.dp)
        // FlowRow: com cinco acoes (abertas e finalizadas) uma Row comum
        // transbordaria em telas estreitas; aqui a linha quebra com o mesmo
        // espacamento da familia (8 dp).
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotaoComprix(
                "Abrir",
                aoAbrir,
                estilo = EstiloDeBotao.PRINCIPAL,
                compacto = true,
                icone = Icones.seta,
            )
            if (aoRenomear != null) {
                BotaoComprix(
                    "Renomear",
                    aoRenomear,
                    estilo = EstiloDeBotao.CONTORNADO,
                    compacto = true,
                    icone = Icones.editar,
                )
            }
            BotaoComprix(
                "Duplicar lista",
                aoDuplicar,
                estilo = EstiloDeBotao.CONTORNADO,
                compacto = true,
                icone = Icones.duplicar,
            )
            if (aoReabrir != null) {
                BotaoComprix("Reabrir", aoReabrir, estilo = EstiloDeBotao.TEXTO, compacto = true)
            }
            if (aoFavoritar != null) {
                BotaoDeIcone(
                    Icones.estrela,
                    if (resumo.lista.favorita) "Remover dos favoritos" else "Marcar como favorita",
                    aoFavoritar,
                    tinta = if (resumo.lista.favorita) cores.verdeTinta else cores.apagado,
                )
            }
            if (aoExcluir != null) {
                BotaoDeIcone(
                    Icones.excluir,
                    "Excluir lista",
                    aoExcluir,
                    tinta = cores.vermelhoTinta,
                )
            }
        }

        EspacoVertical(10.dp)
        BarraDeProgresso(resumo.progresso)
    }
}

private fun descricaoDaLista(resumo: ResumoDeLista): String = buildString {
    append(resumo.totalDeItens)
    append(if (resumo.totalDeItens == 1) " item" else " itens")
    if (resumo.lista.finalizada) {
        append(", concluída")
    } else if (resumo.itensComprados > 0) {
        append(", ").append(resumo.itensComprados).append(" no carrinho")
    }
}

private fun plural(quantidade: Int, singular: String, plural: String) =
    if (quantidade == 1) singular else plural

/**
 * Folha de criacao de lista (tela 11 da referencia).
 *
 * Ja vem com um nome sugerido pronto e selecionavel, porque na pratica a
 * maioria das listas e "a compra deste mes" - e obrigar a digitar um nome
 * antes de digitar o primeiro item e atrito puro.
 */
@Composable
private fun FolhaDeNovaLista(aoFechar: () -> Unit, aoCriar: (String?) -> Unit) {
    val cores = Tema.cores
    var nome by remember { mutableStateOf(Formatadores.nomeSugeridoDeLista()) }
    val sugestoes = listOf("Compra do Mês", "Feira Semanal", "Churrasco", "Farmácia e Higiene")

    FolhaComprix(
        titulo = "Criar nova lista",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Criar e adicionar itens",
                { aoCriar(nome.ifBlank { null }) },
                bloco = true,
                icone = Icones.adicionar,
            )
        },
    ) {
        CampoComprix(
            valor = nome,
            aoMudar = { nome = it },
            rotulo = "Nome da lista",
            dica = "Pode trocar depois, no menu da lista.",
        )
        EspacoVertical(16.dp)
        Text("Sugestões", style = MaterialTheme.typography.titleSmall, color = cores.texto)
        EspacoVertical(8.dp)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sugestoes.forEach { sugestao ->
                PastilhaSelecionavel(
                    texto = sugestao,
                    selecionada = nome == sugestao,
                    aoTocar = { nome = sugestao },
                )
            }
        }
        EspacoVertical(8.dp)
        Spacer(Modifier.heightIn(min = 4.dp))
        Legenda(
            "Para repetir uma compra anterior, use “Duplicar lista” no cartão " +
                "dela — os itens vêm junto, os preços não.",
        )
    }
}

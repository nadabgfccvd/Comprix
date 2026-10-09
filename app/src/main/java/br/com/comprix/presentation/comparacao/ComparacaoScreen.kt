package br.com.comprix.presentation.comparacao

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.DocaInferior
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FaixaDePendencias
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PainelDePendencias
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.comum.areaQueConfirmaAoTocarFora
import br.com.comprix.presentation.comum.filtrarEntradaDeMoeda
import br.com.comprix.presentation.comum.tocarSemRealce
import br.com.comprix.presentation.lojas.CartaoDeLoja
import br.com.comprix.presentation.lojas.DialogoDeExclusao
import br.com.comprix.presentation.lojas.DialogoDeRenomear
import br.com.comprix.presentation.lojas.LojasViewModel
import br.com.comprix.presentation.lojas.contagemDaLoja
import br.com.comprix.presentation.precos.PrecosViewModel
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.presentation.tema.corDeHex
import br.com.comprix.presentation.tema.corDeTextoSobre
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * **Comparar estabelecimentos** (tela 05 da referencia) e, apos finalizar, o
 * resumo da compra (tela 16).
 *
 * Fluxo em tres tempos:
 *
 * 1. **auditar** - o painel de pendencias diz se a comparacao ja e confiavel;
 * 2. **comparar** - a matriz mostra menor preco por linha, total por loja e a
 *    compra mista otima;
 * 3. **finalizar** - grava a compra no historico e mostra a celebracao.
 *
 * A tela nunca esconde que faltam precos: comparar com buraco e pior do que
 * nao comparar, porque a loja incompleta sempre parece a mais barata.
 */
@Composable
fun ComparacaoScreen(
    viewModel: PrecosViewModel,
    viewModelDasLojas: LojasViewModel,
    abrirEmFinalizar: Boolean,
    aoVoltar: () -> Unit,
    aoVerHistorico: () -> Unit,
    aoVoltarParaListas: () -> Unit,
    aoGerenciarLojas: () -> Unit,
    navegacao: @Composable () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val estadoDasLojas by viewModelDasLojas.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val desfazer by viewModel.desfazer.collectAsStateWithLifecycle()
    val gerenciadorDeFoco = LocalFocusManager.current

    val rolagemDaMatriz = rememberScrollState()
    var editando by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var novaLoja by remember { mutableStateOf(false) }
    var copiandoPrecos by remember { mutableStateOf(false) }
    var usarCompraMista by remember { mutableStateOf(true) }
    var concluida by remember { mutableStateOf(false) }
    var lojaEmAcao by remember { mutableStateOf<Estabelecimento?>(null) }
    var renomeando by remember { mutableStateOf<Estabelecimento?>(null) }
    var excluindo by remember { mutableStateOf<Estabelecimento?>(null) }

    LaunchedEffect(abrirEmFinalizar) { if (abrirEmFinalizar) usarCompraMista = true }
    // Contagens da rede para a folha de opcoes da loja (snapshot ao entrar).
    LaunchedEffect(Unit) { viewModelDasLojas.atualizar() }
    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            kotlinx.coroutines.delay(3_200)
            viewModel.mensagemExibida()
        }
    }
    LaunchedEffect(estadoDasLojas.mensagem) {
        if (estadoDasLojas.mensagem != null) {
            kotlinx.coroutines.delay(3_200)
            viewModelDasLojas.mensagemExibida()
        }
    }

    val matriz = estado.matriz
    val compraMista = matriz?.compraMista
    val escolha = if (usarCompraMista) compraMista?.escolhaPorItem.orEmpty() else emptyMap()
    val previa = remember(estado, usarCompraMista) { viewModel.previaDaCompra(escolha) }
    // Guard visual de "nao finalizar 2x": a tela segue ABERTA para comparar e
    // consultar, mas o botao de fechar a conta desliga e a legenda explica.
    val listaFinalizada = estado.lista?.finalizada == true

    if (concluida) {
        TelaComprix(
            barra = { BarraSimples("Compra concluída") },
            navegacao = navegacao,
        ) {
            ResumoDaCompraConcluida(
                resultado = previa,
                totaisPorLoja = matriz?.totais.orEmpty(),
                compraMista = compraMista,
                conflitosDeAlergenicos = matriz?.linhas?.count { it.alertaRestricao != null } ?: 0,
                aoVerHistorico = aoVerHistorico,
                aoVoltarParaListas = aoVoltarParaListas,
            )
        }
        return
    }

    TelaComprix(
        modifier = Modifier.areaQueConfirmaAoTocarFora(gerenciadorDeFoco),
        barra = {
            BarraSimples("Comparar estabelecimentos", aoVoltar = aoVoltar) {
                BotaoDeIcone(Icones.duplicar, "Copiar preços entre lojas", { copiandoPrecos = true })
                BotaoDeIcone(Icones.loja, "Gerenciar lojas", aoGerenciarLojas)
                BotaoDeIcone(Icones.adicionar, "Adicionar loja", { novaLoja = true })
            }
        },
        navegacao = navegacao,
        doca = {
            if (matriz != null) {
                DocaInferior {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Legenda(if (usarCompraMista) "Compra mista ótima" else "Comprando tudo numa loja só")
                            Text(
                                Formatadores.moeda(previa.totalPago),
                                style = MaterialTheme.typography.headlineMedium,
                                color = cores.verdeTinta,
                            )
                        }
                        if (previa.economia.signum() > 0) {
                            Selo(
                                texto = "Economia " + Formatadores.moeda(previa.economia),
                                tom = TomDoSelo.OURO,
                                icone = Icones.trofeu,
                            )
                        }
                    }
                    EspacoVertical(9.dp)
                    if (listaFinalizada) {
                        Legenda("Esta conta já foi finalizada — você pode comparar e consultar sem finalizar de novo.")
                        EspacoVertical(6.dp)
                    }
                    BotaoComprix(
                        "Finalizar compra",
                        {
                            viewModel.finalizarCompra(escolha) { concluida = true }
                        },
                        bloco = true,
                        icone = Icones.confirmarCirculo,
                        habilitado = !listaFinalizada && previa.quantidadeItens > 0,
                    )
                }
            }
        },
        sobreposicao = {
            when {
                desfazer != null -> Torrada(
                    texto = desfazer?.descricao.orEmpty(),
                    rotuloDaAcao = "Desfazer",
                    aoAcionar = viewModel::desfazerUltimaAcao,
                    aoDescartar = viewModel::descartarDesfazer,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )

                mensagem != null -> Torrada(
                    mensagem.orEmpty(),
                    aoDescartar = viewModel::mensagemExibida,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )

                estadoDasLojas.mensagem != null -> Torrada(
                    estadoDasLojas.mensagem.orEmpty(),
                    aoDescartar = { viewModelDasLojas.mensagemExibida() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            if (estado.carregando) {
                // Enquanto o banco emite o primeiro estado, a matriz ainda e
                // nula - mostrar "Falta uma segunda loja" aqui enganaria.
                item {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Legenda("Abrindo…")
                    }
                }
                return@LazyColumn
            }

            item {
                TituloDaTela(
                    estado.lista?.nome.orEmpty(),
                    subtitulo = "${estado.itens.size} itens • ${estado.estabelecimentos.size} " +
                        if (estado.estabelecimentos.size == 1) "loja" else "lojas",
                )
                EspacoVertical(14.dp)
                PainelDePendencias(
                    relatorio = estado.relatorio,
                    totalDeLojas = estado.estabelecimentos.size,
                    aoAdicionarLoja = { novaLoja = true },
                    aoAbrirPendencia = { pendencia ->
                        editando = pendencia.itemId to pendencia.estabelecimentoId
                    },
                )
                EspacoVertical(14.dp)
            }

            if (matriz == null) {
                item {
                    EstadoVazio(
                        icone = Icones.comparar,
                        titulo = "Falta uma segunda loja.",
                        descricao = "A comparação precisa de pelo menos duas colunas de preço " +
                            "sobre a mesma lista de produtos.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            BotaoComprix("Adicionar loja", { novaLoja = true }, icone = Icones.adicionar)
                            BotaoComprix(
                                "Gerenciar lojas",
                                aoGerenciarLojas,
                                estilo = EstiloDeBotao.CONTORNADO,
                                icone = Icones.loja,
                            )
                        }
                    }
                }
            } else {
                item {
                    MatrizDePrecos(
                        estabelecimentos = matriz.estabelecimentos,
                        linhas = matriz.linhas,
                        rolagem = rolagemDaMatriz,
                        aoTocarCelula = { itemId, lojaId -> editando = itemId to lojaId },
                        aoTocarLinha = { itemId ->
                            estado.estabelecimentos.firstOrNull()?.let { editando = itemId to it.id }
                        },
                        aoAdicionarLoja = { novaLoja = true },
                    )
                    EspacoVertical(8.dp)
                    Legenda("Arraste a tabela para o lado para ver as outras lojas.")
                }

                item {
                    EspacoVertical(16.dp)
                    matriz.totais.filter { !it.cestaCompleta }.forEach { total ->
                        FaixaDePendencias(
                            quantidade = total.itensAusentes.size + total.itensSemPreco.size,
                            nomeDaLoja = total.nome,
                        )
                        EspacoVertical(8.dp)
                    }
                }

                item {
                    EspacoVertical(10.dp)
                    Text(
                        "Total por loja",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(10.dp)
                    val melhorCompleta = matriz.totais
                        .filter { it.cestaCompleta }
                        .minByOrNull { it.total }
                    matriz.totais.forEach { total ->
                        CartaoDeTotalDaLoja(
                            total = total,
                            melhorCestaCompleta = total.estabelecimentoId == melhorCompleta?.estabelecimentoId,
                            aoGerenciar = {
                                estado.estabelecimentos
                                    .firstOrNull { it.id == total.estabelecimentoId }
                                    ?.let { lojaEmAcao = it }
                            },
                        )
                        EspacoVertical(9.dp)
                    }
                }

                if (compraMista != null) {
                    item {
                        EspacoVertical(8.dp)
                        PainelDeCompraMista(
                            compraMista = compraMista,
                            aoAplicar = { usarCompraMista = true },
                        )
                        EspacoVertical(10.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoComprix(
                                "Usar compra mista",
                                { usarCompraMista = true },
                                estilo = if (usarCompraMista) EstiloDeBotao.TONAL else EstiloDeBotao.CONTORNADO,
                                compacto = true,
                                icone = if (usarCompraMista) Icones.confirmar else null,
                            )
                            BotaoComprix(
                                "Comprar numa loja só",
                                { usarCompraMista = false },
                                estilo = if (!usarCompraMista) EstiloDeBotao.TONAL else EstiloDeBotao.CONTORNADO,
                                compacto = true,
                                icone = if (!usarCompraMista) Icones.confirmar else null,
                            )
                        }
                    }
                }

                item {
                    EspacoVertical(16.dp)
                    Legenda(
                        "Totais contam preço × quantidade. Linhas sem preço em alguma loja " +
                            "não entram no menor preço daquela linha — por isso o painel " +
                            "no topo avisa antes de você decidir.",
                    )
                    EspacoVertical(18.dp)
                }
            }
        }
    }

    editando?.let { (itemId, lojaId) ->
        val item = estado.itens.firstOrNull { it.item.id == itemId } ?: return@let
        val loja = estado.estabelecimentos.firstOrNull { it.id == lojaId } ?: return@let
        FolhaDePrecoDaCelula(
            nomeDoItem = item.produto.nome,
            detalhe = Formatadores.descricaoEmbalagem(item.item.quantidade, item.item.unidade, item.item.pesoOuVolume, item.item.itensPorKit),
            nomeDaLoja = loja.nome,
            precoAtual = estado.precoDe(itemId, lojaId),
            aoFechar = { editando = null },
            aoGravar = { valor -> viewModel.anotarPreco(itemId, lojaId, valor) },
            aoMarcarIndisponivel = {
                viewModel.marcarIndisponivel(itemId, lojaId)
                editando = null
            },
            aoLimpar = {
                viewModel.limparPrecoDaLoja(itemId, lojaId)
                editando = null
            },
            aoRemoverDaLista = {
                viewModel.removerItemDaListaMestra(itemId)
                editando = null
            },
        )
    }

    if (novaLoja) {
        FolhaDeNovaLoja(
            lojasExistentes = estado.estabelecimentos.map { it.nome },
            aoFechar = { novaLoja = false },
            aoCriar = { nome ->
                viewModel.criarOuSelecionarLoja(nome)
                novaLoja = false
            },
        )
    }

    if (copiandoPrecos) {
        FolhaDeCopiaDePrecos(
            lojas = estado.estabelecimentos,
            aoFechar = { copiandoPrecos = false },
            aoCopiar = { de, para ->
                viewModel.copiarPrecosEntreLojas(de.id, para.id)
                copiandoPrecos = false
            },
        )
    }

    lojaEmAcao?.let { loja ->
        FolhaDeOpcoesDaLoja(
            loja = loja,
            contagem = estadoDasLojas.lojas
                .firstOrNull { it.estabelecimento.id == loja.id }
                ?.let(::contagemDaLoja),
            aoFechar = { lojaEmAcao = null },
            aoRenomear = {
                lojaEmAcao = null
                renomeando = loja
            },
            aoExcluir = {
                lojaEmAcao = null
                excluindo = loja
            },
        )
    }

    renomeando?.let { alvo ->
        DialogoDeRenomear(
            loja = alvo,
            aoFechar = { renomeando = null },
            aoSalvar = { novo ->
                viewModelDasLojas.renomear(alvo, novo)
                renomeando = null
            },
        )
    }

    excluindo?.let { alvo ->
        DialogoDeExclusao(
            loja = alvo,
            aoFechar = { excluindo = null },
            aoConfirmar = {
                viewModelDasLojas.remover(alvo)
                excluindo = null
            },
        )
    }
}

/**
 * Folha de copiar precos: escolhe origem e destino nos chips e o ViewModel
 * replica as celulas. Valida que sao lojas diferentes antes de habilitar.
 */
@Composable
private fun FolhaDeCopiaDePrecos(
    lojas: List<Estabelecimento>,
    aoFechar: () -> Unit,
    aoCopiar: (Estabelecimento, Estabelecimento) -> Unit,
) {
    var origem by remember { mutableStateOf<Estabelecimento?>(null) }
    var destino by remember { mutableStateOf<Estabelecimento?>(null) }

    FolhaComprix(
        titulo = "Copiar preços entre lojas",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Copiar",
                {
                    val de = origem
                    val para = destino
                    if (de != null && para != null) aoCopiar(de, para)
                },
                bloco = true,
                icone = Icones.duplicar,
                habilitado = origem != null && destino != null && origem?.id != destino?.id,
            )
        },
    ) {
        Legenda(
            "Reproduz na loja de destino os preços já anotados na loja de origem. " +
                "O que não existia na origem continua sem preço no destino.",
        )
        EspacoVertical(14.dp)

        Text(
            "Loja de origem",
            style = MaterialTheme.typography.titleSmall,
            color = Tema.cores.texto,
        )
        EspacoVertical(8.dp)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lojas.forEach { loja ->
                PastilhaSelecionavel(
                    texto = loja.nome,
                    selecionada = origem?.id == loja.id,
                    aoTocar = {
                        origem = loja
                        if (destino?.id == loja.id) destino = null
                    },
                )
            }
        }

        EspacoVertical(12.dp)
        Text(
            "Loja de destino",
            style = MaterialTheme.typography.titleSmall,
            color = Tema.cores.texto,
        )
        EspacoVertical(8.dp)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lojas.forEach { loja ->
                PastilhaSelecionavel(
                    texto = loja.nome,
                    selecionada = destino?.id == loja.id,
                    aoTocar = {
                        destino = loja
                        if (origem?.id == loja.id) origem = null
                    },
                )
            }
        }

        if (origem != null && destino != null && origem?.id == destino?.id) {
            EspacoVertical(10.dp)
            Legenda("Escolha duas lojas diferentes para copiar.", cor = Tema.cores.vermelhoTinta)
        }
        EspacoVertical(10.dp)
    }
}

/**
 * Edicao do preco de **um item em uma loja**.
 *
 * As tres acoes sao propositalmente distintas e nomeadas pelo efeito:
 * "nao tinha" marca ausencia nesta loja, "limpar" apaga o preco desta loja,
 * "tirar da lista" remove o produto de todas as colunas.
 *
 * ## Por que o campo e um CampoComprix com estado proprio
 *
 * O CampoDePreco confirma pelo evento de FOCO. Dentro da folha, tocar no
 * botao do rodape nao tira o foco a tempo (o evento chega atrasado), entao o
 * valor digitado se perdia ou chegava stale - falso "preco invalido" ou
 * gravacao perdida. Aqui o salvamento acontece NO TOQUE do botao: o texto
 * atual e interpretado com TextoUtil.paraDecimal e gravado imediatamente, sem
 * depender de foco. O aviso de valor acima do limite e derivado do texto ao
 * vivo, entao some sozinho quando o valor digitado volta a ser valido.
 */
@Composable
private fun FolhaDePrecoDaCelula(
    nomeDoItem: String,
    detalhe: String,
    nomeDaLoja: String,
    precoAtual: BigDecimal?,
    aoFechar: () -> Unit,
    aoGravar: (BigDecimal?) -> Unit,
    aoMarcarIndisponivel: () -> Unit,
    aoLimpar: () -> Unit,
    aoRemoverDaLista: () -> Unit,
) {
    var texto by remember(precoAtual) {
        mutableStateOf(precoAtual?.let { Formatadores.moedaSemSimbolo(it) } ?: "")
    }
    val interpretado = TextoUtil.paraDecimal(texto)
    val acimaDoLimite =
        interpretado != null && interpretado > BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)
    val suspeito = interpretado != null && !acimaDoLimite &&
        interpretado > BigDecimal(Constantes.PRECO_SUSPEITO)

    // Grava so quando o valor mudou: regravar o mesmo preco duplicaria a
    // entrada no historico do produto.
    fun valorMudou(): Boolean = when {
        interpretado == null -> precoAtual != null
        precoAtual == null -> true
        else -> interpretado.compareTo(precoAtual) != 0
    }

    fun gravarECerrar() {
        if (acimaDoLimite) return
        if (valorMudou()) {
            // null/zero = limpar a celula (mesma semantica de anotarPreco).
            aoGravar(interpretado?.takeIf { it.signum() > 0 })
        }
        aoFechar()
    }

    FolhaComprix(
        titulo = nomeDoItem,
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Salvar",
                { gravarECerrar() },
                bloco = true,
                icone = Icones.confirmar,
                habilitado = !acimaDoLimite,
            )
        },
    ) {
        Selo(texto = "$detalhe • $nomeDaLoja", tom = TomDoSelo.VERDE, icone = Icones.loja)
        EspacoVertical(14.dp)
        CampoComprix(
            valor = texto,
            aoMudar = { texto = filtrarEntradaDeMoeda(it) },
            rotulo = "Preço em $nomeDaLoja",
            dica = when {
                suspeito -> "Confirme: ${Formatadores.moeda(interpretado!!)}"
                else -> "Tocar em Salvar grava o preço desta coluna."
            },
            erro = if (acimaDoLimite) {
                "Acima de ${Formatadores.moeda(BigDecimal(Constantes.PRECO_MAXIMO_ACEITO))} — confira os dígitos."
            } else {
                null
            },
            prefixo = "R$",
            tipoDeTeclado = KeyboardType.Decimal,
            alinhamento = TextAlign.End,
            aoConcluir = { gravarECerrar() },
        )
        EspacoVertical(14.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotaoComprix(
                "Não tinha nesta loja",
                aoMarcarIndisponivel,
                estilo = EstiloDeBotao.CONTORNADO,
                compacto = true,
                icone = Icones.indisponivel,
            )
            BotaoComprix(
                "Limpar preço desta loja",
                aoLimpar,
                estilo = EstiloDeBotao.TEXTO,
                compacto = true,
                icone = Icones.recomecar,
            )
            BotaoComprix(
                "Tirar da lista inteira",
                aoRemoverDaLista,
                estilo = EstiloDeBotao.PERIGO,
                compacto = true,
                icone = Icones.excluir,
            )
        }
        EspacoVertical(10.dp)
        Legenda(
            "“Não tinha” e “limpar” mexem só nesta coluna. Remover o produto " +
                "da lista é uma ação separada, de propósito.",
        )
        EspacoVertical(10.dp)
    }
}

@Composable
private fun FolhaDeNovaLoja(
    lojasExistentes: List<String>,
    aoFechar: () -> Unit,
    aoCriar: (String) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    // Mesma chave de dedupe do app inteiro (TextoUtil.normalizar): sem acento,
    // sem caixa e sem pontuacao - igual a "Minhas lojas".
    val repetida = lojasExistentes.any { TextoUtil.normalizar(it) == TextoUtil.normalizar(nome.trim()) }
    FolhaComprix(
        titulo = "Adicionar loja",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Adicionar coluna",
                { aoCriar(nome) },
                bloco = true,
                habilitado = nome.isNotBlank() && !repetida,
                icone = Icones.adicionar,
            )
        },
    ) {
        Legenda("Cada loja vira uma coluna de preços sobre a mesma lista de produtos.")
        EspacoVertical(12.dp)
        CampoComprix(
            valor = nome,
            aoMudar = { nome = it },
            rotulo = "Nome da loja",
            erro = if (repetida) "Já existe uma loja com esse nome." else null,
            dica = "Ex.: Supermercado do bairro, Atacarejo, Feira de sábado",
        )
        EspacoVertical(10.dp)
    }
}

/**
 * **Comparar sem lista**: estado da aba Comparar quando o aparelho nao tem
 * nenhuma lista. Em vez de um vazio sem graca, mostra TODAS as lojas da rede
 * como cartoes - chip colorido com nome, contagem "X precos / Y itens" na
 * mesma regra de "Minhas lojas" (so precos validos: disponivel e maior que
 * zero) - com renomear e excluir a um toque em cada cartao, o atalho bem
 * visivel "Gerenciar lojas" e o convite para adicionar a primeira loja quando
 * a rede esta vazia.
 *
 * Usa o [LojasViewModel] de "Minhas lojas" de proposito: mesma fonte de
 * dados, mesmas contagens e as mesmas mensagens de torrada.
 */
@Composable
fun ComparacaoSemLista(
    viewModel: LojasViewModel,
    aoGerenciarLojas: () -> Unit,
    aoVoltar: () -> Unit,
    navegacao: @Composable () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var nomeDaNovaLoja by remember { mutableStateOf("") }
    var novaLoja by remember { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf<Estabelecimento?>(null) }
    var excluindo by remember { mutableStateOf<Estabelecimento?>(null) }

    // As contagens sao um snapshot: relevante ao entrar na tela.
    LaunchedEffect(Unit) { viewModel.atualizar() }
    LaunchedEffect(estado.mensagem) {
        if (estado.mensagem != null) {
            kotlinx.coroutines.delay(3_200)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = {
            BarraSimples("Comparar estabelecimentos", aoVoltar = aoVoltar) {
                BotaoDeIcone(Icones.adicionar, "Adicionar loja", { novaLoja = true })
                BotaoDeIcone(Icones.loja, "Gerenciar lojas", aoGerenciarLojas)
            }
        },
        navegacao = navegacao,
        sobreposicao = {
            if (estado.mensagem != null) {
                Torrada(
                    texto = estado.mensagem.orEmpty(),
                    aoDescartar = { viewModel.mensagemExibida() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        when {
            estado.carregando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Legenda("Abrindo suas lojas…")
                }
            }

            estado.lojas.isEmpty() -> {
                Column(Modifier.fillMaxSize().padding(PREENCHIMENTO_DA_TELA)) {
                    TituloDaTela(
                        "Comparar preços",
                        subtitulo = "As lojas da sua rede viram as colunas da comparação.",
                    )
                    EstadoVazio(
                        icone = Icones.loja,
                        titulo = "Nenhuma loja ainda.",
                        descricao = "Adicione a primeira loja — depois basta criar uma lista " +
                            "e anotar preços nelas para comparar.",
                        modifier = Modifier.weight(1f),
                    ) {
                        LinhaDeAdicaoDeLoja(
                            nome = nomeDaNovaLoja,
                            aoMudar = { nomeDaNovaLoja = it },
                            aoAdicionar = {
                                viewModel.adicionar(nomeDaNovaLoja)
                                nomeDaNovaLoja = ""
                            },
                        )
                    }
                }
            }

            else -> {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
                    item {
                        TituloDaTela(
                            "Suas lojas",
                            subtitulo = "Sem lista aberta por enquanto — estas são as lojas da sua " +
                                "rede, prontas para receber preços e virar colunas da comparação.",
                        )
                        EspacoVertical(14.dp)
                        BotaoComprix(
                            "Gerenciar lojas",
                            aoGerenciarLojas,
                            bloco = true,
                            estilo = EstiloDeBotao.CONTORNADO,
                            icone = Icones.loja,
                        )
                        EspacoVertical(16.dp)
                    }

                    items(estado.lojas, key = { it.estabelecimento.id }) { loja ->
                        CartaoDeLoja(
                            loja = loja,
                            aoRenomear = { renomeando = loja.estabelecimento },
                            aoExcluir = { excluindo = loja.estabelecimento },
                        )
                        EspacoVertical(12.dp)
                    }

                    item {
                        EspacoVertical(4.dp)
                        Legenda(
                            "Crie uma lista em “Listas” e toque em Comparar para ver essas " +
                                "lojas coluna a coluna sobre os mesmos produtos.",
                        )
                        EspacoVertical(14.dp)
                    }
                }
            }
        }
    }

    if (novaLoja) {
        FolhaDeNovaLoja(
            lojasExistentes = estado.lojas.map { it.estabelecimento.nome },
            aoFechar = { novaLoja = false },
            aoCriar = { nome ->
                viewModel.adicionar(nome)
                novaLoja = false
            },
        )
    }

    renomeando?.let { alvo ->
        DialogoDeRenomear(
            loja = alvo,
            aoFechar = { renomeando = null },
            aoSalvar = { novo ->
                viewModel.renomear(alvo, novo)
                renomeando = null
            },
        )
    }

    excluindo?.let { alvo ->
        DialogoDeExclusao(
            loja = alvo,
            aoFechar = { excluindo = null },
            aoConfirmar = {
                viewModel.remover(alvo)
                excluindo = null
            },
        )
    }
}

/** Campo + botao para incluir uma loja nova (mesmo desenho de Minhas lojas). */
@Composable
private fun LinhaDeAdicaoDeLoja(nome: String, aoMudar: (String) -> Unit, aoAdicionar: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CampoComprix(
            valor = nome,
            aoMudar = aoMudar,
            rotulo = "Nome da loja",
            dica = "Ex.: Mercado da esquina, feira da praça, o atacadão.",
        )
        EspacoVertical(10.dp)
        BotaoComprix(
            "Adicionar loja",
            aoAdicionar,
            icone = Icones.adicionar,
            habilitado = nome.isNotBlank(),
        )
    }
}

/**
 * Folha de opcoes de **uma loja** da comparacao: renomear e excluir, com o
 * chip colorido e a mesma contagem de "Minhas lojas". Aberta pelo botao de
 * opcoes no cartao de total de cada loja.
 */
@Composable
private fun FolhaDeOpcoesDaLoja(
    loja: Estabelecimento,
    contagem: String?,
    aoFechar: () -> Unit,
    aoRenomear: () -> Unit,
    aoExcluir: () -> Unit,
) {
    val cores = Tema.cores
    FolhaComprix(
        titulo = loja.nome,
        aoFechar = aoFechar,
    ) {
        ChipDaLoja(loja)
        if (contagem != null) {
            EspacoVertical(4.dp)
            Legenda(contagem)
        }
        Separador(espacoVertical = 10.dp)
        LinhaDeAcaoDaLoja(
            texto = "Renomear loja",
            icone = Icones.editar,
            tinta = cores.texto,
            aoTocar = aoRenomear,
        )
        LinhaDeAcaoDaLoja(
            texto = "Excluir loja",
            icone = Icones.excluir,
            tinta = cores.vermelhoTinta,
            aoTocar = aoExcluir,
        )
        EspacoVertical(10.dp)
        Legenda("Excluir apaga os preços registrados nessa loja. Os produtos e as listas ficam intactos.")
        EspacoVertical(4.dp)
    }
}

/** Chip colorido com o nome da loja (a cor vem do cadastro do estabelecimento). */
@Composable
private fun ChipDaLoja(loja: Estabelecimento) {
    val cor = corDeHex(loja.corHex)
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            loja.nome,
            style = MaterialTheme.typography.labelMedium,
            color = corDeTextoSobre(cor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Linha de acao da folha: icone + rotulo, alvo de toque cheio, sem realce. */
@Composable
private fun LinhaDeAcaoDaLoja(texto: String, icone: Int, tinta: Color, aoTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO)
            .clip(RoundedCornerShape(13.dp))
            .tocarSemRealce(aoTocar)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconeComprix(icone, null, tamanho = TamanhoDeIcone.padrao, tinta = tinta)
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = tinta,
            modifier = Modifier.weight(1f),
        )
    }
}

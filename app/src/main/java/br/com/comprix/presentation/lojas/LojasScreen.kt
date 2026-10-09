package br.com.comprix.presentation.lojas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.presentation.tema.corDeHex
import br.com.comprix.presentation.tema.corDeTextoSobre
import kotlinx.coroutines.delay

/**
 * **Minhas lojas**: os estabelecimentos onde o usuario registra precos.
 *
 * Cada cartao traz o chip colorido da loja (a mesma cor da matriz de
 * comparacao), a contagem do que ja foi registrado nela e as acoes:
 * renomear, ver (TODOS os produtos com preco, em lista rolavel) e excluir -
 * as duas destrutivas sempre com confirmacao antes.
 */
@Composable
fun LojasScreen(
    viewModel: LojasViewModel,
    aoVoltar: () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var nomeDaNovaLoja by remember { mutableStateOf("") }
    var renomeando by remember { mutableStateOf<Estabelecimento?>(null) }
    var excluindo by remember { mutableStateOf<Estabelecimento?>(null) }

    // Relevanta as contagens ao entrar na tela (precos mudam fora daqui).
    LaunchedEffect(Unit) { viewModel.atualizar() }

    LaunchedEffect(estado.mensagem) {
        if (estado.mensagem != null) {
            delay(3_200)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = { BarraSimples("Minhas lojas", aoVoltar = aoVoltar) },
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
                        "Minhas lojas",
                        subtitulo = "Renomeie, exclua ou apenas confira os lugares onde você registra preços.",
                    )
                    EstadoVazio(
                        icone = Icones.loja,
                        titulo = "Nenhuma loja ainda.",
                        descricao = "Adicione a primeira loja da sua rede — elas aparecem nas colunas de preço da comparação.",
                        modifier = Modifier.weight(1f),
                    ) {
                        LinhaDeAdicao(
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
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PREENCHIMENTO_DA_TELA,
                ) {
                    item {
                        TituloDaTela(
                            "Minhas lojas",
                            subtitulo = "Renomeie, exclua ou apenas confira os lugares onde você registra preços.",
                        )
                        EspacoVertical(14.dp)
                    }

                    item {
                        LinhaDeAdicao(
                            nome = nomeDaNovaLoja,
                            aoMudar = { nomeDaNovaLoja = it },
                            aoAdicionar = {
                                viewModel.adicionar(nomeDaNovaLoja)
                                nomeDaNovaLoja = ""
                            },
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Legenda(
                                "Excluir uma loja apaga os preços registrados nela. " +
                                    "Os produtos e as listas ficam intactos.",
                            )
                        }
                        EspacoVertical(14.dp)
                    }
                }
            }
        }
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

/** Campo + botao para incluir uma loja nova (no topo e no estado vazio). */
@Composable
private fun LinhaDeAdicao(nome: String, aoMudar: (String) -> Unit, aoAdicionar: () -> Unit) {
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
 * Cartao de uma loja da rede (`.list-card` adaptado aos estabelecimentos).
 *
 * Publico de proposito: a comparacao sem lista (tela Comparar sem nenhuma
 * lista aberta) reutiliza exatamente este cartao, com as mesmas contagens e
 * as mesmas acoes de renomear/excluir.
 */
@Composable
fun CartaoDeLoja(
    loja: LojasViewModel.LojaDaRede,
    aoRenomear: () -> Unit,
    aoExcluir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    var expandida by remember { mutableStateOf(false) }
    val corDaLoja = corDeHex(loja.estabelecimento.corHex)

    CartaoComprix(modifier.fillMaxWidth(), preenchimento = PaddingValues(17.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(corDaLoja),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    iniciais(loja.estabelecimento.nome),
                    style = MaterialTheme.typography.titleSmall,
                    color = corDeTextoSobre(corDaLoja),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    loja.estabelecimento.nome,
                    style = MaterialTheme.typography.headlineSmall,
                    color = cores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                contagemDaLoja(loja)?.let { contagem ->
                    EspacoVertical(4.dp)
                    Legenda(contagem)
                }
            }
        }

        EspacoVertical(11.dp)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotaoComprix(
                "Renomear",
                aoRenomear,
                estilo = EstiloDeBotao.CONTORNADO,
                compacto = true,
                icone = Icones.editar,
            )
            if (loja.produtosDaLoja.isNotEmpty()) {
                BotaoComprix(
                    if (expandida) "Ocultar" else "Ver",
                    { expandida = !expandida },
                    estilo = EstiloDeBotao.TEXTO,
                    compacto = true,
                )
            }
            BotaoDeIcone(
                Icones.excluir,
                "Excluir loja ${loja.estabelecimento.nome}",
                aoExcluir,
                tinta = cores.vermelhoTinta,
            )
        }

        if (expandida && loja.produtosDaLoja.isNotEmpty()) {
            EspacoVertical(10.dp)
            val nomes = loja.produtosDaLoja
            val totalDePrecos = loja.quantidadeDePrecos ?: 0
            // Cabecalho discreto com as MESMAS contagens do cartao fechado:
            // o "Ver" lista exatamente estes nomes, nada de amostra cortada.
            Legenda(
                (if (nomes.size == 1) "1 produto" else "${nomes.size} produtos") +
                    " · " +
                    (if (totalDePrecos == 1) "1 preço anotado" else "$totalDePrecos preços anotados"),
            )
            EspacoVertical(4.dp)
            // A lista pode ser longa (a loja inteira): altura limitada com
            // rolagem para nao explodir o cartao dentro da tela.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                nomes.forEach { nomeDoProduto ->
                    Text(
                        "• $nomeDoProduto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cores.texto,
                    )
                }
            }
        }
    }
}

/**
 * Dialogo de renomear: campo preenchido + Cancelar/Salvar.
 *
 * Publico: tambem usado pela comparacao (estado sem lista e folha de opcoes
 * da loja), para que a regra de renomear seja a mesma em todo o app.
 */
@Composable
fun DialogoDeRenomear(
    loja: Estabelecimento,
    aoFechar: () -> Unit,
    aoSalvar: (String) -> Unit,
) {
    var nome by remember { mutableStateOf(loja.nome) }
    DialogoComprix(
        titulo = "Renomear loja",
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
            rotulo = "Nome da loja",
        )
    }
}

/**
 * Dialogo de exclusao da loja: destrutivo, entao confirmado com perigo.
 *
 * Publico: tambem usado pela comparacao, que avisa da mesma forma que os
 * precos da loja sao perdidos na exclusao.
 */
@Composable
fun DialogoDeExclusao(
    loja: Estabelecimento,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val cores = Tema.cores
    DialogoComprix(
        titulo = "Excluir \"${loja.nome}\"?",
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
            "Os preços registrados nessa loja serão apagados. Essa ação não pode ser desfeita.",
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
        )
    }
}

/**
 * Texto de contagem do cartao ("X precos / Y itens"). So contam precos
 * validos: disponivel e maior que zero. "Y itens" e a contagem de NOMES de
 * produto distintos (o mesmo produto em 2 listas conta 1 vez) - o mesmo
 * numero que o "Ver" lista, para o resumo e o detalhe nunca divergirem. Sem
 * acesso as contagens (repositorio de listas ausente), devolve null e a
 * tela omite a linha.
 *
 * Publico: a comparacao reutiliza a mesma regra na folha de opcoes da loja.
 */
fun contagemDaLoja(loja: LojasViewModel.LojaDaRede): String? {
    val precos = loja.quantidadeDePrecos ?: return null
    if (precos == 0) return "Nenhum preço registrado ainda."
    val itens = loja.itensDistintos ?: 0
    val textoDePrecos = if (precos == 1) "1 preço" else "$precos preços"
    val textoDeItens = if (itens == 1) "1 item" else "$itens itens"
    return "$textoDePrecos · $textoDeItens"
}

/** Duas primeiras iniciais do nome, para o chip colorido. */
private fun iniciais(nome: String): String {
    val letras = nome.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return if (letras.isEmpty()) "?" else letras.joinToString("")
}

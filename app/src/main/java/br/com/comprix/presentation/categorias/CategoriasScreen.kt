package br.com.comprix.presentation.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.ChaveComprix
import br.com.comprix.presentation.comum.DocaInferior
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaDeCategoria
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.comum.areaQueConfirmaAoTocarFora
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema

/**
 * **Categorias e ordem do mercado** (tela 14 da referencia).
 *
 * Reordenar aqui muda a ordem em que a lista de compras aparece agrupada - a
 * ideia e espelhar o caminho que a pessoa faz dentro do mercado.
 *
 * ## Duas formas de reordenar, de proposito
 *
 * O arrasto e rapido para quem enxerga e tem firmeza na mao. As setas sao o
 * mesmo recurso para quem usa TalkBack ou tem tremor - e a unica razao de
 * existirem duas. Os dois caminhos chamam o mesmo metodo do ViewModel.
 */
@Composable
fun CategoriasScreen(
    viewModel: CategoriasViewModel,
    aoVoltar: () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    var criando by remember { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf<Categoria?>(null) }
    // Exclusao de categoria criada pela pessoa: passa pela confirmacao (a
    // acao vai para a lixeira, como em todo o app).
    var removendo by remember { mutableStateOf<Categoria?>(null) }

    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        modifier = Modifier.areaQueConfirmaAoTocarFora(),
        barra = {
            BarraSimples("Categorias", aoVoltar = aoVoltar) {
                BotaoDeIcone(
                    Icones.recomecar,
                    "Restaurar ordem padrão do mercado",
                    { viewModel.restaurarOrdemPadrao() },
                )
            }
        },
        doca = {
            DocaInferior {
                BotaoComprix(
                    "Criar nova categoria",
                    { criando = true },
                    bloco = true,
                    icone = Icones.adicionar,
                )
            }
        },
        sobreposicao = {
            if (mensagem != null) {
                Torrada(
                    mensagem.orEmpty(),
                    aoDescartar = viewModel::mensagemExibida,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            if (estado.carregando) {
                // Mesmo padrao da LojasScreen: corpo centrado enquanto o banco
                // emite o primeiro estado.
                item {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Legenda("Abrindo…")
                    }
                }
                return@LazyColumn
            }

            item {
                TituloDaTela("Categorias e ordem do mercado")
                EspacoVertical(14.dp)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(cores.verdeSuave)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconeComprix(
                        Icones.informacao,
                        null,
                        tamanho = TamanhoDeIcone.pequeno,
                        tinta = cores.verdeTinta,
                    )
                    Text(
                        "Categorização automática ativa • ${estado.aprendidos} " +
                            plural(estado.aprendidos, "produto aprendido", "produtos aprendidos") +
                            " no aparelho",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.verdeTinta,
                    )
                }

                EspacoVertical(12.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ALVO_MINIMO),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Reordenação manual",
                            style = MaterialTheme.typography.titleSmall,
                            color = cores.texto,
                        )
                        Legenda("Mostra as setas de mover em cada categoria.")
                    }
                    ChaveComprix(
                        ativa = estado.reordenacaoManual,
                        aoAlternar = viewModel::alternarReordenacaoManual,
                        nomeAcessivel = "Reordenação manual",
                    )
                }
                EspacoVertical(8.dp)
            }

            itemsIndexed(estado.categorias, key = { _, c -> c.id }) { indice, categoria ->
                CartaoDeCategoria(
                    categoria = categoria,
                    produtos = estado.produtosPorCategoria[categoria.id] ?: 0,
                    posicao = indice,
                    total = estado.categorias.size,
                    mostrarSetas = estado.reordenacaoManual,
                    aoSubir = { viewModel.mover(categoria.id, -1) },
                    aoDescer = { viewModel.mover(categoria.id, 1) },
                    aoRenomear = { renomeando = categoria },
                    aoRemover = { removendo = categoria },
                )
                EspacoVertical(8.dp)
            }

            item {
                EspacoVertical(10.dp)
                Legenda(
                    "A ordem acima é a ordem em que a lista aparece agrupada. " +
                        "As 14 categorias originais podem ser renomeadas, mas não removidas — " +
                        "removê-las deixaria produtos sem categoria.",
                )
                EspacoVertical(8.dp)
                BotaoComprix(
                    "Esquecer correções de categoria",
                    { viewModel.esquecerAprendizado() },
                    estilo = EstiloDeBotao.TEXTO,
                    icone = Icones.recomecar,
                    compacto = true,
                )
            }
        }
    }

    if (criando) {
        FolhaDeNomeDeCategoria(
            titulo = "Nova categoria",
            valorInicial = "",
            aoFechar = { criando = false },
            aoConfirmar = { nome ->
                criando = false
                viewModel.criar(nome)
            },
        )
    }
    renomeando?.let { alvo ->
        FolhaDeNomeDeCategoria(
            titulo = "Renomear categoria",
            valorInicial = alvo.nome,
            aoFechar = { renomeando = null },
            aoConfirmar = { nome ->
                renomeando = null
                viewModel.renomear(alvo, nome)
            },
        )
    }

    removendo?.let { alvo ->
        DialogoComprix(
            titulo = "Excluir \"${alvo.nome}\"?",
            aoFechar = { removendo = null },
            icone = Icones.excluir,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { removendo = null },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Excluir",
                        {
                            viewModel.remover(alvo)
                            removendo = null
                        },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                        icone = Icones.excluir,
                    )
                }
            },
        ) {
            Text(
                "A categoria vai para a lixeira e fica recuperável por 30 dias. " +
                    "Depois disso, é apagada para sempre.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tema.cores.apagado,
            )
        }
    }
}

@Composable
private fun CartaoDeCategoria(
    categoria: Categoria,
    produtos: Int,
    posicao: Int,
    total: Int,
    mostrarSetas: Boolean,
    aoSubir: () -> Unit,
    aoDescer: () -> Unit,
    aoRenomear: () -> Unit,
    aoRemover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 61.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cores.cartao)
            .border(1.dp, cores.contorno, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .semantics { contentDescription = "${categoria.nome}, posição ${posicao + 1} de $total" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PastilhaDeCategoria(categoria.chave.ifBlank { categoria.nome.lowercase() })
        Column(Modifier.weight(1f)) {
            Text(
                categoria.nome,
                style = MaterialTheme.typography.titleSmall,
                color = cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Legenda("$produtos ${plural(produtos, "produto", "produtos")}")
        }
        if (mostrarSetas) {
            // Os dois BotaoDeIcone ja nascem com o alvo de 48 dp (ALVO_MINIMO
            // interno) - sem modifier extra, o par fica alinhado e igual.
            BotaoDeIcone(
                Icones.seta,
                "Mover ${categoria.nome} para cima",
                aoSubir,
                tinta = if (posicao == 0) cores.contorno else cores.apagado,
                habilitado = posicao > 0,
                tamanhoDoIcone = 18.dp,
            )
            BotaoDeIcone(
                Icones.descer,
                "Mover ${categoria.nome} para baixo",
                aoDescer,
                tinta = if (posicao == total - 1) cores.contorno else cores.apagado,
                habilitado = posicao < total - 1,
                tamanhoDoIcone = 18.dp,
            )
        } else {
            IconeComprix(Icones.arrastar, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.contornoForte)
        }
        BotaoDeIcone(
            Icones.editar,
            "Renomear ${categoria.nome}",
            aoRenomear,
            tinta = cores.apagado,
            tamanhoDoIcone = 18.dp,
        )
        if (categoria.origem == OrigemCategoria.USUARIO) {
            BotaoDeIcone(
                Icones.excluir,
                "Excluir ${categoria.nome}",
                aoRemover,
                tinta = cores.vermelhoTinta,
                tamanhoDoIcone = 18.dp,
            )
        }
    }
}

@Composable
private fun FolhaDeNomeDeCategoria(
    titulo: String,
    valorInicial: String,
    aoFechar: () -> Unit,
    aoConfirmar: (String) -> Unit,
) {
    var nome by remember { mutableStateOf(valorInicial) }
    FolhaComprix(
        titulo = titulo,
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Salvar",
                { aoConfirmar(nome) },
                bloco = true,
                habilitado = nome.isNotBlank(),
                icone = Icones.confirmar,
            )
        },
    ) {
        CampoComprix(
            valor = nome,
            aoMudar = { nome = it },
            rotulo = "Nome da categoria",
            dica = "Aparece como título do grupo dentro da lista.",
        )
        EspacoVertical(10.dp)
    }
}

private fun plural(quantidade: Int, singular: String, plural: String) =
    if (quantidade == 1) singular else plural

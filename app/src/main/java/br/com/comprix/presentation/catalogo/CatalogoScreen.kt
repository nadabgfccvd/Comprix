package br.com.comprix.presentation.catalogo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.catalogo.ItemDoCatalogo
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CabecalhoDeSecao
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.TextoUtil
import kotlinx.coroutines.delay

/**
 * **Explorar catalogo** (novo na 1.1.0): o acervo de fabrica, pesquisavel.
 *
 * De cima para baixo: campo de busca, linha de chips com os setores do
 * mercado e a lista agrupada por subcategoria. Com lista aberta, cada linha
 * ganha o "+" que manda o produto direto para a lista; sem lista, a tela e
 * consulta pura.
 */
@Composable
fun CatalogoScreen(
    viewModel: CatalogoViewModel,
    listaId: Long,
    aoVoltar: () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()

    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            delay(3_200)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = { BarraSimples("Explorar catálogo", aoVoltar = aoVoltar) },
        sobreposicao = {
            if (mensagem != null) {
                Torrada(
                    texto = mensagem.orEmpty(),
                    aoDescartar = { viewModel.mensagemExibida() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp)) {
                CampoComprix(
                    valor = estado.consulta,
                    aoMudar = viewModel::definirConsulta,
                    rotulo = "Buscar no catálogo",
                    dica = "Sem acento e sem se importar com maiúsculas — “melao” acha “Melão”.",
                )
                EspacoVertical(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PastilhaSelecionavel(
                        texto = "Todos",
                        selecionada = estado.setorSelecionado == null,
                        aoTocar = { viewModel.selecionarSetor(null) },
                    )
                    estado.setores.forEach { setor ->
                        PastilhaSelecionavel(
                            texto = setor.nome,
                            selecionada = estado.setorSelecionado == setor.id,
                            aoTocar = {
                                viewModel.selecionarSetor(
                                    if (estado.setorSelecionado == setor.id) null else setor.id,
                                )
                            },
                        )
                    }
                }
                EspacoVertical(4.dp)
                Legenda(if (estado.total == 1) "1 produto" else "${estado.total} produtos")
            }

            if (estado.carregando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Legenda("Abrindo o catálogo…")
                }
            } else if (estado.grupos.isEmpty()) {
                EstadoVazio(
                    icone = Icones.buscar,
                    titulo = "Nada encontrado no catálogo.",
                    descricao = "Tente outra palavra — a busca ignora acento e caixa. " +
                        "Se o produto não está no acervo, digite-o direto na lista: " +
                        "o Comprix aprende e não pergunta de novo.",
                ) {
                    BotaoComprix(
                        "Limpar a busca",
                        {
                            viewModel.definirConsulta("")
                            viewModel.selecionarSetor(null)
                        },
                        icone = Icones.recomecar,
                        estilo = EstiloDeBotao.CONTORNADO,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
                    estado.grupos.forEach { grupo ->
                        item(key = "cab-${grupo.subcategoria.id}") {
                            CabecalhoDeSecao(
                                grupo.subcategoria.nome,
                                contagem = grupo.itens.size.toString(),
                            )
                        }
                        items(
                            grupo.itens,
                            key = { item -> "item-${grupo.subcategoria.id}-${item.nome}" },
                        ) { item ->
                            LinhaDoCatalogo(
                                item = item,
                                podeAdicionar = listaId > 0,
                                aoAdicionar = { viewModel.adicionar(item.nome, listaId) },
                            )
                            EspacoVertical(4.dp)
                        }
                    }
                    item { EspacoVertical(14.dp) }
                }
            }
        }
    }
}

@Composable
private fun LinhaDoCatalogo(
    item: ItemDoCatalogo,
    podeAdicionar: Boolean,
    aoAdicionar: () -> Unit,
) {
    val cores = Tema.cores
    Row(
        // Sem padding horizontal proprio: o nome alinha exatamente com o
        // titulo do CabecalhoDeSecao acima (ambos caem no PREENCHIMENTO_DA_TELA).
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.nome,
                style = MaterialTheme.typography.bodyLarge,
                color = cores.texto,
            )
            // Sinonimos do acervo, sutis e na mesma chave do nome - e o que
            // mostra por que "mexerica" busca a Tangerina.
            if (item.sinonimos.isNotEmpty()) {
                Legenda("também: " + TextoUtil.capitalizarTitulo(item.sinonimos.joinToString(", ")))
            }
        }
        if (podeAdicionar) {
            BotaoDeIcone(
                Icones.adicionar,
                "Adicionar ${item.nome} à lista",
                aoAdicionar,
                tinta = cores.verdeTinta,
                fundo = cores.verdeSuave,
            )
        }
    }
}

package br.com.comprix.presentation.nutricional

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.nutricional.LinhaNutricional
import br.com.comprix.presentation.comum.BarraTecnica
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores

/** Largura de cada coluna de produto na tabela comparativa. */
private val LARGURA_DA_COLUNA: Dp = 132.dp

/** Largura da coluna fixa com o nome do nutriente. */
private val LARGURA_DO_NUTRIENTE: Dp = 118.dp

/**
 * **Modo tecnico: comparacao nutricional** (tela 07 da referencia).
 *
 * Esta e a unica tela em lavanda. A troca de cor e deliberada: ela avisa, sem
 * precisar ler nada, que o contexto mudou - aqui nao se fala de preco.
 *
 * ## O que esta tela nao faz
 *
 * Nao ranqueia, nao pontua, nao diz o que e "melhor". Mostra os numeros da
 * tabela, normalizados por 100 g ou 100 mL, lado a lado, e marca o menor e o
 * maior de cada linha. Os selos "ALTO EM" aparecem porque estao no rotulo -
 * nunca como criterio de classificacao, o que a Secao 13 proibe.
 */
@Composable
fun NutricionalScreen(
    viewModel: NutricionalViewModel,
    aoVoltar: () -> Unit,
    aoEscanearRotulo: () -> Unit,
) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val rolagem = rememberScrollState()
    val comparacao = estado.comparacao

    TelaComprix(
        barra = { BarraTecnica("Comparação nutricional", aoVoltar = aoVoltar) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            item {
                TituloDaTela(
                    "Modo técnico",
                    subtitulo = "Valores do rótulo, lado a lado. Sem nota, sem ranking.",
                )
                EspacoVertical(14.dp)
            }

            if (!estado.modoTecnicoAtivo) {
                item {
                    EstadoVazio(
                        icone = Icones.nutricao,
                        titulo = "Modo técnico desligado",
                        descricao = "Ligue o modo técnico em Ajustes para comparar tabelas " +
                            "nutricionais dos produtos que você já escaneou.",
                    ) {
                        BotaoComprix("Voltar", aoVoltar, estilo = EstiloDeBotao.CONTORNADO)
                    }
                }
                return@LazyColumn
            }

            if (estado.candidatos.isEmpty()) {
                item {
                    EstadoVazio(
                        icone = Icones.escanear,
                        titulo = "Nenhum rótulo capturado nesta lista.",
                        descricao = "Escaneie a tabela nutricional de ao menos dois produtos " +
                            "da lista para compará-los aqui.",
                    ) {
                        BotaoComprix(
                            "Escanear tabela",
                            aoEscanearRotulo,
                            icone = Icones.camera,
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Text(
                    "Produtos a comparar",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                Legenda("Escolha de 2 a 3 produtos.")
                EspacoVertical(9.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    estado.candidatos.forEach { produto ->
                        PastilhaProduto(
                            produto = produto,
                            selecionado = produto.id in estado.selecionados,
                            aoTocar = { viewModel.alternarProduto(produto.id) },
                        )
                    }
                }

                EspacoVertical(16.dp)
                Text(
                    "Nutrientes exibidos",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(9.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    estado.nutrientesDisponiveis.forEach { nutriente ->
                        PastilhaSelecionavel(
                            texto = nutriente.rotulo,
                            selecionada = nutriente in estado.nutrientesEscolhidos,
                            aoTocar = { viewModel.alternarNutriente(nutriente) },
                        )
                    }
                }
                EspacoVertical(18.dp)
            }

            if (comparacao == null || comparacao.vazia) {
                item {
                    EstadoVazio(
                        icone = Icones.comparar,
                        titulo = "Escolha dois produtos",
                        descricao = "A comparação precisa de pelo menos dois rótulos com " +
                            "tabela nutricional capturada.",
                    ) {
                        BotaoComprix(
                            "Escanear mais um rótulo",
                            aoEscanearRotulo,
                            estilo = EstiloDeBotao.TECNICO,
                            icone = Icones.camera,
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Selo(
                    texto = "Valores por ${comparacao.baseNormalizada}",
                    tom = TomDoSelo.TECNICO,
                    icone = Icones.peso,
                )
                if (comparacao.aviso != null) {
                    EspacoVertical(9.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(cores.ambarSuave)
                            .padding(11.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        IconeComprix(
                            Icones.alerta,
                            null,
                            tamanho = TamanhoDeIcone.pequeno,
                            tinta = cores.ambarTinta,
                        )
                        Text(
                            comparacao.aviso.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = cores.ambarTinta,
                        )
                    }
                }
                EspacoVertical(14.dp)
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cores.cartao)
                        .border(1.dp, cores.contorno, RoundedCornerShape(16.dp)),
                ) {
                    // cabecalho das colunas - tudo centrado na mesma altura
                    Row {
                        Box(
                            Modifier
                                .width(LARGURA_DO_NUTRIENTE)
                                .heightIn(min = 62.dp)
                                .padding(11.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "Por ${comparacao.baseNormalizada}",
                                style = MaterialTheme.typography.labelMedium,
                                color = cores.apagado,
                            )
                        }
                        Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                            comparacao.colunas.forEach { coluna ->
                                Column(
                                    Modifier
                                        .width(LARGURA_DA_COLUNA)
                                        .heightIn(min = 62.dp)
                                        .padding(9.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        coluna.nome,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = cores.texto,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    coluna.porcao?.let { Legenda(it, maximoDeLinhas = 1) }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))

                    comparacao.linhas.forEach { linha ->
                        LinhaDaTabela(linha, comparacao.colunas.size, rolagem)
                        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))
                    }
                }
                EspacoVertical(10.dp)
                Legenda("Arraste a tabela para o lado para ver os outros produtos.")
            }

            item {
                val selos = comparacao.colunas.flatMap { it.selos }.distinct()
                if (selos.isNotEmpty()) {
                    EspacoVertical(16.dp)
                    Text(
                        "Selos frontais encontrados",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(9.dp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        selos.forEach { selo ->
                            Selo(texto = selo.rotulo, tom = TomDoSelo.NEUTRO, icone = Icones.informacao)
                        }
                    }
                    EspacoVertical(7.dp)
                    Legenda(
                        "Selos da Anvisa são informativos e não entram em nenhum " +
                            "cálculo nem ordenação do Comprix.",
                    )
                }

                EspacoVertical(18.dp)
                BotaoComprix(
                    "Escanear tabela via OCR",
                    aoEscanearRotulo,
                    bloco = true,
                    estilo = EstiloDeBotao.TECNICO,
                    icone = Icones.camera,
                )
                EspacoVertical(18.dp)
            }
        }
    }
}

@Composable
private fun LinhaDaTabela(
    linha: LinhaNutricional,
    colunas: Int,
    rolagem: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Row(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .width(LARGURA_DO_NUTRIENTE)
                .heightIn(min = 48.dp)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                linha.nutriente.rotulo,
                style = MaterialTheme.typography.bodyMedium,
                color = cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
            repeat(colunas) { indice ->
                val valor = linha.valores.getOrNull(indice)
                val menor = linha.indiceMenor == indice && colunas > 1
                val maior = linha.indiceMaior == indice && colunas > 1
                Box(
                    Modifier
                        .width(LARGURA_DA_COLUNA)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 9.dp, vertical = 10.dp),
                    // Valor numerico ancorado a direita: rotulo a esquerda,
                    // numero a direita, na mesma base em todas as linhas.
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (valor == null) {
                        Text("—", style = MaterialTheme.typography.bodyMedium, color = cores.contornoForte)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                "${Formatadores.quantidade(valor)} ${linha.nutriente.unidade}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    menor -> cores.verdeTinta
                                    maior -> cores.ambarTinta
                                    else -> cores.texto
                                },
                            )
                            // Texto junto do simbolo: cor sozinha nao informa nada.
                            if (menor) {
                                Text(
                                    "menor",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cores.verdeTinta,
                                )
                            } else if (maior) {
                                Text(
                                    "maior",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cores.ambarTinta,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastilhaProduto(produto: Produto, selecionado: Boolean, aoTocar: () -> Unit) {
    val cores = Tema.cores
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selecionado) cores.lavanda else cores.cartao)
            .border(
                1.dp,
                if (selecionado) cores.lavandaTinta else cores.contorno,
                RoundedCornerShape(14.dp),
            )
            .toggleable(
                value = selecionado,
                role = Role.Checkbox,
                onValueChange = { aoTocar() },
            )
            .semantics { contentDescription = produto.nome }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeComprix(
            if (selecionado) Icones.confirmar else Icones.nutricao,
            null,
            tamanho = TamanhoDeIcone.pequeno,
            tinta = if (selecionado) cores.lavandaTinta else cores.apagado,
        )
        Text(
            produto.nome,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selecionado) cores.lavandaTinta else cores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

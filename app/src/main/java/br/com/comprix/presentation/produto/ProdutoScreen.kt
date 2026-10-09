package br.com.comprix.presentation.produto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.PontoGrafico
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaDeCategoria
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.historico.GraficoDeLinha
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores

/**
 * **Ficha do produto**: o que o app sabe de um item do catalogo local.
 *
 * Reune rotulo lido, selos, alergenos e a variacao de preco ao longo do tempo.
 * E a tela que responde "comprei mais barato da outra vez?" sem precisar
 * abrir a lista antiga.
 */
@Composable
fun ProdutoScreen(viewModel: ProdutoViewModel, aoVoltar: () -> Unit) {
    val cores = Tema.cores
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val produto = estado.produto

    // Recarrega ao entrar na tela: preco anotado na lista ja aparece aqui na volta.
    LaunchedEffect(Unit) { viewModel.atualizar() }

    TelaComprix(
        barra = {
            BarraSimples(
                produto?.nome ?: "Produto",
                aoVoltar = aoVoltar,
                acoes = {
                    // Estrela da ficha: alterna o favorito do PRODUTO (o mesmo
                    // estado que a folha de Favoritos da lista enxerga).
                    BotaoDeIcone(
                        Icones.estrela,
                        if (estado.favorito) "Remover dos favoritos" else "Marcar como favorito",
                        viewModel::alternarFavorito,
                        tinta = if (estado.favorito) cores.verdeTinta else cores.apagado,
                    )
                },
            )
        },
    ) {
        if (produto == null) {
            EstadoVazio(
                icone = Icones.caixa,
                titulo = if (estado.carregando) "Abrindo…" else "Produto não encontrado",
                descricao = "Ele pode ter sido removido do catálogo local.",
                modifier = Modifier.fillMaxSize(),
            ) {
                BotaoComprix("Voltar", aoVoltar, icone = Icones.voltar)
            }
            return@TelaComprix
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PastilhaDeCategoria(estado.categoria?.chave.orEmpty(), lado = 52.dp)
                    Column(Modifier.weight(1f)) {
                        TituloDaTela(produto.nome)
                        // Sinonimos de busca do acervo: mesma legenda da lista,
                        // para a ficha explicar por que "mexerica" acha este
                        // produto.
                        if (estado.sinonimos.isNotEmpty()) {
                            Legenda("também buscar: " + estado.sinonimos.joinToString(", "))
                        }
                        Legenda(estado.categoria?.nome ?: "Sem categoria")
                    }
                }
                EspacoVertical(14.dp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    produto.codigoBarras?.let {
                        Selo("EAN $it", tom = TomDoSelo.NEUTRO, icone = Icones.codigoDeBarras)
                    }
                    Selo(
                        "Unidade padrão: ${produto.unidadePadrao.descricao}",
                        tom = TomDoSelo.NEUTRO,
                        icone = Icones.peso,
                    )
                    produto.dataValidade?.let {
                        Selo(
                            "Validade ${Formatadores.data(it)}",
                            tom = TomDoSelo.NEUTRO,
                            icone = Icones.calendario,
                        )
                    }
                    produto.pesoMedioEstimadoEmBase?.let {
                        Selo(
                            "Peso estimado ${Formatadores.quantidade(it)} g/un",
                            tom = TomDoSelo.NEUTRO,
                            icone = Icones.peso,
                        )
                    }
                }
            }

            item {
                EspacoVertical(18.dp)
                Text(
                    "Variação de preço",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(10.dp)
                CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                    GraficoDeLinha(
                        estado.historico.reversed().map { ponto ->
                            PontoGrafico(
                                rotulo = Formatadores.dataCurta(ponto.quando.toLocalDate()),
                                valor = ponto.preco,
                                destaque = ponto.preco == estado.menorPrecoRecente,
                            )
                        },
                    )
                    if (estado.menorPrecoRecente != null) {
                        EspacoVertical(10.dp)
                        Selo(
                            "Menor preço registrado: ${Formatadores.moeda(estado.menorPrecoRecente)}",
                            tom = TomDoSelo.VERDE,
                            icone = Icones.trofeu,
                        )
                    }
                }
            }

            item {
                EspacoVertical(18.dp)
                Text(
                    "Restrições e rótulo",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(10.dp)
                CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                    Linha("Glúten", produto.gluten.rotulo)
                    if (produto.alergenos.isNotEmpty()) {
                        Separador(espacoVertical = 10.dp)
                        Linha("Alérgenos", produto.alergenos.joinToString { it.rotulo })
                    }
                    if (produto.selosAltoEm.isNotEmpty()) {
                        Separador(espacoVertical = 10.dp)
                        Linha("Selos frontais", produto.selosAltoEm.joinToString { it.rotulo })
                        EspacoVertical(6.dp)
                        Legenda("Informativo. Não entra em nenhum cálculo nem ordenação.")
                    }
                    if (!produto.ingredientes.isNullOrBlank()) {
                        Separador(espacoVertical = 10.dp)
                        Legenda("Ingredientes")
                        Text(
                            produto.ingredientes.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = cores.texto,
                        )
                    }
                    if (produto.gluten == IndicacaoGluten.INDETERMINADO &&
                        produto.alergenos.isEmpty() &&
                        produto.ingredientes.isNullOrBlank()
                    ) {
                        EspacoVertical(6.dp)
                        Legenda(
                            "Nenhum dado de rótulo capturado ainda. Escaneie a embalagem " +
                                "para preencher esta ficha.",
                        )
                    }
                }

                // --- Suas restricoes: so aparece quando o perfil tem algo ligado ---
                if (estado.perfilAtivo) {
                    EspacoVertical(12.dp)
                    Text(
                        "Suas restrições",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(10.dp)
                    CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                        if (estado.alertasDeRestricao.isEmpty()) {
                            LinhaDeRestricao(
                                texto = "Nenhum conflito com as restrições que você cadastrou.",
                                positiva = true,
                            )
                        } else {
                            estado.alertasDeRestricao.forEachIndexed { indice, alerta ->
                                if (indice > 0) EspacoVertical(6.dp)
                                LinhaDeRestricao(alerta, positiva = false)
                            }
                        }
                    }
                }
            }

            item {
                EspacoVertical(18.dp)
                if (produto.temTabelaNutricional) {
                    Text(
                        "Tabela nutricional",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(10.dp)
                    CartaoComprix(preenchimento = PaddingValues(15.dp)) {
                        produto.infoNutricional?.porcaoDescricao?.let {
                            Legenda("Porção: $it")
                            EspacoVertical(8.dp)
                        }
                        produto.infoNutricional?.valores?.forEach { (nutriente, valor) ->
                            Linha(
                                nutriente.rotulo,
                                "${Formatadores.quantidade(valor)} ${nutriente.unidade}",
                            )
                        }
                    }
                }
                EspacoVertical(20.dp)
                Legenda(
                    "Dados do catálogo local deste aparelho. Nada aqui foi baixado " +
                        "nem enviado para lugar nenhum.",
                )
                EspacoVertical(16.dp)
            }
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    val cores = Tema.cores
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = cores.apagado,
            modifier = Modifier.weight(1f),
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * Linha do bloco "Suas restricoes": aviso vermelho quando o produto conflita
 * com o que o usuario cadastrou, confirmacao verde quando nao conflita.
 */
@Composable
private fun LinhaDeRestricao(texto: String, positiva: Boolean) {
    val cores = Tema.cores
    val tinta = if (positiva) cores.verdeTinta else cores.vermelhoTinta
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (positiva) cores.verdeSuave else cores.vermelhoSuave)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeComprix(
            if (positiva) Icones.confirmarCirculo else Icones.alerta,
            null,
            tamanho = TamanhoDeIcone.pequeno,
            tinta = tinta,
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = tinta,
        )
    }
}

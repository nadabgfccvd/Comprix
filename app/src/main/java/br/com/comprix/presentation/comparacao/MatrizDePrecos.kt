package br.com.comprix.presentation.comparacao

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.CelulaComparativa
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.LinhaComparativa
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.tocarSemRealce
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores

/** Largura da coluna de nomes, fixa, que nao rola junto com as lojas. */
private val LARGURA_DO_NOME: Dp = 128.dp

/** Largura de cada coluna de loja. */
private val LARGURA_DA_LOJA: Dp = 112.dp

/**
 * Matriz de precos N lojas x M itens (tela 05 da referencia).
 *
 * ## O desenho e a regra
 *
 * A **lista de produtos e uma so** - ela e a coluna fixa a esquerda. Cada loja
 * acrescenta uma coluna de precos rolavel na horizontal. Isso torna visivel,
 * de relance, o buraco que importa: uma celula vazia significa que aquela
 * linha nao pode ser comparada, e nao que a loja e mais barata.
 *
 * ## Sinalizacao sem depender de cor
 *
 * - **menor preco**: fundo ambar + icone de trofeu + o preco em negrito;
 * - **nao encontrado**: icone de indisponivel + a palavra escrita;
 * - **sem pesquisar**: tracejado e "—", convidando ao toque.
 *
 * Cada celula e um alvo de 48 dp que abre a edicao daquele preco **naquela
 * loja** - nunca "o preco do item".
 */
@Composable
fun MatrizDePrecos(
    estabelecimentos: List<Estabelecimento>,
    linhas: List<LinhaComparativa>,
    rolagem: ScrollState,
    modifier: Modifier = Modifier,
    aoTocarCelula: (itemId: Long, estabelecimentoId: Long) -> Unit,
    aoTocarLinha: (itemId: Long) -> Unit,
    aoAdicionarLoja: () -> Unit,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cores.cartao)
            .border(1.dp, cores.contorno, RoundedCornerShape(18.dp)),
    ) {
        // --- cabecalho das lojas -------------------------------------------------
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(LARGURA_DO_NOME).heightIn(min = 56.dp))
            Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                estabelecimentos.forEach { loja ->
                    Column(
                        Modifier
                            .width(LARGURA_DA_LOJA)
                            .heightIn(min = 56.dp)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(cores.verdeSuave)
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            loja.nome,
                            style = MaterialTheme.typography.labelMedium,
                            color = cores.verdeTinta,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier
                        .width(56.dp)
                        .heightIn(min = 56.dp)
                        // Alvo de toque antes do recuo: o toque recebe os 56 dp
                        // cheios e o desenho segue do mesmo tamanho de antes.
                        .tocarSemRealce(aoAdicionarLoja)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .border(1.dp, cores.contornoForte, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconeComprix(Icones.adicionar, "Adicionar loja à comparação", tinta = cores.acao)
                }
            }
        }

        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))

        // --- linhas ---------------------------------------------------------------
        linhas.forEachIndexed { indice, linha ->
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .width(LARGURA_DO_NOME)
                        .heightIn(min = 62.dp)
                        .tocarSemRealce { aoTocarLinha(linha.itemId) }
                        .padding(horizontal = 11.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        linha.descricao,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cores.texto,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (linha.detalhe.isNotBlank()) Legenda(linha.detalhe, maximoDeLinhas = 1)
                    if (linha.alertaRestricao != null) {
                        Text(
                            linha.alertaRestricao.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = cores.vermelhoTinta,
                            maxLines = 2,
                        )
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                    estabelecimentos.forEach { loja ->
                        val celula = linha.celulas.firstOrNull { it.estabelecimentoId == loja.id }
                        CelulaDePreco(
                            celula = celula,
                            unidade = unidadeDaLinha(linha),
                            nomeDaLoja = loja.nome,
                            nomeDoItem = linha.descricao,
                            aoTocar = { aoTocarCelula(linha.itemId, loja.id) },
                        )
                    }
                    Box(Modifier.width(56.dp))
                }
            }
            if (indice < linhas.lastIndex) {
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(cores.contorno))
            }
        }
    }
}

/**
 * Uma celula da matriz.
 *
 * `null` e "nao pesquisado": propriedade ausente, nao preco zero. A diferenca
 * importa - zero faria a loja parecer gratis no total.
 */
@Composable
private fun CelulaDePreco(
    celula: CelulaComparativa?,
    unidade: Unidade,
    nomeDaLoja: String,
    nomeDoItem: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    val melhor = celula?.melhorPreco == true
    val indisponivel = celula != null && !celula.disponivel
    val semPreco = celula?.preco == null && !indisponivel

    val descricaoLida = when {
        indisponivel -> "$nomeDoItem não encontrado em $nomeDaLoja. Toque para corrigir."
        semPreco -> "$nomeDoItem sem preço em $nomeDaLoja. Toque para anotar."
        melhor -> "$nomeDoItem em $nomeDaLoja: ${Formatadores.moeda(celula?.preco)}, menor preço. " +
            "Toque para editar."

        else -> "$nomeDoItem em $nomeDaLoja: ${Formatadores.moeda(celula?.preco)}. Toque para editar."
    }

    Column(
        modifier
            .width(LARGURA_DA_LOJA)
            .heightIn(min = 62.dp)
            .padding(5.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (melhor) cores.ambar else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (semPreco) {
                    Modifier.border(1.dp, cores.contorno, RoundedCornerShape(11.dp))
                } else {
                    Modifier
                },
            )
            .tocarSemRealce(aoTocar)
            .padding(horizontal = 7.dp, vertical = 8.dp)
            .heightIn(min = ALVO_MINIMO)
            .clearAndSetSemantics { contentDescription = descricaoLida },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            indisponivel -> {
                IconeComprix(Icones.indisponivel, null, tamanho = 17.dp, tinta = cores.apagado)
                Text(
                    "Não encontrado",
                    style = MaterialTheme.typography.labelSmall,
                    color = cores.apagado,
                    textAlign = TextAlign.Center,
                )
            }

            semPreco -> {
                Text("—", style = MaterialTheme.typography.titleMedium, color = cores.contornoForte)
                Text(
                    "Anotar",
                    style = MaterialTheme.typography.labelSmall,
                    color = cores.apagado,
                )
            }

            else -> {
                // Valores sempre alinhados a direita: e o padrao de leitura de
                // dinheiro, nas duas variantes (melhor preco ou preco comum).
                if (melhor) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    ) {
                        IconeComprix(Icones.trofeu, null, tamanho = 14.dp, tinta = cores.ambarTinta)
                        Text(
                            Formatadores.moeda(celula?.preco),
                            style = MaterialTheme.typography.titleSmall,
                            color = cores.ambarTinta,
                        )
                    }
                } else {
                    Text(
                        Formatadores.moeda(celula?.preco),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cores.texto,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
                celula?.precoPorUnidadeBase?.let { base ->
                    Text(
                        Formatadores.precoPorUnidade(base, unidade),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (melhor) cores.ambarTinta else cores.apagado,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Unidade usada para rotular o preco normalizado da linha.
 *
 * O detalhe da linha ja traz a embalagem ("500 mL"), entao basta reconhecer a
 * sigla final; nao casando nada, cai em unidade avulsa.
 */
private fun unidadeDaLinha(linha: LinhaComparativa): Unidade =
    Unidade.entries.firstOrNull { linha.detalhe.trim().endsWith(it.sigla, ignoreCase = true) }
        ?: Unidade.UNIDADE

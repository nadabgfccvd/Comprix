package br.com.comprix.presentation.comum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema

/**
 * Painel **fixo no alto da tela**, antes da lista, com o que ainda falta
 * pesquisar.
 *
 * Nasce da regra da lista mestra: a lista de produtos e uma so e cada loja e
 * uma coluna de precos sobre ela. Se um item tem preco no Mercado 1 e nao tem
 * no Mercado 2, a comparacao daquela linha nao vale - e o total do Mercado 2
 * parece menor so porque faltou anotar. O painel diz exatamente **qual item
 * falta e em qual loja**, o tempo todo, sem precisar ir procurar.
 *
 * Estados:
 * - **com pendencia**: fundo ambar, "N itens sem preço em X e Y" e, ao
 *   expandir, a relacao "Óleo 900 mL — falta no Mercado 2";
 * - **completa**: fundo verde discreto, "Tudo pesquisado nas N lojas";
 * - **uma loja so**: convida a cadastrar a segunda, porque sem duas colunas
 *   nao ha o que comparar.
 */
@Composable
fun PainelDePendencias(
    relatorio: AuditoriaDeCobertura.Relatorio,
    totalDeLojas: Int,
    modifier: Modifier = Modifier,
    aoAdicionarLoja: (() -> Unit)? = null,
    aoAbrirPendencia: ((AuditoriaDeCobertura.Pendencia) -> Unit)? = null,
) {
    val cores = Tema.cores
    var expandido by remember { mutableStateOf(false) }

    val pendencias = relatorio.pendencias
    val completo = pendencias.isEmpty() && totalDeLojas >= 2

    val fundo = when {
        totalDeLojas < 2 -> cores.verdeSuave
        completo -> cores.verdeSuave
        else -> cores.ambarSuave
    }
    val tinta = when {
        totalDeLojas < 2 -> cores.verdeTinta
        completo -> cores.verdeTinta
        else -> cores.ambarTinta
    }
    val icone = when {
        totalDeLojas < 2 -> Icones.loja
        completo -> Icones.confirmarCirculo
        else -> Icones.alerta
    }

    val titulo = when {
        totalDeLojas < 2 -> "Compare com uma segunda loja"
        completo -> "Tudo pesquisado nas $totalDeLojas lojas"
        else -> {
            val lojas = relatorio.porLoja.filter { it.pendencias.isNotEmpty() }.map { it.estabelecimento.nome }
            "${pendencias.size} ${if (pendencias.size == 1) "preço falta" else "preços faltam"} " +
                "em ${nomesEmTexto(lojas)}"
        }
    }
    val detalhe = when {
        totalDeLojas < 2 -> "Com duas ou mais colunas de preço o Comprix mostra o menor preço de cada item."
        completo -> "Dá para comparar com segurança: toda linha tem preço em todas as lojas."
        else -> "Linhas incompletas não entram no menor preço — o total da loja fica menor do que é."
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fundo)
            .border(1.dp, tinta.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALVO_MINIMO)
                .then(
                    if (pendencias.isNotEmpty()) {
                        // Estado e acao no proprio Row (o chevron e decorativo):
                        // o leitor de tela anuncia expandido/recolhido e a acao.
                        Modifier.toggleable(
                            value = expandido,
                            interactionSource = null,
                            indication = null,
                            onValueChange = { novoValor -> expandido = novoValor },
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconeComprix(icone, null, tamanho = 20.dp, tinta = tinta)
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, color = tinta)
                Text(
                    detalhe,
                    style = MaterialTheme.typography.bodySmall,
                    color = tinta.copy(alpha = 0.88f),
                )
            }
            if (pendencias.isNotEmpty()) {
                IconeComprix(
                    if (expandido) Icones.expandir else Icones.descer,
                    null,
                    tamanho = TamanhoDeIcone.pequeno,
                    tinta = tinta,
                )
            } else if (totalDeLojas < 2 && aoAdicionarLoja != null) {
                BotaoComprix(
                    "Adicionar loja",
                    aoAdicionarLoja,
                    estilo = EstiloDeBotao.TONAL,
                    compacto = true,
                    icone = Icones.adicionar,
                )
            }
        }

        AnimatedVisibility(visible = expandido && pendencias.isNotEmpty()) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 12.dp)) {
                relatorio.porLoja.filter { it.pendencias.isNotEmpty() }.forEach { cobertura ->
                    BarraDeCobertura(cobertura)
                    cobertura.pendencias.take(LIMITE_VISIVEL).forEach { pendencia ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ALVO_MINIMO)
                                .then(
                                    if (aoAbrirPendencia != null) {
                                        Modifier.tocarSemRealce { aoAbrirPendencia(pendencia) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tinta),
                            )
                            Text(
                                pendencia.mensagem,
                                style = MaterialTheme.typography.bodySmall,
                                color = tinta,
                                modifier = Modifier.weight(1f),
                            )
                            if (aoAbrirPendencia != null) {
                                IconeComprix(
                                    Icones.seta,
                                    null,
                                    tamanho = 15.dp,
                                    tinta = tinta,
                                )
                            }
                        }
                    }
                    val restantes = cobertura.pendencias.size - LIMITE_VISIVEL
                    if (restantes > 0) {
                        Text(
                            "e mais $restantes ${if (restantes == 1) "item" else "itens"} nesta loja",
                            style = MaterialTheme.typography.bodySmall,
                            color = tinta.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 13.dp, top = 2.dp, bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** Barra de cobertura de uma loja: quanto da lista mestra ja tem preco nela. */
@Composable
private fun BarraDeCobertura(cobertura: AuditoriaDeCobertura.CoberturaDaLoja) {
    val cores = Tema.cores
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                cobertura.estabelecimento.nome,
                style = MaterialTheme.typography.labelMedium,
                color = cores.texto,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${cobertura.registrados}/${cobertura.totalDeItens}",
                style = MaterialTheme.typography.labelMedium,
                color = cores.apagado,
            )
        }
        Spacer(Modifier.height(5.dp))
        BarraDeProgresso(
            fracao = cobertura.progresso,
            cor = if (cobertura.completa) cores.marca else cores.ambar,
            fundo = cores.contorno,
        )
    }
}

/** Faixa curta de pendencia, para telas que nao comportam o painel inteiro. */
@Composable
fun FaixaDePendencias(
    quantidade: Int,
    nomeDaLoja: String,
    modifier: Modifier = Modifier,
    aoTocar: (() -> Unit)? = null,
) {
    if (quantidade <= 0) return
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO)
            .clip(RoundedCornerShape(13.dp))
            .background(cores.ambarSuave)
            .then(if (aoTocar != null) Modifier.tocarSemRealce(aoTocar) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription =
                    "$quantidade itens sem preço em $nomeDaLoja. Toque para ver a lista."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        IconeComprix(Icones.alerta, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.ambarTinta)
        Text(
            "$quantidade ${if (quantidade == 1) "item não encontrado" else "itens não encontrados"} " +
                "em $nomeDaLoja",
            style = MaterialTheme.typography.bodySmall,
            color = cores.ambarTinta,
            modifier = Modifier.weight(1f),
        )
        if (aoTocar != null) {
            IconeComprix(Icones.seta, null, tamanho = 15.dp, tinta = cores.ambarTinta)
        }
    }
}

/** Quantas pendencias de uma loja aparecem antes do "e mais N". */
private const val LIMITE_VISIVEL = 4

private fun nomesEmTexto(nomes: List<String>): String = when (nomes.size) {
    0 -> "alguma loja"
    1 -> nomes.first()
    2 -> "${nomes[0]} e ${nomes[1]}"
    else -> nomes.dropLast(1).joinToString(", ") + " e " + nomes.last()
}


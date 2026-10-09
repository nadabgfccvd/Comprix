package br.com.comprix.presentation.lista

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.domain.parser.ItemInterpretado
import br.com.comprix.domain.parser.OpcaoDeSugestao
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.MarcaDeComprado
import br.com.comprix.presentation.comum.PastilhaDeCategoria
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.tocarSemRealce
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import java.math.BigDecimal

/**
 * Cartao de um item dentro da lista (`.item-card` da referencia).
 *
 * Camadas de informacao, de cima para baixo:
 *
 * 1. marca de comprado, pastilha da categoria, nome, sinonimos de busca do
 *    acervo (quando o produto tem) e embalagem;
 * 2. preco da loja aberta e o preco normalizado por unidade base;
 * 3. selos: oportunidade de kit (ambar), restricao alimentar (vermelho),
 *    selo "ALTO EM" (informativo);
 * 4. **sugestao do parser**, inline, no maximo uma por item e duas opcoes.
 *
 * Marcado como comprado, o cartao cai para 65 % de opacidade e o nome fica
 * riscado - dois sinais, nenhum deles so de cor.
 *
 * Gestos: tocar no nome abre a edicao completa; SEGURAR o dedo no nome (toque
 * longo) abre a folha de acoes do item, quando [aoAbrirAcoes] e informado. O
 * toque longo fica na coluna do nome - a area de identidade do produto - e nao
 * no cartao inteiro, para nao consumir toques que hoje atravessam o cartao
 * (o toque fora de um campo de preco confirma o valor) nem desenhar realce
 * novo sobre o corpo do cartao.
 *
 * Reordenar: o puxador no inicio do cartao ([Icones.arrastar]) carrega o
 * gesto de ARRASTAR por toque longo vindo de [modificadorDeArrasto], montado
 * pela tela - o cartao so desenha o puxador e empresta a area de toque.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartaoDeItem(
    itemComProduto: ItemComProduto,
    categoria: Categoria?,
    preco: BigDecimal?,
    nomeDaLoja: String?,
    sugestao: ItemInterpretado?,
    alertaDeRestricao: String?,
    modifier: Modifier = Modifier,
    aoAlternarComprado: (Boolean) -> Unit,
    aoAbrirEdicao: () -> Unit,
    aoResponderSugestao: (OpcaoDeSugestao) -> Unit,
    aoIgnorarSugestao: () -> Unit,
    aoCompararKit: (() -> Unit)? = null,
    aoAbrirAcoes: (() -> Unit)? = null,
    modificadorDeArrasto: Modifier = Modifier,
    sinonimos: List<String> = emptyList(),
) {
    val cores = Tema.cores
    val item = itemComProduto.item
    val produto = itemComProduto.produto
    val comprado = item.comprado

    val embalagem = Formatadores.descricaoEmbalagem(item.quantidade, item.unidade, item.pesoOuVolume, item.itensPorKit)
    val precoBase = MotorDePrecos.precoPorUnidadeBase(preco, item)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(cores.cartao)
            .border(1.dp, cores.contorno, RoundedCornerShape(17.dp))
            .alpha(if (comprado) 0.65f else 1f)
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Puxador de arrastar: a tela injeta o gesto de toque longo via
            // modificadorDeArrasto. Sem gesto injetado (ex.: busca ativa), o
            // puxador fica desligado - so um indicador apagado.
            Box(
                modifier = modificadorDeArrasto
                    .size(ALVO_MINIMO)
                    .semantics {
                        contentDescription = "Segurar e arrastar para reordenar ${produto.nome}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                IconeComprix(
                    Icones.arrastar,
                    null,
                    tamanho = TamanhoDeIcone.pequeno,
                    tinta = cores.apagado,
                )
            }
            MarcaDeComprado(
                marcado = comprado,
                aoAlternar = aoAlternarComprado,
                nomeAcessivel = if (comprado) {
                    "Desmarcar ${produto.nome}"
                } else {
                    "Marcar ${produto.nome} como comprado"
                },
            )
            PastilhaDeCategoria(categoria?.chave.orEmpty(), lado = 39.dp)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = ALVO_MINIMO)
                    // combinedClickable no lugar de tocarSemRealce: o toque segue
                    // abrindo a edicao e o toque longo abre a folha de acoes.
                    // Sem indication para manter o visual sem realce de antes.
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = "Editar item",
                        onClick = aoAbrirEdicao,
                        onLongClickLabel = "Abrir ações do item",
                        onLongClick = { aoAbrirAcoes?.invoke() },
                    )
                    .padding(start = 11.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    produto.nome,
                    style = MaterialTheme.typography.titleSmall,
                    color = cores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (comprado) TextDecoration.LineThrough else TextDecoration.None,
                )
                // Sinonimos de busca vindos do acervo ("tambem buscar:
                // mexerica, bergamota"): a v1.3.0 limpou o nome poluido e os
                // apelidos sumiram da tela - aqui eles voltam, sutis, uma
                // linha so, igual ao CatalogoScreen.
                if (sinonimos.isNotEmpty()) {
                    Legenda(
                        "também buscar: " + sinonimos.joinToString(", "),
                        maximoDeLinhas = 1,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (embalagem.isNotBlank()) {
                        Text(
                            embalagem,
                            style = MaterialTheme.typography.labelMedium,
                            color = cores.apagado,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .border(1.dp, cores.contorno, RoundedCornerShape(7.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (preco != null) {
                        Text(
                            Formatadores.moeda(preco),
                            style = MaterialTheme.typography.titleSmall,
                            color = cores.verdeTinta,
                        )
                        if (precoBase != null) {
                            Legenda(
                                "(${Formatadores.precoPorUnidade(precoBase, item.unidade)})",
                                maximoDeLinhas = 1,
                            )
                        }
                    } else {
                        Legenda(
                            if (nomeDaLoja != null) "Sem preço em $nomeDaLoja" else "Sem preço ainda",
                            maximoDeLinhas = 1,
                        )
                    }
                }
            }
            IconeComprix(
                Icones.editar,
                null,
                tamanho = TamanhoDeIcone.pequeno,
                tinta = cores.apagado,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        val temSelos = item.ehKit || alertaDeRestricao != null ||
            produto.selosAltoEm.isNotEmpty() || produto.pesoMedioEstimadoEmBase != null
        if (temSelos) {
            EspacoVertical(9.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.ehKit && aoCompararKit != null) {
                    Box(
                        Modifier
                            .heightIn(min = ALVO_MINIMO)
                            .tocarSemRealce(aoCompararKit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Selo(
                            texto = "Kit de ${item.itensPorKit ?: "?"} — comparar com avulso",
                            tom = TomDoSelo.OURO,
                            icone = Icones.comparar,
                        )
                    }
                }
                if (alertaDeRestricao != null) {
                    Selo(texto = alertaDeRestricao, tom = TomDoSelo.ALERTA, icone = Icones.alerta)
                }
                produto.selosAltoEm.forEach { selo ->
                    Selo(texto = rotuloCurto(selo), tom = TomDoSelo.NEUTRO, icone = Icones.informacao)
                }
                if (produto.pesoMedioEstimadoEmBase != null) {
                    Selo(
                        texto = "Peso estimado: " +
                            Formatadores.quantidade(produto.pesoMedioEstimadoEmBase) + " g/un",
                        tom = TomDoSelo.NEUTRO,
                        icone = Icones.peso,
                    )
                }
            }
        }

        if (sugestao?.sugestao != null) {
            EspacoVertical(9.dp)
            SugestaoInline(sugestao, aoResponderSugestao, aoIgnorarSugestao)
        } else if (sugestao?.alerta != null) {
            EspacoVertical(9.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(cores.ambarSuave)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                IconeComprix(Icones.alerta, null, tamanho = 15.dp, tinta = cores.ambarTinta)
                Text(
                    sugestao.alerta.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cores.ambarTinta,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix(
                    "Entendi",
                    aoIgnorarSugestao,
                    estilo = EstiloDeBotao.TEXTO,
                    compacto = true,
                )
            }
        }
    }
}

/**
 * Sugestao do parser, **inline e nao bloqueante** (Secao 9.2 da especificacao).
 *
 * Fica dentro do cartao do proprio item, aceita no maximo duas opcoes e nunca
 * vira dialogo: a pessoa pode continuar digitando a lista inteira e so depois
 * decidir - ou nunca decidir, que tambem e uma resposta valida.
 */
@Composable
fun SugestaoInline(
    interpretado: ItemInterpretado,
    aoResponder: (OpcaoDeSugestao) -> Unit,
    aoIgnorar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    val sugestao = interpretado.sugestao ?: return
    val principal = sugestao.opcoes.firstOrNull() ?: return
    val secundaria = sugestao.opcoes.getOrNull(1)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(cores.verdeSuave)
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .semantics { contentDescription = "Sugestão: ${sugestao.pergunta}" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            IconeComprix(Icones.brilho, null, tamanho = 15.dp, tinta = cores.verdeTinta)
            Text(
                sugestao.pergunta,
                style = MaterialTheme.typography.bodySmall,
                color = cores.verdeTinta,
                modifier = Modifier.weight(1f),
            )
        }
        EspacoVertical(7.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BotaoComprix(
                principal.rotulo,
                { aoResponder(principal) },
                estilo = EstiloDeBotao.TONAL,
                compacto = true,
            )
            if (secundaria != null) {
                BotaoComprix(
                    secundaria.rotulo,
                    { aoResponder(secundaria) },
                    estilo = EstiloDeBotao.CONTORNADO,
                    compacto = true,
                )
            }
            BotaoComprix("Agora não", aoIgnorar, estilo = EstiloDeBotao.TEXTO, compacto = true)
        }
    }
}

/** Cabecalho de um grupo de categoria dentro da lista (`.section-header`). */
@Composable
fun CabecalhoDeCategoria(
    categoria: Categoria,
    quantidade: Int,
    concluido: Boolean,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PastilhaDeCategoria(categoria.chave, lado = 27.dp)
        Text(
            categoria.nome,
            style = MaterialTheme.typography.titleMedium,
            color = cores.texto,
        )
        Text(
            quantidade.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = cores.verdeTinta,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(cores.verdeSuave)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        if (concluido) {
            IconeComprix(
                Icones.confirmarCirculo,
                "Categoria concluída",
                tamanho = 15.dp,
                tinta = cores.marca,
            )
        }
    }
}

private fun rotuloCurto(selo: SeloAltoEm) = "ALTO EM ${selo.descricaoCurta.uppercase()}"

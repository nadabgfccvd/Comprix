package br.com.comprix.presentation.comparacao

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.modelo.CompraMistaOtima
import br.com.comprix.domain.modelo.TotalEstabelecimento
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FaixaDeDestaque
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * **Compra concluida** (tela 16 da referencia).
 *
 * Fecha o ciclo das cinco jornadas: a pessoa so ve esta tela depois de
 * comparar e finalizar. Ela confirma tres coisas, em ordem de importancia:
 * que a compra foi salva, quanto economizou e onde cada parte foi comprada.
 *
 * Nenhuma gamificacao: o numero e o resultado real do calculo
 * `Σ(maior preço × qtd) − pago`, com piso em zero.
 */
@Composable
fun ResumoDaCompraConcluida(
    resultado: CalculadoraDeCompra.ResultadoDaCompra,
    totaisPorLoja: List<TotalEstabelecimento>,
    compraMista: CompraMistaOtima?,
    conflitosDeAlergenicos: Int,
    aoVerHistorico: () -> Unit,
    aoVoltarParaListas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeloDeCelebracao()
        EspacoVertical(20.dp)
        Text(
            "Compra salva no histórico!",
            style = MaterialTheme.typography.headlineLarge,
            color = cores.texto,
            textAlign = TextAlign.Center,
        )
        EspacoVertical(14.dp)

        if (resultado.economia.signum() > 0) {
            FaixaDeDestaque(
                texto = "Você economizou ${Formatadores.moeda(resultado.economia)} " +
                    "(${Formatadores.percentual(resultado.economiaPercentual)})" +
                    if (compraMista != null && compraMista.lojasEnvolvidas > 1) " com a compra mista" else "",
                tom = TomDoSelo.OURO,
                icone = Icones.trofeu,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FaixaDeDestaque(
                texto = "Compra registrada. Sem diferença de preço entre as lojas desta vez.",
                tom = TomDoSelo.VERDE,
                icone = Icones.informacao,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        EspacoVertical(16.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cores.cartao)
                .border(1.dp, cores.contorno, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            totaisPorLoja.filter { it.itensDisponiveis > 0 }.forEach { total ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        total.nome,
                        style = MaterialTheme.typography.titleSmall,
                        color = cores.texto,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${total.itensDisponiveis} " +
                            if (total.itensDisponiveis == 1) "item" else "itens",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.apagado,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        Formatadores.moeda(total.total),
                        style = MaterialTheme.typography.titleSmall,
                        color = cores.texto,
                    )
                }
            }
            Separador(espacoVertical = 11.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total pago",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatadores.moeda(resultado.totalPago),
                    style = MaterialTheme.typography.headlineMedium,
                    color = cores.verdeTinta,
                )
            }
            if (resultado.itensSemPreco > 0) {
                EspacoVertical(9.dp)
                Selo(
                    texto = "${resultado.itensSemPreco} " +
                        (if (resultado.itensSemPreco == 1) "item ficou" else "itens ficaram") +
                        " sem preço e não entrou no total",
                    tom = TomDoSelo.NEUTRO,
                    icone = Icones.informacao,
                )
            }

            // --- dividir a conta -------------------------------------------------
            // Display puro e local (remember, nada persiste): o total pago vai
            // para centavos, a divisao inteira reparte o resto (DivisorDeConta)
            // e a tela mostra um valor representativo por pessoa.
            Separador(espacoVertical = 11.dp)
            Text(
                "Dividir a conta",
                style = MaterialTheme.typography.titleMedium,
                color = cores.texto,
            )
            EspacoVertical(9.dp)
            var pessoas by remember { mutableIntStateOf(MINIMO_DE_PESSOAS) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                BotaoDeIcone(
                    Icones.menos,
                    "Menos uma pessoa",
                    { pessoas -= 1 },
                    habilitado = pessoas > MINIMO_DE_PESSOAS,
                )
                Text(
                    "$pessoas pessoas",
                    style = MaterialTheme.typography.titleSmall,
                    color = cores.texto,
                )
                BotaoDeIcone(
                    Icones.adicionar,
                    "Mais uma pessoa",
                    { pessoas += 1 },
                    habilitado = pessoas < MAXIMO_DE_PESSOAS,
                )
            }
            EspacoVertical(6.dp)
            val totalCentavos = totalEmCentavos(resultado.totalPago)
            val partes = DivisorDeConta.dividirConta(totalCentavos, pessoas)
            val porPessoa = partes.firstOrNull() ?: 0L
            Text(
                "≈ ${Formatadores.moeda(
                    BigDecimal.valueOf(porPessoa).movePointLeft(Constantes.ESCALA_MOEDA),
                )} por pessoa",
                style = MaterialTheme.typography.headlineSmall,
                color = cores.verdeTinta,
            )
            if (totalCentavos - partes.sum() > 0) {
                Legenda("(arredondado)")
            }
        }

        EspacoVertical(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(if (conflitosDeAlergenicos == 0) cores.verdeSuave else cores.vermelhoSuave)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            IconeComprix(
                if (conflitosDeAlergenicos == 0) Icones.confirmarCirculo else Icones.alerta,
                null,
                tamanho = TamanhoDeIcone.pequeno,
                tinta = if (conflitosDeAlergenicos == 0) cores.verdeTinta else cores.vermelhoTinta,
            )
            Text(
                if (conflitosDeAlergenicos == 0) {
                    "Nenhum conflito com o seu perfil de alergênicos"
                } else {
                    "$conflitosDeAlergenicos ${if (conflitosDeAlergenicos == 1) "item conflita" else "itens conflitam"} " +
                        "com o seu perfil de alergênicos"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (conflitosDeAlergenicos == 0) cores.verdeTinta else cores.vermelhoTinta,
            )
        }

        EspacoVertical(22.dp)
        BotaoComprix("Ver gráficos no histórico", aoVerHistorico, bloco = true, icone = Icones.grafico)
        EspacoVertical(9.dp)
        BotaoComprix(
            "Voltar para Minhas Listas",
            aoVoltarParaListas,
            bloco = true,
            estilo = EstiloDeBotao.CONTORNADO,
            icone = Icones.listas,
        )
        EspacoVertical(14.dp)
        Legenda(
            "Tudo gravado só neste aparelho. Para levar para outro celular, " +
                "exporte um backup em Ajustes.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Medalha da celebracao: circulo verde com confirmacao e moedas ambar. */
@Composable
private fun SeloDeCelebracao(modifier: Modifier = Modifier) {
    val cores = Tema.cores
    Box(modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(cores.verdeSuave),
        )
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(cores.marca),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(
                Icones.confirmar,
                null,
                tamanho = 46.dp,
                tinta = androidx.compose.ui.graphics.Color.White,
            )
        }
        Box(
            Modifier
                .offset(x = 46.dp, y = (-40).dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(cores.ambar),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(Icones.trofeu, null, tamanho = 17.dp, tinta = cores.ambarTinta)
        }
        Box(
            Modifier
                .offset(x = (-48).dp, y = 38.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(cores.ambarSuave),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(Icones.etiqueta, null, tamanho = 14.dp, tinta = cores.ambarTinta)
        }
    }
}

/**
 * Cartao do total de uma loja na secao "Total por loja".
 *
 * @param aoGerenciar quando informado, desenha o botao de opcoes que abre a
 *   folha de gerencia da loja (renomear/excluir) na comparacao; no resumo da
 *   compra concluida fica null e o botao nao aparece.
 */
@Composable
fun CartaoDeTotalDaLoja(
    total: TotalEstabelecimento,
    melhorCestaCompleta: Boolean,
    modifier: Modifier = Modifier,
    aoGerenciar: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (melhorCestaCompleta) cores.verdeSuave else cores.cartao)
            .border(
                if (melhorCestaCompleta) 2.dp else 1.dp,
                if (melhorCestaCompleta) cores.acao else cores.contorno,
                RoundedCornerShape(14.dp),
            )
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                total.nome,
                style = MaterialTheme.typography.titleSmall,
                color = if (melhorCestaCompleta) cores.verdeTinta else cores.texto,
                modifier = Modifier.weight(1f),
            )
            if (melhorCestaCompleta) {
                Selo("Cesta completa mais barata", tom = TomDoSelo.VERDE, icone = Icones.trofeu)
            }
            if (aoGerenciar != null) {
                BotaoDeIcone(
                    Icones.maisOpcoes,
                    "Gerenciar loja ${total.nome}",
                    aoGerenciar,
                    tinta = cores.apagado,
                )
            }
        }
        EspacoVertical(5.dp)
        Text(
            Formatadores.moeda(total.total),
            style = MaterialTheme.typography.headlineMedium,
            color = if (melhorCestaCompleta) cores.verdeTinta else cores.texto,
        )
        val faltando = total.itensAusentes.size + total.itensSemPreco.size
        Legenda(
            if (total.cestaCompleta) {
                "${total.itensDisponiveis} itens, lista completa"
            } else {
                "${total.itensDisponiveis} itens • $faltando " +
                    (if (faltando == 1) "item falta" else "itens faltam") + " aqui"
            },
        )
        if (!total.cestaCompleta) {
            EspacoVertical(7.dp)
            Selo(
                texto = "Total incompleto — não dá para comparar direto",
                tom = TomDoSelo.OURO,
                icone = Icones.alerta,
            )
        }
    }
}

/** Painel da compra mista otima, ao lado dos totais por loja. */
@Composable
fun PainelDeCompraMista(
    compraMista: CompraMistaOtima,
    modifier: Modifier = Modifier,
    aoAplicar: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cores.ambarSuave)
            .border(1.dp, cores.ambar, RoundedCornerShape(16.dp))
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            IconeComprix(Icones.trofeu, null, tamanho = 18.dp, tinta = cores.ambarTinta)
            Text(
                "Compra mista ótima",
                style = MaterialTheme.typography.titleMedium,
                color = cores.ambarTinta,
            )
        }
        EspacoVertical(6.dp)
        Text(
            Formatadores.moeda(compraMista.total),
            style = MaterialTheme.typography.displayMedium,
            color = cores.ambarTinta,
        )
        Legenda(
            "Comprando cada item onde está mais barato, em " +
                "${compraMista.lojasEnvolvidas} ${if (compraMista.lojasEnvolvidas == 1) "loja" else "lojas"}.",
            cor = cores.ambarTinta,
        )
        if (compraMista.economiaReais.signum() > 0) {
            EspacoVertical(9.dp)
            Text(
                "Economia de ${Formatadores.moeda(compraMista.economiaReais)} " +
                    "(${Formatadores.percentual(compraMista.economiaPercentual)}) " +
                    "em relação a ${compraMista.referenciaNome}",
                style = MaterialTheme.typography.bodySmall,
                color = cores.ambarTinta,
            )
        }
        if (aoAplicar != null) {
            EspacoVertical(11.dp)
            BotaoComprix(
                "Usar a compra mista",
                aoAplicar,
                bloco = true,
                estilo = EstiloDeBotao.AMBAR,
                compacto = true,
                icone = Icones.confirmar,
            )
        }
    }
}

/** Limites do reparte na tela "Dividir a conta": de 2 a 12 pessoas. */
private const val MINIMO_DE_PESSOAS = 2
private const val MAXIMO_DE_PESSOAS = 12

/**
 * Total pago em centavos (Long): a divisao inteira acontece em centavos para
 * nao criar nem perder dinheiro no reparte (money como centavo e a convencao
 * do app - ver PrecoEntity.precoCentavos).
 */
private fun totalEmCentavos(total: BigDecimal): Long =
    total.movePointRight(Constantes.ESCALA_MOEDA)
        .setScale(0, RoundingMode.HALF_EVEN)
        .longValueExact()

/**
 * Divide um total em centavos entre N pessoas sem perder nem inventar
 * centavo: cada parte recebe o piso da divisao inteira e as primeiras
 * `resto = total % pessoas` pessoas ganham 1 centavo extra (ordem qualquer,
 * de proposito - a diferenca entre partes fica em no maximo 1 centavo e a
 * tela mostra um valor representativo com a legenda de arredondamento).
 *
 * Funcao pura, coberta por DividirContaTest.
 */
object DivisorDeConta {

    /**
     * Reparte [totalCentavos] em [pessoas] partes inteiras cuja soma devolve
     * exatamente o total informado. Pessoas nao positivas devolve lista vazia;
     * total negativo e tratado como zero.
     */
    fun dividirConta(totalCentavos: Long, pessoas: Int): List<Long> {
        if (pessoas <= 0) return emptyList()
        val total = maxOf(totalCentavos, 0L)
        val piso = total / pessoas
        val resto = total % pessoas
        return List(pessoas) { indice -> piso + if (indice < resto) 1L else 0L }
    }
}

package br.com.comprix.presentation.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.CampoRotulo
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CampoDePreco
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import java.math.BigDecimal

/**
 * **Revisao do que a camera leu** (telas 04 e 13 da referencia).
 *
 * Principio: o app **mostra o que entendeu e deixa corrigir**. Campo lido com
 * pouca confianca aparece marcado; nada entra no banco sem passar por aqui.
 *
 * No modo video, cada campo exibe o consenso - "preço R$ 14,90, igual em 6 de
 * 6 quadros". Ver de onde veio o numero e o que torna a correcao possivel.
 */
@Composable
fun FolhaDeRevisao(
    estado: ScannerViewModel.EstadoDoScanner,
    aoConfirmar: (String, BigDecimal?) -> Unit,
    aoDescartar: () -> Unit,
    aoSelecionarLoja: (Long) -> Unit = {},
    aoCriarLoja: () -> Unit = {},
) {
    val cores = Tema.cores
    val leitura = estado.leitura

    var nome by remember(leitura?.nome, estado.produtoConhecido?.nome) {
        mutableStateOf(estado.produtoConhecido?.nome ?: leitura?.nome.orEmpty())
    }
    var preco by remember(leitura?.preco) { mutableStateOf(leitura?.preco) }

    val duvidosos = leitura?.let {
        CampoRotulo.entries.filter { campo ->
            (it.confiancaPorCampo[campo] ?: 1f) < 0.6f && campo in it.camposLidos
        }
    }.orEmpty()

    FolhaComprix(
        titulo = "Confira o que eu li",
        aoFechar = aoDescartar,
        rodape = {
            // Larguras iguais: os dois botoes dividem a linha do rodape.
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Descartar",
                    aoDescartar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                    icone = Icones.fechar,
                )
                BotaoComprix(
                    "Adicionar à lista",
                    { aoConfirmar(nome, preco) },
                    modifier = Modifier.weight(1f),
                    habilitado = nome.isNotBlank(),
                    icone = Icones.confirmar,
                )
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (leitura != null && leitura.quadrosAnalisados > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(cores.verdeSuave)
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    IconeComprix(
                        Icones.camadas,
                        null,
                        tamanho = TamanhoDeIcone.pequeno,
                        tinta = cores.verdeTinta,
                    )
                    Text(
                        "${leitura.quadrosAnalisados} quadros-chave analisados e cruzados " +
                            "por maioria, tudo no aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.verdeTinta,
                    )
                }
                EspacoVertical(14.dp)
            }

            estado.produtoConhecido?.let { produto ->
                Selo(
                    texto = "Já conhecido: ${produto.nome}",
                    tom = TomDoSelo.VERDE,
                    icone = Icones.confirmarCirculo,
                )
                EspacoVertical(5.dp)
                Legenda("O código de barras bateu com um produto que você já cadastrou.")
                EspacoVertical(14.dp)
            }

            CampoComprix(nome, { nome = it }, "Nome do produto")
            // Erro ambiguo comum: o OCR leu outros campos mas nao o nome - o
            // selo aponta o que falta e some quando o campo e preenchido.
            if (leitura != null && !leitura.vazia && leitura.nome.isNullOrBlank() && nome.isBlank()) {
                EspacoVertical(5.dp)
                Selo("Falta o nome — toque para digitar", tom = TomDoSelo.ALERTA, icone = Icones.alerta)
            }
            EspacoVertical(12.dp)
            CampoDePreco(
                valorInicial = preco,
                rotulo = "Preço na gôndola",
                descricaoAcessibilidade = "Tocar fora do campo confirma o valor.",
                aoConfirmar = { preco = it },
            )
            // Mesmo caso no preco: leu o nome, mas nao leu a etiqueta.
            if (leitura != null && !leitura.vazia && leitura.preco == null && preco == null) {
                EspacoVertical(5.dp)
                Selo("Falta o preço — toque para digitar", tom = TomDoSelo.ALERTA, icone = Icones.alerta)
            }

            // --- loja do preco ----------------------------------------------------
            // O preco escaneado tem que cair numa coluna: escolher a loja aqui
            // evita que o valor va, sem perguntar, para a primeira cadastrada.
            EspacoVertical(12.dp)
            Text(
                "Registrar o preço na loja",
                style = MaterialTheme.typography.labelMedium,
                color = cores.texto,
            )
            EspacoVertical(7.dp)
            if (estado.estabelecimentos.isEmpty()) {
                PastilhaSelecionavel(
                    texto = "Criar “Meu mercado”",
                    selecionada = false,
                    aoTocar = aoCriarLoja,
                    icone = Icones.loja,
                )
                EspacoVertical(5.dp)
                Legenda("O preço fica salvo nesta loja — você pode cadastrar outras depois, na comparação.")
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    estado.estabelecimentos.forEach { loja ->
                        PastilhaSelecionavel(
                            texto = loja.nome,
                            selecionada = loja.id == estado.lojaSelecionadaId,
                            aoTocar = { aoSelecionarLoja(loja.id) },
                        )
                    }
                }
            }

            if (leitura != null) {
                Separador()
                Text(
                    "Campos lidos do rótulo",
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                )
                EspacoVertical(10.dp)

                leitura.codigoBarras?.let {
                    LinhaLida(
                        "Código de barras",
                        "$it (${leitura.formatoCodigoBarras ?: "formato não identificado"})",
                        Icones.codigoDeBarras,
                        true,
                    )
                }
                leitura.quantidade?.let { quantidade ->
                    LinhaLida(
                        "Conteúdo da embalagem",
                        "${Formatadores.quantidade(quantidade)} ${leitura.unidade?.sigla.orEmpty()}",
                        Icones.peso,
                        true,
                    )
                }
                leitura.itensPorEmbalagem?.let {
                    LinhaLida("Unidades na embalagem", it.toString(), Icones.caixa, true)
                }
                leitura.dataValidade?.let {
                    LinhaLida("Validade", Formatadores.data(it), Icones.calendario, true)
                }
                leitura.dataFabricacao?.let {
                    LinhaLida("Fabricação", Formatadores.data(it), Icones.calendario, true)
                }
                if (leitura.gluten != IndicacaoGluten.INDETERMINADO) {
                    LinhaLida(
                        "Glúten",
                        leitura.gluten.rotulo,
                        Icones.trigo,
                        leitura.gluten == IndicacaoGluten.NAO_CONTEM,
                    )
                }
                if (leitura.alergenos.isNotEmpty()) {
                    LinhaLida(
                        "Alérgenos",
                        leitura.alergenos.joinToString { it.rotulo },
                        Icones.alerta,
                        false,
                    )
                }
                if (leitura.infoNutricional?.vazia == false) {
                    LinhaLida(
                        "Tabela nutricional",
                        "Capturada — disponível na comparação nutricional",
                        Icones.nutricao,
                        true,
                    )
                }

                if (leitura.selos.isNotEmpty()) {
                    EspacoVertical(12.dp)
                    Text(
                        "Selos frontais detectados",
                        style = MaterialTheme.typography.titleSmall,
                        color = cores.texto,
                    )
                    EspacoVertical(7.dp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        leitura.selos.forEach { selo ->
                            Selo(texto = selo.rotulo, tom = TomDoSelo.NEUTRO, icone = Icones.informacao)
                        }
                    }
                    EspacoVertical(7.dp)
                    Legenda(
                        "Os selos da Anvisa são informativos. O Comprix não ordena " +
                            "nem pontua produtos por causa deles.",
                    )
                }

                if (duvidosos.isNotEmpty()) {
                    EspacoVertical(14.dp)
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
                            "Confira com atenção: ${duvidosos.joinToString { it.rotulo.lowercase() }}. " +
                                "A leitura ficou com pouca confiança nesses campos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cores.ambarTinta,
                        )
                    }
                }
            }
            EspacoVertical(12.dp)
        }
    }
}

/** Uma linha "campo lido = valor", com marca de conferido. */
@Composable
private fun LinhaLida(
    rotulo: String,
    valor: String,
    icone: Int,
    confiavel: Boolean,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (confiavel) cores.verdeSuave else cores.ambarSuave),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(
                icone,
                null,
                tamanho = 14.dp,
                tinta = if (confiavel) cores.verdeTinta else cores.ambarTinta,
            )
        }
        Column(Modifier.weight(1f)) {
            Legenda(rotulo)
            Text(valor, style = MaterialTheme.typography.bodyMedium, color = cores.texto)
        }
    }
}

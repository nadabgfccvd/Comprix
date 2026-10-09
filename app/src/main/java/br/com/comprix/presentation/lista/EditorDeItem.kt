package br.com.comprix.presentation.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.OpcaoDeCompra
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.modelo.VereditoDeOpcoes
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CampoDePreco
import br.com.comprix.presentation.comum.ChaveComprix
import br.com.comprix.presentation.comum.DeslizanteComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FaixaDeDestaque
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.LinhaNavegavel
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.Segmentado
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.SeletorComprix
import br.com.comprix.presentation.comum.OpcaoSegmentada
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.filtrarEntradaDeMoeda
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * **Edicao completa do item** (tela 09 da referencia).
 *
 * Folha inferior com tudo que o app sabe de um item: nome, categoria,
 * embalagem, preco na loja aberta, kit, codigo de barras, datas, selos e
 * alergenos. Salva ao concluir, num unico caminho de gravacao.
 *
 * O preco editado aqui e o preco **naquela loja** - nunca "o preco do item".
 * Essa e a regra que sustenta a lista mestra: o produto e um so, cada loja tem
 * sua coluna.
 */
@Composable
fun EditorDeItem(
    itemComProduto: ItemComProduto,
    categorias: List<Categoria>,
    nomeDaLoja: String?,
    precoNaLoja: BigDecimal?,
    aoFechar: () -> Unit,
    aoSalvar: (item: ItemDaLista, nome: String, categoriaId: Long) -> Unit,
    aoGravarPreco: (BigDecimal?) -> Unit,
    aoRemoverDaLista: () -> Unit,
    aoMarcarIndisponivel: () -> Unit,
    aoAbrirComparadorDeKit: () -> Unit,
    aoAbrirPesoEstimado: () -> Unit,
    aoAbrirProduto: () -> Unit,
    aoRecapturar: () -> Unit,
) {
    val cores = Tema.cores
    val original = itemComProduto.item
    val produto = itemComProduto.produto

    var nome by remember(produto.id) { mutableStateOf(produto.nome) }
    var categoriaId by remember(produto.id) { mutableStateOf(produto.categoriaId) }
    var quantidadeTexto by remember(original.id) {
        mutableStateOf(Formatadores.quantidade(original.quantidade))
    }
    var unidade by remember(original.id) { mutableStateOf(original.unidade) }
    var pesoTexto by remember(original.id) {
        mutableStateOf(original.pesoOuVolume?.let { Formatadores.quantidade(it) } ?: "")
    }
    var ehKit by remember(original.id) { mutableStateOf(original.ehKit) }
    var itensPorKitTexto by remember(original.id) {
        mutableStateOf(original.itensPorKit?.toString() ?: "")
    }
    var observacao by remember(original.id) { mutableStateOf(original.observacao.orEmpty()) }
    var modo by remember(original.id) { mutableStateOf(original.modoComparacao) }

    val quantidade = TextoUtil.paraDecimal(quantidadeTexto) ?: BigDecimal.ONE
    val peso = TextoUtil.paraDecimal(pesoTexto)
    val itensPorKit = itensPorKitTexto.toIntOrNull()

    val itemEditado = original.copy(
        quantidade = quantidade,
        unidade = unidade,
        pesoOuVolume = peso,
        ehKit = ehKit,
        itensPorKit = if (ehKit) itensPorKit else null,
        observacao = observacao.ifBlank { null },
        modoComparacao = modo,
    )
    val precoBase = MotorDePrecos.precoPorUnidadeBase(precoNaLoja, itemEditado)
    val conversao = precoBase?.let { Formatadores.precoPorUnidade(it, unidade) }

    // Kit: o preco anotado e o da EMBALAGEM inteira, entao o valor que o
    // usuario quer conferir (quanto sai cada unidade) e derivado - preco /
    // unidades do kit. So aparece com a ficha do kit completa (marcado como
    // kit, com numero de unidades e preco valido).
    val porUnidadeDeKit = if (ehKit && precoNaLoja != null && (itensPorKit ?: 0) > 1) {
        MotorDePrecos.precoPorUnidadeDeKit(precoNaLoja, itensPorKit ?: 1)
    } else {
        null
    }

    FolhaComprix(
        titulo = produto.nome,
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Concluir edição",
                {
                    aoSalvar(itemEditado, nome, categoriaId)
                    aoFechar()
                },
                bloco = true,
                icone = Icones.confirmar,
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Selo(
                    texto = categorias.firstOrNull { it.id == categoriaId }?.nome ?: "Sem categoria",
                    tom = TomDoSelo.VERDE,
                    icone = Icones.etiqueta,
                )
                Box(Modifier.clip(RoundedCornerShape(20.dp))) {
                    BotaoComprix(
                        "Recapturar foto / OCR",
                        aoRecapturar,
                        estilo = EstiloDeBotao.CONTORNADO,
                        compacto = true,
                        icone = Icones.camera,
                    )
                }
            }

            EspacoVertical(16.dp)
            CampoComprix(nome, { nome = it }, "Nome do produto")

            EspacoVertical(12.dp)
            SeletorComprix(
                rotulo = "Categoria",
                selecionado = categorias.firstOrNull { it.id == categoriaId } ?: categorias.first(),
                opcoes = categorias,
                aoSelecionar = { categoriaId = it.id },
                rotuloDaOpcao = { it.nome },
                dica = "Mudar aqui ensina o Comprix: da próxima vez ele acerta sozinho.",
            )

            EspacoVertical(16.dp)
            Text("Embalagem", style = MaterialTheme.typography.titleMedium, color = cores.texto)
            EspacoVertical(9.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CampoComprix(
                    valor = quantidadeTexto,
                    aoMudar = { quantidadeTexto = filtrarEntradaDeMoeda(it) },
                    rotulo = "Quantidade",
                    modifier = Modifier.weight(1f),
                    tipoDeTeclado = KeyboardType.Decimal,
                )
                SeletorComprix(
                    rotulo = "Unidade",
                    selecionado = unidade,
                    opcoes = Unidade.paraSelecao(),
                    aoSelecionar = { unidade = it },
                    rotuloDaOpcao = { "${it.sigla} — ${it.descricao}" },
                    modifier = Modifier.weight(1.2f),
                )
            }
            EspacoVertical(10.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CampoComprix(
                    valor = pesoTexto,
                    aoMudar = { pesoTexto = filtrarEntradaDeMoeda(it) },
                    rotulo = "Peso ou volume da embalagem",
                    modifier = Modifier.weight(1f),
                    sufixo = unidade.sigla,
                    tipoDeTeclado = KeyboardType.Decimal,
                    dica = "Opcional. Ex.: 1 pacote de 500 g.",
                )
            }

            EspacoVertical(16.dp)
            Text(
                "Preço ${if (nomeDaLoja != null) "em $nomeDaLoja" else "(escolha uma loja primeiro)"}",
                style = MaterialTheme.typography.titleMedium,
                color = cores.texto,
            )
            EspacoVertical(9.dp)
            CampoDePreco(
                valorInicial = precoNaLoja,
                rotulo = "Preço pago",
                habilitado = nomeDaLoja != null,
                descricaoAcessibilidade = conversao,
                aoConfirmar = aoGravarPreco,
            )
            if (conversao != null) {
                EspacoVertical(5.dp)
                Selo(texto = conversao, tom = TomDoSelo.VERDE, icone = Icones.comparar)
            }
            if (porUnidadeDeKit != null && precoNaLoja != null) {
                EspacoVertical(5.dp)
                Selo(
                    texto = "≈ ${Formatadores.moeda(porUnidadeDeKit)} por unidade " +
                        "(${Formatadores.moeda(precoNaLoja)} · ${itensPorKit} un.)",
                    tom = TomDoSelo.VERDE,
                    icone = Icones.comparar,
                )
                if (quantidade > BigDecimal.ONE) {
                    EspacoVertical(5.dp)
                    Legenda(
                        "${Formatadores.quantidade(quantidade)} kits × ${Formatadores.moeda(precoNaLoja)} = " +
                            Formatadores.moeda(MotorDePrecos.totalDaLinha(precoNaLoja, itemEditado)) +
                            " no total.",
                    )
                }
            }

            EspacoVertical(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (ehKit) cores.verdeSuave else cores.cartao)
                    .border(
                        1.dp,
                        if (ehKit) cores.acao else cores.contorno,
                        RoundedCornerShape(13.dp),
                    )
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "É kit, fardo ou leve-mais-pague-menos",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ehKit) cores.verdeTinta else cores.texto,
                    )
                    Legenda(
                        if (ehKit) {
                            "O Comprix compara o kit com a unidade avulsa."
                        } else {
                            "Ligue para informar quantas unidades vêm na embalagem."
                        },
                        cor = if (ehKit) cores.verdeTinta else cores.apagado,
                    )
                }
                ChaveComprix(ehKit, { ehKit = it }, nomeAcessivel = "É kit ou fardo")
            }
            if (ehKit) {
                EspacoVertical(10.dp)
                CampoComprix(
                    valor = itensPorKitTexto,
                    aoMudar = { itensPorKitTexto = it.filter(Char::isDigit).take(3) },
                    rotulo = "Unidades por kit",
                    tipoDeTeclado = KeyboardType.Number,
                    dica = "Sem chute: se não souber, deixe em branco e o Comprix não compara.",
                )
                if (itensPorKit != null && itensPorKit > 1 && precoNaLoja != null) {
                    EspacoVertical(9.dp)
                    BotaoComprix(
                        "Comparar kit × avulso",
                        aoAbrirComparadorDeKit,
                        estilo = EstiloDeBotao.AMBAR,
                        bloco = true,
                        icone = Icones.trofeu,
                    )
                }
            }

            EspacoVertical(16.dp)
            Text(
                "Como comparar este item",
                style = MaterialTheme.typography.titleMedium,
                color = cores.texto,
            )
            EspacoVertical(9.dp)
            Segmentado(
                opcoes = ModoComparacaoUnidade.entries.map { OpcaoSegmentada(it.rotulo) },
                indiceSelecionado = ModoComparacaoUnidade.entries.indexOf(modo),
                aoSelecionar = { modo = ModoComparacaoUnidade.entries[it] },
            )
            if (unidade == Unidade.UNIDADE) {
                EspacoVertical(9.dp)
                LinhaNavegavel(
                    titulo = "Peso médio estimado",
                    resumo = produto.pesoMedioEstimadoEmBase
                        ?.let { "${Formatadores.quantidade(it)} g por unidade (estimado)" }
                        ?: "Informe para comparar com quem vende no quilo",
                    aoTocar = aoAbrirPesoEstimado,
                    icone = Icones.peso,
                )
            }

            EspacoVertical(16.dp)
            CampoComprix(
                valor = observacao,
                aoMudar = { observacao = it },
                rotulo = "Observação",
                linhaUnica = false,
                minimoDeLinhas = 2,
                dica = "Marca preferida, corredor, recado para quem for comprar.",
            )

            Separador()

            LinhaNavegavel(
                titulo = "Ficha do produto",
                resumo = fichaResumida(itemComProduto),
                aoTocar = aoAbrirProduto,
                icone = Icones.informacao,
            )

            EspacoVertical(16.dp)
            Text("Nesta loja", style = MaterialTheme.typography.titleMedium, color = cores.texto)
            EspacoVertical(9.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BotaoComprix(
                    "Não tinha nesta loja",
                    aoMarcarIndisponivel,
                    estilo = EstiloDeBotao.CONTORNADO,
                    compacto = true,
                    icone = Icones.indisponivel,
                    habilitado = nomeDaLoja != null,
                )
                BotaoComprix(
                    "Tirar da lista inteira",
                    {
                        aoRemoverDaLista()
                        aoFechar()
                    },
                    estilo = EstiloDeBotao.PERIGO,
                    compacto = true,
                    icone = Icones.excluir,
                )
            }
            EspacoVertical(7.dp)
            Legenda(
                "“Não tinha” apaga só o preço desta loja. “Tirar da lista” remove o " +
                    "produto de todas as colunas — são ações diferentes de propósito.",
            )
            EspacoVertical(12.dp)
        }
    }
}

/**
 * **Comparador kit x avulso** (tela 12 da referencia).
 *
 * Normaliza as duas opcoes para a unidade-base e diz, em reais e em por
 * cento, qual compensa. Empate tecnico abaixo de 0,5 % e declarado como
 * empate: fingir precisao de centavo em diferenca de centesimo seria mentira.
 *
 * @param vereditoDaLista comparativo derivado dos precos JA ANOTADOS na
 * lista (a mesma computacao de `LinhaComparativa.vereditoKit`, via
 * ListaViewModel.vereditoDeEmbalagem). Aparece como dica inicial e sai de
 * cena assim que o usuario digita o preco avulso - dali em diante o veredito
 * digitado, ao vivo, manda.
 */
@Composable
fun FolhaDeKitOuAvulso(
    itemComProduto: ItemComProduto,
    precoDoKit: BigDecimal,
    vereditoDaLista: VereditoDeOpcoes? = null,
    aoFechar: () -> Unit,
    aoAplicar: (ehKit: Boolean) -> Unit,
) {
    val cores = Tema.cores
    val item = itemComProduto.item
    val porKit = item.itensPorKit ?: 1
    var precoAvulsoTexto by remember { mutableStateOf("") }
    val precoAvulso = TextoUtil.paraDecimal(precoAvulsoTexto)

    val tamanho = item.pesoOuVolume ?: BigDecimal.ONE
    val opcaoAvulsa = precoAvulso?.let {
        OpcaoDeCompra(
            rotulo = "Unidade avulsa",
            preco = it,
            quantidade = tamanho,
            unidade = item.unidade,
            itensPorEmbalagem = 1,
        )
    }
    val opcaoKit = OpcaoDeCompra(
        rotulo = "Kit de $porKit",
        preco = precoDoKit,
        quantidade = tamanho,
        unidade = item.unidade,
        itensPorEmbalagem = porKit,
    )
    val veredito = opcaoAvulsa?.let { MotorDePrecos.compararOpcoes(it, opcaoKit) }

    FolhaComprix(
        titulo = "Kit ou avulso?",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                texto = when {
                    veredito == null -> "Informe o preço avulso"
                    veredito.equivalentes -> "Tanto faz — manter como está"
                    veredito.melhor.itensPorEmbalagem == 1 -> "Aplicar: comprar avulso"
                    else -> "Aplicar: levar o kit de $porKit"
                },
                aoTocar = {
                    aoAplicar(veredito?.melhor?.itensPorEmbalagem?.let { it > 1 } ?: item.ehKit)
                    aoFechar()
                },
                bloco = true,
                habilitado = veredito != null,
                icone = Icones.confirmar,
            )
        },
    ) {
        Legenda(
            "Os dois preços são normalizados para a mesma unidade-base " +
                "(${Formatadores.unidadeBase(item.unidade.dimensao)}) antes de comparar.",
        )

        // Dica dos precos ja anotados na lista: some quando o preco avulso
        // digitado produz o veredito ao vivo, que passa a mandar.
        if (vereditoDaLista != null && veredito == null) {
            EspacoVertical(14.dp)
            FaixaDeDestaque(
                texto = "Pelos preços já anotados na lista: ${vereditoDaLista.mensagem}",
                tom = if (vereditoDaLista.equivalentes) TomDoSelo.VERDE else TomDoSelo.OURO,
                icone = if (vereditoDaLista.equivalentes) Icones.informacao else Icones.trofeu,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        EspacoVertical(14.dp)
        CartaoDeOpcao(
            rotulo = "Opção A — unidade avulsa",
            titulo = itemComProduto.produto.nome,
            detalhe = Formatadores.descricaoEmbalagem(BigDecimal.ONE, item.unidade, item.pesoOuVolume, null),
            preco = precoAvulso,
            precoBase = opcaoAvulsa?.let(MotorDePrecos::precoPorUnidadeBase),
            unidade = item.unidade,
            vencedora = veredito?.melhor?.itensPorEmbalagem == 1,
        )
        EspacoVertical(10.dp)
        CampoDePreco(
            valorInicial = precoAvulso,
            rotulo = "Preço de 1 unidade avulsa",
            aoConfirmar = { precoAvulsoTexto = it?.let(Formatadores::moedaSemSimbolo).orEmpty() },
        )

        EspacoVertical(16.dp)
        CartaoDeOpcao(
            rotulo = "Opção B — kit de $porKit",
            titulo = "${itemComProduto.produto.nome} (kit)",
            detalhe = Formatadores.descricaoEmbalagem(BigDecimal.ONE, item.unidade, item.pesoOuVolume, porKit),
            preco = precoDoKit,
            precoBase = MotorDePrecos.precoPorUnidadeBase(opcaoKit),
            unidade = item.unidade,
            vencedora = veredito != null && veredito.melhor.itensPorEmbalagem != 1,
        )

        if (veredito != null) {
            EspacoVertical(14.dp)
            FaixaDeDestaque(
                texto = veredito.mensagem,
                tom = if (veredito.equivalentes) TomDoSelo.VERDE else TomDoSelo.OURO,
                icone = if (veredito.equivalentes) Icones.informacao else Icones.trofeu,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EspacoVertical(10.dp)
    }
}

@Composable
private fun CartaoDeOpcao(
    rotulo: String,
    titulo: String,
    detalhe: String,
    preco: BigDecimal?,
    precoBase: BigDecimal?,
    unidade: Unidade,
    vencedora: Boolean,
    modifier: Modifier = Modifier,
) {
    val cores = Tema.cores
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(cores.cartao)
            .border(
                if (vencedora) 2.dp else 1.dp,
                if (vencedora) cores.ambar else cores.contorno,
                RoundedCornerShape(15.dp),
            )
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = cores.apagado)
            if (vencedora) {
                EspacoVertical(0.dp)
                Box(Modifier.padding(start = 7.dp)) {
                    Selo("Melhor", tom = TomDoSelo.OURO, icone = Icones.trofeu)
                }
            }
        }
        EspacoVertical(6.dp)
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = cores.texto)
        Legenda(detalhe)
        EspacoVertical(8.dp)
        Text(
            preco?.let(Formatadores::moeda) ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = cores.texto,
        )
        Legenda(
            precoBase?.let { "Normalizado: ${Formatadores.precoPorUnidade(it, unidade)}" }
                ?: "Informe o preço para normalizar",
        )
    }
}

/**
 * **Peso medio estimado** para hortifruti e itens sem peso (tela 17).
 *
 * Serve para comparar quem vende por cabeca com quem vende no quilo. O valor
 * fica marcado como **estimativa** em toda a interface - nunca vira "peso" de
 * verdade, porque nao foi pesado.
 */
@Composable
fun FolhaDePesoEstimado(
    itemComProduto: ItemComProduto,
    aoFechar: () -> Unit,
    aoAplicar: (BigDecimal?) -> Unit,
) {
    val cores = Tema.cores
    val produto = itemComProduto.produto
    var gramas by remember(produto.id) {
        mutableStateOf(produto.pesoMedioEstimadoEmBase?.toFloat() ?: 350f)
    }
    var texto by remember(produto.id) {
        mutableStateOf(produto.pesoMedioEstimadoEmBase?.let { Formatadores.quantidade(it) } ?: "350")
    }
    var porPeso by remember(produto.id) {
        mutableStateOf(itemComProduto.item.modoComparacao == ModoComparacaoUnidade.POR_PESO)
    }

    FolhaComprix(
        titulo = "Ajuste de unidade",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Aplicar conversão",
                {
                    aoAplicar(if (porPeso) BigDecimal(gramas.toInt()) else null)
                    aoFechar()
                },
                bloco = true,
                icone = Icones.confirmar,
            )
        },
    ) {
        Legenda(
            "Hortifruti costuma ser vendido por unidade numa feira e por quilo " +
                "no supermercado. Informando o peso médio de 1 unidade, o Comprix " +
                "coloca os dois na mesma régua.",
        )
        EspacoVertical(14.dp)
        Segmentado(
            opcoes = listOf(
                OpcaoSegmentada("Comparar por unidade", Icones.caixa),
                OpcaoSegmentada("Informar peso médio", Icones.peso),
            ),
            indiceSelecionado = if (porPeso) 1 else 0,
            aoSelecionar = { porPeso = it == 1 },
        )

        if (porPeso) {
            EspacoVertical(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(cores.cartao)
                    .border(1.dp, cores.contorno, RoundedCornerShape(15.dp))
                    .padding(14.dp),
            ) {
                Text(produto.nome, style = MaterialTheme.typography.titleSmall, color = cores.texto)
                EspacoVertical(10.dp)
                DeslizanteComprix(
                    valor = gramas,
                    aoMudar = {
                        gramas = it
                        texto = it.toInt().toString()
                    },
                    faixa = 20f..5_000f,
                    passos = 0,
                    descricao = "Peso médio estimado de uma unidade, em gramas",
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Peso médio estimado de 1 unidade",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.apagado,
                        modifier = Modifier.weight(1f),
                    )
                    CampoComprix(
                        valor = texto,
                        aoMudar = { novo ->
                            texto = novo.filter(Char::isDigit).take(5)
                            texto.toFloatOrNull()?.let { gramas = it.coerceIn(20f, 5_000f) }
                        },
                        rotulo = "",
                        sufixo = "g",
                        tipoDeTeclado = KeyboardType.Number,
                        modifier = Modifier.heightIn(min = 52.dp).padding(top = 0.dp),
                    )
                }
                EspacoVertical(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(cores.verdeSuave)
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconeComprix(
                        Icones.comparar,
                        null,
                        tamanho = TamanhoDeIcone.pequeno,
                        tinta = cores.verdeTinta,
                    )
                    Text(
                        "1 unidade ≈ ${gramas.toInt()} g, então 1 kg ≈ " +
                            "${String.format(java.util.Locale("pt", "BR"), "%.1f", 1000f / gramas)} unidades.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.verdeTinta,
                    )
                }
            }
            EspacoVertical(10.dp)
            Selo(
                texto = "O valor fica marcado como estimativa na lista e na matriz.",
                tom = TomDoSelo.NEUTRO,
                icone = Icones.informacao,
            )
        } else {
            EspacoVertical(14.dp)
            Legenda(
                "Comparando por unidade, o Comprix mostra R$/un e ignora quem " +
                    "vende no quilo — nenhuma estimativa é inventada.",
            )
        }
        EspacoVertical(10.dp)
    }
}

/** Folha curta para escolher ou criar a loja em que os precos serao anotados. */
@Composable
fun FolhaDeLojas(
    lojas: List<br.com.comprix.domain.modelo.Estabelecimento>,
    lojaAtivaId: Long?,
    aoFechar: () -> Unit,
    aoSelecionar: (Long) -> Unit,
    aoCriar: (String) -> Unit,
) {
    var nova by remember { mutableStateOf("") }
    FolhaComprix(
        titulo = "Loja onde estou agora",
        aoFechar = aoFechar,
        rodape = {
            BotaoComprix(
                "Adicionar loja",
                {
                    aoCriar(nova)
                    nova = ""
                    aoFechar()
                },
                bloco = true,
                habilitado = nova.isNotBlank(),
                icone = Icones.adicionar,
            )
        },
    ) {
        Legenda("Os preços que você digitar vão para a coluna desta loja.")
        EspacoVertical(12.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            lojas.forEach { loja ->
                PastilhaSelecionavel(
                    texto = loja.nome,
                    selecionada = loja.id == lojaAtivaId,
                    aoTocar = {
                        aoSelecionar(loja.id)
                        aoFechar()
                    },
                    icone = Icones.loja,
                )
            }
        }
        EspacoVertical(16.dp)
        CampoComprix(nova, { nova = it }, "Nome da nova loja", dica = "Ex.: Atacarejo do bairro")
        EspacoVertical(10.dp)
    }
}

private fun fichaResumida(itemComProduto: ItemComProduto): String {
    val produto = itemComProduto.produto
    val partes = buildList {
        produto.codigoBarras?.let { add("EAN $it") }
        if (produto.temTabelaNutricional) add("tabela nutricional")
        if (produto.alergenos.isNotEmpty()) add("${produto.alergenos.size} alérgeno(s)")
        if (produto.selosAltoEm.isNotEmpty()) add("${produto.selosAltoEm.size} selo(s) ALTO EM")
    }
    return if (partes.isEmpty()) "Ver histórico de preços e dados do rótulo" else partes.joinToString(" · ")
}

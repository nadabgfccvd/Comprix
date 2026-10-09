package br.com.comprix.domain.preco

import br.com.comprix.domain.alergia.VerificadorDeAlergias
import br.com.comprix.domain.modelo.CelulaComparativa
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.CompraMistaOtima
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.LinhaComparativa
import br.com.comprix.domain.modelo.MatrizComparativa
import br.com.comprix.domain.modelo.OpcaoDeCompra
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.TotalEstabelecimento
import br.com.comprix.domain.modelo.VereditoDeOpcoes
import br.com.comprix.domain.unidade.ConversorDeUnidades
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Motor de precos do Comprix - o coracao das Secoes 4.1 a 4.3 do briefing.
 *
 * Responde a tres perguntas, sempre com numero e nunca com achismo:
 *
 * 1. **Quanto custa a unidade-base?** `preco / quantidadeBase`, para que
 *    "R$ 24,90 o pacote de 5 kg" vire "R$ 4,98 o kg" e possa ser comparado.
 * 2. **Fardo ou avulso?** Compara o preco por unidade-base das duas formas e
 *    diz a economia em R$ **e** em %, ou declara empate quando a diferenca e
 *    menor que [Constantes.LIMIAR_EQUIVALENCIA_PERCENTUAL].
 * 3. **Onde comprar?** Monta a matriz item x loja, marca o menor preco de cada
 *    linha, soma o total de cada loja, lista o que falta em cada uma e calcula
 *    a "compra mista otima" (cada item onde esta mais barato).
 *
 * ## Convencoes
 * - Dinheiro: escala 2, `HALF_EVEN` (arredondamento bancario, sem vies).
 * - Preco por unidade-base: escala 6 - R$/g de um produto barato e um numero
 *   bem pequeno, e arredondar cedo distorceria a comparacao.
 * - Celula da matriz = preco UNITARIO da embalagem; total da loja = preco x
 *   quantidade de embalagens.
 * - "Menor preco" so e destacado quando ha pelo menos duas lojas com preco
 *   para aquele item: destacar o unico preco existente seria enganoso.
 */
object MotorDePrecos {

    private val CEM = BigDecimal("100")

    // =================================================================================
    // 1. Preco por unidade-base
    // =================================================================================

    /**
     * Preco de UMA unidade-base (R$/g, R$/mL, R$/un) a partir do preco da embalagem.
     *
     * @return null quando nao da para calcular (preco ausente, zero/negativo ou
     * quantidade invalida).
     */
    fun precoPorUnidadeBase(preco: BigDecimal?, item: ItemDaLista): BigDecimal? {
        if (preco == null || preco.signum() <= 0) return null
        val conteudoDeUma = item.pesoOuVolume ?: BigDecimal.ONE
        val base = ConversorDeUnidades.paraUnidadeBase(conteudoDeUma, item.unidade, item.itensPorKit)
        if (base.signum() <= 0) return null
        return preco.divide(base, Constantes.ESCALA_UNIDADE_BASE, RoundingMode.HALF_EVEN)
    }

    /** Mesma conta, a partir de uma [OpcaoDeCompra] (kit x avulso). */
    fun precoPorUnidadeBase(opcao: OpcaoDeCompra): BigDecimal? {
        if (opcao.preco.signum() <= 0 || opcao.quantidadeBase.signum() <= 0) return null
        return opcao.preco.divide(opcao.quantidadeBase, Constantes.ESCALA_UNIDADE_BASE, RoundingMode.HALF_EVEN)
    }

    /**
     * Preco da embalagem x numero de embalagens - o valor que entra em TODOS
     * os totais do app (doca da lista, matriz, compra mista, finalizacao).
     *
     * ## Semantica com kit
     * Quando `ItemDaLista.ehKit` e true, o preco anotado e o da EMBALAGEM
     * inteira (o fardo/pack com `itensPorKit` unidades) e `quantidade` conta
     * KITS, nao unidades. A conta continua preco x quantidade: "2 fardos de
     * R$ 30,00" = "R$ 60,00". O equivalente por unidade (preco / itensPorKit)
     * e apenas DERIVADO para exibicao, via [precoPorUnidadeDeKit] - nenhum
     * total multiplica por itensPorKit, porque o preco ja e do kit inteiro.
     * Anotar preco POR UNIDADE num item marcado como kit, portanto, joga os
     * totais para baixo sem aviso - o editor de item mostra a conta derivada
     * exatamente para o usuario conferir isso na hora.
     */
    fun totalDaLinha(preco: BigDecimal?, item: ItemDaLista): BigDecimal {
        if (preco == null || preco.signum() <= 0 || item.quantidade.signum() <= 0) return BigDecimal.ZERO
        return preco.multiply(item.quantidade).setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
    }

    /**
     * Valor de UMA unidade dentro do kit: preco da embalagem dividido pelo
     * numero de unidades dela ("R$ 30,00 o fardo de 12" = "R$ 2,50 cada").
     * Somente para EXIBICAO - os totais continuam usando [totalDaLinha].
     *
     * @return null quando o preco nao e positivo ou o kit nao tem unidades.
     */
    fun precoPorUnidadeDeKit(preco: BigDecimal, itensPorKit: Int): BigDecimal? {
        if (preco.signum() <= 0 || itensPorKit <= 0) return null
        return preco.divide(BigDecimal(itensPorKit), Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
    }

    // =================================================================================
    // 2. Kit x avulso
    // =================================================================================

    /**
     * Compara duas formas de comprar o mesmo produto.
     *
     * A economia e calculada sobre a MAIOR das duas quantidades-base: a pergunta
     * que o usuario faz e "se eu levar o equivalente ao fardo, quanto economizo?".
     *
     * @return null quando as unidades nao sao comparaveis (kg x L) ou falta preco.
     */
    fun compararOpcoes(opcaoA: OpcaoDeCompra, opcaoB: OpcaoDeCompra): VereditoDeOpcoes? {
        if (!ConversorDeUnidades.saoComparaveis(opcaoA.unidade, opcaoB.unidade)) return null
        val baseA = precoPorUnidadeBase(opcaoA) ?: return null
        val baseB = precoPorUnidadeBase(opcaoB) ?: return null

        val (melhor, pior, precoMelhor, precoPior) =
            if (baseA <= baseB) Quadra(opcaoA, opcaoB, baseA, baseB) else Quadra(opcaoB, opcaoA, baseB, baseA)

        val diferencaPorBase = precoPior.subtract(precoMelhor)
        val percentual = if (precoPior.signum() > 0) {
            diferencaPorBase.multiply(CEM).divide(precoPior, 2, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }

        val quantidadeDeReferencia = melhor.quantidadeBase.max(pior.quantidadeBase)
        val economiaReais = diferencaPorBase
            .multiply(quantidadeDeReferencia)
            .setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)

        val equivalentes = percentual.toDouble() < Constantes.LIMIAR_EQUIVALENCIA_PERCENTUAL
        val unidadeExibida = melhor.unidade.unidadeDeExibicao.sigla

        val mensagem = if (equivalentes) {
            "Dá no mesmo: ${melhor.rotulo} e ${pior.rotulo} custam praticamente igual por $unidadeExibida."
        } else {
            "${melhor.rotulo} compensa: " +
                "${Formatadores.precoPorUnidade(precoMelhor, melhor.unidade)} contra " +
                "${Formatadores.precoPorUnidade(precoPior, pior.unidade)} — " +
                "economia de ${Formatadores.moeda(economiaReais)} (${Formatadores.percentual(percentual)})."
        }

        return VereditoDeOpcoes(
            melhor = melhor,
            pior = pior,
            precoBaseMelhor = precoMelhor,
            precoBasePior = precoPior,
            economiaReais = economiaReais,
            economiaPercentual = percentual,
            equivalentes = equivalentes,
            mensagem = mensagem,
        )
    }

    private data class Quadra(
        val melhor: OpcaoDeCompra,
        val pior: OpcaoDeCompra,
        val precoMelhor: BigDecimal,
        val precoPior: BigDecimal,
    )

    // =================================================================================
    // 3. Matriz de comparacao entre N estabelecimentos
    // =================================================================================

    /**
     * Monta a matriz completa itens x lojas.
     *
     * @param precos todos os precos registrados dos itens desta lista.
     * @param categorias mapa id -> categoria, para agrupar as linhas.
     * @param perfil restricoes do usuario, para o alerta de alergenico/gluten.
     */
    fun montarMatriz(
        itens: List<ItemComProduto>,
        estabelecimentos: List<Estabelecimento>,
        precos: List<PrecoRegistrado>,
        categorias: Map<Long, Categoria>,
        perfil: PerfilRestricoes = PerfilRestricoes(),
    ): MatrizComparativa {
        val precosPorItem: Map<Long, Map<Long, PrecoRegistrado>> = precos
            .groupBy { it.itemDaListaId }
            .mapValues { (_, lista) -> lista.associateBy { it.estabelecimentoId } }

        val vereditos = calcularVereditosDeEmbalagem(itens, precosPorItem)

        val linhas = itens.map { itemComProduto ->
            montarLinha(itemComProduto, estabelecimentos, precosPorItem, categorias, perfil, vereditos)
        }

        val totais = estabelecimentos.map { loja ->
            montarTotal(loja, itens, precosPorItem)
        }

        val semNenhumPreco = itens.count { itemComProduto ->
            precosPorItem[itemComProduto.item.id].orEmpty().values.none { it.disponivel && it.preco.signum() > 0 }
        }

        return MatrizComparativa(
            estabelecimentos = estabelecimentos,
            linhas = linhas,
            totais = totais,
            compraMista = calcularCompraMista(itens, estabelecimentos, precosPorItem, totais),
            itensSemNenhumPreco = semNenhumPreco,
        )
    }

    private fun montarLinha(
        itemComProduto: ItemComProduto,
        estabelecimentos: List<Estabelecimento>,
        precosPorItem: Map<Long, Map<Long, PrecoRegistrado>>,
        categorias: Map<Long, Categoria>,
        perfil: PerfilRestricoes,
        vereditos: Map<Long, VereditoDeOpcoes>,
    ): LinhaComparativa {
        val item = itemComProduto.item
        val produto = itemComProduto.produto
        val precosDoItem = precosPorItem[item.id].orEmpty()

        val disponiveis = precosDoItem.values.filter { it.disponivel && it.preco.signum() > 0 }
        val menor = disponiveis.minByOrNull { it.preco }
        val maior = disponiveis.maxByOrNull { it.preco }
        // Destacar "o mais barato" com uma loja so seria enganoso.
        val podeDestacar = disponiveis.size >= 2

        val celulas = estabelecimentos.map { loja ->
            val registro = precosDoItem[loja.id]
            val precoValido = registro?.takeIf { it.disponivel && it.preco.signum() > 0 }?.preco
            CelulaComparativa(
                estabelecimentoId = loja.id,
                preco = precoValido,
                precoPorUnidadeBase = precoPorUnidadeBase(precoValido, item),
                disponivel = registro?.disponivel ?: true,
                melhorPreco = podeDestacar && precoValido != null &&
                    menor != null && precoValido.compareTo(menor.preco) == 0,
                registrado = registro != null,
            )
        }

        val economia = if (podeDestacar && menor != null && maior != null) {
            maior.preco.subtract(menor.preco).setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }
        val economiaPercentual = if (economia.signum() > 0 && maior != null && maior.preco.signum() > 0) {
            economia.multiply(CEM).divide(maior.preco, 2, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }

        val categoria = categorias[produto.categoriaId]
        return LinhaComparativa(
            itemId = item.id,
            produtoId = produto.id,
            descricao = produto.nome,
            detalhe = Formatadores.descricaoEmbalagem(
                quantidade = item.quantidade,
                unidade = item.unidade,
                pesoOuVolume = item.pesoOuVolume,
                itensPorKit = item.itensPorKit,
            ),
            categoriaId = categoria?.id ?: 0L,
            nomeCategoria = categoria?.nome ?: "Outros",
            celulas = celulas,
            menorPreco = menor?.preco,
            maiorPreco = maior?.preco,
            economiaEntreLojas = economia,
            economiaPercentualEntreLojas = economiaPercentual,
            melhorEstabelecimentoId = if (podeDestacar) menor?.estabelecimentoId else null,
            vereditoKit = vereditos[item.id],
            alertaRestricao = alertaDeRestricao(itemComProduto, perfil),
            selos = produto.selosAltoEm,
        )
    }

    private fun montarTotal(
        loja: Estabelecimento,
        itens: List<ItemComProduto>,
        precosPorItem: Map<Long, Map<Long, PrecoRegistrado>>,
    ): TotalEstabelecimento {
        var total = BigDecimal.ZERO
        var disponiveis = 0
        val ausentes = mutableListOf<String>()
        val semPreco = mutableListOf<String>()

        itens.forEach { itemComProduto ->
            val registro = precosPorItem[itemComProduto.item.id]?.get(loja.id)
            when {
                registro == null -> semPreco += itemComProduto.produto.nome
                !registro.disponivel -> ausentes += itemComProduto.produto.nome
                registro.preco.signum() <= 0 -> semPreco += itemComProduto.produto.nome
                else -> {
                    total = total.add(totalDaLinha(registro.preco, itemComProduto.item))
                    disponiveis++
                }
            }
        }

        return TotalEstabelecimento(
            estabelecimentoId = loja.id,
            nome = loja.nome,
            corHex = loja.corHex,
            total = total.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            itensDisponiveis = disponiveis,
            itensAusentes = ausentes,
            itensSemPreco = semPreco,
            cestaCompleta = itens.isNotEmpty() && disponiveis == itens.size,
        )
    }

    /**
     * Compra mista otima: cada item na loja onde ele esta mais barato.
     *
     * A economia e medida contra a melhor alternativa de loja unica. Quando
     * nenhuma loja tem a cesta completa, usamos a de maior cobertura como
     * referencia e comparamos apenas o subconjunto que ela cobre - comparar com
     * uma cesta incompleta inflaria a economia artificialmente.
     */
    private fun calcularCompraMista(
        itens: List<ItemComProduto>,
        estabelecimentos: List<Estabelecimento>,
        precosPorItem: Map<Long, Map<Long, PrecoRegistrado>>,
        totais: List<TotalEstabelecimento>,
    ): CompraMistaOtima? {
        if (itens.isEmpty() || estabelecimentos.size < 2) return null

        val escolha = LinkedHashMap<Long, Long>()
        var totalMisto = BigDecimal.ZERO

        itens.forEach { itemComProduto ->
            val melhor = precosPorItem[itemComProduto.item.id].orEmpty().values
                .filter { it.disponivel && it.preco.signum() > 0 }
                .minByOrNull { it.preco } ?: return@forEach
            escolha[itemComProduto.item.id] = melhor.estabelecimentoId
            totalMisto = totalMisto.add(totalDaLinha(melhor.preco, itemComProduto.item))
        }
        if (escolha.isEmpty()) return null

        val cestasCompletas = totais.filter { it.cestaCompleta && it.total.signum() > 0 }
        val referencia: TotalEstabelecimento
        val totalReferencia: BigDecimal

        if (cestasCompletas.isNotEmpty()) {
            referencia = cestasCompletas.minByOrNull { it.total }!!
            totalReferencia = referencia.total
        } else {
            val maiorCobertura = totais.maxByOrNull { it.itensDisponiveis } ?: return null
            if (maiorCobertura.itensDisponiveis == 0) return null
            referencia = maiorCobertura
            // Compara so o que essa loja cobre, para a economia nao ser inflada.
            var parcialMisto = BigDecimal.ZERO
            itens.forEach { itemComProduto ->
                val naReferencia = precosPorItem[itemComProduto.item.id]?.get(referencia.estabelecimentoId)
                if (naReferencia == null || !naReferencia.disponivel || naReferencia.preco.signum() <= 0) return@forEach
                val melhor = precosPorItem[itemComProduto.item.id].orEmpty().values
                    .filter { it.disponivel && it.preco.signum() > 0 }
                    .minByOrNull { it.preco } ?: return@forEach
                parcialMisto = parcialMisto.add(totalDaLinha(melhor.preco, itemComProduto.item))
            }
            totalReferencia = referencia.total
            val economiaParcial = totalReferencia.subtract(parcialMisto).max(BigDecimal.ZERO)
            return CompraMistaOtima(
                total = totalMisto.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
                escolhaPorItem = escolha,
                economiaReais = economiaParcial.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
                economiaPercentual = percentualDe(economiaParcial, totalReferencia),
                referenciaNome = referencia.nome,
                referenciaTotal = totalReferencia,
                lojasEnvolvidas = escolha.values.distinct().size,
            )
        }

        val economia = totalReferencia.subtract(totalMisto).max(BigDecimal.ZERO)
        return CompraMistaOtima(
            total = totalMisto.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            escolhaPorItem = escolha,
            economiaReais = economia.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN),
            economiaPercentual = percentualDe(economia, totalReferencia),
            referenciaNome = referencia.nome,
            referenciaTotal = totalReferencia,
            lojasEnvolvidas = escolha.values.distinct().size,
        )
    }

    private fun percentualDe(parte: BigDecimal, total: BigDecimal): BigDecimal =
        if (total.signum() <= 0) BigDecimal.ZERO
        else parte.multiply(CEM).divide(total, 2, RoundingMode.HALF_EVEN)

    /**
     * Entrada publica dos vereditos de embalagem: dado o acervo de precos da
     * lista, devolve, por itemId, o comparativo entre as duas formas de
     * comprar o mesmo produto que ja estao na lista (menor preco anotado de
     * cada). E o que alimenta `LinhaComparativa.vereditoKit` na matriz e a
     * dica inicial da folha de kit x avulso (via ListaViewModel).
     */
    fun vereditosDeEmbalagem(
        itens: List<ItemComProduto>,
        precos: List<PrecoRegistrado>,
    ): Map<Long, VereditoDeOpcoes> {
        val precosPorItem = precos
            .groupBy { it.itemDaListaId }
            .mapValues { (_, lista) -> lista.associateBy { it.estabelecimentoId } }
        return calcularVereditosDeEmbalagem(itens, precosPorItem)
    }

    /**
     * Procura, dentro da propria lista, dois tamanhos do mesmo produto
     * ("Coca Cola 2L" e "Coca Cola 350ml") e emite o veredito kit x avulso.
     * O agrupamento usa o nome sem a quantidade.
     */
    private fun calcularVereditosDeEmbalagem(
        itens: List<ItemComProduto>,
        precosPorItem: Map<Long, Map<Long, PrecoRegistrado>>,
    ): Map<Long, VereditoDeOpcoes> {
        val resultado = HashMap<Long, VereditoDeOpcoes>()

        val grupos = itens.groupBy { itemComProduto ->
            TextoUtil.normalizar(ConversorDeUnidades.nomeSemQuantidade(itemComProduto.produto.nomeNormalizado))
        }

        grupos.forEach { (_, doGrupo) ->
            if (doGrupo.size < 2) return@forEach

            val opcoes = doGrupo.mapNotNull { itemComProduto ->
                val melhorPreco = precosPorItem[itemComProduto.item.id].orEmpty()
                    .filterValues { it.disponivel && it.preco.signum() > 0 }
                    .values.minByOrNull { it.preco }?.preco ?: return@mapNotNull null
                val item = itemComProduto.item
                item.id to OpcaoDeCompra(
                    rotulo = Formatadores.descricaoEmbalagem(
                        quantidade = BigDecimal.ONE,
                        unidade = item.unidade,
                        pesoOuVolume = item.pesoOuVolume,
                        itensPorKit = item.itensPorKit,
                    ),
                    preco = melhorPreco,
                    quantidade = item.pesoOuVolume ?: BigDecimal.ONE,
                    unidade = item.unidade,
                    itensPorEmbalagem = item.itensPorKit,
                )
            }
            if (opcoes.size < 2) return@forEach

            val ordenadas = opcoes.sortedBy { precoPorUnidadeBase(it.second) ?: BigDecimal.ZERO }
            val melhor = ordenadas.first()
            val alternativa = ordenadas.last()
            if (melhor.first == alternativa.first) return@forEach

            val veredito = compararOpcoes(melhor.second, alternativa.second) ?: return@forEach
            doGrupo.forEach { itemComProduto -> resultado[itemComProduto.item.id] = veredito }
        }
        return resultado
    }

    /**
     * Alerta de restricao alimentar para um item, segundo o perfil do usuario:
     * alergenos oficiais (RDC 26/2015), "sem glúten" e as restricoes
     * customizadas criadas pelo proprio usuario.
     * Informativo: o app avisa, nunca remove nem reordena nada por conta disso.
     *
     * A regra completa vive em [VerificadorDeAlergias] (pura, testavel); aqui
     * fica so a delegacao para o primeiro alerta, formato String? de hoje.
     *
     * @return o texto do alerta, ou null quando nao ha conflito.
     */
    fun alertaDeRestricao(itemComProduto: ItemComProduto, perfil: PerfilRestricoes): String? {
        return VerificadorDeAlergias.alertaUnico(itemComProduto.produto, perfil)
    }
}

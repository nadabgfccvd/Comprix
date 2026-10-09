package br.com.comprix.domain.preco

import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.util.Formatadores

/**
 * Auditoria da **lista mestra**: antes de comparar, o app confere se cada
 * produto tem preco em TODAS as lojas pesquisadas e avisa, item por item, o
 * que esta faltando e onde.
 *
 * ## Por que isso existe
 * A lista e uma so (a "lista mestra"); as lojas sao colunas de preco sobre ela.
 * Se o oleo tem preco no Mercado 1 e nao tem no Mercado 2, a comparacao
 * daquela linha e invalida - e o total do Mercado 2 fica artificialmente mais
 * barato so porque um item nao foi pesquisado. Sem esse aviso, o app levaria o
 * usuario a uma conclusao errada; e exatamente o oposto do que ele serve.
 *
 * A auditoria distingue tres situacoes, que **nao** sao a mesma coisa:
 *
 * | Situacao            | Significado                               | Afeta a comparacao? |
 * |---------------------|-------------------------------------------|---------------------|
 * | `PENDENTE`          | ainda nao pesquisei o preco aqui          | sim - bloqueia      |
 * | `INDISPONIVEL`      | pesquisei e a loja nao tem o produto      | nao - e um dado     |
 * | registrado          | preco anotado                             | nao                 |
 *
 * "Nao tem na loja" e informacao legitima (entra como item ausente na matriz);
 * "ainda nao anotei" e uma lacuna que precisa aparecer no alto da tela.
 */
object AuditoriaDeCobertura {

    enum class Situacao { REGISTRADO, INDISPONIVEL, PENDENTE }

    /** Falta de um item especifico numa loja especifica. */
    data class Pendencia(
        val itemId: Long,
        val produtoId: Long,
        val nomeProduto: String,
        val detalhe: String,
        val estabelecimentoId: Long,
        val nomeEstabelecimento: String,
    ) {
        /** "Falta o preço de Óleo de soja 900 mL no Mercado 2" */
        val mensagem: String
            get() = buildString {
                append("Falta o preço de ")
                append(nomeProduto)
                if (detalhe.isNotBlank()) append(" ").append(detalhe)
                append(" no ").append(nomeEstabelecimento)
            }
    }

    /** Situacao de uma loja: o que falta nela e o que ela nao tem. */
    data class CoberturaDaLoja(
        val estabelecimento: Estabelecimento,
        val registrados: Int,
        val indisponiveis: List<String>,
        val pendencias: List<Pendencia>,
        val totalDeItens: Int,
    ) {
        val completa: Boolean get() = pendencias.isEmpty()
        val progresso: Float
            get() = if (totalDeItens == 0) 1f else (registrados + indisponiveis.size).toFloat() / totalDeItens
        val resumo: String
            get() = when {
                totalDeItens == 0 -> "Sem itens na lista"
                completa && indisponiveis.isEmpty() -> "Todos os $totalDeItens itens com preço"
                completa -> "Pesquisa concluída (${indisponiveis.size} sem estoque)"
                else -> "Faltam ${pendencias.size} de $totalDeItens itens"
            }
    }

    /** Linha da lista mestra: como cada item esta em cada loja. */
    data class ItemDaListaMestra(
        val itemId: Long,
        val nomeProduto: String,
        val detalhe: String,
        val situacaoPorLoja: Map<Long, Situacao>,
    ) {
        val lojasPendentes: List<Long>
            get() = situacaoPorLoja.filterValues { it == Situacao.PENDENTE }.keys.toList()
        val comparavel: Boolean
            get() = situacaoPorLoja.values.count { it == Situacao.REGISTRADO } >= 2
        val semNenhumPreco: Boolean
            get() = situacaoPorLoja.values.none { it == Situacao.REGISTRADO }
    }

    data class Relatorio(
        val itens: List<ItemDaListaMestra>,
        val porLoja: List<CoberturaDaLoja>,
        val pendencias: List<Pendencia>,
        val itensSemNenhumPreco: List<String>,
        val totalDeItens: Int,
    ) {
        /** Nenhuma lacuna: a comparacao pode ser lida sem ressalva. */
        val completa: Boolean get() = pendencias.isEmpty() && totalDeItens > 0

        val itensComPendencia: Int get() = pendencias.map { it.itemId }.distinct().size

        /**
         * Texto do painel fixo que fica no alto da tela, antes da lista.
         * Sempre visivel enquanto houver lacuna - e a queixa que o app precisa
         * responder sem o usuario ter que procurar.
         */
        val aviso: String?
            get() = when {
                totalDeItens == 0 -> null
                porLoja.size < 2 && pendencias.isEmpty() -> null
                completa -> null
                porLoja.isEmpty() -> null
                else -> {
                    val lojas = pendencias.map { it.nomeEstabelecimento }.distinct()
                    val quantos = itensComPendencia
                    val plural = if (quantos == 1) "item" else "itens"
                    "$quantos $plural sem preço em ${Formatadores.listaDeNomes(lojas)}"
                }
            }

        /** Pendencias agrupadas por loja, prontas para o painel. */
        fun pendenciasDaLoja(estabelecimentoId: Long): List<Pendencia> =
            pendencias.filter { it.estabelecimentoId == estabelecimentoId }
    }

    /**
     * Cruza a lista mestra com os precos registrados.
     *
     * @param itens itens da lista mestra (um por produto, compartilhado entre lojas).
     * @param estabelecimentos lojas pesquisadas.
     * @param precos todos os precos da lista, de todas as lojas.
     */
    fun auditar(
        itens: List<ItemComProduto>,
        estabelecimentos: List<Estabelecimento>,
        precos: List<PrecoRegistrado>,
    ): Relatorio {
        if (itens.isEmpty() || estabelecimentos.isEmpty()) {
            return Relatorio(emptyList(), emptyList(), emptyList(), emptyList(), itens.size)
        }

        val precosPorItem: Map<Long, Map<Long, PrecoRegistrado>> = precos
            .groupBy { it.itemDaListaId }
            .mapValues { (_, lista) -> lista.associateBy { it.estabelecimentoId } }

        val pendencias = mutableListOf<Pendencia>()
        val semNenhumPreco = mutableListOf<String>()

        val linhas = itens.map { itemComProduto ->
            val item = itemComProduto.item
            val detalhe = Formatadores.descricaoEmbalagem(
                quantidade = item.quantidade,
                unidade = item.unidade,
                pesoOuVolume = item.pesoOuVolume,
                itensPorKit = item.itensPorKit,
            )
            val situacoes = estabelecimentos.associate { loja ->
                val registro = precosPorItem[item.id]?.get(loja.id)
                val situacao = when {
                    registro == null -> Situacao.PENDENTE
                    !registro.disponivel -> Situacao.INDISPONIVEL
                    registro.preco.signum() <= 0 -> Situacao.PENDENTE
                    else -> Situacao.REGISTRADO
                }
                if (situacao == Situacao.PENDENTE) {
                    pendencias += Pendencia(
                        itemId = item.id,
                        produtoId = itemComProduto.produto.id,
                        nomeProduto = itemComProduto.produto.nome,
                        detalhe = detalhe,
                        estabelecimentoId = loja.id,
                        nomeEstabelecimento = loja.nome,
                    )
                }
                loja.id to situacao
            }
            val linha = ItemDaListaMestra(
                itemId = item.id,
                nomeProduto = itemComProduto.produto.nome,
                detalhe = detalhe,
                situacaoPorLoja = situacoes,
            )
            if (linha.semNenhumPreco) semNenhumPreco += itemComProduto.produto.nome
            linha
        }

        val porLoja = estabelecimentos.map { loja ->
            val doGrupo = linhas.map { it.situacaoPorLoja[loja.id] ?: Situacao.PENDENTE }
            CoberturaDaLoja(
                estabelecimento = loja,
                registrados = doGrupo.count { it == Situacao.REGISTRADO },
                indisponiveis = linhas
                    .filter { it.situacaoPorLoja[loja.id] == Situacao.INDISPONIVEL }
                    .map { it.nomeProduto },
                pendencias = pendencias.filter { it.estabelecimentoId == loja.id },
                totalDeItens = linhas.size,
            )
        }

        return Relatorio(
            itens = linhas,
            porLoja = porLoja,
            pendencias = pendencias,
            itensSemNenhumPreco = semNenhumPreco,
            totalDeItens = itens.size,
        )
    }
}

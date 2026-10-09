package br.com.comprix.domain.lista

import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.util.TextoUtil

/**
 * Organiza os itens da lista para a tela de compra (Secao 3.3).
 *
 * A ordem padrao segue o **caminho fisico do mercado**: hortifruti na entrada,
 * padaria e acougue no fundo, congelados por ultimo para nao derreter. Isso
 * poupa o usuario de ziguezaguear entre os corredores, que e o objetivo real
 * de agrupar por categoria.
 *
 * Dentro de cada categoria vale a ordem manual definida pelo usuario (tela
 * "Reordenar") e, em caso de empate, a ordem alfabetica.
 */
object OrganizadorDeLista {

    data class GrupoDeItens(
        val categoria: Categoria,
        val itens: List<ItemComProduto>,
    ) {
        val comprados: Int get() = itens.count { it.item.comprado }
        val concluido: Boolean get() = itens.isNotEmpty() && comprados == itens.size
    }

    /**
     * Agrupa por categoria na ordem do mercado.
     *
     * @param moverCompradosParaOFim quando true, os itens ja marcados descem
     * dentro do grupo - o que falta fica sempre visivel no alto.
     */
    fun agrupar(
        itens: List<ItemComProduto>,
        categorias: Map<Long, Categoria>,
        moverCompradosParaOFim: Boolean = true,
    ): List<GrupoDeItens> {
        if (itens.isEmpty()) return emptyList()

        return itens
            .groupBy { it.produto.categoriaId }
            .map { (categoriaId, doGrupo) ->
                val categoria = categorias[categoriaId] ?: categoriaDesconhecida(categoriaId)
                GrupoDeItens(
                    categoria = categoria,
                    itens = doGrupo.sortedWith(
                        compareBy<ItemComProduto> { if (moverCompradosParaOFim && it.item.comprado) 1 else 0 }
                            .thenBy { it.item.ordemManual }
                            .thenBy { TextoUtil.normalizar(it.produto.nome) },
                    ),
                )
            }
            .sortedWith(compareBy({ it.categoria.ordemPadrao }, { TextoUtil.normalizar(it.categoria.nome) }))
    }

    /** Lista plana, sem agrupamento (modo "lista corrida"). */
    fun ordenarPlano(itens: List<ItemComProduto>, categorias: Map<Long, Categoria>): List<ItemComProduto> =
        itens.sortedWith(
            compareBy<ItemComProduto> { if (it.item.comprado) 1 else 0 }
                .thenBy { categorias[it.produto.categoriaId]?.ordemPadrao ?: Int.MAX_VALUE }
                .thenBy { it.item.ordemManual }
                .thenBy { TextoUtil.normalizar(it.produto.nome) },
        )

    /** Busca tolerante a acento e a erro de digitacao ("acucar" acha "Açúcar"). */
    fun filtrar(itens: List<ItemComProduto>, consulta: String): List<ItemComProduto> {
        val alvo = TextoUtil.normalizar(consulta)
        if (alvo.isBlank()) return itens
        return itens.filter { itemComProduto ->
            val nome = TextoUtil.normalizar(itemComProduto.produto.nome)
            nome.contains(alvo) ||
                itemComProduto.produto.codigoBarras?.contains(alvo) == true ||
                TextoUtil.similaridade(nome, alvo) >= 0.82
        }
    }

    /** Progresso da compra: 0f a 1f. */
    fun progresso(itens: List<ItemComProduto>): Float {
        if (itens.isEmpty()) return 0f
        return itens.count { it.item.comprado }.toFloat() / itens.size
    }

    /**
     * Reatribui [br.com.comprix.domain.modelo.ItemDaLista.ordemManual] depois de
     * uma reordenacao, em passos de 10 para caber insercoes futuras sem
     * reescrever a lista inteira.
     */
    fun recalcularOrdem(itensNaNovaOrdem: List<ItemComProduto>): Map<Long, Int> =
        itensNaNovaOrdem.mapIndexed { indice, itemComProduto ->
            itemComProduto.item.id to (indice + 1) * 10
        }.toMap()

    private fun categoriaDesconhecida(id: Long) = Categoria(
        id = id,
        nome = "Outros",
        icone = "outros",
        ordemPadrao = 99,
    )
}

package br.com.comprix.data.repositorio

import br.com.comprix.data.local.ContagemPorProduto
import br.com.comprix.data.local.ItemDao
import br.com.comprix.data.local.ListaDao
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.local.PrecoDao
import br.com.comprix.data.local.ProdutoDao
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.ResumoDeLista
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.util.Formatadores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Listas de compras e seus itens.
 *
 * As consultas combinam tres fluxos (listas, itens, precos) para que a tela
 * inicial mostre progresso e total estimado sempre atualizados - sem recarregar
 * nada na mao.
 */
class ListaRepositorio(
    private val listaDao: ListaDao,
    private val itemDao: ItemDao,
    private val produtoDao: ProdutoDao,
    private val precoDao: PrecoDao,
    private val lixeira: LixeiraRepositorio? = null,
) {

    val listas: Flow<List<ListaDeCompras>> =
        listaDao.observarTodas().map { lista -> lista.map(Mapeadores::paraDominio) }

    /** Resumo de cada lista: quantos itens, quantos comprados e quanto deve custar. */
    val resumos: Flow<List<ResumoDeLista>> =
        combine(listaDao.observarTodas(), itemDao.observarTodos()) { listas, itens ->
            val itensPorLista = itens.groupBy { it.listaId }
            listas.map { entidade ->
                val doGrupo = itensPorLista[entidade.id].orEmpty()
                ResumoDeLista(
                    lista = Mapeadores.paraDominio(entidade),
                    totalDeItens = doGrupo.size,
                    itensComprados = doGrupo.count { it.comprado },
                    totalEstimado = BigDecimal.ZERO,
                    itensSemPreco = 0,
                )
            }
        }

    fun observarLista(id: Long): Flow<ListaDeCompras?> =
        listaDao.observar(id).map { entidade -> entidade?.let(Mapeadores::paraDominio) }

    /** Itens de uma lista ja casados com o produto - o que as telas consomem. */
    fun observarItens(listaId: Long): Flow<List<ItemComProduto>> =
        combine(itemDao.observarDaLista(listaId), produtoDao.observarTodos()) { itens, produtos ->
            val porId = produtos.associateBy { it.id }
            itens.mapNotNull { entidade ->
                val produto = porId[entidade.produtoId] ?: return@mapNotNull null
                ItemComProduto(Mapeadores.paraDominio(entidade), Mapeadores.paraDominio(produto))
            }
        }

    suspend fun listarItens(listaId: Long): List<ItemComProduto> {
        val itens = itemDao.listarDaLista(listaId)
        if (itens.isEmpty()) return emptyList()
        val produtos = produtoDao.porIds(itens.map { it.produtoId }.distinct()).associateBy { it.id }
        return itens.mapNotNull { entidade ->
            val produto = produtos[entidade.produtoId] ?: return@mapNotNull null
            ItemComProduto(Mapeadores.paraDominio(entidade), Mapeadores.paraDominio(produto))
        }
    }

    suspend fun lista(id: Long): ListaDeCompras? = listaDao.porId(id)?.let(Mapeadores::paraDominio)

    /** Cria uma lista; nome em branco vira "Compras de 1 de outubro". */
    suspend fun criarLista(nome: String?): Long {
        val titulo = nome?.trim()?.takeIf { it.isNotBlank() } ?: Formatadores.nomeSugeridoDeLista()
        return listaDao.inserir(Mapeadores.paraEntidade(ListaDeCompras(nome = titulo)))
    }

    suspend fun renomearLista(lista: ListaDeCompras, novoNome: String) {
        listaDao.atualizar(Mapeadores.paraEntidade(lista.copy(nome = novoNome.trim())))
    }

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias). */
    suspend fun removerLista(id: Long) {
        if (lixeira != null) {
            lixeira.enviarLista(id)
            return
        }
        listaDao.remover(id)
    }

    /** Favoritas: resumos das listas marcadas (seção própria na tela de listas). */
    val resumosFavoritas: Flow<List<ResumoDeLista>> =
        resumos.map { lista -> lista.filter { it.lista.favorita } }

    suspend fun favoritarLista(listaId: Long, favorita: Boolean) {
        val lista = listaDao.porId(listaId) ?: return
        listaDao.atualizar(lista.copy(favorita = favorita))
    }

    suspend fun marcarFinalizada(listaId: Long, momento: LocalDateTime = LocalDateTime.now()) {
        val lista = listaDao.porId(listaId) ?: return
        listaDao.atualizar(lista.copy(finalizada = true, finalizadaEm = Mapeadores.dataHoraParaEpoch(momento)))
    }

    suspend fun reabrirLista(listaId: Long) {
        val lista = listaDao.porId(listaId) ?: return
        listaDao.atualizar(lista.copy(finalizada = false, finalizadaEm = null))
    }

    /**
     * Define o orcamento da lista em centavos; `null` remove o orcamento.
     *
     * E um dado da LISTA (sobrevive a reabrir, duplicar nao copia de proposito
     * - o orcamento da copia e outro orcamento). A doca da tela da lista usa
     * [nivelDoOrcamento] para colorir o alerta de estouro.
     */
    suspend fun definirOrcamento(listaId: Long, orcamentoCentavos: Long?) {
        val lista = listaDao.porId(listaId) ?: return
        listaDao.atualizar(lista.copy(orcamentoCentavos = orcamentoCentavos))
    }

    /**
     * Produtos com mais registros no historico de precos, do mais frequente
     * para o menos (empate cai para o registro mais recente). E a materia-prima
     * do cartao "Comprar de novo" da tela da lista; a conversao de id para
     * Produto fica com o CatalogoRepositorio, que ja sabe mapear.
     */
    suspend fun produtosMaisRegistrados(limite: Int): List<ContagemPorProduto> =
        precoDao.produtosMaisRegistrados(limite)

    /**
     * Duplica uma lista (jornada 4): copia os itens desmarcados e **nao** copia
     * os precos - eles mudam toda semana, e trazer preco velho induziria o
     * usuario a acreditar numa comparacao vencida.
     *
     * O nome da copia e numerado contra o acumulo: a base perde os sufixos
     * " (cópia)" que ja tiver e o proximo indice livre entra no lugar, entao
     * duplicar 28 vezes "Compra do Mês" cria "Compra do Mês (cópia 28)" - nunca
     * "Compra do Mês (cópia) (cópia) (cópia)". Um [novoNome] passado pelo
     * usuario entra PURO; se colidir com um nome existente, recebe " (cópia N)"
     * pela mesma regra.
     */
    suspend fun duplicarLista(listaId: Long, novoNome: String? = null): Long? {
        val original = listaDao.porId(listaId) ?: return null
        val itens = itemDao.listarDaLista(listaId)
        val escolhido = novoNome?.trim()?.takeIf { it.isNotBlank() }
        val nomesExistentes = listaDao.listarTodas().map { it.nome }
        val nome = if (escolhido != null && escolhido !in nomesExistentes) {
            // Nome do usuario livre: entra exatamente como foi digitado.
            escolhido
        } else {
            // Sem nome do usuario, ou nome colidindo: numeracao automatica a
            // partir da base (o escolhido entra como base para o sufixo novo).
            proximoNomeDeCopia(nomesExistentes, escolhido ?: original.nome)
        }
        val novoId = listaDao.inserir(
            Mapeadores.paraEntidade(
                ListaDeCompras(nome = nome),
            ),
        )
        if (itens.isNotEmpty()) {
            itemDao.inserirTodos(
                itens.map { item -> item.copy(id = 0, listaId = novoId, comprado = false) },
            )
        }
        return novoId
    }

    // --- itens -----------------------------------------------------------------------

    suspend fun adicionarItem(item: ItemDaLista): Long {
        val ordem = (itemDao.contarDaLista(item.listaId) + 1) * 10
        return itemDao.inserir(Mapeadores.paraEntidade(item.copy(ordemManual = ordem)))
    }

    /**
     * Soma 1 a quantidade da PRIMEIRA ocorrencia do [produtoId] na lista.
     *
     * E o caminho do atalho de favoritos (1 toque) quando o produto ja estava
     * na lista: nunca duplica o item - a quantidade cresce. NAO aplica em
     * lista finalizada (o chamador decide); aqui e so o incremento.
     *
     * @return `true` quando encontrou e somou; `false` quando o produto nao
     *   tem nenhum item nessa lista (o chamador entao cria o item novo).
     */
    suspend fun somarQuantidadeDoProduto(listaId: Long, produtoId: Long): Boolean {
        val existente = listarItens(listaId)
            .firstOrNull { it.item.produtoId == produtoId }
            ?: return false
        atualizarItem(existente.item.copy(quantidade = existente.item.quantidade + BigDecimal.ONE))
        return true
    }

    suspend fun atualizarItem(item: ItemDaLista) = itemDao.atualizar(Mapeadores.paraEntidade(item))

    suspend fun removerItem(item: ItemDaLista) = itemDao.remover(Mapeadores.paraEntidade(item))

    suspend fun marcarComprado(itemId: Long, comprado: Boolean) = itemDao.marcarComprado(itemId, comprado)

    suspend fun desmarcarTodos(listaId: Long) = itemDao.desmarcarTodos(listaId)

    suspend fun aplicarOrdem(novaOrdem: Map<Long, Int>) {
        if (novaOrdem.isEmpty()) return
        val atualizados = novaOrdem.mapNotNull { (itemId, ordem) ->
            itemDao.porId(itemId)?.copy(ordemManual = ordem)
        }
        if (atualizados.isNotEmpty()) itemDao.atualizarTodos(atualizados)
    }

    suspend fun item(id: Long): ItemDaLista? = itemDao.porId(id)?.let(Mapeadores::paraDominio)

    /** Total estimado pelo menor preco registrado de cada item. */
    suspend fun totalEstimado(listaId: Long): BigDecimal {
        val itens = listarItens(listaId)
        val precos = precoDao.listarDaLista(listaId).map(Mapeadores::paraDominio)
        if (itens.isEmpty() || precos.isEmpty()) return BigDecimal.ZERO
        val porItem = precos.groupBy { it.itemDaListaId }
        return itens.fold(BigDecimal.ZERO) { soma, itemComProduto ->
            val menor = porItem[itemComProduto.item.id].orEmpty()
                .filter { it.disponivel && it.preco.signum() > 0 }
                .minByOrNull { it.preco } ?: return@fold soma
            soma.add(MotorDePrecos.totalDaLinha(menor.preco, itemComProduto.item))
        }
    }

    companion object {
        /** Sufixo de copia no FIM do nome: "(cópia)", "(copia)" (sem acento) ou "(cópia N)". */
        private val SUFIXO_DE_COPIA = Regex("\\s*\\((?:cópia|copia)(?:\\s+(\\d+))?\\)\\s*$")

        /**
         * Nome da proxima copia de uma lista, numerado para nao acumular sufixos.
         *
         * A base e extraida de [baseBruta] removendo, REPETIDAMENTE, o sufixo
         * " (cópia)" / " (cópia N)" do fim - "Compra do Mês (cópia) (cópia 3)"
         * volta a ser "Compra do Mês". Entre os [nomes] existentes, os que tem
         * a MESMA base contam como copias: o nome puro da base vale 0,
         * "base (cópia)" vale 1 e "base (cópia 5)" vale 5. A proxima copia usa
         * o maior indice + 1; a primeira (nenhuma existente) fica sem numero,
         * "base (cópia)". Se o nome proposto ainda assim existir, o indice
         * avanca ate achar um livre.
         *
         * Funcao pura, sem DAO: testavel direto e reusavel pelo nome digitado
         * pelo usuario quando ele colide com um existente.
         */
        fun proximoNomeDeCopia(nomes: List<String>, baseBruta: String): String {
            val base = baseDoNome(baseBruta)
            val existentes = nomes.toSet()
            val maiorIndice = nomes.maxOfOrNull { indiceDaCopia(it, base) } ?: 0
            var indice = maiorIndice + 1
            var nome = if (maiorIndice == 0) "$base (cópia)" else "$base (cópia $indice)"
            while (nome in existentes) {
                indice += 1
                nome = "$base (cópia $indice)"
            }
            return nome
        }

        /** Nome sem NENHUM sufixo de copia no fim ("Compra do Mês (cópia 3)" vira "Compra do Mês"). */
        private fun baseDoNome(nome: String): String {
            var atual = nome.trim()
            while (true) {
                val casamento = SUFIXO_DE_COPIA.find(atual) ?: break
                atual = atual.substring(0, casamento.range.first).trim()
            }
            // Nome que era so sufixo ("(cópia)") nao vira base vazia.
            return atual.ifBlank { nome.trim() }
        }

        /**
         * Indice de copia que [nome] representa para a [base]: puro = 0,
         * "base (cópia)" = 1, "base (cópia 5)" = 5. Nome de outra base = 0.
         */
        private fun indiceDaCopia(nome: String, base: String): Int {
            if (baseDoNome(nome) != base) return 0
            val casamento = SUFIXO_DE_COPIA.find(nome) ?: return 0
            return casamento.groupValues[1].toIntOrNull() ?: 1
        }

        /**
         * Nivel do orcamento para a doca da lista, pelo percentual atingido:
         *
         * - 0 = tranquilo: ate 79% do orcamento gasto (barra verde);
         * - 1 = atencao: de 80% a 99% (barra ambar);
         * - 2 = estourou: 100% ou mais (doca vermelha + legenda do excesso).
         *
         * Funcao pura em centavos, sem DAO nem rounding decimal: a divisao
         * inteira truncada e de proposito, entao 7999/10000 da 79% (nivel 0)
         * e 8000/10000 da 80% (nivel 1), sem caso de borda duvidoso.
         * Orcamento nao positivo nao tem nivel (a tela nem desenha a linha).
         *
         * @return 0, 1 ou 2 - ver acima.
         */
        fun nivelDoOrcamento(totalCentavos: Long, orcamentoCentavos: Long): Int {
            if (orcamentoCentavos <= 0) return 0
            val percentual = totalCentavos * 100 / orcamentoCentavos
            return when {
                percentual >= 100 -> 2
                percentual >= 80 -> 1
                else -> 0
            }
        }
    }
}

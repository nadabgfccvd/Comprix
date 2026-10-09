package br.com.comprix.presentation.lojas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.util.TextoUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tela "Minhas lojas": os estabelecimentos onde o usuario registra precos,
 * com renomear, excluir e adicionar.
 *
 * A lista de lojas e reativa (Flow do Room): renomear ou excluir reflete na
 * hora. As **contagens por loja** nao tem fluxo global no repositorio (os
 * precos sao observados por lista), entao sao levantadas por snapshot quando
 * o estado e recalculado - ao abrir a tela (via [atualizar]) e apos cada
 * acao da propria tela. Precos gravados em outra tela aparecem ao reabrir.
 *
 * @param listaRepositorio OPCIONAL: sem ele a tela funciona igual (renomear,
 *   excluir, adicionar), mas sem as contagens "X precos - Y itens" nem a
 *   lista de produtos do "Ver", porque e ele quem permite percorrer as
 *   listas e achar os precos e os nomes de produto. O ServiceLocator deve
 *   passar ServiceLocator.listaRepositorio para habilitar tudo.
 */
class LojasViewModel(
    private val precoRepositorio: PrecoRepositorio,
    private val listaRepositorio: ListaRepositorio? = null,
) : ViewModel() {

    /** Linha da tela: a loja e o quanto de dados dela ja existe no app. */
    data class LojaDaRede(
        val estabelecimento: Estabelecimento,
        /**
         * Total de registros de preco validos registrados na loja (disponivel
         * e > 0), somando TODAS as listas; null = contagem indisponivel.
         */
        val quantidadeDePrecos: Int? = null,
        /**
         * Quantidade de NOMES de produto distintos com preco valido - e o
         * "Y itens" do cartao e o mesmo numero que [produtosDaLoja].size,
         * ou seja, o que o "Ver" lista. O mesmo produto anotado em 2 listas
         * conta 1 vez so. null = contagem indisponivel.
         */
        val itensDistintos: Int? = null,
        /**
         * TODOS os nomes de produto distintos com preco nessa loja (dedupe
         * por nome normalizado via [TextoUtil.normalizar], ordem alfabetica)
         * - sem corte de amostra: o "Ver" do cartao lista exatamente esta
         * lista, entao resumo e detalhe nunca divergem. Um preco cujo item
         * nao resolve nome de produto nao aparece aqui (e nao conta em
         * [itensDistintos]), mas segue contando em [quantidadeDePrecos].
         */
        val produtosDaLoja: List<String> = emptyList(),
    )

    data class EstadoDasLojas(
        val lojas: List<LojaDaRede> = emptyList(),
        val carregando: Boolean = true,
        val mensagem: String? = null,
    )

    private val mensagemInterna = MutableStateFlow<String?>(null)

    /** Gatilho de re-leitura do snapshot de precos (contagens e produtos). */
    private val pedidoDeAtualizacao = MutableStateFlow(0)

    /** Relevanta as contagens; a tela chama ao entrar. */
    fun atualizar() {
        pedidoDeAtualizacao.value = pedidoDeAtualizacao.value + 1
    }

    val estado: StateFlow<EstadoDasLojas> = combine(
        precoRepositorio.estabelecimentos,
        pedidoDeAtualizacao,
        mensagemInterna,
    ) { estabelecimentos, _, mensagem ->
        EstadoDasLojas(
            lojas = montarLojas(estabelecimentos),
            carregando = false,
            mensagem = mensagem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDasLojas())

    /**
     * Cria a loja (ou reaproveita a que ja existe). O repositorio ja deduplica
     * por nome; aqui so traduzimos o resultado em mensagem.
     */
    fun adicionar(nome: String) {
        val limpo = nome.trim()
        if (limpo.isBlank()) return
        viewModelScope.launch {
            val chave = TextoUtil.normalizar(limpo)
            val jaExistia = precoRepositorio.estabelecimentos.first()
                .any { TextoUtil.normalizar(it.nome) == chave }
            val loja = precoRepositorio.garantirEstabelecimento(limpo)
            mensagemInterna.value = if (jaExistia) {
                "Loja \"${loja.nome}\" já está na sua rede."
            } else {
                "Loja \"${loja.nome}\" adicionada."
            }
            pedidoDeAtualizacao.value = pedidoDeAtualizacao.value + 1
        }
    }

    /**
     * Renomeia a loja. Bloqueia nomes que colidam com outra loja da rede,
     * comparando sem acento, caixa ou pontuacao (mesma chave do app inteiro).
     */
    fun renomear(loja: Estabelecimento, novoNome: String) {
        val limpo = novoNome.trim()
        if (limpo.isBlank()) return
        viewModelScope.launch {
            val chave = TextoUtil.normalizar(limpo)
            val conflito = precoRepositorio.estabelecimentos.first().any {
                it.id != loja.id && TextoUtil.normalizar(it.nome) == chave
            }
            if (conflito) {
                mensagemInterna.value = "Já existe uma loja com esse nome"
                return@launch
            }
            precoRepositorio.renomearEstabelecimento(loja.copy(nome = limpo), limpo)
            mensagemInterna.value = "Loja renomeada para \"$limpo\"."
        }
    }

    /**
     * Exclui a loja e os precos dela. O dialogo de confirmacao fica na tela -
     * aqui vai direto, como o resto das acoes destrutivas do app.
     *
     * O resto do app tolera a falta da loja: os estados que usam precos
     * recalculam sozinhos quando o estabelecimento some do fluxo.
     */
    fun remover(loja: Estabelecimento) {
        viewModelScope.launch {
            precoRepositorio.removerEstabelecimento(loja.id)
            mensagemInterna.value = "A loja foi para a lixeira (30 dias)."
            pedidoDeAtualizacao.value = pedidoDeAtualizacao.value + 1
        }
    }

    fun mensagemExibida() {
        mensagemInterna.value = null
    }

    /**
     * Monta as linhas da tela. Com [listaRepositorio], percorre as listas uma
     * vez para juntar precos e nomes de produto por loja; sem ele, devolve as
     * lojas com contagens indisponiveis (null) - a tela omite a linha.
     *
     * Semantica das contagens (a mesma em "Minhas lojas" e no "Comparar"):
     * - quantidadeDePrecos: total de registros de preco validos, somando
     *   todas as listas.
     * - itensDistintos: NOMES de produto distintos (dedupe por nome
     *   normalizado) - o mesmo produto em 2 listas conta 1 vez - e o que o
     *   "Ver" lista.
     * - Um preco cujo item nao resolve nome de produto (mapNotNull null,
     *   item apagado) NAO entra na contagem de itens nem na lista de
     *   produtos: nenhuma contagem "fantasma". Ele segue contando nos
     *   precos.
     */
    private suspend fun montarLojas(estabelecimentos: List<Estabelecimento>): List<LojaDaRede> {
        if (estabelecimentos.isEmpty()) return emptyList()
        val repositorioDeListas = listaRepositorio
            ?: return estabelecimentos.map { LojaDaRede(it) }

        val listas = repositorioDeListas.listas.first()
        if (listas.isEmpty()) return estabelecimentos.map { LojaDaRede(it, 0, 0) }

        val nomePorItem = mutableMapOf<Long, String>()
        val precosPorLoja = mutableMapOf<Long, MutableList<PrecoRegistrado>>()
        listas.forEach { lista ->
            val itens: List<ItemComProduto> = repositorioDeListas.listarItens(lista.id)
            itens.forEach { nomePorItem[it.item.id] = it.produto.nome }
            precoRepositorio.observarPrecosDaLista(lista.id).first().forEach { preco ->
                precosPorLoja.getOrPut(preco.estabelecimentoId) { mutableListOf() }.add(preco)
            }
        }

        return estabelecimentos.map { loja ->
            // Contam como preco so os registros validos: "nao tinha na loja"
            // e um dado legitimo, mas nao e um preco.
            val validos = precosPorLoja[loja.id].orEmpty()
                .filter { it.disponivel && it.preco.signum() > 0 }
            // Produtos distintos por NOME NORMALIZADO (TextoUtil.normalizar,
            // a mesma chave do app inteiro): o mesmo produto com caixa ou
            // acento diferente - ou anotado em listas diferentes - conta uma
            // vez so. Ordenado alfabeticamente para o "Ver" ficar legivel.
            val nomes = validos
                .mapNotNull { nomePorItem[it.itemDaListaId] }
                .distinctBy { TextoUtil.normalizar(it) }
                .sortedBy { TextoUtil.normalizar(it) }
            LojaDaRede(
                estabelecimento = loja,
                quantidadeDePrecos = validos.size,
                // "Y itens" = os NOMES distintos de cima, exatamente o que o
                // "Ver" do cartao lista (antes era por itemDaListaId, o que
                // contava o mesmo produto de 2 listas como 2 itens).
                itensDistintos = nomes.size,
                produtosDaLoja = nomes,
            )
        }
    }
}

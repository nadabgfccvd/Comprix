package br.com.comprix.presentation.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.domain.catalogo.FiltroDeProdutosLocais
import br.com.comprix.domain.catalogo.ItemDoCatalogo
import br.com.comprix.domain.catalogo.SetorDoCatalogo
import br.com.comprix.domain.catalogo.SubcategoriaDoCatalogo
import br.com.comprix.domain.modelo.Produto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado da tela de exploracao do catalogo-semente.
 *
 * @param consulta texto digitado na busca.
 * @param setorSelecionado id do setor filtrado; `null` mostra todos os setores.
 * @param carregando verdadeiro ate a primeira emissao do fluxo de estado.
 * @param setores os setores do acervo, para os chips de filtro.
 * @param grupos itens agrupados por subcategoria, na ordem do arquivo.
 * @param produtosLocais produtos do acervo LOCAL (Room) que casam com a busca,
 * num secao propria acima do acervo de fabrica - vazia sem consulta ou com
 * filtro de setor ativo (o setor filtra o acervo, nao o acervo pessoal).
 * @param total quantos itens estao visiveis agora (acervo + locais).
 * @param buscaLimitada verdadeiro quando a busca bruta atingiu o teto de
 * [LIMITE_DA_BUSCA] resultados (e nenhum filtro de setor esta ativo): a
 * contagem da tela explica que existe mais alem do recorte mostrado.
 */
data class EstadoDoCatalogo(
    val consulta: String = "",
    val setorSelecionado: Int? = null,
    val carregando: Boolean = true,
    val setores: List<SetorDoCatalogo> = emptyList(),
    val grupos: List<GrupoDoCatalogo> = emptyList(),
    val produtosLocais: List<Produto> = emptyList(),
    val total: Int = 0,
    val buscaLimitada: Boolean = false,
)

/** Bloco de uma subcategoria com seus itens, pronto para a lista agrupada. */
data class GrupoDoCatalogo(
    val subcategoria: SubcategoriaDoCatalogo,
    val itens: List<ItemDoCatalogo>,
)

/**
 * Explorar o catalogo-semente: buscar, filtrar por setor e mandar o produto
 * para a lista aberta.
 *
 * A tela chega aqui por dois caminhos: pelo menu da lista (com [listaId] >
 * 0, quando "adicionar" insere de fato) e pelas configuracoes (sem lista, so
 * consulta). O [adicionarNaLista] e injetado pela navegacao - o ViewModel nao
 * conhece o [br.com.comprix.di.ServiceLocator] - e o repositorio resolve o
 * produto (encontrar ou criar) com a categoria sugerida pelos cinco degraus.
 */
class CatalogoViewModel(
    private val catalogoRepositorio: CatalogoRepositorio,
    private val adicionarNaLista: suspend (listaId: Long, produto: Produto) -> Unit = { _, _ -> },
) : ViewModel() {

    private val _consulta = MutableStateFlow("")
    private val _setorSelecionado = MutableStateFlow<Int?>(null)

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val estado: StateFlow<EstadoDoCatalogo> = combine(
        _consulta,
        _setorSelecionado,
        catalogoRepositorio.produtos,
    ) { consulta, setor, produtosDoRoom ->
        Triple(consulta, setor, produtosDoRoom)
    }
        .map { (consulta, setor, produtosDoRoom) -> calcular(consulta, setor, produtosDoRoom) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDoCatalogo())

    /** Atualiza o termo de busca; a lista recolhe por prioridade (exato, prefixo, contem). */
    fun definirConsulta(valor: String) {
        _consulta.value = valor
    }

    /** Troca o filtro de setor; `null` limpa o filtro e volta a mostrar tudo. */
    fun selecionarSetor(setorId: Int?) {
        _setorSelecionado.value = setorId
    }

    /**
     * Sinonimos de busca de um produto do acervo local ("Mexerica" ->
     * "Tangerina"), direto do catalogo-semente. Funcao PURA em memoria -
     * barata o bastante para a renderizacao por item da secao
     * "Seus produtos".
     */
    fun sinonimosDoProduto(nome: String): List<String> =
        catalogoRepositorio.sinonimosDe(nome)

    /**
     * Encontra (ou cria) o produto do nome tocado e, quando ha lista aberta,
     * insere um item nela. Sem lista, o produto so entra no catalogo local -
     * e a mensagem deixa isso claro em vez de fingir sucesso.
     */
    fun adicionar(nomeDoItem: String, listaId: Long) {
        val nome = nomeDoItem.trim()
        if (nome.isBlank()) return
        viewModelScope.launch {
            val sugestao = catalogoRepositorio.sugerirCategoria(nome)
            val produto = catalogoRepositorio.encontrarOuCriar(
                nome = nome,
                categoriaId = sugestao.categoria.id,
            )
            if (listaId > 0) {
                adicionarNaLista(listaId, produto)
                _mensagem.value = "${produto.nome} adicionado à lista."
            } else {
                _mensagem.value = "${produto.nome} registrado no catálogo local."
            }
        }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }

    /**
     * Calcula o estado inteiro a partir do acervo. Roda fora da thread
     * principal ([Dispatchers.Default] no `flowOn`), porque filtrar 1.714 itens
     * por tecla e trabalho de fundo.
     */
    private fun calcular(
        consulta: String,
        setorSelecionado: Int?,
        produtosDoRoom: List<Produto>,
    ): EstadoDoCatalogo {
        val catalogo = catalogoRepositorio.dadosDoCatalogoSemente()
            ?: return EstadoDoCatalogo(consulta = consulta, setorSelecionado = setorSelecionado, carregando = false)

        val subvisiveis = if (setorSelecionado == null) {
            catalogo.subcategorias
        } else {
            catalogo.subcategoriasPorSetor[setorSelecionado].orEmpty()
        }
        val subPorId = subvisiveis.associateBy { it.id }

        val termo = consulta.trim()
        // A busca bruta (antes do filtro de setor) e guardada para saber se o
        // teto foi alcancado - o detalhe que a contagem da tela mostra.
        val buscaBruta = if (termo.isBlank()) null else catalogo.buscar(termo, LIMITE_DA_BUSCA)
        val itensVisiveis = if (buscaBruta == null) {
            catalogo.itens.filter { it.subcategoriaId in subPorId }
        } else {
            buscaBruta.filter { it.subcategoriaId in subPorId }
        }

        // Secao "Seus produtos": so com consulta digitada e sem filtro de
        // setor - a exploracao aberta e conteudo do acervo; o setor filtra o
        // acervo, nao o acervo pessoal.
        val produtosLocais = if (termo.isNotBlank() && setorSelecionado == null) {
            FiltroDeProdutosLocais.filtrar(termo, produtosDoRoom)
        } else {
            emptyList()
        }

        val grupos = itensVisiveis
            .groupBy { it.subcategoriaId }
            .mapNotNull { (subId, itens) ->
                subPorId[subId]?.let { subcategoria -> GrupoDoCatalogo(subcategoria, itens) }
            }
            .sortedBy { it.subcategoria.id }

        return EstadoDoCatalogo(
            consulta = consulta,
            setorSelecionado = setorSelecionado,
            carregando = false,
            setores = catalogo.setores,
            grupos = grupos,
            produtosLocais = produtosLocais,
            total = itensVisiveis.size + produtosLocais.size,
            // Com filtro de setor ativo a contagem ja e um recorte menor; o
            // aviso de teto so ajuda quando o rol inteiro vem da busca.
            buscaLimitada = buscaBruta != null &&
                buscaBruta.size >= LIMITE_DA_BUSCA &&
                setorSelecionado == null,
        )
    }

    private companion object {
        /** Teto da busca no acervo: a tela agrupa por subcategoria e o rol segue o resto. */
        const val LIMITE_DA_BUSCA = 120
    }
}

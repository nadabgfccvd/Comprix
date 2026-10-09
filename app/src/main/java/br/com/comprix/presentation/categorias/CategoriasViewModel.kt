package br.com.comprix.presentation.categorias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Gestao das categorias e da **ordem do mercado** (tela 14 da referencia).
 *
 * A ordem aqui nao e cosmetica: ela define a sequencia em que a lista agrupada
 * aparece no corredor. Quem comeca pelo hortifruti e termina na limpeza quer a
 * lista nessa ordem, nao em ordem alfabetica.
 *
 * ## Ordenacao otimista
 *
 * Arrastar reordena a lista **em memoria** na hora e so depois grava. Sem isso
 * o item pularia de volta ao lugar antigo durante o instante entre soltar e o
 * Room reemitir - um defeito visivel num Moto E5.
 */
class CategoriasViewModel(
    private val catalogoRepositorio: CatalogoRepositorio,
) : ViewModel() {

    data class EstadoDasCategorias(
        val categorias: List<Categoria> = emptyList(),
        val produtosPorCategoria: Map<Long, Int> = emptyMap(),
        val aprendidos: Int = 0,
        val reordenacaoManual: Boolean = false,
        val carregando: Boolean = true,
    ) {
        val totalDeProdutos: Int get() = produtosPorCategoria.values.sum()
    }

    /** Ordem provisoria enquanto a pessoa arrasta; `null` significa "use a do banco". */
    private val ordemLocal = MutableStateFlow<List<Long>?>(null)
    private val extras = MutableStateFlow(Triple(emptyMap<Long, Int>(), 0, false))

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val estado: StateFlow<EstadoDasCategorias> = combine(
        catalogoRepositorio.categorias,
        ordemLocal,
        extras,
    ) { categorias, ordem, dados ->
        val ordenadas = if (ordem == null) {
            categorias
        } else {
            val porId = categorias.associateBy { it.id }
            ordem.mapNotNull(porId::get) + categorias.filter { it.id !in ordem }
        }
        EstadoDasCategorias(
            categorias = ordenadas,
            produtosPorCategoria = dados.first,
            aprendidos = dados.second,
            reordenacaoManual = dados.third,
            carregando = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDasCategorias())

    init {
        recarregarContagens()
    }

    private fun recarregarContagens() {
        viewModelScope.launch {
            extras.value = Triple(
                catalogoRepositorio.contagemPorCategoria(),
                catalogoRepositorio.memoriaDeCategorias().size,
                extras.value.third,
            )
        }
    }

    fun alternarReordenacaoManual(ativa: Boolean) {
        extras.value = extras.value.copy(third = ativa)
    }

    /**
     * Move uma categoria uma posicao para cima ou para baixo.
     *
     * Existe alem do arrastar porque arrastar e impossivel com TalkBack ligado
     * e dificil com tremor nas maos - as setas sao o caminho acessivel para a
     * mesma operacao.
     */
    fun mover(id: Long, passos: Int) {
        val atual = estado.value.categorias.map { it.id }.toMutableList()
        val de = atual.indexOf(id)
        if (de < 0) return
        val para = (de + passos).coerceIn(0, atual.lastIndex)
        if (de == para) return
        atual.removeAt(de)
        atual.add(para, id)
        ordemLocal.value = atual
        gravarOrdem(atual)
    }

    /** Aplica o resultado de um arrasto concluido. */
    fun reordenar(deIndice: Int, paraIndice: Int) {
        val atual = estado.value.categorias.map { it.id }.toMutableList()
        if (deIndice !in atual.indices || paraIndice !in atual.indices) return
        val id = atual.removeAt(deIndice)
        atual.add(paraIndice, id)
        ordemLocal.value = atual
        gravarOrdem(atual)
    }

    private fun gravarOrdem(ordem: List<Long>) {
        viewModelScope.launch { catalogoRepositorio.reordenarCategorias(ordem) }
    }

    fun restaurarOrdemPadrao() {
        viewModelScope.launch {
            catalogoRepositorio.restaurarOrdemPadrao()
            ordemLocal.value = null
            _mensagem.value = "Ordem do mercado restaurada."
        }
    }

    fun renomear(categoria: Categoria, novoNome: String) {
        val limpo = novoNome.trim()
        if (limpo.isBlank() || limpo == categoria.nome) return
        viewModelScope.launch {
            catalogoRepositorio.renomearCategoria(categoria, limpo)
            _mensagem.value = "Categoria renomeada para “$limpo”."
        }
    }

    fun criar(nome: String) {
        val limpo = nome.trim()
        if (limpo.isBlank()) return
        viewModelScope.launch {
            catalogoRepositorio.criarCategoria(limpo)
            recarregarContagens()
            _mensagem.value = "Categoria “$limpo” criada."
        }
    }

    /**
     * Remove uma categoria criada pela pessoa.
     *
     * As 14 da especificacao nao sao removiveis - sumir com "Hortifruti"
     * deixaria produtos orfaos e quebraria a categorizacao automatica.
     */
    fun remover(categoria: Categoria) {
        if (categoria.origem != OrigemCategoria.USUARIO) {
            _mensagem.value = "As categorias originais não podem ser removidas, só renomeadas."
            return
        }
        viewModelScope.launch {
            catalogoRepositorio.removerCategoriaDoUsuario(categoria.id)
            recarregarContagens()
            _mensagem.value = "Categoria “${categoria.nome}” foi para a lixeira (30 dias)."
        }
    }

    fun esquecerAprendizado() {
        viewModelScope.launch {
            catalogoRepositorio.esquecerCategorias()
            recarregarContagens()
            _mensagem.value = "Correções de categoria esquecidas."
        }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }
}

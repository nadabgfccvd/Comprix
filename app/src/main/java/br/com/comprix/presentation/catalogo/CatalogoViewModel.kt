package br.com.comprix.presentation.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
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
 * @param total quantos itens estao visiveis agora (ja com filtro aplicado).
 */
data class EstadoDoCatalogo(
    val consulta: String = "",
    val setorSelecionado: Int? = null,
    val carregando: Boolean = true,
    val setores: List<SetorDoCatalogo> = emptyList(),
    val grupos: List<GrupoDoCatalogo> = emptyList(),
    val total: Int = 0,
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
    ) { consulta, setor -> consulta to setor }
        .map { (consulta, setor) -> calcular(consulta, setor) }
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
    private fun calcular(consulta: String, setorSelecionado: Int?): EstadoDoCatalogo {
        val catalogo = catalogoRepositorio.dadosDoCatalogoSemente()
            ?: return EstadoDoCatalogo(consulta = consulta, setorSelecionado = setorSelecionado, carregando = false)

        val subvisiveis = if (setorSelecionado == null) {
            catalogo.subcategorias
        } else {
            catalogo.subcategoriasPorSetor[setorSelecionado].orEmpty()
        }
        val subPorId = subvisiveis.associateBy { it.id }

        val termo = consulta.trim()
        val itensVisiveis = if (termo.isBlank()) {
            catalogo.itens.filter { it.subcategoriaId in subPorId }
        } else {
            catalogo.buscar(termo, LIMITE_DA_BUSCA).filter { it.subcategoriaId in subPorId }
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
            total = itensVisiveis.size,
        )
    }

    private companion object {
        /** Teto da busca no acervo: a tela agrupa por subcategoria e o rol segue o resto. */
        const val LIMITE_DA_BUSCA = 120
    }
}

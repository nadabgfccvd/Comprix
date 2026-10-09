package br.com.comprix.presentation.produto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.alergia.VerificadorDeAlergias
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Produto
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ficha de um produto do catalogo local.
 *
 * Carrega uma vez e pronto: a ficha é leitura, não edição (editar quantidade e
 * categoria se faz na lista, onde o item tem contexto).
 *
 * ## Assinatura para a navegacao
 *
 * Construtor: `ProdutoViewModel(produtoId, catalogoRepositorio, precoRepositorio,
 * configuracoesRepositorio)` - o repositorio de configuracoes alimenta os
 * alertas personalizados; a rota em Navegacao.kt precisa passa-lo.
 */
class ProdutoViewModel(
    private val produtoId: Long,
    private val catalogoRepositorio: CatalogoRepositorio,
    private val precoRepositorio: PrecoRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
) : ViewModel() {

    data class EstadoDoProduto(
        val produto: Produto? = null,
        val categoria: Categoria? = null,
        val historico: List<PrecoRepositorio.PontoDeHistorico> = emptyList(),
        val menorPrecoRecente: BigDecimal? = null,
        val carregando: Boolean = true,
        /** Alertas das restricoes do usuario contra esta ficha (VerificadorDeAlergias). */
        val alertasDeRestricao: List<String> = emptyList(),
        /** `true` quando o perfil tem alguma restricao ligada (gluten, alergenos ou customizadas). */
        val perfilAtivo: Boolean = false,
        /** Sinonimos de busca do acervo para o nome do produto ("tambem buscar: ..."). */
        val sinonimos: List<String> = emptyList(),
        /** Estrela do produto (favorito): liga o atalho da folha de Favoritos. */
        val favorito: Boolean = false,
    )

    private val _estado = MutableStateFlow(EstadoDoProduto())

    /**
     * Estado exibido: os dados da ficha combinados com o perfil de restricoes,
     * reativo - mudar o perfil reflete nos alertas sem recarregar a tela.
     */
    val estado: StateFlow<EstadoDoProduto> = combine(
        _estado,
        configuracoesRepositorio.perfil,
    ) { estado, perfil ->
        val produto = estado.produto
        estado.copy(
            alertasDeRestricao = produto?.let { VerificadorDeAlergias.verificar(it, perfil) }
                ?: emptyList(),
            perfilAtivo = perfil.ativo,
            // Sinonimos vem da semente (pura em memoria, sem DAO): recomputados
            // aqui, junto do resto do estado derivado, sem nova fonte de dados.
            sinonimos = produto?.let { catalogoRepositorio.sinonimosDe(it.nome) }
                ?: emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDoProduto())

    init {
        carregar()
    }

    private fun carregar() {
        viewModelScope.launch {
            val produto = catalogoRepositorio.produtoPorId(produtoId)
            val categoria = produto?.let { alvo ->
                catalogoRepositorio.listarCategorias().firstOrNull { it.id == alvo.categoriaId }
            }
            _estado.update {
                it.copy(
                    produto = produto,
                    categoria = categoria,
                    favorito = produto?.favorito ?: false,
                    historico = if (produto != null) {
                        precoRepositorio.historicoDoProduto(produto.id).sortedBy { ponto -> ponto.quando }
                    } else {
                        emptyList()
                    },
                    menorPrecoRecente = produto?.let { alvo -> precoRepositorio.menorPrecoRecente(alvo.id) },
                    carregando = false,
                )
            }
        }
    }

    /** Recarrega depois de anotar um preço novo em outra tela. */
    fun atualizar() = carregar()

    /**
     * Alterna a estrela da ficha (cabecalho). A marca e otimista na tela e
     * o recarregamento traz o estado definitivo do banco - o mesmo campo que
     * a folha de acoes do item e a folha de Favoritos da lista manipulam.
     */
    fun alternarFavorito() {
        val produto = _estado.value.produto ?: return
        val novo = !_estado.value.favorito
        _estado.update { it.copy(favorito = novo) }
        viewModelScope.launch {
            catalogoRepositorio.marcarProdutoFavorito(produto.id, novo)
            atualizar()
        }
    }
}

package br.com.comprix.presentation.nutricional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.nutricional.ComparacaoNutricional
import br.com.comprix.domain.nutricional.ComparadorNutricional
import br.com.comprix.util.Constantes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Seleção dos produtos que entram na comparação nutricional.
 *
 * Só entram produtos da lista que já tiveram a tabela lida pela câmera - o app
 * não inventa valor nutricional nem busca em base externa (é offline).
 */
class NutricionalViewModel(
    private val listaId: Long,
    private val listaRepositorio: ListaRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
) : ViewModel() {

    data class EstadoNutricional(
        val candidatos: List<Produto> = emptyList(),
        val selecionados: Set<Long> = emptySet(),
        val nutrientesEscolhidos: List<Nutriente> = Nutriente.selecaoPadrao(),
        val comparacao: ComparacaoNutricional? = null,
        val modoTecnicoAtivo: Boolean = false,
    ) {
        val nutrientesDisponiveis: List<Nutriente> get() = Nutriente.entries
    }

    private val _selecionados = MutableStateFlow<Set<Long>>(emptySet())

    val estado: StateFlow<EstadoNutricional> = combine(
        listaRepositorio.observarItens(listaId),
        configuracoesRepositorio.configuracoes,
        _selecionados,
    ) { itens, config, selecionados ->
        val candidatos = ComparadorNutricional.comparaveis(itens.map { it.produto }.distinctBy { it.id })
        val efetivos = selecionados
            .ifEmpty { candidatos.take(Constantes.MAXIMO_PRODUTOS_COMPARADOS).map { it.id }.toSet() }
            .take(Constantes.MAXIMO_PRODUTOS_COMPARADOS)
            .toSet()
        val escolhidos = candidatos.filter { it.id in efetivos }

        EstadoNutricional(
            candidatos = candidatos,
            selecionados = efetivos,
            nutrientesEscolhidos = config.nutrientesComparados,
            comparacao = if (escolhidos.size >= 2) {
                ComparadorNutricional.comparar(escolhidos, config.nutrientesComparados)
            } else {
                null
            },
            modoTecnicoAtivo = config.modoTecnico,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoNutricional())

    /** Marca ou desmarca um produto, respeitando o teto de 3 colunas. */
    fun alternarProduto(produtoId: Long) {
        val atuais = estado.value.selecionados
        _selecionados.value = when {
            produtoId in atuais -> atuais - produtoId
            atuais.size >= Constantes.MAXIMO_PRODUTOS_COMPARADOS ->
                atuais.drop(1).toSet() + produtoId

            else -> atuais + produtoId
        }
    }

    /** Liga/desliga um nutriente na tabela - a escolha fica salva nas configurações. */
    fun alternarNutriente(nutriente: Nutriente) {
        val atuais = estado.value.nutrientesEscolhidos
        val novos = if (nutriente in atuais) atuais - nutriente else atuais + nutriente
        viewModelScope.launch {
            configuracoesRepositorio.definirNutrientes(novos.ifEmpty { Nutriente.selecaoPadrao() })
        }
    }
}

package br.com.comprix.presentation.listas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.ResumoDeLista
import br.com.comprix.util.Formatadores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tela inicial: todas as listas do usuário, abertas e finalizadas.
 *
 * O estado vem direto do banco (Flow do Room), então criar, renomear ou
 * duplicar uma lista reflete na hora sem recarregar nada na mão.
 */
class ListasViewModel(
    private val listaRepositorio: ListaRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
) : ViewModel() {

    data class EstadoDasListas(
        /** Favoritas de qualquer estado (abertas e finalizadas): secao fixa no topo. */
        val favoritas: List<ResumoDeLista> = emptyList(),
        val abertas: List<ResumoDeLista> = emptyList(),
        val finalizadas: List<ResumoDeLista> = emptyList(),
        val carregando: Boolean = true,
        val mostrarDica: Boolean = false,
    ) {
        val vazio: Boolean
            get() = !carregando && favoritas.isEmpty() && abertas.isEmpty() && finalizadas.isEmpty()
    }

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val estado: StateFlow<EstadoDasListas> = combine(
        listaRepositorio.resumos,
        configuracoesRepositorio.configuracoes.map { it.dicasVistas },
    ) { resumos, dicasVistas ->
        // Secao Favoritas e DISJUNTA das outras: a favorita sobe para o topo e
        // nao aparece repetida em abertas/finalizadas.
        EstadoDasListas(
            favoritas = resumos.filter { it.lista.favorita },
            abertas = resumos.filter { !it.lista.finalizada && !it.lista.favorita },
            finalizadas = resumos.filter { it.lista.finalizada && !it.lista.favorita },
            carregando = false,
            mostrarDica = resumos.isNotEmpty() && DICA_LISTAS !in dicasVistas,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDasListas())

    /** Cria uma lista e devolve o id para a tela navegar direto para ela. */
    fun criarLista(nome: String?, aoCriar: (Long) -> Unit) {
        viewModelScope.launch {
            val titulo = nome?.trim()?.takeIf { it.isNotBlank() } ?: Formatadores.nomeSugeridoDeLista()
            val id = listaRepositorio.criarLista(titulo)
            aoCriar(id)
        }
    }

    fun renomear(resumo: ResumoDeLista, novoNome: String) {
        val limpo = novoNome.trim()
        if (limpo.isBlank()) return
        viewModelScope.launch { listaRepositorio.renomearLista(resumo.lista, limpo) }
    }

    /**
     * Duplicar copia os itens, **não** os preços: preço é de uma ida ao
     * mercado específica e envelhece rápido.
     */
    fun duplicar(resumo: ResumoDeLista) {
        viewModelScope.launch {
            val novoId = listaRepositorio.duplicarLista(resumo.lista.id)
            _mensagem.value = if (novoId != null) {
                "Lista duplicada sem os preços antigos."
            } else {
                "Não foi possível duplicar a lista."
            }
        }
    }

    fun remover(resumo: ResumoDeLista) {
        viewModelScope.launch {
            listaRepositorio.removerLista(resumo.lista.id)
            _mensagem.value = "Lista \"${resumo.lista.nome}\" foi para a lixeira (30 dias)."
        }
    }

    /**
     * Alterna a estrela de UMA lista (botao do cartao, fora do modo selecao).
     * O repositorio grava a flag e a secao "Favoritas" da tela se move sozinha,
     * porque o estado observa o banco.
     */
    fun alternarFavorita(resumo: ResumoDeLista) {
        viewModelScope.launch {
            val favorita = !resumo.lista.favorita
            listaRepositorio.favoritarLista(resumo.lista.id, favorita)
            _mensagem.value = if (favorita) {
                "Lista \"${resumo.lista.nome}\" marcada como favorita."
            } else {
                "Lista \"${resumo.lista.nome}\" saiu dos favoritos."
            }
        }
    }

    fun reabrir(resumo: ResumoDeLista) {
        viewModelScope.launch {
            listaRepositorio.reabrirLista(resumo.lista.id)
            _mensagem.value = "Lista reaberta para edição."
        }
    }

    // --- selecao em massa -------------------------------------------------------------

    private val _modoSelecao = MutableStateFlow(false)
    /** `true` enquanto o modo de selecao em massa esta ativo (toque longo num cartao). */
    val modoSelecao: StateFlow<Boolean> = _modoSelecao.asStateFlow()

    private val _idsSelecionados = MutableStateFlow<Set<Long>>(emptySet())
    /** Ids das listas marcadas no modo selecao (a ordem nao importa). */
    val idsSelecionados: StateFlow<Set<Long>> = _idsSelecionados.asStateFlow()

    /**
     * Liga/desliga uma lista na selecao. Desmarcar a ULTIMA sai do modo -
     * barra de acoes com selecao vazia nao tem utilidade nenhuma.
     */
    fun alternarSelecao(id: Long) {
        val agora = _idsSelecionados.value.toMutableSet()
        if (!agora.remove(id)) {
            agora.add(id)
            _modoSelecao.value = true
        } else if (agora.isEmpty()) {
            limparSelecao()
            return
        }
        _idsSelecionados.value = agora
    }

    /** Entra no modo selecao com a lista tocada (toque longo) ja marcada. */
    fun entrarEmSelecao(id: Long) {
        _modoSelecao.value = true
        _idsSelecionados.value = setOf(id)
    }

    /** Sai do modo e limpa a marcacao (X da barra ou acao concluida). */
    fun limparSelecao() {
        _modoSelecao.value = false
        _idsSelecionados.value = emptySet()
    }

    /**
     * Marca todas as listas VISIVEIS de uma vez - a tela passa ja filtradas
     * pela busca atual, entao "todas" respeita o que esta na frente do usuario.
     */
    fun selecionarTodas(visiveis: List<Long>) {
        if (visiveis.isEmpty()) return
        _modoSelecao.value = true
        _idsSelecionados.value = visiveis.toSet()
    }

    /**
     * Exclui todas as selecionadas de uma vez. Cada uma passa pela lixeira
     * (30 dias recuperaveis) - nada e apagado na hora.
     */
    fun excluirSelecionadas() {
        val ids = _idsSelecionados.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { listaRepositorio.removerLista(it) }
            limparSelecao()
            _mensagem.value = if (ids.size == 1) {
                "1 lista foi para a lixeira (30 dias)."
            } else {
                "${ids.size} listas foram para a lixeira (30 dias)."
            }
        }
    }

    /**
     * Favorita (ou desfavorita) as selecionadas de uma vez. O alvo sai da
     * maioria: se mais da metade ainda NAO e favorita, a acao favorita; caso
     * contrario, desfaz - o mesmo toque sempre avanca o estado do grupo.
     */
    fun alternarFavoritas() {
        val ids = _idsSelecionados.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val selecionadas = (estado.value.abertas + estado.value.finalizadas)
                .filter { it.lista.id in ids }
            val favoritar = selecionadas.count { !it.lista.favorita } > selecionadas.size / 2
            ids.forEach { listaRepositorio.favoritarLista(it, favoritar) }
            val quantidade = selecionadas.size
            limparSelecao()
            _mensagem.value = when {
                favoritar && quantidade == 1 -> "Lista marcada como favorita."
                favoritar -> "$quantidade listas marcadas como favoritas."
                quantidade == 1 -> "Lista tirada dos favoritos."
                else -> "$quantidade listas tiradas dos favoritos."
            }
        }
    }

    /**
     * Finaliza as listas ABERTAS selecionadas. As que ja estavam finalizadas
     * sao puladas e entram na contagem da mensagem - nunca recebem um segundo
     * registro de "compra concluida".
     */
    fun finalizarSelecionadas() {
        val ids = _idsSelecionados.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var finalizadas = 0
            var jaEstavam = 0
            ids.forEach { id ->
                val lista = listaRepositorio.lista(id) ?: return@forEach
                if (lista.finalizada) {
                    jaEstavam += 1
                } else {
                    listaRepositorio.marcarFinalizada(id)
                    finalizadas += 1
                }
            }
            limparSelecao()
            _mensagem.value = when {
                finalizadas == 1 && jaEstavam == 0 -> "1 lista finalizada."
                finalizadas == 1 -> "1 lista finalizada, $jaEstavam já estavam."
                jaEstavam == 0 -> "$finalizadas listas finalizadas."
                else -> "$finalizadas finalizadas, $jaEstavam já estavam."
            }
        }
    }

    fun dispensarDica() {
        viewModelScope.launch { configuracoesRepositorio.marcarDicaVista(DICA_LISTAS) }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }

    private companion object {
        const val DICA_LISTAS = "dica_listas"
    }
}

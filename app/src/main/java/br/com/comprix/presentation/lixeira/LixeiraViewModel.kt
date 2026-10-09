package br.com.comprix.presentation.lixeira

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.data.repositorio.LixeiraRepositorio
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tela da **Lixeira**: o que o usuario excluiu no app fica aqui por
 * [LixeiraRepositorio.PRAZO_EM_DIAS] dias antes da exclusao permanente.
 *
 * ## O que a tela precisa
 * - [registros]: tudo que esta na lixeira (Flow do Room, do mais recente para
 *   o mais antigo);
 * - [contagens]: quantos registros existem por categoria (alimenta os chips);
 * - [tipoSelecionado] + [registrosVisiveis]: o filtro da tela, `null` mostra
 *   todas as categorias;
 * - [expiraEm]: a data da exclusao permanente de um registro, para o cartao.
 *
 * As acoes destrutivas ([esvaziarCategoria], [esvaziarTudo], [removerRegistro])
 * apagam PARA SEMPRE - a dupla confirmacao (e a simples, no individual) mora
 * na tela, que conhece as contagens para formular o aviso.
 */
class LixeiraViewModel(
    private val lixeiraRepositorio: LixeiraRepositorio,
) : ViewModel() {

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private val _tipoSelecionado = MutableStateFlow<LixeiraRepositorio.TipoDeLixeira?>(null)

    /** Categoria escolhida nos chips; `null` = Todas (padrao da tela). */
    val tipoSelecionado: StateFlow<LixeiraRepositorio.TipoDeLixeira?> = _tipoSelecionado.asStateFlow()

    /** Todos os registros vivos, do mais recente para o mais antigo. */
    val registros: StateFlow<List<RegistroDeLixeiraEntity>> = lixeiraRepositorio.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Contagem por categoria, derivada dos registros (chips da linha de filtro). */
    val contagens: StateFlow<Map<LixeiraRepositorio.TipoDeLixeira, Int>> = registros
        .map { lista ->
            LixeiraRepositorio.TipoDeLixeira.entries.associateWith { tipo ->
                lista.count { it.tipo == tipo.name }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Lista visivel: os registros do filtro atual (`null` = todas as categorias). */
    val registrosVisiveis: StateFlow<List<RegistroDeLixeiraEntity>> =
        combine(registros, _tipoSelecionado) { lista, tipo ->
            if (tipo == null) lista else lista.filter { it.tipo == tipo.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // A purga roda no arranque do processo (ServiceLocator) e, pela mesma
        // promessa do repositorio, ao abrir a tela: o que passou dos 30 dias
        // sai da lista na hora. Idempotente e barato (so toca na `lixeira`).
        viewModelScope.launch { runCatching { lixeiraRepositorio.purgarExpirados() } }
    }

    fun selecionarTipo(tipo: LixeiraRepositorio.TipoDeLixeira?) {
        _tipoSelecionado.value = tipo
    }

    /** Devolve o registro aonde ele estava (o repositorio devolve a mensagem pronta). */
    fun restaurar(registroId: Long) {
        viewModelScope.launch {
            _mensagem.value = lixeiraRepositorio.restaurar(registroId)
        }
    }

    /** "Excluir agora" de um registro so, com confirmacao simples na tela. */
    fun removerRegistro(registroId: Long) {
        viewModelScope.launch {
            _mensagem.value = if (lixeiraRepositorio.removerRegistro(registroId)) {
                "Registro apagado definitivamente."
            } else {
                "Registro não encontrado na lixeira."
            }
        }
    }

    /** Apaga UMA categoria inteira - a tela confirma DUAS vezes antes de chamar. */
    fun esvaziarCategoria(tipo: LixeiraRepositorio.TipoDeLixeira) {
        viewModelScope.launch {
            when (val quantidade = lixeiraRepositorio.esvaziarPorTipo(tipo)) {
                0 -> _mensagem.value = "Não havia registros nesta categoria."
                1 -> _mensagem.value = "1 registro apagado definitivamente."
                else -> _mensagem.value = "$quantidade registros apagados definitivamente."
            }
        }
    }

    /** Apaga TUDO - a tela confirma DUAS vezes antes de chamar. */
    fun esvaziarTudo() {
        viewModelScope.launch {
            when (val quantidade = lixeiraRepositorio.esvaziarTudo()) {
                0 -> _mensagem.value = "A lixeira já estava vazia."
                1 -> _mensagem.value = "1 registro apagado definitivamente."
                else -> _mensagem.value = "$quantidade registros apagados definitivamente."
            }
        }
    }

    /** Data da exclusao permanente de um registro, para a linha do cartao. */
    fun expiraEm(registro: RegistroDeLixeiraEntity): LocalDateTime =
        lixeiraRepositorio.expiraEm(registro)

    fun mensagemExibida() {
        _mensagem.value = null
    }
}

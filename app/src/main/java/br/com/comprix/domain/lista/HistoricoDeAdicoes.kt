package br.com.comprix.domain.lista

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Historico de desfazer/refazer das adicoes feitas na caixa de adicao rapida
 * da tela de lista.
 *
 * ## Por que um `object` (singleton de processo) e nao estado do ViewModel
 * A navegacao entre abas do app troca de destino com popUpTo(Rotas.LISTAS),
 * o que desempilha a entrada "lista/{id}" da pilha do NavHost. Quando a
 * entrada sai da pilha, o ViewModelStore descarta o ListaViewModel (o
 * onCleared roda) e, ao voltar para a aba, um ViewModel NOVO nasce. Se as
 * pilhas de desfazer/refazer vivessem dentro do ViewModel, elas morriam a
 * cada ida e volta entre abas - o usuario desfazia, trocava de aba e o
 * historico voltava vazio. Sendo um objeto de processo, o historico
 * sobrevive a navegacao, a recriacao do ViewModel e a recriacao da
 * Activity; so morre quando o processo do app morre.
 *
 * ## Uma pilha por lista
 * As pilhas sao chaveadas por [listaId]: desfazer numa lista A nunca remove
 * itens da lista B, e o refazer de uma nao contamina a outra. Os fluxos sao
 * criados on-demand (nunca ficam nulos) e o [limpar] zera as duas pilhas de
 * uma lista quando ela for excluida.
 *
 * Toda mutacao parte da thread principal (eventos de tela e corrotinas do
 * viewModelScope), igual ao resto do app; o `synchronized` e apenas uma
 * garantia barata para o caso improvavel de concorrencia.
 */
object HistoricoDeAdicoes {

    /** Uma adicao ja feita: o texto original e os itens que ele criou. */
    data class AdicaoHistorica(val texto: String, val idsDosItens: List<Long>)

    private val desfazerPorLista = mutableMapOf<Long, MutableStateFlow<List<AdicaoHistorica>>>()
    private val refazerPorLista = mutableMapOf<Long, MutableStateFlow<List<AdicaoHistorica>>>()

    private val trava = Any()

    /** Pilha de desfazer da lista (o topo e a adicao mais recente). */
    fun pilhaDeDesfazer(listaId: Long): StateFlow<List<AdicaoHistorica>> =
        desfazerDe(listaId).asStateFlow()

    /** Pilha de refazer da lista (o topo e a adicao desfeita mais recente). */
    fun pilhaDeRefazer(listaId: Long): StateFlow<List<AdicaoHistorica>> =
        refazerDe(listaId).asStateFlow()

    /**
     * Registra uma adicao nova: empurra no desfazer e limpa o refazer, porque
     * uma adicao nova invalida o que estava pendente de refazer (semantica
     * classica de undo/redo, mantida do comportamento anterior).
     */
    fun registrar(listaId: Long, adicao: AdicaoHistorica) {
        synchronized(trava) {
            desfazerDe(listaId).value = desfazerDe(listaId).value + adicao
            refazerDe(listaId).value = emptyList()
        }
    }

    /**
     * Empurra no desfazer SEM limpar o refazer. Usado pelo refazer do
     * ViewModel: reexecutar o texto cria itens com ids novos, e a entrada
     * refeita volta para o desfazer carregando esses ids novos - sem apagar o
     * restante da pilha de refazer, para o refazer em cadeia continuar
     * possivel (desfez duas, refaz uma, refaz a outra).
     */
    fun empilharNoDesfazer(listaId: Long, adicao: AdicaoHistorica) {
        synchronized(trava) {
            desfazerDe(listaId).value = desfazerDe(listaId).value + adicao
        }
    }

    /**
     * Desfaz o topo do desfazer: devolve a adicao (para o chamador remover os
     * itens dela) e a empurra no refazer. null = nao ha nada a desfazer.
     */
    fun desfazer(listaId: Long): AdicaoHistorica? {
        synchronized(trava) {
            val pilha = desfazerDe(listaId)
            val topo = pilha.value.lastOrNull() ?: return null
            pilha.value = pilha.value.dropLast(1)
            refazerDe(listaId).value = refazerDe(listaId).value + topo
            return topo
        }
    }

    /**
     * Tira o topo do refazer e o devolve (para o chamador reexecutar o texto
     * e reempilhar no desfazer com os ids novos, via [empilharNoDesfazer]).
     * null = nao ha nada a refazer.
     */
    fun refazer(listaId: Long): AdicaoHistorica? {
        synchronized(trava) {
            val pilha = refazerDe(listaId)
            val topo = pilha.value.lastOrNull() ?: return null
            pilha.value = pilha.value.dropLast(1)
            return topo
        }
    }

    /**
     * Zera as duas pilhas de uma lista - pensado para a exclusao da lista,
     * que nao deve deixar historico orfao atras.
     */
    fun limpar(listaId: Long) {
        synchronized(trava) {
            desfazerPorLista.remove(listaId)
            refazerPorLista.remove(listaId)
        }
    }

    /** Fluxo da lista, criado na primeira chamada (on-demand, nunca nulo). */
    private fun desfazerDe(listaId: Long): MutableStateFlow<List<AdicaoHistorica>> =
        synchronized(trava) {
            desfazerPorLista.getOrPut(listaId) { MutableStateFlow<List<AdicaoHistorica>>(emptyList()) }
        }

    /** Fluxo da lista, criado na primeira chamada (on-demand, nunca nulo). */
    private fun refazerDe(listaId: Long): MutableStateFlow<List<AdicaoHistorica>> =
        synchronized(trava) {
            refazerPorLista.getOrPut(listaId) { MutableStateFlow<List<AdicaoHistorica>>(emptyList()) }
        }
}

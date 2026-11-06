package br.com.comprix.presentation.lixeira

import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.util.TextoUtil
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Logica pura da **busca** e do **aviso de prazo** da Lixeira - isolada da tela
 * e do repositorio para ser testada direto na JVM (mesma receita do
 * [br.com.comprix.util.Formatadores], testada em `LixeiraFormatoTest`).
 *
 * ## Busca
 * Cada palavra digitada (normalizada, sem acento e sem maiuscula) precisa
 * aparecer no titulo ou no detalhe do registro - o mesmo criterio amigavel do
 * "melao acha Melao" do catalogo. Consulta em branco casa com tudo (o
 * campo some da tela vazia, mas a regra fica explicita e testada).
 *
 * ## Prazo
 * A Lixeira apaga tudo 30 dias depois da exclusao. [diasRestantes] conta
 * dias de calendario entre "hoje" e a data da exclusao permanente;
 * [prazo] devolve o selo urgente so para quem esta a uma semana ou menos
 * do fim - depois disso o cartao ja mostra a data completa, e um selo
 * "some em 23 dias" em tudo seria ruido, nao informacao.
 */
object FiltroDaLixeira {

    /** Janela do selo de prazo: cartoes a esta distancia ou menos ganham o aviso. */
    const val DIAS_URGENTE = 7L

    /**
     * Termos alternativos da consulta (sinonimos do acervo), prontos para
     * [corresponde]. Computa UMA vez por emissao de busca — a funcao de
     * sinonimos e pura e barata, mas nao deve rodar por registro; consulta em
     * branco ou sem candidatos devolve vazio. Mesma receita dos filtros do
     * Historico e de "Seus produtos": expansao pela consulta INTEIRA, nunca
     * por palavra solta, e `candidatos = null` = comportamento de antes.
     */
    fun alternativasDe(
        consulta: String,
        candidatos: ((String) -> List<String>)?,
    ): List<String> {
        if (candidatos == null || consulta.isBlank()) return emptyList()
        return candidatos(consulta).filter { it.isNotBlank() }
    }

    /**
     * Verdadeiro quando [registro] casa com [consulta] OU com qualquer termo
     * alternativo de [alternativas] (sinonimos precomputados por
     * [alternativasDe] — "mexerica" encontra a lista "Tangerina da feira" que
     * a pessoa apagou, e vice-versa, porque a resolucao do acervo e
     * bidirecional). Todas as palavras do termo precisam ser encontradas
     * (busca por pedaco de palavra, sem acento e sem maiuscula) no titulo ou
     * no detalhe. O casamento direto e o [TextoUtil.correspondeBusca],
     * compartilhado com o Historico.
     */
    fun corresponde(
        registro: RegistroDeLixeiraEntity,
        consulta: String,
        alternativas: List<String> = emptyList(),
    ): Boolean {
        val alvo = registro.titulo + " " + registro.detalhe.orEmpty()
        if (TextoUtil.correspondeBusca(alvo, consulta)) return true
        return alternativas.any { TextoUtil.correspondeBusca(alvo, it) }
    }

    /**
     * Dias de calendario entre [agora] e [expiraEm]. Datas - nao horas: um
     * registro que expira amanha as 09:00 e visto hoje as 23:00 como "1 dia",
     * que e o que a pessoa espera ler.
     */
    fun diasRestantes(expiraEm: LocalDateTime, agora: LocalDateTime): Long =
        ChronoUnit.DAYS.between(agora.toLocalDate(), expiraEm.toLocalDate())

    /**
     * Selo urgente do cartao; `null` quando falta mais de [DIAS_URGENTE] dias
     * (o cartao ja mostra a data completa nesse caso).
     *
     * - hoje (ou atrasado, em corrida com a purga) - ALERTA, "some hoje";
     * - amanha ou 3 dias - ALERTA, ainda da para se organizar;
     * - 4 a 7 dias - OURO, o aviso ambar de "esta acabando".
     */
    fun prazo(diasRestantes: Long): SeloDoPrazo? = when {
        diasRestantes <= 0 -> SeloDoPrazo("some hoje", TomDoSelo.ALERTA)
        diasRestantes == 1L -> SeloDoPrazo("some amanhã", TomDoSelo.ALERTA)
        diasRestantes <= 3 -> SeloDoPrazo("some em $diasRestantes dias", TomDoSelo.ALERTA)
        diasRestantes <= DIAS_URGENTE -> SeloDoPrazo("some em $diasRestantes dias", TomDoSelo.OURO)
        else -> null
    }
}

/** Rotulo e tom do selo de prazo exibido no cartao da Lixeira. */
data class SeloDoPrazo(val rotulo: String, val tom: TomDoSelo)

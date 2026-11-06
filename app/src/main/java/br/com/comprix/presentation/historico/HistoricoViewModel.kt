package br.com.comprix.presentation.historico

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.HistoricoRepositorio
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.historico.FiltroDeHistorico
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.ResumoHistorico
import br.com.comprix.util.Constantes
import br.com.comprix.util.ExportadorCsv
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Histórico de compras (jornada 5): quanto se gastou, onde e em quê.
 *
 * O período é escolhido pelo usuário e recalcula tudo - total, ticket médio,
 * gráfico mensal e divisão por categoria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoricoViewModel(
    private val historicoRepositorio: HistoricoRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
    private val catalogoRepositorio: CatalogoRepositorio,
) : ViewModel() {

    data class EstadoDoHistorico(
        val periodo: AnalisadorDeHistorico.Periodo = AnalisadorDeHistorico.Periodo.SEIS_MESES,
        val resumo: ResumoHistorico = ResumoHistorico(),
        val compras: List<CompraFinalizada> = emptyList(),
        val variacaoMensal: BigDecimal? = null,
        val carregando: Boolean = true,
        val mostrarDica: Boolean = false,
        /** Economia somada das compras do mes corrente (base do card "Meta do mes"). */
        val economiaDoMes: BigDecimal = BigDecimal.ZERO,
        /** Meta de economia mensal em reais; `null` = usuario ainda nao definiu. */
        val metaEconomia: BigDecimal? = null,
    ) {
        val vazio: Boolean get() = !carregando && compras.isEmpty()
    }

    private val _periodo = MutableStateFlow(AnalisadorDeHistorico.Periodo.SEIS_MESES)
    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private val _consulta = MutableStateFlow("")

    /** Consulta digitada na busca de compras ("" = sem busca). */
    val consulta: StateFlow<String> = _consulta.asStateFlow()

    private val _lojaSelecionada = MutableStateFlow<String?>(null)

    /**
     * Loja selecionada na linha de pastilhas (`null` = "Todas"). O valor e o
     * rotulo do cartao - [FiltroDeHistorico] explica por que rotulo, e nao id.
     */
    val lojaSelecionada: StateFlow<String?> = _lojaSelecionada.asStateFlow()

    val estado: StateFlow<EstadoDoHistorico> = combine(
        _periodo.flatMapLatest { periodo -> historicoRepositorio.resumo(periodo) },
        historicoRepositorio.compras,
        _periodo,
        configuracoesRepositorio.configuracoes,
    ) { resumo, compras, periodo, config ->
        EstadoDoHistorico(
            periodo = periodo,
            resumo = resumo,
            compras = compras,
            variacaoMensal = AnalisadorDeHistorico.variacaoMensal(compras),
            carregando = false,
            mostrarDica = compras.isNotEmpty() && Constantes.DICA_HISTORICO !in config.dicasVistas,
            economiaDoMes = AnalisadorDeHistorico.economiaDoMes(compras),
            metaEconomia = config.metaEconomiaCentavos
                ?.let { BigDecimal(it).movePointLeft(Constantes.ESCALA_MOEDA) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDoHistorico())

    /**
     * Compras visiveis na secao "Compras realizadas": a lista inteira
     * atravessando o filtro de loja e a busca amigavel - a intersecao dos
     * dois e calculada por [FiltroDeHistorico.comprasVisiveis] ("MERCADO"
     * acha "Mercado do Bairro"). Declarado DEPOIS de [estado] - o initializer
     * le a propriedade e Kotlin constroi na ordem. As analiticas, os numeros
     * do cabecalho e a variacao mensal seguem calculados sobre TODAS as
     * compras: busca e loja afinam a lista, nunca reescrevem a estatistica.
     *
     * A busca tambem entende sinonimos do acervo de fabrica: os candidatos
     * chegam como lambda (a funcao e pura e em memoria, nao toca no Room) e
     * "mexerica" encontra a lista "Tangerina para o suco" - e "tangerina"
     * encontra "Mexerica", porque a resolucao e bidirecional. As regras da
     * expansao (consulta inteira, nunca palavra solta) estao no filtro.
     */
    val comprasVisiveis: StateFlow<List<CompraFinalizada>> =
        combine(estado, _consulta, _lojaSelecionada) { estadoAtual, consulta, loja ->
            FiltroDeHistorico.comprasVisiveis(
                estadoAtual.compras,
                consulta,
                loja,
                candidatos = catalogoRepositorio::sinonimosDe,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Pastilhas da linha de lojas: rotulos distintos das compras do periodo
     * (nao das compras filtradas) com quantas compras tem cada um - derivado
     * de [estado] para acompanhar a troca de periodo sem estado duplicado.
     * Declarado DEPOIS de [estado], pela mesma ordem de construcao.
     */
    val lojasDisponiveis: StateFlow<List<FiltroDeHistorico.LojaDoHistorico>> =
        estado
            .map { FiltroDeHistorico.lojas(it.compras) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun definirPeriodo(periodo: AnalisadorDeHistorico.Periodo) {
        _periodo.value = periodo
    }

    /** Define a consulta da busca de compras; "" desliga o filtro. */
    fun definirConsulta(consulta: String) {
        _consulta.value = consulta
    }

    /**
     * Define a loja selecionada; `null` = "Todas". O toque na pastilha ativa
     * recolhe o filtro, como nos setores do catalogo e nas categorias da
     * Lixeira - a decisao do toggle fica na tela.
     */
    fun definirLoja(loja: String?) {
        _lojaSelecionada.value = loja
    }

    /** Limpa busca e loja de uma vez - o caminho de saida do "Nada encontrado". */
    fun limparFiltros() {
        _consulta.value = ""
        _lojaSelecionada.value = null
    }

    fun remover(compra: CompraFinalizada) {
        viewModelScope.launch {
            historicoRepositorio.remover(compra.id)
            _mensagem.value = "Compra removida do histórico — foi para a lixeira (30 dias)."
        }
    }

    /**
     * Exporta TODAS as compras (sem filtro de periodo) como CSV para um destino
     * escolhido pelo usuario (CreateDocument/SAF). O arquivo e montado em
     * memoria - o historico inteiro cabe facil - e escrito de uma vez.
     */
    fun exportarCsv(contexto: Context, destino: Uri) {
        viewModelScope.launch {
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openOutputStream(destino)?.use { saida ->
                        saida.write(
                            ExportadorCsv.comprasCsv(historicoRepositorio.listarCompras())
                                .toByteArray(Charsets.UTF_8),
                        )
                    } ?: error("destino invalido")
                }
            }
            _mensagem.value = if (resultado.isSuccess) {
                "Arquivo CSV salvo com todas as compras."
            } else {
                "Não consegui exportar o CSV."
            }
        }
    }

    fun dispensarDica() {
        viewModelScope.launch { configuracoesRepositorio.marcarDicaVista(Constantes.DICA_HISTORICO) }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }
}

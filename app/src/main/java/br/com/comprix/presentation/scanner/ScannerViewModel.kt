package br.com.comprix.presentation.scanner

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.camera.LeitorDeImagem
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.modelo.EtapaGuiaVideo
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.LeituraDeRotulo
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.rotulo.ExtratorDeRotulo
import br.com.comprix.domain.rotulo.MescladorDeLeituras
import br.com.comprix.util.Constantes
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Os três modos da câmera (Seção 5 do documento). */
enum class ModoScanner(val rotulo: String) {
    FOTO("Foto"),
    VIDEO("Vídeo guiado"),
    CODIGO("Código de barras"),
}

/**
 * Cérebro da tela de câmera.
 *
 * Mantém o estado dos três modos, acumula as leituras e entrega uma
 * [LeituraDeRotulo] única - mesclada por votação de maioria no caso do vídeo.
 * Nada é gravado no banco sem o usuário confirmar na folha de revisão.
 */
class ScannerViewModel(
    private val listaId: Long,
    private val leitor: LeitorDeImagem,
    private val catalogoRepositorio: CatalogoRepositorio,
    private val listaRepositorio: ListaRepositorio,
    private val precoRepositorio: PrecoRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
) : ViewModel() {

    data class EstadoDoScanner(
        val modo: ModoScanner = ModoScanner.FOTO,
        val gravando: Boolean = false,
        val milissegundosGravados: Long = 0,
        val analisando: Boolean = false,
        val progressoQuadros: Pair<Int, Int>? = null,
        val leitura: LeituraDeRotulo? = null,
        val produtoConhecido: Produto? = null,
        val codigoAoVivo: String? = null,
        val mensagem: String? = null,
        val mostrarDicaVideo: Boolean = false,
        val mostrarDicaFoto: Boolean = false,
        val mostrarDicaCodigo: Boolean = false,
        val estabelecimentos: List<Estabelecimento> = emptyList(),
        val lojaSelecionadaId: Long? = null,
    ) {
        val etapaDoGuia: EtapaGuiaVideo
            get() = EtapaGuiaVideo.paraInstante(milissegundosGravados, Constantes.DURACAO_MAXIMA_VIDEO_MS)

        val progressoDaGravacao: Float
            get() = (milissegundosGravados.toFloat() / Constantes.DURACAO_MAXIMA_VIDEO_MS).coerceIn(0f, 1f)

        val temResultado: Boolean get() = leitura != null || produtoConhecido != null
    }

    private val _estado = MutableStateFlow(EstadoDoScanner())
    val estado: StateFlow<EstadoDoScanner> = _estado.asStateFlow()

    private var ultimaAnaliseAoVivo = 0L

    init {
        viewModelScope.launch {
            val config = configuracoesRepositorio.carregar()
            _estado.update {
                it.copy(
                    mostrarDicaVideo = Constantes.DICA_SCANNER_VIDEO !in config.dicasVistas,
                    mostrarDicaFoto = Constantes.DICA_SCANNER_FOTO !in config.dicasVistas,
                    mostrarDicaCodigo = Constantes.DICA_SCANNER_CODIGO !in config.dicasVistas,
                )
            }
        }
        // Lojas para o seletor da folha de revisao: o preco escaneado tem que
        // cair numa coluna, e a escolha fica na tela (nao "na primeira loja").
        viewModelScope.launch {
            precoRepositorio.estabelecimentos.collect { lojas ->
                _estado.update { atual ->
                    atual.copy(
                        estabelecimentos = lojas,
                        lojaSelecionadaId = atual.lojaSelecionadaId
                            ?.takeIf { id -> lojas.any { loja -> loja.id == id } }
                            ?: lojas.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun definirModo(modo: ModoScanner) {
        _estado.update { it.copy(modo = modo, leitura = null, produtoConhecido = null, codigoAoVivo = null) }
    }

    /** Loja em que o preco da revisao sera registrado. */
    fun selecionarLoja(id: Long) {
        _estado.update { it.copy(lojaSelecionadaId = id) }
    }

    /** Cria a loja padrao quando o aparelho ainda nao tem nenhuma cadastrada. */
    fun criarLojaPadrao() {
        viewModelScope.launch {
            val loja = precoRepositorio.garantirEstabelecimento(DadosIniciais.ESTABELECIMENTO_PADRAO)
            selecionarLoja(loja.id)
        }
    }

    // --- código de barras ao vivo ---------------------------------------------------------

    /**
     * Analisa um quadro da pré-visualização.
     *
     * Roda no máximo a cada 350 ms: num Snapdragon 425, analisar todo quadro
     * aquece o aparelho e engasga a pré-visualização sem ler nada a mais.
     */
    fun analisarQuadroAoVivo(imagem: ImageProxy) {
        val agora = System.currentTimeMillis()
        val estadoAtual = _estado.value
        if (estadoAtual.analisando || agora - ultimaAnaliseAoVivo < Constantes.INTERVALO_MINIMO_ANALISE_AO_VIVO_MS) {
            imagem.close()
            return
        }
        ultimaAnaliseAoVivo = agora

        viewModelScope.launch {
            try {
                val lerCodigo = estadoAtual.modo != ModoScanner.VIDEO
                val quadro = leitor.lerQuadro(imagem, lerCodigo = lerCodigo, lerTexto = false)
                val codigo = quadro.codigoBarras
                if (codigo != null && codigo != estadoAtual.codigoAoVivo) {
                    _estado.update { it.copy(codigoAoVivo = codigo) }
                    if (estadoAtual.modo == ModoScanner.CODIGO) {
                        resolverCodigo(codigo, quadro.formatoCodigoBarras)
                    }
                }
            } finally {
                imagem.close()
            }
        }
    }

    private suspend fun resolverCodigo(codigo: String, formato: String?) {
        val conhecido = catalogoRepositorio.produtoPorCodigoBarras(codigo)
        _estado.update {
            it.copy(
                produtoConhecido = conhecido,
                leitura = LeituraDeRotulo(codigoBarras = codigo, formatoCodigoBarras = formato),
                mensagem = if (conhecido != null) {
                    "Já conhecíamos este produto: ${conhecido.nome}."
                } else {
                    "Código lido. Complete o nome e o preço."
                },
            )
        }
    }

    // --- foto -------------------------------------------------------------------------------

    fun analisarFoto(arquivo: File, contexto: Context) {
        viewModelScope.launch {
            _estado.update { it.copy(analisando = true) }
            val quadro = withContext(Dispatchers.Default) { leitor.lerArquivo(arquivo, contexto) }
            val leitura = ExtratorDeRotulo.extrair(
                linhas = quadro.linhas,
                codigoBarras = quadro.codigoBarras,
                formatoCodigoBarras = quadro.formatoCodigoBarras,
                quadros = 1,
            )
            val conhecido = quadro.codigoBarras?.let { catalogoRepositorio.produtoPorCodigoBarras(it) }
            _estado.update {
                it.copy(
                    analisando = false,
                    leitura = leitura,
                    produtoConhecido = conhecido,
                    mensagem = if (leitura.vazia) {
                        "Não consegui ler o rótulo. Tente com mais luz, aproxime 10-15 cm ou use o vídeo guiado."
                    } else {
                        null
                    },
                )
            }
            runCatching { arquivo.delete() }
        }
    }

    // --- vídeo guiado -------------------------------------------------------------------------

    fun marcarGravando(gravando: Boolean) {
        _estado.update { it.copy(gravando = gravando, milissegundosGravados = if (gravando) 0 else it.milissegundosGravados) }
    }

    fun atualizarTempoGravado(milissegundos: Long) {
        _estado.update { it.copy(milissegundosGravados = milissegundos) }
    }

    /**
     * Analisa o vídeo gravado: quadros-chave, uma leitura por quadro e
     * **votação de maioria** entre elas (Seção 5.3).
     */
    fun analisarVideo(arquivo: File) {
        viewModelScope.launch {
            _estado.update { it.copy(analisando = true, gravando = false, progressoQuadros = 0 to 1) }
            val leituras = withContext(Dispatchers.Default) {
                leitor.lerVideo(arquivo) { lidos, total ->
                    _estado.update { it.copy(progressoQuadros = lidos to total) }
                }
            }
            val mesclada = MescladorDeLeituras.mesclar(leituras)
            val conhecido = mesclada.codigoBarras?.let { catalogoRepositorio.produtoPorCodigoBarras(it) }
            _estado.update {
                it.copy(
                    analisando = false,
                    progressoQuadros = null,
                    leitura = mesclada,
                    produtoConhecido = conhecido,
                    mensagem = when {
                        leituras.isEmpty() ->
                            "O vídeo não trouxe nada legível. Grave de novo com mais luz " +
                                "e gire devagar: frente → preço → tabela → código."
                        mesclada.vazia -> "Li o vídeo, mas não reconheci os campos. Preencha à mão."
                        else -> "${leituras.size} quadros analisados."
                    },
                )
            }
            runCatching { arquivo.delete() }
        }
    }

    // --- gravar o resultado ---------------------------------------------------------------------

    /**
     * Confirma a leitura e grava: cria (ou reaproveita) o produto, adiciona o
     * item na lista e, se houver preço, anota na loja escolhida.
     */
    fun confirmar(
        nome: String,
        preco: BigDecimal?,
        estabelecimentoId: Long?,
        aoConcluir: () -> Unit,
    ) {
        val leitura = _estado.value.leitura
        viewModelScope.launch {
            val nomeFinal = nome.trim().ifBlank { leitura?.nome?.trim().orEmpty() }
            if (nomeFinal.isBlank()) {
                _estado.update { it.copy(mensagem = "Dê um nome ao produto para salvar.") }
                return@launch
            }

            val categoriaId = catalogoRepositorio.sugerirCategoria(nomeFinal).categoria.id
            val base = catalogoRepositorio.encontrarOuCriar(
                nome = nomeFinal,
                categoriaId = categoriaId,
                codigoBarras = leitura?.codigoBarras,
            )
            val enriquecido = base.copy(
                codigoBarras = leitura?.codigoBarras ?: base.codigoBarras,
                unidadePadrao = leitura?.unidade ?: base.unidadePadrao,
                infoNutricional = leitura?.infoNutricional ?: base.infoNutricional,
                selosAltoEm = leitura?.selos?.takeIf { it.isNotEmpty() } ?: base.selosAltoEm,
                ingredientes = leitura?.ingredientes ?: base.ingredientes,
                gluten = leitura?.gluten ?: base.gluten,
                alergenos = leitura?.alergenos?.takeIf { it.isNotEmpty() } ?: base.alergenos,
                dataValidade = leitura?.dataValidade ?: base.dataValidade,
                dataFabricacao = leitura?.dataFabricacao ?: base.dataFabricacao,
            )
            val produto = catalogoRepositorio.salvarProduto(enriquecido)

            val itemId = listaRepositorio.adicionarItem(
                ItemDaLista(
                    listaId = listaId,
                    produtoId = produto.id,
                    quantidade = BigDecimal.ONE,
                    unidade = produto.unidadePadrao,
                    pesoOuVolume = leitura?.quantidade,
                    ehKit = (leitura?.itensPorEmbalagem ?: 1) > 1,
                    itensPorKit = leitura?.itensPorEmbalagem,
                ),
            )

            val precoFinal = preco ?: leitura?.preco
            val loja = estabelecimentoId
                ?: _estado.value.lojaSelecionadaId
                ?: precoRepositorio.listarEstabelecimentos().firstOrNull()?.id
            val item = listaRepositorio.item(itemId)
            var avisoDePreco: String? = null
            if (precoFinal != null && loja != null && item != null) {
                // Versao com aviso: o escaneamento e uma observacao nova do
                // usuario, entao merece o alerta de "ja viu mais barato".
                avisoDePreco = precoRepositorio.registrarPrecoComAviso(
                    item = item,
                    produtoId = produto.id,
                    estabelecimentoId = loja,
                    preco = precoFinal,
                )
            }

            _estado.update {
                it.copy(
                    leitura = null,
                    produtoConhecido = null,
                    codigoAoVivo = null,
                    mensagem = avisoDePreco ?: it.mensagem,
                )
            }
            aoConcluir()
        }
    }

    fun descartarLeitura() {
        _estado.update { it.copy(leitura = null, produtoConhecido = null, codigoAoVivo = null, mensagem = null) }
    }

    fun mensagemExibida() {
        _estado.update { it.copy(mensagem = null) }
    }

    /** Marca uma dica do scanner (video, foto ou codigo) como ja vista. */
    fun dispensarDica(chave: String) {
        viewModelScope.launch {
            configuracoesRepositorio.marcarDicaVista(chave)
            _estado.update {
                it.copy(
                    mostrarDicaVideo = it.mostrarDicaVideo && chave != Constantes.DICA_SCANNER_VIDEO,
                    mostrarDicaFoto = it.mostrarDicaFoto && chave != Constantes.DICA_SCANNER_FOTO,
                    mostrarDicaCodigo = it.mostrarDicaCodigo && chave != Constantes.DICA_SCANNER_CODIGO,
                )
            }
        }
    }

    override fun onCleared() {
        leitor.encerrar()
        super.onCleared()
    }
}

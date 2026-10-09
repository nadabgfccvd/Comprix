package br.com.comprix.presentation.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BarraDeProgresso
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.OpcaoSegmentada
import br.com.comprix.presentation.comum.Segmentado
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Constantes
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **Escanear preço** (telas 04 e 13 da referencia): foto, video guiado de 30 s
 * e codigo de barras.
 *
 * A permissao e pedida **aqui**, no momento em que faz sentido, e a recusa nao
 * cria beco sem saida: a tela oferece voltar e cadastrar o produto a mao.
 *
 * Tudo acontece dentro do aparelho. O ML Kit entra na variante **embarcada**,
 * sem modulo dinamico do Play Services, entao o primeiro escaneamento funciona
 * no avião, no mercado sem sinal e num celular que nunca viu a internet.
 */
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modoInicial: ModoScanner,
    aoVoltar: () -> Unit,
    aoConcluir: () -> Unit,
) {
    val cores = Tema.cores
    val contexto = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()

    var temPermissao by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedida -> temPermissao = concedida }

    LaunchedEffect(Unit) {
        viewModel.definirModo(modoInicial)
        if (!temPermissao) pedirPermissao.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(estado.mensagem) {
        if (estado.mensagem != null) {
            delay(3_200)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = {
            BarraSimples(tituloDoModo(estado.modo), aoVoltar = aoVoltar) {
                Selo("100% offline", tom = TomDoSelo.VERDE, icone = Icones.offline)
                Spacer(Modifier.width(8.dp))
            }
        },
        sobreposicao = {
            if (estado.mensagem != null) {
                Torrada(
                    estado.mensagem.orEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp)) {
                Segmentado(
                    opcoes = listOf(
                        OpcaoSegmentada("Foto", Icones.camera),
                        OpcaoSegmentada("Vídeo 30 s", Icones.video),
                        OpcaoSegmentada("Código", Icones.codigoDeBarras),
                    ),
                    indiceSelecionado = ModoScanner.entries.indexOf(estado.modo),
                    aoSelecionar = { viewModel.definirModo(ModoScanner.entries[it]) },
                )
                if (estado.modo == ModoScanner.VIDEO && estado.gravando) {
                    EspacoVertical(10.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(cores.vermelho))
                        Text(
                            "%02d:%02d / %02d:%02d".format(
                                estado.milissegundosGravados / 60_000,
                                estado.milissegundosGravados / 1_000 % 60,
                                Constantes.DURACAO_MAXIMA_VIDEO_MS / 60_000,
                                Constantes.DURACAO_MAXIMA_VIDEO_MS / 1_000 % 60,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = cores.texto,
                        )
                        BarraDeProgresso(estado.progressoDaGravacao, Modifier.weight(1f))
                    }
                }
            }

            if (!temPermissao) {
                EstadoVazio(
                    icone = Icones.camera,
                    titulo = "Câmera desligada",
                    descricao = "Sem a permissão de câmera o escaneamento não funciona, " +
                        "mas você pode cadastrar o produto à mão na lista — nada se perde.",
                    modifier = Modifier.weight(1f),
                ) {
                    BotaoComprix(
                        "Permitir câmera",
                        { pedirPermissao.launch(Manifest.permission.CAMERA) },
                        icone = Icones.camera,
                    )
                    EspacoVertical(8.dp)
                    BotaoComprix("Cadastrar à mão", aoVoltar, estilo = EstiloDeBotao.CONTORNADO)
                }
                return@Column
            }

            VisorDaCamera(
                viewModel = viewModel,
                modo = estado.modo,
                gravando = estado.gravando,
                instrucao = when {
                    estado.modo == ModoScanner.VIDEO && estado.gravando -> estado.etapaDoGuia.instrucao
                    estado.modo == ModoScanner.VIDEO -> "Grave 30 s girando a embalagem devagar"
                    estado.modo == ModoScanner.CODIGO -> "Aponte para o código de barras"
                    else -> "Enquadre a etiqueta de preço e o rótulo"
                },
                dica = when {
                    estado.modo == ModoScanner.VIDEO && estado.gravando -> estado.etapaDoGuia.dica
                    // Codigo: a distancia certa e a duvida numero um - escrita
                    // junto da moldura, na instrucao flutuante.
                    estado.modo == ModoScanner.CODIGO -> "Distância ideal: 15 a 25 cm, sem reflexo"
                    else -> null
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                aoCapturarFoto = { arquivo -> viewModel.analisarFoto(arquivo, contexto) },
                aoTerminarVideo = { arquivo -> viewModel.analisarVideo(arquivo) },
                aoAtualizarTempo = viewModel::atualizarTempoGravado,
                aoMudarGravacao = viewModel::marcarGravando,
                escopoDeTempo = { bloco -> escopo.launch { bloco() } },
            )

            Column(Modifier.padding(horizontal = 17.dp, vertical = 11.dp)) {
                // Banner de primeira vez, um por modo - mesmo estilo para os tres,
                // com passos numerados curtos e botao Entendi que marca como vista.
                val textoDaDica = when (estado.modo) {
                    ModoScanner.VIDEO -> if (estado.mostrarDicaVideo && !estado.gravando) {
                        "O vídeo guiado dura ${Constantes.DURACAO_MAXIMA_VIDEO_MS / 1000} s e pede " +
                            "para girar a embalagem: frente, preço, tabela, código e validade. " +
                            "O Comprix junta os quadros por maioria."
                    } else {
                        null
                    }

                    ModoScanner.FOTO -> if (estado.mostrarDicaFoto) {
                        "1. Enquadre o rótulo e o preço · 2. Segure firme · " +
                            "3. Toque em Capturar · 4. Confira o que o app leu e corrija se precisar."
                    } else {
                        null
                    }

                    ModoScanner.CODIGO -> if (estado.mostrarDicaCodigo) {
                        "1. Aponte para o código de barras · 2. Espere o bip visual · " +
                            "3. Confira nome e preço."
                    } else {
                        null
                    }
                }
                if (textoDaDica != null) {
                    BannerDeDica(textoDaDica) { viewModel.dispensarDica(chaveDaDica(estado.modo)) }
                }

                if (estado.analisando) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconeComprix(
                            Icones.recomecar,
                            null,
                            tamanho = TamanhoDeIcone.pequeno,
                            tinta = cores.acao,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                estado.progressoQuadros
                                    ?.let { (lidos, total) -> "Lendo quadro $lidos de $total…" }
                                    ?: "Lendo o rótulo…",
                                style = MaterialTheme.typography.titleSmall,
                                color = cores.texto,
                            )
                            estado.progressoQuadros?.let { (lidos, total) ->
                                EspacoVertical(5.dp)
                                BarraDeProgresso(lidos.toFloat() / total.coerceAtLeast(1))
                            }
                        }
                    }
                }

                estado.codigoAoVivo?.takeIf { estado.modo == ModoScanner.CODIGO }?.let { codigo ->
                    Selo("Código lido: $codigo", tom = TomDoSelo.VERDE, icone = Icones.confirmarCirculo)
                }
            }
        }
    }

    if (estado.temResultado && !estado.analisando) {
        FolhaDeRevisao(
            estado = estado,
            aoConfirmar = { nome, preco ->
                viewModel.confirmar(nome, preco, estado.lojaSelecionadaId) { aoConcluir() }
            },
            aoDescartar = viewModel::descartarLeitura,
            aoSelecionarLoja = viewModel::selecionarLoja,
            aoCriarLoja = viewModel::criarLojaPadrao,
        )
    }
}

private fun tituloDoModo(modo: ModoScanner) = when (modo) {
    ModoScanner.FOTO -> "Escanear preço"
    ModoScanner.VIDEO -> "Vídeo 30 s — fusão OCR"
    ModoScanner.CODIGO -> "Ler código de barras"
}

/**
 * Pre-visualizacao, moldura e disparo.
 *
 * O CameraX e preso ao ciclo de vida da tela; sair da tela libera a camera. Em
 * aparelho fraco o analisador roda em `STRATEGY_KEEP_ONLY_LATEST`, para nao
 * empilhar quadros na memoria de um Moto E5.
 */
@Composable
private fun VisorDaCamera(
    viewModel: ScannerViewModel,
    modo: ModoScanner,
    gravando: Boolean,
    instrucao: String,
    dica: String?,
    aoCapturarFoto: (File) -> Unit,
    aoTerminarVideo: (File) -> Unit,
    aoAtualizarTempo: (Long) -> Unit,
    aoMudarGravacao: (Boolean) -> Unit,
    escopoDeTempo: (suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val donoDoCiclo = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(contexto) }

    val visor = remember { PreviewView(contexto).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val captura = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val gravador = remember {
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
    }
    val capturaDeVideo = remember { VideoCapture.withOutput(gravador) }
    var gravacaoAtual by remember { mutableStateOf<Recording?>(null) }

    DisposableEffect(modo) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener({
            val provedor = futuro.get()
            val previa = Preview.Builder().build().also { it.setSurfaceProvider(visor.surfaceProvider) }
            val analise = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analisador ->
                    analisador.setAnalyzer(executor) { imagem -> viewModel.analisarQuadroAoVivo(imagem) }
                }

            runCatching {
                provedor.unbindAll()
                when (modo) {
                    ModoScanner.VIDEO -> provedor.bindToLifecycle(
                        donoDoCiclo,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previa,
                        capturaDeVideo,
                    )

                    else -> provedor.bindToLifecycle(
                        donoDoCiclo,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previa,
                        captura,
                        analise,
                    )
                }
            }
        }, executor)

        onDispose { runCatching { ProcessCameraProvider.getInstance(contexto).get().unbindAll() } }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { visor }, modifier = Modifier.fillMaxSize())

        // Moldura de enquadramento - o retangulo aponta onde o OCR tem foco.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.84f)
                .aspectRatio(if (modo == ModoScanner.CODIGO) 2.2f else 1.1f)
                .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(18.dp)),
        )

        // Instrucao flutuante, legivel sobre qualquer fundo.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(14.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                instrucao,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            if (dica != null) {
                Text(
                    dica,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (modo) {
                ModoScanner.FOTO -> BotaoDeDisparo(
                    rotulo = "Tirar foto",
                    icone = Icones.camera,
                    gravando = false,
                    aoTocar = {
                        val arquivo = File.createTempFile("comprix-foto", ".jpg", contexto.cacheDir)
                        captura.takePicture(
                            ImageCapture.OutputFileOptions.Builder(arquivo).build(),
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                                    aoCapturarFoto(arquivo)
                                }

                                override fun onError(erro: ImageCaptureException) {
                                    arquivo.delete()
                                }
                            },
                        )
                    },
                )

                ModoScanner.VIDEO -> BotaoDeDisparo(
                    rotulo = if (gravando) "Parar agora" else "Gravar 30 s",
                    icone = Icones.video,
                    gravando = gravando,
                    aoTocar = {
                        if (gravando) {
                            gravacaoAtual?.stop()
                            gravacaoAtual = null
                        } else {
                            val arquivo = File.createTempFile("comprix-video", ".mp4", contexto.cacheDir)
                            val opcoes = FileOutputOptions.Builder(arquivo).build()
                            aoMudarGravacao(true)
                            gravacaoAtual = gravador
                                .prepareRecording(contexto, opcoes)
                                .start(executor) { evento ->
                                    when (evento) {
                                        is VideoRecordEvent.Status ->
                                            aoAtualizarTempo(
                                                evento.recordingStats.recordedDurationNanos / 1_000_000,
                                            )

                                        is VideoRecordEvent.Finalize -> {
                                            aoMudarGravacao(false)
                                            if (evento.hasError()) {
                                                arquivo.delete()
                                            } else {
                                                aoTerminarVideo(arquivo)
                                            }
                                        }
                                    }
                                }
                            escopoDeTempo {
                                delay(Constantes.DURACAO_MAXIMA_VIDEO_MS)
                                gravacaoAtual?.stop()
                                gravacaoAtual = null
                            }
                        }
                    },
                )

                ModoScanner.CODIGO -> Text(
                    "Mantenha o código dentro da moldura",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BotaoDeDisparo(
    rotulo: String,
    icone: Int,
    gravando: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotaoComprix(
        texto = rotulo,
        aoTocar = aoTocar,
        modifier = modifier.width(220.dp).heightIn(min = 56.dp),
        estilo = if (gravando) EstiloDeBotao.PERIGO else EstiloDeBotao.PRINCIPAL,
        icone = if (gravando) Icones.fechar else icone,
    )
}

/** Banner de dica de primeira vez: mesmo estilo para foto, video e codigo. */
@Composable
private fun BannerDeDica(texto: String, aoDispensar: () -> Unit) {
    val cores = Tema.cores
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(cores.verdeSuave)
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        // Com botao na linha, o alinhamento e centralizado: o texto curto nao
        // deixa o botao pendurar no topo.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeComprix(
            Icones.informacao,
            null,
            tamanho = TamanhoDeIcone.pequeno,
            tinta = cores.verdeTinta,
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = cores.verdeTinta,
            modifier = Modifier.weight(1f),
        )
        BotaoComprix(
            "Entendi",
            aoDispensar,
            estilo = EstiloDeBotao.TEXTO,
            compacto = true,
        )
    }
    EspacoVertical(10.dp)
}

/** Chave da dica contextual de cada modo (gravada nas configuracoes). */
private fun chaveDaDica(modo: ModoScanner) = when (modo) {
    ModoScanner.FOTO -> Constantes.DICA_SCANNER_FOTO
    ModoScanner.VIDEO -> Constantes.DICA_SCANNER_VIDEO
    ModoScanner.CODIGO -> Constantes.DICA_SCANNER_CODIGO
}

/** Executor direto - usado em testes locais da tela. */
val executorImediato: Executor = Executor { comando -> comando.run() }

/** Verifica a permissao sem lancar dialogo (usado pela navegacao). */
fun temPermissaoDeCamera(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

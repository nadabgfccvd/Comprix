package br.com.comprix.data.camera

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import br.com.comprix.domain.modelo.LeituraDeRotulo
import br.com.comprix.domain.modelo.LinhaOcr
import br.com.comprix.domain.rotulo.ExtratorDeRotulo
import br.com.comprix.util.Constantes
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Leitura de imagem com ML Kit **empacotado** (nunca o módulo dinâmico do Play
 * Services): o reconhecimento funciona no primeiro uso, no avião, no corredor
 * do mercado sem sinal.
 *
 * Esta classe faz só a ponte ML Kit -> domínio. Quem interpreta o texto é o
 * [ExtratorDeRotulo], que é puro e testável sem Android.
 */
class LeitorDeImagem {

    private val reconhecedorDeTexto by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val leitorDeCodigos: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_ITF,
                )
                .build(),
        )
    }

    /** Resultado cru de um quadro: linhas de texto e código de barras. */
    data class QuadroLido(
        val linhas: List<LinhaOcr> = emptyList(),
        val codigoBarras: String? = null,
        val formatoCodigoBarras: String? = null,
    ) {
        val vazio: Boolean get() = linhas.isEmpty() && codigoBarras == null
    }

    // --- câmera ao vivo ------------------------------------------------------------------

    /**
     * Lê um quadro vindo do CameraX.
     *
     * O `ImageProxy` **não** é fechado aqui: quem chama controla o ciclo de
     * vida do analisador (e precisa fechar sempre, senão a câmera trava).
     */
    @OptIn(ExperimentalGetImage::class)
    suspend fun lerQuadro(imagem: ImageProxy, lerCodigo: Boolean, lerTexto: Boolean): QuadroLido {
        val original = imagem.image ?: return QuadroLido()
        val entrada = InputImage.fromMediaImage(original, imagem.imageInfo.rotationDegrees)
        val altura = entrada.height.coerceAtLeast(1).toFloat()

        val codigo = if (lerCodigo) primeiroCodigo(entrada) else null
        val linhas = if (lerTexto) linhasDeTexto(entrada, altura) else emptyList()

        return QuadroLido(
            linhas = linhas,
            codigoBarras = codigo?.rawValue,
            formatoCodigoBarras = codigo?.let { nomeDoFormato(it.format) },
        )
    }

    /** Lê uma foto já salva em arquivo (modo foto). */
    suspend fun lerArquivo(arquivo: File, contexto: android.content.Context): QuadroLido {
        val entrada = InputImage.fromFilePath(contexto, android.net.Uri.fromFile(arquivo))
        val altura = entrada.height.coerceAtLeast(1).toFloat()
        val codigo = primeiroCodigo(entrada)
        return QuadroLido(
            linhas = linhasDeTexto(entrada, altura),
            codigoBarras = codigo?.rawValue,
            formatoCodigoBarras = codigo?.let { nomeDoFormato(it.format) },
        )
    }

    private suspend fun lerBitmap(bitmap: Bitmap): QuadroLido {
        val entrada = InputImage.fromBitmap(bitmap, 0)
        val altura = entrada.height.coerceAtLeast(1).toFloat()
        val codigo = primeiroCodigo(entrada)
        return QuadroLido(
            linhas = linhasDeTexto(entrada, altura),
            codigoBarras = codigo?.rawValue,
            formatoCodigoBarras = codigo?.let { nomeDoFormato(it.format) },
        )
    }

    // --- vídeo guiado --------------------------------------------------------------------

    /**
     * Extrai quadros-chave de um vídeo e lê cada um deles (Seção 5.3).
     *
     * Um quadro a cada 600 ms, no máximo 36, reduzidos a 1280 px de largura:
     * é o equilíbrio que cabe em 2 GB de RAM sem derrubar o app e ainda dá
     * material suficiente para a votação por maioria.
     *
     * @param aoProgredir recebe o par (quadros lidos, total previsto).
     */
    suspend fun lerVideo(
        arquivo: File,
        aoProgredir: (Int, Int) -> Unit = { _, _ -> },
    ): List<LeituraDeRotulo> {
        val extrator = MediaMetadataRetriever()
        val leituras = mutableListOf<LeituraDeRotulo>()
        try {
            extrator.setDataSource(arquivo.absolutePath)
            val duracaoMs = extrator
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return emptyList()

            val instantes = instantesDosQuadros(duracaoMs)
            instantes.forEachIndexed { indice, instanteMs ->
                val quadro = extrator.getFrameAtTime(
                    instanteMs * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return@forEachIndexed

                val reduzido = reduzir(quadro)
                val lido = lerBitmap(reduzido)
                if (reduzido !== quadro) reduzido.recycle()
                quadro.recycle()

                if (!lido.vazio) {
                    leituras += ExtratorDeRotulo.extrair(
                        linhas = lido.linhas,
                        codigoBarras = lido.codigoBarras,
                        formatoCodigoBarras = lido.formatoCodigoBarras,
                        quadros = 1,
                    )
                }
                aoProgredir(indice + 1, instantes.size)
            }
        } catch (erro: IllegalArgumentException) {
            return leituras
        } catch (erro: RuntimeException) {
            return leituras
        } finally {
            runCatching { extrator.release() }
        }
        return leituras
    }

    /** Instantes (ms) dos quadros a analisar, respeitando o teto de quadros. */
    fun instantesDosQuadros(duracaoMs: Long): List<Long> {
        if (duracaoMs <= 0) return emptyList()
        val passo = Constantes.INTERVALO_ENTRE_QUADROS_MS
        val quantidade = ((duracaoMs / passo).toInt() + 1)
            .coerceAtMost(Constantes.MAXIMO_QUADROS_ANALISADOS)
            .coerceAtLeast(1)
        val intervalo = duracaoMs / quantidade
        return (0 until quantidade).map { indice -> (indice * intervalo).coerceAtMost(duracaoMs - 1) }
    }

    /** Reduz o quadro para no máximo 1280 px de largura, mantendo a proporção. */
    private fun reduzir(original: Bitmap): Bitmap {
        val largura = original.width
        if (largura <= Constantes.LARGURA_MAXIMA_QUADRO) return original
        val escala = Constantes.LARGURA_MAXIMA_QUADRO.toFloat() / largura
        val novaAltura = (original.height * escala).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(original, Constantes.LARGURA_MAXIMA_QUADRO, novaAltura, true)
    }

    // --- ML Kit --------------------------------------------------------------------------

    private suspend fun linhasDeTexto(entrada: InputImage, alturaDaImagem: Float): List<LinhaOcr> {
        val texto = aguardar(reconhecedorDeTexto.process(entrada)) ?: return emptyList()
        return texto.textBlocks.flatMap { bloco -> bloco.lines }.map { linha -> paraLinhaOcr(linha, alturaDaImagem) }
    }

    private fun paraLinhaOcr(linha: Text.Line, alturaDaImagem: Float): LinhaOcr {
        val caixa = linha.boundingBox
        return LinhaOcr(
            texto = linha.text,
            alturaRelativa = caixa?.let { it.height() / alturaDaImagem }?.coerceIn(0f, 1f) ?: 0.5f,
            topoRelativo = caixa?.let { it.top / alturaDaImagem }?.coerceIn(0f, 1f) ?: 0.5f,
        )
    }

    private suspend fun primeiroCodigo(entrada: InputImage): Barcode? =
        aguardar(leitorDeCodigos.process(entrada))
            ?.firstOrNull { !it.rawValue.isNullOrBlank() }

    /** Converte a `Task` do ML Kit em suspensão, sem bloquear a thread. */
    private suspend fun <T> aguardar(tarefa: com.google.android.gms.tasks.Task<T>): T? =
        suspendCoroutine { continuacao ->
            tarefa
                .addOnSuccessListener { resultado -> continuacao.resume(resultado) }
                .addOnFailureListener { continuacao.resume(null) }
                .addOnCanceledListener { continuacao.resume(null) }
        }

    private fun nomeDoFormato(formato: Int): String = when (formato) {
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "CODE-128"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "desconhecido"
    }

    /** Libera os leitores quando a tela de câmera sai de cena. */
    fun encerrar() {
        runCatching { reconhecedorDeTexto.close() }
        runCatching { leitorDeCodigos.close() }
    }
}

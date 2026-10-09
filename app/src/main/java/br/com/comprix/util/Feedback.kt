package br.com.comprix.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Motor de feedback sensorial (som + vibracao) do Comprix.
 *
 * Os interruptores "Sons" e "Vibracao" dos Ajustes descrevem exatamente este
 * comportamento ("Confirmacao curta ao marcar item e ao gravar preço" /
 * "Retorno tátil no scanner e nas ações destrutivas"), mas antes daqui nada
 * os executava: a preferencia existia e ninguem a lia. Este objeto centraliza
 * a emissao num ponto unico, para que toda tela toque no mesmo motor e o
 * usuario perceba um padrao unico de retorno.
 *
 * Contratos:
 * - Nenhum caminho aqui lanca excecao: tudo vive dentro de `runCatching`,
 *   porque ToneGenerator e Vibrator falham em aparelho ruim e o app e 100%
 *   offline - nao ha por que derrubar uma compra por causa de um beep.
 * - Thread-safe: o estado e `@Volatile` (escrito pelo collector em IO,
 *   lido pela thread de UI) e `Vibrator.vibrate` aceita qualquer thread.
 * - Silencioso quando desligado: `sons`/`vibracao` falsos simplesmente
 *   pulam a emissao, sem efeito colateral.
 *
 * Nao ha arquivos de audio no APK: o som usa tons de sistema (ToneGenerator),
 * o que mantem o binario enxuto e o app funcional em modo aviao.
 */
object Feedback {

    private const val TAG = "ComprixFeedback"

    /** Soltar o ToneGenerator imediatamente corta o beep no meio. */
    private const val ATRASO_DO_RELEASE_MS = 350L

    /** Volume do beep em STREAM_MUSIC (escala 0-100). */
    private const val VOLUME_DO_TOM = 80

    /** Estado espelhado das configuracoes do usuario (padrao: ligados). */
    @Volatile
    var sons: Boolean = true
        private set

    @Volatile
    var vibracao: Boolean = true
        private set

    @Volatile
    private var contextoApp: Context? = null

    private val manipuladorDoRelease by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Tipos de vibracao. Para TOQUE e CONFIRMACAO e um one-shot curto; para
     * SUCESSO e ERRO e um waveform "pum-pum" (pausa, vibra, pausa, vibra).
     * Amplitudes sempre DEFAULT_AMPLITUDE: intensidade do sistema, e o
     * one-shot/waveform existem desde a API 26 (o proprio minSdk do app).
     */
    enum class TipoDeVibracao(val duracaoMs: Long, val padrao: LongArray?) {
        TOQUE(20, null),
        CONFIRMACAO(35, null),
        SUCESSO(0, longArrayOf(0, 30, 60, 30)),
        ERRO(0, longArrayOf(0, 40, 60, 40, 60, 40)),
    }

    /** Tipos de som: tons curtos de sistema, um por significado. */
    enum class TipoDeSom(val idDoTom: Int) {
        CONFIRMACAO(ToneGenerator.TONE_PROP_ACK),
        ERRO(ToneGenerator.TONE_PROP_NACK),
        SUCESSO(ToneGenerator.TONE_CDMA_CONFIRM),
    }

    /** Guarda o applicationContext uma unica vez (ComprixApp.onCreate). */
    fun inicializar(contexto: Context) {
        contextoApp = contexto.applicationContext
    }

    /** Espelha as configuracoes do usuario (collector no ComprixApp). */
    fun configurar(sons: Boolean, vibracao: Boolean) {
        this.sons = sons
        this.vibracao = vibracao
    }

    /** Vibra conforme o tipo; ignora se desligado, indisponivel ou em falha. */
    fun vibrar(tipo: TipoDeVibracao) {
        if (!vibracao) return
        runCatching {
            val obtido = vibrador() ?: return@runCatching
            val efeito = if (tipo.padrao != null) {
                VibrationEffect.createWaveform(tipo.padrao, -1)
            } else {
                VibrationEffect.createOneShot(tipo.duracaoMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            obtido.vibrate(efeito)
        }
    }

    /** Emite um tom curto; ignora se desligado ou se o aparelho nao cooperar. */
    fun som(tipo: TipoDeSom) {
        if (!sons) return
        runCatching {
            val gerador = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_DO_TOM)
            gerador.startTone(tipo.idDoTom)
            // Release adiado: soltar na hora corta o beep antes de tocar.
            manipuladorDoRelease.postDelayed(
                { runCatching { gerador.release() } },
                ATRASO_DO_RELEASE_MS,
            )
        }.onFailure { erro ->
            // ToneGenerator falha em alguns aparelhos; feedback nunca derruba o app.
            Log.d(TAG, "Tom indisponível: ${erro.message}")
        }
    }

    /** Descricao curta para diagnostico (log da abertura / modo tecnico). */
    fun statusDaVibracao(contexto: Context): String =
        if (vibrador(contexto) != null) "Vibração disponível" else "Vibração indisponível neste aparelho"

    /**
     * Vibrator nas geracoes de API: a partir do S (31) o servico correto e o
     * `VibratorManager.defaultVibrator`; nas 26-30 o `VIBRATOR_SERVICE`
     * classico (obsoleto, porem funcional). Guard final `hasVibrator()`
     * cobre aparelhos sem motor de vibracao.
     */
    @Suppress("DEPRECATION")
    private fun vibrador(contexto: Context? = contextoApp): Vibrator? {
        if (contexto == null) return null
        return runCatching {
            val obtido = if (Build.VERSION.SDK_INT >= 31) {
                val gerente = contexto.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                gerente?.defaultVibrator
            } else {
                contexto.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (obtido != null && obtido.hasVibrator()) obtido else null
        }.getOrNull()
    }
}

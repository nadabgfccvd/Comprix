package br.com.comprix.data.catalogo

import android.content.Context
import br.com.comprix.R
import br.com.comprix.domain.catalogo.CatalogoSemente

/**
 * Le o catalogo-semente do `res/raw` uma unica vez por processo.
 *
 * O parse do arquivo (~50 KB) leva poucos milissegundos, mas nao e de graca -
 * e nem sempre e necessario (quem nao abre o catalogo nao paga nada). Por isso
 * a leitura e preguicosa: o primeiro acesso carrega e guarda; os seguintes
 * apenas devolvem o que ja esta em memoria. O `@Volatile` com trava dupla
 * garante que dois fios que chegam juntos nao carreguem duas vezes.
 */
class FonteDoCatalogoSemente(private val contexto: Context) {

    @Volatile
    private var emMemoria: CatalogoSemente? = null

    private val trava = Any()

    /** Catalogo carregado sob demanda na primeira consulta e reaproveitado depois. */
    val catalogo: CatalogoSemente
        get() {
            emMemoria?.let { pronto -> return pronto }
            synchronized(trava) {
                emMemoria?.let { pronto -> return pronto }
                val texto = contexto.resources
                    .openRawResource(R.raw.catalogo_semente)
                    .bufferedReader()
                    .use { leitor -> leitor.readText() }
                val carregado = CatalogoSemente(texto)
                emMemoria = carregado
                return carregado
            }
        }
}

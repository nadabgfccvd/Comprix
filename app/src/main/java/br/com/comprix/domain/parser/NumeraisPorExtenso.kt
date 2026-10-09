package br.com.comprix.domain.parser

import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Numerais por extenso de 0 a 50.000, por **regra de formacao** - nao por
 * lista de 50.001 entradas (que pesaria no APK e no tempo de carga).
 *
 * ## Como funciona
 * O texto e quebrado em palavras conhecidas e somado/multiplicado conforme a
 * classe de cada uma:
 *
 * ```
 * "mil e duzentos"      -> 1000 + 200            = 1200
 * "doze centos"         -> 12 x 100              = 1200   (forma popular)
 * "vinte e um mil"      -> (20 + 1) x 1000       = 21000
 * "duzentas e uma"      -> 200 + 1               = 201    (concordancia de genero)
 * ```
 *
 * ## O que e aceito
 * - com e sem acento ("tres", "três"), com e sem hifen ("vinte e um", "vinte-e-um");
 * - concordancia de genero ("duzentos"/"duzentas", "um"/"uma");
 * - o conectivo "e" entre centena e dezena/unidade, e entre milhar e resto;
 * - a forma popular "N centos" (doze centos = 1200);
 * - digitos com separador de milhar ("1.200", "1200").
 *
 * ## O que nao e aceito (de proposito)
 * - acima de [MAXIMO] (50.000): numero de lista de compras nao chega la, e
 *   aceitar abriria porta para interpretar codigo de barras como quantidade;
 * - ordinais ("primeiro", "segundo") - nao sao quantidade;
 * - leitura digito a digito ("dois cinco" = 25) - fora de escopo.
 */
object NumeraisPorExtenso {

    const val MAXIMO = 50_000

    /** Unidades e irregulares de 0 a 19, ja normalizados (sem acento). */
    private val UNIDADES: Map<String, Int> = mapOf(
        "zero" to 0,
        "um" to 1, "uma" to 1, "hum" to 1,
        "dois" to 2, "duas" to 2,
        "tres" to 3,
        "quatro" to 4,
        "cinco" to 5,
        "seis" to 6,
        "sete" to 7,
        "oito" to 8,
        "nove" to 9,
        "dez" to 10,
        "onze" to 11,
        "doze" to 12,
        "treze" to 13,
        "catorze" to 14, "quatorze" to 14,
        "quinze" to 15,
        "dezesseis" to 16, "dezasseis" to 16,
        "dezessete" to 17, "dezassete" to 17,
        "dezoito" to 18,
        "dezenove" to 19, "dezanove" to 19,
    )

    private val DEZENAS: Map<String, Int> = mapOf(
        "vinte" to 20,
        "trinta" to 30,
        "quarenta" to 40,
        "cinquenta" to 50, "cincoenta" to 50,
        "sessenta" to 60,
        "setenta" to 70,
        "oitenta" to 80,
        "noventa" to 90,
    )

    private val CENTENAS: Map<String, Int> = mapOf(
        "cem" to 100, "cento" to 100,
        "duzentos" to 200, "duzentas" to 200,
        "trezentos" to 300, "trezentas" to 300,
        "quatrocentos" to 400, "quatrocentas" to 400,
        "quinhentos" to 500, "quinhentas" to 500,
        "seiscentos" to 600, "seiscentas" to 600,
        "setecentos" to 700, "setecentas" to 700,
        "oitocentos" to 800, "oitocentas" to 800,
        "novecentos" to 900, "novecentas" to 900,
    )

    private const val MIL = "mil"

    /** "centos" na forma popular multiplicativa: "doze centos" = 1200. */
    private val CENTOS_PLURAL = setOf("centos", "centenas")

    /** Palavras que podem aparecer no meio de um numeral sem alterar o valor. */
    private val LIGACOES = setOf("e")

    /** Todas as palavras que o parser reconhece como parte de um numeral. */
    val palavrasConhecidas: Set<String> =
        UNIDADES.keys + DEZENAS.keys + CENTENAS.keys + CENTOS_PLURAL + setOf(MIL)

    /**
     * Interpreta um numeral por extenso.
     *
     * @return o valor, ou null se o texto nao for um numeral valido (nunca
     * lanca excecao, nunca "chuta" um numero parcial).
     */
    fun interpretar(texto: String): Int? {
        val palavras = TextoUtil.removerAcentos(texto.lowercase())
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
        if (palavras.isEmpty()) return null
        return interpretarPalavras(palavras)
    }

    /**
     * Mesma leitura, mas sobre uma lista de palavras ja separada.
     * Devolve null se qualquer palavra for desconhecida.
     */
    fun interpretarPalavras(palavras: List<String>): Int? {
        if (palavras.isEmpty()) return null
        if (palavras.all { it in LIGACOES }) return null

        var total = 0
        var parcial = 0
        var viuAlgum = false

        palavras.forEach { palavra ->
            when {
                palavra in LIGACOES -> Unit

                palavra in UNIDADES -> {
                    parcial += UNIDADES.getValue(palavra)
                    viuAlgum = true
                }

                palavra in DEZENAS -> {
                    parcial += DEZENAS.getValue(palavra)
                    viuAlgum = true
                }

                palavra in CENTENAS -> {
                    parcial += CENTENAS.getValue(palavra)
                    viuAlgum = true
                }

                // Forma popular: "doze centos" = 12 x 100.
                palavra in CENTOS_PLURAL -> {
                    if (parcial == 0) return null
                    parcial *= 100
                    viuAlgum = true
                }

                palavra == MIL -> {
                    // "mil" sozinho vale 1000; "vinte e um mil" vale 21 x 1000.
                    total += (if (parcial == 0) 1 else parcial) * 1000
                    parcial = 0
                    viuAlgum = true
                }

                else -> return null
            }
        }

        if (!viuAlgum) return null
        val resultado = total + parcial
        return if (resultado in 0..MAXIMO) resultado else null
    }

    /**
     * Le um numero escrito em digitos, com ou sem separador de milhar e com
     * virgula ou ponto decimal: "1.200", "1200", "1,5", "0.5".
     */
    fun interpretarDigitos(texto: String): BigDecimal? {
        val limpo = texto.trim()
        if (limpo.isEmpty() || limpo.none { it.isDigit() }) return null
        if (limpo.any { !it.isDigit() && it != '.' && it != ',' }) return null
        return TextoUtil.paraDecimal(limpo)
    }

    /**
     * Varre o inicio de [palavras] consumindo o maior numeral valido possivel.
     *
     * Necessario porque "duas duzias de ovos" comeca com numeral ("duas") mas
     * o que vem depois nao e numeral - e preciso saber onde o numero termina.
     *
     * @return o valor e quantas palavras foram consumidas, ou null.
     */
    fun consumirNoInicio(palavras: List<String>): Pair<Int, Int>? {
        var melhor: Pair<Int, Int>? = null
        var tamanho = 1
        while (tamanho <= palavras.size && tamanho <= LIMITE_DE_PALAVRAS) {
            val trecho = palavras.take(tamanho)
            // Nao deixa o numeral terminar em "e" pendurado ("dois e meio").
            if (trecho.last() !in LIGACOES) {
                interpretarPalavras(trecho)?.let { valor -> melhor = valor to tamanho }
            }
            tamanho++
        }
        return melhor
    }

    /** "vinte e um mil e quinhentos" = 6 palavras; acima disso nao e lista de compras. */
    private const val LIMITE_DE_PALAVRAS = 7
}

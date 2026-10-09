package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Leitura de quantidade em **posicao estritamente numerica** - o campo de
 * quantidade do editor de item, onde so cabe um numero.
 *
 * E o unico lugar do app em que um apelido de animal vira numero. A posicao nao
 * pode ser deduzida do texto (Regra A da Secao 7.4 e explicita: nem pontuacao
 * nem estrutura de frase abrem excecao) - ela vem de **onde** a pessoa digitou.
 * Por isso a decisao mora aqui, e nao em [ParserDeLinhaDeCompra]:
 *
 * ```
 * campo de quantidade:  "cachorro"            -> 5
 * linha de texto livre: "racao para cachorro" -> item Pet, nunca 5
 * ```
 *
 * Nao reconheceu? Devolve `null`. Campo em branco e melhor que numero inventado.
 */
object QuantidadeEmPosicaoNumerica {

    data class Resultado(
        val valor: BigDecimal,
        val unidade: Unidade?,
        val token: TokenDeQuantidade?,
        val confianca: Confianca,
        /** Apelido de origem pejorativa: aceito se digitado, nunca oferecido. */
        val sensivel: Boolean = false,
    )

    private val FRACOES: Map<String, String> = mapOf(
        "meio" to "0.5",
        "meia" to "0.5",
        "metade" to "0.5",
        "um terco" to "0.333",
        "dois tercos" to "0.667",
        "um quarto" to "0.25",
        "tres quartos" to "0.75",
    )

    fun interpretar(texto: String): Resultado? {
        val normalizado = TextoUtil.normalizar(texto)
        if (normalizado.isBlank()) return null

        // 1. Numero puro, com virgula, ponto ou barra de fracao.
        numeroDireto(normalizado)?.let {
            return Resultado(it, null, null, Confianca.ALTA)
        }

        // 2. Token da tabela: coletivo, embalagem ou apelido popular.
        //    Aqui o apelido de animal E aceito - a posicao e numerica por
        //    construcao, que e exatamente o que a Regra A exige.
        TabelaDeQuantidades.porTexto(normalizado)?.let { token ->
            if (PosicaoAceita.ISOLADO_POSICAO_NUMERICA !in token.posicoesAceitas) return@let
            return Resultado(
                valor = token.valor,
                unidade = token.unidade,
                token = token,
                confianca = token.confiancaBase,
                sensivel = token.sensivel,
            )
        }

        // 3. Fracao falada.
        FRACOES[normalizado]?.let {
            return Resultado(BigDecimal(it), null, null, Confianca.ALTA)
        }

        // 4. Numeral por extenso ("vinte e um", "mil e duzentos", "doze centos").
        NumeraisPorExtenso.interpretar(normalizado)?.let {
            return Resultado(BigDecimal(it), null, null, Confianca.ALTA)
        }

        // 5. Numero seguido de unidade ("2 kg", "500 g").
        val palavras = normalizado.split(' ').filter { it.isNotBlank() }
        if (palavras.size == 2) {
            val valor = numeroDireto(palavras[0])
                ?: NumeraisPorExtenso.interpretar(palavras[0])?.let { BigDecimal(it) }
            val unidade = Unidade.porTexto(palavras[1])
            if (valor != null && unidade != null) {
                return Resultado(valor, unidade, null, Confianca.ALTA)
            }
        }

        return null
    }

    private fun numeroDireto(texto: String): BigDecimal? {
        if (Regex("""\d+/\d+""").matches(texto)) {
            val (a, b) = texto.split('/')
            val numerador = a.toBigDecimalOrNull() ?: return null
            val denominador = b.toBigDecimalOrNull() ?: return null
            if (denominador.signum() == 0) return null
            return numerador.divide(denominador, 4, RoundingMode.HALF_EVEN).stripTrailingZeros()
        }
        if (!Regex("""\d+([.,]\d+)?""").matches(texto)) return null
        return TextoUtil.paraDecimal(texto)
    }
}

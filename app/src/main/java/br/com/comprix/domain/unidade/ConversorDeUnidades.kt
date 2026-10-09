package br.com.comprix.domain.unidade

import br.com.comprix.domain.modelo.Dimensao
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.Constantes
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Normalizacao de unidades - a base de toda comparacao honesta de precos.
 *
 * ## Por que isso existe
 * Comparar "R$ 24,90 o pacote" com "R$ 5,49 o pacote" nao diz nada: um pode ser
 * de 5 kg e o outro de 1 kg. O app converte tudo para uma unidade-base por
 * dimensao (g, mL, un, m) e so entao divide o preco.
 *
 * ## Modelo de quantidade
 * Um item tem tres numeros que se multiplicam:
 *
 * ```
 * quantidadeBase = quantidade x fatorDaUnidade x itensPorKit
 *                  ^embalagens  ^kg->g, L->mL    ^fardo com 12
 * ```
 *
 * - `quantidade`: quantas EMBALAGENS vao no carrinho ("2" em "2 x 500 g").
 * - `pesoOuVolume`: conteudo de UMA embalagem ("500" em "2 x 500 g").
 * - `itensPorKit`: unidades dentro do pack ("12" em "fardo com 12 x 350 mL").
 *
 * Todas as operacoes sao em [BigDecimal]: dinheiro e quantidade nunca passam
 * por `Double` neste app.
 */
object ConversorDeUnidades {

    /**
     * Converte um valor para a unidade-base da sua dimensao.
     *
     * @param itensPorEmbalagem multiplicador de pack (fardo com 12, caixa com 6).
     * @return a quantidade equivalente em g, mL, un ou m (nunca negativa).
     */
    fun paraUnidadeBase(
        quantidade: BigDecimal,
        unidade: Unidade,
        itensPorEmbalagem: Int? = null,
    ): BigDecimal {
        if (quantidade.signum() <= 0) return BigDecimal.ZERO
        val multiplicador = BigDecimal((itensPorEmbalagem ?: 1).coerceAtLeast(1))
        return quantidade
            .multiply(unidade.fatorParaBase)
            .multiply(multiplicador)
            .setScale(Constantes.ESCALA_UNIDADE_BASE, RoundingMode.HALF_EVEN)
            .stripTrailingZeros()
    }

    /**
     * Quantidade total que o item representa no carrinho, na unidade-base.
     *
     * Leva em conta o numero de embalagens: "2 pacotes de 500 g" = 1000 g.
     * Quando o item nao declara peso/volume, cada embalagem conta como 1 unidade.
     */
    fun quantidadeTotalEmBase(item: ItemDaLista): BigDecimal {
        if (item.quantidade.signum() <= 0) return BigDecimal.ZERO
        val conteudoDeUma = item.pesoOuVolume ?: BigDecimal.ONE
        val base = paraUnidadeBase(conteudoDeUma, item.unidade, item.itensPorKit)
        return base.multiply(item.quantidade).stripTrailingZeros()
    }

    /**
     * Converte entre duas unidades da MESMA dimensao.
     *
     * @return o valor convertido, ou null quando as dimensoes sao incompativeis
     * (kg para L, por exemplo) - o app nunca "chuta" densidade.
     */
    fun converter(valor: BigDecimal, de: Unidade, para: Unidade): BigDecimal? {
        if (!saoComparaveis(de, para)) return null
        return valor
            .multiply(de.fatorParaBase)
            .divide(para.fatorParaBase, Constantes.ESCALA_UNIDADE_BASE, RoundingMode.HALF_EVEN)
            .stripTrailingZeros()
    }

    /** Quantidade reconhecida dentro de um texto livre. */
    data class QuantidadeInterpretada(
        val quantidade: BigDecimal,
        val unidade: Unidade,
        val itensPorEmbalagem: Int? = null,
    )

    private val REGEX_QUANTIDADE =
        Regex("""(\d+(?:[.,]\d+)?)\s*(kg|quilos?|g|gr|gramas?|ml|mls|l|lt|litros?|un|unid(?:ades?)?|dz|duzias?|m|metros?)\b""")

    private val REGEX_MULTIPACK =
        Regex("""(?:c/|com|pack|kit|fardo|leve)\s*(\d{1,3})|(\d{1,3})\s*x\s*(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l|un)\b""")

    /** "6x", "12 x" sobrando no nome depois que o conteudo foi extraido. */
    private val REGEX_MULTIPLICADOR_ORFAO = Regex("""(?<![a-z0-9])\d{1,3}\s*x(?![a-z0-9])""")

    /**
     * Interpreta textos livres como "500g", "1,5 L", "Pacote c/ 6 un", "12x350ml".
     * E o que permite preencher peso/volume automaticamente a partir do OCR ou
     * da linha de adicao rapida.
     *
     * Prioridade: pack com conteudo ("12x350ml") > quantidade simples ("500g") >
     * contagem pura ("fardo com 6").
     *
     * @return a quantidade reconhecida, ou null se nada for identificavel.
     */
    fun interpretarTexto(texto: String?): QuantidadeInterpretada? {
        if (texto.isNullOrBlank()) return null
        val normalizado = TextoUtil.removerAcentos(texto.lowercase())

        // Caso "12 x 350 ml" / "6x1L": embalagem multipla com conteudo declarado.
        val multipack = REGEX_MULTIPACK.find(normalizado)
        if (multipack != null && multipack.groupValues[2].isNotBlank()) {
            val itens = multipack.groupValues[2].toIntOrNull()
            val valor = TextoUtil.paraDecimal(multipack.groupValues[3])
            val unidade = Unidade.porTexto(multipack.groupValues[4])
            if (itens != null && valor != null && unidade != null && valor.signum() > 0) {
                return QuantidadeInterpretada(valor, unidade, itens)
            }
        }

        val achado = REGEX_QUANTIDADE.find(normalizado)
        if (achado != null) {
            val valor = TextoUtil.paraDecimal(achado.groupValues[1])
            val unidade = Unidade.porTexto(achado.groupValues[2])
            if (valor != null && unidade != null && valor.signum() > 0) {
                val itensPorEmbalagem = multipack?.groupValues?.get(1)?.toIntOrNull()
                return QuantidadeInterpretada(valor, unidade, itensPorEmbalagem)
            }
        }

        // Somente "c/ 12" ou "fardo com 6", sem conteudo: contagem pura.
        val somenteContagem = multipack?.groupValues?.get(1)?.toIntOrNull()
        if (somenteContagem != null && somenteContagem > 1) {
            return QuantidadeInterpretada(BigDecimal.ONE, Unidade.PACOTE, somenteContagem)
        }
        return null
    }

    /**
     * Remove do texto a parte que virou quantidade, para o produto nao se chamar
     * "Arroz 5kg 5kg". Ex.: "arroz 5kg" -> "arroz".
     *
     * A ordem importa: o multipack sai ANTES da quantidade simples. Fazendo o
     * contrario, "6x350ml cerveja" perderia o "350ml" e deixaria o "6x" orfao,
     * criando o produto "6x Cerveja" (bug real da primeira versao).
     *
     * @return o texto limpo; se sobrar vazio (o texto era SO quantidade),
     * devolve o original - quem chama decide o que fazer.
     */
    fun nomeSemQuantidade(texto: String): String {
        val base = TextoUtil.removerAcentos(texto.lowercase())
        val semMultipack = REGEX_MULTIPACK.replace(base, " ")
        val semQuantidade = REGEX_QUANTIDADE.replace(semMultipack, " ")
        val limpo = REGEX_MULTIPLICADOR_ORFAO.replace(semQuantidade, " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ',', '.')
        return if (limpo.isBlank()) texto.trim() else limpo
    }

    /** Dimensoes compativeis para comparacao direta entre dois itens. */
    fun saoComparaveis(a: Unidade, b: Unidade): Boolean = a.dimensao == b.dimensao

    /** Rotulo da unidade-base ("g", "mL", "un", "m"). */
    fun rotuloBase(dimensao: Dimensao): String = dimensao.unidadeBase
}

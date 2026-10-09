package br.com.comprix.domain.parser

import br.com.comprix.util.TextoUtil

/**
 * Expressoes que **parecem** quantidade e nao sao (Secao 8 do documento do
 * parser), mais as expressoes de quantidade indefinida (Secao 6.3).
 *
 * Sao guardas de prioridade maxima: antes de tentar qualquer conversao, o
 * parser verifica se o trecho casa com um falso positivo conhecido. Se casar,
 * a palavra fica como texto e nenhuma quantidade e inventada.
 */
object FalsosPositivos {

    /**
     * Par (gatilho, expressao completa que o anula).
     *
     * "meia" vira 6 em "meia duzia", mas nao em "meia calca": a diferenca esta
     * na palavra seguinte, nao na palavra isolada.
     */
    data class Guarda(
        val gatilho: String,
        val expressao: String,
        val explicacao: String,
    )

    val guardas: List<Guarda> = listOf(
        Guarda("meia", "meia calca", "peca de vestuario, nao 6 nem 0,5"),
        Guarda("meia", "meia calça", "peca de vestuario, nao 6 nem 0,5"),
        Guarda("meia", "meia estacao", "expressao de clima/vestuario"),
        Guarda("meia", "meia noite", "horario"),
        Guarda("meia", "meia hora", "duracao, nao quantidade de item"),
        Guarda("meia", "meia entrada", "ingresso"),
        Guarda("meia", "par de meias", "vestuario"),
        Guarda("par", "parafuso", "ferragem - comeca com 'par' mas nao e o coletivo"),
        Guarda("par", "parafusos", "ferragem"),
        Guarda("par", "parmesao", "queijo - comeca com 'par'"),
        Guarda("par", "parboilizado", "tipo de arroz"),
        Guarda("cento", "cento e um dalmatas", "titulo de filme"),
        Guarda("duzia", "duzia de motivos", "figura de linguagem"),
        Guarda("duzia", "duzia de vezes", "figura de linguagem"),
        Guarda("quina", "quina da mesa", "canto de movel"),
        Guarda("quina", "quina da parede", "canto de parede"),
        Guarda("grossa", "tecido grosso", "adjetivo"),
        Guarda("grossa", "farinha grossa", "adjetivo: granulometria"),
        Guarda("grossa", "fatia grossa", "adjetivo: espessura"),
        Guarda("grossa", "sal grosso", "tipo de sal"),
        Guarda("molho", "molho de tomate", "produto, nao embalagem"),
        Guarda("molho", "molho shoyu", "produto"),
        Guarda("molho", "molho ingles", "produto"),
        Guarda("lata", "lata de lixo", "utensilio, nao embalagem do item"),
        Guarda("trio", "trio eletrico", "expressao"),
        Guarda("coco", "coco ralado", "produto de mercearia, nao fruta inteira"),
        Guarda("meia", "meia duzia", "ESTE e valido: 6 - guarda listada para contraste"),
    )

    /**
     * Expressoes de quantidade indefinida: quem escreve isso **nao** quer um
     * numero. Produzem `quantidade = null` com motivo, nunca 1.
     */
    val quantidadeIndefinida: List<String> = listOf(
        "um pouco", "uma pouca", "um tanto", "um pouquinho", "uns", "umas",
        "alguns", "algumas", "varios", "varias", "mais ou menos", "a gosto",
        "o quanto baste", "q b", "qb", "a olho", "tanto quanto", "o suficiente",
        "se tiver", "caso tenha", "o de sempre", "o normal",
    )

    /**
     * Guardas agrupadas pelo gatilho.
     *
     * A frase so e comparada com as expressoes do gatilho que realmente aparece
     * nela. Sem esse filtro seriam ~27 varreduras de string por segmento, a
     * cada tecla digitada - caro demais para o orcamento de 16 ms.
     */
    private val guardasPorGatilho: Map<String, List<Pair<String, Guarda>>> =
        guardas.groupBy { TextoUtil.normalizar(it.gatilho) }
            .mapValues { (_, lista) ->
                lista.map { TextoUtil.normalizar(it.expressao) to it }
                    .sortedByDescending { it.first.length }
            }

    /** Expressoes indefinidas agrupadas pela primeira palavra ("um", "a", "uns"). */
    private val indefinidasPorPrimeiraPalavra: Map<String, List<String>> =
        quantidadeIndefinida.map { TextoUtil.normalizar(it) }
            .sortedByDescending { it.length }
            .groupBy { it.substringBefore(' ') }

    /**
     * Procura um falso positivo em [textoNormalizado].
     *
     * A guarda de "meia duzia" existe so para documentacao e e ignorada aqui.
     */
    fun guardaAtiva(textoNormalizado: String): Guarda? {
        val palavras = textoNormalizado.split(' ').filterTo(HashSet()) { it.isNotBlank() }
        if (palavras.isEmpty()) return null
        // O gatilho tem de aparecer como palavra solta: sem isso, "uma grossa de
        // parafusos" seria bloqueada por conter "parafusos", sendo que ali
        // "grossa" (144) e legitimo.
        palavras.forEach { palavra ->
            guardasPorGatilho[palavra]?.forEach { (expressao, guarda) ->
                if (guarda.expressao != "meia duzia" &&
                    TextoUtil.contemPalavra(textoNormalizado, expressao)
                ) {
                    return guarda
                }
            }
        }
        return null
    }

    /** Expressao de quantidade indefinida encontrada no texto, se houver. */
    fun indefinidaEm(textoNormalizado: String): String? {
        val palavras = textoNormalizado.split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return null
        palavras.forEach { palavra ->
            indefinidasPorPrimeiraPalavra[palavra]?.forEach { expressao ->
                if (TextoUtil.contemPalavra(textoNormalizado, expressao)) return expressao
            }
        }
        return null
    }

    /** Remove do texto a expressao de quantidade indefinida, deixando so o item. */
    fun removerIndefinida(textoNormalizado: String): String {
        val encontrada = indefinidaEm(textoNormalizado) ?: return textoNormalizado
        return textoNormalizado
            .replace(Regex("(^|\\s)${Regex.escape(encontrada)}(\\s|$)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

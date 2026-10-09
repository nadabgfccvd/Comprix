package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.TextoUtil

/** Os quatro jeitos reais de escrever a quantidade (Secao 6.2 do documento). */
enum class PadraoDePosicao {
    /** quantidade + `de` + item: "meia duzia de ovos". */
    P1,

    /** item + `,` + quantidade: "ovos, meia duzia". */
    P2,

    /** numeral + unidade + `de` + item: "2 kg de carne". */
    P3,

    /** item + numeral + unidade: "cafe 500g", "pao frances 3". **O mais comum.** */
    P4,

    /** Sem quantidade declarada: "leite". */
    SEM_QUANTIDADE,
}

/** Pedaco da linha que vai virar um item. */
data class Segmento(
    val texto: String,
    /** Trecho de quantidade que veio de um segmento seguinte (padrao P2). */
    val quantidadeAnexa: String? = null,
)

/**
 * Quebra uma linha de texto livre em segmentos - um por item.
 *
 * ## O problema do `e`
 * A mesma letra separa itens e compoe quantidade:
 * ```
 * "carne e ovos"        -> 2 itens
 * "um quilo e meio"     -> 1 item  (quantidade 1,5)
 * "2 e 50 de carne"     -> 1 item  (quantidade ambigua, ver G4)
 * "mil e duzentos"      -> 1 numero
 * ```
 * A decisao olha os dois lados: o `e` so fica preso a quantidade quando o lado
 * esquerdo termina em numero/unidade **e** o lado direito comeca com numero ou
 * fracao.
 *
 * ## Virgula, barra e ponto
 * Sao separadores, **menos** entre digitos: "1,5" e "1/2" continuam inteiros.
 * A protecao e feita com marcadores temporarios, devolvidos antes da saida.
 *
 * ## Itens colados por espaco, sem virgula
 * Quem digita rapido encadeia itens por espaco: "bolo 1kg 11,99 arroz 5kg
 * 20,33". Nenhum separador forte existe ali, entao a linha viraria UM item
 * misturado (o ultimo peso com o ultimo preco). A passada
 * [separarAntesDoNomeAposPreco] roda depois do split por separador forte e
 * antes do conectivo `e`: corta ANTES de uma palavra que comeca nome novo
 * quando o token anterior e um preco. E conservadora de proposito - ver o
 * KDoc dela.
 */
object DivisorDeLinha {

    private const val MARCA_VIRGULA = '\u0001'
    private const val MARCA_BARRA = '\u0002'

    /** "c/ 12" e "c/12" viram "com 12" antes de a barra virar separador. */
    private val ABREVIACAO_COM = Regex("""(?<![a-zA-Z0-9])[cC]/\s*""")
    private val DECIMAL_COM_VIRGULA = Regex("""(?<=\d),(?=\d)""")
    private val FRACAO_COM_BARRA = Regex("""(?<=\d)/(?=\d)""")
    private val SEPARADORES = Regex("""[,;+/\n]""")
    private val CONECTIVO_E = Regex("""\s+e\s+também\s+|\s+e\s+tambem\s+|\s+&\s+|\s+e\s+""")

    /** Palavras que indicam fracao logo depois do `e`: "um quilo e meio". */
    private val FRACOES_APOS_E = setOf("meio", "meia", "metade", "pouco", "tanto")

    /** Preposicoes que ligam quantidade e item. */
    val PREPOSICOES_DE = setOf("de", "do", "da", "dos", "das")

    /** Palavras que indicam conteudo de embalagem: "pacote com 2". */
    val PREPOSICOES_COM = setOf("com", "c", "contendo")

    fun dividir(linha: String): List<Segmento> {
        if (linha.isBlank()) return emptyList()

        val protegido = linha
            .replace(ABREVIACAO_COM, "com ")
            .replace(DECIMAL_COM_VIRGULA, MARCA_VIRGULA.toString())
            .replace(FRACAO_COM_BARRA, MARCA_BARRA.toString())

        val porSeparadorForte = protegido.split(SEPARADORES)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // A fronteira "preco -> nome do proximo item" roda no fragmento JA
        // protegido (o decimal pode chegar como "11<MARCA_VIRGULA>99"), antes
        // do `e` e da juncao de quantidades soltas: cada passada cuida do
        // proprio padrao e a ordem acima e a que menos interfere nas outras.
        val porPrecoSeguidoDeNome = porSeparadorForte.flatMap { separarAntesDoNomeAposPreco(it) }

        val porConectivo = porPrecoSeguidoDeNome.flatMap { separarPeloE(it) }
            .map { it.replace(MARCA_VIRGULA, ',').replace(MARCA_BARRA, '/') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return juntarQuantidadesSoltas(porConectivo)
    }

    // =================================================================================
    // Fronteira "preco -> nome do proximo item" (itens colados por espaco)
    // =================================================================================

    /** Classificacao minima de um token dentro da heuristica de colagem. */
    private enum class TipoDeToken {
        /** Dinheiro declarado: "11,99", "20.33" (ou com MARCA_VIRGULA). */
        PRECO,

        /** Quantidade com unidade colada: "1kg", "750g", "2l", "900ml". */
        NUMERO_COM_UNIDADE,

        /** Comeca com letra: candidato a nome (ou a "de", "kg", "pacote"...). */
        PALAVRA,

        /** Puro digito ("3"), fracao ("1/2"), "r$4,99" - nada conclusivo. */
        OUTRO,
    }

    /**
     * Preco isolado no meio de itens colados: "11,99", "3.99". O decimal pode
     * estar protegido por [MARCA_VIRGULA] quando a passada roda (sempre roda:
     * a protecao vem antes), entao o marcador vale como separador decimal.
     * 1-2 casas decimais e o mesmo perfil que [ParserDeLinhaDeCompra] usa para
     * reconhecer dinheiro - os dois lados da fronteira precisam concordar.
     */
    private val REGEX_PRECO_ISOLADO = Regex("""\d{1,4}[.,\u0001]\d{1,2}""")

    /**
     * Quantidade com unidade colada: "1kg", "5kg", "750g", "2l", "900ml". E a
     * EVIDENCIA de que existe outro item adiante - um nome seguido de "numero
     * + unidade" quase sempre e o inicio da medida do proximo produto.
     */
    private val REGEX_NUMERO_COM_UNIDADE = Regex(
        """\d+(?:[.,\u0001]\d+)?(?:kg|g|l|ml|un|dz|cx|pct|fd)""",
        RegexOption.IGNORE_CASE,
    )

    /** Palavra que LIGA quantidade a item: nunca inicia um nome novo. */
    private val PALAVRAS_QUE_NAO_INICIAM_NOME = PREPOSICOES_DE + setOf("e", "com")

    /**
     * Palavras de CONTAINER que casam `Unidade.porTexto` mas comecam nome de
     * item legitimo ("pacote de asa"): "6,49 pacote de asa..." e dois itens.
     * A "kg" de "1,5 kg de carne" NAO esta aqui - ela e medida da quantidade
     * anterior, e o corte ali destruiria o padrao P3.
     */
    private val PALAVRAS_DE_EMBALAGEM_QUE_INICIAM_NOME = setOf(
        "pacote", "pacotes", "caixa", "caixas", "fardo", "fardos",
        "lata", "latas", "garrafa", "garrafas", "saco", "sacos",
        "bandeja", "bandejas",
    )

    private fun classificarToken(token: String): TipoDeToken = when {
        REGEX_PRECO_ISOLADO.matchEntire(token) != null -> TipoDeToken.PRECO
        REGEX_NUMERO_COM_UNIDADE.matchEntire(token) != null -> TipoDeToken.NUMERO_COM_UNIDADE
        token.firstOrNull()?.isLetter() == true -> TipoDeToken.PALAVRA
        else -> TipoDeToken.OUTRO
    }

    /**
     * Divide itens colados por espaco SEM virgula, cortando ANTES de uma
     * palavra que inicia o proximo item.
     *
     * Heuristica de fronteira - um corte antes da palavra `W` so acontece
     * quando TUDO isto vale, junto:
     *
     * 1. o token ANTERIOR a `W` e um PRECO ("... 1kg 11,99 arroz ...");
     * 2. `W` comeca com letra e nao e palavra de ligacao ("de", "do", ...,
     *    "e", "com") nem unidade de medida (`Unidade.porTexto`) - exceto as
     *    palavras de container que iniciam item ("pacote de asa");
     * 3. o RESTO depois de `W` ainda contem um numero+unidade ("5kg") ou um
     *    preco ("20,33") - garantia de que existe OUTRO item a frente, e nao
     *    so um complemento solto.
     *
     * Por que e conservadora: sem preco anterior nao ha corte ("cafe 500g
     * leite" fica junto - listar por espaco sem preco e ilegivel para esta
     * regra); sem evidencia numerica no resto nao ha corte ("sabonete 3,99
     * dove" e UM item com marca); e "1,5 kg de carne" sobra inteiro porque
     * "kg" e unidade e "de" e ligacao. Falso NEGATIVO (nao dividir) devolve a
     * linha de hoje; falso POSITIVO (dividir demais) inventaria item - por
     * isso as condicoes sao conjuntivas e fechadas.
     *
     * Ex.: "bolo de laranja 1kg 11,99 arroz 5kg 20,33" corta antes de "arroz"
     * (anterior "11,99" e preco; resto tem "5kg") e o ciclo repete no
     * fragmento seguinte, cobrindo 3+ itens.
     */
    private fun separarAntesDoNomeAposPreco(fragmento: String): List<String> {
        val tokens = fragmento.split(' ').filter { it.isNotBlank() }
        if (tokens.size < 2) return listOf(fragmento)

        val tipos = tokens.map { classificarToken(it) }
        val cortes = mutableListOf<Int>()
        for (indice in 1 until tokens.size) {
            if (tipos[indice] != TipoDeToken.PALAVRA) continue
            val palavra = tokens[indice]
            val minuscula = palavra.lowercase()
            if (minuscula in PALAVRAS_QUE_NAO_INICIAM_NOME) continue
            val ehUnidade = Unidade.porTexto(palavra) != null &&
                minuscula !in PALAVRAS_DE_EMBALAGEM_QUE_INICIAM_NOME
            if (ehUnidade) continue
            if (tipos[indice - 1] != TipoDeToken.PRECO) continue
            val temItemAdiante = tipos.drop(indice + 1).any {
                it == TipoDeToken.PRECO || it == TipoDeToken.NUMERO_COM_UNIDADE
            }
            if (temItemAdiante) cortes += indice
        }
        if (cortes.isEmpty()) return listOf(fragmento)

        val partes = mutableListOf<String>()
        var inicio = 0
        for (corte in cortes) {
            partes += tokens.subList(inicio, corte).joinToString(" ")
            inicio = corte
        }
        partes += tokens.subList(inicio, tokens.size).joinToString(" ")
        return partes
    }

    /**
     * Separa pelo conectivo `e` quando ele divide dois itens, mantendo junto
     * quando faz parte da quantidade ou do nome.
     */
    private fun separarPeloE(trecho: String): List<String> {
        // Nome composto catalogado ("leite de coco") nunca e dividido.
        if (LexicoDeItens.porNome(trecho) != null) return listOf(trecho)

        val partes = mutableListOf<String>()
        var restante = trecho
        var achado = CONECTIVO_E.find(restante)
        var inicio = 0

        while (achado != null) {
            val esquerda = restante.substring(inicio, achado.range.first)
            val direita = restante.substring(achado.range.last + 1)
            if (ehParteDaQuantidade(esquerda, direita)) {
                // Mantem o "e" colado e procura o proximo.
                achado = CONECTIVO_E.find(restante, achado.range.last + 1)
            } else {
                partes += restante.substring(inicio, achado.range.first).trim()
                restante = direita
                inicio = 0
                achado = CONECTIVO_E.find(restante)
            }
        }
        partes += restante.substring(inicio).trim()
        return partes.filter { it.isNotBlank() }
    }

    /** "2 e 50", "um quilo e meio", "mil e duzentos" -> o `e` nao separa itens. */
    private fun ehParteDaQuantidade(esquerda: String, direita: String): Boolean {
        val palavrasEsquerda = TextoUtil.normalizar(esquerda).split(' ').filter { it.isNotBlank() }
        val palavrasDireita = TextoUtil.normalizar(direita).split(' ').filter { it.isNotBlank() }
        if (palavrasEsquerda.isEmpty() || palavrasDireita.isEmpty()) return false

        val ultima = palavrasEsquerda.last()
        val primeira = palavrasDireita.first()

        val esquerdaTerminaEmNumero = ultima.all { it.isDigit() } ||
            NumeraisPorExtenso.palavrasConhecidas.contains(ultima) ||
            Unidade.porTexto(ultima) != null

        val direitaComecaComNumero = primeira.all { it.isDigit() } ||
            NumeraisPorExtenso.palavrasConhecidas.contains(primeira) ||
            primeira in FRACOES_APOS_E

        // "pouco"/"tanto" so contam como fracao em "um pouco" - ali a direita e
        // quantidade indefinida, e separar criaria um item fantasma.
        return esquerdaTerminaEmNumero && direitaComecaComNumero
    }

    /**
     * Junta o padrao P2: um segmento que so tem quantidade pertence ao item
     * anterior ("ovos, meia duzia").
     */
    private fun juntarQuantidadesSoltas(segmentos: List<String>): List<Segmento> {
        val resultado = mutableListOf<Segmento>()
        segmentos.forEach { texto ->
            if (resultado.isNotEmpty() && soTemQuantidade(texto)) {
                val anterior = resultado.removeAt(resultado.lastIndex)
                resultado += anterior.copy(
                    quantidadeAnexa = listOfNotNull(anterior.quantidadeAnexa, texto).joinToString(" "),
                )
            } else {
                resultado += Segmento(texto)
            }
        }
        return resultado
    }

    /**
     * O segmento e **so** uma expressao de quantidade?
     *
     * Apelido de animal nao conta: "ovos, cachorro" nao vira "12 ovos" em
     * silencio - vira sugestao (G1). Regra A nao abre excecao por pontuacao.
     */
    fun soTemQuantidade(texto: String): Boolean {
        val palavras = TextoUtil.normalizar(texto).split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return false
        var indice = 0
        var achouAlgo = false
        while (indice < palavras.size) {
            val restante = palavras.drop(indice)
            val token = TabelaDeQuantidades.casarNoInicio(restante)
            if (token != null && token.first.origemRepertorio != OrigemRepertorio.ANIMAL) {
                indice += token.second
                achouAlgo = true
                continue
            }
            val numeral = NumeraisPorExtenso.consumirNoInicio(restante)
            if (numeral != null) {
                indice += numeral.second
                achouAlgo = true
                continue
            }
            val palavra = palavras[indice]
            when {
                palavra.all { it.isDigit() } -> {
                    achouAlgo = true
                    indice++
                }
                Unidade.porTexto(palavra) != null -> indice++
                palavra in PREPOSICOES_DE -> indice++
                palavra in FRACOES_APOS_E -> {
                    achouAlgo = true
                    indice++
                }
                else -> return false
            }
        }
        return achouAlgo
    }

    /**
     * Qual dos quatro padroes o segmento usa.
     *
     * P4 e testado primeiro: e o mais comum na digitacao rapida e o que
     * sustenta a meta de 60 itens em 3 minutos.
     */
    fun padraoDe(segmento: Segmento): PadraoDePosicao {
        if (segmento.quantidadeAnexa != null) return PadraoDePosicao.P2
        val palavras = TextoUtil.normalizar(segmento.texto).split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return PadraoDePosicao.SEM_QUANTIDADE

        val temLetraNoInicio = palavras.first().any { it.isLetter() } &&
            TabelaDeQuantidades.casarNoInicio(palavras) == null &&
            NumeraisPorExtenso.consumirNoInicio(palavras) == null

        val ultima = palavras.last()
        val penultima = palavras.getOrNull(palavras.size - 2)
        val terminaEmQuantidade = ultima.all { it.isDigit() } ||
            (Unidade.porTexto(ultima) != null && penultima?.any { it.isDigit() } == true)

        if (temLetraNoInicio && terminaEmQuantidade) return PadraoDePosicao.P4

        val numeroNoInicio = palavras.first().any { it.isDigit() } ||
            NumeraisPorExtenso.consumirNoInicio(palavras) != null
        val tokenNoInicio = TabelaDeQuantidades.casarNoInicio(palavras)
        val temDe = palavras.any { it in PREPOSICOES_DE }

        return when {
            numeroNoInicio && palavras.any { Unidade.porTexto(it) != null } && temDe -> PadraoDePosicao.P3
            tokenNoInicio != null && temDe -> PadraoDePosicao.P1
            tokenNoInicio != null || numeroNoInicio -> PadraoDePosicao.P1
            else -> PadraoDePosicao.SEM_QUANTIDADE
        }
    }
}

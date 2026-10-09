package br.com.comprix.domain.lista

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.parser.DivisorDeLinha
import br.com.comprix.domain.unidade.ConversorDeUnidades
import br.com.comprix.util.Constantes
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Interpreta a linha unica da adicao rapida (Secao 3.2 / jornada 2 - 60 itens
 * em menos de 3 minutos).
 *
 * O usuario digita naturalmente e o app separa os pedacos:
 *
 * ```
 * "2 arroz 5kg 24,90"   -> 2 embalagens, "Arroz", 5 kg,  R$ 24,90
 * "leite"               -> 1 embalagem,  "Leite", 1 un,  sem preco
 * "3x cerveja 350ml"    -> 3 embalagens, "Cerveja", 350 mL
 * "fardo c/ 12 agua 1,5l 19,90" -> pack de 12, "Água", 1,5 L, R$ 19,90
 * ```
 *
 * ## Regras de desempate
 * - O numero **no inicio** da linha e a quantidade de embalagens; o numero
 *   **colado a uma unidade** e o peso/volume; o numero **no fim com centavos
 *   ou R$** e o preco.
 * - Sem numero inicial, quantidade = 1 (o caso mais comum: "leite").
 * - Nunca falha: o que nao for reconhecido vira parte do nome.
 */
object InterpretadorDeAdicaoRapida {

    data class LinhaInterpretada(
        val nome: String,
        val quantidade: BigDecimal,
        val unidade: Unidade,
        val pesoOuVolume: BigDecimal?,
        val itensPorKit: Int?,
        val preco: BigDecimal?,
    ) {
        val ehKit: Boolean get() = (itensPorKit ?: 1) > 1
    }

    private val REGEX_QUANTIDADE_INICIAL = Regex("""^\s*(\d{1,3})\s*(?:x|un|unid|unidades?)?\s+(?=\S)""")
    // (?<!\d) evita ler "99999,00" como 9999,00: digito a mais nao pode virar
    // preco truncado - preferivel nao reconhecer preco nenhum.
    private val REGEX_PRECO_FINAL = Regex("""(?:r\$\s*)?(?<!\d)(\d{1,4}[.,]\d{2})\s*$""")
    private val REGEX_PRECO_MARCADO = Regex("""r\$\s*(\d{1,4}(?:[.,]\d{1,2})?)""")

    /** Preco encontrado no fim da linha + o texto que sobra sem ele. */
    data class PrecoNaLinha(val preco: BigDecimal?, val restante: String)

    /**
     * Separa **so** o preco da linha, devolvendo o resto intacto.
     *
     * Existe para que a adicao por texto livre tenha um unico interpretador de
     * quantidade - o `domain/parser` - sem perder o atalho de digitar o preco
     * junto ("arroz 5kg 24,90"). Aqui nao se interpreta quantidade nenhuma.
     *
     * Em linha MULTI-item ("uva 1kg 3,99, limao 1kg 6,50") o preco NAO e
     * extraido aqui: devolve `[preco = null, restante intacto]` e quem extrai
     * cada preco e o parser, por segmento. Se esta funcao arrancasse o numero
     * do fim, o ULTIMO item da linha ficaria sem valor - e o chamador so usa
     * o preco extraido quando a linha vira um unico item, entao nada se perde.
     * Linha single-item segue exatamente como antes.
     */
    fun extrairPreco(texto: String): PrecoNaLinha {
        var restante = texto.trim()
        if (restante.isBlank()) return PrecoNaLinha(null, restante)

        // Mesma divisao que o parser usa (protege "3,99" da virgula separadora):
        // mais de um segmento = multi-item, preco por segmento manda.
        if (DivisorDeLinha.dividir(restante).size > 1) {
            return PrecoNaLinha(null, restante)
        }

        REGEX_PRECO_MARCADO.find(TextoUtil.normalizarLeve(restante))?.let { achado ->
            val valor = TextoUtil.paraDecimal(achado.groupValues[1])
            if (valor != null && valor <= BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)) {
                return PrecoNaLinha(valor, restante.removeRange(achado.range).trim(' ', ',', '-'))
            }
        }
        REGEX_PRECO_FINAL.find(TextoUtil.normalizarLeve(restante))?.let { achado ->
            val valor = TextoUtil.paraDecimal(achado.groupValues[1])
            if (valor != null && valor <= BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)) {
                return PrecoNaLinha(valor, restante.removeRange(achado.range).trim(' ', ',', '-'))
            }
        }
        return PrecoNaLinha(null, restante)
    }

    /**
     * @param texto linha crua digitada pelo usuario.
     * @return os campos separados; nome nunca vem vazio (cai no texto original).
     */
    fun interpretar(texto: String): LinhaInterpretada? {
        val original = texto.trim()
        if (original.isBlank()) return null

        var restante = TextoUtil.normalizarLeve(original)

        // 1. Preco: primeiro "R$ x", senao o numero com centavos no fim da linha.
        var preco: BigDecimal? = null
        REGEX_PRECO_MARCADO.find(restante)?.let { achado ->
            preco = TextoUtil.paraDecimal(achado.groupValues[1])
            restante = restante.removeRange(achado.range).trim()
        }
        if (preco == null) {
            REGEX_PRECO_FINAL.find(restante)?.let { achado ->
                val candidato = TextoUtil.paraDecimal(achado.groupValues[1])
                if (candidato != null && candidato <= BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)) {
                    preco = candidato
                    restante = restante.removeRange(achado.range).trim()
                }
            }
        }

        // 2. Quantidade de embalagens no inicio ("2 arroz", "3x cerveja").
        var quantidade = BigDecimal.ONE
        REGEX_QUANTIDADE_INICIAL.find(restante)?.let { achado ->
            val valor = achado.groupValues[1].toBigDecimalOrNull()
            if (valor != null && valor.signum() > 0 && valor <= BigDecimal("999")) {
                quantidade = valor
                restante = restante.removeRange(achado.range).trim()
            }
        }

        // 3. Peso/volume e multipack dentro do que sobrou.
        val conteudo = ConversorDeUnidades.interpretarTexto(restante)
        val nomeLimpo = ConversorDeUnidades.nomeSemQuantidade(restante)
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ',', '.')

        val nome = if (nomeLimpo.count { it.isLetter() } >= 2) {
            TextoUtil.capitalizarTitulo(nomeLimpo)
        } else {
            // A linha era so numeros ("2 500g"): preserva o texto original.
            TextoUtil.capitalizarTitulo(original)
        }

        return LinhaInterpretada(
            nome = nome,
            quantidade = quantidade,
            unidade = conteudo?.unidade ?: Unidade.UNIDADE,
            pesoOuVolume = conteudo?.quantidade,
            itensPorKit = conteudo?.itensPorEmbalagem,
            preco = preco,
        )
    }

    /** Interpreta varias linhas de uma vez (colar lista inteira). */
    fun interpretarVarias(texto: String): List<LinhaInterpretada> =
        texto.split('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { interpretar(it) }
}

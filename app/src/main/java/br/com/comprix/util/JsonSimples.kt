package br.com.comprix.util

/**
 * Serializador JSON minimo (mapa plano de String para String).
 *
 * Por que nao usar kotlinx.serialization/Gson: o app precisa de JSON em
 * exatamente dois lugares - a tabela nutricional guardada numa coluna do Room e
 * o arquivo de backup `.cbk`. Ambos sao mapas planos. Trocar isso por uma
 * biblioteca custaria ~300 KB no APK e um processador de anotacoes a mais no
 * build, sem ganho real.
 *
 * Suporta: escape de aspas, barra invertida e quebras de linha; acentos passam
 * intactos (UTF-8). Nao suporta (e nao precisa): aninhamento, numeros tipados,
 * booleanos ou null como valor - tudo e string.
 */
object JsonSimples {

    /** {"chave":"valor","outra":"valor"} - chaves com valor null sao omitidas. */
    fun paraJson(dados: Map<String, String?>): String = buildString {
        append('{')
        var primeiro = true
        dados.forEach { (chave, valor) ->
            if (valor == null) return@forEach
            if (!primeiro) append(',')
            primeiro = false
            append('"').append(escapar(chave)).append('"')
            append(':')
            append('"').append(escapar(valor)).append('"')
        }
        append('}')
    }

    /** [{"a":"1"},{"a":"2"}] */
    fun listaParaJson(itens: List<Map<String, String?>>): String =
        itens.joinToString(",", prefix = "[", postfix = "]") { paraJson(it) }

    /** Le um objeto plano. Texto invalido devolve mapa vazio (nunca lanca). */
    fun deJson(texto: String?): Map<String, String> {
        if (texto.isNullOrBlank()) return emptyMap()
        val conteudo = texto.trim()
        if (!conteudo.startsWith("{") || !conteudo.endsWith("}")) return emptyMap()

        val resultado = LinkedHashMap<String, String>()
        var i = 1
        while (i < conteudo.length - 1) {
            when (conteudo[i]) {
                '"' -> {
                    val (chave, depoisDaChave) = lerTexto(conteudo, i)
                    var j = depoisDaChave
                    while (j < conteudo.length && conteudo[j] != ':') j++
                    j++
                    while (j < conteudo.length && conteudo[j].isWhitespace()) j++
                    if (j >= conteudo.length || conteudo[j] != '"') return resultado
                    val (valor, depoisDoValor) = lerTexto(conteudo, j)
                    resultado[chave] = valor
                    i = depoisDoValor
                }
                else -> i++
            }
        }
        return resultado
    }

    /** Le uma lista de objetos planos. Texto invalido devolve lista vazia. */
    fun listaDeJson(texto: String?): List<Map<String, String>> {
        if (texto.isNullOrBlank()) return emptyList()
        val conteudo = texto.trim()
        if (!conteudo.startsWith("[") || !conteudo.endsWith("]")) return emptyList()

        val itens = mutableListOf<Map<String, String>>()
        var profundidade = 0
        var inicio = -1
        var dentroDeTexto = false
        var escapado = false

        conteudo.forEachIndexed { indice, caractere ->
            when {
                escapado -> escapado = false
                caractere == '\\' && dentroDeTexto -> escapado = true
                caractere == '"' -> dentroDeTexto = !dentroDeTexto
                dentroDeTexto -> Unit
                caractere == '{' -> {
                    if (profundidade == 0) inicio = indice
                    profundidade++
                }
                caractere == '}' -> {
                    profundidade--
                    if (profundidade == 0 && inicio >= 0) {
                        itens += deJson(conteudo.substring(inicio, indice + 1))
                        inicio = -1
                    }
                }
            }
        }
        return itens
    }

    /** Le um literal entre aspas a partir de [posicaoAspasInicial]; devolve o texto e o indice seguinte. */
    private fun lerTexto(texto: String, posicaoAspasInicial: Int): Pair<String, Int> {
        val construtor = StringBuilder()
        var i = posicaoAspasInicial + 1
        while (i < texto.length) {
            val caractere = texto[i]
            if (caractere == '\\' && i + 1 < texto.length) {
                when (val proximo = texto[i + 1]) {
                    'n' -> construtor.append('\n')
                    'r' -> construtor.append('\r')
                    't' -> construtor.append('\t')
                    else -> construtor.append(proximo)
                }
                i += 2
                continue
            }
            if (caractere == '"') return construtor.toString() to (i + 1)
            construtor.append(caractere)
            i++
        }
        return construtor.toString() to i
    }

    private fun escapar(texto: String): String = buildString {
        texto.forEach { caractere ->
            when (caractere) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(caractere)
            }
        }
    }
}

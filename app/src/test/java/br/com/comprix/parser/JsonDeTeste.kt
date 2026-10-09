package br.com.comprix.parser

/**
 * Leitor de JSON minimo, **exclusivo dos testes**.
 *
 * O app nao embarca parser de JSON generico (o backup usa o
 * `JsonSimples`, de pares planos). O corpus de casos do parser, porem, e
 * aninhado, e trazer uma biblioteca so para o teste aumentaria o tempo de
 * build sem beneficio para o aparelho. Sao ~90 linhas, so leitura, sem
 * dependencia externa - e nada disso entra no APK.
 */
object JsonDeTeste {

    fun ler(texto: String): Any? = Leitor(texto).let { leitor ->
        val valor = leitor.valor()
        leitor.pularEspacos()
        valor
    }

    @Suppress("UNCHECKED_CAST")
    fun objeto(valor: Any?): Map<String, Any?> = valor as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun lista(valor: Any?): List<Any?> = valor as List<Any?>

    private class Leitor(private val texto: String) {
        private var posicao = 0

        fun pularEspacos() {
            while (posicao < texto.length && texto[posicao].isWhitespace()) posicao++
        }

        fun valor(): Any? {
            pularEspacos()
            return when (val atual = texto[posicao]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> cadeia()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (atual == '-' || atual.isDigit()) numero() else erro("valor inesperado")
            }
        }

        private fun objeto(): Map<String, Any?> {
            val mapa = LinkedHashMap<String, Any?>()
            posicao++ // {
            pularEspacos()
            if (texto[posicao] == '}') { posicao++; return mapa }
            while (true) {
                pularEspacos()
                val chave = cadeia()
                pularEspacos()
                if (texto[posicao] != ':') erro("esperado ':'")
                posicao++
                mapa[chave] = valor()
                pularEspacos()
                when (texto[posicao]) {
                    ',' -> posicao++
                    '}' -> { posicao++; return mapa }
                    else -> erro("esperado ',' ou '}'")
                }
            }
        }

        private fun lista(): List<Any?> {
            val itens = mutableListOf<Any?>()
            posicao++ // [
            pularEspacos()
            if (texto[posicao] == ']') { posicao++; return itens }
            while (true) {
                itens += valor()
                pularEspacos()
                when (texto[posicao]) {
                    ',' -> posicao++
                    ']' -> { posicao++; return itens }
                    else -> erro("esperado ',' ou ']'")
                }
            }
        }

        private fun cadeia(): String {
            if (texto[posicao] != '"') erro("esperado '\"'")
            posicao++
            val saida = StringBuilder()
            while (texto[posicao] != '"') {
                val atual = texto[posicao]
                if (atual == '\\') {
                    posicao++
                    when (val escapado = texto[posicao]) {
                        'n' -> saida.append('\n')
                        't' -> saida.append('\t')
                        'r' -> saida.append('\r')
                        'b' -> saida.append('\b')
                        'f' -> saida.append('\u000C')
                        'u' -> {
                            saida.append(texto.substring(posicao + 1, posicao + 5).toInt(16).toChar())
                            posicao += 4
                        }
                        else -> saida.append(escapado)
                    }
                } else {
                    saida.append(atual)
                }
                posicao++
            }
            posicao++
            return saida.toString()
        }

        private fun numero(): Any {
            val inicio = posicao
            while (posicao < texto.length && (texto[posicao].isDigit() || texto[posicao] in "-+.eE")) posicao++
            val bruto = texto.substring(inicio, posicao)
            return bruto.toIntOrNull() ?: bruto.toDouble()
        }

        private fun literal(palavra: String, resultado: Any?): Any? {
            if (!texto.startsWith(palavra, posicao)) erro("literal invalido")
            posicao += palavra.length
            return resultado
        }

        private fun erro(mensagem: String): Nothing =
            throw IllegalArgumentException("JSON invalido na posicao $posicao: $mensagem")
    }
}

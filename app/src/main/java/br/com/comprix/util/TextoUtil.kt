package br.com.comprix.util

import java.math.BigDecimal
import java.text.Normalizer

/**
 * Utilitarios de texto usados em todo o app: normalizacao para comparacao,
 * leitura de numeros no formato brasileiro e validacao de codigo de barras.
 */
object TextoUtil {

    private val REGEX_ACENTOS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val REGEX_NAO_ALFANUMERICO = "[^a-z0-9%\\s]".toRegex()
    private val REGEX_ESPACOS = "\\s+".toRegex()

    /** Remove acentos preservando as letras ("acucar" <- "açúcar"). */
    fun removerAcentos(texto: String): String =
        REGEX_ACENTOS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "")

    /**
     * Normalizacao completa, para comparacao: minusculas, sem acento, sem
     * pontuacao, espacos colapsados. E a chave usada para "lembrar" categorias,
     * casar palavras do dicionario e deduplicar produtos.
     */
    fun normalizar(texto: String): String =
        REGEX_ESPACOS.replace(
            REGEX_NAO_ALFANUMERICO.replace(removerAcentos(texto).lowercase(), " "),
            " ",
        ).trim()

    /**
     * Normalizacao LEVE, usada no pipeline de OCR: minusculas e sem acento,
     * mas PRESERVANDO a pontuacao.
     *
     * E indispensavel para o leitor de rotulos. A normalizacao completa apagaria
     * justamente os caracteres que carregam a informacao: "R$ 24,90" viraria
     * "r 24 90" e "10/12/2026" viraria "10 12 2026", e nenhum preco ou data
     * seria reconhecido. (Esse bug existiu na primeira versao e foi pego pelos
     * testes do ExtratorDeRotulo.)
     */
    fun normalizarLeve(texto: String): String =
        REGEX_ESPACOS.replace(removerAcentos(texto).lowercase(), " ").trim()

    /** Quebra o texto normalizado em palavras. */
    fun tokens(texto: String): List<String> =
        normalizar(texto).split(" ").filter { it.isNotBlank() }

    /** "arroz branco tipo 1" -> "Arroz Branco Tipo 1" (apenas para exibicao). */
    fun capitalizarTitulo(texto: String): String =
        texto.trim().split(" ").joinToString(" ") { palavra ->
            when {
                palavra.isBlank() -> palavra
                palavra.length <= 2 && palavra.lowercase() in PREPOSICOES -> palavra.lowercase()
                else -> palavra.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

    private val PREPOSICOES = setOf("de", "da", "do", "e", "em", "a", "o")

    /**
     * Converte texto digitado/lido para numero aceitando os formatos brasileiros
     * mais comuns: "12,90", "1.299,90", "R$ 7,50", "7.50".
     *
     * @return o valor, ou null se nao houver numero reconhecivel.
     */
    fun paraDecimal(texto: String?): BigDecimal? {
        if (texto.isNullOrBlank()) return null
        var limpo = texto.replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace("\u00A0", "")
            .trim()
        if (limpo.isEmpty()) return null

        val temVirgula = limpo.contains(',')
        val temPonto = limpo.contains('.')
        limpo = when {
            // "1.299,90": ponto e milhar, virgula e decimal.
            temVirgula && temPonto -> limpo.replace(".", "").replace(',', '.')
            temVirgula -> limpo.replace(',', '.')
            // "1.299" (milhar) vs "7.50" (decimal): 3 digitos apos o ponto = milhar.
            temPonto && limpo.substringAfterLast('.').length == 3 &&
                limpo.substringBefore('.').length <= 3 && !limpo.endsWith(".") ->
                limpo.replace(".", "")
            else -> limpo
        }
        limpo = limpo.filter { it.isDigit() || it == '.' || it == '-' }
        if (limpo.isEmpty() || limpo == "." || limpo == "-") return null
        return try {
            BigDecimal(limpo)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Distancia de edicao (Levenshtein) - usada para sugerir produtos parecidos. */
    fun distanciaEdicao(a: String, b: String): Int {
        val na = normalizar(a)
        val nb = normalizar(b)
        if (na == nb) return 0
        if (na.isEmpty()) return nb.length
        if (nb.isEmpty()) return na.length

        var anterior = IntArray(nb.length + 1) { it }
        var atual = IntArray(nb.length + 1)
        for (i in 1..na.length) {
            atual[0] = i
            for (j in 1..nb.length) {
                val custo = if (na[i - 1] == nb[j - 1]) 0 else 1
                atual[j] = minOf(atual[j - 1] + 1, anterior[j] + 1, anterior[j - 1] + custo)
            }
            val troca = anterior
            anterior = atual
            atual = troca
        }
        return anterior[nb.length]
    }

    /** 0f (nada a ver) a 1f (identico). */
    fun similaridade(a: String, b: String): Float {
        val maior = maxOf(normalizar(a).length, normalizar(b).length)
        if (maior == 0) return 1f
        return 1f - distanciaEdicao(a, b).toFloat() / maior
    }

    /** Verdadeiro se o texto normalizado contem a palavra inteira (nao pedaco de outra). */
    fun contemPalavra(textoNormalizado: String, palavraNormalizada: String): Boolean {
        if (palavraNormalizada.isBlank()) return false
        if (palavraNormalizada.contains(" ")) return textoNormalizado.contains(palavraNormalizada)
        return tokensSeparados(textoNormalizado).any { it == palavraNormalizada }
    }

    private fun tokensSeparados(textoNormalizado: String): List<String> =
        textoNormalizado.split(" ", "-", "/").filter { it.isNotBlank() }

    /**
     * Valida o digito verificador de um EAN-8 / EAN-13 / UPC-A.
     * Evita aceitar "codigos" fantasiosos que o OCR leu por engano num rotulo.
     */
    fun codigoDeBarrasValido(codigo: String?): Boolean {
        if (codigo == null) return false
        val digitos = codigo.filter { it.isDigit() }
        if (digitos.length !in setOf(8, 12, 13, 14)) return false

        val corpo = digitos.dropLast(1)
        val verificador = digitos.last().digitToInt()
        // Da direita para a esquerda, pesos alternados 3 e 1.
        var soma = 0
        corpo.reversed().forEachIndexed { indice, caractere ->
            val peso = if (indice % 2 == 0) 3 else 1
            soma += caractere.digitToInt() * peso
        }
        return (10 - soma % 10) % 10 == verificador
    }
}

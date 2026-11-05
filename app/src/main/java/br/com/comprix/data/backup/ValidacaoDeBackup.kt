package br.com.comprix.data.backup

import br.com.comprix.util.JsonSimples
import java.security.MessageDigest

private const val CABECALHO_V1 = "#COMPRIX-BACKUP v1"
private const val CABECALHO_V2 = "#COMPRIX-BACKUP v2"
private const val MARCA_TABELA = "#TABELA "
private val TABELAS_ESSENCIAIS = setOf(
    "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos", "compras",
)

/**
 * Valida o formato canonico dos backups. O v2 usa SHA-256 para detectar
 * corrupcao acidental; v1 nao tem rodape e nao detecta todo truncamento.
 * Nao altera o banco; um erro em qualquer linha invalida o arquivo inteiro.
 * O checksum nao autentica a origem e nao protege contra alteracao deliberada.
 */
internal fun lerBackup(linhas: List<String>): Map<String, List<Map<String, String>>>? {
    return when (linhas.firstOrNull()) {
        CABECALHO_V1 -> lerBackupV1(linhas)
        CABECALHO_V2 -> {
            val rodape = linhas.lastOrNull() ?: return null
            if (!rodape.matches(Regex("#SHA256 [0-9a-f]{64}"))) return null
            val corpo = linhas.dropLast(1)
            val digest = MessageDigest.getInstance("SHA-256")
            corpo.forEach { linha ->
                digest.update(linha.toByteArray(Charsets.UTF_8))
                digest.update('\n'.code.toByte())
            }
            val calculado = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (rodape != "#SHA256 $calculado") return null
            val tabelas = lerTabelas(corpo) ?: return null
            if ("historico_precos" !in tabelas) return null
            tabelas
        }
        else -> null
    }
}

internal fun lerBackupV1(linhas: List<String>): Map<String, List<Map<String, String>>>? {
    if (linhas.firstOrNull()?.trim() != CABECALHO_V1) return null
    return lerTabelas(linhas)
}

private fun lerTabelas(linhas: List<String>): Map<String, List<Map<String, String>>>? {
    val tabelas = linkedMapOf<String, MutableList<Map<String, String>>>()
    var atual: MutableList<Map<String, String>>? = null
    var nomeAtual: String? = null
    val chavesVistas = mutableSetOf<Pair<String, String>>()
    for (linha in linhas.drop(1)) {
        val texto = linha.trim()
        when {
            texto.isEmpty() -> Unit
            texto.startsWith(MARCA_TABELA) -> {
                val nome = texto.removePrefix(MARCA_TABELA).trim()
                if (nome.isEmpty() || nome in tabelas) return null
                atual = mutableListOf()
                nomeAtual = nome
                tabelas[nome] = atual
            }
            texto.startsWith("{") -> {
                val destino = atual ?: return null
                val dados = JsonSimples.deJson(texto)
                // deJson e deliberadamente tolerante; comparar com a escrita
                // canonica impede que aceite um objeto parcial ou malformado.
                if (dados.isEmpty() || JsonSimples.paraJson(dados) != texto) return null
                val nome = nomeAtual ?: return null
                if (!registroPossuiIdentidadeValida(nome, dados)) return null
                val chave = when (nome) {
                    "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos",
                    "compras", "historico_precos", "lixeira", "alergias_customizadas" -> dados["id"]?.toLongOrNull()?.toString()
                    "memoria_categorias" -> dados["nome"]
                    "decisoes_parser" -> dados["termo"]
                    else -> null
                }
                if (chave != null && !chavesVistas.add(nome to chave)) return null
                destino.add(dados)
            }
            else -> return null
        }
    }
    if (!tabelas.keys.containsAll(TABELAS_ESSENCIAIS)) return null
    return tabelas
}

/** Valida identidades usadas para restaurar vinculos sem inventar IDs padrao. */
private fun registroPossuiIdentidadeValida(tabela: String, dados: Map<String, String>): Boolean {
    fun id(chave: String) = dados[chave]?.toLongOrNull()?.let { it > 0 } == true
    return when (tabela) {
        "categorias", "produtos", "estabelecimentos", "listas", "compras", "lixeira", "alergias_customizadas" -> id("id")
        "itens" -> id("id") && id("lista") && id("prod")
        "precos" -> id("id") && id("item") && id("loja")
        "historico_precos" -> id("id") && id("prod") && id("loja")
        "memoria_categorias" -> !dados["nome"].isNullOrBlank() && id("cat")
        "decisoes_parser" -> !dados["termo"].isNullOrBlank()
        else -> true // secoes adicionadas em outras versoes nao sao descartadas
    }
}

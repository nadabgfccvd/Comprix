package br.com.comprix.data.backup

import br.com.comprix.util.JsonSimples

private const val CABECALHO = "#COMPRIX-BACKUP v1"
private const val MARCA_TABELA = "#TABELA "
private val TABELAS_ESSENCIAIS = setOf(
    "categorias", "produtos", "estabelecimentos", "listas", "itens", "precos", "compras",
)

/**
 * Aceita o formato canonico emitido pelo exportador v1. O formato antigo nao
 * contem rodape nem checksum: nao e possivel detectar todo arquivo truncado.
 * Nao altera o banco; um erro em qualquer linha invalida o arquivo inteiro.
 */
internal fun lerBackupV1(linhas: List<String>): Map<String, List<Map<String, String>>>? {
    if (linhas.firstOrNull()?.trim() != CABECALHO) return null
    val tabelas = linkedMapOf<String, MutableList<Map<String, String>>>()
    var atual: MutableList<Map<String, String>>? = null
    for (linha in linhas.drop(1)) {
        val texto = linha.trim()
        when {
            texto.isEmpty() -> Unit
            texto.startsWith(MARCA_TABELA) -> {
                val nome = texto.removePrefix(MARCA_TABELA).trim()
                if (nome.isEmpty() || nome in tabelas) return null
                atual = mutableListOf()
                tabelas[nome] = atual
            }
            texto.startsWith("{") -> {
                val destino = atual ?: return null
                val dados = JsonSimples.deJson(texto)
                // deJson e deliberadamente tolerante; comparar com a escrita
                // canonica impede que aceite um objeto parcial ou malformado.
                if (dados.isEmpty() || JsonSimples.paraJson(dados) != texto) return null
                destino.add(dados)
            }
            else -> return null
        }
    }
    if (!tabelas.keys.containsAll(TABELAS_ESSENCIAIS)) return null
    return tabelas
}

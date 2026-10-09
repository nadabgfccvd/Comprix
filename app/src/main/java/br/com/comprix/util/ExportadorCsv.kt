package br.com.comprix.util

import br.com.comprix.domain.modelo.CompraFinalizada

/**
 * Gerador de CSV do historico de compras - funcao PURA, sem Android, feita
 * para teste de JVM e reuso fora da tela de historico.
 *
 * ## Decisoes de formato
 *
 * - **BOM (`\uFEFF`)** na primeira posicao: sem ele o Excel abre o arquivo como
 *   ANSI e os acentos se corrompem no caminho. Com o BOM, o Excel reconhece
 *   UTF-8 mesmo com o encoding nao declarado.
 * - **Separador `;`**: o Excel/Calc em locale pt-BR lista a virgula como
 *   separador decimal; CSV com virgula de campo viraria uma unica coluna.
 * - **Numeros** em formato brasileiro com duas casas ("1234,56"), via
 *   [Formatadores.moedaSemSimbolo] - o mesmo padrao que a pessoa ve no app.
 * - **Escape** estilo RFC 4180, minimo e seguro: somente quando o campo
 *   contem `;`, aspas ou quebra de linha ele e envolto em aspas duplas, e
 *   cada aspa interna e dobrada. Campos limpos nao ganham aspas (arquivo
 *   menor e legivel ate num editor de texto).
 */
object ExportadorCsv {

    const val CABECALHO = "data;lista;loja;itens;total;economia"

    private const val BOM = "\uFEFF"

    /** Cabecalho + uma linha por compra, na ordem recebida. */
    fun comprasCsv(compras: List<CompraFinalizada>): String = buildString {
        append(BOM)
        appendLine(CABECALHO)
        compras.forEach { compra ->
            appendLine(linhaDaCompra(compra))
        }
    }

    /** Uma linha do CSV; separada da montagem do arquivo para ficar testavel isolada. */
    private fun linhaDaCompra(compra: CompraFinalizada): String = listOf(
        Formatadores.data(compra.data),
        compra.nomeLista,
        compra.descricaoEstabelecimento,
        compra.quantidadeItens.toString(),
        Formatadores.moedaSemSimbolo(compra.totalPago),
        Formatadores.moedaSemSimbolo(compra.economia),
    ).joinToString(";") { escapar(it) }

    /**
     * Envolve o campo em aspas apenas quando preciso, dobrando as aspas
     * internas - assim "Mercado; Atacado" nao quebra a linha ao meio.
     */
    private fun escapar(campo: String): String {
        val precisa = campo.contains(';') || campo.contains('"') ||
            campo.contains('\n') || campo.contains('\r')
        if (!precisa) return campo
        return "\"" + campo.replace("\"", "\"\"") + "\""
    }
}

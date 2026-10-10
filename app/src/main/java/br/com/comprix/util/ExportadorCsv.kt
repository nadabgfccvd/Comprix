package br.com.comprix.util

import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.PrecoDoHistorico
import java.math.BigDecimal

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

    const val CABECALHO_PRECOS = "produto;loja;preco;quantidade;data"

    /**
     * Cabecalho + uma linha por anotacao de preco do acervo, na ordem
     * recebida (o repositorio ja entrega do mais recente para o mais antigo).
     * Mesmas decisoes de formato do [comprasCsv]: BOM, `;` e dinheiro pt-BR.
     */
    fun precosCsv(linhas: List<PrecoDoHistorico>): String = buildString {
        append(BOM)
        appendLine(CABECALHO_PRECOS)
        linhas.forEach { linha ->
            appendLine(linhaDoPreco(linha))
        }
    }

    /** Uma linha do CSV de compras; separada da montagem do arquivo para ficar testavel isolada. */
    private fun linhaDaCompra(compra: CompraFinalizada): String = listOf(
        Formatadores.data(compra.data),
        compra.nomeLista,
        compra.descricaoEstabelecimento,
        compra.quantidadeItens.toString(),
        Formatadores.moedaSemSimbolo(compra.totalPago),
        Formatadores.moedaSemSimbolo(compra.economia),
    ).joinToString(";") { escapar(it) }

    /** Uma linha do CSV de precos; quantidade vazia sai como `-` para a coluna nao sumir no Calc. */
    private fun linhaDoPreco(linha: PrecoDoHistorico): String = listOf(
        linha.produto,
        linha.loja,
        Formatadores.moedaSemSimbolo(linha.preco),
        linha.quantidade.ifBlank { "-" },
        Formatadores.data(linha.quando),
    ).joinToString(";") { escapar(it) }

    /**
     * Uma linha da matriz de comparacao ja achatada para CSV: descricao,
     * detalhe de embalagem, um preco por loja (na mesma ordem de
     * [matrizCsv]) e o indice da loja de menor preco, ou null se ninguem
     * respondeu. Funcao PURA: montada pela tela a partir da matriz viva.
     */
    data class LinhaDaMatrizCsv(
        val descricao: String,
        val detalhe: String,
        val precoPorLoja: List<BigDecimal?>,
        val indiceDoMelhor: Int? = null,
    )

    /**
     * CSV da matriz de comparacao: uma linha por produto, uma coluna por
     * loja - a tabela inteira da tela "Comparar estabelecimentos" em
     * planilha. Mesmas decisoes de formato das outras exportacoes: BOM, `;`
     * e dinheiro pt-BR. Celula sem preco sai como `-`; a coluna final
     * "melhor loja" repete o nome do mercado vencedor da linha, para quem
     * ordenar por outra coluna nao perder a resposta.
     */
    fun matrizCsv(
        nomesDasLojas: List<String>,
        linhas: List<LinhaDaMatrizCsv>,
    ): String = buildString {
        append(BOM)
        appendLine(
            (listOf("produto", "detalhe") + nomesDasLojas.map { escapar(it) } + listOf("melhor loja"))
                .joinToString(";"),
        )
        linhas.forEach { linha ->
            val celulas = linha.precoPorLoja.map { preco ->
                if (preco == null || preco.signum() <= 0) "-" else Formatadores.moedaSemSimbolo(preco)
            }
            val melhor = linha.indiceDoMelhor?.let { nomesDasLojas.getOrNull(it) } ?: "-"
            appendLine(
                (listOf(linha.descricao, linha.detalhe.ifBlank { "-" }) + celulas + listOf(melhor))
                    .joinToString(";") { escapar(it) },
            )
        }
    }

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

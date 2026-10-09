package br.com.comprix.domain.modelo

import java.math.BigDecimal

/**
 * Estruturas de saida das regras de negocio (motor de precos, finalizacao,
 * historico). Sao imutaveis e sem dependencia de Android: a interface apenas
 * desenha o que chega aqui.
 */

// =====================================================================================
// Kit x avulso
// =====================================================================================

/**
 * Uma forma de comprar o mesmo produto ("fardo com 12", "lata 350 mL").
 *
 * @property quantidade conteudo de UMA embalagem.
 * @property itensPorEmbalagem unidades dentro do pack, quando houver.
 */
data class OpcaoDeCompra(
    val rotulo: String,
    val preco: BigDecimal,
    val quantidade: BigDecimal,
    val unidade: Unidade,
    val itensPorEmbalagem: Int? = null,
) {
    /** Conteudo total da embalagem na unidade-base (ja multiplicado pelo pack). */
    val quantidadeBase: BigDecimal
        get() = quantidade
            .multiply(unidade.fatorParaBase)
            .multiply(BigDecimal(itensPorEmbalagem ?: 1))
}

/**
 * Veredito da comparacao entre duas formas de comprar o mesmo produto.
 * A economia sempre vem em reais E em percentual (regra da Secao 4.2).
 */
data class VereditoDeOpcoes(
    val melhor: OpcaoDeCompra,
    val pior: OpcaoDeCompra,
    val precoBaseMelhor: BigDecimal,
    val precoBasePior: BigDecimal,
    val economiaReais: BigDecimal,
    val economiaPercentual: BigDecimal,
    val equivalentes: Boolean,
    val mensagem: String,
)

// =====================================================================================
// Matriz de comparacao entre estabelecimentos
// =====================================================================================

/** Cruzamento item x loja. [registrado] distingue "sem preco" de "nao tinha". */
data class CelulaComparativa(
    val estabelecimentoId: Long,
    val preco: BigDecimal?,
    val precoPorUnidadeBase: BigDecimal?,
    val disponivel: Boolean,
    val melhorPreco: Boolean,
    val registrado: Boolean,
)

data class LinhaComparativa(
    val itemId: Long,
    val produtoId: Long,
    val descricao: String,
    val detalhe: String,
    val categoriaId: Long,
    val nomeCategoria: String,
    val celulas: List<CelulaComparativa>,
    val menorPreco: BigDecimal?,
    val maiorPreco: BigDecimal?,
    val economiaEntreLojas: BigDecimal,
    val economiaPercentualEntreLojas: BigDecimal,
    val melhorEstabelecimentoId: Long?,
    val vereditoKit: VereditoDeOpcoes?,
    val alertaRestricao: String?,
    val selos: List<SeloAltoEm>,
)

data class TotalEstabelecimento(
    val estabelecimentoId: Long,
    val nome: String,
    val corHex: String,
    val total: BigDecimal,
    val itensDisponiveis: Int,
    val itensAusentes: List<String>,
    val itensSemPreco: List<String>,
    val cestaCompleta: Boolean,
)

/**
 * Melhor combinacao possivel comprando cada item onde ele esta mais barato.
 *
 * @property referenciaNome cesta usada como base da economia (a loja unica mais
 * barata que tem tudo; se nenhuma tem tudo, a de maior cobertura).
 */
data class CompraMistaOtima(
    val total: BigDecimal,
    val escolhaPorItem: Map<Long, Long>,
    val economiaReais: BigDecimal,
    val economiaPercentual: BigDecimal,
    val referenciaNome: String,
    val referenciaTotal: BigDecimal,
    val lojasEnvolvidas: Int,
)

data class MatrizComparativa(
    val estabelecimentos: List<Estabelecimento>,
    val linhas: List<LinhaComparativa>,
    val totais: List<TotalEstabelecimento>,
    val compraMista: CompraMistaOtima?,
    val itensSemNenhumPreco: Int,
) {
    val temDados: Boolean get() = estabelecimentos.isNotEmpty() && linhas.isNotEmpty()
}

// =====================================================================================
// Historico e graficos
// =====================================================================================

/** Ponto generico de grafico (barras ou rosca). */
data class PontoGrafico(
    val rotulo: String,
    val valor: BigDecimal,
    val destaque: Boolean = false,
)

data class ResumoHistorico(
    val totalGasto: BigDecimal = BigDecimal.ZERO,
    val totalEconomizado: BigDecimal = BigDecimal.ZERO,
    val quantidadeCompras: Int = 0,
    val ticketMedio: BigDecimal = BigDecimal.ZERO,
    val gastosPorPeriodo: List<PontoGrafico> = emptyList(),
    val gastosPorCategoria: List<PontoGrafico> = emptyList(),
) {
    val vazio: Boolean get() = quantidadeCompras == 0
}

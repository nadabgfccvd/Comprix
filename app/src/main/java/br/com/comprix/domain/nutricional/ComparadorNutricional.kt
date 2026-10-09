package br.com.comprix.domain.nutricional

import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.util.Constantes
import java.math.BigDecimal
import java.math.RoundingMode

/** Coluna da tabela comparativa - um produto. */
data class ColunaNutricional(
    val produtoId: Long,
    val nome: String,
    val porcao: String?,
    val info: InfoNutricional,
    val selos: List<SeloAltoEm>,
)

/** Linha da tabela comparativa - um nutriente em todos os produtos. */
data class LinhaNutricional(
    val nutriente: Nutriente,
    val valores: List<BigDecimal?>,
    val menorValor: BigDecimal?,
    val maiorValor: BigDecimal?,
    val indiceMenor: Int?,
    val indiceMaior: Int?,
)

data class ComparacaoNutricional(
    val colunas: List<ColunaNutricional>,
    val linhas: List<LinhaNutricional>,
    val baseNormalizada: String,
    val porcoesDiferentes: Boolean,
    val aviso: String?,
) {
    val vazia: Boolean get() = colunas.size < 2 || linhas.isEmpty()
}

/**
 * Comparacao nutricional do **modo tecnico** (Secao 8).
 *
 * ## Princípio: neutralidade
 * O app mostra os numeros lado a lado e destaca o maior e o menor de cada
 * linha. Ele **nao** diz qual produto e "melhor", nao calcula nota, nao faz
 * ranking e nao usa os selos "ALTO EM" para ordenar nada (proibido pela
 * Secao 13). O que e saudavel depende da pessoa - um atleta quer mais
 * proteina, um hipertenso quer menos sodio, e o app nao sabe quem esta
 * olhando.
 *
 * ## Base de comparacao
 * Rotulos declaram porcoes diferentes (30 g de cereal x 200 mL de leite).
 * Comparar numeros de porcoes distintas seria mentira estatistica, entao
 * tudo e normalizado para **100 g ou 100 mL** sempre que a porcao for
 * numericamente legivel. Quando nao for, o app compara por porcao e avisa
 * na tela, em vez de inventar uma conversao.
 *
 * Maximo de [Constantes.MAXIMO_PRODUTOS_COMPARADOS] produtos - acima disso a
 * tabela nao cabe na tela de um celular de 5".
 */
object ComparadorNutricional {

    private val CEM = BigDecimal("100")
    private val REGEX_PORCAO_NUMERO = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml|mg)""")

    /** Produtos que podem entrar na comparacao (precisam de tabela lida). */
    fun comparaveis(produtos: List<Produto>): List<Produto> =
        produtos.filter { it.temTabelaNutricional }

    /** Monta a coluna de um produto. */
    fun colunaDe(produto: Produto): ColunaNutricional? {
        val info = produto.infoNutricional?.takeIf { !it.vazia } ?: return null
        return ColunaNutricional(
            produtoId = produto.id,
            nome = produto.nome,
            porcao = info.porcaoDescricao,
            info = info,
            selos = produto.selosAltoEm,
        )
    }

    /**
     * Compara ate 3 produtos nos nutrientes escolhidos.
     *
     * @param nutrientes selecao do usuario; vazio usa [Nutriente.selecaoPadrao].
     * @param normalizarPorCem quando true (padrao), converte os valores para
     * 100 g/mL usando a porcao declarada.
     */
    fun comparar(
        produtos: List<Produto>,
        nutrientes: List<Nutriente> = Nutriente.selecaoPadrao(),
        normalizarPorCem: Boolean = true,
    ): ComparacaoNutricional {
        val selecionados = comparaveis(produtos).take(Constantes.MAXIMO_PRODUTOS_COMPARADOS)
        val colunas = selecionados.mapNotNull { colunaDe(it) }
        val nutrientesUsados = nutrientes.ifEmpty { Nutriente.selecaoPadrao() }

        if (colunas.size < 2) {
            return ComparacaoNutricional(
                colunas = colunas,
                linhas = emptyList(),
                baseNormalizada = "porção declarada",
                porcoesDiferentes = false,
                aviso = "Escolha pelo menos dois produtos com tabela nutricional lida.",
            )
        }

        val fatores = colunas.map { fatorParaCem(it.porcao, normalizarPorCem) }
        val todosNormalizados = fatores.all { it != null }
        val porcoesDiferentes = colunas.map { it.porcao?.trim()?.lowercase() }.distinct().size > 1

        val linhas = nutrientesUsados.mapNotNull { nutriente ->
            val valores = colunas.mapIndexed { indice, coluna ->
                val bruto = coluna.info.valores[nutriente] ?: return@mapIndexed null
                val fator = fatores[indice]
                if (todosNormalizados && fator != null) {
                    bruto.multiply(fator).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
                } else {
                    bruto
                }
            }
            if (valores.all { it == null }) return@mapNotNull null

            val presentes = valores.filterNotNull()
            val menor = presentes.minOrNull()
            val maior = presentes.maxOrNull()
            val destacar = menor != null && maior != null && menor.compareTo(maior) != 0
            LinhaNutricional(
                nutriente = nutriente,
                valores = valores,
                menorValor = menor,
                maiorValor = maior,
                indiceMenor = if (destacar) valores.indexOfFirst { it != null && it.compareTo(menor) == 0 } else null,
                indiceMaior = if (destacar) valores.indexOfFirst { it != null && it.compareTo(maior) == 0 } else null,
            )
        }

        val base = if (todosNormalizados && normalizarPorCem) "100 g / 100 mL" else "porção declarada"
        val aviso = when {
            !todosNormalizados && porcoesDiferentes ->
                "As porções declaradas são diferentes e não puderam ser convertidas. " +
                    "Os valores abaixo são por porção — compare com atenção."
            linhas.isEmpty() -> "Os produtos escolhidos não têm nutrientes em comum na seleção atual."
            else -> null
        }

        return ComparacaoNutricional(
            colunas = colunas,
            linhas = linhas,
            baseNormalizada = base,
            porcoesDiferentes = porcoesDiferentes,
            aviso = aviso,
        )
    }

    /**
     * Fator que converte "por porcao" em "por 100 g/mL".
     * Ex.: porcao de 30 g -> fator 3,3333.
     *
     * @return null quando a porcao nao traz numero legivel ("1 copo", "2 fatias").
     */
    private fun fatorParaCem(porcao: String?, normalizar: Boolean): BigDecimal? {
        if (!normalizar) return null
        val texto = porcao?.lowercase()?.replace(',', '.') ?: return null
        val achado = REGEX_PORCAO_NUMERO.find(texto) ?: return null
        var valor = achado.groupValues[1].toBigDecimalOrNull() ?: return null
        if (achado.groupValues[2] == "mg") valor = valor.divide(BigDecimal("1000"))
        if (valor.signum() <= 0) return null
        return CEM.divide(valor, 6, RoundingMode.HALF_EVEN)
    }
}

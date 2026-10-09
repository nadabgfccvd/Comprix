package br.com.comprix.domain.rotulo

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.CampoRotulo
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.LeituraDeRotulo
import br.com.comprix.domain.modelo.LinhaOcr
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.util.Constantes
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Funde as leituras de varios quadros de video numa unica leitura (Secao 5.4).
 *
 * ## Por que o video ajuda
 * Uma foto unica pega o rotulo num angulo so: o brilho apaga a validade, o
 * dedo cobre o preco, a curva da garrafa embaralha a tabela. Gravando 30 s
 * girando o produto, cada campo aparece nitido em ALGUM quadro.
 *
 * ## Como decidimos
 * - **Campos de texto curto (nome, codigo, datas, quantidade):** voto da
 *   maioria. Empate e desempatado pela confianca media do quadro e, depois,
 *   pela ordem de chegada.
 * - **Preco:** mediana dos valores vistos, nao a media - um unico "R$ 199,90"
 *   lido de um cartaz vizinho nao deve puxar o resultado.
 * - **Conjuntos (selos, alergenicos):** uniao do que apareceu em pelo menos
 *   [Constantes.LIMIAR_PRESENCA] dos quadros em que havia texto. Selo e
 *   informacao de saude: melhor mostrar a mais do que esconder.
 * - **Gluten:** "CONTEM" vence "NAO CONTEM" em caso de conflito (principio da
 *   precaucao - quem tem doenca celiaca nao pode receber falso negativo).
 * - **Tabela nutricional:** vence a leitura com mais nutrientes; os faltantes
 *   sao completados pelas demais.
 */
object MescladorDeLeituras {

    /**
     * @param leituras uma por quadro analisado, na ordem de captura.
     * @return leitura unica consolidada; [LeituraDeRotulo.quadrosAnalisados]
     * informa quantos quadros entraram.
     */
    fun mesclar(leituras: List<LeituraDeRotulo>): LeituraDeRotulo {
        val validas = leituras.filterNot { it.vazia }
        if (validas.isEmpty()) return LeituraDeRotulo(quadrosAnalisados = leituras.size)
        if (validas.size == 1) return validas.first().copy(quadrosAnalisados = leituras.size)

        val totalQuadros = leituras.size
        val confianca = HashMap<CampoRotulo, Float>()

        val nome = votar(validas.mapNotNull { it.nome?.takeIf { n -> n.isNotBlank() } }) { TextoUtil.normalizar(it) }
        if (nome != null) confianca[CampoRotulo.NOME] = proporcao(validas) { it.nome != null }

        val codigo = votar(validas.mapNotNull { it.codigoBarras?.takeIf { c -> c.isNotBlank() } }) { it }
        if (codigo != null) confianca[CampoRotulo.CODIGO_BARRAS] = proporcao(validas) { it.codigoBarras != null }

        val preco = medianaDePrecos(validas.mapNotNull { it.preco })
        if (preco != null) confianca[CampoRotulo.PRECO] = proporcao(validas) { it.preco != null }

        val quantidadeVencedora = validas.filter { it.quantidade != null }
            .groupBy { "${it.quantidade}-${it.unidade}-${it.itensPorEmbalagem}" }
            .maxByOrNull { (_, grupo) -> grupo.size }
            ?.value?.first()
        if (quantidadeVencedora != null) confianca[CampoRotulo.QUANTIDADE] = proporcao(validas) { it.quantidade != null }

        val validade = votar(validas.mapNotNull { it.dataValidade }) { it.toString() }
        if (validade != null) confianca[CampoRotulo.VALIDADE] = proporcao(validas) { it.dataValidade != null }

        val fabricacao = votar(validas.mapNotNull { it.dataFabricacao }) { it.toString() }
        if (fabricacao != null) confianca[CampoRotulo.FABRICACAO] = proporcao(validas) { it.dataFabricacao != null }

        // Lista de ingredientes: a mais longa costuma ser a menos cortada.
        val ingredientes = validas.mapNotNull { it.ingredientes }.maxByOrNull { it.length }
        if (ingredientes != null) confianca[CampoRotulo.INGREDIENTES] = proporcao(validas) { it.ingredientes != null }

        val gluten = consolidarGluten(validas.map { it.gluten })
        if (gluten != IndicacaoGluten.INDETERMINADO) confianca[CampoRotulo.GLUTEN] = 0.9f

        val selos = porPresenca(validas, totalQuadros) { it.selos }
        if (selos.isNotEmpty()) confianca[CampoRotulo.SELOS] = 0.9f
        val alergenos = porPresenca(validas, totalQuadros) { it.alergenos }
        if (alergenos.isNotEmpty()) confianca[CampoRotulo.ALERGENOS] = 0.7f

        val nutricional = consolidarNutricional(validas.mapNotNull { it.infoNutricional })
        if (nutricional != null) confianca[CampoRotulo.NUTRICIONAL] = proporcao(validas) { it.infoNutricional != null }

        return LeituraDeRotulo(
            nome = nome,
            preco = preco,
            quantidade = quantidadeVencedora?.quantidade,
            unidade = quantidadeVencedora?.unidade,
            itensPorEmbalagem = quantidadeVencedora?.itensPorEmbalagem,
            codigoBarras = codigo,
            formatoCodigoBarras = validas.firstOrNull { it.formatoCodigoBarras != null }?.formatoCodigoBarras,
            selos = selos.sortedBy { it.ordinal },
            dataValidade = validade,
            dataFabricacao = fabricacao,
            ingredientes = ingredientes,
            gluten = gluten,
            alergenos = alergenos.sortedBy { it.ordinal },
            infoNutricional = nutricional,
            textoBruto = validas.joinToString("\n---\n") { it.textoBruto }.take(8000),
            confiancaPorCampo = confianca,
            quadrosAnalisados = totalQuadros,
        )
    }

    /** Voto da maioria com chave de agrupamento configuravel. */
    private fun <T> votar(valores: List<T>, chave: (T) -> String): T? =
        valores.groupBy(chave).maxByOrNull { (_, grupo) -> grupo.size }?.value?.first()

    private fun proporcao(leituras: List<LeituraDeRotulo>, criterio: (LeituraDeRotulo) -> Boolean): Float =
        if (leituras.isEmpty()) 0f else leituras.count(criterio).toFloat() / leituras.size

    /** Mediana: resistente ao preco do cartaz vizinho que entrou num quadro so. */
    private fun medianaDePrecos(precos: List<BigDecimal>): BigDecimal? {
        if (precos.isEmpty()) return null
        val ordenados = precos.sorted()
        val meio = ordenados.size / 2
        return if (ordenados.size % 2 == 1) {
            ordenados[meio]
        } else {
            ordenados[meio - 1].add(ordenados[meio])
                .divide(BigDecimal("2"), Constantes.ESCALA_MOEDA, java.math.RoundingMode.HALF_EVEN)
        }
    }

    private fun consolidarGluten(indicacoes: List<IndicacaoGluten>): IndicacaoGluten = when {
        indicacoes.any { it == IndicacaoGluten.CONTEM } -> IndicacaoGluten.CONTEM
        indicacoes.any { it == IndicacaoGluten.NAO_CONTEM } -> IndicacaoGluten.NAO_CONTEM
        else -> IndicacaoGluten.INDETERMINADO
    }

    private fun <T> porPresenca(
        leituras: List<LeituraDeRotulo>,
        totalQuadros: Int,
        extrator: (LeituraDeRotulo) -> List<T>,
    ): List<T> {
        if (leituras.isEmpty()) return emptyList()
        val minimo = (totalQuadros * Constantes.LIMIAR_PRESENCA).coerceAtLeast(1.0)
        return leituras
            .flatMap { extrator(it).distinct() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= minimo }
            .keys
            .toList()
    }

    private fun consolidarNutricional(tabelas: List<InfoNutricional>): InfoNutricional? {
        val uteis = tabelas.filterNot { it.vazia }
        if (uteis.isEmpty()) return null
        val base = uteis.maxByOrNull { it.valores.size }!!
        val valores = LinkedHashMap<Nutriente, BigDecimal>(base.valores)
        uteis.forEach { tabela ->
            tabela.valores.forEach { (nutriente, valor) -> valores.putIfAbsent(nutriente, valor) }
        }
        return InfoNutricional(
            porcaoDescricao = base.porcaoDescricao ?: uteis.firstNotNullOfOrNull { it.porcaoDescricao },
            valores = valores.toSortedMap(compareBy { it.ordinal }),
        )
    }

    /**
     * Leitura sintetica usada pela tela de diagnostico ("Testar leitura") e
     * pelos testes: converte texto colado pelo usuario numa [LeituraDeRotulo]
     * completa, com o mesmo pipeline do OCR real.
     */
    fun leituraDeTeste(texto: String): LeituraDeRotulo {
        val linhas = texto.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { indice, linha ->
                LinhaOcr(
                    texto = linha,
                    alturaRelativa = if (indice == 0) 0.9f else 0.4f,
                    topoRelativo = indice / texto.lines().size.coerceAtLeast(1).toFloat(),
                )
            }
        return ExtratorDeRotulo.extrair(linhas)
    }

    /** Conjunto de selos visto em qualquer quadro (usado no resumo da revisao). */
    fun selosDeTodosOsQuadros(leituras: List<LeituraDeRotulo>): List<SeloAltoEm> =
        leituras.flatMap { it.selos }.distinct().sortedBy { it.ordinal }

    /** Alergenicos vistos em qualquer quadro (visao conservadora, para alerta). */
    fun alergenosDeTodosOsQuadros(leituras: List<LeituraDeRotulo>): List<Alergeno> =
        leituras.flatMap { it.alergenos }.distinct().sortedBy { it.ordinal }
}

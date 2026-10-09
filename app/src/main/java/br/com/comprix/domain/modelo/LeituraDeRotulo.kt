package br.com.comprix.domain.modelo

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Uma linha de texto devolvida pelo OCR, com a geometria que importa para o
 * nosso heuristico: letra grande no alto costuma ser nome/preco; letra miuda
 * em baixo costuma ser lote, validade ou tabela nutricional.
 *
 * @property alturaRelativa altura da linha dividida pela altura da imagem (0..1).
 * @property topoRelativo posicao vertical do topo da linha (0 = topo da imagem).
 */
data class LinhaOcr(
    val texto: String,
    val alturaRelativa: Float = 0.5f,
    val topoRelativo: Float = 0.5f,
)

/** Campos que o scanner tenta preencher - cada um com sua confianca. */
enum class CampoRotulo(val rotulo: String) {
    NOME("Nome"),
    PRECO("Preço"),
    QUANTIDADE("Peso/volume"),
    CODIGO_BARRAS("Código de barras"),
    VALIDADE("Validade"),
    FABRICACAO("Fabricação"),
    INGREDIENTES("Ingredientes"),
    GLUTEN("Glúten"),
    ALERGENOS("Alergênicos"),
    SELOS("Selos ALTO EM"),
    NUTRICIONAL("Tabela nutricional"),
}

/**
 * Resultado da leitura de um rotulo (uma foto, um quadro de video, ou a fusao
 * de varios quadros).
 *
 * @property confiancaPorCampo 0..1 por campo, mostrado na tela de revisao para
 * o usuario saber onde olhar com atencao.
 * @property quadrosAnalisados quantos quadros entraram nesta leitura (1 na foto).
 */
data class LeituraDeRotulo(
    val nome: String? = null,
    val preco: BigDecimal? = null,
    val quantidade: BigDecimal? = null,
    val unidade: Unidade? = null,
    val itensPorEmbalagem: Int? = null,
    val codigoBarras: String? = null,
    val formatoCodigoBarras: String? = null,
    val selos: List<SeloAltoEm> = emptyList(),
    val dataValidade: LocalDate? = null,
    val dataFabricacao: LocalDate? = null,
    val ingredientes: String? = null,
    val gluten: IndicacaoGluten = IndicacaoGluten.INDETERMINADO,
    val alergenos: List<Alergeno> = emptyList(),
    val infoNutricional: InfoNutricional? = null,
    val textoBruto: String = "",
    val confiancaPorCampo: Map<CampoRotulo, Float> = emptyMap(),
    val quadrosAnalisados: Int = 1,
) {
    val camposLidos: List<CampoRotulo>
        get() = buildList {
            if (!nome.isNullOrBlank()) add(CampoRotulo.NOME)
            if (preco != null) add(CampoRotulo.PRECO)
            if (quantidade != null) add(CampoRotulo.QUANTIDADE)
            if (!codigoBarras.isNullOrBlank()) add(CampoRotulo.CODIGO_BARRAS)
            if (dataValidade != null) add(CampoRotulo.VALIDADE)
            if (dataFabricacao != null) add(CampoRotulo.FABRICACAO)
            if (!ingredientes.isNullOrBlank()) add(CampoRotulo.INGREDIENTES)
            if (gluten != IndicacaoGluten.INDETERMINADO) add(CampoRotulo.GLUTEN)
            if (alergenos.isNotEmpty()) add(CampoRotulo.ALERGENOS)
            if (selos.isNotEmpty()) add(CampoRotulo.SELOS)
            if (infoNutricional?.vazia == false) add(CampoRotulo.NUTRICIONAL)
        }

    val vazia: Boolean get() = camposLidos.isEmpty()

    /** Media das confiancas dos campos preenchidos (0 quando nada foi lido). */
    val confiancaMedia: Float
        get() {
            val valores = camposLidos.mapNotNull { confiancaPorCampo[it] }
            return if (valores.isEmpty()) 0f else valores.sum() / valores.size
        }
}

/**
 * Roteiro do modo video: 30 segundos divididos em 5 etapas, com instrucao na
 * tela para o usuario saber para onde apontar a camera.
 */
enum class EtapaGuiaVideo(val instrucao: String, val dica: String) {
    FRENTE("Aponte para a frente da embalagem", "Nome e marca do produto"),
    PRECO("Aponte para o preço na gôndola", "Etiqueta amarela ou display"),
    TABELA("Aponte para a tabela nutricional", "Costuma ficar atrás ou na lateral"),
    CODIGO("Aponte para o código de barras", "Mantenha a 15 cm, sem reflexo"),
    VALIDADE("Aponte para a validade", "Topo, fundo ou tampa da embalagem"),
    ;

    companion object {
        /** Divide a gravacao igualmente entre as etapas do roteiro. */
        fun paraInstante(milissegundos: Long, duracaoTotalMs: Long): EtapaGuiaVideo {
            if (duracaoTotalMs <= 0) return FRENTE
            val fatia = duracaoTotalMs / entries.size
            val indice = (milissegundos / fatia.coerceAtLeast(1)).toInt()
            return entries[indice.coerceIn(0, entries.size - 1)]
        }
    }
}

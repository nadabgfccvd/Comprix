package br.com.comprix.domain.alergia

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.util.TextoUtil

/**
 * Verificacao de restricoes alimentares do usuario, pura e sem Android.
 *
 * Examina o produto em tres camadas, nesta ordem:
 *
 * 1. **Gluten** (so quando o perfil marca "sem gluten"): rotulo CONTEM dispara
 *    alerta forte; rotulo INDETERMINADO pede conferencia do rotulo.
 * 2. **Alergenos oficiais** (RDC 26/2015): intersecao entre os alergenos do
 *    produto e os marcados no perfil.
 * 3. **Restricoes customizadas** criadas pelo usuario: o nome da restricao e
 *    suas palavras extras sao procurados no nome do produto e nos ingredientes,
 *    usando [TextoUtil.contemPalavra] (palavra inteira; frases casam por
 *    substring do texto normalizado).
 *
 * Informativo por design: o app avisa, nunca remove nem reordena nada.
 *
 * **Limites** (mesma mensagem dada ao usuario na ficha do produto): os dados
 * do produto vem de OCR/texto e regras — podem falhar para mais (falso
 * positivo) ou para menos (falso negativo, pior caso: rotulo mal fotografado,
 * alergenico nao listado). O alerta e lembrete, nao garantia; o rotulo fisico
 * sempre prevalece.
 */
object VerificadorDeAlergias {

    private const val ALERTA_GLUTEN_CONTEM = "Contém glúten (restrição sua)"
    private const val ALERTA_GLUTEN_INDETERMINADO = "Glúten não informado — confira o rótulo"

    /** Devolve uma linha de alerta por restricao atingida, pronta para exibir. */
    fun verificar(produto: Produto, perfil: PerfilRestricoes): List<String> {
        if (!perfil.ativo) return emptyList()
        val alertas = mutableListOf<String>()

        // 1. Gluten - apenas quando o usuario marcou "sem glúten".
        if (perfil.semGluten) {
            when (produto.gluten) {
                IndicacaoGluten.CONTEM -> alertas += ALERTA_GLUTEN_CONTEM
                IndicacaoGluten.INDETERMINADO -> alertas += ALERTA_GLUTEN_INDETERMINADO
                IndicacaoGluten.NAO_CONTEM -> Unit
            }
        }
        // Quando o rotulo ja declarou "CONTEM gluten", o fato "contém glúten"
        // esta dito: nao repetir o mesmo fato na intersecao oficial abaixo.
        val glutenConflitoJaAvisado = perfil.semGluten && produto.gluten == IndicacaoGluten.CONTEM

        // 2. Alergenos oficiais (RDC 26/2015) marcados no perfil.
        val conflitos = produto.alergenos.filter {
            it in perfil.alergenos && !(it == Alergeno.GLUTEN && glutenConflitoJaAvisado)
        }
        if (conflitos.isNotEmpty()) {
            alertas += "Contém " + conflitos.joinToString(", ") { it.rotulo.lowercase() }
        }

        // 3. Restricoes customizadas: nome e palavras extras contra nome e
        //    ingredientes do produto (tudo normalizado antes de comparar).
        if (perfil.customizadas.isNotEmpty()) {
            val nomeDoProduto = TextoUtil.normalizar(produto.nome)
            val ingredientes = produto.ingredientes?.let { TextoUtil.normalizar(it) }
            perfil.customizadas.forEach { alergia ->
                val termoAchado = (listOf(alergia.nome) + alergia.palavras).firstOrNull { termo ->
                    val termoNormalizado = TextoUtil.normalizar(termo)
                    termoNormalizado.isNotBlank() && (
                        TextoUtil.contemPalavra(nomeDoProduto, termoNormalizado) ||
                            (ingredientes != null && TextoUtil.contemPalavra(ingredientes, termoNormalizado))
                        )
                }
                if (termoAchado != null) {
                    alertas += "Pode conter ${alergia.nome} (encontrei \"$termoAchado\")"
                }
            }
        }

        return alertas.distinct()
    }

    /** Primeiro alerta da lista, para os pontos que hoje recebem String?. */
    fun alertaUnico(produto: Produto, perfil: PerfilRestricoes): String? =
        verificar(produto, perfil).firstOrNull()
}

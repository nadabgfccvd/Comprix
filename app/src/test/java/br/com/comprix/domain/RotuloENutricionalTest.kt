package br.com.comprix.domain

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.CampoRotulo
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.LinhaOcr
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.domain.nutricional.ComparadorNutricional
import br.com.comprix.domain.rotulo.ExtratorDeRotulo
import br.com.comprix.domain.rotulo.MescladorDeLeituras
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leitura de rótulo (Seção 5.4), mesclagem por maioria (5.3) e comparação
 * nutricional neutra (Seção 8).
 *
 * Os testes trabalham sobre [LinhaOcr], que é o que o ML Kit entrega depois de
 * traduzido para o domínio - assim a regra é testável sem câmera e sem Android.
 */
class RotuloENutricionalTest {

    private fun linha(texto: String, altura: Float = 0.1f, topo: Float = 0.5f) =
        LinhaOcr(texto = texto, alturaRelativa = altura, topoRelativo = topo)

    // --- preço na gôndola ------------------------------------------------------------------

    @Test
    fun `preco com cifrao e o candidato mais forte`() {
        val preco = ExtratorDeRotulo.extrairPreco(
            listOf(
                linha("Arroz Tipo 1", altura = 0.12f, topo = 0.1f),
                linha("R$ 24,90", altura = 0.2f, topo = 0.6f),
                linha("5 kg", altura = 0.08f, topo = 0.8f),
            ),
        )
        assertNotNull(preco)
        assertEquals(0, preco!!.compareTo(BigDecimal("24.90")))
    }

    @Test
    fun `codigo de barras nunca vira preco`() {
        val preco = ExtratorDeRotulo.extrairPreco(listOf(linha("78999123456", altura = 0.3f)))
        assertNull("sequência longa de dígitos é código, não dinheiro", preco)
    }

    @Test
    fun `preco maior ganha de ruido pequeno na etiqueta`() {
        val preco = ExtratorDeRotulo.extrairPreco(
            listOf(
                linha("0,01", altura = 0.03f, topo = 0.95f),
                linha("12,99", altura = 0.25f, topo = 0.5f),
            ),
        )
        assertEquals(0, preco!!.compareTo(BigDecimal("12.99")))
    }

    // --- nome e quantidade ------------------------------------------------------------------

    @Test
    fun `nome vem da linha mais destacada do topo`() {
        val nome = ExtratorDeRotulo.extrairNome(
            listOf(
                linha("Leite Integral Bom Gosto", altura = 0.22f, topo = 0.12f),
                linha("Informação nutricional", altura = 0.05f, topo = 0.7f),
            ),
        )
        assertNotNull(nome)
        assertTrue(nome!!.contains("Leite", ignoreCase = true))
    }

    @Test
    fun `quantidade sai do conteudo declarado`() {
        val quantidade = ExtratorDeRotulo.extrairQuantidade(listOf(linha("Pacote 500 g")))
        assertNotNull(quantidade)
        assertEquals(0, quantidade!!.quantidade.compareTo(BigDecimal("500")))
    }

    // --- glúten e alérgenos ------------------------------------------------------------------

    @Test
    fun `contem gluten quando o rotulo diz que contem`() {
        val indicacao = ExtratorDeRotulo.detectarGluten("contem gluten", null)
        assertEquals(IndicacaoGluten.CONTEM, indicacao)
    }

    @Test
    fun `nao contem gluten e diferente de indeterminado`() {
        assertEquals(
            IndicacaoGluten.NAO_CONTEM,
            ExtratorDeRotulo.detectarGluten("nao contem gluten", null),
        )
        assertEquals(
            IndicacaoGluten.INDETERMINADO,
            ExtratorDeRotulo.detectarGluten("arroz branco tipo 1", null),
        )
    }

    @Test
    fun `alergenos saem da lista de ingredientes`() {
        val alergenos = ExtratorDeRotulo.detectarAlergenos(
            "ingredientes: farinha de trigo, leite em po, lecitina de soja",
            "farinha de trigo, leite em po, lecitina de soja",
        )
        assertTrue(Alergeno.GLUTEN in alergenos)
        assertTrue(Alergeno.LEITE in alergenos)
        assertTrue(Alergeno.SOJA in alergenos)
    }

    @Test
    fun `selos da anvisa sao reconhecidos no rotulo`() {
        val selos = ExtratorDeRotulo.detectarSelos("alto em sodio e alto em gorduras saturadas")
        assertTrue(selos.isNotEmpty())
        assertTrue(selos.any { it == SeloAltoEm.SODIO })
    }

    // --- leitura completa ----------------------------------------------------------------------

    @Test
    fun `extracao completa preenche os campos lidos e marca confianca`() {
        val leitura = ExtratorDeRotulo.extrair(
            linhas = listOf(
                linha("Biscoito Maria", altura = 0.2f, topo = 0.1f),
                linha("R$ 3,49", altura = 0.18f, topo = 0.5f),
                linha("200 g", altura = 0.08f, topo = 0.7f),
                linha("contem gluten", altura = 0.05f, topo = 0.85f),
            ),
            codigoBarras = "7891000100103",
            formatoCodigoBarras = "EAN-13",
        )
        assertFalse(leitura.vazia)
        assertEquals("7891000100103", leitura.codigoBarras)
        assertEquals(IndicacaoGluten.CONTEM, leitura.gluten)
        assertTrue(CampoRotulo.PRECO in leitura.camposLidos)
        assertTrue(leitura.confiancaMedia > 0f)
    }

    @Test
    fun `leitura vazia nao inventa dado`() {
        val leitura = ExtratorDeRotulo.extrair(emptyList())
        assertTrue(leitura.vazia)
        assertNull(leitura.nome)
        assertNull(leitura.preco)
    }

    // --- mesclagem por maioria ---------------------------------------------------------------------

    @Test
    fun `maioria dos quadros decide o valor de cada campo`() {
        val leituras = listOf(
            MescladorDeLeituras.leituraDeTeste("Arroz Tio Joao R$ 24,90"),
            MescladorDeLeituras.leituraDeTeste("Arroz Tio Joao R$ 24,90"),
            MescladorDeLeituras.leituraDeTeste("Arroz Tio Joao R$ 2,49"),
        )
        val mesclada = MescladorDeLeituras.mesclar(leituras)
        assertEquals(0, mesclada.preco!!.compareTo(BigDecimal("24.90")))
        assertEquals(3, mesclada.quadrosAnalisados)
    }

    @Test
    fun `mesclar lista vazia devolve leitura vazia e nao quebra`() {
        val mesclada = MescladorDeLeituras.mesclar(emptyList())
        assertTrue(mesclada.vazia)
    }

    @Test
    fun `selo visto em um unico quadro nao se perde na mesclagem`() {
        val leituras = listOf(
            MescladorDeLeituras.leituraDeTeste("Biscoito recheado"),
            MescladorDeLeituras.leituraDeTeste("Biscoito recheado alto em sodio"),
        )
        val selos = MescladorDeLeituras.selosDeTodosOsQuadros(leituras)
        assertTrue("o selo aparece só quando a face certa é filmada", selos.isNotEmpty())
    }

    // --- comparação nutricional ---------------------------------------------------------------------

    private fun produtoCom(
        id: Long,
        nome: String,
        porcao: String?,
        valores: Map<Nutriente, String>,
    ) = Produto(
        id = id,
        nome = nome,
        nomeNormalizado = nome.lowercase(),
        categoriaId = 1,
        infoNutricional = InfoNutricional(
            porcaoDescricao = porcao,
            valores = valores.mapValues { BigDecimal(it.value) },
        ),
    )

    @Test
    fun `comparacao normaliza porcoes diferentes para cem gramas`() {
        val a = produtoCom(1, "Cereal A", "30 g", mapOf(Nutriente.SODIO to "60"))
        val b = produtoCom(2, "Cereal B", "60 g", mapOf(Nutriente.SODIO to "150"))

        val comparacao = ComparadorNutricional.comparar(listOf(a, b), listOf(Nutriente.SODIO))

        assertFalse(comparacao.vazia)
        assertTrue(comparacao.baseNormalizada.contains("100"))
        val linha = comparacao.linhas.first()
        // 60 em 30 g = 200 por 100 g; 150 em 60 g = 250 por 100 g
        assertEquals(0, linha.valores[0]!!.compareTo(BigDecimal("200")))
        assertEquals(0, linha.valores[1]!!.compareTo(BigDecimal("250")))
        assertEquals(0, linha.indiceMenor)
        assertEquals(1, linha.indiceMaior)
    }

    @Test
    fun `porcao ilegivel compara por porcao declarada e avisa`() {
        val a = produtoCom(1, "Produto A", "1 fatia", mapOf(Nutriente.PROTEINAS to "5"))
        val b = produtoCom(2, "Produto B", "1 copo", mapOf(Nutriente.PROTEINAS to "7"))

        val comparacao = ComparadorNutricional.comparar(listOf(a, b), listOf(Nutriente.PROTEINAS))

        assertNotNull(comparacao.aviso)
        assertTrue(comparacao.baseNormalizada.contains("porção"))
    }

    @Test
    fun `no maximo tres produtos entram na tabela`() {
        val produtos = (1L..5L).map {
            produtoCom(it, "Produto $it", "100 g", mapOf(Nutriente.PROTEINAS to "$it"))
        }
        val comparacao = ComparadorNutricional.comparar(produtos)
        assertTrue(comparacao.colunas.size <= 3)
    }

    @Test
    fun `produto sem tabela nao entra na comparacao`() {
        val comTabela = produtoCom(1, "Com tabela", "100 g", mapOf(Nutriente.PROTEINAS to "9"))
        val semTabela = Produto(id = 2, nome = "Sem tabela", nomeNormalizado = "sem tabela", categoriaId = 1)
        val comparaveis = ComparadorNutricional.comparaveis(listOf(comTabela, semTabela))
        assertEquals(1, comparaveis.size)
        assertEquals(comTabela.id, comparaveis.first().id)
    }

    @Test
    fun `um produto so nao gera comparacao`() {
        val unico = produtoCom(1, "Único", "100 g", mapOf(Nutriente.PROTEINAS to "9"))
        assertTrue(ComparadorNutricional.comparar(listOf(unico)).vazia)
    }

    @Test
    fun `comparacao nao produz nota nem ranking`() {
        val a = produtoCom(1, "A", "100 g", mapOf(Nutriente.SODIO to "100"))
        val b = produtoCom(2, "B", "100 g", mapOf(Nutriente.SODIO to "900"))
        val comparacao = ComparadorNutricional.comparar(listOf(a, b), listOf(Nutriente.SODIO))

        // A API expõe maior e menor por linha - e nada que ordene produtos.
        val campos = ComparadorNutricional::class.java.methods.map { it.name }
        assertFalse(campos.any { it.contains("ranking", ignoreCase = true) })
        assertFalse(campos.any { it.contains("nota", ignoreCase = true) })
        assertEquals(0, comparacao.linhas.first().indiceMenor)
    }
}

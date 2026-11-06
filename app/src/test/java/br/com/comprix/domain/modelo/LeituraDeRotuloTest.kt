package br.com.comprix.domain.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * `LeituraDeRotulo`: o contrato do que a tela de revisao mostra como "lido" e
 * a confianca media que orienta onde o usuario deve olhar com atencao.
 *
 * As regras sao sutis e a tela depende delas: campo em branco nao e campo
 * lido; gluten so conta quando foi determinado (nao "INDETERMINADO"); tabela
 * nutricional so conta quando tem conteudo; e a media de confianca so usa os
 * campos lidos que TAMBEM tem confianca registrada. `EtapaGuiaVideo` divide a
 * gravacao do modo video em 5 fatias iguais e nunca sai da faixa.
 */
class LeituraDeRotuloTest {

    // --- camposLidos -----------------------------------------------------------------

    @Test
    fun `leitura padrao e vazia`() {
        assertTrue(LeituraDeRotulo().camposLidos.isEmpty())
        assertTrue(LeituraDeRotulo().vazia)
    }

    @Test
    fun `leitura completa lista os onze campos na ordem da revisao`() {
        val leitura = LeituraDeRotulo(
            nome = "Arroz integral", preco = BigDecimal("7.99"), quantidade = BigDecimal("1"),
            unidade = Unidade.QUILO, codigoBarras = "7891234567890",
            dataValidade = LocalDate.of(2027, 3, 1), dataFabricacao = LocalDate.of(2026, 10, 1),
            ingredientes = "arroz, agua", gluten = IndicacaoGluten.CONTEM,
            alergenos = listOf(Alergeno.GLUTEN), selos = listOf(SeloAltoEm.SODIO),
            infoNutricional = InfoNutricional("30 g", mapOf(Nutriente.SODIO to BigDecimal("0.30"))),
        )
        assertEquals(
            listOf(
                CampoRotulo.NOME, CampoRotulo.PRECO, CampoRotulo.QUANTIDADE, CampoRotulo.CODIGO_BARRAS,
                CampoRotulo.VALIDADE, CampoRotulo.FABRICACAO, CampoRotulo.INGREDIENTES, CampoRotulo.GLUTEN,
                CampoRotulo.ALERGENOS, CampoRotulo.SELOS, CampoRotulo.NUTRICIONAL,
            ),
            leitura.camposLidos,
        )
        assertFalse(leitura.vazia)
    }

    @Test
    fun `texto em branco nao e campo lido`() {
        val leitura = LeituraDeRotulo(nome = "   ", ingredientes = "", codigoBarras = "")
        assertTrue(leitura.camposLidos.isEmpty())
    }

    @Test
    fun `gluten indeterminado nao e campo lido, os outros sim`() {
        assertFalse(CampoRotulo.GLUTEN in LeituraDeRotulo().camposLidos)
        assertTrue(CampoRotulo.GLUTEN in LeituraDeRotulo(gluten = IndicacaoGluten.CONTEM).camposLidos)
        assertTrue(CampoRotulo.GLUTEN in LeituraDeRotulo(gluten = IndicacaoGluten.NAO_CONTEM).camposLidos)
    }

    @Test
    fun `tabela nutricional so conta quando tem conteudo`() {
        assertFalse(CampoRotulo.NUTRICIONAL in LeituraDeRotulo().camposLidos)
        assertFalse(CampoRotulo.NUTRICIONAL in LeituraDeRotulo(infoNutricional = InfoNutricional()).camposLidos)
        assertTrue(CampoRotulo.NUTRICIONAL in
            LeituraDeRotulo(infoNutricional = InfoNutricional(porcaoDescricao = "30 g")).camposLidos)
    }

    @Test
    fun `preco e quantidade zero sao leituras legitimas`() {
        val leitura = LeituraDeRotulo(preco = BigDecimal.ZERO, quantidade = BigDecimal.ZERO)
        assertEquals(listOf(CampoRotulo.PRECO, CampoRotulo.QUANTIDADE), leitura.camposLidos)
    }

    // --- confiancaMedia --------------------------------------------------------------

    @Test
    fun `sem leitura, confianca zero`() {
        assertEquals(0f, LeituraDeRotulo().confiancaMedia, 0.0001f)
    }

    @Test
    fun `media simples das confiancas dos campos lidos`() {
        val leitura = LeituraDeRotulo(
            nome = "Arroz", preco = BigDecimal("7.99"),
            confiancaPorCampo = mapOf(CampoRotulo.NOME to 0.8f, CampoRotulo.PRECO to 0.6f),
        )
        assertEquals(0.7f, leitura.confiancaMedia, 0.0001f)
    }

    @Test
    fun `campo lido sem confianca registrada sai da media`() {
        val leitura = LeituraDeRotulo(
            nome = "Arroz", preco = BigDecimal("7.99"),
            confiancaPorCampo = mapOf(CampoRotulo.PRECO to 0.6f),
        )
        assertEquals(0.6f, leitura.confiancaMedia, 0.0001f)
    }

    @Test
    fun `confianca de campo nao lido nao entra na media`() {
        val leitura = LeituraDeRotulo(
            nome = "Arroz",
            confiancaPorCampo = mapOf(CampoRotulo.NOME to 0.8f, CampoRotulo.VALIDADE to 0.9f),
        )
        assertEquals(0.8f, leitura.confiancaMedia, 0.0001f)
    }

    // --- EtapaGuiaVideo --------------------------------------------------------------

    @Test
    fun `duracao invalida fica na primeira etapa`() {
        assertEquals(EtapaGuiaVideo.FRENTE, EtapaGuiaVideo.paraInstante(0L, 0L))
        assertEquals(EtapaGuiaVideo.FRENTE, EtapaGuiaVideo.paraInstante(1000L, -5L))
    }

    @Test
    fun `cinco etapas em fatias iguais`() {
        // 5.000 ms divididos em 5 fatias de 1.000 ms.
        assertEquals(EtapaGuiaVideo.FRENTE, EtapaGuiaVideo.paraInstante(0L, 5000L))
        assertEquals(EtapaGuiaVideo.FRENTE, EtapaGuiaVideo.paraInstante(999L, 5000L))
        assertEquals(EtapaGuiaVideo.PRECO, EtapaGuiaVideo.paraInstante(1000L, 5000L))
        assertEquals(EtapaGuiaVideo.TABELA, EtapaGuiaVideo.paraInstante(2500L, 5000L))
        assertEquals(EtapaGuiaVideo.CODIGO, EtapaGuiaVideo.paraInstante(3500L, 5000L))
        assertEquals(EtapaGuiaVideo.VALIDADE, EtapaGuiaVideo.paraInstante(4999L, 5000L))
    }

    @Test
    fun `instante fora da gravacao e preso na faixa`() {
        assertEquals(EtapaGuiaVideo.VALIDADE, EtapaGuiaVideo.paraInstante(5000L, 5000L))
        assertEquals(EtapaGuiaVideo.VALIDADE, EtapaGuiaVideo.paraInstante(99_999L, 5000L))
        assertEquals(EtapaGuiaVideo.FRENTE, EtapaGuiaVideo.paraInstante(-1500L, 5000L))
    }

    @Test
    fun `gravacao menor que uma fatia ainda divide por etapa`() {
        // fatia = 1/5 = 0 -> coercida para 1 ms por etapa; nunca divide por zero.
        assertEquals(EtapaGuiaVideo.CODIGO, EtapaGuiaVideo.paraInstante(3L, 1L))
        assertEquals(EtapaGuiaVideo.VALIDADE, EtapaGuiaVideo.paraInstante(10L, 1L))
    }

    @Test
    fun `duracao nao divisivel arredonda a fatia para baixo`() {
        // 3.000 ms / 5 = fatia de 600 ms.
        assertEquals(EtapaGuiaVideo.TABELA, EtapaGuiaVideo.paraInstante(1200L, 3000L))
        assertEquals(EtapaGuiaVideo.VALIDADE, EtapaGuiaVideo.paraInstante(2999L, 3000L))
    }
}

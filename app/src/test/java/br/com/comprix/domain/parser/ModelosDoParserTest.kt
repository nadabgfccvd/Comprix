package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * `MemoriaDoParser` e `ItemInterpretado.prontoParaAdicionar`: os mini-contratos
 * que ficam de fora da especificacao ponta-a-ponta.
 *
 * - A memoria compara termos SEMPRE em minusculas (o usuario confirma "Arroz",
 *   o parser pergunta "arroz" - e a mesma decisao).
 * - "Pronto para adicionar" = tem nome E nao veio com confianca BAIXA: item de
 *   confianca baixa abre o editor em vez de entrar direto na lista.
 */
class ModelosDoParserTest {

    // --- MemoriaDoParser -------------------------------------------------------------

    @Test
    fun `memoria vazia nao rejeita nem confirma nada`() {
        assertFalse(MemoriaDoParser.VAZIA.jaRejeitou("arroz"))
        assertNull(MemoriaDoParser.VAZIA.valorConfirmado("arroz"))
    }

    @Test
    fun `rejeicao ignora maiusculas e minusculas`() {
        val memoria = MemoriaDoParser(rejeitados = setOf("arroz"))
        assertTrue(memoria.jaRejeitou("arroz"))
        assertTrue(memoria.jaRejeitou("ARROZ"))
        assertTrue(memoria.jaRejeitou("Arroz"))
        assertFalse(memoria.jaRejeitou("feijao"))
    }

    @Test
    fun `confirmacao devolve o valor aceito, sem ligar para caixa`() {
        val memoria = MemoriaDoParser(confirmados = mapOf("arroz" to BigDecimal("2.5")))
        assertEquals(0, memoria.valorConfirmado("ARROZ")!!.compareTo(BigDecimal("2.5")))
        assertNull(memoria.valorConfirmado("feijao"))
    }

    // --- ItemInterpretado.prontoParaAdicionar ----------------------------------------

    @Test
    fun `item com nome e confianca media esta pronto`() {
        val item = ItemInterpretado(nomeItem = "Arroz", textoOriginal = "arroz")
        assertTrue(item.prontoParaAdicionar)
    }

    @Test
    fun `item sem nome nunca esta pronto`() {
        val item = ItemInterpretado(nomeItem = "   ", textoOriginal = "   ",
            confianca = Confianca.ALTA)
        assertFalse(item.prontoParaAdicionar)
    }

    @Test
    fun `confianca baixa abre o editor em vez de entrar direto`() {
        val item = ItemInterpretado(nomeItem = "Coisa", textoOriginal = "coisa?",
            confianca = Confianca.BAIXA)
        assertFalse(item.prontoParaAdicionar)
        assertTrue(item.copy(confianca = Confianca.MEDIA).prontoParaAdicionar)
        assertTrue(item.copy(confianca = Confianca.ALTA).prontoParaAdicionar)
    }

    @Test
    fun `quantidade nula e legitima e nao impede a adicao`() {
        // "um pouco de sal" nao e 1 - mas ainda entra como item.
        val item = ItemInterpretado(nomeItem = "sal", textoOriginal = "um pouco de sal",
            quantidade = null)
        assertTrue(item.prontoParaAdicionar)
        assertNull(item.quantidade)
    }

    @Test
    fun `preco por item vive no proprio item da linha multi-item`() {
        val uva = ItemInterpretado(nomeItem = "uva", textoOriginal = "uva 1kg 3,99",
            unidade = Unidade.QUILO, preco = BigDecimal("3.99"))
        assertEquals(0, uva.preco!!.compareTo(BigDecimal("3.99")))
    }
}

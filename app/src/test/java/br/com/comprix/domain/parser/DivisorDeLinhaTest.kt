package br.com.comprix.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM puros do [DivisorDeLinha] — a porta de entrada do parser de
 * texto livre. Ele era exercitado só indiretamente pela especificação do
 * parser (`EspecificacaoDoParserTest` + JSON de casos); aqui o contrato
 * próprio fica congelado: proteção de decimal/fração, conectivo `e`,
 * itens colados por espaço após preço, anexação do padrão P2 e a regra do
 * apelido de animal que não vira quantidade.
 *
 * Só a API que o parser usa ([DivisorDeLinha.dividir] e
 * [DivisorDeLinha.soTemQuantidade]) é coberta — [DivisorDeLinha.padraoDe]
 * não tem chamador em produção nem em teste (código morto conhecido,
 * registrado no work log) e não recebe teste de congelamento.
 */
class DivisorDeLinhaTest {

    private fun textos(linha: String) = DivisorDeLinha.dividir(linha).map { it.texto }

    // --- vazios e separadores fortes --------------------------------------------------

    @Test
    fun `linha vazia ou branca nao gera segmento`() {
        assertTrue(DivisorDeLinha.dividir("").isEmpty())
        assertTrue(DivisorDeLinha.dividir("   ").isEmpty())
        assertTrue(DivisorDeLinha.dividir(", ; ,").isEmpty())
    }

    @Test
    fun `virgula ponto e virgula e barra separam itens`() {
        assertEquals(listOf("arroz", "feijão", "leite"), textos("arroz, feijão; leite"))
        assertEquals(listOf("leite", "ovos"), textos("leite/ovos"))
    }

    @Test
    fun `decimal com virgula e fracao com barra continuam inteiros`() {
        assertEquals(listOf("refrigerante 2,5 litros"), textos("refrigerante 2,5 litros"))
        assertEquals(listOf("1/2 kg de carne"), textos("1/2 kg de carne"))
    }

    @Test
    fun `abreviacao c barra vira com e nao separa`() {
        assertEquals(listOf("pão com 12"), textos("pão c/ 12"))
        assertEquals(listOf("pão com 12", "leite"), textos("pão c/ 12, leite"))
    }

    // --- conectivo `e` ----------------------------------------------------------------

    @Test
    fun `e entre dois nomes separa itens`() {
        assertEquals(listOf("carne", "ovos"), textos("carne e ovos"))
    }

    @Test
    fun `e no meio de quantidade nao separa`() {
        assertEquals(listOf("um quilo e meio de carne"), textos("um quilo e meio de carne"))
        assertEquals(listOf("2 e 50 de sabão"), textos("2 e 50 de sabão"))
    }

    // --- itens colados por espaço depois do preço -------------------------------------

    @Test
    fun `item colado apos preco e cortado antes do nome novo`() {
        assertEquals(
            listOf("bolo 1kg 11,99", "arroz 5kg 20,33"),
            textos("bolo 1kg 11,99 arroz 5kg 20,33"),
        )
    }

    @Test
    fun `colagem conservadora - sem preco anterior nao ha corte`() {
        // Sem preço antes de "leite", dividir aqui inventaria item - fica junto.
        assertEquals(listOf("cafe 500g leite"), textos("cafe 500g leite"))
    }

    @Test
    fun `colagem conservadora - sem numero adiante nao ha corte`() {
        // "dove" é marca, não há evidência numérica adiante: um item só.
        assertEquals(listOf("sabonete 3,99 dove"), textos("sabonete 3,99 dove"))
    }

    // --- padrao P2: quantidade solta vira anexa do item anterior ----------------------

    @Test
    fun `quantidade solta depois de item e anexada`() {
        val segmentos = DivisorDeLinha.dividir("ovos, meia duzia")
        assertEquals(1, segmentos.size)
        assertEquals("ovos", segmentos[0].texto)
        assertEquals("meia duzia", segmentos[0].quantidadeAnexa)
    }

    @Test
    fun `palavra comum depois de item nao e anexada`() {
        val segmentos = DivisorDeLinha.dividir("ovos, sabonete")
        assertEquals(2, segmentos.size)
        assertNull(segmentos[0].quantidadeAnexa)
        assertNull(segmentos[1].quantidadeAnexa)
    }

    @Test
    fun `apelido de animal nao vira quantidade em silencio`() {
        // "cachorro" é 5 no jogo do bicho E produto de verdade: vira segmento
        // próprio (vira sugestão na UI), nunca "12 ovos" escondido.
        val segmentos = DivisorDeLinha.dividir("ovos, cachorro")
        assertEquals(2, segmentos.size)
        assertNull(segmentos[0].quantidadeAnexa)
        assertEquals("cachorro", segmentos[1].texto)
    }

    // --- soTemQuantidade --------------------------------------------------------------

    @Test
    fun `soTemQuantidade reconhece quantidade real`() {
        assertTrue(DivisorDeLinha.soTemQuantidade("meia duzia"))
        assertTrue(DivisorDeLinha.soTemQuantidade("3"))
        assertTrue(DivisorDeLinha.soTemQuantidade("2 kg"))
        assertTrue(DivisorDeLinha.soTemQuantidade("duzia"))
    }

    @Test
    fun `soTemQuantidade rejeita nome, preposicao solta e apelido de animal`() {
        assertTrue(!DivisorDeLinha.soTemQuantidade("leite"))
        assertTrue(!DivisorDeLinha.soTemQuantidade("de"))
        assertTrue(!DivisorDeLinha.soTemQuantidade(""))
        assertTrue(!DivisorDeLinha.soTemQuantidade("cachorro"))
    }

    // --- guardas de API ----------------------------------------------------------------

    @Test
    fun `preposicoes expostas sao as do contrato`() {
        assertEquals(setOf("de", "do", "da", "dos", "das"), DivisorDeLinha.PREPOSICOES_DE)
        assertEquals(setOf("com", "c", "contendo"), DivisorDeLinha.PREPOSICOES_COM)
    }
}

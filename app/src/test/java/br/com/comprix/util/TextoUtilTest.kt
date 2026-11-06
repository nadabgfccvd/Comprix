package br.com.comprix.util

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato do [TextoUtil], o utilitario de texto usado em TODO o app.
 *
 * As funcoes daqui sao a chave de comparacao de categorias, casamento do
 * dicionario de sinônimos, deduplicacao de produtos, busca da Lixeira e do
 * catalogo, sugestao de produtos parecidos e leitura de numeros/codigos em
 * rotulos OCR. Qualquer mudanca silenciosa aqui muda o comportamento de
 * meio aplicativo — por isso o contrato inteiro esta pinado abaixo, incluindo
 * as excentricidades deliberadas (o "%" sobrevive a normalizacao completa; a
 * normalizacao leve PRESERVA pontuacao; "1.299" e milhar, mas "7.50" e
 * decimal; barcode aceita ruido alfabetico do OCR).
 */
class TextoUtilTest {

    // ------------------------------------------------------------------
    // removerAcentos
    // ------------------------------------------------------------------

    @Test
    fun `removerAcentos tira diacriticos preservando as letras`() {
        assertEquals("acao", TextoUtil.removerAcentos("ação"))
    }

    @Test
    fun `removerAcentos cobre vogais acentuadas de todos os tipos`() {
        assertEquals("aeiou", TextoUtil.removerAcentos("àéîõü"))
    }

    @Test
    fun `removerAcentos deixa texto ascii intocado`() {
        assertEquals("arroz branco", TextoUtil.removerAcentos("arroz branco"))
    }

    // ------------------------------------------------------------------
    // normalizar (completa — a chave de comparacao do app)
    // ------------------------------------------------------------------

    @Test
    fun `normalizar junta caixa acento e pontuacao`() {
        assertEquals("acucar crystal", TextoUtil.normalizar("AÇÚCAR Crystal!"))
    }

    @Test
    fun `normalizar preserva o percentual`() {
        assertEquals("10% off", TextoUtil.normalizar("10% OFF"))
    }

    @Test
    fun `normalizar colapsa espacos e faz trim`() {
        assertEquals("varios espacos", TextoUtil.normalizar("  vários   espaços  "))
    }

    @Test
    fun `normalizar destrutura moeda em tokens soltos`() {
        assertEquals("r 24 90", TextoUtil.normalizar("R$ 24,90"))
    }

    @Test
    fun `normalizar de string vazia e vazia`() {
        assertEquals("", TextoUtil.normalizar(""))
    }

    // ------------------------------------------------------------------
    // normalizarLeve (pipeline de OCR — preserva pontuacao)
    // ------------------------------------------------------------------

    @Test
    fun `normalizarLeve preserva a pontuacao que carrega informacao`() {
        // Se apagasse pontuacao, "R$ 24,90" viraria "r 24 90" e "10/12/2026"
        // viraria "10 12 2026" — nenhum preco ou data seria reconhecido.
        assertEquals("r\$ 24,90", TextoUtil.normalizarLeve("R$ 24,90"))
        assertEquals("10/12/2026", TextoUtil.normalizarLeve("10/12/2026"))
    }

    @Test
    fun `normalizarLeve ainda tira acento e caixa`() {
        assertEquals("acucar", TextoUtil.normalizarLeve("AÇÚCAR"))
    }

    // ------------------------------------------------------------------
    // tokens
    // ------------------------------------------------------------------

    @Test
    fun `tokens quebra texto sujo em palavras limpas`() {
        assertEquals(
            listOf("arroz", "branco", "tipo", "1"),
            TextoUtil.tokens("  ARROZ   Branco, Tipo 1  "),
        )
    }

    @Test
    fun `tokens de texto sem palavras e lista vazia`() {
        assertTrue(TextoUtil.tokens("").isEmpty())
        assertTrue(TextoUtil.tokens("!!! ???").isEmpty())
    }

    // ------------------------------------------------------------------
    // correspondeBusca (busca da Lixeira e do catalogo)
    // ------------------------------------------------------------------

    @Test
    fun `correspondeBusca ignora acento e caixa`() {
        assertTrue(TextoUtil.correspondeBusca("Melão", "melao"))
        assertTrue(TextoUtil.correspondeBusca("MERCADO EXTRA", "mercado"))
    }

    @Test
    fun `correspondeBusca exige TODAS as palavras digitadas`() {
        assertFalse(TextoUtil.correspondeBusca("arroz", "arroz feijao"))
        assertTrue(TextoUtil.correspondeBusca("Arroz e Feijão", "arroz feijao"))
    }

    @Test
    fun `correspondeBusca aceita pedaco de palavra`() {
        assertTrue(TextoUtil.correspondeBusca("Biscoito Recheado", "reche"))
    }

    @Test
    fun `correspondeBusca com consulta em branco casa com tudo`() {
        assertTrue(TextoUtil.correspondeBusca("qualquer coisa", "   "))
        assertTrue(TextoUtil.correspondeBusca("", ""))
    }

    // ------------------------------------------------------------------
    // capitalizarTitulo (apenas exibicao)
    // ------------------------------------------------------------------

    @Test
    fun `capitalizarTitulo faz Title Case simples`() {
        assertEquals("Arroz Branco Tipo 1", TextoUtil.capitalizarTitulo("arroz branco tipo 1"))
    }

    @Test
    fun `capitalizarTitulo mantem preposicoes curtas em minuscula`() {
        assertEquals("Pão de Queijo da Vovó", TextoUtil.capitalizarTitulo("pão de queijo da vovó"))
    }

    @Test
    fun `capitalizarTitulo normaliza caixa alta de entrada`() {
        assertEquals("Feijão Preto", TextoUtil.capitalizarTitulo("FEIJÃO PRETO"))
    }

    @Test
    fun `capitalizarTitulo faz trim mas preserva espacos internos`() {
        assertEquals("Arroz  e Feijão", TextoUtil.capitalizarTitulo("  arroz  e feijão "))
    }

    // ------------------------------------------------------------------
    // paraDecimal (formatos brasileiros)
    // ------------------------------------------------------------------

    @Test
    fun `paraDecimal le virgula decimal`() {
        assertEquals(BigDecimal("12.90"), TextoUtil.paraDecimal("12,90"))
    }

    @Test
    fun `paraDecimal le ponto de milhar com virgula decimal`() {
        assertEquals(BigDecimal("1299.90"), TextoUtil.paraDecimal("1.299,90"))
    }

    @Test
    fun `paraDecimal ignora cifrao e espacos`() {
        assertEquals(BigDecimal("7.50"), TextoUtil.paraDecimal("R\$ 7,50"))
    }

    @Test
    fun `paraDecimal desambigua ponto sozinho por numero de casas`() {
        // "7.50": 2 casas -> decimal. "1.299": 3 casas e corpo curto -> milhar.
        assertEquals(BigDecimal("7.50"), TextoUtil.paraDecimal("7.50"))
        assertEquals(BigDecimal("1299"), TextoUtil.paraDecimal("1.299"))
    }

    @Test
    fun `paraDecimal nao confunde quatro casas com milhar`() {
        assertEquals(BigDecimal("1.2345"), TextoUtil.paraDecimal("1.2345"))
    }

    @Test
    fun `paraDecimal remove espaco duro nbsp do OCR`() {
        assertEquals(BigDecimal("1299.90"), TextoUtil.paraDecimal("1\u00A0299,90"))
    }

    @Test
    fun `paraDecimal aceita negativo`() {
        assertEquals(BigDecimal("-12.90"), TextoUtil.paraDecimal("-12,90"))
    }

    @Test
    fun `paraDecimal e tolerante a ruido alfabetico`() {
        // OCR sujo: o que nao e digito/sinal/ponto descarta, o resto vira numero.
        assertEquals(BigDecimal("1234"), TextoUtil.paraDecimal("12x34"))
    }

    @Test
    fun `paraDecimal devolve null para lixo`() {
        assertNull(TextoUtil.paraDecimal(null))
        assertNull(TextoUtil.paraDecimal(""))
        assertNull(TextoUtil.paraDecimal("   "))
        assertNull(TextoUtil.paraDecimal("abc"))
        assertNull(TextoUtil.paraDecimal("R\$"))
        assertNull(TextoUtil.paraDecimal("."))
        assertNull(TextoUtil.paraDecimal("-"))
    }

    // ------------------------------------------------------------------
    // distanciaEdicao / similaridade (sugestao de produtos parecidos)
    // ------------------------------------------------------------------

    @Test
    fun `distanciaEdicao e zero quando so muda acento e caixa`() {
        assertEquals(0, TextoUtil.distanciaEdicao("Melão", "melao"))
    }

    @Test
    fun `distanciaEdicao bate com o classico kitten-sitting`() {
        assertEquals(3, TextoUtil.distanciaEdicao("kitten", "sitting"))
    }

    @Test
    fun `distanciaEdicao contra vazio e o comprimento`() {
        assertEquals(3, TextoUtil.distanciaEdicao("abc", ""))
        assertEquals(3, TextoUtil.distanciaEdicao("", "abc"))
        assertEquals(0, TextoUtil.distanciaEdicao("", ""))
    }

    @Test
    fun `similaridade e 1 para identicas e para ambas vazias`() {
        assertEquals(1f, TextoUtil.similaridade("arroz", "arroz"), 0f)
        assertEquals(1f, TextoUtil.similaridade("", ""), 0f)
    }

    @Test
    fun `similaridade cai proporcional a edicao sobre a maior string`() {
        // "abc" vs "abd": 1 edicao sobre 3 caracteres.
        assertEquals(1f - 1f / 3f, TextoUtil.similaridade("abc", "abd"), 0.001f)
    }

    // ------------------------------------------------------------------
    // contemPalavra (texto JA normalizado — palavra inteira, nao pedaco)
    // ------------------------------------------------------------------

    @Test
    fun `contemPalavra exige a palavra inteira`() {
        assertTrue(TextoUtil.contemPalavra("arroz branco tipo 1", "branco"))
        assertFalse(TextoUtil.contemPalavra("arroz branco tipo 1", "branc"))
    }

    @Test
    fun `contemPalavra com frase usa contains direto`() {
        assertTrue(TextoUtil.contemPalavra("leite condensado", "leite condensado"))
        assertFalse(TextoUtil.contemPalavra("leite condensado", "condensado leite"))
    }

    @Test
    fun `contemPalavra separa tambem por traco e barra`() {
        assertTrue(TextoUtil.contemPalavra("leite-condensado", "condensado"))
        assertTrue(TextoUtil.contemPalavra("10/12/2026", "2026"))
    }

    @Test
    fun `contemPalavra com palavra vazia e falso`() {
        assertFalse(TextoUtil.contemPalavra("arroz", ""))
        assertFalse(TextoUtil.contemPalavra("arroz", "  "))
    }

    // ------------------------------------------------------------------
    // codigoDeBarrasValido (EAN-8 / UPC-A / EAN-13 / EAN-14)
    // ------------------------------------------------------------------

    @Test
    fun `codigoDeBarrasValido aceita EAN-13 valido e rejeita digito errado`() {
        assertTrue(TextoUtil.codigoDeBarrasValido("4006381333931"))
        assertFalse(TextoUtil.codigoDeBarrasValido("4006381333932"))
    }

    @Test
    fun `codigoDeBarrasValido cobre EAN-8 UPC-A e EAN-14`() {
        assertTrue(TextoUtil.codigoDeBarrasValido("73513537")) // EAN-8
        assertTrue(TextoUtil.codigoDeBarrasValido("036000291452")) // UPC-A (12)
        assertTrue(TextoUtil.codigoDeBarrasValido("04006381333931")) // EAN-14
    }

    @Test
    fun `codigoDeBarrasValido rejeita comprimentos fora do padrao`() {
        assertFalse(TextoUtil.codigoDeBarrasValido("4006381")) // 7
        assertFalse(TextoUtil.codigoDeBarrasValido("400638133")) // 9
        assertFalse(TextoUtil.codigoDeBarrasValido(null))
    }

    @Test
    fun `codigoDeBarrasValido e tolerante ao ruido do OCR`() {
        // Letras coladas sao descartadas; o que sobra decide.
        assertTrue(TextoUtil.codigoDeBarrasValido("EAN 4006381333931 x"))
        assertFalse(TextoUtil.codigoDeBarrasValido("sem numeros"))
    }
}

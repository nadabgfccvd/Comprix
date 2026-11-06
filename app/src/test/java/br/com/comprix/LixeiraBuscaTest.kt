package br.com.comprix

import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.lixeira.FiltroDaLixeira
import br.com.comprix.util.TextoUtil
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM puros da busca e do aviso de prazo da Lixeira
 * ([FiltroDaLixeira]): a busca precisa achar "Café" digitando "cafe" (o
 * mesmo padrao amigavel do catalogo), o prazo precisa contar dias de
 * calendario e o selo urgente so existe a uma semana ou menos do fim.
 */
class LixeiraBuscaTest {

    private fun registro(
        titulo: String = "Compra do mês",
        detalhe: String? = "12 itens · R$ 214,90",
        enviadaEm: Long = 0L,
    ) = RegistroDeLixeiraEntity(
        id = 1,
        tipo = "LISTAS",
        titulo = titulo,
        detalhe = detalhe,
        refId = 0,
        payload = "{}",
        enviadaEm = enviadaEm,
    )

    // --- busca -------------------------------------------------------------------------

    @Test
    fun `busca em branco casa com tudo`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(), ""))
        assertTrue(FiltroDaLixeira.corresponde(registro(), "   "))
    }

    @Test
    fun `busca ignora acento e maiuscula no titulo`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(titulo = "Café 3 Corações"), "CAFE"))
        assertTrue(FiltroDaLixeira.corresponde(registro(), "MÊS"))
    }

    @Test
    fun `busca encontra pelo detalhe`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(), "214"))
    }

    @Test
    fun `busca por palavra que nao existe nao casa`() {
        assertFalse(FiltroDaLixeira.corresponde(registro(), "feijao"))
    }

    @Test
    fun `todas as palavras da busca precisam aparecer`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(), "compra mes"))
        assertFalse(FiltroDaLixeira.corresponde(registro(), "compra feijao"))
    }

    @Test
    fun `busca casa por pedaco de palavra`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(), "mes"))
    }

    @Test
    fun `registro sem detalhe nao quebra a busca`() {
        assertTrue(FiltroDaLixeira.corresponde(registro(detalhe = null), "compra"))
        assertFalse(FiltroDaLixeira.corresponde(registro(detalhe = null), "qualquer coisa fora"))
    }

    // ---------------------------------------------------------------------------------
    // sinonimos: mesma receita do Historico e de "Seus produtos" — alternativas
    // precomputadas por emissao (alternativasDe) e consumidas por corresponde.
    // ---------------------------------------------------------------------------------

    /** Sinonimos de teste, no mesmo espirito do acervo de fabrica. */
    private val sinonimosDeTeste: (String) -> List<String> = { consulta ->
        when (TextoUtil.normalizar(consulta)) {
            "mexerica" -> listOf("tangerina", "bergamota")
            "tangerina" -> listOf("mexerica", "bergamota")
            else -> emptyList()
        }
    }

    @Test
    fun `consulta pelo sinonimo encontra a lista apagada pelo nome oficial`() {
        val alternativas = FiltroDaLixeira.alternativasDe("mexerica", sinonimosDeTeste)

        assertTrue(
            FiltroDaLixeira.corresponde(registro(titulo = "Tangerina da feira"), "mexerica", alternativas),
        )
    }

    @Test
    fun `consulta pelo nome oficial encontra a lista apagada pelo sinonimo`() {
        val alternativas = FiltroDaLixeira.alternativasDe("tangerina", sinonimosDeTeste)

        assertTrue(
            FiltroDaLixeira.corresponde(registro(titulo = "Mexerica do sitio"), "tangerina", alternativas),
        )
    }

    @Test
    fun `consulta sem sinonimo ou sem candidatos se comporta como antes`() {
        // Sem candidatos: alternativas vazias, busca direta segue valendo.
        assertTrue(FiltroDaLixeira.alternativasDe("mexerica", null).isEmpty())
        assertFalse(FiltroDaLixeira.corresponde(registro(titulo = "Tangerina da feira"), "mexerica"))
        // Com candidatos que nao resolvem: nada se expande, direta continua.
        val vazias = FiltroDaLixeira.alternativasDe("feijao", sinonimosDeTeste)
        assertTrue(vazias.isEmpty())
        assertTrue(FiltroDaLixeira.corresponde(registro(titulo = "Feijao carioca"), "feijao", vazias))
    }

    @Test
    fun `consulta em branco nao pede alternativas e frase inteira nao expande`() {
        // Consulta em branco: alternativasDe nao chama a funcao de sinonimos.
        assertTrue(FiltroDaLixeira.alternativasDe("   ", sinonimosDeTeste).isEmpty())
        // "suco de mexerica" nao resolve como frase inteira: sem expansao.
        val alternativas = FiltroDaLixeira.alternativasDe("suco de mexerica", sinonimosDeTeste)
        assertTrue(alternativas.isEmpty())
        assertFalse(
            FiltroDaLixeira.corresponde(registro(titulo = "Tangerina da feira"), "suco de mexerica", alternativas),
        )
    }

    // --- dias restantes ------------------------------------------------------------------

    @Test
    fun `expiracao hoje deixa zero dia`() {
        val agora = LocalDateTime.of(2026, 11, 6, 8, 0)
        val expira = LocalDateTime.of(2026, 11, 6, 23, 59)
        assertEquals(0L, FiltroDaLixeira.diasRestantes(expira, agora))
    }

    @Test
    fun `expiracao amanha deixa um dia mesmo de noite`() {
        val agora = LocalDateTime.of(2026, 11, 6, 23, 0)
        val expira = LocalDateTime.of(2026, 11, 7, 9, 0)
        assertEquals(1L, FiltroDaLixeira.diasRestantes(expira, agora))
    }

    @Test
    fun `virada de mes conta dias de calendario`() {
        val agora = LocalDateTime.of(2026, 11, 30, 20, 0)
        val expira = LocalDateTime.of(2026, 12, 1, 8, 0)
        assertEquals(1L, FiltroDaLixeira.diasRestantes(expira, agora))
    }

    // --- selo de prazo -------------------------------------------------------------------

    @Test
    fun `expira hoje e selo vermelho de urgencia`() {
        val selo = FiltroDaLixeira.prazo(0)!!
        assertEquals("some hoje", selo.rotulo)
        assertEquals(TomDoSelo.ALERTA, selo.tom)
    }

    @Test
    fun `registro atrasado e mostrado como se expirasse hoje`() {
        // Corrida com a purga: se um registro vencido aparece, o rotulo nao
        // pode prometer dias que nao existem.
        assertEquals("some hoje", FiltroDaLixeira.prazo(-1)!!.rotulo)
    }

    @Test
    fun `expira amanha tem rotulo proprio`() {
        val selo = FiltroDaLixeira.prazo(1)!!
        assertEquals("some amanhã", selo.rotulo)
        assertEquals(TomDoSelo.ALERTA, selo.tom)
    }

    @Test
    fun `tres dias ou menos segue vermelho`() {
        assertEquals(TomDoSelo.ALERTA, FiltroDaLixeira.prazo(3)!!.tom)
        assertEquals("some em 3 dias", FiltroDaLixeira.prazo(3)!!.rotulo)
    }

    @Test
    fun `de quatro a sete dias o aviso e ambar`() {
        assertEquals(TomDoSelo.OURO, FiltroDaLixeira.prazo(4)!!.tom)
        assertEquals(TomDoSelo.OURO, FiltroDaLixeira.prazo(FiltroDaLixeira.DIAS_URGENTE)!!.tom)
        assertEquals("some em 7 dias", FiltroDaLixeira.prazo(7)!!.rotulo)
    }

    @Test
    fun `mais de uma semana nao ganha selo`() {
        assertNull(FiltroDaLixeira.prazo(FiltroDaLixeira.DIAS_URGENTE + 1))
        assertNull(FiltroDaLixeira.prazo(30))
    }
}

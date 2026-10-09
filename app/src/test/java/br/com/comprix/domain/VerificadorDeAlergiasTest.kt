package br.com.comprix.domain

import br.com.comprix.domain.alergia.VerificadorDeAlergias
import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.util.TextoUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alertas de restricao alimentar (Secao 4.1 + restricoes customizadas do
 * usuario): gluten, alergenos oficiais RDC 26/2015 e alergias proprias.
 */
class VerificadorDeAlergiasTest {

    private fun produto(
        nome: String,
        ingredientes: String? = null,
        gluten: IndicacaoGluten = IndicacaoGluten.INDETERMINADO,
        alergenos: List<Alergeno> = emptyList(),
    ) = Produto(
        id = 1,
        nome = nome,
        nomeNormalizado = TextoUtil.normalizar(nome),
        categoriaId = 1,
        ingredientes = ingredientes,
        gluten = gluten,
        alergenos = alergenos,
    )

    // --- alergenos oficiais (RDC 26/2015) ------------------------------------------------

    @Test
    fun `alergeno oficial marcado no perfil dispara exatamente um alerta`() {
        val iogurte = produto(nome = "Iogurte de morango", alergenos = listOf(Alergeno.LEITE))
        val perfil = PerfilRestricoes(alergenos = setOf(Alergeno.LEITE))

        val alertas = VerificadorDeAlergias.verificar(iogurte, perfil)

        assertEquals(listOf("Contém leite"), alertas)
        assertEquals("Contém leite", VerificadorDeAlergias.alertaUnico(iogurte, perfil))
    }

    @Test
    fun `varios alergenos oficiais entram na mesma linha como hoje`() {
        val bolo = produto(nome = "Bolo pronto", alergenos = listOf(Alergeno.LEITE, Alergeno.OVO, Alergeno.SOJA))
        val perfil = PerfilRestricoes(alergenos = setOf(Alergeno.LEITE, Alergeno.SOJA))

        assertEquals(listOf("Contém leite, soja"), VerificadorDeAlergias.verificar(bolo, perfil))
    }

    @Test
    fun `produto sem conflito com perfil ativo nao alerta`() {
        val arroz = produto(nome = "Arroz branco", alergenos = emptyList())
        val perfil = PerfilRestricoes(alergenos = setOf(Alergeno.LEITE))

        assertTrue(VerificadorDeAlergias.verificar(arroz, perfil).isEmpty())
        assertNull(VerificadorDeAlergias.alertaUnico(arroz, perfil))
    }

    // --- gluten --------------------------------------------------------------------------

    @Test
    fun `sem gluten com rotulo contem gluten alerta a restricao do usuario`() {
        val pao = produto(nome = "Pão francês", gluten = IndicacaoGluten.CONTEM)
        val perfil = PerfilRestricoes(semGluten = true)

        assertEquals(listOf("Contém glúten (restrição sua)"), VerificadorDeAlergias.verificar(pao, perfil))
    }

    @Test
    fun `sem gluten com rotulo indeterminado pede conferencia do rotulo`() {
        val pate = produto(nome = "Patê de azeitona", gluten = IndicacaoGluten.INDETERMINADO)
        val perfil = PerfilRestricoes(semGluten = true)

        assertEquals(
            listOf("Glúten não informado — confira o rótulo"),
            VerificadorDeAlergias.verificar(pate, perfil),
        )
    }

    @Test
    fun `sem gluten com rotulo nao contem nao alerta`() {
        val arroz = produto(nome = "Arroz", gluten = IndicacaoGluten.NAO_CONTEM)
        val perfil = PerfilRestricoes(semGluten = true)

        assertTrue(VerificadorDeAlergias.verificar(arroz, perfil).isEmpty())
    }

    @Test
    fun `aviso de gluten indeterminado so aparece quando o perfil marca sem gluten`() {
        // Perfil vazio (inativo) e perfil sem semGluten: nenhum aviso de gluten.
        val macarrao = produto(nome = "Macarrão", gluten = IndicacaoGluten.INDETERMINADO)

        assertTrue(VerificadorDeAlergias.verificar(macarrao, PerfilRestricoes()).isEmpty())
        assertTrue(VerificadorDeAlergias.verificar(macarrao, PerfilRestricoes(alergenos = setOf(Alergeno.LEITE))).isEmpty())
    }

    @Test
    fun `alerta de gluten nao se duplica quando o alergeno oficial tambem esta marcado`() {
        // Gluten CONTEM ja diz o fato; a intersecao oficial nao repete "Contém glúten".
        val pao = produto(nome = "Pão de forma", gluten = IndicacaoGluten.CONTEM, alergenos = listOf(Alergeno.GLUTEN))
        val perfil = PerfilRestricoes(semGluten = true, alergenos = setOf(Alergeno.GLUTEN))

        assertEquals(listOf("Contém glúten (restrição sua)"), VerificadorDeAlergias.verificar(pao, perfil))
    }

    // --- restricoes customizadas ---------------------------------------------------------

    @Test
    fun `restricao customizada bate nos ingredientes por frase`() {
        val lactose = AlergiaCustomizada(nome = "Lactose", palavras = listOf("lactose", "soro de leite"))
        val perfil = PerfilRestricoes(customizadas = listOf(lactose))

        val achocolatado = produto(nome = "Achocolatado", ingredientes = "contém soro de leite e aroma")
        val alertas = VerificadorDeAlergias.verificar(achocolatado, perfil)
        assertEquals(1, alertas.size)
        assertTrue(alertas.single().startsWith("Pode conter Lactose"))
        assertTrue(alertas.single().contains("soro de leite"))

        val farinha = produto(nome = "Farinha de milho", ingredientes = "milho, sal e ferro")
        assertTrue(VerificadorDeAlergias.verificar(farinha, perfil).isEmpty())
    }

    @Test
    fun `nome da restricao customizada sempre entra na busca`() {
        // Sem palavras extras: o proprio nome "Mel" denuncia o produto.
        val mel = AlergiaCustomizada(nome = "Mel")
        val perfil = PerfilRestricoes(customizadas = listOf(mel))

        val alertas = VerificadorDeAlergias.verificar(produto(nome = "Mel puro"), perfil)
        assertEquals(listOf("Pode conter Mel (encontrei \"Mel\")"), alertas)
    }

    @Test
    fun `perfil com apenas restricoes customizadas e considerado ativo`() {
        val perfil = PerfilRestricoes(
            customizadas = listOf(AlergiaCustomizada(nome = "Corante amarelo", palavras = listOf("tartrazina"))),
        )

        assertTrue(perfil.ativo)
        assertTrue(VerificadorDeAlergias.verificar(produto(nome = "Refrigerante", ingredientes = "tartrazina"), perfil).isNotEmpty())
    }

    // --- normalizacao --------------------------------------------------------------------

    @Test
    fun `normalizacao - palavra nao casa por pedaco e palavra acentada casa sem acento`() {
        val perfil = PerfilRestricoes(
            customizadas = listOf(
                AlergiaCustomizada(nome = "Mel", palavras = listOf("abelha", "proteína do leite")),
            ),
        )

        // "abelha" (palavra inteira) NAO bate em "abelhas" - nao casa por substring.
        val propolis = produto(nome = "Própolis", ingredientes = "extrato de abelhas e cera")
        assertTrue(VerificadorDeAlergias.verificar(propolis, perfil).isEmpty())

        // "proteína do leite" (com acento) bate em "proteina do leite" (sem acento).
        val suplemento = produto(nome = "Suplemento", ingredientes = "contém proteína do leite e vitamina C")
        val alertas = VerificadorDeAlergias.verificar(suplemento, perfil)
        assertEquals(1, alertas.size)
        assertTrue(alertas.single().startsWith("Pode conter Mel"))
        assertTrue(alertas.single().contains("proteína do leite"))
    }

    // --- compatibilidade com o MotorDePrecos ----------------------------------------------

    @Test
    fun `motor de precos continua alertando pelo mesmo caminho`() {
        val iogurte = produto(nome = "Iogurte", alergenos = listOf(Alergeno.LEITE))
        val itemComProduto = ItemComProduto(
            item = ItemDaLista(listaId = 1, produtoId = 1),
            produto = iogurte,
        )

        val alerta = MotorDePrecos.alertaDeRestricao(itemComProduto, PerfilRestricoes(alergenos = setOf(Alergeno.LEITE)))
        assertEquals("Contém leite", alerta)

        // E continua silencioso quando o perfil nao conflita.
        assertNull(MotorDePrecos.alertaDeRestricao(itemComProduto, PerfilRestricoes(alergenos = setOf(Alergeno.SOJA))))
    }
}

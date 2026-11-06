package br.com.comprix.domain.catalogo

import br.com.comprix.domain.modelo.Produto
import br.com.comprix.util.TextoUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da secao "Seus produtos" da busca do catalogo: casamento amigavel
 * (sem acento, sem caixa, multi-palavra com E), ordenacao, teto e o
 * desaparecimento da secao sem consulta. Tudo na JVM, objeto puro.
 */
class FiltroDeProdutosLocaisTest {

    private fun produto(nome: String, id: Long = 0) =
        Produto(nome = nome, nomeNormalizado = nome, categoriaId = 1L, id = id)

    private val acervo = listOf(
        produto("Queijo da fazenda", 1),
        produto("Água mineral 500ml", 2),
        produto("Bolacha do Zé", 3),
        produto("agua tonica zero", 4),
    )

    @Test
    fun `sem consulta a secao nao existe`() {
        assertTrue(FiltroDeProdutosLocais.filtrar("", acervo).isEmpty())
        assertTrue(FiltroDeProdutosLocais.filtrar("   ", acervo).isEmpty())
    }

    @Test
    fun `busca ignora acento e caixa`() {
        // A ordenacao e pelo nome normalizado: "agua mineral..." vem antes de
        // "agua tonica..." independentemente do acento exibido.
        val nomes = FiltroDeProdutosLocais.filtrar("agua", acervo).map { it.nome }
        assertEquals(listOf("Água mineral 500ml", "agua tonica zero"), nomes)
    }

    @Test
    fun `cada palavra digitada precisa aparecer - E, nao OU`() {
        val nomes = FiltroDeProdutosLocais.filtrar("queijo faz", acervo).map { it.nome }
        assertEquals(listOf("Queijo da fazenda"), nomes)
        assertTrue(FiltroDeProdutosLocais.filtrar("queijo bolacha", acervo).isEmpty())
    }

    @Test
    fun `ordenacao e por nome normalizado`() {
        val nomes = FiltroDeProdutosLocais.filtrar("a", acervo).map { it.nome }
        val esperado = acervo
            .filter { TextoUtil.correspondeBusca(it.nome, "a") }
            .sortedBy { TextoUtil.normalizar(it.nome) }
            .map { it.nome }
        assertEquals(esperado, nomes)
    }

    @Test
    fun `teto da secao respeita o limite`() {
        val muitos = (1..30).map { produto("Produto $it", it.toLong()) }
        val saida = FiltroDeProdutosLocais.filtrar("produto", muitos)
        assertEquals(FiltroDeProdutosLocais.LIMITE_PADRAO, saida.size)
        assertEquals(
            5,
            FiltroDeProdutosLocais.filtrar("produto", muitos, 5).size,
        )
    }

    @Test
    fun `acervo sem casamento devolve vazio sem erro`() {
        assertTrue(FiltroDeProdutosLocais.filtrar("xilofone", acervo).isEmpty())
        assertTrue(FiltroDeProdutosLocais.filtrar("agua", emptyList()).isEmpty())
    }
}

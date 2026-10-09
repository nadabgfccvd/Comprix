package br.com.comprix

import br.com.comprix.data.repositorio.ListaRepositorio
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros da numeracao de nomes ao duplicar listas - exercitam a
 * funcao pura [ListaRepositorio.proximoNomeDeCopia] sem DAO, sem banco e sem
 * Android. A regua e o relato do usuario: duplicar a copia da copia empilhava
 * " (copia)" no fim do nome ("Compra do Mês (cópia) (cópia) (cópia)...").
 */
class ListasTest {

    @Test
    fun `primeira copia fica sem numero`() {
        val nome = ListaRepositorio.proximoNomeDeCopia(listOf("Compra do Mês"), "Compra do Mês")
        assertEquals("Compra do Mês (cópia)", nome)
    }

    @Test
    fun `base acumulada perde os sufixos antes de numerar`() {
        // Cenario do relato: varias duplicacoes seguidas empilharam sufixos;
        // a base volta a ser "Compra do Mês" e a numeracao segue do maior numero.
        val nomes = listOf(
            "Compra do Mês",
            "Compra do Mês (cópia)",
            "Compra do Mês (cópia) (cópia)",
            "Compra do Mês (cópia) (cópia) (cópia 3)",
        )
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "Compra do Mês (cópia) (cópia) (cópia 3)")
        assertEquals("Compra do Mês (cópia 4)", nome)
    }

    @Test
    fun `colisao com copia existente empurra para o proximo numero`() {
        val nomes = listOf("Feira", "Feira (cópia)")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "Feira")
        assertEquals("Feira (cópia 2)", nome)
    }

    @Test
    fun `base com copia 5 existente recebe copia 6`() {
        val nomes = listOf("Feira", "Feira (cópia 5)")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "Feira")
        assertEquals("Feira (cópia 6)", nome)
    }

    @Test
    fun `sequencia longa pula para o proximo numero mesmo com buracos`() {
        // Numeracao pode ter buracos (lista renomeada, copia excluida); o
        // proximo nome olha o MAIOR indice, nao a primeira lacuna.
        val nomes = listOf("A", "A (cópia)", "A (cópia 3)", "A (cópia 28)")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "A (cópia 3)")
        assertEquals("A (cópia 29)", nome)
    }

    @Test
    fun `sufixo sem acento tambem conta como copia`() {
        val nomes = listOf("X", "X (copia)")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "X")
        assertEquals("X (cópia 2)", nome)
    }

    @Test
    fun `nome de outra base nao interfere na numeracao`() {
        val nomes = listOf("Compra do Mês", "Feira (cópia)", "Feira (cópia 7)")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "Compra do Mês")
        assertEquals("Compra do Mês (cópia)", nome)
    }

    @Test
    fun `base pura entre as existentes vale zero e a primeira copia nao ganha numero`() {
        val nomes = listOf("A", "B (cópia 2)", "C")
        val nome = ListaRepositorio.proximoNomeDeCopia(nomes, "A")
        assertEquals("A (cópia)", nome)
    }
}

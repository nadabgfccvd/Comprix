package br.com.comprix.domain

import br.com.comprix.domain.catalogo.CatalogoSemente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalogo-semente: parse do formato linha-a-linha, indice de busca e o elo
 * item -> subcategoria -> setor -> categoria Comprix.
 *
 * O texto de teste e embutido (nao vai ler o `res/raw`, que e coisa de
 * Android): assim o parse e a busca sao cobertos em JVM puro, igual aos
 * outros testes de dominio.
 */
class CatalogoSementeTest {

    private companion object {
        // 1 setor, 2 subcategorias, 4 itens e 1 apelido - e a CONTAGEM bate.
        val TEXTO_OK = """
            # comprix catalogo-semente de teste
            # CONTAGEM 1 2 4 1
            SETOR|1|Hortifruti|Hortifruti
            SUB|1|Frutas|1
            SUB|2|Legumes e raízes|1
            ITEM|Melão amarelo|1
            ITEM|Melancia|1
            ITEM|Banana-prata|1
            ITEM|Tomate Italiano|2
            ALIAS|melao|Melão amarelo
        """.trimIndent().trim()

        // Cabecalho mente (esperava 9 de tudo); o parse nao pode lancar.
        val TEXTO_DIVERGENTE = """
            # comprix catalogo-semente de teste
            # CONTAGEM 9 9 9 9
            SETOR|1|Hortifruti|Hortifruti
            SUB|1|Frutas|1
            ITEM|Melão amarelo|1
        """.trimIndent().trim()

        // Sinonimos em coluna propria (formato novo) e colados no nome (formato
        // da semente 1.2.0) - os dois caem no mesmo campo normalizado.
        val TEXTO_SINONIMOS = """
            # CONTAGEM 1 2 3 0
            SETOR|1|Hortifruti|Hortifruti
            SUB|1|Frutas|1
            SUB|2|Legumes|1
            ITEM|Tangerina|1|mexerica, bergamota
            ITEM|Salsão — também buscar: aipo|2
            ITEM|Melancia|1
        """.trimIndent().trim()

        // Um item cujo nome e o MESMO sinonimo de outro: nome direto vence.
        val TEXTO_COLISAO = """
            # CONTAGEM 1 1 2 0
            SETOR|1|Hortifruti|Hortifruti
            SUB|1|Frutas|1
            ITEM|Tangerina|1|mexerica, bergamota
            ITEM|Mexerica|1
        """.trimIndent().trim()
    }

    @Test
    fun `parseia setores, subcategorias, itens e alias`() {
        val catalogo = CatalogoSemente(TEXTO_OK)

        assertEquals(1, catalogo.setores.size)
        assertEquals("Hortifruti", catalogo.setores.first().nome)
        assertEquals("Hortifruti", catalogo.setores.first().categoriaComprix)

        assertEquals(2, catalogo.subcategorias.size)
        assertEquals(4, catalogo.itens.size)
        assertEquals(4, catalogo.contarItens())
        assertTrue(catalogo.contagemConfere)

        val itensDeFrutas = catalogo.itensPorSubcategoria[1].orEmpty()
        assertEquals(3, itensDeFrutas.size)
        assertEquals(2, catalogo.subcategoriasPorSetor[1].orEmpty().size)

        // Retrocompatibilidade: linha sem anotacao continua igual, sem sinonimos.
        assertTrue(catalogo.itens.all { it.sinonimos.isEmpty() })
    }

    @Test
    fun `sinonimos da coluna propria entram no parse normalizados`() {
        val catalogo = CatalogoSemente(TEXTO_SINONIMOS)

        val tangerina = catalogo.buscar("Tangerina", 1).first()
        assertEquals("Tangerina", tangerina.nome)
        assertEquals(listOf("mexerica", "bergamota"), tangerina.sinonimos)

        // Item sem anotacao nenhuma: lista vazia, nunca null.
        assertTrue(catalogo.buscar("Melancia", 1).first().sinonimos.isEmpty())
        assertEquals(3, catalogo.contarItens())
        assertTrue(catalogo.contagemConfere)
    }

    @Test
    fun `itemPorNome resolve o nome exato com os sinonimos parseados`() {
        val catalogo = CatalogoSemente(TEXTO_SINONIMOS)

        // Nome exato, qualquer caixa: e o caminho que alimenta a legenda
        // "tambem buscar: mexerica, bergamota" na ficha do produto.
        val tangerina = catalogo.itemPorNome("TANGERINA")!!
        assertEquals("Tangerina", tangerina.nome)
        assertEquals(listOf("mexerica", "bergamota"), tangerina.sinonimos)

        // Nome digitado pelo APELIDO: o item oficial segue carregando os
        // sinonimos; quem remove o nome repetido da lista e o repositorio.
        assertEquals("Tangerina", catalogo.itemPorSinonimo("mexerica")!!.nome)
        assertEquals(listOf("mexerica", "bergamota"), catalogo.itemPorSinonimo("Mexerica")!!.sinonimos)

        // Resolucao por nome e EXATA - prefixo nao canoniza produto.
        assertNull(catalogo.itemPorNome("tangeri"))
        assertNull(catalogo.itemPorNome(""))
        assertNull(catalogo.itemPorNome("abacaxi"))
    }

    @Test
    fun `anotacao colada no nome e separada e vira sinonimo`() {
        val catalogo = CatalogoSemente(TEXTO_SINONIMOS)

        val salsao = catalogo.buscar("Salsão", 1).first()
        assertEquals("Salsão", salsao.nome)
        assertEquals(listOf("aipo"), salsao.sinonimos)
        assertEquals(3, catalogo.contarItens())
    }

    @Test
    fun `busca por sinonimo resolve o produto oficial`() {
        val catalogo = CatalogoSemente(TEXTO_SINONIMOS)

        // Exato, com e sem acento e caixa.
        assertEquals(listOf("Tangerina"), catalogo.buscar("mexerica").map { it.nome })
        assertEquals(listOf("Tangerina"), catalogo.buscar("MEXERICA").map { it.nome })
        assertEquals(listOf("Tangerina"), catalogo.buscar("bergamota").map { it.nome })
        assertEquals(listOf("Salsão"), catalogo.buscar("aipo").map { it.nome })

        // Prefixo de sinonimo (digitacao incremental) e frase com sinonimo dentro.
        assertEquals(listOf("Tangerina"), catalogo.buscar("mexeri").map { it.nome })
        assertEquals(listOf("Tangerina"), catalogo.buscar("1 kg de mexerica").map { it.nome })
    }

    @Test
    fun `nome direto vence sinonimo e sinonimo colidante e descartado`() {
        val catalogo = CatalogoSemente(TEXTO_COLISAO)

        // "Mexerica" e um item de verdade: exato por nome vem antes de qualquer
        // sinonimo da Tangerina...
        assertEquals("Mexerica", catalogo.buscar("mexerica").first().nome)
        // ...e o sinonimo colidante sai do indice (itemPorSinonimo devolve null,
        // nomes diretos nunca passam por ali), enquanto "bergamota" segue
        // apontando para a Tangerina.
        assertNull(catalogo.itemPorSinonimo("mexerica"))
        assertEquals("Tangerina", catalogo.itemPorSinonimo("bergamota")?.nome)
    }

    @Test
    fun `itemPorSinonimo devolve null quando o termo nao e sinonimo`() {
        val catalogo = CatalogoSemente(TEXTO_SINONIMOS)

        // Nome direto do acervo nao e sinonimo de ninguem.
        assertNull(catalogo.itemPorSinonimo("melancia"))
        assertNull(catalogo.itemPorSinonimo("abacaxi"))
        assertNull(catalogo.itemPorSinonimo(""))
    }

    @Test
    fun `separarAnotacaoDeSinonimos limpa o nome nos formatos documentados`() {
        // Formato real da semente 1.2.0 (em dash, com acento).
        assertEquals(
            "Tangerina" to listOf("mexerica", "bergamota"),
            CatalogoSemente.separarAnotacaoDeSinonimos("Tangerina — também buscar: mexerica, bergamota"),
        )

        // Hifen simples, sem acento - e nome hifenizado legitimo preservado.
        assertEquals(
            "Lava-roupas líquido" to listOf("sabao liquido"),
            CatalogoSemente.separarAnotacaoDeSinonimos("Lava-roupas líquido - também buscar: Sabão líquido"),
        )

        // En dash e espacos variaveis.
        assertEquals(
            "Batata-baroa" to listOf("mandioquinha"),
            CatalogoSemente.separarAnotacaoDeSinonimos("Batata-baroa  –  tambem buscar:  mandioquinha"),
        )

        // Sem anotacao: nome intacto, lista vazia.
        assertEquals("Melancia" to emptyList<String>(), CatalogoSemente.separarAnotacaoDeSinonimos("Melancia"))
    }

    @Test
    fun `categoria do item vem do setor da subcategoria`() {
        val catalogo = CatalogoSemente(TEXTO_OK)
        val melao = catalogo.buscar("Melão amarelo", 1).first()
        assertEquals("Hortifruti", catalogo.categoriaDe(melao))
    }

    @Test
    fun `categoriaDe devolve null quando o elo quebra`() {
        val quebrado = """
            SETOR|1|Hortifruti|Hortifruti
            ITEM|Melão amarelo|42
        """.trimIndent()
        val catalogo = CatalogoSemente(quebrado)
        val melao = catalogo.itens.first()
        assertNull(catalogo.categoriaDe(melao))
    }

    @Test
    fun `busca exata vence prefixo, que vence contem`() {
        val catalogo = CatalogoSemente(TEXTO_OK)

        // Exato (com acento e caixa).
        assertEquals("Melão amarelo", catalogo.buscar("melão amarelo").first().nome)

        // Prefixo: "mel" comeca em Melao e Melancia, na ordem do arquivo.
        val prefixo = catalogo.buscar("mel")
        assertEquals(listOf("Melão amarelo", "Melancia"), prefixo.map { it.nome })

        // Contem: "ancia" so esta no meio de Melancia.
        assertEquals(listOf("Melancia"), catalogo.buscar("ancia").map { it.nome })

        // Hifen vira espaco na normalizacao.
        assertEquals("Banana-prata", catalogo.buscar("BANANA PRATA").first().nome)
    }

    @Test
    fun `alias resolve sem acento e sem caixa`() {
        val catalogo = CatalogoSemente(TEXTO_OK)
        val achados = catalogo.buscar("MELAO")
        assertEquals(listOf("Melão amarelo"), achados.map { it.nome })
    }

    @Test
    fun `termo em branco nao acha nada`() {
        val catalogo = CatalogoSemente(TEXTO_OK)
        assertTrue(catalogo.buscar("").isEmpty())
        assertTrue(catalogo.buscar("   ").isEmpty())
    }

    @Test
    fun `limite e respeitado e sem duplicatas`() {
        val catalogo = CatalogoSemente(TEXTO_OK)
        val limitado = catalogo.buscar("a", limite = 2)
        assertEquals(2, limitado.size)
        assertEquals(limitado.size, limitado.distinct().size)
    }

    @Test
    fun `contagem divergente do cabecalho nao lanca e mantem o que parseou`() {
        val catalogo = CatalogoSemente(TEXTO_DIVERGENTE)

        assertEquals(1, catalogo.setores.size)
        assertEquals(1, catalogo.subcategorias.size)
        assertEquals(1, catalogo.contarItens())
        assertNotNull(catalogo.buscar("melao", 1).firstOrNull())
        assertFalse(catalogo.contagemConfere)
    }

    @Test
    fun `linhas malformadas sao ignoradas sem quebrar o parse`() {
        val sujo = """
            # CONTAGEM 1 1 1 0
            SETOR|1|Hortifruti|Hortifruti
            LIXO|sem|sentido
            SUB|1|Frutas|1
            ITEM
            ITEM|Melão amarelo|1
        """.trimIndent()
        val catalogo = CatalogoSemente(sujo)
        assertEquals(1, catalogo.setores.size)
        assertEquals(1, catalogo.subcategorias.size)
        assertEquals(1, catalogo.itens.size)
    }
}

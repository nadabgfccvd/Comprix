package br.com.comprix.domain.catalogo

import br.com.comprix.domain.modelo.Produto
import br.com.comprix.util.TextoUtil

/**
 * Logica pura da secao **"Seus produtos"** da busca na exploracao do
 * catalogo - isolada da tela e do ViewModel para ser testada direto na JVM
 * (mesma receita de `FiltroDeHistorico` e `FiltroDaLixeira`).
 *
 * ## Por que existe
 * A tela de exploracao listava so o acervo de fabrica (1.714 itens do
 * catalogo-semente): um produto que a pessoa mesma criou ("Bolacha do Zé",
 * "Queijo da fazenda") era invisivel na busca, por mais que aparecesse nas
 * listas. Esta secao devolve esses produtos a mesma busca, SEM mutar o
 * acervo: o acervo segue com suas subcategorias e o usuario reconhece o
 * que e dele pelo cabecalho proprio.
 *
 * ## Critério de casamento
 * O mesmo das buscas da Lixeira e do Historico
 * ([TextoUtil.correspondeBusca]): cada palavra digitada precisa aparecer no
 * nome do produto, sem acento e sem se importar com maiusculas - "queijo
 * faz" acha "Queijo da fazenda". Sem consulta, a secao NAO existe: a
 * exploracao aberta e conteudo do acervo, e os produtos locais so entram
 * quando a pessoa procura.
 */
object FiltroDeProdutosLocais {

    /** Teto da secao: a busca do acervo tem teto proprio (120) e a local segue pequena. */
    const val LIMITE_PADRAO = 12

    /**
     * Produtos do acervo LOCAL que casam com a consulta, ordenados por nome
     * (normalizado, para "Água mineral" e "agua Mineral" ficarem juntos) e
     * limitados. Consulta em branco devolve vazio - a secao some.
     *
     * ## Sinonimos (mesma receita do Historico)
     * [candidatos], quando presente, recebe a consulta e devolve termos
     * alternativos do acervo de fabrica ("mexerica" -> "tangerina,
     * bergamota") - na pratica `CatalogoRepositorio.sinonimosDe`, que e
     * bidirecional. O produto passa se a consulta direta OU QUALQUER
     * alternativa casar: "tangerina" acha "Mexerica da feira" gravada pelo
     * usuario, e vice-versa. Duas regras deliberadas: expansao pela consulta
     * INTEIRA (nunca por palavra solta) e `null` = comportamento de antes
     * (regressao zero). Detalhe em `FiltroDeHistorico.comprasVisiveis`,
     * que estabeleceu a receita.
     */
    fun filtrar(
        consulta: String,
        produtos: List<Produto>,
        limite: Int = LIMITE_PADRAO,
        candidatos: ((String) -> List<String>)? = null,
    ): List<Produto> {
        if (consulta.isBlank()) return emptyList()
        val alternativas = candidatos?.invoke(consulta).orEmpty().filter { it.isNotBlank() }
        return produtos
            .filter { casa(it.nome, consulta, alternativas) }
            .sortedBy { TextoUtil.normalizar(it.nome) }
            .take(limite)
    }

    /** Busca direta OU qualquer alternativa de sinonimo casa no mesmo nome. */
    private fun casa(nome: String, consulta: String, alternativas: List<String>): Boolean {
        if (TextoUtil.correspondeBusca(nome, consulta)) return true
        return alternativas.any { TextoUtil.correspondeBusca(nome, it) }
    }
}

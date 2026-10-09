package br.com.comprix.domain.catalogo

import br.com.comprix.util.TextoUtil

/**
 * Setor do catalogo-semente: uma area do mercado e a categoria Comprix a que
 * ela corresponde (uma das 14 da Secao 4.4).
 */
data class SetorDoCatalogo(val id: Int, val nome: String, val categoriaComprix: String)

/** Subcategoria do catalogo-semente, sempre presa a um setor. */
data class SubcategoriaDoCatalogo(val id: Int, val nome: String, val setorId: Int)

/**
 * Item do catalogo-semente, sempre preso a uma subcategoria.
 *
 * CONTRATO para autocomplete/busca (usado pela interface):
 * - [nome] e o nome limpo e exibivel do produto (nunca carrega anotacoes);
 * - [sinonimos] sao apelidos de busca NORMALIZADOS (minusculas, sem acento,
 *   pela [TextoUtil.normalizar]) - vazia quando o item nao tem nenhum.
 */
data class ItemDoCatalogo(
    val nome: String,
    val subcategoriaId: Int,
    val sinonimos: List<String> = emptyList(),
)

/**
 * Catalogo-semente: o acervo de produtos que vem de fabrica com o app.
 *
 * O texto segue o formato linha-a-linha do `catalogo_semente.txt`, o mesmo
 * estilo de linhas do backup `.cbk`:
 *
 * - comentarios comecam com `#` (a segunda linha util traz `# CONTAGEM` com
 *   setores, subcategorias, itens e apelidos esperados);
 * - `SETOR|<id>|<nome>|<categoriaComprix>`;
 * - `SUB|<id>|<nome>|<setorId>`;
 * - `ITEM|<nome>|<subId>[|<sinonimos separados por virgula>]` - a 4a coluna,
 *   opcional, traz apelidos de busca do produto ("Tangerina" com
 *   "mexerica, bergamota"); o parser os normaliza e expoe em
 *   [ItemDoCatalogo.sinonimos];
 * - `ALIAS|<aliasNormalizado>|<nomeOficialDoItem>`.
 *
 * ## Retrocompatibilidade com a semente 1.2.0
 * Na 1.2.0 a anotacao de sinonimos foi COLADA no campo nome de 37 linhas
 * ("Tangerina — também buscar: mexerica, bergamota"), e o nome poluido vazou
 * para a interface. O parser segue separando esse sufixo do nome
 * ([separarAnotacaoDeSinonimos]) e movendo o conteudo para [ItemDoCatalogo.sinonimos],
 * entao tanto o formato novo (coluna 4) quanto linhas antigas coladas parseiam
 * para o mesmo lugar.
 *
 * A classe e Kotlin puro - nada de Android, Context ou Room - entao cabe em
 * teste de JVM sem instrumentacao. Se a contagem do cabecalho divergir do que
 * foi lido, o que parseou permanece: catalogo parcial e melhor que app que
 * nao abre, e a divergencia fica visivel em [contagemConfere].
 *
 * A busca usa as mesmas regras do resto do app: normalizacao completa do
 * [TextoUtil], igualdade exata (incluindo apelidos e sinonimos) vence prefixo,
 * que vence "contem" - e o sinonimo exato ranqueia abaixo do nome direto, mas
 * acima do "contem" fuzzy. Tudo sem acento e sem diferenca de caixa.
 */
class CatalogoSemente(texto: String) {

    val setores: List<SetorDoCatalogo>
    val subcategorias: List<SubcategoriaDoCatalogo>
    val itens: List<ItemDoCatalogo>

    /** Itens agrupados pela subcategoria a que pertencem. */
    val itensPorSubcategoria: Map<Int, List<ItemDoCatalogo>>

    /** Subcategorias agrupadas pelo setor a que pertencem. */
    val subcategoriasPorSetor: Map<Int, List<SubcategoriaDoCatalogo>>

    /**
     * Falso quando o cabecalho `# CONTAGEM` divergiu das listas reais. Nao
     * bloqueia nada: e um sinal para os testes e para quem atualizar o arquivo.
     */
    val contagemConfere: Boolean
        get() {
            val esperado = contagensDoCabecalho ?: return true
            return esperado[0] == setores.size &&
                esperado[1] == subcategorias.size &&
                esperado[2] == itens.size &&
                esperado[3] == totalDeAliases
        }

    private val contagensDoCabecalho: IntArray?
    private val totalDeAliases: Int

    private val setorPorId: Map<Int, SetorDoCatalogo>
    private val subcategoriaPorId: Map<Int, SubcategoriaDoCatalogo>

    private val itensPorNome: Map<String, ItemDoCatalogo>
    private val itensPorAlias: Map<String, ItemDoCatalogo>
    private val itensPorSinonimo: Map<String, ItemDoCatalogo>

    init {
        val setoresLidos = mutableListOf<SetorDoCatalogo>()
        val subcategoriasLidas = mutableListOf<SubcategoriaDoCatalogo>()
        val itensLidos = mutableListOf<ItemDoCatalogo>()
        val aliasesLidos = mutableListOf<Pair<String, String>>()
        var contagens: IntArray? = null

        texto.lineSequence().forEach { linhaBruta ->
            val linha = linhaBruta.trim()
            if (linha.isEmpty()) return@forEach
            if (linha.startsWith("#")) {
                if (contagens == null && linha.startsWith(MARCADOR_CONTAGEM)) {
                    val numeros = linha.removePrefix(MARCADOR_CONTAGEM).trim()
                        .split(" ")
                        .mapNotNull { parte -> parte.toIntOrNull() }
                    if (numeros.size >= 4) contagens = numeros.take(4).toIntArray()
                }
                return@forEach
            }

            val partes = linha.split("|")
            when (partes.firstOrNull()?.uppercase()) {
                "SETOR" -> if (partes.size >= 4) {
                    val id = partes[1].toIntOrNull()
                    val nome = partes[2].trim()
                    if (id != null && nome.isNotBlank()) {
                        setoresLidos += SetorDoCatalogo(id, nome, partes[3].trim())
                    }
                }

                "SUB" -> if (partes.size >= 4) {
                    val id = partes[1].toIntOrNull()
                    val nome = partes[2].trim()
                    val setorId = partes[3].toIntOrNull()
                    if (id != null && setorId != null && nome.isNotBlank()) {
                        subcategoriasLidas += SubcategoriaDoCatalogo(id, nome, setorId)
                    }
                }

                "ITEM" -> if (partes.size >= 3) {
                    val subId = partes[2].toIntOrNull()
                    // Sinonimos podem vir de dois jeitos: na 4a coluna (formato
                    // atual) ou colados no nome (semente 1.2.0). Os dois caem no
                    // mesmo campo, ja normalizados.
                    val (nomeLimpo, sinonimosColados) = separarAnotacaoDeSinonimos(partes[1])
                    val sinonimosDaColuna = if (partes.size >= 4) sinonimosNormalizados(partes[3]) else emptyList()
                    if (nomeLimpo.isNotBlank() && subId != null) {
                        itensLidos += ItemDoCatalogo(nomeLimpo, subId, (sinonimosColados + sinonimosDaColuna).distinct())
                    }
                }

                "ALIAS" -> if (partes.size >= 3) {
                    val alias = partes[1].trim().lowercase()
                    val oficial = partes[2].trim()
                    if (alias.isNotBlank() && oficial.isNotBlank()) {
                        aliasesLidos += alias to oficial
                    }
                }
            }
        }

        contagensDoCabecalho = contagens
        setores = setoresLidos.toList()
        subcategorias = subcategoriasLidas.toList()
        itens = itensLidos.toList()
        totalDeAliases = aliasesLidos.size

        itensPorSubcategoria = itens.groupBy { it.subcategoriaId }
        subcategoriasPorSetor = subcategorias.groupBy { it.setorId }
        setorPorId = setores.associateBy { it.id }
        subcategoriaPorId = subcategorias.associateBy { it.id }

        val indiceDeNomes = linkedMapOf<String, ItemDoCatalogo>()
        itens.forEach { item -> indiceDeNomes[TextoUtil.normalizar(item.nome)] = item }
        itensPorNome = indiceDeNomes

        val indiceDeAliases = linkedMapOf<String, ItemDoCatalogo>()
        aliasesLidos.forEach { (alias, nomeOficial) ->
            val chave = TextoUtil.normalizar(alias)
            val alvo = indiceDeNomes[TextoUtil.normalizar(nomeOficial)]
            if (chave.isNotBlank() && alvo != null) indiceDeAliases[chave] = alvo
        }
        itensPorAlias = indiceDeAliases

        // Indice de sinonimos: apelido normalizado -> item. Um sinonimo que
        // colide com o nome de OUTRO item e descartado - nome direto sempre
        // vence sinonimo, e a resolucao por sinonimo nunca rouba o produto
        // que ja existe com aquele nome.
        val indiceDeSinonimos = linkedMapOf<String, ItemDoCatalogo>()
        itens.forEach { item ->
            item.sinonimos.forEach { sinonimo ->
                if (sinonimo.isNotBlank() && sinonimo !in indiceDeNomes && sinonimo !in indiceDeSinonimos) {
                    indiceDeSinonimos[sinonimo] = item
                }
            }
        }
        itensPorSinonimo = indiceDeSinonimos
    }

    /**
     * Busca por prioridade:
     * 1) igualdade exata pelo nome;
     * 2) igualdade exata por apelido (`ALIAS`);
     * 3) nomes que comecam com o termo;
     * 4) termo e sinonimo exato de um item ("mexerica" -> "Tangerina");
     * 5) nomes que contem o termo;
     * 6) sinonimos que comecam com o termo;
     * 7) termo que contem um sinonimo ("1 kg de mexerica").
     *
     * O sinonimo ranqueia abaixo do nome direto (exato e prefixo) e acima do
     * "contem" fuzzy. O resultado nao tem duplicatas e respeita o limite.
     */
    fun buscar(termo: String, limite: Int = 30): List<ItemDoCatalogo> {
        val chave = TextoUtil.normalizar(termo)
        if (chave.isBlank() || limite <= 0) return emptyList()

        val achados = LinkedHashSet<ItemDoCatalogo>()
        itensPorNome[chave]?.let { achados.add(it) }
        if (achados.size < limite) itensPorAlias[chave]?.let { achados.add(it) }

        if (achados.size < limite) {
            for ((nome, item) in itensPorNome) {
                if (nome.startsWith(chave)) {
                    achados.add(item)
                    if (achados.size >= limite) break
                }
            }
        }

        if (achados.size < limite) itensPorSinonimo[chave]?.let { achados.add(it) }

        if (achados.size < limite) {
            for ((nome, item) in itensPorNome) {
                if (nome.contains(chave)) {
                    achados.add(item)
                    if (achados.size >= limite) break
                }
            }
        }

        if (achados.size < limite) {
            for ((sinonimo, item) in itensPorSinonimo) {
                if (sinonimo.startsWith(chave)) {
                    achados.add(item)
                    if (achados.size >= limite) break
                }
            }
        }

        if (achados.size < limite) {
            for ((sinonimo, item) in itensPorSinonimo) {
                if (chave.contains(sinonimo)) {
                    achados.add(item)
                    if (achados.size >= limite) break
                }
            }
        }

        return achados.toList().take(limite)
    }

    /**
     * Resolve um item pelo NOME exato (normalizado). Sem prefixo, sem "contem":
     * "Tangerina" acha o item, "tangeri" nao acha nada - procuras fuzzy ficam
     * em [buscar]. `null` quando o acervo nao tem produto com aquele nome.
     */
    fun itemPorNome(termo: String): ItemDoCatalogo? {
        val chave = TextoUtil.normalizar(termo)
        if (chave.isBlank()) return null
        return itensPorNome[chave]
    }

    /**
     * Resolve um termo que e SINONIMO de um item do acervo ("mexerica" ->
     * "Tangerina"): exato vence prefixo (este ultimo so a partir de 3 letras,
     * para nao canonizar produtos por 1 ou 2 caracteres). Devolve `null` quando
     * o termo nao e sinonimo de ninguem - nomes diretos nunca passam por aqui.
     */
    fun itemPorSinonimo(termo: String): ItemDoCatalogo? {
        val chave = TextoUtil.normalizar(termo)
        if (chave.isBlank()) return null
        itensPorSinonimo[chave]?.let { return it }
        if (chave.length < MINIMO_PARA_PREFIXO_DE_SINONIMO) return null
        return itensPorSinonimo.entries.firstOrNull { it.key.startsWith(chave) }?.value
    }

    /**
     * Categoria Comprix do setor da subcategoria do item - o elo item ->
     * subcategoria -> setor -> categoria. `null` se qualquer elo faltar.
     */
    fun categoriaDe(item: ItemDoCatalogo): String? =
        subcategoriaPorId[item.subcategoriaId]?.let { subcategoria ->
            setorPorId[subcategoria.setorId]?.categoriaComprix
        }

    /** Quantos itens o catalogo carrega. */
    fun contarItens(): Int = itens.size

    companion object {
        private const val MARCADOR_CONTAGEM = "# CONTAGEM"

        /** Um sinonimo so casa por prefixo a partir deste tamanho. */
        private const val MINIMO_PARA_PREFIXO_DE_SINONIMO = 3

        /** Inicio da anotacao de sinonimos, ja normalizado ("tambem buscar"). */
        private const val PREFIXO_DA_ANOTACAO = "tambem buscar"

        /** Tracos que podem preceder a anotacao: hifen, en e em dash. */
        private val TRACOS = charArrayOf('-', '\u2013', '\u2014')

        /**
         * " mexerica, Bergamota " -> ["mexerica", "bergamota"]: separa por
         * virgula, normaliza cada pedaco e ignora vazios e repetidos.
         */
        private fun sinonimosNormalizados(texto: String): List<String> =
            texto.split(',')
                .map { TextoUtil.normalizar(it) }
                .filter { it.isNotBlank() }
                .distinct()

        /**
         * Separa do nome a anotacao de sinonimos COLADA nele (semente 1.2.0):
         * "Tangerina — também buscar: mexerica, bergamota" vira
         * `("Tangerina", ["mexerica", "bergamota"])`.
         *
         * A deteccao e por traco (hifen, en dash ou em dash) seguido de texto
         * que normalizado comeca com "tambem buscar" e tem dois-pontos - assim
         * aceita com/sem acento, qualquer caixa e espacos variaveis, e nomes
         * hifenizados legitimos ("Lava-roupas") passam ilesos. Sem anotacao,
         * devolve o nome intacto com lista vazia.
         *
         * Publico de proposito: o reparo de dados do [br.com.comprix.data.repositorio.CatalogoRepositorio]
         * usa a mesma funcao para limpar produtos que o usuario ja gravou com o
         * nome poluido.
         */
        fun separarAnotacaoDeSinonimos(nome: String): Pair<String, List<String>> {
            val texto = nome.trim()
            var busca = 0
            while (true) {
                val traco = texto.indexOfAny(TRACOS, busca)
                if (traco < 0) return texto to emptyList()
                val cauda = texto.substring(traco + 1)
                val doisPontos = cauda.indexOf(':')
                if (
                    doisPontos >= 0 &&
                    TextoUtil.normalizar(cauda).startsWith(PREFIXO_DA_ANOTACAO)
                ) {
                    val nomeLimpo = texto.substring(0, traco).trim()
                    val conteudo = cauda.substring(doisPontos + 1)
                    return nomeLimpo to sinonimosNormalizados(conteudo)
                }
                busca = traco + 1
            }
        }
    }
}

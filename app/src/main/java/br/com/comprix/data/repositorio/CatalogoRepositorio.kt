package br.com.comprix.data.repositorio

import br.com.comprix.data.local.CategoriaDao
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.DecisaoDoParserDao
import br.com.comprix.data.local.DecisaoDoParserEntity
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.local.MemoriaCategoriaDao
import br.com.comprix.data.local.MemoriaCategoriaEntity
import br.com.comprix.data.local.ProdutoDao
import br.com.comprix.domain.categoria.CategorizadorAutomatico
import br.com.comprix.domain.catalogo.CatalogoSemente
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.parser.MemoriaDoParser
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Catalogo local: categorias, produtos e a memoria da categorizacao.
 *
 * Tambem e aqui que o app "aprende": sempre que o usuario corrige a categoria
 * de um produto, [lembrarCategoria] grava a associacao para que o proximo
 * produto parecido ja nasca certo.
 */
class CatalogoRepositorio(
    private val categoriaDao: CategoriaDao,
    private val produtoDao: ProdutoDao,
    private val memoriaDao: MemoriaCategoriaDao,
    private val decisaoDoParserDao: DecisaoDoParserDao,
    private val catalogoSemente: CatalogoSemente? = null,
    private val lixeira: LixeiraRepositorio? = null,
) {

    val categorias: Flow<List<Categoria>> =
        categoriaDao.observarTodas().map { lista -> lista.map(Mapeadores::paraDominio) }

    val produtos: Flow<List<Produto>> =
        produtoDao.observarTodos().map { lista -> lista.map(Mapeadores::paraDominio) }

    val produtosComTabelaNutricional: Flow<List<Produto>> =
        produtoDao.observarComTabelaNutricional().map { lista -> lista.map(Mapeadores::paraDominio) }

    /**
     * Escopo proprio do reparo de dados (abaixo): gravacao curta e idempotente
     * que nao pode depender de nenhuma tela. O repositorio e singleton do
     * processo, entao o escopo vive como ele vive - mesma ideia do
     * ServiceLocator.EscopoDeAplicacao, sem circular dependencia com o `di`.
     */
    private val escopoDoReparo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        escopoDoReparo.launch { runCatching { repararNomesPoluidos() } }
    }

    /**
     * Reparo de dados da semente 1.2.0 (executa uma vez por processo).
     *
     * Na 1.2.0, 36 itens do catalogo-semente tinham a anotacao de sinonimos
     * COLADA no nome ("Tangerina — também buscar: mexerica, bergamota") e quem
     * adicionou esses itens gravou Produtos no Room com o nome poluido. Aqui o
     * produto ganha o nome limpo - RENOMEANDO a linha, nunca duplicando nem
     * apagando: o id permanece, entao itens da lista e historico de precos que
     * apontam para ele continuam validos.
     *
     * Se ja existir outro produto com o nome limpo, a renomeacao cria nomes
     * normalizados iguais; isso e tolerado a de proposito - re-pointar itens e
     * historico exigiria DAOs de outras tabelas, e a deduplicacao por nome
     * normalizado (sugestoes e porNomeNormalizado LIMIT 1) mantem o comportamento
     * estavel. Resetar o banco aqui NUNCA: `fallbackToDestructiveMigration` e
     * proibido no projeto e este reparo e apenas UPDATE de dados, sem mudanca
     * de esquema (nao precisa de Migration nem de versao nova).
     */
    private suspend fun repararNomesPoluidos() {
        val poluidos = produtoDao.listarTodos()
            .filter { it.nomeNormalizado.contains(MARCADOR_DE_SINONIMOS_COLADOS) }
        for (poluido in poluidos) {
            val (nomeLimpo, _) = CatalogoSemente.separarAnotacaoDeSinonimos(poluido.nome)
            if (nomeLimpo.isBlank()) continue
            val nomeNormalizadoLimpo = TextoUtil.normalizar(nomeLimpo)
            if (nomeNormalizadoLimpo.isBlank()) continue
            produtoDao.salvar(
                poluido.copy(
                    nome = nomeLimpo,
                    nomeNormalizado = nomeNormalizadoLimpo,
                    atualizadoEm = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun listarCategorias(): List<Categoria> = categoriaDao.listarTodas().map(Mapeadores::paraDominio)

    suspend fun categoriasPorId(): Map<Long, Categoria> = listarCategorias().associateBy { it.id }

    suspend fun criarCategoria(nome: String, icone: String = "outros"): Long {
        val existentes = listarCategorias()
        val ordem = (existentes.maxOfOrNull { it.ordemPadrao } ?: 100) + 5
        return categoriaDao.inserir(
            Mapeadores.paraEntidade(
                Categoria(
                    nome = nome.trim(),
                    icone = icone,
                    ordemPadrao = ordem.coerceAtMost(998),
                    origem = OrigemCategoria.USUARIO,
                    chave = "",
                ),
            ),
        )
    }

    suspend fun renomearCategoria(categoria: Categoria, novoNome: String) {
        categoriaDao.atualizar(Mapeadores.paraEntidade(categoria.copy(nome = novoNome.trim())))
    }

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias); padrão imutável cai fora. */
    suspend fun removerCategoriaDoUsuario(id: Long) {
        if (lixeira != null && lixeira.enviarCategoria(id)) return
        categoriaDao.removerDoUsuario(id)
    }

    // --- favoritos --------------------------------------------------------------------

    /** Favoritos de item: produtos marcados, prontos para o atalho de 1 toque. */
    val produtosFavoritos: Flow<List<Produto>> =
        produtoDao.observarFavoritos().map { lista -> lista.map(Mapeadores::paraDominio) }

    suspend fun marcarProdutoFavorito(id: Long, favorito: Boolean) {
        val produto = produtoDao.porId(id) ?: return
        produtoDao.salvar(produto.copy(favorito = favorito))
    }

    /**
     * Grava a ordem em que as categorias aparecem na lista agrupada.
     *
     * A ordem recebida e a sequencia final de ids; cada posicao vira um
     * multiplo de 10 para sobrar espaco entre vizinhas e uma categoria nova
     * poder entrar no meio depois sem reescrever a tabela inteira.
     */
    /** Quantos produtos conhecidos existem em cada categoria. */
    suspend fun contagemPorCategoria(): Map<Long, Int> =
        produtoDao.contarPorCategoria().associate { it.categoriaId to it.total }

    /**
     * Devolve a ordem do mercado ao padrao de fabrica das 14 categorias.
     *
     * As criadas pela pessoa vao para o fim, preservadas: apagar uma categoria
     * so porque ela nao estava no conjunto original seria perda de dado.
     */
    suspend fun restaurarOrdemPadrao() {
        val padrao = DadosIniciais.categorias.withIndex().associate { (i, c) -> c.chave to (i + 1) * 10 }
        listarCategorias().forEach { categoria ->
            val ordem = padrao[categoria.chave] ?: (900 + categoria.id.toInt().coerceAtMost(98))
            categoriaDao.definirOrdem(categoria.id, ordem)
        }
    }

    suspend fun reordenarCategorias(idsNaOrdem: List<Long>) {
        idsNaOrdem.forEachIndexed { posicao, id -> categoriaDao.definirOrdem(id, (posicao + 1) * 10) }
    }

    /**
     * Peso medio de uma unidade, para hortifruti vendido por cabeca/pe.
     *
     * Fica marcado como estimativa em toda a interface: serve para comparar
     * quem vende por unidade com quem vende por quilo, nunca para substituir a
     * balanca. `null` apaga a estimativa.
     */
    suspend fun definirPesoMedioEstimado(produtoId: Long, pesoEmBase: java.math.BigDecimal?) {
        val produto = produtoPorId(produtoId) ?: return
        salvarProduto(produto.copy(pesoMedioEstimadoEmBase = pesoEmBase))
    }

    // --- produtos --------------------------------------------------------------------

    suspend fun produtoPorId(id: Long): Produto? = produtoDao.porId(id)?.let(Mapeadores::paraDominio)

    /**
     * Produtos com validade preenchida e vencendo dentro de [dias] dias (ou ja
     * vencidos). O limite e convertido para epoch day pelo mesmo conversor que
     * grava a coluna ([Mapeadores.dataParaEpoch]), entao a comparacao no SQL
     * e sempre consistente com o que a ficha do produto salvou.
     */
    suspend fun validadesProximas(dias: Int = 30): List<Produto> {
        val limite = Mapeadores.dataParaEpoch(LocalDate.now().plusDays(dias.toLong())) ?: return emptyList()
        return produtoDao.porValidadeAte(limite).map(Mapeadores::paraDominio)
    }

    suspend fun produtosPorIds(ids: List<Long>): List<Produto> =
        if (ids.isEmpty()) emptyList() else produtoDao.porIds(ids).map(Mapeadores::paraDominio)

    suspend fun produtoPorCodigoBarras(codigo: String): Produto? =
        produtoDao.porCodigoBarras(codigo)?.let(Mapeadores::paraDominio)

    suspend fun buscarProdutos(termo: String, limite: Int = 20): List<Produto> {
        val normalizado = TextoUtil.normalizar(termo)
        if (normalizado.isBlank()) return emptyList()
        val achados = produtoDao.buscar(normalizado, limite).map(Mapeadores::paraDominio).toMutableList()

        // Busca por sinonimo: "mexerica" nao esta no nome de nenhum produto,
        // mas e apelido de fabrica da Tangerina - que entao entra no resultado
        // se ja existir no catalogo local. Dedupe por id preserva a ordem da
        // busca original (o Room primeiro).
        if (achados.size < limite) {
            val item = catalogoSemente?.itemPorSinonimo(termo)
            if (item != null) {
                val chave = TextoUtil.normalizar(item.nome)
                produtoDao.porNomeNormalizado(chave)?.let { entidade ->
                    if (achados.none { it.id == entidade.id }) {
                        achados.add(Mapeadores.paraDominio(entidade))
                    }
                }
            }
        }
        return achados.take(limite)
    }

    /** Salva (ou atualiza) e devolve o produto com o id definitivo. */
    suspend fun salvarProduto(produto: Produto): Produto {
        val normalizado = TextoUtil.normalizar(produto.nome)
        val paraSalvar = produto.copy(nomeNormalizado = normalizado)
        val id = produtoDao.salvar(Mapeadores.paraEntidade(paraSalvar))
        return paraSalvar.copy(id = if (id > 0) id else produto.id)
    }

    /**
     * Encontra um produto equivalente ou cria um novo.
     *
     * Ordem de busca: codigo de barras (identidade forte) -> nome normalizado
     * exato -> nome canonico do acervo quando o que foi digitado e apelido de
     * fabrica ("mexerica" nasce como "Tangerina", nao como produto novo).
     * Nao fazemos busca aproximada aqui de proposito: "leite integral" e
     * "leite desnatado" sao 93 % parecidos e sao produtos diferentes.
     */
    suspend fun encontrarOuCriar(
        nome: String,
        categoriaId: Long,
        codigoBarras: String? = null,
    ): Produto {
        if (!codigoBarras.isNullOrBlank()) {
            produtoDao.porCodigoBarras(codigoBarras)?.let { return Mapeadores.paraDominio(it) }
        }
        val normalizado = TextoUtil.normalizar(nome)
        produtoDao.porNomeNormalizado(normalizado)?.let { return Mapeadores.paraDominio(it) }

        // O termo digitado e sinonimo de um item do acervo ("mexerica" ->
        // "Tangerina"): o produto nasce com o NOME CANONICO do acervo e
        // reaproveita um produto canonico que ja exista, para nunca duplicar
        // o mesmo item do mercado com dois nomes.
        val itemDoAcervo = catalogoSemente?.itemPorSinonimo(nome)
        val nomeFinal = itemDoAcervo?.nome ?: nome
        val chaveFinal = itemDoAcervo?.let { TextoUtil.normalizar(it.nome) } ?: normalizado
        if (itemDoAcervo != null) {
            produtoDao.porNomeNormalizado(chaveFinal)?.let { return Mapeadores.paraDominio(it) }
        }

        return salvarProduto(
            Produto(
                nome = TextoUtil.capitalizarTitulo(nomeFinal),
                nomeNormalizado = chaveFinal,
                categoriaId = categoriaId,
                codigoBarras = codigoBarras,
            ),
        )
    }

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias). */
    suspend fun removerProduto(id: Long) {
        if (lixeira != null) {
            lixeira.enviarProduto(id)
            return
        }
        produtoDao.remover(id)
    }

    suspend fun contarProdutos(): Int = produtoDao.contar()

    // --- categorizacao hibrida -------------------------------------------------------

    /** Memoria de correcoes: nome normalizado -> id da categoria. */
    suspend fun memoriaDeCategorias(): Map<String, Long> =
        memoriaDao.listarTodas().associate { it.nomeNormalizado to it.categoriaId }

    /** Registra a correcao do usuario (ou reforca uma ja existente). */
    suspend fun lembrarCategoria(nome: String, categoriaId: Long) {
        val chave = TextoUtil.normalizar(nome)
        if (chave.isBlank()) return
        val anterior = memoriaDao.porNome(chave)
        memoriaDao.salvar(
            MemoriaCategoriaEntity(
                nomeNormalizado = chave,
                categoriaId = categoriaId,
                vezes = (anterior?.vezes ?: 0) + 1,
                atualizadoEm = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun esquecerCategorias() = memoriaDao.limpar()

    // --- aprendizado do parser de texto livre ----------------------------------------

    /**
     * Le o que o usuario ja respondeu nas sugestoes inline.
     *
     * Chamado uma vez por tela de digitacao e passado adiante para o
     * [br.com.comprix.domain.parser.SugestoesDoParser]: e isso que faz o app
     * **parar de perguntar** o que ja foi respondido (G6 do documento).
     */
    suspend fun memoriaDoParser(): MemoriaDoParser {
        val decisoes = decisaoDoParserDao.listarTodas()
        return MemoriaDoParser(
            rejeitados = decisoes.filter { it.decisao == DECISAO_REJEITADO }
                .map { it.termo }
                .toSet(),
            confirmados = decisoes.filter { it.decisao == DECISAO_CONFIRMADO && it.valor != null }
                .associate { it.termo to BigDecimal(it.valor) },
        )
    }

    /** Grava a resposta do usuario a uma sugestao do parser. */
    suspend fun lembrarDecisaoDoParser(termo: String, valorConfirmado: BigDecimal?) {
        val chave = termo.lowercase()
        val anterior = decisaoDoParserDao.porTermo(chave)
        decisaoDoParserDao.salvar(
            DecisaoDoParserEntity(
                termo = chave,
                decisao = if (valorConfirmado != null) DECISAO_CONFIRMADO else DECISAO_REJEITADO,
                valor = valorConfirmado?.toPlainString(),
                vezes = (anterior?.vezes ?: 0) + 1,
                atualizadoEm = System.currentTimeMillis(),
            ),
        )
    }

    /** Apaga uma decisao - o app volta a perguntar sobre aquele termo. */
    suspend fun esquecerDecisaoDoParser(termo: String) = decisaoDoParserDao.remover(termo.lowercase())

    /** Zera o aprendizado do parser (botao em Configuracoes > Dados). */
    suspend fun esquecerDecisoesDoParser() = decisaoDoParserDao.limpar()

    /** Lista legivel para a tela de Configuracoes: termo, decisao e vezes. */
    suspend fun decisoesDoParser(): List<DecisaoDoParserEntity> =
        decisaoDoParserDao.listarTodas().sortedByDescending { it.atualizadoEm }

    /** Monta o categorizador com o catalogo atual (dicionario + memoria) e o acervo de fabrica. */
    suspend fun categorizador(): CategorizadorAutomatico {
        val categorias = listarCategorias()
        val padrao = categorias.firstOrNull { it.chave == "outros" }
            ?: categorias.lastOrNull()
            ?: Categoria(nome = "Outros", icone = "outros", ordemPadrao = 999, chave = "outros")
        return CategorizadorAutomatico(
            categoriasPorChave = categorias.filter { it.chave.isNotBlank() }.associateBy { it.chave },
            categoriaPadrao = padrao,
            catalogoSemente = catalogoSemente,
        )
    }

    /** Sugestao pronta para a interface: aplica dicionario + memoria de uma vez. */
    suspend fun sugerirCategoria(nome: String): CategorizadorAutomatico.Sugestao {
        val categorizador = categorizador()
        return categorizador.sugerir(
            nome = nome,
            memoria = memoriaDeCategorias(),
            categoriasPorId = categoriasPorId(),
        )
    }

    /**
     * Sugestoes de digitacao: primeiro o que o usuario ja tem no Room (nomes
     * reais das suas compras), depois o acervo de fabrica. Ambos os lados sao
     * sensveis a sinonimos ("mexerica" sugere "Tangerina"). Deduplicadas pelo
     * nome normalizado - "Banana prata" e "Banana-prata" viram uma so linha.
     */
    suspend fun sugestoesDeDigitacao(termo: String, limite: Int = 6): List<String> {
        val limpo = termo.trim()
        if (limpo.isBlank()) return emptyList()
        val daRoom = buscarProdutos(limpo, limite).map { it.nome }
        val doSemente = catalogoSemente?.buscar(limpo, limite)?.map { it.nome }.orEmpty()
        if (doSemente.isEmpty()) return daRoom.take(limite)

        val vistas = linkedSetOf<String>()
        return (daRoom + doSemente)
            .filter { nome ->
                val chave = TextoUtil.normalizar(nome)
                chave.isNotBlank() && vistas.add(chave)
            }
            .take(limite)
    }

    /** O acervo de fabrica, para a tela de exploracao do catalogo. `null` se o app nao o embutiu. */
    fun dadosDoCatalogoSemente(): CatalogoSemente? = catalogoSemente

    /**
     * Sinonimos de busca de um produto, direto do acervo de fabrica.
     *
     * E o que devolve a anotacao "tambem buscar: mexerica, bergamota" de volta
     * para a tela: a v1.3.0 limpou o sufixo do nome dos produtos gravados
     * ([repararNomesPoluidos]), e os apelidos, que continuam funcionando na
     * BUSCA, sumiram da EXIBICAO. ProdutoEntity nao tem coluna de sinonimos (e
     * migration nova e proibida no projeto), entao a fonte de verdade segue
     * sendo a semente - e esta funcao e PURA EM MEMORIA, barata o bastante
     * para ser chamada por item na renderizacao da lista.
     *
     * Resolucao: nome exato do acervo primeiro ([CatalogoSemente.itemPorNome]);
     * se o produto foi gravado pelo APELIDO ("Mexerica"), cai para
     * [CatalogoSemente.itemPorSinonimo] e devolve os sinonimos do item oficial.
     * O proprio nome sai da lista (nunca aparece como sinonimo de si mesmo),
     * com dedupe; minusculas como estao na semente. Vazia quando nao ha acervo
     * ou o nome nao resolve para nenhum item.
     */
    fun sinonimosDe(nomeDoProduto: String): List<String> {
        val semente = catalogoSemente ?: return emptyList()
        val item = semente.itemPorNome(nomeDoProduto)
            ?: semente.itemPorSinonimo(nomeDoProduto)
            ?: return emptyList()
        val nomeNormalizado = TextoUtil.normalizar(nomeDoProduto)
        return item.sinonimos
            .filter { it.isNotBlank() && it != nomeNormalizado }
            .distinct()
    }

    private companion object {
        const val DECISAO_REJEITADO = "REJEITADO"
        const val DECISAO_CONFIRMADO = "CONFIRMADO"

        /** Trecho do nome normalizado que denuncia a anotacao colada (1.2.0). */
        const val MARCADOR_DE_SINONIMOS_COLADOS = "tambem buscar"
    }
}

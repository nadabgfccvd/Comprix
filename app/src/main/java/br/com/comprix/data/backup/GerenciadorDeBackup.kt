package br.com.comprix.data.backup

import android.content.Context
import br.com.comprix.data.local.CategoriaEntity
import br.com.comprix.data.local.CompraEntity
import br.com.comprix.data.local.DecisaoDoParserEntity
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.MemoriaCategoriaEntity
import br.com.comprix.data.local.PrecoEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.util.Formatadores
import br.com.comprix.util.JsonSimples
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Exportacao e restauracao do banco em arquivo `.cbk` (Comprix Backup).
 *
 * ## Formato (texto puro, uma entidade por linha)
 * ```
 * #COMPRIX-BACKUP v1
 * #TABELA categorias
 * {"id":"1","nome":"Hortifrúti",...}
 * #TABELA produtos
 * {"id":"7","nome":"Arroz",...}
 * ```
 *
 * Por que texto e nao uma copia do arquivo .db: o `.cbk` e legivel, resiste a
 * mudanca de versao do SQLite, pode ser aberto num editor e so contem dados do
 * usuario. Um `.db` copiado dependeria do esquema exato e falharia em silencio
 * numa versao futura do app.
 *
 * ## Backup automatico
 * A cada 24 h de uso o app grava um `.cbk` na pasta privada de arquivos
 * externos e mantem os 5 mais recentes - dentro do limite de "sem servidor,
 * sem nuvem, sem login".
 */
class GerenciadorDeBackup(
    private val contexto: Context,
    private val banco: ComprixDatabase,
) {

    companion object {
        const val CABECALHO = "#COMPRIX-BACKUP v1"
        private const val MARCA_TABELA = "#TABELA "
        const val EXTENSAO = "cbk"
        private const val MAXIMO_AUTOMATICOS = 5
        private const val INTERVALO_AUTOMATICO_MS = 24L * 60 * 60 * 1000
    }

    data class ResultadoRestauracao(
        val sucesso: Boolean,
        val mensagem: String,
        val tabelas: Map<String, Int> = emptyMap(),
    ) {
        val totalDeRegistros: Int get() = tabelas.values.sum()
    }

    /** Pasta privada do app (nao precisa de permissao nem de FileProvider). */
    fun pastaDeBackups(): File =
        File(contexto.getExternalFilesDir(null) ?: contexto.filesDir, "backups").apply { mkdirs() }

    fun nomeSugerido(): String = "comprix-${Formatadores.carimboDeArquivo()}.$EXTENSAO"

    // --- exportacao ------------------------------------------------------------------

    /** Escreve o backup completo no fluxo recebido (arquivo local ou SAF). */
    suspend fun exportar(saida: OutputStream) {
        saida.bufferedWriter(Charsets.UTF_8).use { escritor ->
            escritor.appendLine(CABECALHO)

            escritor.appendLine(MARCA_TABELA + "categorias")
            banco.categoriaDao().listarTodas().forEach { escritor.appendLine(JsonSimples.paraJson(deCategoria(it))) }

            escritor.appendLine(MARCA_TABELA + "produtos")
            banco.produtoDao().listarTodos().forEach { escritor.appendLine(JsonSimples.paraJson(deProduto(it))) }

            escritor.appendLine(MARCA_TABELA + "estabelecimentos")
            banco.estabelecimentoDao().listarTodos().forEach {
                escritor.appendLine(JsonSimples.paraJson(deEstabelecimento(it)))
            }

            escritor.appendLine(MARCA_TABELA + "listas")
            banco.listaDao().listarTodas().forEach { escritor.appendLine(JsonSimples.paraJson(deLista(it))) }

            escritor.appendLine(MARCA_TABELA + "itens")
            banco.itemDao().listarTodos().forEach { escritor.appendLine(JsonSimples.paraJson(deItem(it))) }

            escritor.appendLine(MARCA_TABELA + "precos")
            banco.precoDao().listarTodos().forEach { escritor.appendLine(JsonSimples.paraJson(dePreco(it))) }

            escritor.appendLine(MARCA_TABELA + "compras")
            banco.compraDao().listarTodas().forEach { escritor.appendLine(JsonSimples.paraJson(deCompra(it))) }

            escritor.appendLine(MARCA_TABELA + "memoria_categorias")
            banco.memoriaCategoriaDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deMemoria(it)))
            }
            escritor.appendLine(MARCA_TABELA + "decisoes_parser")
            banco.decisaoDoParserDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deDecisaoDoParser(it)))
            }
            escritor.flush()
        }
    }

    /** Backup automatico: no maximo 1 por dia, mantendo os 5 mais recentes. */
    suspend fun backupAutomaticoSeNecessario(ultimoBackupMs: Long): File? {
        val agora = System.currentTimeMillis()
        if (agora - ultimoBackupMs < INTERVALO_AUTOMATICO_MS) return null
        val arquivo = File(pastaDeBackups(), "auto-${Formatadores.carimboDeArquivo()}.$EXTENSAO")
        arquivo.outputStream().use { exportar(it) }
        limparAntigos()
        return arquivo
    }

    fun listarBackups(): List<File> =
        pastaDeBackups().listFiles { arquivo -> arquivo.extension == EXTENSAO }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun limparAntigos() {
        listarBackups().filter { it.name.startsWith("auto-") }
            .drop(MAXIMO_AUTOMATICOS)
            .forEach { it.delete() }
    }

    // --- restauracao -----------------------------------------------------------------

    /**
     * Le um `.cbk` e substitui o conteudo atual.
     *
     * A restauracao e "tudo ou nada" no sentido pratico: primeiro o arquivo e
     * validado (cabecalho + tabelas conhecidas) e so entao o banco e limpo.
     * Arquivo invalido nao apaga nada.
     */
    suspend fun restaurar(entrada: InputStream): ResultadoRestauracao {
        val linhas = runCatching { entrada.bufferedReader(Charsets.UTF_8).readLines() }
            .getOrElse { return ResultadoRestauracao(false, "Não foi possível ler o arquivo.") }

        if (linhas.isEmpty() || !linhas.first().trim().startsWith("#COMPRIX-BACKUP")) {
            return ResultadoRestauracao(false, "Este arquivo não é um backup do Comprix.")
        }

        val porTabela = LinkedHashMap<String, MutableList<Map<String, String>>>()
        var tabelaAtual: String? = null
        linhas.drop(1).forEach { linha ->
            val texto = linha.trim()
            when {
                texto.isBlank() -> Unit
                texto.startsWith(MARCA_TABELA) -> {
                    tabelaAtual = texto.removePrefix(MARCA_TABELA).trim()
                    porTabela.getOrPut(tabelaAtual!!) { mutableListOf() }
                }
                texto.startsWith("{") -> {
                    val dados = JsonSimples.deJson(texto)
                    if (dados.isNotEmpty() && tabelaAtual != null) porTabela[tabelaAtual]?.add(dados)
                }
            }
        }
        if (porTabela.isEmpty()) return ResultadoRestauracao(false, "O backup está vazio.")

        val manutencao = banco.manutencaoDao()
        manutencao.apagarPrecos()
        manutencao.apagarItens()
        manutencao.apagarListas()
        manutencao.apagarCompras()
        manutencao.apagarProdutos()
        manutencao.apagarEstabelecimentos()
        manutencao.apagarHistoricoPrecos()
        manutencao.apagarCategoriasDoUsuario()

        val contagem = LinkedHashMap<String, Int>()

        porTabela["categorias"]?.forEach { dados ->
            banco.categoriaDao().inserir(paraCategoria(dados))
            contagem["categorias"] = (contagem["categorias"] ?: 0) + 1
        }
        porTabela["produtos"]?.forEach { dados ->
            banco.produtoDao().salvar(paraProduto(dados))
            contagem["produtos"] = (contagem["produtos"] ?: 0) + 1
        }
        porTabela["estabelecimentos"]?.forEach { dados ->
            banco.estabelecimentoDao().inserir(paraEstabelecimento(dados))
            contagem["estabelecimentos"] = (contagem["estabelecimentos"] ?: 0) + 1
        }
        porTabela["listas"]?.forEach { dados ->
            banco.listaDao().inserir(paraLista(dados))
            contagem["listas"] = (contagem["listas"] ?: 0) + 1
        }
        porTabela["itens"]?.forEach { dados ->
            banco.itemDao().inserir(paraItem(dados))
            contagem["itens"] = (contagem["itens"] ?: 0) + 1
        }
        porTabela["precos"]?.forEach { dados ->
            banco.precoDao().salvar(paraPreco(dados))
            contagem["precos"] = (contagem["precos"] ?: 0) + 1
        }
        porTabela["compras"]?.forEach { dados ->
            banco.compraDao().inserir(paraCompra(dados))
            contagem["compras"] = (contagem["compras"] ?: 0) + 1
        }
        porTabela["memoria_categorias"]?.forEach { dados ->
            banco.memoriaCategoriaDao().salvar(paraMemoria(dados))
            contagem["memória"] = (contagem["memória"] ?: 0) + 1
        }

        porTabela["decisoes_parser"]?.forEach { dados ->
            banco.decisaoDoParserDao().salvar(paraDecisaoDoParser(dados))
            contagem["parser"] = (contagem["parser"] ?: 0) + 1
        }

        return ResultadoRestauracao(
            sucesso = true,
            mensagem = "Backup restaurado: ${contagem.values.sum()} registros.",
            tabelas = contagem,
        )
    }

    // --- serializacao por tabela -----------------------------------------------------

    private fun deCategoria(e: CategoriaEntity) = mapOf(
        "id" to e.id.toString(), "nome" to e.nome, "icone" to e.icone,
        "ordem" to e.ordemPadrao.toString(), "origem" to e.origem, "chave" to e.chave,
    )

    private fun paraCategoria(d: Map<String, String>) = CategoriaEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        nome = d["nome"].orEmpty(),
        icone = d["icone"] ?: "outros",
        ordemPadrao = d["ordem"]?.toIntOrNull() ?: 999,
        origem = d["origem"] ?: "USUARIO",
        chave = d["chave"].orEmpty(),
    )

    private fun deProduto(e: ProdutoEntity) = mapOf(
        "id" to e.id.toString(), "nome" to e.nome, "norm" to e.nomeNormalizado,
        "cat" to e.categoriaId.toString(), "ean" to e.codigoBarras, "un" to e.unidadePadrao,
        "nutri" to e.infoNutricionalJson?.replace("\"", "'"), "selos" to e.selosAltoEm,
        "ingr" to e.ingredientes, "gluten" to e.gluten, "alerg" to e.alergenos,
        "val" to e.dataValidade?.toString(), "fab" to e.dataFabricacao?.toString(),
        "peso" to e.pesoMedioEstimadoEmBase, "att" to e.atualizadoEm.toString(),
        "fav" to e.favorito.toString(),
    )

    private fun paraProduto(d: Map<String, String>) = ProdutoEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        nome = d["nome"].orEmpty(),
        nomeNormalizado = d["norm"].orEmpty(),
        categoriaId = d["cat"]?.toLongOrNull() ?: 0,
        codigoBarras = d["ean"],
        unidadePadrao = d["un"] ?: "UNIDADE",
        infoNutricionalJson = d["nutri"]?.replace("'", "\""),
        selosAltoEm = d["selos"].orEmpty(),
        ingredientes = d["ingr"],
        gluten = d["gluten"] ?: "INDETERMINADO",
        alergenos = d["alerg"].orEmpty(),
        dataValidade = d["val"]?.toLongOrNull(),
        dataFabricacao = d["fab"]?.toLongOrNull(),
        pesoMedioEstimadoEmBase = d["peso"],
        atualizadoEm = d["att"]?.toLongOrNull() ?: System.currentTimeMillis(),
        favorito = d["fav"]?.toBooleanStrictOrNull() ?: false,
    )

    private fun deEstabelecimento(e: EstabelecimentoEntity) =
        mapOf("id" to e.id.toString(), "nome" to e.nome, "cor" to e.corHex)

    private fun paraEstabelecimento(d: Map<String, String>) = EstabelecimentoEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        nome = d["nome"].orEmpty(),
        corHex = d["cor"] ?: "#1E8E5A",
    )

    private fun deLista(e: ListaEntity) = mapOf(
        "id" to e.id.toString(), "nome" to e.nome, "criada" to e.criadaEm.toString(),
        "fim" to e.finalizada.toString(), "fimEm" to e.finalizadaEm?.toString(),
        "fav" to e.favorita.toString(), "orc" to e.orcamentoCentavos?.toString(),
    )

    private fun paraLista(d: Map<String, String>) = ListaEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        nome = d["nome"].orEmpty(),
        criadaEm = d["criada"]?.toLongOrNull() ?: System.currentTimeMillis(),
        finalizada = d["fim"]?.toBooleanStrictOrNull() ?: false,
        finalizadaEm = d["fimEm"]?.toLongOrNull(),
        favorita = d["fav"]?.toBooleanStrictOrNull() ?: false,
        orcamentoCentavos = d["orc"]?.toLongOrNull(),
    )

    private fun deItem(e: ItemEntity) = mapOf(
        "id" to e.id.toString(), "lista" to e.listaId.toString(), "prod" to e.produtoId.toString(),
        "qtd" to e.quantidade, "un" to e.unidade, "pv" to e.pesoOuVolume,
        "kit" to e.ehKit.toString(), "porKit" to e.itensPorKit?.toString(),
        "comp" to e.comprado.toString(), "ordem" to e.ordemManual.toString(),
        "modo" to e.modoComparacao, "obs" to e.observacao,
    )

    private fun paraItem(d: Map<String, String>) = ItemEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        listaId = d["lista"]?.toLongOrNull() ?: 0,
        produtoId = d["prod"]?.toLongOrNull() ?: 0,
        quantidade = d["qtd"] ?: "1",
        unidade = d["un"] ?: "UNIDADE",
        pesoOuVolume = d["pv"],
        ehKit = d["kit"]?.toBooleanStrictOrNull() ?: false,
        itensPorKit = d["porKit"]?.toIntOrNull(),
        comprado = d["comp"]?.toBooleanStrictOrNull() ?: false,
        ordemManual = d["ordem"]?.toIntOrNull() ?: 0,
        modoComparacao = d["modo"] ?: "POR_UNIDADE",
        observacao = d["obs"],
    )

    private fun dePreco(e: PrecoEntity) = mapOf(
        "id" to e.id.toString(), "item" to e.itemDaListaId.toString(),
        "loja" to e.estabelecimentoId.toString(), "cent" to e.precoCentavos.toString(),
        "data" to e.dataRegistro.toString(), "disp" to e.disponivel.toString(),
    )

    private fun paraPreco(d: Map<String, String>) = PrecoEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        itemDaListaId = d["item"]?.toLongOrNull() ?: 0,
        estabelecimentoId = d["loja"]?.toLongOrNull() ?: 0,
        precoCentavos = d["cent"]?.toLongOrNull() ?: 0,
        dataRegistro = d["data"]?.toLongOrNull() ?: System.currentTimeMillis(),
        disponivel = d["disp"]?.toBooleanStrictOrNull() ?: true,
    )

    private fun deCompra(e: CompraEntity) = mapOf(
        "id" to e.id.toString(), "lista" to e.listaId?.toString(), "nome" to e.nomeLista,
        "data" to e.data.toString(), "total" to e.totalPagoCentavos.toString(),
        "econ" to e.economiaCentavos.toString(), "itens" to e.quantidadeItens.toString(),
        "estab" to e.descricaoEstabelecimento, "estabId" to e.estabelecimentoPrincipalId?.toString(),
        "cats" to e.gastosPorCategoriaJson.replace("\"", "'"),
    )

    private fun paraCompra(d: Map<String, String>) = CompraEntity(
        id = d["id"]?.toLongOrNull() ?: 0,
        listaId = d["lista"]?.toLongOrNull(),
        nomeLista = d["nome"].orEmpty(),
        data = d["data"]?.toLongOrNull() ?: System.currentTimeMillis(),
        totalPagoCentavos = d["total"]?.toLongOrNull() ?: 0,
        economiaCentavos = d["econ"]?.toLongOrNull() ?: 0,
        quantidadeItens = d["itens"]?.toIntOrNull() ?: 0,
        descricaoEstabelecimento = d["estab"].orEmpty(),
        estabelecimentoPrincipalId = d["estabId"]?.toLongOrNull(),
        gastosPorCategoriaJson = d["cats"]?.replace("'", "\"") ?: "{}",
    )

    private fun deDecisaoDoParser(e: DecisaoDoParserEntity) = mapOf(
        "termo" to e.termo, "decisao" to e.decisao, "valor" to e.valor.orEmpty(),
        "vezes" to e.vezes.toString(), "atualizadoEm" to e.atualizadoEm.toString(),
    )

    private fun paraDecisaoDoParser(d: Map<String, String>) = DecisaoDoParserEntity(
        termo = d["termo"].orEmpty(),
        decisao = d["decisao"] ?: "REJEITADO",
        valor = d["valor"]?.takeIf { it.isNotBlank() },
        vezes = d["vezes"]?.toIntOrNull() ?: 1,
        atualizadoEm = d["atualizadoEm"]?.toLongOrNull() ?: System.currentTimeMillis(),
    )

    private fun deMemoria(e: MemoriaCategoriaEntity) = mapOf(
        "nome" to e.nomeNormalizado, "cat" to e.categoriaId.toString(),
        "vezes" to e.vezes.toString(), "att" to e.atualizadoEm.toString(),
    )

    private fun paraMemoria(d: Map<String, String>) = MemoriaCategoriaEntity(
        nomeNormalizado = d["nome"].orEmpty(),
        categoriaId = d["cat"]?.toLongOrNull() ?: 0,
        vezes = d["vezes"]?.toIntOrNull() ?: 1,
        atualizadoEm = d["att"]?.toLongOrNull() ?: System.currentTimeMillis(),
    )
}

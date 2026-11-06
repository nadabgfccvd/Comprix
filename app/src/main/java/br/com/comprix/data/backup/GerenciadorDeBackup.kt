package br.com.comprix.data.backup

import android.content.Context
import androidx.room.withTransaction
import br.com.comprix.data.local.CategoriaEntity
import br.com.comprix.data.local.AlergiaCustomizadaEntity
import br.com.comprix.data.local.PerfilRestricoesEntity
import br.com.comprix.data.local.ConfiguracoesEntity
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.data.local.CompraEntity
import br.com.comprix.data.local.DecisaoDoParserEntity
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.HistoricoPrecoEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.MemoriaCategoriaEntity
import br.com.comprix.data.local.PrecoEntity
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.util.Formatadores
import br.com.comprix.util.JsonSimples
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * Exportacao e restauracao do banco em arquivo `.cbk` (Comprix Backup).
 *
 * ## Formato (texto puro, uma entidade por linha)
 * ```
 * #COMPRIX-BACKUP v2
 * #TABELA categorias
 * {"id":"1","nome":"Hortifrúti",...}
 * #TABELA produtos
 * {"id":"7","nome":"Arroz",...}
 * #SHA256 <soma do conteudo anterior em UTF-8>
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
        const val CABECALHO = "#COMPRIX-BACKUP v2"
        private const val MARCA_TABELA = "#TABELA "
        const val EXTENSAO = "cbk"
        private const val MAXIMO_AUTOMATICOS = 5
        private const val INTERVALO_AUTOMATICO_MS = 24L * 60 * 60 * 1000

        /** Prefixo das copias de seguranca pre-restauracao (politica legada — veja [restaurar]). */
        internal const val PREFIXO_COPIA_SEGURANCA = "pre-restauracao-"
        private const val MAXIMO_COPIAS_SEGURANCA = 3
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
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(saida, digest).bufferedWriter(Charsets.UTF_8).use { escritor ->
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

            escritor.appendLine(MARCA_TABELA + "historico_precos")
            banco.precoDao().listarHistoricoCompleto().forEach {
                escritor.appendLine(JsonSimples.paraJson(deHistoricoPreco(it)))
            }

            escritor.appendLine(MARCA_TABELA + "compras")
            banco.compraDao().listarTodas().forEach { escritor.appendLine(JsonSimples.paraJson(deCompra(it))) }

            escritor.appendLine(MARCA_TABELA + "configuracoes")
            banco.configuracoesDao().carregar()?.let {
                escritor.appendLine(JsonSimples.paraJson(deConfiguracoes(it)))
            }
            escritor.appendLine(MARCA_TABELA + "lixeira")
            banco.lixeiraDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deLixeira(it)))
            }

            escritor.appendLine(MARCA_TABELA + "perfil_restricoes")
            banco.configuracoesDao().carregarPerfil()?.let {
                escritor.appendLine(JsonSimples.paraJson(dePerfil(it)))
            }
            escritor.appendLine(MARCA_TABELA + "alergias_customizadas")
            banco.alergiaCustomizadaDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deAlergia(it)))
            }

            escritor.appendLine(MARCA_TABELA + "memoria_categorias")
            banco.memoriaCategoriaDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deMemoria(it)))
            }
            escritor.appendLine(MARCA_TABELA + "decisoes_parser")
            banco.decisaoDoParserDao().listarTodas().forEach {
                escritor.appendLine(JsonSimples.paraJson(deDecisaoDoParser(it)))
            }
            escritor.flush()
            val soma = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            escritor.appendLine("#SHA256 $soma")
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
     * A validacao ocorre antes de qualquer escrita e a substituicao usa uma
     * transacao. O formato v2 verifica integridade por checksum. Arquivos v1
     * (legados, sem checksum) passam ainda pela conferencia do layout exato
     * das secoes — truncamentos no meio do arquivo sao rejeitados — e, como os
     * truncamentos nos ultimos registros continuam indistinguiveis, NADA e
     * apagado sem antes gravar uma copia de seguranca do estado atual (v2, com
     * checksum), que fica na pasta de backups como "desfazer" da restauracao.
     */
    suspend fun restaurar(entrada: InputStream): ResultadoRestauracao {
        val linhas = runCatching { entrada.bufferedReader(Charsets.UTF_8).readLines() }
            .getOrElse { return ResultadoRestauracao(false, "Não foi possível ler o arquivo.") }

        val porTabela = lerBackup(linhas)
            ?: return ResultadoRestauracao(false, "Backup incompleto ou inválido; nenhum dado foi alterado.")

        // Backups antigos nao carregam historico. Nao podemos combinar ids de
        // bancos diferentes sem correr o risco de associar precos aos produtos
        // errados, nem apagar o historico existente. Conferido antes da copia
        // de seguranca (restauracao cancelada nao gera copia) e de novo dentro
        // da transacao (a prova de corrida).
        if ("historico_precos" !in porTabela && banco.precoDao().contarHistorico() > 0) {
            return ResultadoRestauracao(
                false,
                "Este backup antigo não contém histórico de preços. A restauração foi cancelada para proteger os dados atuais.",
            )
        }

        // Politica de restauracao legada (v1): sem checksum, um truncamento no
        // fim do arquivo e indistinguivel de um backup valido. Uma restauracao
        // v1 so prossegue com copia de seguranca do estado atual gravada — se
        // nem a copia for possivel, a restauracao e abortada.
        val copiaDeSeguranca: File? = if (linhas.firstOrNull()?.trim() == CABECALHO_V1) {
            try {
                criarCopiaDeSeguranca()
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (erro: Exception) {
                return ResultadoRestauracao(
                    false,
                    "Não foi possível salvar a cópia de segurança antes de restaurar este backup antigo; nenhum dado foi alterado.",
                )
            }
        } else {
            null
        }

        return try {
            banco.withTransaction {
                // Backups antigos nao carregam historico. Nao podemos combinar
                // ids de bancos diferentes sem correr o risco de associar precos
                // aos produtos errados, nem apagar o historico existente.
                if ("historico_precos" !in porTabela && banco.precoDao().contarHistorico() > 0) {
                    return@withTransaction ResultadoRestauracao(
                        false,
                        "Este backup antigo não contém histórico de preços. A restauração foi cancelada para proteger os dados atuais.",
                    )
                }
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
                porTabela["historico_precos"]?.forEach { dados ->
                    banco.precoDao().registrarHistorico(paraHistoricoPreco(dados))
                    contagem["histórico de preços"] = (contagem["histórico de preços"] ?: 0) + 1
                }
                porTabela["compras"]?.forEach { dados ->
                    banco.compraDao().inserir(paraCompra(dados))
                    contagem["compras"] = (contagem["compras"] ?: 0) + 1
                }
                porTabela["configuracoes"]?.let { configuracoes ->
                    require(configuracoes.size <= 1)
                    banco.configuracoesDao().limparConfiguracoes()
                    configuracoes.singleOrNull()?.let { banco.configuracoesDao().salvar(paraConfiguracoes(it)) }
                    contagem["configurações"] = configuracoes.size
                }
                porTabela["lixeira"]?.let { registros ->
                    banco.lixeiraDao().removerTudo()
                    registros.forEach { banco.lixeiraDao().inserir(paraLixeira(it)) }
                    contagem["lixeira"] = registros.size
                }
                porTabela["perfil_restricoes"]?.let { perfis ->
                    require(perfis.size <= 1)
                    banco.configuracoesDao().limparPerfil()
                    perfis.singleOrNull()?.let { banco.configuracoesDao().salvarPerfil(paraPerfil(it)) }
                    contagem["perfil de restrições"] = perfis.size
                }
                porTabela["alergias_customizadas"]?.let { alergias ->
                    banco.alergiaCustomizadaDao().limpar()
                    alergias.forEach { banco.alergiaCustomizadaDao().inserir(paraAlergia(it)) }
                    contagem["alergias"] = alergias.size
                }

                porTabela["memoria_categorias"]?.let { registros ->
                    banco.memoriaCategoriaDao().limpar()
                    registros.forEach { banco.memoriaCategoriaDao().salvar(paraMemoria(it)) }
                    contagem["memória"] = registros.size
                }

                porTabela["decisoes_parser"]?.let { registros ->
                    banco.decisaoDoParserDao().limpar()
                    registros.forEach { banco.decisaoDoParserDao().salvar(paraDecisaoDoParser(it)) }
                    contagem["parser"] = registros.size
                }

                ResultadoRestauracao(
                    sucesso = true,
                    mensagem = if (copiaDeSeguranca != null) {
                        "Backup antigo restaurado: ${contagem.values.sum()} registros. " +
                            "O estado anterior foi salvo como ${copiaDeSeguranca.name} na pasta de backups."
                    } else {
                        "Backup restaurado: ${contagem.values.sum()} registros."
                    },
                    tabelas = contagem,
                )
            }
        } catch (cancelamento: CancellationException) {
            throw cancelamento
        } catch (erro: Exception) {
            ResultadoRestauracao(false, "Não foi possível restaurar o backup; nenhum dado foi alterado.")
        }
    }

    /**
     * Copia de seguranca do estado ATUAL, em v2 (com checksum), gravada antes
     * de uma restauracao legada (v1) apagar qualquer dado — o "desfazer" da
     * restauracao que saiu errada. Aparece na lista de backups de Configuracoes
     * como qualquer outro `.cbk`. Mantem as [MAXIMO_COPIAS_SEGURANCA] mais
     * recentes; uma copia parcial (falta de espaco, processo morto) e descartada
     * em vez de deixar arquivo invalido com cara de salvamento.
     */
    private suspend fun criarCopiaDeSeguranca(): File {
        val arquivo = File(
            pastaDeBackups(),
            "$PREFIXO_COPIA_SEGURANCA${Formatadores.carimboDeArquivo()}.$EXTENSAO",
        )
        try {
            arquivo.outputStream().use { exportar(it) }
        } catch (erro: Throwable) {
            arquivo.delete()
            throw erro
        }
        listarBackups()
            .filter { it.name.startsWith(PREFIXO_COPIA_SEGURANCA) }
            .drop(MAXIMO_COPIAS_SEGURANCA)
            .forEach { it.delete() }
        return arquivo
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
        "nutri" to e.infoNutricionalJson?.replace("\"", "'"),
        "nutri_raw" to e.infoNutricionalJson, "selos" to e.selosAltoEm,
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
        infoNutricionalJson = d["nutri_raw"] ?: d["nutri"]?.replace("'", "\""),
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

    private fun deConfiguracoes(e: ConfiguracoesEntity) = mapOf(
        "tema" to e.tema, "altoContraste" to e.altoContraste.toString(),
        "escala" to e.escalaDaFonte.toString(), "coresDinamicas" to e.coresDinamicas.toString(),
        "sons" to e.sons.toString(), "vibracao" to e.vibracao.toString(),
        "modoTecnico" to e.modoTecnico.toString(), "onboarding" to e.onboardingConcluido.toString(),
        "nutrientes" to e.nutrientesComparados, "dicas" to e.dicasVistas,
        "meta" to e.metaEconomiaCentavos?.toString(),
    )

    private fun paraConfiguracoes(d: Map<String, String>) = ConfiguracoesEntity(
        tema = d.getValue("tema"), altoContraste = d.getValue("altoContraste").toBooleanStrict(),
        escalaDaFonte = d.getValue("escala").toInt(), coresDinamicas = d.getValue("coresDinamicas").toBooleanStrict(),
        sons = d.getValue("sons").toBooleanStrict(), vibracao = d.getValue("vibracao").toBooleanStrict(),
        modoTecnico = d.getValue("modoTecnico").toBooleanStrict(), onboardingConcluido = d.getValue("onboarding").toBooleanStrict(),
        nutrientesComparados = d.getValue("nutrientes"), dicasVistas = d.getValue("dicas"),
        // O horario do ultimo backup e local ao dispositivo, nao ao arquivo importado.
        ultimoBackupMs = 0L, metaEconomiaCentavos = d["meta"]?.toLong(),
    )

    private fun deLixeira(e: RegistroDeLixeiraEntity) = mapOf(
        "id" to e.id.toString(), "tipo" to e.tipo, "titulo" to e.titulo, "detalhe" to e.detalhe,
        "ref" to e.refId.toString(), "payload" to e.payload, "enviada" to e.enviadaEm.toString(),
    )

    private fun paraLixeira(d: Map<String, String>) = RegistroDeLixeiraEntity(
        id = d.getValue("id").toLong().also { require(it > 0) }, tipo = d.getValue("tipo"),
        titulo = d.getValue("titulo"), detalhe = d["detalhe"], refId = d.getValue("ref").toLong(),
        payload = d.getValue("payload"), enviadaEm = d.getValue("enviada").toLong(),
    )

    private fun dePerfil(e: PerfilRestricoesEntity) = mapOf(
        "alergenos" to e.alergenos, "semGluten" to e.semGluten.toString(),
    )

    private fun paraPerfil(d: Map<String, String>) = PerfilRestricoesEntity(
        alergenos = d.getValue("alergenos"), semGluten = d.getValue("semGluten").toBooleanStrict(),
    )

    private fun deAlergia(e: AlergiaCustomizadaEntity) = mapOf(
        "id" to e.id.toString(), "nome" to e.nome, "palavras" to e.palavras,
    )

    private fun paraAlergia(d: Map<String, String>) = AlergiaCustomizadaEntity(
        id = d.getValue("id").toLong().also { require(it > 0) },
        nome = d.getValue("nome"), palavras = d.getValue("palavras"),
    )

    private fun deHistoricoPreco(e: HistoricoPrecoEntity) = mapOf(
        "id" to e.id.toString(), "prod" to e.produtoId.toString(),
        "loja" to e.estabelecimentoId.toString(), "cent" to e.precoCentavos.toString(),
        "qtd" to e.quantidadeBase, "data" to e.registradoEm.toString(),
    )

    private fun paraHistoricoPreco(d: Map<String, String>) = HistoricoPrecoEntity(
        id = d.getValue("id").toLong(),
        produtoId = d.getValue("prod").toLong(),
        estabelecimentoId = d.getValue("loja").toLong(),
        precoCentavos = d.getValue("cent").toLong(),
        quantidadeBase = d.getValue("qtd"),
        registradoEm = d.getValue("data").toLong(),
    )

    private fun deCompra(e: CompraEntity) = mapOf(
        "id" to e.id.toString(), "lista" to e.listaId?.toString(), "nome" to e.nomeLista,
        "data" to e.data.toString(), "total" to e.totalPagoCentavos.toString(),
        "econ" to e.economiaCentavos.toString(), "itens" to e.quantidadeItens.toString(),
        "estab" to e.descricaoEstabelecimento, "estabId" to e.estabelecimentoPrincipalId?.toString(),
        "cats" to e.gastosPorCategoriaJson.replace("\"", "'"),
        "cats_raw" to e.gastosPorCategoriaJson,
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
        gastosPorCategoriaJson = d["cats_raw"] ?: d["cats"]?.replace("'", "\"") ?: "{}",
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

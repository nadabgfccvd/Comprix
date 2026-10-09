package br.com.comprix.data.repositorio

import br.com.comprix.data.local.AlergiaCustomizadaEntity
import br.com.comprix.data.local.CategoriaEntity
import br.com.comprix.data.local.CompraEntity
import br.com.comprix.data.local.EstabelecimentoEntity
import br.com.comprix.data.local.ItemEntity
import br.com.comprix.data.local.ListaEntity
import br.com.comprix.data.local.LixeiraDao
import br.com.comprix.data.local.ProdutoEntity
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.util.JsonSimples
import br.com.comprix.util.TextoUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lixeira do Comprix: todo registro excluivel passa por aqui antes de sumir.
 *
 * ## Como funciona
 * 1. `enviar*` serializa o registro (payload JSON plano do [JsonSimples]),
 *    grava na tabela `lixeira` e SO ENTÃO apaga o original - a exclusao que o
 *    usuario ja conhece continua acontecendo, so que recuperavel por 30 dias.
 * 2. `restaurar` reconstrói o registro a partir do payload (com ids NOVOS,
 *    nunca reutilizando o id antigo, para nao colidir com dados criados depois).
 * 3. A purga permanente roda no arranque do processo e ao abrir a tela:
 *    `purgarExpirados` apaga o que foi enviado ha mais de [PRAZO_EM_DIAS] dias.
 *
 * Registros ATIVOS nunca sao tocados pela purga - ela so consulta `lixeira`.
 */
class LixeiraRepositorio(
    private val lixeiraDao: LixeiraDao,
    private val listaDao: br.com.comprix.data.local.ListaDao,
    private val itemDao: br.com.comprix.data.local.ItemDao,
    private val produtoDao: br.com.comprix.data.local.ProdutoDao,
    private val categoriaDao: br.com.comprix.data.local.CategoriaDao,
    private val estabelecimentoDao: br.com.comprix.data.local.EstabelecimentoDao,
    private val precoDao: br.com.comprix.data.local.PrecoDao,
    private val compraDao: br.com.comprix.data.local.CompraDao,
    private val alergiaDao: br.com.comprix.data.local.AlergiaCustomizadaDao,
) {
    companion object {
        /** Prazo visivel ao usuario antes da exclusao permanente. */
        const val PRAZO_EM_DIAS = 30L

        /** Prazo em millis (30 * 24h). */
        const val PRAZO_EM_MS: Long = PRAZO_EM_DIAS * 24L * 60L * 60L * 1000L

        private val FORMATO_DE_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    /** Categorias de conteudo da Lixeira (uma secao cada na tela). */
    enum class TipoDeLixeira(val rotulo: String) {
        LISTAS("Listas"),
        LOJAS("Lojas"),
        ALERGIAS("Alergias e restrições"),
        CATEGORIAS("Categorias"),
        PRODUTOS("Produtos do catálogo"),
        COMPRAS("Compras do histórico"),
    }

    // =================================================================================
    // Leitura
    // =================================================================================

    fun observarTodos(): Flow<List<RegistroDeLixeiraEntity>> = lixeiraDao.observarTodos()

    fun observarPorTipo(tipo: TipoDeLixeira): Flow<List<RegistroDeLixeiraEntity>> =
        lixeiraDao.observarPorTipo(tipo.name)

    /** Prazo de exclusao permanente de um registro, para exibir na UI. */
    fun expiraEm(registro: RegistroDeLixeiraEntity): LocalDateTime =
        Instant.ofEpochMilli(registro.enviadaEm + PRAZO_EM_MS).atZone(ZoneId.systemDefault())
            .toLocalDateTime()

    // =================================================================================
    // Envio (serializa, grava na lixeira e apaga o original)
    // =================================================================================

    /** Lista com os itens; preços anotados NAO voltam com a restauração (historico de preços por produto fica). */
    suspend fun enviarLista(listaId: Long): Boolean {
        val lista = listaDao.porId(listaId) ?: return false
        val itens = itemDao.listarDaLista(listaId)
        val nomeDosProdutos = itens.associate { it.produtoId to (produtoDao.porId(it.produtoId)?.nome ?: "?") }
        val payload = JsonSimples.paraJson(
            mapOf(
                "nome" to lista.nome,
                "criada" to lista.criadaEm.toString(),
                "finalizada" to lista.finalizada.toString(),
                "finalizadaEm" to lista.finalizadaEm?.toString(),
                "favorita" to lista.favorita.toString(),
                "orcamento" to lista.orcamentoCentavos?.toString(),
            ),
        )
        val itensPayload = JsonSimples.listaParaJson(
            itens.map { item ->
                mapOf(
                    "prodId" to item.produtoId.toString(),
                    "nomeProduto" to (nomeDosProdutos[item.produtoId] ?: "?"),
                    "qtd" to item.quantidade,
                    "un" to item.unidade,
                    "pv" to item.pesoOuVolume,
                    "kit" to item.ehKit.toString(),
                    "porKit" to item.itensPorKit?.toString(),
                    "comprado" to item.comprado.toString(),
                    "ordem" to item.ordemManual.toString(),
                    "modo" to item.modoComparacao,
                    "obs" to item.observacao,
                )
            },
        )
        val payloadCompleto = """{"dados":$payload,"itens":$itensPayload}"""
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.LISTAS.name,
                titulo = lista.nome,
                detalhe = "${itens.size} itens · criada em ${formatar(lista.criadaEm)}",
                refId = lista.id,
                payload = payloadCompleto,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        // CASCADE apaga itens e preços ligados ao item da lista.
        listaDao.remover(listaId)
        return true
    }

    /** Loja sozinha; os preços anotados na loja sao apagados e NAO voltam. */
    suspend fun enviarLoja(estabelecimentoId: Long): Boolean {
        val loja = estabelecimentoDao.porId(estabelecimentoId) ?: return false
        val payload = JsonSimples.paraJson(
            mapOf("nome" to loja.nome, "cor" to loja.corHex),
        )
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.LOJAS.name,
                titulo = loja.nome,
                detalhe = "Os preços anotados nela não voltam com a restauração",
                refId = loja.id,
                payload = payload,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        precoDao.removerDaLoja(estabelecimentoId)
        estabelecimentoDao.remover(estabelecimentoId)
        return true
    }

    suspend fun enviarAlergia(id: Long): Boolean {
        val alergia = alergiaDao.listarTodas().firstOrNull { it.id == id } ?: return false
        val payload = JsonSimples.paraJson(
            mapOf("nome" to alergia.nome, "palavras" to alergia.palavras),
        )
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.ALERGIAS.name,
                titulo = alergia.nome,
                detalhe = alergia.palavras.split("|").filter { it.isNotBlank() }.joinToString(", "),
                refId = alergia.id,
                payload = payload,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        alergiaDao.remover(id)
        return true
    }

    /** Somente categorias de origem USUARIO; as 14 padrao sao imutaveis. */
    suspend fun enviarCategoria(id: Long): Boolean {
        val categoria = categoriaDao.listarTodas().firstOrNull { it.id == id } ?: return false
        if (categoria.origem != "USUARIO") return false
        val payload = JsonSimples.paraJson(
            mapOf(
                "nome" to categoria.nome,
                "icone" to categoria.icone,
                "ordem" to categoria.ordemPadrao.toString(),
            ),
        )
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.CATEGORIAS.name,
                titulo = categoria.nome,
                detalhe = null,
                refId = categoria.id,
                payload = payload,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        categoriaDao.removerDoUsuario(id)
        return true
    }

    /** Produto do catalogo (criado pelo usuario ou pelo uso do app). */
    suspend fun enviarProduto(id: Long): Boolean {
        val produto = produtoDao.porId(id) ?: return false
        val payload = JsonSimples.paraJson(
            mapOf(
                "nome" to produto.nome,
                "norm" to produto.nomeNormalizado,
                "cat" to produto.categoriaId.toString(),
                "ean" to produto.codigoBarras,
                "un" to produto.unidadePadrao,
                "gluten" to produto.gluten,
                "alerg" to produto.alergenos,
                "fav" to produto.favorito.toString(),
            ),
        )
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.PRODUTOS.name,
                titulo = produto.nome,
                detalhe = null,
                refId = produto.id,
                payload = payload,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        produtoDao.remover(id)
        return true
    }

    /** Compra registrada no historico (com o resumo que ela ja guarda). */
    suspend fun enviarCompra(id: Long): Boolean {
        val compra = compraDao.porId(id) ?: return false
        val payload = JsonSimples.paraJson(
            mapOf(
                "listaId" to compra.listaId?.toString(),
                "nomeLista" to compra.nomeLista,
                "data" to compra.data.toString(),
                "total" to compra.totalPagoCentavos.toString(),
                "econ" to compra.economiaCentavos.toString(),
                "itens" to compra.quantidadeItens.toString(),
                "loja" to compra.descricaoEstabelecimento,
                "lojaId" to compra.estabelecimentoPrincipalId?.toString(),
                "porCat" to compra.gastosPorCategoriaJson,
            ),
        )
        lixeiraDao.inserir(
            RegistroDeLixeiraEntity(
                tipo = TipoDeLixeira.COMPRAS.name,
                titulo = compra.nomeLista,
                detalhe = "${compra.quantidadeItens} itens · ${formatar(compra.data)}",
                refId = compra.id,
                payload = payload,
                enviadaEm = System.currentTimeMillis(),
            ),
        )
        compraDao.remover(id)
        return true
    }

    // =================================================================================
    // Restauração
    // =================================================================================

    /** @return mensagem pronta para a torrada da UI. */
    suspend fun restaurar(registroId: Long): String {
        val registro = lixeiraDao.porId(registroId) ?: return "Registro não encontrado na lixeira."
        val tipo = TipoDeLixeira.entries.firstOrNull { it.name == registro.tipo }
            ?: return "Não sei restaurar este tipo de registro."
        val resultado = when (tipo) {
            TipoDeLixeira.LISTAS -> restaurarLista(registro)
            TipoDeLixeira.LOJAS -> restaurarLoja(registro)
            TipoDeLixeira.ALERGIAS -> restaurarAlergia(registro)
            TipoDeLixeira.CATEGORIAS -> restaurarCategoria(registro)
            TipoDeLixeira.PRODUTOS -> restaurarProduto(registro)
            TipoDeLixeira.COMPRAS -> restaurarCompra(registro)
        }
        if (resultado) lixeiraDao.remover(registroId)
        return if (resultado) {
            "\"${registro.titulo}\" restaurado(a)."
        } else {
            "Não consegui restaurar \"${registro.titulo}\"."
        }
    }

    private suspend fun restaurarLista(registro: RegistroDeLixeiraEntity): Boolean {
        val raiz = JsonSimples.deJson(extrairObjeto(registro.payload, "dados"))
        if (raiz.isEmpty()) return false
        val itens = JsonSimples.listaDeJson(extrairLista(registro.payload, "itens"))
        val novoId = listaDao.inserir(
            ListaEntity(
                nome = raiz["nome"] ?: registro.titulo,
                criadaEm = raiz["criada"]?.toLongOrNull() ?: System.currentTimeMillis(),
                finalizada = raiz["finalizada"]?.toBooleanStrictOrNull() ?: false,
                finalizadaEm = raiz["finalizadaEm"]?.toLongOrNull(),
                favorita = raiz["favorita"]?.toBooleanStrictOrNull() ?: false,
                orcamentoCentavos = raiz["orcamento"]?.toLongOrNull(),
            ),
        )
        itens.forEach { dados ->
            val produto = resolverProduto(dados["prodId"]?.toLongOrNull(), dados["nomeProduto"].orEmpty())
                ?: return@forEach
            itemDao.inserir(
                ItemEntity(
                    listaId = novoId,
                    produtoId = produto,
                    quantidade = dados["qtd"] ?: "1",
                    unidade = dados["un"] ?: "UNIDADE",
                    pesoOuVolume = dados["pv"],
                    ehKit = dados["kit"]?.toBooleanStrictOrNull() ?: false,
                    itensPorKit = dados["porKit"]?.toIntOrNull(),
                    comprado = dados["comprado"]?.toBooleanStrictOrNull() ?: false,
                    ordemManual = dados["ordem"]?.toIntOrNull() ?: 0,
                    modoComparacao = dados["modo"] ?: "POR_UNIDADE",
                    observacao = dados["obs"],
                ),
            )
        }
        return true
    }

    /** Acha o produto pelo id; cai para o nome; recria minimo quando sumiu. */
    private suspend fun resolverProduto(produtoId: Long?, nomeProduto: String): Long? {
        if (produtoId != null && produtoId > 0) {
            produtoDao.porId(produtoId)?.let { return it.id }
        }
        val nome = nomeProduto.trim()
        if (nome.isEmpty()) return null
        produtoDao.porNomeNormalizado(TextoUtil.normalizar(nome))?.let { return it.id }
        val primeiraCategoria = categoriaDao.listarTodas().firstOrNull()?.id ?: 0L
        return produtoDao.salvar(
            ProdutoEntity(
                nome = nome,
                nomeNormalizado = TextoUtil.normalizar(nome),
                categoriaId = primeiraCategoria,
                codigoBarras = null,
                unidadePadrao = "UNIDADE",
                infoNutricionalJson = null,
                selosAltoEm = "",
                ingredientes = null,
                gluten = "INDETERMINADO",
                alergenos = "",
                dataValidade = null,
                dataFabricacao = null,
                pesoMedioEstimadoEmBase = null,
                atualizadoEm = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun restaurarLoja(registro: RegistroDeLixeiraEntity): Boolean {
        val dados = JsonSimples.deJson(registro.payload)
        val nome = dados["nome"] ?: registro.titulo
        val nomeFinal = if (estabelecimentoDao.listarTodos().any { it.nome == nome }) {
            "$nome (restaurada)"
        } else {
            nome
        }
        estabelecimentoDao.inserir(
            EstabelecimentoEntity(nome = nomeFinal, corHex = dados["cor"] ?: "#1E8E5A"),
        )
        return true
    }

    private suspend fun restaurarAlergia(registro: RegistroDeLixeiraEntity): Boolean {
        val dados = JsonSimples.deJson(registro.payload)
        alergiaDao.inserir(
            AlergiaCustomizadaEntity(nome = dados["nome"] ?: registro.titulo, palavras = dados["palavras"].orEmpty()),
        )
        return true
    }

    private suspend fun restaurarCategoria(registro: RegistroDeLixeiraEntity): Boolean {
        val dados = JsonSimples.deJson(registro.payload)
        val nome = dados["nome"] ?: registro.titulo
        val chave = TextoUtil.normalizar(nome)
        if (categoriaDao.listarTodas().any { it.chave == chave }) return false
        categoriaDao.inserir(
            CategoriaEntity(
                nome = nome,
                icone = dados["icone"] ?: "OUTROS",
                ordemPadrao = dados["ordem"]?.toIntOrNull() ?: 900,
                origem = "USUARIO",
                chave = chave,
            ),
        )
        return true
    }

    private suspend fun restaurarProduto(registro: RegistroDeLixeiraEntity): Boolean {
        val dados = JsonSimples.deJson(registro.payload)
        val nome = dados["nome"] ?: registro.titulo
        val chave = dados["norm"] ?: TextoUtil.normalizar(nome)
        // Produto que voltou a existir por uso do app: nao duplica.
        if (produtoDao.porNomeNormalizado(chave) != null) return false
        val categoriaId = dados["cat"]?.toLongOrNull() ?: categoriaDao.listarTodas().firstOrNull()?.id ?: 0L
        produtoDao.salvar(
            ProdutoEntity(
                nome = nome,
                nomeNormalizado = chave,
                categoriaId = categoriaId,
                codigoBarras = dados["ean"],
                unidadePadrao = dados["un"] ?: "UNIDADE",
                infoNutricionalJson = null,
                selosAltoEm = "",
                ingredientes = null,
                gluten = dados["gluten"] ?: "INDETERMINADO",
                alergenos = dados["alerg"].orEmpty(),
                dataValidade = null,
                dataFabricacao = null,
                pesoMedioEstimadoEmBase = null,
                atualizadoEm = System.currentTimeMillis(),
                favorito = dados["fav"]?.toBooleanStrictOrNull() ?: false,
            ),
        )
        return true
    }

    private suspend fun restaurarCompra(registro: RegistroDeLixeiraEntity): Boolean {
        val dados = JsonSimples.deJson(registro.payload)
        compraDao.inserir(
            CompraEntity(
                listaId = dados["listaId"]?.toLongOrNull(),
                nomeLista = dados["nomeLista"] ?: registro.titulo,
                data = dados["data"]?.toLongOrNull() ?: System.currentTimeMillis(),
                totalPagoCentavos = dados["total"]?.toLongOrNull() ?: 0,
                economiaCentavos = dados["econ"]?.toLongOrNull() ?: 0,
                quantidadeItens = dados["itens"]?.toIntOrNull() ?: 0,
                descricaoEstabelecimento = dados["loja"].orEmpty(),
                estabelecimentoPrincipalId = dados["lojaId"]?.toLongOrNull(),
                gastosPorCategoriaJson = dados["porCat"].orEmpty(),
            ),
        )
        return true
    }

    // =================================================================================
    // Esvaziar e purgar
    // =================================================================================

    /** Esvazia UMA categoria de conteudo - permanente, chamado com dupla confirmacao. */
    suspend fun esvaziarPorTipo(tipo: TipoDeLixeira): Int = lixeiraDao.removerPorTipo(tipo.name)

    /** Esvazia TUDO - permanente, chamado com dupla confirmacao. */
    suspend fun esvaziarTudo(): Int = lixeiraDao.removerTudo()

    /**
     * Exclui UM registro da lixeira AGORA ("excluir agora" do cartao, com
     * confirmacao simples na UI) - sem esperar os 30 dias.
     *
     * @return `true` quando o registro existia e foi apagado.
     */
    suspend fun removerRegistro(id: Long): Boolean {
        if (lixeiraDao.porId(id) == null) return false
        lixeiraDao.remover(id)
        return true
    }

    /**
     * Purga permanente do que passou de [PRAZO_EM_DIAS]. Roda no arranque do
     * processo e ao abrir a Lixeira; so consulta a tabela `lixeira`, nunca os
     * registros ativos.
     */
    suspend fun purgarExpirados(agora: Long = System.currentTimeMillis()): Int =
        lixeiraDao.removerEnviadosAntesDe(agora - PRAZO_EM_MS)

    suspend fun contar(): Int = lixeiraDao.contar()

    // =================================================================================
    // Auxiliares
    // =================================================================================

    private fun formatar(epoch: Long): String =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime()
            .format(FORMATO_DE_DATA)

    /** Payload da lista = {"dados":{...},"itens":[...]}; pega o objeto da chave. */
    private fun extrairObjeto(payload: String, chave: String): String {
        val alvo = "\"$chave\":{"
        val inicio = payload.indexOf(alvo)
        if (inicio < 0) return ""
        var profundidade = 0
        for (i in inicio + alvo.length - 1 until payload.length) {
            when (payload[i]) {
                '{' -> profundidade++
                '}' -> {
                    profundidade--
                    if (profundidade == 0) return payload.substring(inicio + alvo.length - 1, i + 1)
                }
            }
        }
        return ""
    }

    /** Pega a lista JSON da chave do payload composto. */
    private fun extrairLista(payload: String, chave: String): String {
        val alvo = "\"$chave\":["
        val inicio = payload.indexOf(alvo)
        if (inicio < 0) return ""
        var profundidade = 0
        for (i in inicio + alvo.length - 1 until payload.length) {
            when (payload[i]) {
                '[' -> profundidade++
                ']' -> {
                    profundidade--
                    if (profundidade == 0) return payload.substring(inicio + alvo.length - 1, i + 1)
                }
            }
        }
        return ""
    }
}

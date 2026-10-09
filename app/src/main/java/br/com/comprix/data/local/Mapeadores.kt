package br.com.comprix.data.local

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.OrigemCategoria
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.domain.modelo.TipoTema
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.Constantes
import br.com.comprix.util.JsonSimples
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Conversao entre as entidades do Room e os modelos de dominio.
 *
 * Toda a "sujeira" de persistencia mora aqui: centavos viram [BigDecimal],
 * epoch millis viram [LocalDateTime], texto separado por `|` vira lista de
 * enum. O dominio nunca ve um `Long` de data nem uma `String` de enum.
 *
 * Decisao deliberada: enums invalidos (vindos de um backup de versao futura,
 * por exemplo) caem num valor seguro em vez de lancar excecao - o app abre
 * mesmo com um dado estranho, e o usuario pode corrigir na tela.
 */
object Mapeadores {

    private val FUSO: ZoneId = ZoneId.systemDefault()
    private const val SEPARADOR = "|"

    // --- primitivos ------------------------------------------------------------------

    fun centavosParaReais(centavos: Long): BigDecimal =
        BigDecimal(centavos).movePointLeft(Constantes.ESCALA_MOEDA)

    fun reaisParaCentavos(valor: BigDecimal): Long =
        valor.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN).movePointRight(Constantes.ESCALA_MOEDA).toLong()

    fun epochParaDataHora(epoch: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), FUSO)

    fun dataHoraParaEpoch(valor: LocalDateTime): Long =
        valor.atZone(FUSO).toInstant().toEpochMilli()

    fun epochParaData(epoch: Long?): LocalDate? =
        epoch?.let { LocalDate.ofEpochDay(it) }

    fun dataParaEpoch(valor: LocalDate?): Long? = valor?.toEpochDay()

    private fun texto(valor: BigDecimal?): String? = valor?.stripTrailingZeros()?.toPlainString()

    private fun decimal(valor: String?): BigDecimal? =
        valor?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private fun <T : Enum<T>> lista(texto: String, valores: Array<T>): List<T> =
        texto.split(SEPARADOR)
            .filter { it.isNotBlank() }
            .mapNotNull { nome -> valores.firstOrNull { it.name == nome } }

    private fun <T : Enum<T>> juntar(itens: List<T>): String = itens.joinToString(SEPARADOR) { it.name }

    // --- categoria -------------------------------------------------------------------

    fun paraDominio(entidade: CategoriaEntity) = Categoria(
        id = entidade.id,
        nome = entidade.nome,
        icone = entidade.icone,
        ordemPadrao = entidade.ordemPadrao,
        origem = OrigemCategoria.entries.firstOrNull { it.name == entidade.origem } ?: OrigemCategoria.USUARIO,
        chave = entidade.chave,
    )

    fun paraEntidade(categoria: Categoria) = CategoriaEntity(
        id = categoria.id,
        nome = categoria.nome,
        icone = categoria.icone,
        ordemPadrao = categoria.ordemPadrao,
        origem = categoria.origem.name,
        chave = categoria.chave,
    )

    // --- produto ---------------------------------------------------------------------

    fun paraDominio(entidade: ProdutoEntity) = Produto(
        id = entidade.id,
        nome = entidade.nome,
        nomeNormalizado = entidade.nomeNormalizado,
        categoriaId = entidade.categoriaId,
        codigoBarras = entidade.codigoBarras,
        unidadePadrao = Unidade.entries.firstOrNull { it.name == entidade.unidadePadrao } ?: Unidade.UNIDADE,
        infoNutricional = infoNutricionalDeJson(entidade.infoNutricionalJson),
        selosAltoEm = lista(entidade.selosAltoEm, SeloAltoEm.entries.toTypedArray()),
        ingredientes = entidade.ingredientes,
        gluten = IndicacaoGluten.entries.firstOrNull { it.name == entidade.gluten } ?: IndicacaoGluten.INDETERMINADO,
        alergenos = lista(entidade.alergenos, Alergeno.entries.toTypedArray()),
        dataValidade = epochParaData(entidade.dataValidade),
        dataFabricacao = epochParaData(entidade.dataFabricacao),
        pesoMedioEstimadoEmBase = decimal(entidade.pesoMedioEstimadoEmBase),
        favorito = entidade.favorito,
    )

    fun paraEntidade(produto: Produto, atualizadoEm: Long = System.currentTimeMillis()) = ProdutoEntity(
        id = produto.id,
        nome = produto.nome,
        nomeNormalizado = produto.nomeNormalizado,
        categoriaId = produto.categoriaId,
        codigoBarras = produto.codigoBarras,
        unidadePadrao = produto.unidadePadrao.name,
        infoNutricionalJson = infoNutricionalParaJson(produto.infoNutricional),
        selosAltoEm = juntar(produto.selosAltoEm),
        ingredientes = produto.ingredientes,
        gluten = produto.gluten.name,
        alergenos = juntar(produto.alergenos),
        dataValidade = dataParaEpoch(produto.dataValidade),
        dataFabricacao = dataParaEpoch(produto.dataFabricacao),
        pesoMedioEstimadoEmBase = texto(produto.pesoMedioEstimadoEmBase),
        atualizadoEm = atualizadoEm,
        favorito = produto.favorito,
    )

    /** Tabela nutricional: um JSON plano com a porcao e os nutrientes por chave. */
    fun infoNutricionalParaJson(info: InfoNutricional?): String? {
        if (info == null || info.vazia) return null
        val dados = LinkedHashMap<String, String?>()
        info.porcaoDescricao?.let { dados["porcao"] = it }
        info.valores.forEach { (nutriente, valor) -> dados[nutriente.chave] = valor.toPlainString() }
        return JsonSimples.paraJson(dados)
    }

    fun infoNutricionalDeJson(json: String?): InfoNutricional? {
        if (json.isNullOrBlank()) return null
        val dados = JsonSimples.deJson(json)
        if (dados.isEmpty()) return null
        val valores = LinkedHashMap<Nutriente, BigDecimal>()
        dados.forEach { (chave, valor) ->
            if (chave == "porcao") return@forEach
            val nutriente = Nutriente.porChave(chave) ?: return@forEach
            decimal(valor)?.let { valores[nutriente] = it }
        }
        val porcao = dados["porcao"]
        if (valores.isEmpty() && porcao.isNullOrBlank()) return null
        return InfoNutricional(porcaoDescricao = porcao, valores = valores)
    }

    // --- lista e itens ---------------------------------------------------------------

    fun paraDominio(entidade: ListaEntity) = ListaDeCompras(
        id = entidade.id,
        nome = entidade.nome,
        criadaEm = epochParaDataHora(entidade.criadaEm),
        finalizada = entidade.finalizada,
        finalizadaEm = entidade.finalizadaEm?.let { epochParaDataHora(it) },
        favorita = entidade.favorita,
        orcamentoCentavos = entidade.orcamentoCentavos,
    )

    fun paraEntidade(lista: ListaDeCompras) = ListaEntity(
        id = lista.id,
        nome = lista.nome,
        criadaEm = dataHoraParaEpoch(lista.criadaEm),
        finalizada = lista.finalizada,
        finalizadaEm = lista.finalizadaEm?.let { dataHoraParaEpoch(it) },
        favorita = lista.favorita,
        orcamentoCentavos = lista.orcamentoCentavos,
    )

    fun paraDominio(entidade: ItemEntity) = ItemDaLista(
        id = entidade.id,
        listaId = entidade.listaId,
        produtoId = entidade.produtoId,
        quantidade = decimal(entidade.quantidade) ?: BigDecimal.ONE,
        unidade = Unidade.entries.firstOrNull { it.name == entidade.unidade } ?: Unidade.UNIDADE,
        pesoOuVolume = decimal(entidade.pesoOuVolume),
        ehKit = entidade.ehKit,
        itensPorKit = entidade.itensPorKit,
        comprado = entidade.comprado,
        ordemManual = entidade.ordemManual,
        modoComparacao = ModoComparacaoUnidade.entries.firstOrNull { it.name == entidade.modoComparacao }
            ?: ModoComparacaoUnidade.POR_UNIDADE,
        observacao = entidade.observacao,
    )

    fun paraEntidade(item: ItemDaLista) = ItemEntity(
        id = item.id,
        listaId = item.listaId,
        produtoId = item.produtoId,
        quantidade = item.quantidade.toPlainString(),
        unidade = item.unidade.name,
        pesoOuVolume = texto(item.pesoOuVolume),
        ehKit = item.ehKit,
        itensPorKit = item.itensPorKit,
        comprado = item.comprado,
        ordemManual = item.ordemManual,
        modoComparacao = item.modoComparacao.name,
        observacao = item.observacao,
    )

    // --- estabelecimento e precos ----------------------------------------------------

    fun paraDominio(entidade: EstabelecimentoEntity) =
        Estabelecimento(id = entidade.id, nome = entidade.nome, corHex = entidade.corHex)

    fun paraEntidade(estabelecimento: Estabelecimento) =
        EstabelecimentoEntity(id = estabelecimento.id, nome = estabelecimento.nome, corHex = estabelecimento.corHex)

    fun paraDominio(entidade: PrecoEntity) = PrecoRegistrado(
        id = entidade.id,
        itemDaListaId = entidade.itemDaListaId,
        estabelecimentoId = entidade.estabelecimentoId,
        preco = centavosParaReais(entidade.precoCentavos),
        dataRegistro = epochParaDataHora(entidade.dataRegistro),
        disponivel = entidade.disponivel,
    )

    fun paraEntidade(preco: PrecoRegistrado) = PrecoEntity(
        id = preco.id,
        itemDaListaId = preco.itemDaListaId,
        estabelecimentoId = preco.estabelecimentoId,
        precoCentavos = reaisParaCentavos(preco.preco),
        dataRegistro = dataHoraParaEpoch(preco.dataRegistro),
        disponivel = preco.disponivel,
    )

    // --- compras ---------------------------------------------------------------------

    fun paraDominio(entidade: CompraEntity) = CompraFinalizada(
        id = entidade.id,
        listaId = entidade.listaId,
        nomeLista = entidade.nomeLista,
        data = epochParaDataHora(entidade.data),
        totalPago = centavosParaReais(entidade.totalPagoCentavos),
        economia = centavosParaReais(entidade.economiaCentavos),
        quantidadeItens = entidade.quantidadeItens,
        descricaoEstabelecimento = entidade.descricaoEstabelecimento,
        estabelecimentoPrincipalId = entidade.estabelecimentoPrincipalId,
        gastosPorCategoria = JsonSimples.deJson(entidade.gastosPorCategoriaJson)
            .mapNotNull { (categoria, centavos) ->
                centavos.toLongOrNull()?.let { categoria to centavosParaReais(it) }
            }
            .toMap(),
    )

    fun paraEntidade(compra: CompraFinalizada) = CompraEntity(
        id = compra.id,
        listaId = compra.listaId,
        nomeLista = compra.nomeLista,
        data = dataHoraParaEpoch(compra.data),
        totalPagoCentavos = reaisParaCentavos(compra.totalPago),
        economiaCentavos = reaisParaCentavos(compra.economia),
        quantidadeItens = compra.quantidadeItens,
        descricaoEstabelecimento = compra.descricaoEstabelecimento,
        estabelecimentoPrincipalId = compra.estabelecimentoPrincipalId,
        gastosPorCategoriaJson = JsonSimples.paraJson(
            compra.gastosPorCategoria.mapValues { reaisParaCentavos(it.value).toString() },
        ),
    )

    // --- configuracoes ---------------------------------------------------------------

    fun paraDominio(entidade: ConfiguracoesEntity?) = ConfiguracoesApp(
        tema = TipoTema.entries.firstOrNull { it.name == entidade?.tema } ?: TipoTema.SISTEMA,
        altoContraste = entidade?.altoContraste ?: false,
        escalaDaFonte = (entidade?.escalaDaFonte ?: 100).coerceIn(100, 160),
        coresDinamicas = entidade?.coresDinamicas ?: false,
        sons = entidade?.sons ?: true,
        vibracao = entidade?.vibracao ?: true,
        modoTecnico = entidade?.modoTecnico ?: false,
        onboardingConcluido = entidade?.onboardingConcluido ?: false,
        nutrientesComparados = entidade?.nutrientesComparados
            ?.let { lista(it, Nutriente.entries.toTypedArray()) }
            ?.takeIf { it.isNotEmpty() }
            ?: Nutriente.selecaoPadrao(),
        dicasVistas = entidade?.dicasVistas?.split(SEPARADOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
        ultimoBackupMs = entidade?.ultimoBackupMs ?: 0L,
        metaEconomiaCentavos = entidade?.metaEconomiaCentavos,
    )

    fun paraEntidade(configuracoes: ConfiguracoesApp) = ConfiguracoesEntity(
        id = 1,
        tema = configuracoes.tema.name,
        altoContraste = configuracoes.altoContraste,
        escalaDaFonte = configuracoes.escalaDaFonte.coerceIn(100, 160),
        coresDinamicas = configuracoes.coresDinamicas,
        sons = configuracoes.sons,
        vibracao = configuracoes.vibracao,
        modoTecnico = configuracoes.modoTecnico,
        onboardingConcluido = configuracoes.onboardingConcluido,
        nutrientesComparados = juntar(configuracoes.nutrientesComparados),
        dicasVistas = configuracoes.dicasVistas.joinToString(SEPARADOR),
        ultimoBackupMs = configuracoes.ultimoBackupMs,
        metaEconomiaCentavos = configuracoes.metaEconomiaCentavos,
    )

    fun paraDominio(entidade: PerfilRestricoesEntity?) = PerfilRestricoes(
        alergenos = entidade?.alergenos?.let { lista(it, Alergeno.entries.toTypedArray()).toSet() } ?: emptySet(),
        semGluten = entidade?.semGluten ?: false,
    )

    fun paraEntidade(perfil: PerfilRestricoes) = PerfilRestricoesEntity(
        id = 1,
        alergenos = perfil.alergenos.joinToString(SEPARADOR) { it.name },
        semGluten = perfil.semGluten,
    )

    // --- alergias customizadas ---------------------------------------------------------

    fun AlergiaCustomizadaEntity.paraModelo() = AlergiaCustomizada(
        id = id,
        nome = nome,
        palavras = palavras.split(SEPARADOR).filter { it.isNotBlank() },
    )

    fun AlergiaCustomizada.paraEntidade() = AlergiaCustomizadaEntity(
        nome = nome.trim(),
        palavras = palavras.joinToString(SEPARADOR),
    )
}

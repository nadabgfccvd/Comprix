package br.com.comprix.data.repositorio

import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.data.local.EstabelecimentoDao
import br.com.comprix.data.local.HistoricoPrecoEntity
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.local.PrecoDao
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.unidade.ConversorDeUnidades
import br.com.comprix.util.Formatadores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * Precos registrados e estabelecimentos.
 *
 * Alem de gravar o preco do item na lista atual, cada registro alimenta o
 * **historico por produto**, que sobrevive ao apagar a lista. E ele que permite
 * dizer "voce ja viu esse arroz por R$ 21,90 no mes passado".
 */
class PrecoRepositorio(
    private val precoDao: PrecoDao,
    private val estabelecimentoDao: EstabelecimentoDao,
    private val lixeira: LixeiraRepositorio? = null,
) {

    val estabelecimentos: Flow<List<Estabelecimento>> =
        estabelecimentoDao.observarTodos().map { lista -> lista.map(Mapeadores::paraDominio) }

    fun observarPrecosDaLista(listaId: Long): Flow<List<PrecoRegistrado>> =
        precoDao.observarDaLista(listaId).map { lista -> lista.map(Mapeadores::paraDominio) }

    suspend fun listarPrecosDaLista(listaId: Long): List<PrecoRegistrado> =
        precoDao.listarDaLista(listaId).map(Mapeadores::paraDominio)

    suspend fun listarEstabelecimentos(): List<Estabelecimento> =
        estabelecimentoDao.listarTodos().map(Mapeadores::paraDominio)

    /**
     * Cria o estabelecimento se ainda nao existir (comparacao por nome, sem
     * diferenciar maiusculas). A cor vem da paleta, em rodizio.
     */
    suspend fun garantirEstabelecimento(nome: String): Estabelecimento {
        val limpo = nome.trim().ifBlank { DadosIniciais.ESTABELECIMENTO_PADRAO }
        estabelecimentoDao.porNome(limpo)?.let { return Mapeadores.paraDominio(it) }

        val existentes = estabelecimentoDao.listarTodos()
        val cor = DadosIniciais.CORES_DE_LOJA[existentes.size % DadosIniciais.CORES_DE_LOJA.size]
        val id = estabelecimentoDao.inserir(
            Mapeadores.paraEntidade(Estabelecimento(nome = limpo, corHex = cor)),
        )
        return if (id > 0) {
            Estabelecimento(id = id, nome = limpo, corHex = cor)
        } else {
            estabelecimentoDao.porNome(limpo)?.let(Mapeadores::paraDominio)
                ?: Estabelecimento(nome = limpo, corHex = cor)
        }
    }

    suspend fun renomearEstabelecimento(estabelecimento: Estabelecimento, novoNome: String) {
        estabelecimentoDao.atualizar(Mapeadores.paraEntidade(estabelecimento.copy(nome = novoNome.trim())))
    }

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias, sem os preços). */
    suspend fun removerEstabelecimento(id: Long) {
        if (lixeira != null && lixeira.enviarLoja(id)) return
        precoDao.removerDaLoja(id)
        estabelecimentoDao.remover(id)
    }

    /**
     * Grava o preco de um item numa loja (substitui o anterior, se houver) e
     * alimenta o historico do produto.
     *
     * @param produtoId usado so pelo historico; o preco em si pertence ao item.
     */
    suspend fun registrarPreco(
        item: ItemDaLista,
        produtoId: Long,
        estabelecimentoId: Long,
        preco: BigDecimal,
        disponivel: Boolean = true,
        momento: LocalDateTime = LocalDateTime.now(),
    ) {
        val existente = precoDao.doItemNaLoja(item.id, estabelecimentoId)
        precoDao.salvar(
            Mapeadores.paraEntidade(
                PrecoRegistrado(
                    id = existente?.id ?: 0,
                    itemDaListaId = item.id,
                    estabelecimentoId = estabelecimentoId,
                    preco = preco,
                    dataRegistro = momento,
                    disponivel = disponivel,
                ),
            ),
        )
        if (disponivel && preco.signum() > 0) {
            precoDao.registrarHistorico(
                HistoricoPrecoEntity(
                    produtoId = produtoId,
                    estabelecimentoId = estabelecimentoId,
                    precoCentavos = Mapeadores.reaisParaCentavos(preco),
                    quantidadeBase = ConversorDeUnidades
                        .paraUnidadeBase(item.pesoOuVolume ?: BigDecimal.ONE, item.unidade, item.itensPorKit)
                        .toPlainString(),
                    registradoEm = Mapeadores.dataHoraParaEpoch(momento),
                ),
            )
        }
    }

    /**
     * Grava o preco (mesma gravacao de [registrarPreco]) e avisa quando o
     * valor digitado ficou ACIMA do menor ja registrado para o produto.
     *
     * ## Por que o minimo considera QUALQUER loja
     *
     * O historico e por produto (historico_precos nao tem chave por item):
     * a coluna de loja e apenas contexto da linha. Para o objetivo do aviso -
     * "voce ja viu este arroz mais barato" - o que importa e a melhor observacao
     * que o usuario ja fez, nao a melhor observacao POR loja: a boa oferta da
     * rede concorrente e exatamente a referencia que ajuda a decidir comprar
     * agora ou procurar. Janela por loja deixaria o aviso mudo no caso mais
     * util (trocar de mercado de uma compra para a outra).
     *
     * ## Regras do aviso
     *
     * - O minimo e capturado ANTES da gravacao, para o registro atual nunca se
     *   auto-referenciar.
     * - Primeiro registro do produto (minimo nulo) e preco novo que EMPATA ou
     *   bate o recorde nao avisam nada.
     * - Preco indisponivel ou zero nao entra no historico e, por isso, nunca
     *   avisa.
     *
     * @return `null` quando tudo esta normal, ou a mensagem pronta para a
     *   torrada ("Atencao: este preco esta X% acima do menor registrado para
     *   este produto (R$ Y).").
     */
    suspend fun registrarPrecoComAviso(
        item: ItemDaLista,
        produtoId: Long,
        estabelecimentoId: Long,
        preco: BigDecimal,
        disponivel: Boolean = true,
        momento: LocalDateTime = LocalDateTime.now(),
    ): String? {
        val minimoAnteriorCentavos = if (disponivel && preco.signum() > 0) {
            precoDao.menorPrecoRegistrado(produtoId)
        } else {
            null
        }
        registrarPreco(item, produtoId, estabelecimentoId, preco, disponivel, momento)

        val minimoCentavos = minimoAnteriorCentavos ?: return null
        if (minimoCentavos <= 0) return null
        val centavosNovos = Mapeadores.reaisParaCentavos(preco)
        if (centavosNovos <= minimoCentavos) return null

        val acimaEmCentavos = BigDecimal(centavosNovos - minimoCentavos)
        val percentual = acimaEmCentavos
            .multiply(BigDecimal(100))
            .divide(BigDecimal(minimoCentavos), 0, RoundingMode.HALF_UP)
        val menorFormatado = Formatadores.moeda(Mapeadores.centavosParaReais(minimoCentavos))
        return "Atenção: este preço está $percentual% acima do menor registrado para este produto ($menorFormatado)."
    }

    /** Marca "nao tinha na loja" - diferente de "ainda nao pesquisei". */
    suspend fun marcarIndisponivel(itemId: Long, estabelecimentoId: Long) {
        val existente = precoDao.doItemNaLoja(itemId, estabelecimentoId)
        precoDao.salvar(
            Mapeadores.paraEntidade(
                PrecoRegistrado(
                    id = existente?.id ?: 0,
                    itemDaListaId = itemId,
                    estabelecimentoId = estabelecimentoId,
                    preco = existente?.let { Mapeadores.centavosParaReais(it.precoCentavos) } ?: BigDecimal.ZERO,
                    dataRegistro = LocalDateTime.now(),
                    disponivel = false,
                ),
            ),
        )
    }

    suspend fun removerPreco(itemId: Long, estabelecimentoId: Long) = precoDao.remover(itemId, estabelecimentoId)

    suspend fun precosDoItem(itemId: Long): List<PrecoRegistrado> =
        precoDao.doItem(itemId).map(Mapeadores::paraDominio)

    /** Menor preco ja visto para o produto nos ultimos [dias] dias. */
    suspend fun menorPrecoRecente(produtoId: Long, dias: Long = 90): BigDecimal? {
        val desde = System.currentTimeMillis() - dias * 24 * 60 * 60 * 1000
        return precoDao.menorPrecoRecente(produtoId, desde)?.let(Mapeadores::centavosParaReais)
    }

    data class PontoDeHistorico(val quando: LocalDateTime, val preco: BigDecimal, val estabelecimentoId: Long)

    /** Serie historica de precos do produto, da mais recente para a mais antiga. */
    suspend fun historicoDoProduto(produtoId: Long, limite: Int = 20): List<PontoDeHistorico> =
        precoDao.historicoDoProduto(produtoId, limite).map { entidade ->
            PontoDeHistorico(
                quando = Mapeadores.epochParaDataHora(entidade.registradoEm),
                preco = Mapeadores.centavosParaReais(entidade.precoCentavos),
                estabelecimentoId = entidade.estabelecimentoId,
            )
        }
}

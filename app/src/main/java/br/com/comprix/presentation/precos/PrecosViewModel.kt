package br.com.comprix.presentation.precos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.HistoricoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.MatrizComparativa
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.util.Formatadores
import br.com.comprix.util.ExportadorCsv
import br.com.comprix.data.local.DadosIniciais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Estado da tela de registro de precos e da matriz de comparacao.
 *
 * ## Modelo mental: lista mestra + colunas de preco
 * Existe **uma** lista de produtos (a lista mestra). Cada estabelecimento e uma
 * **coluna de precos** sobre essa mesma lista. Dessa escolha decorrem tres
 * regras que o codigo abaixo garante:
 *
 * 1. **Preco e por (item, loja).** O estado nunca guarda "o preco do item":
 *    guarda o preco de um item **numa loja**, com chave composta
 *    [ChaveDePreco]. Por isso digitar R$ 7,90 no Mercado 1 nao mexe no
 *    Mercado 2 - sao duas chaves diferentes, duas linhas diferentes no banco.
 * 2. **Apagar preco != apagar item.** [limparPrecoDaLoja] remove apenas a
 *    celula daquela loja; o produto continua na lista e nas outras colunas.
 *    Tirar o produto da lista inteira e outra acao, explicita e com aviso:
 *    [removerItemDaListaMestra].
 * 3. **O que falta fica visivel.** [EstadoDePrecos.relatorio] e recalculado a
 *    cada mudanca e alimenta o painel fixo no alto da tela, dizendo quais
 *    produtos ainda nao tem preco em quais lojas.
 */
class PrecosViewModel(
    private val listaId: Long,
    private val listaRepositorio: ListaRepositorio,
    private val precoRepositorio: PrecoRepositorio,
    private val catalogoRepositorio: CatalogoRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
    private val historicoRepositorio: HistoricoRepositorio,
) : ViewModel() {

    /** Identidade de uma celula: um item **em uma** loja. Nunca so o item. */
    data class ChaveDePreco(val itemId: Long, val estabelecimentoId: Long)

    /** Acao reversivel (mostra "Desfazer" na barra inferior). */
    data class AcaoDesfazivel(
        val descricao: String,
        val item: ItemDaLista? = null,
        val precos: List<PrecoRegistrado> = emptyList(),
        val tipo: Tipo,
    ) {
        enum class Tipo { PRECO_REMOVIDO, ITEM_REMOVIDO }
    }

    data class EstadoDePrecos(
        val lista: ListaDeCompras? = null,
        val itens: List<ItemComProduto> = emptyList(),
        val estabelecimentos: List<Estabelecimento> = emptyList(),
        val precos: Map<ChaveDePreco, PrecoRegistrado> = emptyMap(),
        val categorias: Map<Long, Categoria> = emptyMap(),
        val perfil: PerfilRestricoes = PerfilRestricoes(),
        val lojaAtivaId: Long? = null,
        val relatorio: AuditoriaDeCobertura.Relatorio =
            AuditoriaDeCobertura.Relatorio(emptyList(), emptyList(), emptyList(), emptyList(), 0),
        val carregando: Boolean = true,
    ) {
        /** Preco de um item **em uma loja** - a unica forma de ler um preco aqui. */
        fun precoDe(itemId: Long, estabelecimentoId: Long): BigDecimal? =
            precos[ChaveDePreco(itemId, estabelecimentoId)]
                ?.takeIf { it.disponivel && it.preco.signum() > 0 }
                ?.preco

        fun situacaoDe(itemId: Long, estabelecimentoId: Long): AuditoriaDeCobertura.Situacao {
            val registro = precos[ChaveDePreco(itemId, estabelecimentoId)]
            return when {
                registro == null -> AuditoriaDeCobertura.Situacao.PENDENTE
                !registro.disponivel -> AuditoriaDeCobertura.Situacao.INDISPONIVEL
                registro.preco.signum() <= 0 -> AuditoriaDeCobertura.Situacao.PENDENTE
                else -> AuditoriaDeCobertura.Situacao.REGISTRADO
            }
        }

        val lojaAtiva: Estabelecimento?
            get() = estabelecimentos.firstOrNull { it.id == lojaAtivaId } ?: estabelecimentos.firstOrNull()

        /** Itens que ainda nao tem preco na loja aberta na tela. */
        fun pendentesDaLojaAtiva(): List<AuditoriaDeCobertura.Pendencia> =
            lojaAtiva?.let { relatorio.pendenciasDaLoja(it.id) }.orEmpty()

        /** Matriz completa (so faz sentido com 2+ lojas). */
        val matriz: MatrizComparativa?
            get() = if (estabelecimentos.size < 2 || itens.isEmpty()) {
                null
            } else {
                MotorDePrecos.montarMatriz(
                    itens = itens,
                    estabelecimentos = estabelecimentos,
                    precos = precos.values.toList(),
                    categorias = categorias,
                    perfil = perfil,
                )
            }
    }

    private val lojaSelecionada = MutableStateFlow<Long?>(null)

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private val _desfazer = MutableStateFlow<AcaoDesfazivel?>(null)
    val desfazer: StateFlow<AcaoDesfazivel?> = _desfazer.asStateFlow()

    val estado: StateFlow<EstadoDePrecos> = combine(
        listaRepositorio.observarLista(listaId),
        listaRepositorio.observarItens(listaId),
        precoRepositorio.estabelecimentos,
        precoRepositorio.observarPrecosDaLista(listaId),
        combine(catalogoRepositorio.categorias, configuracoesRepositorio.perfil, lojaSelecionada) { c, p, l ->
            Triple(c, p, l)
        },
    ) { lista, itens, estabelecimentos, precos, extras ->
        val (categorias, perfil, selecionada) = extras
        EstadoDePrecos(
            lista = lista,
            itens = itens,
            estabelecimentos = estabelecimentos,
            precos = precos.associateBy { ChaveDePreco(it.itemDaListaId, it.estabelecimentoId) },
            categorias = categorias.associateBy { it.id },
            perfil = perfil,
            lojaAtivaId = selecionada ?: estabelecimentos.firstOrNull()?.id,
            relatorio = AuditoriaDeCobertura.auditar(itens, estabelecimentos, precos),
            carregando = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDePrecos())

    // --- selecao de loja -------------------------------------------------------------

    fun selecionarLoja(estabelecimentoId: Long) {
        lojaSelecionada.value = estabelecimentoId
    }

    /** Cria (ou reaproveita) uma loja e ja a deixa ativa. */
    fun criarOuSelecionarLoja(nome: String) {
        viewModelScope.launch {
            val loja = precoRepositorio.garantirEstabelecimento(
                nome.ifBlank { DadosIniciais.ESTABELECIMENTO_PADRAO },
            )
            lojaSelecionada.value = loja.id
        }
    }

    // --- registro de precos ----------------------------------------------------------

    /**
     * Anota o preco de um item **em uma loja**.
     *
     * Usa [PrecoRepositorio.registrarPrecoComAviso]: se o preco digitado ficou
     * acima do menor ja registrado para o produto (em qualquer loja), a torrada
     * avisa o usuario. Restauracoes ([desfazerUltimaAcao]) e copias entre lojas
     * seguem no gravador simples de proposito - regravar dado que ja existia
     * nao e observacao nova e nao deve disparar aviso.
     *
     * @param valor null ou zero limpa a celula daquela loja (equivale a
     * [limparPrecoDaLoja]); as demais lojas nao sao tocadas.
     */
    fun anotarPreco(itemId: Long, estabelecimentoId: Long, valor: BigDecimal?) {
        viewModelScope.launch {
            val item = listaRepositorio.item(itemId) ?: return@launch
            if (valor == null || valor.signum() <= 0) {
                limparPrecoDaLoja(itemId, estabelecimentoId)
                return@launch
            }
            val itemComProduto = estado.value.itens.firstOrNull { it.item.id == itemId }
            val aviso = precoRepositorio.registrarPrecoComAviso(
                item = item,
                produtoId = itemComProduto?.produto?.id ?: item.produtoId,
                estabelecimentoId = estabelecimentoId,
                preco = valor,
            )
            if (aviso != null) _mensagem.value = aviso
        }
    }

    /**
     * Remove **somente** o preco daquela loja. O produto continua na lista
     * mestra e os precos das outras lojas ficam intactos.
     */
    fun limparPrecoDaLoja(itemId: Long, estabelecimentoId: Long) {
        viewModelScope.launch {
            val anterior = estado.value.precos[ChaveDePreco(itemId, estabelecimentoId)]
            precoRepositorio.removerPreco(itemId, estabelecimentoId)
            val nomeDaLoja = estado.value.estabelecimentos.firstOrNull { it.id == estabelecimentoId }?.nome.orEmpty()
            if (anterior != null) {
                _desfazer.value = AcaoDesfazivel(
                    descricao = "Preço apagado em $nomeDaLoja",
                    precos = listOf(anterior),
                    tipo = AcaoDesfazivel.Tipo.PRECO_REMOVIDO,
                )
            }
        }
    }

    /** "Não tinha na loja": dado legitimo, diferente de "ainda não pesquisei". */
    fun marcarIndisponivel(itemId: Long, estabelecimentoId: Long) {
        viewModelScope.launch {
            precoRepositorio.marcarIndisponivel(itemId, estabelecimentoId)
            // Mesma torrada do ListaViewModel.marcarIndisponivel (a acao e a
            // mesma vinda da folha da celula ou da folha do item da lista).
            val nome = estado.value.itens
                .firstOrNull { it.item.id == itemId }?.produto?.nome ?: "Item"
            _mensagem.value = "\"$nome\" marcado como indisponível nessa loja."
        }
    }

    /**
     * Tira o produto da **lista mestra** - some de todas as lojas de uma vez.
     *
     * Acao separada e explicita justamente porque o efeito e amplo: a tela deve
     * confirmar antes de chamar, e o "Desfazer" devolve o item com todos os
     * precos que ele tinha.
     */
    fun removerItemDaListaMestra(itemId: Long) {
        viewModelScope.launch {
            val item = listaRepositorio.item(itemId) ?: return@launch
            val precosDoItem = precoRepositorio.precosDoItem(itemId)
            val nome = estado.value.itens.firstOrNull { it.item.id == itemId }?.produto?.nome ?: "Item"
            listaRepositorio.removerItem(item)
            _desfazer.value = AcaoDesfazivel(
                descricao = "$nome removido da lista (todas as lojas)",
                item = item,
                precos = precosDoItem,
                tipo = AcaoDesfazivel.Tipo.ITEM_REMOVIDO,
            )
        }
    }

    /** Refaz a ultima acao destrutiva (preco apagado ou item removido). */
    fun desfazerUltimaAcao() {
        val acao = _desfazer.value ?: return
        viewModelScope.launch {
            when (acao.tipo) {
                AcaoDesfazivel.Tipo.PRECO_REMOVIDO -> {
                    acao.precos.forEach { preco ->
                        val item = listaRepositorio.item(preco.itemDaListaId) ?: return@forEach
                        precoRepositorio.registrarPreco(
                            item = item,
                            produtoId = item.produtoId,
                            estabelecimentoId = preco.estabelecimentoId,
                            preco = preco.preco,
                            disponivel = preco.disponivel,
                        )
                    }
                }
                AcaoDesfazivel.Tipo.ITEM_REMOVIDO -> {
                    val item = acao.item ?: return@launch
                    val novoId = listaRepositorio.adicionarItem(item.copy(id = 0))
                    val reinserido = listaRepositorio.item(novoId) ?: return@launch
                    acao.precos.forEach { preco ->
                        precoRepositorio.registrarPreco(
                            item = reinserido,
                            produtoId = reinserido.produtoId,
                            estabelecimentoId = preco.estabelecimentoId,
                            preco = preco.preco,
                            disponivel = preco.disponivel,
                        )
                    }
                }
            }
            _desfazer.value = null
        }
    }

    fun descartarDesfazer() {
        _desfazer.value = null
    }

    /**
     * Exporta a matriz de comparacao atual em CSV (uma linha por produto, uma
     * coluna por loja + "melhor loja") pelo SAF, sem permissao de armazenamento
     * - mesma rota do backup e do CSV de precos. So ha conteudo quando a
     * matriz esta montada (2+ lojas e itens na lista); sem isso, a mensagem
     * avisa e nada e escrito.
     */
    fun exportarMatrizCsv(contexto: Context, destino: Uri) {
        viewModelScope.launch {
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    val matriz = estado.value.matriz
                        ?: error("matriz indisponivel (precisa de 2 lojas e itens)")
                    val csv = ExportadorCsv.matrizCsv(
                        nomesDasLojas = matriz.estabelecimentos.map { it.nome },
                        linhas = matriz.linhas.map { linha ->
                            ExportadorCsv.LinhaDaMatrizCsv(
                                descricao = linha.descricao,
                                detalhe = linha.detalhe,
                                precoPorLoja = matriz.estabelecimentos.map { loja ->
                                    linha.celulas.firstOrNull { celula -> celula.estabelecimentoId == loja.id }
                                        ?.takeIf { celula -> celula.registrado && celula.disponivel }
                                        ?.preco
                                },
                                indiceDoMelhor = linha.melhorEstabelecimentoId
                                    ?.let { melhor -> matriz.estabelecimentos.indexOfFirst { it.id == melhor } }
                                    ?.takeIf { indice -> indice >= 0 },
                            )
                        },
                    )
                    contexto.contentResolver.openOutputStream(destino)?.use { saida ->
                        saida.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("destino invalido")
                }
            }
            _mensagem.value = if (resultado.isSuccess) {
                "Matriz exportada: uma linha por produto, uma coluna por loja."
            } else {
                "Não consegui exportar a matriz agora."
            }
        }
    }

    fun mensagemExibida() {
        _mensagem.update { null }
    }

    // --- finalizacao da compra -------------------------------------------------------

    /**
     * Previa do que sera gravado no historico, sem gravar nada.
     *
     * Conta so os itens **marcados como comprados**; se nenhum foi marcado,
     * considera a lista inteira (o usuario que compra tudo de uma vez nao
     * precisa marcar item por item).
     *
     * @param escolhaPorItem loja escolhida item a item - normalmente a da
     * compra mista otima; vazio significa "sempre o menor preco".
     */
    fun previaDaCompra(escolhaPorItem: Map<Long, Long> = emptyMap()): CalculadoraDeCompra.ResultadoDaCompra {
        val atual = estado.value
        val marcados = atual.itens.filter { it.item.comprado }
        val considerados = marcados.ifEmpty { atual.itens }
        return CalculadoraDeCompra.calcular(
            itens = considerados,
            precos = atual.precos.values.toList(),
            estabelecimentos = atual.estabelecimentos,
            categorias = atual.categorias,
            escolhaPorItem = escolhaPorItem,
        )
    }

    /**
     * Fecha a compra: grava no historico, marca a lista como finalizada e
     * devolve o id do registro para a tela navegar ao resumo.
     *
     * Os precos continuam na lista - reabrir a lista nao perde nada.
     *
     * Guard contra registro duplicado: se a lista ja esta finalizada (tela
     * aberta de antes, menu acessado de novo, dois toques rapidos), NADA e
     * gravado no historico, a flag nao e regravada, [aoConcluir] nao e chamado
     * e a mensagem explica o por que.
     */
    fun finalizarCompra(escolhaPorItem: Map<Long, Long> = emptyMap(), aoConcluir: (Long) -> Unit) {
        val atual = estado.value
        val lista = atual.lista ?: return
        if (lista.finalizada) {
            _mensagem.value = "Esta conta já foi finalizada."
            return
        }
        viewModelScope.launch {
            val resultado = previaDaCompra(escolhaPorItem)
            val id = historicoRepositorio.registrar(
                listaId = lista.id,
                nomeLista = lista.nome,
                resultado = resultado,
            )
            listaRepositorio.marcarFinalizada(lista.id)
            _mensagem.value = "Compra registrada: ${Formatadores.moeda(resultado.totalPago)}."
            aoConcluir(id)
        }
    }

    /**
     * Copia os precos de uma loja para outra (util em rede com duas unidades,
     * ou para reproduzir a ultima pesquisa sem digitar tudo de novo).
     *
     * So copia precos validos (`disponivel`); o que a origem nao tem, o destino
     * continua sem - "ainda nao pesquisei" nao vira "preco zero".
     */
    fun copiarPrecosEntreLojas(deId: Long, paraId: Long) {
        viewModelScope.launch {
            val atual = estado.value
            val nomeDe = atual.estabelecimentos.firstOrNull { it.id == deId }?.nome.orEmpty()
            val nomePara = atual.estabelecimentos.firstOrNull { it.id == paraId }?.nome.orEmpty()
            val atuais = atual.precos.values.filter { it.estabelecimentoId == deId && it.disponivel }
            atuais.forEach { preco ->
                val item = listaRepositorio.item(preco.itemDaListaId) ?: return@forEach
                precoRepositorio.registrarPreco(
                    item = item,
                    produtoId = item.produtoId,
                    estabelecimentoId = paraId,
                    preco = preco.preco,
                )
            }
            _mensagem.value = if (atuais.isEmpty()) {
                "Nenhum preço para copiar de $nomeDe."
            } else {
                "Preços copiados de $nomeDe para $nomePara — revise antes de comparar."
            }
        }
    }
}

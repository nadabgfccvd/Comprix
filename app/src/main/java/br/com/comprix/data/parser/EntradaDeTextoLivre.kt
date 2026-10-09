package br.com.comprix.data.parser

import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.parser.ClasseDeItem
import br.com.comprix.domain.parser.FonteDaEntrada
import br.com.comprix.domain.parser.ItemInterpretado
import br.com.comprix.domain.parser.MemoriaDoParser
import br.com.comprix.domain.parser.OpcaoDeSugestao
import br.com.comprix.domain.parser.ParserDeLinhaDeCompra
import br.com.comprix.domain.parser.SugestoesDoParser
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Ponte entre o parser puro ([ParserDeLinhaDeCompra], em `domain/`) e o banco.
 *
 * O parser nao conhece Room, Context nem repositorio - de proposito. Quem
 * carrega a memoria local, resolve a categoria no catalogo do usuario e grava o
 * item na lista e esta classe, que vive na camada de dados.
 *
 * Tres responsabilidades, nessa ordem:
 * 1. [interpretar] - le o que foi digitado (varias linhas, varios itens por
 *    linha) ja com o aprendizado do aparelho aplicado;
 * 2. [responder] - registra a escolha do usuario na sugestao inline;
 * 3. [adicionar] - faz o mapeamento da Secao 3.2 do documento do parser
 *    (`nomeItem` -> [Produto], quantidade/unidade -> [ItemDaLista]) e grava.
 *
 * Nada aqui faz chamada de rede: o parser e tabela + regra, 100 % offline.
 */
class EntradaDeTextoLivre(
    private val catalogo: CatalogoRepositorio,
    private val listas: ListaRepositorio,
) {

    /**
     * Interpreta o texto digitado.
     *
     * Quebra de linha separa entradas independentes (colar uma lista inteira
     * funciona); dentro de cada linha o [br.com.comprix.domain.parser.DivisorDeLinha]
     * cuida da divisao em multiplos itens.
     */
    suspend fun interpretar(
        texto: String,
        fonte: FonteDaEntrada = FonteDaEntrada.TEXTO,
    ): List<ItemInterpretado> {
        if (texto.isBlank()) return emptyList()
        val parser = ParserDeLinhaDeCompra(memoria = catalogo.memoriaDoParser())
        return texto.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { linha -> parser.interpretar(linha, fonte).asSequence() }
            .toList()
    }

    /** Interpreta uma unica linha reaproveitando a memoria ja carregada. */
    suspend fun interpretarLinha(
        linha: String,
        fonte: FonteDaEntrada = FonteDaEntrada.TEXTO,
    ): ItemInterpretado? = interpretar(linha, fonte).firstOrNull()

    /**
     * Registra a resposta do usuario a uma sugestao e devolve o item ajustado.
     *
     * O lado durável da resposta (o "nao pergunte de novo") vai para a tabela
     * `decisoes_parser` via [CatalogoRepositorio.lembrarDecisaoDoParser].
     */
    suspend fun responder(item: ItemInterpretado, opcao: OpcaoDeSugestao): ItemInterpretado {
        val sugestao = item.sugestao ?: return item
        val resposta = SugestoesDoParser.responder(item, opcao, MemoriaDoParser.VAZIA)
        catalogo.lembrarDecisaoDoParser(
            termo = sugestao.termo,
            valorConfirmado = if (opcao.manterComoEsta) null else resposta.item.quantidade,
        )
        return resposta.item
    }

    /** O usuario fechou a sugestao sem escolher: nada e aprendido. */
    fun ignorar(item: ItemInterpretado): ItemInterpretado = SugestoesDoParser.ignorar(item)

    /**
     * Grava o item interpretado na lista (mapeamento da Secao 3.2).
     *
     * `confianca`, `motivo`, `outrasInterpretacoes`, `sugestao` e `alerta` nao
     * sao persistidos: sao informacao de tela, nao de dominio.
     */
    suspend fun adicionar(listaId: Long, item: ItemInterpretado): Long {
        val produto = resolverProduto(item)
        return listas.adicionarItem(
            ItemDaLista(
                listaId = listaId,
                produtoId = produto.id,
                quantidade = item.quantidade ?: BigDecimal.ONE,
                unidade = item.unidade ?: produto.unidadePadrao,
                pesoOuVolume = item.pesoOuVolume,
                ehKit = item.ehKit,
                itensPorKit = item.itensPorKit,
                modoComparacao = modoDeComparacao(item),
                observacao = observacaoDe(item),
            ),
        )
    }

    /** Adiciona varios de uma vez; devolve quantos entraram. */
    suspend fun adicionarTodos(listaId: Long, itens: List<ItemInterpretado>): Int {
        var gravados = 0
        itens.forEach { item ->
            if (item.nomeItem.isNotBlank()) {
                adicionar(listaId, item)
                gravados++
            }
        }
        return gravados
    }

    /**
     * Sugestoes de digitacao para o campo de adicao rapida: o que o usuario ja
     * tem no catalogo local primeiro, depois o acervo de fabrica. E apenas leitura,
     * mas suspend porque o Room consulta o banco.
     */
    suspend fun sugestoesDeDigitacao(termo: String, limite: Int = 6): List<String> =
        catalogo.sugestoesDeDigitacao(termo, limite)

    // --- apoio -------------------------------------------------------------------------

    /**
     * Encontra (ou cria) o produto do item.
     *
     * A categoria vem primeiro do lexico do parser - que so devolve chaves das
     * 14 categorias da Secao 4.4 - e, quando ele nao sabe, cai no
     * categorizador hibrido do app, que ainda consulta a memoria do usuario.
     */
    private suspend fun resolverProduto(item: ItemInterpretado): Produto {
        val nome = TextoUtil.capitalizarTitulo(item.nomeItem.trim())
        val categoriaId = resolverCategoria(item)
        val produto = catalogo.encontrarOuCriar(nome = nome, categoriaId = categoriaId)
        val unidadePadrao = item.unidade ?: produto.unidadePadrao
        return if (produto.unidadePadrao != unidadePadrao && produto.id > 0 && item.unidade != null) {
            catalogo.salvarProduto(produto.copy(unidadePadrao = unidadePadrao))
        } else {
            produto
        }
    }

    private suspend fun resolverCategoria(item: ItemInterpretado): Long {
        val chave = item.categoriaSugerida
        if (!chave.isNullOrBlank()) {
            catalogo.listarCategorias().firstOrNull { it.chave == chave }?.let { return it.id }
        }
        return catalogo.sugerirCategoria(item.nomeItem).categoria.id
    }

    /**
     * Item pesavel ja nasce comparando por peso/volume - e o que o usuario quer
     * ver quando escreveu "2 kg de carne".
     */
    private fun modoDeComparacao(item: ItemInterpretado): ModoComparacaoUnidade = when {
        item.classeDoItem == ClasseDeItem.PESAVEL -> ModoComparacaoUnidade.POR_PESO
        item.unidade in UNIDADES_DE_PESO -> ModoComparacaoUnidade.POR_PESO
        else -> ModoComparacaoUnidade.POR_UNIDADE
    }

    /**
     * A observacao guarda o que sobrou do casamento (marca, sabor, "caixa") e,
     * quando o parser nao conseguiu fixar a quantidade, o motivo em linguagem
     * de gente - para o item nunca chegar mudo na lista.
     */
    private fun observacaoDe(item: ItemInterpretado): String? {
        val partes = listOfNotNull(
            item.observacao?.takeIf { it.isNotBlank() },
            item.motivo?.takeIf { item.quantidade == null && it.isNotBlank() },
        )
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private companion object {
        val UNIDADES_DE_PESO = setOf(
            Unidade.QUILO,
            Unidade.GRAMA,
            Unidade.LITRO,
            Unidade.MILILITRO,
        )
    }
}

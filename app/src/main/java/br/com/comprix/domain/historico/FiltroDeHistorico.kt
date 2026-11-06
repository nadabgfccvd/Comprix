package br.com.comprix.domain.historico

import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.util.TextoUtil

/**
 * Logica pura do **filtro de lojas** do Historico - isolada da tela e do
 * ViewModel para ser testada direto na JVM (mesma receita do
 * `FiltroDaLixeira`).
 *
 * ## Por que rotulo, e nao id de loja
 * A compra guarda [CompraFinalizada.estabelecimentoPrincipalId], mas o que a
 * pessoa reconhece no cartao e o rotulo escrito:
 * "Mercado do Bairro" - ou "Mercado do Bairro + 2 loja(s)", quando a compra
 * aconteceu em mais de um lugar (o rotulo vem de
 * `CalculadoraDeCompra.ResultadoDaCompra.descricaoEstabelecimento`). O filtro
 * fala exatamente a lingua dos cartoes: tocar na pastilha "Mercado do Bairro"
 * mostra as compras cujo cartao diz "Mercado do Bairro". Compras de varias
 * lojas ficam agrupadas sob o rotulo combinado - prevevel e sem surpresa.
 *
 * ## Combinacao com a busca
 * Loja e busca se somam (E), nao competem: "hortifruti" + pastilha
 * "Mercado do Bairro" mostra so as compras desse mercado que casam com a
 * palavra. As analiticas e os numeros do topo seguem sobre TODAS as compras -
 * o filtro afina a lista, nunca reescreve a estatistica.
 */
object FiltroDeHistorico {

    /**
     * Rotulo usado quando a compra ficou sem estabelecimento ("Sem
     * estabelecimento" na Calculadora; a defesa aqui cobre tambem dados
     * antigos importados de backup, que podem chegar com texto vazio).
     */
    const val ROTULO_SEM_LOJA = "Não informado"

    /** Uma pastilha da linha de lojas: rotulo exibido e quantas compras tem. */
    data class LojaDoHistorico(val rotulo: String, val quantidade: Int)

    /** Rotulo de filtro da compra - o mesmo texto que aparece no cartao. */
    private fun rotuloDa(compra: CompraFinalizada): String =
        compra.descricaoEstabelecimento.ifBlank { ROTULO_SEM_LOJA }

    /**
     * Lojas distintas das compras do periodo, mais usadas no topo: ordenadas
     * por quantidade de compras (decrescente) e, em caso de empate, por ordem
     * alfabetica - lista estavel que nao danca enquanto a pessoa olha.
     */
    fun lojas(compras: List<CompraFinalizada>): List<LojaDoHistorico> =
        compras
            .groupBy { rotuloDa(it) }
            .map { (rotulo, doRotulo) -> LojaDoHistorico(rotulo, doRotulo.size) }
            .sortedWith(compareByDescending<LojaDoHistorico> { it.quantidade }.thenBy { it.rotulo })

    /** Verdadeiro quando [compra] pertence a loja selecionada (`null` = todas). */
    fun correspondeLoja(compra: CompraFinalizada, loja: String?): Boolean =
        loja == null || rotuloDa(compra) == loja

    /**
     * Compras visiveis na secao "Compras realizadas": intersecao do filtro de
     * loja ([correspondeLoja]) com a busca amigavel
     * ([TextoUtil.correspondeBusca] sobre nome da lista e lojas - o mesmo
     * criterio da Lixeira e do catalogo). Consulta em branco e loja nula
     * passam tudo (filtros desligados).
     *
     * ## Sinonimos (expansao da consulta)
     * [candidatos], quando presente, recebe a consulta e devolve termos
     * alternativos - na pratica, os sinonimos do acervo de fabrica via
     * `CatalogoRepositorio.sinonimosDe` ("mexerica" -> "tangerina,
     * bergamota"). A busca passa se a consulta direta OU QUALQUER alternativa
     * casar: "tangerina" encontra a lista "Mexerica para o suco" e
     * vice-versa - a resolucao do acervo e bidirecional (nome oficial e
     * apelido caem no mesmo item).
     *
     * Duas regras deliberadas, para o filtro ficar prevevel:
     * 1. **Expansao pela consulta inteira, nao por palavra solta** - "suco de
     *    mexerica" so se expande se a FRASE inteira resolver num item do
     *    acervo; nao viramos cada palavra da consulta numa busca separada
     *    (o resultado cresceria em silencio e a intersecao com a loja
     *    perderia o sentido de "afinar").
     * 2. **`null` = comportamento de antes** - sem candidatos, o filtro e
     *    exatamente o que era (regressao zero para quem nao injeta o acervo).
     */
    fun comprasVisiveis(
        compras: List<CompraFinalizada>,
        consulta: String,
        loja: String?,
        candidatos: ((String) -> List<String>)? = null,
    ): List<CompraFinalizada> {
        // Alternativas calculadas UMA vez por chamada (a funcao de sinonimos
        // e pura e barata, mas o filtro roda a cada emissao de estado - nao
        // a cada compra da lista).
        val alternativas = if (candidatos == null || consulta.isBlank()) {
            emptyList()
        } else {
            candidatos(consulta).filter { it.isNotBlank() }
        }
        return compras.filter { compra ->
            correspondeLoja(compra, loja) && casaNaBusca(compra, consulta, alternativas)
        }
    }

    /** Busca direta OU qualquer alternativa de sinonimo casa no mesmo alvo. */
    private fun casaNaBusca(
        compra: CompraFinalizada,
        consulta: String,
        alternativas: List<String>,
    ): Boolean {
        val alvo = compra.nomeLista + " " + compra.descricaoEstabelecimento
        if (TextoUtil.correspondeBusca(alvo, consulta)) return true
        return alternativas.any { TextoUtil.correspondeBusca(alvo, it) }
    }
}

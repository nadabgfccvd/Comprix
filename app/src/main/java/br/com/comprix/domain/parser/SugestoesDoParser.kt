package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Sugestao **inline e nao bloqueante** - os gatilhos G1 a G8 da Secao 9.
 *
 * Interrupcao e pior que parser imperfeito: quem esta no corredor do mercado
 * digitando 60 itens em 3 minutos nao pode receber um dialogo. Entao:
 *
 * - no maximo **1 sugestao por item**;
 * - no maximo **2 opcoes** (com 3 ou mais, a tela mostra o campo editavel ja
 *   preenchido - [ItemInterpretado.outrasInterpretacoes]);
 * - **nunca modal**: a sugestao e um texto secundario na propria linha;
 * - ignorar e uma resposta valida: o item fica como esta e a sugestao some.
 *
 * | Gatilho | Situacao | Acao |
 * |---|---|---|
 * | G1 | apelido de animal em texto livre | sugere inline |
 * | G2 | apelido nao animal com confianca baixa | sugere inline |
 * | G3 | `meia` isolado | sugere inline (6? 0,5?) |
 * | G4 | `2 e 50` e analogos | sugere inline |
 * | G5 | token incompativel com a classe (`duzia de carne`) | **alerta**, nao sugere |
 * | G6 | mais de um candidato a valor | sugere inline |
 * | G7 | confianca alta | **nunca** pergunta |
 * | G8 | item fora do lexico | nao pergunta: cria em "Outros" e aprende |
 */
object SugestoesDoParser {

    /** Rotulo da acao que mantem o item como o parser entendeu. */
    const val ROTULO_MANTER = "Manter como está"

    /**
     * Decide o gatilho e devolve o item ja com (ou sem) sugestao.
     *
     * A ordem importa: G5 e G7 silenciam os demais.
     */
    fun aplicar(
        item: ItemInterpretado,
        tokenPendente: TokenDeQuantidade?,
        itemDoLexico: ItemDoLexico?,
        memoria: MemoriaDoParser,
        fonte: FonteDaEntrada,
    ): ItemInterpretado {
        // OCR nunca pergunta: o texto nao foi digitado por ninguem que esteja
        // olhando para a tela naquele instante.
        if (fonte == FonteDaEntrada.OCR) return item
        if (item.sugestao != null) return item

        // G5 - incompatibilidade de classe ja virou alerta; nao se pergunta por cima.
        if (item.alerta != null) return item

        // G1 / G2 - apelido popular pendente.
        if (tokenPendente != null) {
            val termo = tokenPendente.canonico
            if (memoria.jaRejeitou(termo)) return item
            memoria.valorConfirmado(termo)?.let { confirmado ->
                return item.copy(
                    quantidade = confirmado,
                    unidade = item.unidade ?: Unidade.UNIDADE,
                    confianca = Confianca.ALTA,
                    motivo = "valor aprendido de uma confirmacao anterior sua",
                )
            }
            return item.copy(sugestao = sugestaoDeApelido(tokenPendente, item))
        }

        // G4 - "2 e 50": casa decimal ou duas coisas?
        if (item.confianca == Confianca.BAIXA && item.motivo?.contains("casa decimal") == true) {
            if (memoria.jaRejeitou(TERMO_DECIMAL_FALADO)) return item
            return item.copy(sugestao = sugestaoDeDecimalFalado(item))
        }

        // G3 - "meia" sozinho, sem duzia e sem embalagem.
        if (meiaIsolada(item)) {
            if (memoria.jaRejeitou(TERMO_MEIA)) return item
            return item.copy(sugestao = sugestaoDeMeia(item))
        }

        // G6 - mais de um candidato a valor.
        if (item.outrasInterpretacoes.isNotEmpty() && item.confianca != Confianca.ALTA) {
            return item.copy(sugestao = sugestaoDeConflito(item))
        }

        // G8 - fora do lexico: nao pergunta nada, cria em "Outros" e aprende.
        if (itemDoLexico == null && item.categoriaSugerida == CHAVE_OUTROS) {
            return item.copy(
                motivo = item.motivo
                    ?: "item novo: criado em Outros, a categoria que voce escolher fica aprendida",
            )
        }

        // G7 - confianca alta: aplica direto, sem perguntar.
        return item
    }

    // =================================================================================
    // Montagem das sugestoes
    // =================================================================================

    private fun sugestaoDeApelido(token: TokenDeQuantidade, item: ItemInterpretado): SugestaoNaoBloqueante {
        val gatilho = if (token.origemRepertorio == OrigemRepertorio.ANIMAL) {
            GatilhoDeSugestao.G1
        } else {
            GatilhoDeSugestao.G2
        }
        val valor = token.valor.stripTrailingZeros().toPlainString()
        return SugestaoNaoBloqueante(
            gatilho = gatilho,
            pergunta = "\"${token.canonico}\": quis dizer $valor?",
            opcoes = listOf(
                OpcaoDeSugestao(
                    rotulo = "Usar $valor",
                    quantidade = token.valor,
                    unidade = token.unidade,
                ),
                OpcaoDeSugestao(rotulo = ROTULO_MANTER, manterComoEsta = true),
            ),
            termo = token.canonico,
        )
    }

    private fun sugestaoDeDecimalFalado(item: ItemInterpretado): SugestaoNaoBloqueante {
        val comoDecimal = item.quantidade ?: BigDecimal.ONE
        val alternativa = item.outrasInterpretacoes.firstOrNull()?.quantidade
        val unidade = item.unidade ?: Unidade.UNIDADE
        return SugestaoNaoBloqueante(
            gatilho = GatilhoDeSugestao.G4,
            pergunta = "${formatar(comoDecimal)} ${unidade.sigla}?",
            opcoes = listOfNotNull(
                OpcaoDeSugestao(
                    rotulo = "Sim, ${formatar(comoDecimal)} ${unidade.sigla}",
                    quantidade = comoDecimal,
                    unidade = unidade,
                ),
                alternativa?.let {
                    OpcaoDeSugestao(
                        rotulo = "Não, ${formatar(it)} ${unidade.sigla}",
                        quantidade = it,
                        unidade = unidade,
                    )
                } ?: OpcaoDeSugestao(rotulo = ROTULO_MANTER, manterComoEsta = true),
            ),
            termo = TERMO_DECIMAL_FALADO,
        )
    }

    private fun sugestaoDeMeia(item: ItemInterpretado): SugestaoNaoBloqueante =
        SugestaoNaoBloqueante(
            gatilho = GatilhoDeSugestao.G3,
            pergunta = "\"meia\": 6 unidades ou metade?",
            opcoes = listOf(
                OpcaoDeSugestao(rotulo = "6 unidades", quantidade = BigDecimal(6), unidade = Unidade.UNIDADE),
                OpcaoDeSugestao(
                    rotulo = "Metade",
                    quantidade = BigDecimal("0.5"),
                    unidade = item.unidade ?: Unidade.QUILO,
                ),
            ),
            termo = TERMO_MEIA,
        )

    private fun sugestaoDeConflito(item: ItemInterpretado): SugestaoNaoBloqueante {
        val outra = item.outrasInterpretacoes.first()
        val unidade = item.unidade ?: Unidade.UNIDADE
        return SugestaoNaoBloqueante(
            gatilho = GatilhoDeSugestao.G6,
            pergunta = "Mais de uma leitura possível para \"${item.textoOriginal}\"",
            opcoes = listOf(
                OpcaoDeSugestao(
                    rotulo = "${formatar(item.quantidade ?: BigDecimal.ONE)} ${unidade.sigla}",
                    quantidade = item.quantidade,
                    unidade = unidade,
                ),
                OpcaoDeSugestao(
                    rotulo = outra.descricao,
                    quantidade = outra.quantidade,
                    unidade = outra.unidade ?: unidade,
                ),
            ),
            termo = TextoUtil.normalizar(item.textoOriginal),
        )
    }

    // =================================================================================
    // Respostas do usuario - alimentam o aprendizado local (Secao 3.5)
    // =================================================================================

    data class Resposta(
        val item: ItemInterpretado,
        val memoria: MemoriaDoParser,
    )

    /**
     * Aplica a escolha feita na linha.
     *
     * - `aplicar` grava `confirmadoPorUsuario` e usa o valor;
     * - `manter como esta` grava `rejeitado` e **nao volta a sugerir** o termo.
     *
     * Em ambos os casos a sugestao some: nada fica pendurado na tela.
     */
    fun responder(
        item: ItemInterpretado,
        opcao: OpcaoDeSugestao,
        memoria: MemoriaDoParser,
    ): Resposta {
        val sugestao = item.sugestao ?: return Resposta(item, memoria)
        val termo = sugestao.termo.lowercase()

        if (opcao.manterComoEsta) {
            return Resposta(
                item = item.copy(sugestao = null),
                memoria = memoria.copy(rejeitados = memoria.rejeitados + termo),
            )
        }

        val quantidade = opcao.quantidade ?: item.quantidade
        return Resposta(
            item = item.copy(
                quantidade = quantidade,
                unidade = opcao.unidade ?: item.unidade,
                itensPorKit = opcao.itensPorKit ?: item.itensPorKit,
                ehKit = (opcao.itensPorKit ?: item.itensPorKit ?: 1) > 1,
                confianca = Confianca.ALTA,
                motivo = null,
                sugestao = null,
                outrasInterpretacoes = emptyList(),
            ),
            memoria = if (quantidade != null) {
                memoria.copy(confirmados = memoria.confirmados + (termo to quantidade))
            } else {
                memoria
            },
        )
    }

    /** O usuario ignorou: o item fica como esta e a sugestao desaparece. */
    fun ignorar(item: ItemInterpretado): ItemInterpretado = item.copy(sugestao = null)

    /**
     * Segunda duvida no mesmo item: aplica a melhor hipotese, marca confianca
     * baixa e **nao** pergunta de novo (Secao 9.3).
     */
    fun segundaDuvida(item: ItemInterpretado, melhorHipotese: BigDecimal?): ItemInterpretado =
        item.copy(
            quantidade = melhorHipotese ?: item.quantidade,
            confianca = Confianca.BAIXA,
            motivo = "segunda duvida no mesmo item: aplicada a melhor hipotese sem nova pergunta",
            sugestao = null,
        )

    // =================================================================================
    // Deteccao auxiliar
    // =================================================================================

    /** "meia" sozinha, sem `duzia` e sem embalagem por perto. */
    private fun meiaIsolada(item: ItemInterpretado): Boolean {
        val texto = TextoUtil.normalizar(item.textoOriginal)
        val temMeia = TextoUtil.contemPalavra(texto, "meia") || TextoUtil.contemPalavra(texto, "meio")
        if (!temMeia) return false
        if (TextoUtil.contemPalavra(texto, "duzia")) return false
        if (FalsosPositivos.guardaAtiva(texto) != null) return false
        val temEmbalagem = TabelaDeQuantidades.embalagens.any { token ->
            (token.variantes + token.canonico).any { TextoUtil.contemPalavra(texto, TextoUtil.normalizar(it)) }
        }
        if (temEmbalagem) return false
        // "meio quilo", "meia garrafa de 2 l": unidade explicita resolve a duvida.
        return item.unidade == null || item.unidade == Unidade.UNIDADE
    }

    private fun formatar(valor: BigDecimal): String =
        valor.stripTrailingZeros().toPlainString().replace('.', ',')

    private const val CHAVE_OUTROS = "outros"
    private const val TERMO_MEIA = "meia"
    private const val TERMO_DECIMAL_FALADO = "numero e numero"
}

package br.com.comprix.domain.parser

import br.com.comprix.domain.categoria.DicionarioDeCategorias
import br.com.comprix.domain.modelo.Dimensao
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.unidade.ConversorDeUnidades
import br.com.comprix.util.Constantes
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parser de entrada em texto livre da linha de adicao rapida.
 *
 * Transforma `"2kg de carne e meia duzia de ovo"` em dois
 * [ItemInterpretado] prontos para virar `Produto` + `ItemDaLista`.
 *
 * ## Garantias
 * - **Nunca descarta a linha.** O que nao for entendido volta como item
 *   literal, com `quantidade = null` e um `motivo`.
 * - **Nunca inventa numero.** Sem valor documentado, o campo fica `null`.
 * - **Deterministico e offline.** Tabelas em memoria, nenhuma rede, nenhum
 *   modelo generativo, nenhuma aleatoriedade.
 * - **Nao bloqueia.** Duvida vira [SugestaoNaoBloqueante] inline, no maximo uma
 *   por item.
 *
 * ## Ordem de decisao
 * 1. [DivisorDeLinha] quebra a linha em segmentos (um por item);
 * 2. guardas de falso positivo e de quantidade indefinida;
 * 3. quantidade, na ordem P4 (mais comum) -> P2 -> P1/P3;
 * 4. nome do item e categoria pelo lexico (que e o dicionario da Secao 4.4);
 * 5. validacao semantica classe x unidade (alerta, nunca bloqueio);
 * 6. confianca e sugestao.
 *
 * @param memoria decisoes que o usuario ja tomou (aprendizado local da
 * Secao 4.4 - nao existe segundo mecanismo de aprendizado no app).
 */
class ParserDeLinhaDeCompra(
    private val memoria: MemoriaDoParser = MemoriaDoParser.VAZIA,
) : ParserDeTextoLivre {

    override fun interpretar(linha: String, fonte: FonteDaEntrada): List<ItemInterpretado> {
        if (linha.isBlank()) return emptyList()
        return DivisorDeLinha.dividir(linha).map { interpretarSegmento(it, fonte) }
    }

    // =================================================================================
    // Um segmento = um item
    // =================================================================================

    private fun interpretarSegmento(segmento: Segmento, fonte: FonteDaEntrada): ItemInterpretado {
        val original = segmento.texto.trim()

        // 1. Parenteses sao anotacao explicita do usuario: "ovos (caipira)".
        val parenteses = REGEX_PARENTESES.find(original)?.groupValues?.get(1)?.trim()
        val semParenteses = REGEX_PARENTESES.replace(original, " ").trim()

        val leve = TextoUtil.normalizarLeve(semParenteses)
        val ilegivel = fonte == FonteDaEntrada.OCR && REGEX_ILEGIVEL.containsMatchIn(leve)

        val palavras = separarEmPalavras(leve)
        if (palavras.isEmpty()) {
            return itemLiteral(original, "nenhuma palavra reconhecivel na linha")
        }

        // 2. Preco solto no FIM do segmento ("uva 1kg 3,99"): sai das palavras
        //    ANTES da leitura de quantidade, senao o decimal final quebra o
        //    padrao P4 ("1kg 3,99" tem numero+unidade antes do preco). Cada
        //    segmento carrega o proprio preco - e o que faz a linha multi-item
        //    ("uva 1kg 3,99, limao 1kg 6,50") anotar os dois valores. OCR com
        //    caractere ilegivel nao extrai: o numero pode ter sido corrompido.
        var preco: BigDecimal? = null
        var palavrasUteis = palavras
        if (!ilegivel) {
            val precoNoFim = extrairPrecoDoFim(palavras)
            if (precoNoFim != null) {
                preco = precoNoFim.valor
                palavrasUteis = precoNoFim.restantes
            }
        }

        // 2b. Preco anexado no padrao P2 ("ovos, 12,50", "ovos, meia duzia, 12,50").
        //     A quantidadeAnexa cai aqui quando o usuario escreve o preco apos
        //     a virgula, e o divisor a prende ao item anterior. Um decimal com
        //     separador na anexa e DINHEIRO, nao quantidade - mesmo criterio do
        //     preco solto no fim (2), para o numero nao mudar de sentido conforme
        //     a posicao na frase. Inteiro solto ("ovos, 12") segue quantidade;
        //     a anexa com palavra de quantidade + decimal no fim ("meia duzia
        //     12,50") mantem a quantidade do prefixo e leva so o decimal.
        var quantidadeAnexaAtiva = segmento.quantidadeAnexa
        if (!ilegivel && preco == null && quantidadeAnexaAtiva != null) {
            val candidata = quantidadeAnexaAtiva.trim()
            val partesDaAnexa = candidata.split(' ').filter { it.isNotBlank() }
            val ultimoToken = partesDaAnexa.lastOrNull()
            val prefixo = partesDaAnexa.dropLast(1).joinToString(" ").trim()
            val anexaPuraDecimal = REGEX_PRECO_DECIMAL.matchEntire(candidata) != null
            val tokenDecimal = ultimoToken?.takeIf {
                REGEX_PRECO_DECIMAL.matchEntire(it) != null
            }
            val candidatoValido = anexaPuraDecimal ||
                (tokenDecimal != null &&
                    prefixo.isNotEmpty() &&
                    DivisorDeLinha.soTemQuantidade(prefixo))
            if (candidatoValido) {
                val valor = TextoUtil.paraDecimal(
                    if (anexaPuraDecimal) candidata else tokenDecimal!!,
                )
                if (valor != null && valor.signum() > 0 &&
                    valor <= BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)
                ) {
                    preco = valor
                    quantidadeAnexaAtiva = prefixo.ifBlank { null }
                }
            }
        }

        // 3. Quantidade indefinida tem prioridade sobre qualquer numero.
        val textoNormalizado = palavrasUteis.joinToString(" ")
        val indefinida = FalsosPositivos.indefinidaEm(textoNormalizado)
        if (indefinida != null) {
            val resto = limparPreposicoes(FalsosPositivos.removerIndefinida(textoNormalizado))
            val nome = if (resto.isBlank()) original else recortarOriginal(semParenteses, resto)
            return montarItem(
                nomeBruto = nome,
                original = original,
                quantidade = null,
                unidade = null,
                pesoOuVolume = null,
                ehKit = false,
                itensPorKit = null,
                observacao = parenteses,
                confianca = Confianca.MEDIA,
                motivo = "quantidade indefinida (\"$indefinida\") - nenhum numero foi inventado",
                alternativas = emptyList(),
                fonte = fonte,
                tokenPendente = null,
                embalagem = null,
                preco = preco,
            )
        }

        // 4. OCR com caractere ilegivel nao adivinha quantidade.
        if (ilegivel) {
            val nome = limparPreposicoes(palavras.filterNot { it.any(::ehRuido) }.joinToString(" "))
            return montarItem(
                nomeBruto = if (nome.isBlank()) original else nome,
                original = original,
                quantidade = null,
                unidade = null,
                pesoOuVolume = null,
                ehKit = false,
                itensPorKit = null,
                observacao = parenteses,
                confianca = Confianca.BAIXA,
                motivo = "texto de OCR com caractere ilegivel - quantidade nao foi deduzida",
                alternativas = emptyList(),
                fonte = fonte,
                tokenPendente = null,
                embalagem = null,
            )
        }

        // 5. Quantidade.
        val leitura = extrairQuantidade(palavrasUteis, quantidadeAnexaAtiva, fonte)
        val nomeNormalizado = limparPreposicoes(leitura.palavrasRestantes.joinToString(" "))
        val nomeBruto = if (nomeNormalizado.isBlank()) {
            original
        } else {
            recortarOriginal(semParenteses, nomeNormalizado)
        }

        return montarItem(
            nomeBruto = nomeBruto,
            original = original,
            quantidade = leitura.quantidade,
            unidade = leitura.unidade,
            pesoOuVolume = leitura.pesoOuVolume,
            ehKit = leitura.ehKit,
            itensPorKit = leitura.itensPorKit,
            observacao = parenteses,
            confianca = leitura.confianca,
            motivo = leitura.motivo,
            alternativas = leitura.alternativas,
            fonte = fonte,
            tokenPendente = leitura.tokenPendente,
            embalagem = leitura.embalagem,
            numeroExplicito = leitura.numeroExplicito,
            nomeLiteral = leitura.nomeLiteral,
            preco = preco,
        )
    }

    // =================================================================================
    // Montagem final: lexico, categoria, validacao semantica, sugestao
    // =================================================================================

    private fun montarItem(
        nomeBruto: String,
        original: String,
        quantidade: BigDecimal?,
        unidade: Unidade?,
        pesoOuVolume: BigDecimal?,
        ehKit: Boolean,
        itensPorKit: Int?,
        observacao: String?,
        confianca: Confianca,
        motivo: String?,
        alternativas: List<InterpretacaoAlternativa>,
        fonte: FonteDaEntrada,
        tokenPendente: TokenDeQuantidade?,
        embalagem: String?,
        numeroExplicito: Boolean = false,
        nomeLiteral: Boolean = false,
        preco: BigDecimal? = null,
    ): ItemInterpretado {
        val doLexico = LexicoDeItens.encontrarNoTexto(nomeBruto)

        // Nome: o trecho catalogado vira nome; o resto vira observacao - MAS
        // somente quando a sobra e complemento (embalagem, "sem gordura" ou
        // numero residual). Sobra com palavra propria ("de peito", "natural")
        // preserva o nome digitado inteiro: encolher para o catalogo trocaria
        // o produto ("maça de peito" nunca pode virar so "Maça").
        val nomeFinal: String
        var observacaoFinal = observacao
        val variantesCasadas = if (doLexico != null && !nomeLiteral) {
            val normalizado = TextoUtil.normalizar(nomeBruto)
            doLexico.variantesNormalizadas.firstOrNull { TextoUtil.contemPalavra(normalizado, it) }
        } else {
            null
        }
        if (variantesCasadas != null) {
            val normalizado = TextoUtil.normalizar(nomeBruto)
            val palavrasSobra = normalizado
                .replace(Regex("(^|\\s)${Regex.escape(variantesCasadas)}(\\s|$)"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .split(' ')
                .filter { it.isNotBlank() }
            if (sobraEhComplemento(palavrasSobra)) {
                // O trecho catalogado vira o nome; o que sobra vira observacao
                // ("carne sem gordura" -> nome "carne", observacao "sem gordura").
                val sobra = limparPreposicoes(palavrasSobra.joinToString(" "))
                nomeFinal = TextoUtil.capitalizarTitulo(recortarOriginal(nomeBruto, variantesCasadas))
                if (sobra.isNotBlank()) {
                    observacaoFinal = listOfNotNull(observacaoFinal, sobra).joinToString("; ")
                }
            } else {
                // O catalogo so nomeia o item quando o texto digitado,
                // normalizado, e igual ao trecho casado; palavras alem do
                // casamento ficam no nome, nao na observacao.
                nomeFinal = TextoUtil.capitalizarTitulo(nomeBruto)
            }
        } else {
            nomeFinal = TextoUtil.capitalizarTitulo(nomeBruto)
        }

        // Unidade: a declarada vence. Sem unidade declarada:
        // - contagem digitada ("3 macas") vira `un`, porque o numero conta pecas;
        // - item pesavel e o "1 implicito" usam a unidade padrao do lexico
        //   ("carne" -> 1 kg, "leite" -> 1 L).
        val inteiro = quantidade != null && quantidade.stripTrailingZeros().scale() <= 0
        val unidadeFinal = unidade ?: when {
            quantidade == null -> null
            // Numero INTEIRO sem unidade conta pecas: "tomate 2" = 2 unidades,
            // nao 2 kg. Quem quer peso escreve a unidade ("tomate 2kg").
            numeroExplicito && inteiro -> Unidade.UNIDADE
            // Numero QUEBRADO sem unidade so faz sentido como peso/volume:
            // ninguem compra 2,5 unidades de carne - usa a unidade do lexico.
            doLexico == null -> Unidade.UNIDADE
            else -> doLexico.unidadePadrao
        }
        val quantidadeFinal = quantidade

        // Categoria: lexico primeiro, dicionario depois (mesmo indice - Secao 4.4).
        val categoria = doLexico?.categoria ?: DicionarioDeCategorias.sugerirChave(nomeFinal)

        // Validacao semantica classe x unidade (Secao 7.3): alerta, nunca erro.
        val alerta = alertaDeClasse(doLexico, unidadeFinal, quantidadeFinal)

        val itemBase = ItemInterpretado(
            nomeItem = nomeFinal,
            textoOriginal = original,
            quantidade = quantidadeFinal,
            unidade = unidadeFinal,
            pesoOuVolume = pesoOuVolume,
            ehKit = ehKit,
            itensPorKit = itensPorKit,
            categoriaSugerida = categoria,
            classeDoItem = doLexico?.classe,
            observacao = observacaoFinal?.takeIf { it.isNotBlank() }?.let { TextoUtil.capitalizarTitulo(it) },
            confianca = confianca,
            motivo = motivo,
            outrasInterpretacoes = alternativas,
            alerta = alerta,
            preco = preco,
        )

        val comEmbalagem = if (embalagem != null) {
            itemBase.copy(
                observacao = listOfNotNull(itemBase.observacao, "Em $embalagem").joinToString("; "),
            )
        } else {
            itemBase
        }

        return SugestoesDoParser.aplicar(
            item = comEmbalagem,
            tokenPendente = tokenPendente,
            itemDoLexico = doLexico,
            memoria = memoria,
            fonte = fonte,
        )
    }

    /**
     * A sobra do casamento com o lexico e **complemento descartavel** do nome?
     *
     * E descartavel (vai para observacao, e o nome fica so com o trecho do
     * catalogo) quando a sobra:
     * - so tem palavras de embalagem e preposicao ("pacote de" em "pacote de asa");
     * - comeca com qualificador de observacao ("sem gordura");
     * - tem qualquer numero residual ("12 rolos", "1 kg").
     *
     * Qualquer OUTRA sobra e palavra propria do produto ("de peito" em
     * "maça de peito"): entra no nome, nunca e descartada.
     */
    private fun sobraEhComplemento(palavrasSobra: List<String>): Boolean {
        if (palavrasSobra.isEmpty()) return true
        if (palavrasSobra.all { it in PALAVRAS_DE_EMBALAGEM || it in DivisorDeLinha.PREPOSICOES_DE }) return true
        if (palavrasSobra.first() in QUALIFICADORES_DE_OBSERVACAO) return true
        if (palavrasSobra.any { it.any(Char::isDigit) }) return true
        return false
    }

    /** "duzia de carne" -> alerta; nunca bloqueia, nunca falha em silencio. */
    private fun alertaDeClasse(
        item: ItemDoLexico?,
        unidade: Unidade?,
        quantidade: BigDecimal?,
    ): String? {
        if (item == null || unidade == null || quantidade == null) return null
        // Contagem de itens avulsos (un/dz). "2 pacotes de arroz" nao e alerta:
        // pacote e embalagem, e arroz e vendido em pacote o tempo todo.
        val contagemAvulsa = unidade == Unidade.UNIDADE || unidade == Unidade.DUZIA
        return when {
            contagemAvulsa && item.classe == ClasseDeItem.PESAVEL && quantidade > BigDecimal.ONE ->
                "${item.nomeCanonico} e usualmente vendido por peso"
            unidade.dimensao == Dimensao.COMPRIMENTO && item.unidadePadrao.dimensao != Dimensao.COMPRIMENTO ->
                "${item.nomeCanonico} nao e vendido por metro"
            else -> null
        }
    }

    // =================================================================================
    // Leitura da quantidade
    // =================================================================================

    private data class Leitura(
        val quantidade: BigDecimal? = null,
        val unidade: Unidade? = null,
        val pesoOuVolume: BigDecimal? = null,
        val ehKit: Boolean = false,
        val itensPorKit: Int? = null,
        val palavrasRestantes: List<String> = emptyList(),
        val confianca: Confianca = Confianca.MEDIA,
        val motivo: String? = null,
        val alternativas: List<InterpretacaoAlternativa> = emptyList(),
        val tokenPendente: TokenDeQuantidade? = null,
        val embalagem: String? = null,
        /** O numero foi digitado (3, "tres"), nao herdado de "1 implicito". */
        val numeroExplicito: Boolean = false,
        /** Mantem o texto inteiro como nome, sem recortar pelo lexico. */
        val nomeLiteral: Boolean = false,
    )

    private fun extrairQuantidade(
        palavras: List<String>,
        quantidadeAnexa: String?,
        fonte: FonteDaEntrada,
    ): Leitura {
        // Guarda de falso positivo: "meia calca", "quina da mesa", "parafuso".
        val texto = palavras.joinToString(" ")
        val guarda = FalsosPositivos.guardaAtiva(texto)

        // P2: a quantidade veio de um segmento seguinte ("ovos, meia duzia").
        if (quantidadeAnexa != null) {
            val anexa = separarEmPalavras(TextoUtil.normalizarLeve(quantidadeAnexa))
            val lida = lerNoInicio(anexa, fonte)
            if (lida != null) {
                return lida.copy(palavrasRestantes = palavras)
            }
        }

        if (guarda != null) {
            return Leitura(
                quantidade = BigDecimal.ONE,
                palavrasRestantes = palavras,
                confianca = Confianca.ALTA,
                motivo = "\"${guarda.gatilho}\" aqui e ${guarda.explicacao}",
                nomeLiteral = true,
            )
        }

        // P4 (prioridade maxima): item + numeral + unidade no fim.
        lerNoFim(palavras)?.let { return it }

        // P1 / P3: quantidade na frente.
        lerNoInicio(palavras, fonte)?.let { return it }

        // Sem quantidade declarada: uma unidade do item.
        return Leitura(
            quantidade = BigDecimal.ONE,
            palavrasRestantes = palavras,
            confianca = Confianca.ALTA,
            tokenPendente = candidatoPopular(palavras, fonte),
        )
    }

    /** Padrao P4: "cafe 500g", "arroz 5kg", "pao frances 3", "leite 2 l". */
    private fun lerNoFim(palavras: List<String>): Leitura? {
        if (palavras.size < 2) return null

        // Multipack ("cerveja 12x350ml", "agua c/ 6"): quem sabe ler isso e o
        // ConversorDeUnidades do app - o parser nao reimplementa conversao.
        val texto = palavras.joinToString(" ")
        val doConversor = ConversorDeUnidades.interpretarTexto(texto)
        if (doConversor?.itensPorEmbalagem != null && doConversor.itensPorEmbalagem!! > 1) {
            val nome = ConversorDeUnidades.nomeSemQuantidade(texto)
                .split(' ')
                .filter { it.isNotBlank() && it !in DivisorDeLinha.PREPOSICOES_COM }
            if (nome.any { it.any(Char::isLetter) }) {
                return Leitura(
                    quantidade = BigDecimal.ONE,
                    unidade = doConversor.unidade,
                    pesoOuVolume = doConversor.quantidade,
                    ehKit = true,
                    itensPorKit = doConversor.itensPorEmbalagem,
                    palavrasRestantes = nome,
                    confianca = Confianca.ALTA,
                )
            }
        }

        val ultima = palavras.last()
        val penultima = palavras[palavras.size - 2]

        // "cafe 500 g"
        val unidadeFinal = Unidade.porTexto(ultima)
        if (unidadeFinal != null && palavras.size >= 3) {
            val valor = numeroSimples(penultima)
            if (valor != null) {
                val restantes = palavras.dropLast(2)
                if (restantes.any { it.any(Char::isLetter) }) {
                    return Leitura(
                        quantidade = BigDecimal.ONE,
                        unidade = unidadeFinal,
                        pesoOuVolume = valor,
                        palavrasRestantes = restantes,
                        confianca = Confianca.ALTA,
                    )
                }
            }
        }

        // "pao frances 3"
        val valorFinal = numeroSimples(ultima)
        if (valorFinal != null) {
            val restantes = palavras.dropLast(1)
            if (restantes.any { it.any(Char::isLetter) } && restantes.none { Unidade.porTexto(it) != null }) {
                return Leitura(
                    quantidade = valorFinal,
                    unidade = null,
                    palavrasRestantes = restantes,
                    confianca = Confianca.ALTA,
                    numeroExplicito = true,
                )
            }
        }
        return null
    }

    /** Padroes P1 e P3: quantidade (token, numeral ou digito) no inicio. */
    private fun lerNoInicio(palavras: List<String>, fonte: FonteDaEntrada): Leitura? {
        if (palavras.isEmpty()) return null
        var indice = 0
        var valor: BigDecimal? = null
        var unidade: Unidade? = null
        var ehKit = false
        var itensPorKit: Int? = null
        var embalagem: String? = null
        var confianca = Confianca.ALTA
        var motivo: String? = null
        val alternativas = mutableListOf<InterpretacaoAlternativa>()

        // 1. Fracao composta primeiro ("um quarto", "tres quartos"): comeca com
        //    numeral, entao precisa vencer o token "um".
        var fracao: Pair<BigDecimal, Int>? = casarFracao(palavras, apenasCompostas = true)
        if (fracao != null) {
            valor = fracao.first
            indice = fracao.second
        }

        // 2. Token nomeado - "meia duzia" tem de vencer "meia".
        var tokenRecusado: TokenDeQuantidade? = null
        if (valor == null) {
            val casado = TabelaDeQuantidades.casarNoInicio(palavras)
            val token = casado?.first
            if (token != null) {
                if (aceitaToken(token, PosicaoAceita.ANTES_DO_ITEM, fonte, palavras, casado.second)) {
                    valor = token.valor
                    indice = casado.second
                    if (token.ehEmbalagem) {
                        unidade = Unidade.PACOTE
                        embalagem = token.canonico
                    } else {
                        unidade = token.unidade
                    }
                    confianca = token.confiancaBase
                } else if (token.dominio == DominioDoToken.POPULAR && casado.second == palavras.size) {
                    // Regra C: a expressao popular esta inteira na linha, mas sem
                    // "de" + item. Nao converte e nao chuta - vira sugestao (G2).
                    tokenRecusado = token
                }
            }
        }

        // 3. Fracao simples ("meio", "meia", "metade").
        if (valor == null && tokenRecusado == null) {
            fracao = casarFracao(palavras, apenasCompostas = false)
            if (fracao != null) {
                valor = fracao.first
                indice = fracao.second
            }
        }

        if (tokenRecusado != null) {
            return Leitura(
                quantidade = BigDecimal.ONE,
                palavrasRestantes = palavras,
                confianca = Confianca.BAIXA,
                motivo = "\"${tokenRecusado.canonico}\" so vira ${tokenRecusado.valor.toPlainString()} " +
                    "seguido de \"de\" + item conhecido",
                alternativas = listOf(
                    InterpretacaoAlternativa(
                        descricao = "${tokenRecusado.valor.toPlainString()} unidades",
                        quantidade = tokenRecusado.valor,
                        unidade = tokenRecusado.unidade,
                        confianca = Confianca.BAIXA,
                        motivo = "apelido popular catalogado",
                    ),
                ),
                tokenPendente = tokenRecusado,
                nomeLiteral = true,
            )
        }

        // 4. Digitos ou numeral por extenso.
        var numeroExplicito = fracao != null
        if (valor == null) {
            val digito = numeroSimples(palavras[0])
            if (digito != null) {
                valor = digito
                indice = 1
                numeroExplicito = true
            } else {
                val numeral = NumeraisPorExtenso.consumirNoInicio(palavras)
                if (numeral != null) {
                    valor = BigDecimal(numeral.first)
                    indice = numeral.second
                    numeroExplicito = true
                }
            }
        }

        // 5. Unidade logo no inicio: "quilo e meio de carne" = 1 quilo + meio.
        if (valor == null) {
            val unidadeInicial = Unidade.porTexto(palavras[0])
            if (unidadeInicial != null) {
                valor = BigDecimal.ONE
                unidade = unidadeInicial
                indice = 1
            }
        }

        if (valor == null) return null

        // 6. "e meio" / "e 50" logo depois do numero.
        val complemento = casarComplementoE(palavras, indice)
        if (complemento != null) {
            valor = valor.add(complemento.valor)
            indice = complemento.proximoIndice
            if (complemento.ambiguo) {
                confianca = Confianca.BAIXA
                motivo = complemento.motivo
                alternativas += complemento.alternativa
            }
        }

        // 7. Unidade declarada depois do numero ("2 kg", "500 g", "3 pacotes").
        //    Antes dela pode vir "de": "um quarto DE quilo de presunto".
        while (indice < palavras.size && palavras[indice] in DivisorDeLinha.PREPOSICOES_DE &&
            indice + 1 < palavras.size &&
            (Unidade.porTexto(palavras[indice + 1]) != null ||
                TabelaDeQuantidades.casarNoInicio(palavras.drop(indice + 1))?.first?.ehEmbalagem == true)
        ) {
            indice++
        }
        if (indice < palavras.size && embalagem == null) {
            val casadoEmbalagem = TabelaDeQuantidades.casarNoInicio(palavras.drop(indice))
            val tokenEmbalagem = casadoEmbalagem?.first
            if (tokenEmbalagem != null && tokenEmbalagem.ehEmbalagem) {
                // "meia caixa", "2 fardos": a embalagem guarda o nome, nao so "pct".
                unidade = Unidade.PACOTE
                embalagem = tokenEmbalagem.canonico
                indice += casadoEmbalagem.second
            } else {
                val possivel = Unidade.porTexto(palavras[indice])
                if (possivel != null) {
                    unidade = possivel
                    indice++
                    val depoisDaUnidade = casarComplementoE(palavras, indice)
                    if (depoisDaUnidade != null && !depoisDaUnidade.ambiguo) {
                        valor = valor.add(depoisDaUnidade.valor)
                        indice = depoisDaUnidade.proximoIndice
                    }
                }
            }
        }

        // 6. "pacote com 2", "fardo c/ 6" -> kit com conteudo declarado.
        if (embalagem != null && indice < palavras.size && palavras[indice] in DivisorDeLinha.PREPOSICOES_COM) {
            val conteudo = numeroSimples(palavras.getOrNull(indice + 1).orEmpty())
                ?: NumeraisPorExtenso.consumirNoInicio(palavras.drop(indice + 1))?.first?.let { BigDecimal(it) }
            if (conteudo != null && conteudo.signum() > 0) {
                ehKit = true
                itensPorKit = conteudo.toInt()
                indice += 2
            }
        }

        // 7. Preposicao de ligacao antes do item.
        while (indice < palavras.size && palavras[indice] in DivisorDeLinha.PREPOSICOES_DE) {
            indice++
        }

        val restantes = palavras.drop(indice)

        // 8. Embalagem sem conteudo declarado: o lexico pode saber a capacidade.
        if (embalagem != null && itensPorKit == null) {
            val item = LexicoDeItens.encontrarNoTexto(restantes.joinToString(" "))
            val capacidade = item?.unidadeInterna?.get(apelidoDaEmbalagem(embalagem))
            when {
                capacidade != null && fracao != null -> {
                    // "meia caixa de leite" = 0,5 x 12 = 6 unidades (Secao 5.4).
                    valor = valor.multiply(BigDecimal(capacidade)).stripTrailingZeros()
                    unidade = Unidade.UNIDADE
                    motivo = "metade da ${embalagem} de ${item.nomeCanonico} (${capacidade} por ${embalagem})"
                }
                capacidade != null -> {
                    ehKit = true
                    itensPorKit = capacidade
                }
                item?.variacaoDeclarada?.contains(embalagem) == true -> {
                    // Variacao real de mercado: declara em vez de escolher um valor.
                    motivo = "capacidade varia (${item.variacaoDeclarada}) - itensPorKit nao foi chutado"
                }
            }
        }

        return Leitura(
            quantidade = valor,
            unidade = unidade,
            pesoOuVolume = null,
            ehKit = ehKit,
            itensPorKit = itensPorKit,
            palavrasRestantes = restantes,
            confianca = confianca,
            motivo = motivo,
            alternativas = alternativas,
            tokenPendente = candidatoPopular(restantes, fonte),
            embalagem = embalagem,
            numeroExplicito = numeroExplicito,
        )
    }

    // =================================================================================
    // Preco por segmento
    // =================================================================================

    /** Preco achado no fim do segmento + as palavras que sobraram para nome/quantidade. */
    private data class PrecoNoFim(val valor: BigDecimal, val restantes: List<String>)

    /**
     * Preco monetario no FIM do segmento - **por segmento**, nunca por linha.
     *
     * E dinheiro quando:
     * - tem prefixo "R$" ("leite r$ 4,99", "leite r$4,99"); com o prefixo
     *   explicito, inteiro tambem e dinheiro ("r$ 3999");
     * - e o ultimo token com separador decimal ("uva 1kg 3,99", "3.99").
     *
     * Inteiro solto no fim ("cafe 3") e QUANTIDADE (padrao P4), nunca preco;
     * "99999,00" com digito demais tambem nao (mesmo cuidado de
     * InterpretadorDeAdicaoRapida: preferivel nao reconhecer a truncar).
     * Devolve null quando nao ha preco - o item nao ganha valor inventado.
     */
    private fun extrairPrecoDoFim(palavras: List<String>): PrecoNoFim? {
        if (palavras.isEmpty()) return null

        // 1. Prefixo "R$": o mais recente vence. Depois do prefixo, decimal ou
        //    inteiro - o usuario declarou dinheiro de forma explicita.
        for (indice in palavras.indices.reversed()) {
            val token = palavras[indice]
            val colado = REGEX_PRECO_RS_COLADO.matchEntire(token)
            if (colado != null) {
                val valor = TextoUtil.paraDecimal(colado.groupValues[1])
                val restantes = palavras.semIndice(indice)
                if (aceitaPreco(valor, restantes)) return PrecoNoFim(valor!!, restantes)
                continue
            }
            if (token == "r$") {
                val valor = palavras.getOrNull(indice + 1)?.let { numeroAposPrefixoRs(it) }
                if (valor != null) {
                    // Remove o prefixo e o numero logo depois dele.
                    val restantes = palavras.filterIndexed { posicao, _ ->
                        posicao != indice && posicao != indice + 1
                    }
                    if (aceitaPreco(valor, restantes)) return PrecoNoFim(valor, restantes)
                }
            }
        }

        // 2. Decimal solto no fim: "uva 1kg 3,99". So com separador decimal -
        //    inteiro no fim e quantidade (P4).
        val ultima = palavras.last()
        if (REGEX_PRECO_DECIMAL.matchEntire(ultima) != null) {
            val valor = TextoUtil.paraDecimal(ultima)
            val restantes = palavras.dropLast(1)
            if (aceitaPreco(valor, restantes)) return PrecoNoFim(valor!!, restantes)
        }
        return null
    }

    /** Token depois do "r$": "4,99", "3.99" ou "3999" (inteiro explicito de dinheiro). */
    private fun numeroAposPrefixoRs(token: String): BigDecimal? {
        if (REGEX_PRECO_APOS_RS.matchEntire(token) == null) return null
        return TextoUtil.paraDecimal(token)
    }

    /** Aceita o preco so se for positivo, dentro do teto, e sobrar nome para o item. */
    private fun aceitaPreco(valor: BigDecimal?, restantes: List<String>): Boolean =
        valor != null && valor.signum() > 0 &&
            valor <= BigDecimal(Constantes.PRECO_MAXIMO_ACEITO) &&
            restantes.any { it.any(Char::isLetter) }

    private fun List<String>.semIndice(indice: Int): List<String> =
        filterIndexed { posicao, _ -> posicao != indice }

    // =================================================================================
    // Pecas auxiliares
    // =================================================================================

    private data class ComplementoE(
        val valor: BigDecimal,
        val proximoIndice: Int,
        val ambiguo: Boolean,
        val motivo: String?,
        val alternativa: InterpretacaoAlternativa,
    )

    /** Trata "um quilo e meio" (1,5) e "2 e 50" (2,5 com duvida registrada). */
    private fun casarComplementoE(palavras: List<String>, indice: Int): ComplementoE? {
        if (indice >= palavras.size || palavras[indice] != "e") return null
        val seguinte = palavras.getOrNull(indice + 1) ?: return null

        if (seguinte in MEIO) {
            return ComplementoE(
                valor = MEIO_VALOR,
                proximoIndice = indice + 2,
                ambiguo = false,
                motivo = null,
                alternativa = InterpretacaoAlternativa("meio a mais", MEIO_VALOR),
            )
        }

        val centavos = numeroSimples(seguinte) ?: return null
        if (centavos >= BigDecimal(100)) return null
        val fracionario = centavos.movePointLeft(centavos.precision().coerceAtLeast(1))
        return ComplementoE(
            valor = fracionario,
            proximoIndice = indice + 2,
            ambiguo = true,
            motivo = "\"e $seguinte\" pode ser casa decimal ou outra quantidade - confirme",
            alternativa = InterpretacaoAlternativa(
                descricao = "$seguinte como item separado",
                quantidade = centavos,
                confianca = Confianca.BAIXA,
                motivo = "leitura alternativa de \"e $seguinte\"",
            ),
        )
    }

    private fun casarFracao(palavras: List<String>, apenasCompostas: Boolean): Pair<BigDecimal, Int>? {
        val menor = if (apenasCompostas) 2 else 1
        for (tamanho in minOf(2, palavras.size) downTo menor) {
            val trecho = palavras.take(tamanho).joinToString(" ")
            FRACOES[trecho]?.let { return it to tamanho }
        }
        return null
    }

    /** Numero em digitos, com decimal ou fracao com barra ("1/2"). */
    private fun numeroSimples(palavra: String): BigDecimal? {
        if (palavra.isBlank()) return null
        if (REGEX_FRACAO_BARRA.matches(palavra)) {
            val (a, b) = palavra.split('/')
            val numerador = a.toBigDecimalOrNull() ?: return null
            val denominador = b.toBigDecimalOrNull() ?: return null
            if (denominador.signum() == 0) return null
            return numerador.divide(denominador, 4, RoundingMode.HALF_EVEN).stripTrailingZeros()
        }
        if (!REGEX_NUMERO.matches(palavra)) return null
        return TextoUtil.paraDecimal(palavra)
    }

    /**
     * Um apelido de animal (ou outro token fora de posicao) aparece no nome do
     * item? Vira candidato para a sugestao G1 - **nunca** conversao automatica.
     */
    private fun candidatoPopular(palavras: List<String>, fonte: FonteDaEntrada): TokenDeQuantidade? {
        if (fonte == FonteDaEntrada.OCR) return null
        if (palavras.size != 1) return null
        val token = TabelaDeQuantidades.porTexto(palavras.first()) ?: return null
        return token.takeIf { it.dominio == DominioDoToken.POPULAR && !it.sensivel }
    }

    /** Filtro duro de posicao (Regra A): animal so em posicao estritamente numerica. */
    private fun aceitaToken(
        token: TokenDeQuantidade,
        posicao: PosicaoAceita,
        fonte: FonteDaEntrada,
        palavras: List<String>,
        consumidas: Int,
    ): Boolean {
        if (fonte == FonteDaEntrada.OCR && token.dominio == DominioDoToken.POPULAR) return false
        if (token.origemRepertorio == OrigemRepertorio.ANIMAL) return false
        if (token.sensivel) return false
        if (posicao in token.posicoesAceitas) return true
        // Regra B: expressao completa + "de" + item do lexico.
        if (PosicaoAceita.ANTES_DO_ITEM_COM_DE in token.posicoesAceitas) {
            val resto = palavras.drop(consumidas)
            val temDe = resto.firstOrNull() in DivisorDeLinha.PREPOSICOES_DE
            val temItem = LexicoDeItens.encontrarNoTexto(resto.drop(1).joinToString(" ")) != null
            return temDe && temItem
        }
        return false
    }

    private fun itemLiteral(original: String, motivo: String) = ItemInterpretado(
        nomeItem = original,
        textoOriginal = original,
        quantidade = null,
        confianca = Confianca.BAIXA,
        motivo = motivo,
        categoriaSugerida = DicionarioDeCategorias.CHAVE_PADRAO,
    )

    /**
     * Quebra em palavras separando **apenas** digito de unidade conhecida
     * ("500g" -> "500 g") e o multiplicador ("12x350" -> "12 x 350").
     *
     * Separar todo digito de toda letra destruiria texto de OCR ruim
     * ("arr0z" viraria "arr 0 z") e nomes legitimos ("omega3", "zero7").
     */
    private fun separarEmPalavras(textoLeve: String): List<String> =
        REGEX_NUMERO_UNIDADE.replace(textoLeve) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .let { REGEX_MULTIPLICADOR.replace(it) { m -> "${m.groupValues[1]} x ${m.groupValues[2]}" } }
            .split(' ', '\t')
            .map { it.trim().trim('.', ':', '!', '?', '-', '"', '\'', '(', ')') }
            .filter { it.isNotBlank() }

    private fun limparPreposicoes(texto: String): String {
        var palavras = texto.split(' ').filter { it.isNotBlank() }
        while (palavras.isNotEmpty() && palavras.first() in DivisorDeLinha.PREPOSICOES_DE) {
            palavras = palavras.drop(1)
        }
        while (palavras.isNotEmpty() && palavras.last() in DivisorDeLinha.PREPOSICOES_DE) {
            palavras = palavras.dropLast(1)
        }
        return palavras.joinToString(" ")
    }

    /**
     * Recupera o trecho **original** (com acento e maiuscula) correspondente a
     * um texto normalizado - a tela mostra o que a pessoa escreveu.
     */
    private fun recortarOriginal(original: String, normalizadoProcurado: String): String {
        val alvo = normalizadoProcurado.split(' ').filter { it.isNotBlank() }
        if (alvo.isEmpty()) return original
        val palavrasOriginais = original.split(' ', '\t').filter { it.isNotBlank() }
        val normalizadas = palavrasOriginais.map { TextoUtil.normalizar(it) }
        for (inicio in normalizadas.indices) {
            if (normalizadas[inicio] != alvo.first()) continue
            val fim = inicio + alvo.size
            if (fim > normalizadas.size) continue
            if (normalizadas.subList(inicio, fim) == alvo) {
                return palavrasOriginais.subList(inicio, fim).joinToString(" ").trim(',', ';', '.')
            }
        }
        return normalizadoProcurado
    }

    private fun apelidoDaEmbalagem(canonico: String): String = when (canonico) {
        "caixa" -> "cx"
        "pacote" -> "pct"
        "fardo" -> "fardo"
        "bandeja" -> "bandeja"
        else -> canonico
    }

    /** Devolve a PIOR das duas confiancas (ALTA < MEDIA < BAIXA na escala interna). */
    private fun pior(a: Confianca, b: Confianca): Confianca =
        if (a.ordinal >= b.ordinal) a else b

    private fun ehRuido(caractere: Char): Boolean =
        !caractere.isLetterOrDigit() && caractere !in " ,./%-"

    private companion object {
        val REGEX_PARENTESES = Regex("""\(([^)]*)\)""")
        val REGEX_ILEGIVEL = Regex("""[#@*\\|~^<>{}\[\]\uFFFD]""")
        val REGEX_NUMERO = Regex("""\d+([.,]\d+)?""")
        val REGEX_FRACAO_BARRA = Regex("""\d+/\d+""")

        /** Preco solto no fim: "3,99", "3.99" - sempre com separador decimal. */
        val REGEX_PRECO_DECIMAL = Regex("""\d{1,4}[.,]\d{1,2}""")

        /** "r$4,99" colado: o preco vem junto do prefixo. */
        val REGEX_PRECO_RS_COLADO = Regex("""r\$\s*(\d{1,4}(?:[.,]\d{1,2})?)""")

        /** Token depois do "r$" solto: decimal ou inteiro explicito. */
        val REGEX_PRECO_APOS_RS = Regex("""\d{1,4}(?:[.,]\d{1,2})?""")

        /**
         * Embalagens que so valem como PREFIXO do nome ("pacote de asa" ->
         * "Asa"): no meio do nome nao sao descartaveis ("de peito" fica).
         */
        val PALAVRAS_DE_EMBALAGEM = setOf(
            "pacote", "pacotes", "caixa", "caixas", "fardo", "fardos",
            "lata", "latas", "garrafa", "garrafas", "saco", "sacos",
            "bandeja", "bandejas", "pct", "cx",
        )

        /** Palavra que inicia um complemento de observacao, nao de nome. */
        val QUALIFICADORES_DE_OBSERVACAO = setOf("sem")
        /** "2kg", "500g", "1,5l": digito colado numa unidade canonica. */
        val REGEX_NUMERO_UNIDADE = Regex(
            """(\d)(kgs?|gramas?|grs?|g|mls?|lts?|litros?|l|quilos?|kilos?|kl|un|und|uni|unidades?|dz|duzias?|pct|pacotes?|cx|caixas?|fd|fardos?|m)(?![a-z0-9])""",
        )

        /** "12x350", "6 x 1": multiplicador de embalagem. */
        val REGEX_MULTIPLICADOR = Regex("""(\d)\s*[xX]\s*(\d)""")

        val MEIO = setOf("meio", "meia", "metade")
        val MEIO_VALOR: BigDecimal = BigDecimal("0.5")

        /** Fracoes faladas -> decimal (Secao 5.4). */
        val FRACOES: Map<String, BigDecimal> = mapOf(
            "meio" to BigDecimal("0.5"),
            "meia" to BigDecimal("0.5"),
            "metade" to BigDecimal("0.5"),
            "um terco" to BigDecimal("0.333"),
            "dois tercos" to BigDecimal("0.667"),
            "um quarto" to BigDecimal("0.25"),
            "tres quartos" to BigDecimal("0.75"),
            "quarto" to BigDecimal("0.25"),
        )
    }
}

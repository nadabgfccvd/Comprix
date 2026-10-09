package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * Tabela de tokens de quantidade: coletivos, embalagens e apelidos populares
 * de numeros.
 *
 * ## Procedencia dos dados (nada aqui foi inventado)
 * - **Apelidos de animais** = os 25 grupos do **jogo do bicho** (cachorro 5,
 *   cabra 6, carneiro 7, peru 20, veado 24...), conferidos em tabelas publicas
 *   do jogo. Marcados [Verificacao.CONFIRMADO_EM_FONTE].
 * - **Apelidos nao animais** = repertorio de **bingo/vispora** ("cantar as
 *   pedras"). Os que aparecem no corpo da fonte consultada sao
 *   `CONFIRMADO_EM_FONTE`; os de tradicao oral (incluindo os listados no
 *   documento do parser) sao `TRADICAO_POPULAR_SEM_FONTE`.
 * - Numero da faixa 1-90 **sem** apelido documentado simplesmente nao tem
 *   entrada. A lista de lacunas esta no README do parser.
 *
 * ## A regra que protege a lista de compras
 * "cachorro" e 5 no bingo **e** racao para cachorro no mercado; "patinho" e
 * parte de "dois patinhos na lagoa" **e** corte bovino; "peru" e 20 **e** ave
 * de Natal. Por isso todo apelido de animal so e aceito em
 * [PosicaoAceita.ISOLADO_POSICAO_NUMERICA] - ou seja, sozinho, onde so cabe um
 * numero. Em nome de item ele e **sempre** texto literal.
 */
object TabelaDeQuantidades {

    private const val FONTE_BICHO =
        "Tabela dos 25 grupos do jogo do bicho (eojogodobicho.com; resultadosjogodobicho.net)"
    private const val FONTE_BINGO =
        "Repertorio de bingo/vispora - 'Cantar as pedras de bingo' (obreveverbo.blogspot.com, 2013)"
    private const val FONTE_BINGO_COMENTARIOS =
        "$FONTE_BINGO - secao de comentarios (tradicao oral registrada)"
    private const val FONTE_DOCUMENTO =
        "Documento 'Parser de entrada em texto livre para o Comprix', secao 5.2"

    private fun decimal(valor: String) = BigDecimal(valor)

    // =================================================================================
    // 1. Coletivos e numerais de contagem
    // =================================================================================

    val coletivos: List<TokenDeQuantidade> = listOf(
        TokenDeQuantidade(
            canonico = "unidade", variantes = listOf("um", "uma", "unidade", "unidades", "un", "und", "uni"),
            valor = decimal("1"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Norma da lingua",
        ),
        TokenDeQuantidade(
            canonico = "par", variantes = listOf("par", "um par", "uma dupla", "dupla"),
            valor = decimal("2"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            classesAceitas = setOf(ClasseDeItem.CONTAVEL, ClasseDeItem.AMBIGUO),
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
            colisaoItem = "parafuso, par de meias (vestuario)",
        ),
        TokenDeQuantidade(
            canonico = "trinca", variantes = listOf("trinca", "terno", "trio", "um trio"),
            valor = decimal("3"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            classesAceitas = setOf(ClasseDeItem.CONTAVEL, ClasseDeItem.AMBIGUO),
            verificacao = Verificacao.TRADICAO_POPULAR_SEM_FONTE, fonte = FONTE_DOCUMENTO,
        ),
        TokenDeQuantidade(
            canonico = "quadra", variantes = listOf("quadra", "uma quadra"),
            valor = decimal("4"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            posicoesAceitas = setOf(PosicaoAceita.ISOLADO_POSICAO_NUMERICA, PosicaoAceita.ANTES_DO_ITEM_COM_DE),
            confiancaBase = Confianca.MEDIA,
            verificacao = Verificacao.TRADICAO_POPULAR_SEM_FONTE, fonte = FONTE_DOCUMENTO,
            colisaoItem = "quadra (lugar)",
        ),
        TokenDeQuantidade(
            canonico = "quina", variantes = listOf("quina", "uma quina"),
            valor = decimal("5"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            posicoesAceitas = setOf(PosicaoAceita.ISOLADO_POSICAO_NUMERICA, PosicaoAceita.ANTES_DO_ITEM_COM_DE),
            confiancaBase = Confianca.MEDIA,
            verificacao = Verificacao.TRADICAO_POPULAR_SEM_FONTE, fonte = FONTE_DOCUMENTO,
            colisaoItem = "quina da mesa (falso positivo conhecido)",
        ),
        TokenDeQuantidade(
            canonico = "meia duzia", variantes = listOf("meia duzia", "meia-duzia", "meiaduzia", "meia dz"),
            valor = decimal("6"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            classesAceitas = setOf(ClasseDeItem.CONTAVEL, ClasseDeItem.AMBIGUO),
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = FONTE_BINGO,
        ),
        TokenDeQuantidade(
            canonico = "dezena", variantes = listOf("dezena", "uma dezena", "dezenas"),
            valor = decimal("10"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
        ),
        TokenDeQuantidade(
            // Expandida em unidades: "uma duzia de ovos" sai como 12 un, igual a
            // "meia duzia" = 6 un. A unidade dz continua disponivel na edicao manual.
            canonico = "duzia", variantes = listOf("duzia", "uma duzia", "duzias", "1 duzia"),
            valor = decimal("12"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            classesAceitas = setOf(ClasseDeItem.CONTAVEL, ClasseDeItem.AMBIGUO),
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
            colisaoItem = "duzia de motivos (falso positivo conhecido)",
        ),
        TokenDeQuantidade(
            canonico = "duas duzias", variantes = listOf("duas duzias", "2 duzias", "dois duzias"),
            valor = decimal("24"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            classesAceitas = setOf(ClasseDeItem.CONTAVEL, ClasseDeItem.AMBIGUO),
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Combinacao multiplicativa (2 x 12)",
        ),
        TokenDeQuantidade(
            canonico = "vintena", variantes = listOf("vintena", "uma vintena"),
            valor = decimal("20"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
        ),
        TokenDeQuantidade(
            canonico = "meia centena", variantes = listOf("meia centena", "meia-centena"),
            valor = decimal("50"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
        ),
        TokenDeQuantidade(
            canonico = "cento", variantes = listOf("cento", "um cento", "centena", "uma centena"),
            valor = decimal("100"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo da norma culta",
            colisaoItem = "cento e um dalmatas (falso positivo conhecido)",
        ),
        TokenDeQuantidade(
            canonico = "grossa", variantes = listOf("grossa", "uma grossa"),
            valor = decimal("144"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            posicoesAceitas = setOf(PosicaoAceita.ISOLADO_POSICAO_NUMERICA, PosicaoAceita.ANTES_DO_ITEM_COM_DE),
            confiancaBase = Confianca.MEDIA,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo tradicional do comercio (12 duzias)",
            colisaoItem = "adjetivo 'grossa' (falso positivo conhecido)",
        ),
        TokenDeQuantidade(
            canonico = "meio milheiro", variantes = listOf("meio milheiro", "meio-milheiro"),
            valor = decimal("500"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo do comercio (material de construcao)",
        ),
        TokenDeQuantidade(
            canonico = "milheiro", variantes = listOf("milheiro", "um milheiro"),
            valor = decimal("1000"), unidade = Unidade.UNIDADE, dominio = DominioDoToken.COLETIVO,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE, fonte = "Coletivo do comercio",
        ),
    )

    // =================================================================================
    // 2. Embalagens - valor 1 + o conteudo vem do lexico (nunca chutado)
    // =================================================================================

    val embalagens: List<TokenDeQuantidade> = listOf(
        embalagem("caixa", listOf("caixa", "cx", "uma caixa", "caixas")),
        embalagem("fardo", listOf("fardo", "fd", "um fardo", "fardos")),
        embalagem("pacote", listOf("pacote", "pct", "um pacote", "pacotes", "pct.")),
        embalagem("saco", listOf("saco", "um saco", "sacos", "saca")),
        embalagem("vidro", listOf("vidro", "um vidro", "vidros", "pote")),
        embalagem("lata", listOf("lata", "uma lata", "latas", "latinha")),
        embalagem("garrafa", listOf("garrafa", "uma garrafa", "garrafas", "garrafinha")),
        embalagem("bandeja", listOf("bandeja", "uma bandeja", "bandejas")),
        embalagem("molho", listOf("molho", "um molho", "molhos"), colisao = "molho de tomate (produto)"),
        embalagem("penca", listOf("penca", "uma penca", "pencas")),
        embalagem("cacho", listOf("cacho", "um cacho", "cachos")),
        embalagem("engradado", listOf("engradado", "um engradado", "grade")),
    )

    private fun embalagem(canonico: String, variantes: List<String>, colisao: String? = null) =
        TokenDeQuantidade(
            canonico = canonico,
            variantes = variantes,
            valor = BigDecimal.ONE,
            unidade = Unidade.PACOTE,
            dominio = DominioDoToken.EMBALAGEM,
            verificacao = Verificacao.CONFIRMADO_EM_FONTE,
            fonte = "Embalagens usuais do varejo brasileiro",
            colisaoItem = colisao,
            ehEmbalagem = true,
        )

    // =================================================================================
    // 3. Apelidos de ANIMAIS - os 25 grupos do jogo do bicho
    //    Posicao estritamente numerica, sem excecao (Regra A).
    // =================================================================================

    val apelidosDeAnimais: List<TokenDeQuantidade> = listOf(
        animal(1, "avestruz", listOf("avestruz")),
        animal(2, "aguia", listOf("aguia", "águia")),
        animal(3, "burro", listOf("burro", "jumento")),
        animal(4, "borboleta", listOf("borboleta")),
        animal(5, "cachorro", listOf("cachorro", "cao", "cachorrinho"), colisao = "racao para cachorro (Pet)"),
        animal(6, "cabra", listOf("cabra", "bode"), colisao = "queijo de cabra (Laticinios)"),
        animal(7, "carneiro", listOf("carneiro", "ovelha"), colisao = "carne de carneiro (Acougue)"),
        animal(8, "camelo", listOf("camelo")),
        animal(9, "cobra", listOf("cobra", "serpente")),
        animal(10, "coelho", listOf("coelho"), colisao = "carne de coelho (Acougue)"),
        animal(11, "cavalo", listOf("cavalo")),
        animal(12, "elefante", listOf("elefante")),
        animal(13, "galo", listOf("galo"), colisao = "galo/galinha (Acougue)"),
        animal(14, "gato", listOf("gato"), colisao = "racao para gato (Pet)"),
        animal(15, "jacare", listOf("jacare", "jacaré")),
        animal(16, "leao", listOf("leao", "leão")),
        animal(17, "macaco", listOf("macaco")),
        animal(18, "porco", listOf("porco", "suino"), colisao = "carne de porco (Acougue)"),
        animal(19, "pavao", listOf("pavao", "pavão")),
        animal(20, "peru", listOf("peru"), colisao = "peru congelado (Congelados)"),
        animal(21, "touro", listOf("touro", "boi"), colisao = "carne bovina (Acougue)"),
        animal(22, "tigre", listOf("tigre")),
        animal(23, "urso", listOf("urso")),
        animal(
            24, "veado", listOf("veado", "cervo"),
            colisao = "carne de caca",
            sensivel = true,
        ),
        animal(25, "vaca", listOf("vaca"), colisao = "carne bovina, leite (Acougue/Laticinios)"),
    )

    private fun animal(
        valor: Int,
        canonico: String,
        variantes: List<String>,
        colisao: String? = null,
        sensivel: Boolean = false,
    ) = TokenDeQuantidade(
        canonico = canonico,
        variantes = variantes,
        valor = BigDecimal(valor),
        unidade = Unidade.UNIDADE,
        dominio = DominioDoToken.POPULAR,
        origemRepertorio = OrigemRepertorio.ANIMAL,
        // Filtro duro: nome de animal so vira numero onde SO cabe numero.
        posicoesAceitas = setOf(PosicaoAceita.ISOLADO_POSICAO_NUMERICA),
        confiancaBase = Confianca.MEDIA,
        verificacao = Verificacao.CONFIRMADO_EM_FONTE,
        fonte = FONTE_BICHO,
        colisaoItem = colisao,
        sensivel = sensivel,
    )

    // =================================================================================
    // 4. Apelidos NAO animais - repertorio de bingo/vispora
    // =================================================================================

    val apelidosNaoAnimais: List<TokenDeQuantidade> = listOf(
        popular(1, "comecou o jogo", listOf("comecou o jogo", "comecou a partida", "inicio do jogo"), FONTE_BINGO),
        popular(1, "ronco de porco", listOf("ronco de porco"), FONTE_BINGO),
        popular(2, "o unico patinho na lagoa", listOf("o unico patinho na lagoa", "um patinho na lagoa"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(3, "orelha de nico", listOf("orelha de nico", "orelha do nico"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(4, "e tetra", listOf("e tetra", "é tetra"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(4, "quatro cantos da terra", listOf("quatro cantos da terra", "quatro estacoes"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(5, "mao cheia", listOf("mao cheia", "mão cheia"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(6, "pingo na panca", listOf("pingo na panca", "pingo na pança"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(7, "numero do mentiroso", listOf("numero do mentiroso"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(8, "violao sem braco", listOf("violao sem braco", "violão sem braço"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(9, "pingo no pe", listOf("pingo no pe", "pingo no pé"), FONTE_BINGO),
        popular(10, "craque de bola", listOf("craque de bola", "pele na copa", "pelé na copa"), FONTE_BINGO),
        popular(11, "um atras do outro", listOf("um atras do outro", "dois palitos"), FONTE_BINGO),
        popular(11, "casa de bronze", listOf("casa de bronze"), FONTE_BINGO),
        popular(12, "dia das criancas", listOf("dia das criancas", "dia das crianças"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(13, "maria cristina", listOf("maria cristina", "terezinha de jesus"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(13, "numero da sorte", listOf("numero da sorte", "azar e sorte"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(15, "menina moca", listOf("menina moca", "menina moça"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(18, "dos outros", listOf("dos outros"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(20, "peru de natal", listOf("peru de natal"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(21, "ti-um", listOf("ti-um", "ti um"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(22, "dois patinhos na lagoa", listOf("dois patinhos na lagoa", "dois patinho na lagoa", "2 patinhos na lagoa"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(23, "descendente dele", listOf("descendente dele", "o bem pertinho"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(28, "vem torto que eu endireito", listOf("vem torto que eu endireito"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(29, "sao pedro", listOf("sao pedro", "são pedro"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(30, "trinca mas nao racha", listOf("trinca mas nao racha", "trinca mais nao racha"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(33, "idade de cristo", listOf("idade de cristo", "idade do cristo"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(51, "uma boa ideia", listOf("uma boa ideia", "uma boa ideía"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(66, "tapa na orelha", listOf("tapa na orelha"), FONTE_DOCUMENTO, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(70, "a copa", listOf("a copa"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(71, "a bruxa", listOf("a bruxa", "a bruxa vem ai"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(75, "carro do gas", listOf("carro do gas", "carro do gás"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(77, "duas machadinhas", listOf("duas machadinhas", "dois martelos"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(88, "infinito duplo", listOf("infinito duplo", "dois gordos"), FONTE_BINGO_COMENTARIOS, Verificacao.TRADICAO_POPULAR_SEM_FONTE),
        popular(90, "a velha", listOf("a velha", "o anciao", "apita o juiz"), FONTE_BINGO),
    )

    private fun popular(
        valor: Int,
        canonico: String,
        variantes: List<String>,
        fonte: String,
        verificacao: Verificacao = Verificacao.CONFIRMADO_EM_FONTE,
    ) = TokenDeQuantidade(
        canonico = canonico,
        variantes = variantes,
        valor = BigDecimal(valor),
        unidade = Unidade.UNIDADE,
        dominio = DominioDoToken.POPULAR,
        origemRepertorio = OrigemRepertorio.NAO_ANIMAL,
        posicoesAceitas = setOf(
            PosicaoAceita.ISOLADO_POSICAO_NUMERICA,
            PosicaoAceita.ANTES_DO_ITEM_COM_DE,
            PosicaoAceita.FRASE_QUANTITATIVA,
        ),
        // Expressao de varias palavras e inequivoca; palavra unica pede cautela.
        confiancaBase = if (canonico.contains(' ')) Confianca.ALTA else Confianca.MEDIA,
        verificacao = verificacao,
        fonte = fonte,
    )

    // =================================================================================
    // 5. Indice de busca
    // =================================================================================

    val todos: List<TokenDeQuantidade> = coletivos + embalagens + apelidosDeAnimais + apelidosNaoAnimais

    /**
     * variante normalizada -> token, em tabela de hash.
     *
     * Mapa em vez de lista varrida: o parser roda na main thread a cada tecla
     * digitada, e 150 comparacoes de string por palavra estourariam o orcamento
     * de 16 ms do Snapdragon 425. Com hash, cada consulta e O(1).
     */
    private val indice: Map<String, TokenDeQuantidade> = LinkedHashMap<String, TokenDeQuantidade>().apply {
        todos
            .flatMap { token -> (token.variantes + token.canonico).map { TextoUtil.normalizar(it) to token } }
            .filter { it.first.isNotBlank() }
            .sortedByDescending { it.first.split(' ').size * 100 + it.first.length }
            .forEach { (texto, token) -> putIfAbsent(texto, token) }
    }

    /** Quantas palavras tem a maior expressao da tabela (limite da janela de busca). */
    val maiorExpressaoEmPalavras: Int = indice.keys.maxOf { it.count { c -> c == ' ' } + 1 }

    /** Primeira palavra das expressoes com mais de uma palavra. */
    private val iniciaisDeExpressao: Set<String> =
        indice.keys.filter { it.contains(' ') }.mapTo(HashSet()) { it.substringBefore(' ') }

    /** Ultima palavra das expressoes com mais de uma palavra. */
    private val finaisDeExpressao: Set<String> =
        indice.keys.filter { it.contains(' ') }.mapTo(HashSet()) { it.substringAfterLast(' ') }

    /** Busca exata por texto ja normalizado. */
    fun porTexto(textoNormalizado: String): TokenDeQuantidade? = indice[textoNormalizado]

    /**
     * Procura o maior token que casa com o **inicio** de [palavras].
     *
     * @return o token e quantas palavras ele consumiu.
     */
    fun casarNoInicio(palavras: List<String>): Pair<TokenDeQuantidade, Int>? {
        if (palavras.isEmpty()) return null
        // Se a primeira palavra nao inicia nenhuma expressao composta, so a
        // consulta de uma palavra pode dar certo - evita montar n-gramas a toa.
        val limite = if (palavras.first() in iniciaisDeExpressao) {
            minOf(palavras.size, maiorExpressaoEmPalavras)
        } else {
            1
        }
        for (tamanho in limite downTo 1) {
            val trecho = if (tamanho == 1) palavras.first() else palavras.take(tamanho).joinToString(" ")
            porTexto(trecho)?.let { return it to tamanho }
        }
        return null
    }

    /** Procura o maior token que casa com o **fim** de [palavras]. */
    fun casarNoFim(palavras: List<String>): Pair<TokenDeQuantidade, Int>? {
        if (palavras.isEmpty()) return null
        val limite = if (palavras.last() in finaisDeExpressao) {
            minOf(palavras.size, maiorExpressaoEmPalavras)
        } else {
            1
        }
        for (tamanho in limite downTo 1) {
            val trecho = if (tamanho == 1) palavras.last() else palavras.takeLast(tamanho).joinToString(" ")
            porTexto(trecho)?.let { return it to tamanho }
        }
        return null
    }

    /** Numeros de 1 a 90 que **nao** tem apelido documentado (declarado no README). */
    fun numerosSemApelido(): List<Int> {
        val comApelido = (apelidosDeAnimais + apelidosNaoAnimais)
            .map { it.valor.toInt() }
            .toSet()
        return (1..90).filterNot { it in comApelido }
    }

    /** Entradas de origem pejorativa - listadas para revisao, nunca oferecidas pela interface. */
    fun conteudoSensivel(): List<TokenDeQuantidade> = todos.filter { it.sensivel }
}

package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.util.TextoUtil

/**
 * Colisao entre o nome de um item e um apelido numerico popular.
 *
 * Existe para documentar **por que** a palavra nao vira numero: "patinho" e
 * corte bovino, e so vira 22 dentro da expressao completa "dois patinhos na
 * lagoa" seguida de `de` + item.
 */
data class ColisaoDeApelido(
    val valor: Int,
    val expressao: String?,
    val regra: String,
)

/**
 * Item do lexico de supermercado.
 *
 * @param unidadeInterna quantas unidades vem em cada tipo de embalagem
 *   (`"dz" to 12`). **So e preenchido quando existe valor usual documentado** -
 *   quando varia por marca ou regiao, o campo fica vazio e a variacao e
 *   descrita em [variacaoDeclarada]. O parser nunca chuta: sem valor aqui,
 *   `itensPorKit` sai `null`.
 * @param notaDeColisao palavra que tambem e algo diferente no portugues comum
 *   ("lagarto", "coxao", "lingua"), sem ser apelido numerico.
 */
data class ItemDoLexico(
    val nomeCanonico: String,
    val variantes: List<String> = emptyList(),
    val categoria: String,
    val classe: ClasseDeItem,
    val unidadePadrao: Unidade,
    val unidadeInterna: Map<String, Int> = emptyMap(),
    val variacaoDeclarada: String? = null,
    val colisaoApelido: ColisaoDeApelido? = null,
    val notaDeColisao: String? = null,
) {
    /**
     * Nome e variantes ja normalizados, da expressao mais longa para a mais
     * curta. Calculado uma unica vez por item: o parser consulta isso a cada
     * linha digitada, e normalizar texto (NFD + tres regex) e caro.
     */
    val variantesNormalizadas: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (listOf(nomeCanonico) + variantes)
            .map { TextoUtil.normalizar(it) }
            .distinct()
            .sortedByDescending { it.length }
    }
}

/**
 * Lexico de itens de supermercado brasileiro - **a fonte rica do dicionario de
 * categorias** (Secao 4.4 do app).
 *
 * Nao e um segundo dicionario: [br.com.comprix.domain.categoria.DicionarioDeCategorias]
 * mescla estas entradas no mesmo indice de palavras-chave, mantendo a API que o
 * resto do app ja usa. O que o lexico acrescenta ao dicionario antigo e o que o
 * parser precisa e o dicionario nao tinha: **classe** (contavel/pesavel/ambiguo),
 * **unidade padrao** e **colisoes**.
 *
 * Fica em tabela Kotlin, nao em asset JSON - justificativa no README do parser
 * (sem parser de JSON em runtime, sem custo de carga, cabe no orcamento de
 * 16 ms do Moto E5). O JSON continua existindo como artefato de especificacao,
 * gerado a partir destas tabelas.
 */
object LexicoDeItens {

    private fun cont(
        nome: String,
        variantes: List<String> = emptyList(),
        categoria: String,
        unidade: Unidade = Unidade.UNIDADE,
        interna: Map<String, Int> = emptyMap(),
        variacao: String? = null,
        colisao: ColisaoDeApelido? = null,
        nota: String? = null,
    ) = ItemDoLexico(nome, variantes, categoria, ClasseDeItem.CONTAVEL, unidade, interna, variacao, colisao, nota)

    private fun pes(
        nome: String,
        variantes: List<String> = emptyList(),
        categoria: String,
        unidade: Unidade = Unidade.QUILO,
        interna: Map<String, Int> = emptyMap(),
        variacao: String? = null,
        colisao: ColisaoDeApelido? = null,
        nota: String? = null,
    ) = ItemDoLexico(nome, variantes, categoria, ClasseDeItem.PESAVEL, unidade, interna, variacao, colisao, nota)

    private fun amb(
        nome: String,
        variantes: List<String> = emptyList(),
        categoria: String,
        unidade: Unidade = Unidade.QUILO,
        interna: Map<String, Int> = emptyMap(),
        variacao: String? = null,
        colisao: ColisaoDeApelido? = null,
        nota: String? = null,
    ) = ItemDoLexico(nome, variantes, categoria, ClasseDeItem.AMBIGUO, unidade, interna, variacao, colisao, nota)

    /** Regra reaproveitada por todos os cortes bovinos cujo nome e tambem outra coisa. */
    private const val REGRA_EXPRESSAO_COMPLETA =
        "so vira numero com a expressao completa + 'de' + item do lexico"

    // =================================================================================
    // Hortifruti
    // =================================================================================
    private val hortifruti = listOf(
        amb("banana", listOf("bananas", "banana prata", "banana nanica", "banana da terra"), categoria = "hortifruti", interna = mapOf("dz" to 12), variacao = "penca: 6 a 14 bananas, sem valor fixo"),
        amb("maçã", listOf("maca", "macas", "maçãs", "maca gala", "maca fuji"), categoria = "hortifruti"),
        amb("laranja", listOf("laranjas", "laranja pera", "laranja lima"), categoria = "hortifruti", interna = mapOf("dz" to 12)),
        amb("abacate", listOf("abacates"), categoria = "hortifruti"),
        amb("mamão", listOf("mamao", "mamao papaia", "mamao formosa"), categoria = "hortifruti"),
        amb("coco", listOf("coco seco", "coco verde"), categoria = "hortifruti", nota = "coco ralado e item de mercearia"),
        amb("melancia", listOf("melancias"), categoria = "hortifruti"),
        amb("melão", listOf("melao", "melões"), categoria = "hortifruti"),
        amb("abacaxi", listOf("abacaxis", "abacaxi perola"), categoria = "hortifruti"),
        pes("uva", listOf("uvas", "uva thompson", "uva itália"), categoria = "hortifruti"),
        pes("morango", listOf("morangos"), categoria = "hortifruti"),
        pes("manga", listOf("mangas", "manga palmer", "manga tommy"), categoria = "hortifruti"),
        pes("pera", listOf("peras"), categoria = "hortifruti"),
        pes("tomate", listOf("tomates", "tomate italiano", "tomate cereja"), categoria = "hortifruti"),
        pes("cebola", listOf("cebolas", "cebola roxa"), categoria = "hortifruti"),
        pes("batata", listOf("batatas", "batata inglesa"), categoria = "hortifruti"),
        pes("batata-doce", listOf("batata doce"), categoria = "hortifruti"),
        pes("cenoura", listOf("cenouras"), categoria = "hortifruti"),
        pes("beterraba", listOf("beterrabas"), categoria = "hortifruti"),
        pes("mandioca", listOf("aipim", "macaxeira"), categoria = "hortifruti"),
        pes("abóbora", listOf("abobora", "jerimum", "moranga"), categoria = "hortifruti"),
        // Apelidos regionais da tangerina (mesmos sinonimos do catalogo-semente):
        // "1 kg de mexerica" tem que virar o item certo, e nao um novo em Outros.
        amb("tangerina", listOf("tangerinas", "mexerica", "mexericas", "bergamota", "bergamotas"), categoria = "hortifruti"),
        pes("chuchu", listOf("chuchus"), categoria = "hortifruti"),
        pes("pimentão", listOf("pimentao", "pimentão verde"), categoria = "hortifruti"),
        pes("berinjela", listOf("berinjelas"), categoria = "hortifruti"),
        pes("abobrinha", listOf("abobrinhas"), categoria = "hortifruti"),
        pes("limão", listOf("limao", "limoes", "limão taiti"), categoria = "hortifruti"),
        cont("alface", listOf("alfaces", "alface crespa", "alface americana"), categoria = "hortifruti"),
        cont("couve", listOf("couve manteiga"), categoria = "hortifruti"),
        cont("brócolis", listOf("brocolis"), categoria = "hortifruti"),
        cont("repolho", listOf("repolhos"), categoria = "hortifruti"),
        cont("alho", listOf("cabeça de alho", "cabeca de alho"), categoria = "hortifruti"),
        cont("cheiro-verde", listOf("cheiro verde", "salsinha", "cebolinha", "tempero verde"), categoria = "hortifruti"),
        pes("pepino", listOf("pepinos"), categoria = "hortifruti"),
        pes("milho verde", listOf("espiga de milho", "milho na espiga"), categoria = "hortifruti"),
    )

    // =================================================================================
    // Padaria
    // =================================================================================
    private val padaria = listOf(
        cont("pão francês", listOf("pao frances", "pao", "paes", "pão", "pãozinho", "paozinho", "pao careca", "pao de sal"), categoria = "padaria", interna = mapOf("dz" to 12)),
        cont("pão de forma", listOf("pao de forma", "pao forma", "pão de forma integral"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("pão de queijo", listOf("pao de queijo"), categoria = "padaria", unidade = Unidade.QUILO),
        cont("pão doce", listOf("pao doce"), categoria = "padaria"),
        cont("pão integral", listOf("pao integral"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("pão sírio", listOf("pao sirio", "pao arabe", "pão árabe"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("bisnaguinha", listOf("bisnaguinhas", "pao bisnaguinha"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("broa de milho", listOf("broa", "broa de fuba"), categoria = "padaria"),
        cont("croissant", listOf("croissants"), categoria = "padaria"),
        cont("sonho", listOf("sonhos", "sonho de creme"), categoria = "padaria"),
        cont("bolo", listOf("bolos", "bolo de fuba", "bolo de cenoura"), categoria = "padaria"),
        cont("torta", listOf("tortas", "torta salgada"), categoria = "padaria"),
        cont("rosca", listOf("roscas", "rosca de coco"), categoria = "padaria"),
        cont("baguete", listOf("baguetes", "pao baguete"), categoria = "padaria"),
        pes("biscoito de polvilho", listOf("biscoito polvilho", "polvilho assado"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("salgado", listOf("coxinha", "esfiha", "empada", "pastel assado"), categoria = "padaria"),
        cont("torrada", listOf("torradas", "pao torrado"), categoria = "padaria", unidade = Unidade.PACOTE),
        cont("panetone", listOf("panetones", "chocotone"), categoria = "padaria", unidade = Unidade.PACOTE),
    )

    // =================================================================================
    // Acougue e peixaria - concentra as colisoes criticas
    // =================================================================================
    private val acougue = listOf(
        pes("carne", listOf("carne bovina", "carne de boi", "carne vermelha"), categoria = "acougue"),
        pes(
            "patinho", listOf("carne patinho", "patinho moido", "patinho moído"), categoria = "acougue",
            colisao = ColisaoDeApelido(22, "dois patinhos na lagoa", REGRA_EXPRESSAO_COMPLETA),
            nota = "corte bovino; isolado NUNCA vira numero",
        ),
        pes(
            "lagarto", listOf("lagarto bovino", "carne lagarto"), categoria = "acougue",
            nota = "corte bovino. Verificado: 'lagarto' NAO consta entre os 25 grupos do jogo do bicho " +
                "(o reptil do grupo 15 e o jacare) nem no repertorio de bingo levantado - logo nao tem " +
                "apelido numerico e nunca vira numero.",
        ),
        pes("coxão mole", listOf("coxao mole", "coxão-mole", "chã de dentro"), categoria = "acougue", nota = "'coxao' isolado nao e quantidade"),
        pes("coxão duro", listOf("coxao duro", "chã de fora", "cha de fora"), categoria = "acougue"),
        pes("acém", listOf("acem", "carne acem"), categoria = "acougue"),
        pes("maminha", listOf("maminha de alcatra"), categoria = "acougue", nota = "corte bovino; palavra comum fora do acougue"),
        pes("fraldinha", listOf("fraldinhas"), categoria = "acougue", nota = "corte bovino; nao confundir com 'fralda' (Infantil)"),
        pes("alcatra", listOf("alcatras"), categoria = "acougue"),
        pes("picanha", listOf("picanhas"), categoria = "acougue"),
        pes("contrafilé", listOf("contrafile", "contra file", "contra-filé"), categoria = "acougue"),
        pes("filé mignon", listOf("file mignon", "filet mignon"), categoria = "acougue"),
        pes("costela", listOf("costela bovina", "costelinha"), categoria = "acougue"),
        pes("músculo", listOf("musculo", "musculo bovino"), categoria = "acougue"),
        pes("cupim", listOf("cupins"), categoria = "acougue"),
        pes("carne moída", listOf("carne moida", "moida", "carne de panela"), categoria = "acougue"),
        pes("frango", listOf("frangos", "frango inteiro", "galinha"), categoria = "acougue", colisao = ColisaoDeApelido(13, "galo", "apelido de animal: so em posicao estritamente numerica")),
        pes("peito de frango", listOf("peito frango", "file de frango", "filé de frango"), categoria = "acougue"),
        pes("coxa de frango", listOf("coxa", "sobrecoxa", "coxa e sobrecoxa"), categoria = "acougue"),
        pes("asa de frango", listOf("asinha", "asa", "tulipa"), categoria = "acougue"),
        pes("linguiça", listOf("linguica", "linguiça toscana", "linguica calabresa"), categoria = "acougue"),
        pes("bacon", listOf("bacons", "toucinho"), categoria = "acougue"),
        pes("costelinha suína", listOf("costelinha suina", "costela de porco"), categoria = "acougue", colisao = ColisaoDeApelido(18, "porco", "apelido de animal: so em posicao estritamente numerica")),
        pes("lombo suíno", listOf("lombo suino", "lombo de porco", "lombo"), categoria = "acougue"),
        pes("pernil", listOf("pernil suino", "pernil de porco"), categoria = "acougue"),
        pes("carne de carneiro", listOf("carneiro", "cordeiro", "ovelha"), categoria = "acougue", colisao = ColisaoDeApelido(7, "carneiro", "apelido de animal: so em posicao estritamente numerica")),
        pes("cabrito", listOf("carne de cabrito", "bode"), categoria = "acougue", colisao = ColisaoDeApelido(6, "cabra", "apelido de animal: so em posicao estritamente numerica")),
        pes("carne de coelho", listOf("coelho"), categoria = "acougue", colisao = ColisaoDeApelido(10, "coelho", "apelido de animal: so em posicao estritamente numerica")),
        pes("peixe", listOf("peixes", "file de peixe"), categoria = "acougue"),
        pes("tilápia", listOf("tilapia", "file de tilapia"), categoria = "acougue"),
        pes("sardinha fresca", listOf("sardinha"), categoria = "acougue", nota = "sardinha em lata e Mercearia"),
        pes("camarão", listOf("camarao", "camaroes"), categoria = "acougue"),
        pes("bacalhau", listOf("bacalhau dessalgado"), categoria = "acougue"),
        pes("salmão", listOf("salmao", "file de salmao"), categoria = "acougue"),
        pes("língua", listOf("lingua bovina"), categoria = "acougue", nota = "corte bovino; palavra comum"),
        pes("fígado", listOf("figado", "figado bovino"), categoria = "acougue"),
    )

    // =================================================================================
    // Frios e laticinios
    // =================================================================================
    private val laticinios = listOf(
        amb(
            "leite", listOf("leite integral", "leite desnatado", "leite semidesnatado", "leite longa vida", "leite uht"),
            categoria = "laticinios", unidade = Unidade.LITRO, interna = mapOf("cx" to 12),
            variacao = "caixa fechada de leite UHT: 12 unidades de 1 L (padrao do varejo)",
        ),
        amb("leite condensado", listOf("leite moca", "leite moça"), categoria = "laticinios", unidade = Unidade.UNIDADE),
        amb("creme de leite", listOf("creme leite"), categoria = "laticinios", unidade = Unidade.UNIDADE),
        amb("queijo", listOf("queijos"), categoria = "laticinios"),
        pes("queijo mussarela", listOf("mussarela", "muçarela", "mucarela"), categoria = "laticinios"),
        pes("queijo prato", listOf("prato fatiado"), categoria = "laticinios"),
        pes("queijo minas", listOf("minas frescal", "queijo branco"), categoria = "laticinios"),
        pes("queijo coalho", listOf("queijo de coalho"), categoria = "laticinios"),
        pes("queijo parmesão", listOf("parmesao", "queijo ralado"), categoria = "laticinios"),
        pes("queijo de cabra", listOf("queijo cabra", "boursin de cabra"), categoria = "laticinios", colisao = ColisaoDeApelido(6, "cabra", "apelido de animal: so em posicao estritamente numerica")),
        pes("requeijão", listOf("requeijao", "requeijao cremoso"), categoria = "laticinios", unidade = Unidade.UNIDADE),
        pes("presunto", listOf("presunto cozido", "presunto fatiado"), categoria = "laticinios"),
        pes("mortadela", listOf("mortadelas"), categoria = "laticinios"),
        pes("salame", listOf("salaminho"), categoria = "laticinios"),
        pes("peito de peru", listOf("blanquet de peru"), categoria = "laticinios", colisao = ColisaoDeApelido(20, "peru", "apelido de animal: so em posicao estritamente numerica")),
        cont("iogurte", listOf("iogurtes", "iogurte natural", "iogurte grego"), categoria = "laticinios", variacao = "bandeja/pack de iogurte: 4, 6 ou 8 potes conforme a marca - sem valor unico"),
        cont("manteiga", listOf("manteiga com sal", "manteiga sem sal"), categoria = "laticinios"),
        cont("margarina", listOf("margarinas"), categoria = "laticinios"),
        cont("cream cheese", listOf("creamcheese"), categoria = "laticinios"),
        cont("danoninho", listOf("petit suisse", "danoninhos"), categoria = "laticinios"),
        cont("bebida láctea", listOf("bebida lactea"), categoria = "laticinios", unidade = Unidade.LITRO),
        cont("ovo", listOf("ovos", "ovo caipira", "ovos caipiras", "ovo branco", "ovo vermelho", "ovo de galinha"), categoria = "mercearia", unidade = Unidade.DUZIA, interna = mapOf("dz" to 12, "cx" to 30, "bandeja" to 30), variacao = "cartela tambem aparece com 10, 12 ou 20 unidades conforme marca e regiao"),
        pes("ricota", listOf("ricotas"), categoria = "laticinios"),
        pes("catupiry", listOf("queijo catupiry"), categoria = "laticinios"),
    )

    // =================================================================================
    // Congelados
    // =================================================================================
    private val congelados = listOf(
        cont("pizza congelada", listOf("pizza", "pizzas"), categoria = "congelados"),
        cont("lasanha congelada", listOf("lasanha"), categoria = "congelados"),
        cont("nuggets", listOf("nugget", "empanado de frango"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("hambúrguer congelado", listOf("hamburguer", "hamburguer congelado", "burguer"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("batata frita congelada", listOf("batata palito", "batata congelada"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("sorvete", listOf("sorvetes", "pote de sorvete"), categoria = "congelados", unidade = Unidade.LITRO),
        cont("açaí", listOf("acai", "polpa de acai"), categoria = "congelados"),
        cont("polpa de fruta", listOf("polpa", "polpas congeladas"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("pão de queijo congelado", listOf("pao de queijo congelado"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("peru congelado", listOf("peru", "peru de natal", "chester"), categoria = "congelados", colisao = ColisaoDeApelido(20, "peru", "apelido de animal: so em posicao estritamente numerica")),
        cont("ervilha congelada", listOf("ervilha congelada"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("brócolis congelado", listOf("brocolis congelado", "brócolis congelado", "brocolis ultracongelado"), categoria = "congelados", unidade = Unidade.PACOTE),
        cont("pescado congelado", listOf("file congelado", "posta congelada"), categoria = "congelados"),
        cont("massa folhada", listOf("massa de pastel", "massa de lasanha"), categoria = "congelados", unidade = Unidade.PACOTE),
    )

    // =================================================================================
    // Mercearia e secos
    // =================================================================================
    private val mercearia = listOf(
        pes("arroz", listOf("arroz branco", "arroz agulhinha", "arroz parboilizado", "arroz tipo 1"), categoria = "mercearia", interna = mapOf("fardo" to 10), variacao = "fardo de arroz: 10 pacotes de 1 kg; tambem circula 6 x 5 kg"),
        pes("arroz integral", listOf("arroz integral"), categoria = "mercearia"),
        pes("feijão", listOf("feijao", "feijao carioca", "feijão preto", "feijao preto"), categoria = "mercearia", interna = mapOf("fardo" to 10)),
        pes("açúcar", listOf("acucar", "açucar refinado", "acucar cristal", "açúcar mascavo"), categoria = "mercearia"),
        pes("sal", listOf("sal refinado", "sal grosso"), categoria = "mercearia"),
        pes("café", listOf("cafe", "cafe em po", "café torrado", "cafe moido"), categoria = "mercearia"),
        pes("farinha de trigo", listOf("farinha trigo", "farinha"), categoria = "mercearia"),
        pes("farinha de mandioca", listOf("farinha mandioca", "farofa pronta"), categoria = "mercearia"),
        pes("fubá", listOf("fuba", "fuba de milho"), categoria = "mercearia"),
        pes("macarrão", listOf("macarrao", "espaguete", "macarrao parafuso", "penne", "talharim"), categoria = "mercearia", unidade = Unidade.PACOTE, nota = "'macarrao parafuso' leva o formato junto; 'parafuso' sozinho e ferragem (Bazar)"),
        cont("macarrão instantâneo", listOf("miojo", "macarrao instantaneo", "lamen"), categoria = "mercearia", variacao = "pack de 5 unidades e o mais comum, mas ha 3 e 6 - sem valor unico"),
        pes("óleo de soja", listOf("oleo", "oleo de soja", "óleo"), categoria = "mercearia", unidade = Unidade.UNIDADE, interna = mapOf("cx" to 20), variacao = "caixa fechada de oleo: 20 garrafas de 900 mL (padrao do atacado)"),
        cont("azeite", listOf("azeite de oliva", "azeite extra virgem"), categoria = "mercearia"),
        cont("vinagre", listOf("vinagre de alcool", "vinagre de maca"), categoria = "mercearia"),
        cont("molho de tomate", listOf("molho tomate", "extrato de tomate", "polpa de tomate"), categoria = "mercearia", nota = "'molho' aqui e produto, nao embalagem"),
        cont("maionese", listOf("maioneses"), categoria = "mercearia"),
        cont("ketchup", listOf("catchup", "catsup"), categoria = "mercearia"),
        cont("mostarda", listOf("mostardas"), categoria = "mercearia"),
        cont("sardinha em lata", listOf("sardinha em lata", "sardinha enlatada"), categoria = "mercearia"),
        cont("atum em lata", listOf("atum", "atum ralado"), categoria = "mercearia"),
        cont("milho em conserva", listOf("milho em lata", "milho verde em conserva", "milho"), categoria = "mercearia"),
        cont("ervilha em conserva", listOf("ervilha em lata"), categoria = "mercearia"),
        cont("seleta de legumes", listOf("seleta", "seleta de legumes congelada"), categoria = "mercearia"),
        cont("palmito", listOf("palmito em conserva"), categoria = "mercearia"),
        cont("azeitona", listOf("azeitonas", "azeitona verde"), categoria = "mercearia"),
        pes("biscoito", listOf("biscoitos", "bolacha", "bolachas"), categoria = "mercearia", unidade = Unidade.PACOTE),
        cont("biscoito recheado", listOf("bolacha recheada"), categoria = "mercearia", unidade = Unidade.PACOTE),
        cont("biscoito cream cracker", listOf("cream cracker", "agua e sal", "água e sal"), categoria = "mercearia", unidade = Unidade.PACOTE),
        cont("achocolatado", listOf("achocolatado em po", "nescau", "toddy"), categoria = "mercearia"),
        cont("leite em pó", listOf("leite em po", "leite ninho"), categoria = "mercearia"),
        cont("chá", listOf("cha", "cha mate", "chá de camomila"), categoria = "mercearia", unidade = Unidade.PACOTE),
        pes("aveia", listOf("aveia em flocos", "farelo de aveia"), categoria = "mercearia"),
        pes("granola", listOf("granolas"), categoria = "mercearia"),
        cont("cereal matinal", listOf("sucrilhos", "cereal"), categoria = "mercearia", unidade = Unidade.PACOTE),
        pes("amendoim", listOf("amendoins", "amendoim torrado"), categoria = "mercearia"),
        pes("castanha", listOf("castanha de caju", "castanha do para", "castanha-do-pará"), categoria = "mercearia"),
        pes("lentilha", listOf("lentilhas"), categoria = "mercearia"),
        pes("grão-de-bico", listOf("grao de bico", "grao-de-bico"), categoria = "mercearia"),
        pes("ervilha seca", listOf("ervilha partida"), categoria = "mercearia"),
        pes("canjica", listOf("canjicas", "mungunzá"), categoria = "mercearia"),
        pes("polvilho", listOf("polvilho doce", "polvilho azedo"), categoria = "mercearia"),
        pes("tapioca", listOf("goma de tapioca"), categoria = "mercearia"),
        cont("fermento", listOf("fermento em po", "fermento biologico"), categoria = "mercearia"),
        cont("gelatina", listOf("gelatinas", "po para gelatina"), categoria = "mercearia"),
        cont("tempero pronto", listOf("tempero", "caldo de galinha", "colorau", "cominho", "oregano", "orégano", "pimenta do reino"), categoria = "mercearia"),
        cont("chocolate", listOf("barra de chocolate", "chocolates"), categoria = "mercearia"),
        cont("bombom", listOf("bombons", "caixa de bombom"), categoria = "mercearia"),
        cont("bala", listOf("balas", "pacote de bala"), categoria = "mercearia", unidade = Unidade.PACOTE),
        cont("chiclete", listOf("chicletes", "goma de mascar"), categoria = "mercearia"),
        pes("salgadinho", listOf("salgadinhos", "batata chips"), categoria = "mercearia", unidade = Unidade.PACOTE),
        pes("pipoca", listOf("milho de pipoca", "pipoca de micro-ondas"), categoria = "mercearia"),
        cont("geleia", listOf("geleias", "geleia de morango"), categoria = "mercearia"),
        cont("doce de leite", listOf("doce leite"), categoria = "mercearia"),
        cont("mel", listOf("mel de abelha"), categoria = "mercearia"),
        cont("creme de avelã", listOf("creme de avela", "nutella"), categoria = "mercearia"),
        cont("amido de milho", listOf("maisena", "amido milho"), categoria = "mercearia"),
        cont("coco ralado", listOf("coco ralado"), categoria = "mercearia", unidade = Unidade.PACOTE),
        cont("sopa instantânea", listOf("sopa instantanea", "creme de cebola"), categoria = "mercearia"),
        cont("proteína de soja", listOf("proteina de soja", "carne de soja"), categoria = "mercearia", unidade = Unidade.PACOTE),
    )

    // =================================================================================
    // Organicos
    // =================================================================================
    private val organicos = listOf(
        amb("alface orgânica", listOf("alface organica"), categoria = "organicos", unidade = Unidade.UNIDADE),
        amb("tomate orgânico", listOf("tomate organico"), categoria = "organicos"),
        amb("banana orgânica", listOf("banana organica"), categoria = "organicos"),
        pes("arroz orgânico", listOf("arroz organico"), categoria = "organicos"),
        pes("feijão orgânico", listOf("feijao organico"), categoria = "organicos"),
        cont("ovo orgânico", listOf("ovo organico", "ovos organicos"), categoria = "organicos", unidade = Unidade.DUZIA, interna = mapOf("dz" to 12)),
        cont("leite orgânico", listOf("leite organico"), categoria = "organicos", unidade = Unidade.LITRO),
        pes("café orgânico", listOf("cafe organico"), categoria = "organicos"),
        pes("açúcar demerara", listOf("acucar demerara", "açucar mascavo organico"), categoria = "organicos"),
    )

    // =================================================================================
    // Bebidas
    // =================================================================================
    private val bebidas = listOf(
        cont("água mineral", listOf("agua mineral", "agua", "água"), categoria = "bebidas", unidade = Unidade.LITRO, variacao = "fardo de agua: 12 x 500 mL ou 6 x 1,5 L conforme a marca"),
        cont("água com gás", listOf("agua com gas"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("refrigerante", listOf("refri", "refrigerantes", "coca cola", "guarana", "guaraná"), categoria = "bebidas", unidade = Unidade.LITRO, variacao = "fardo de refrigerante lata: 12 unidades de 350 mL; ha packs de 6 e 15"),
        cont("cerveja", listOf("cervejas", "breja", "cerveja lata"), categoria = "bebidas", unidade = Unidade.UNIDADE, variacao = "fardo de cerveja: 12 latas de 350 mL e o mais comum; existem 6, 8 e 15"),
        cont("suco", listOf("sucos", "suco de caixinha", "suco pronto"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("suco em pó", listOf("suco em po", "refresco em po"), categoria = "bebidas", unidade = Unidade.PACOTE),
        cont("néctar", listOf("nectar de fruta"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("vinho", listOf("vinhos", "vinho tinto", "vinho branco"), categoria = "bebidas"),
        cont("espumante", listOf("espumantes", "champanhe"), categoria = "bebidas"),
        cont("cachaça", listOf("cachaca", "pinga", "aguardente"), categoria = "bebidas"),
        cont("vodca", listOf("vodka"), categoria = "bebidas"),
        cont("whisky", listOf("uisque", "whiskey"), categoria = "bebidas"),
        cont("energético", listOf("energetico", "energeticos"), categoria = "bebidas"),
        cont("isotônico", listOf("isotonico", "gatorade"), categoria = "bebidas"),
        cont("água de coco", listOf("agua de coco"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("chá gelado", listOf("cha gelado", "cha pronto"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("leite de coco", listOf("leite coco"), categoria = "bebidas", nota = "nao e laticinio: o dicionario ja trata 'leite de coco' antes de 'leite'"),
        cont("café solúvel", listOf("cafe soluvel", "cafe instantaneo"), categoria = "bebidas"),
        cont("capuccino", listOf("cappuccino", "capuchino"), categoria = "bebidas"),
        cont("vinho do porto", listOf("vinho porto"), categoria = "bebidas"),
        cont("gelo", listOf("gelo em cubo", "saco de gelo"), categoria = "bebidas", unidade = Unidade.PACOTE),
        cont("tônica", listOf("tonica", "agua tonica"), categoria = "bebidas", unidade = Unidade.LITRO),
        cont("licor", listOf("licores"), categoria = "bebidas"),
        cont("rum", listOf("runs"), categoria = "bebidas"),
    )

    // =================================================================================
    // Limpeza
    // =================================================================================
    private val limpeza = listOf(
        cont("detergente", listOf("detergentes", "detergente liquido"), categoria = "limpeza"),
        cont("sabão em pó", listOf("sabao em po", "sabao po", "omo"), categoria = "limpeza", unidade = Unidade.PACOTE),
        cont("sabão líquido", listOf("sabao liquido", "lava roupas liquido"), categoria = "limpeza", unidade = Unidade.LITRO),
        cont("sabão em barra", listOf("sabao em barra", "sabao de coco"), categoria = "limpeza", unidade = Unidade.UNIDADE),
        cont("amaciante", listOf("amaciantes", "amaciante de roupa"), categoria = "limpeza", unidade = Unidade.LITRO),
        cont("água sanitária", listOf("agua sanitaria", "candida", "cândida", "cloro"), categoria = "limpeza", unidade = Unidade.LITRO),
        cont("desinfetante", listOf("desinfetantes", "pinho"), categoria = "limpeza", unidade = Unidade.LITRO),
        cont("multiuso", listOf("limpador multiuso", "veja"), categoria = "limpeza"),
        cont("limpa vidros", listOf("limpa vidro"), categoria = "limpeza"),
        cont("lustra móveis", listOf("lustra moveis"), categoria = "limpeza"),
        cont("esponja de aço", listOf("esponja de aco", "bombril", "palha de aco"), categoria = "limpeza", unidade = Unidade.PACOTE),
        cont("esponja", listOf("esponjas", "esponja de louca"), categoria = "limpeza"),
        cont("pano de chão", listOf("pano de chao", "rodo pano"), categoria = "limpeza"),
        cont("pano de prato", listOf("pano prato"), categoria = "limpeza"),
        cont("saco de lixo", listOf("sacos de lixo", "saco para lixo"), categoria = "limpeza", unidade = Unidade.PACOTE, variacao = "rolo/pacote com 10, 15, 20 ou 30 sacos conforme o tamanho"),
        cont("vassoura", listOf("vassouras"), categoria = "limpeza"),
        cont("rodo", listOf("rodos"), categoria = "limpeza"),
        cont("balde", listOf("baldes"), categoria = "limpeza"),
        cont("luva de limpeza", listOf("luva de borracha", "luvas"), categoria = "limpeza"),
        cont("inseticida", listOf("inseticidas", "mata mosquito"), categoria = "limpeza"),
        cont("odorizador", listOf("odorizador de ambiente", "aromatizador"), categoria = "limpeza"),
        cont("tira manchas", listOf("tira-manchas", "alvejante"), categoria = "limpeza"),
        cont("limpa forno", listOf("desengordurante"), categoria = "limpeza"),
        cont("papel toalha", listOf("papel-toalha", "toalha de papel"), categoria = "limpeza", unidade = Unidade.PACOTE),
    )

    // =================================================================================
    // Higiene e beleza
    // =================================================================================
    private val higiene = listOf(
        cont("papel higiênico", listOf("papel higienico", "papel"), categoria = "higiene", unidade = Unidade.PACOTE, variacao = "pacote com 4, 8, 12, 16 ou 24 rolos - sem valor unico"),
        cont("sabonete", listOf("sabonetes", "sabonete liquido"), categoria = "higiene"),
        cont("shampoo", listOf("xampu", "shampoos"), categoria = "higiene"),
        cont("condicionador", listOf("condicionadores"), categoria = "higiene"),
        cont("creme dental", listOf("pasta de dente", "pasta dental", "dentifricio"), categoria = "higiene"),
        cont("escova de dente", listOf("escova dental", "escova de dentes"), categoria = "higiene"),
        cont("fio dental", listOf("fita dental"), categoria = "higiene"),
        cont("enxaguante bucal", listOf("antisseptico bucal", "listerine"), categoria = "higiene"),
        cont("desodorante", listOf("desodorantes", "antitranspirante"), categoria = "higiene"),
        cont("absorvente", listOf("absorventes", "absorvente intimo"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("lâmina de barbear", listOf("lamina de barbear", "aparelho de barbear", "gilete"), categoria = "higiene"),
        cont("espuma de barbear", listOf("creme de barbear"), categoria = "higiene"),
        cont("hidratante", listOf("creme hidratante", "locao hidratante"), categoria = "higiene"),
        cont("protetor solar", listOf("filtro solar"), categoria = "higiene"),
        cont("algodão", listOf("algodao", "disco de algodao"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("cotonete", listOf("cotonetes", "haste flexivel"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("lenço umedecido", listOf("lenco umedecido", "lencos umedecidos"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("lenço de papel", listOf("lenco de papel"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("talco", listOf("talcos"), categoria = "higiene"),
        cont("cortador de unha", listOf("cortador de unhas", "alicate de unha"), categoria = "higiene"),
        cont("tintura de cabelo", listOf("tinta de cabelo", "coloracao"), categoria = "higiene"),
        cont("gel de cabelo", listOf("gel capilar"), categoria = "higiene"),
        cont("preservativo", listOf("camisinha"), categoria = "higiene", unidade = Unidade.PACOTE),
        cont("máscara facial", listOf("mascara facial"), categoria = "higiene"),
    )

    // =================================================================================
    // Infantil
    // =================================================================================
    private val infantil = listOf(
        cont("fralda descartável", listOf("fralda", "fraldas", "fralda descartavel"), categoria = "infantil", unidade = Unidade.PACOTE, variacao = "pacote com 8 a 70 fraldas conforme tamanho e linha - sem valor unico", nota = "nao confundir com 'fraldinha' (corte bovino)"),
        cont("fórmula infantil", listOf("formula infantil", "leite infantil", "nan", "aptamil"), categoria = "infantil"),
        cont("papinha", listOf("papinhas", "papinha de bebe"), categoria = "infantil"),
        cont("mamadeira", listOf("mamadeiras"), categoria = "infantil"),
        cont("chupeta", listOf("chupetas"), categoria = "infantil"),
        cont("pomada para assadura", listOf("pomada de assadura", "hipoglos"), categoria = "infantil"),
        cont("sabonete infantil", listOf("sabonete de bebe"), categoria = "infantil"),
        cont("shampoo infantil", listOf("shampoo de bebe", "xampu infantil"), categoria = "infantil"),
        cont("toalha umedecida", listOf("lenco umedecido infantil"), categoria = "infantil", unidade = Unidade.PACOTE),
        cont("cereal infantil", listOf("mucilon", "farinha lactea"), categoria = "infantil"),
    )

    // =================================================================================
    // Pet
    // =================================================================================
    private val pet = listOf(
        pes(
            "ração para cachorro", listOf("racao para cachorro", "racao de cachorro", "racao canina", "ração de cachorro"),
            categoria = "pet",
            colisao = ColisaoDeApelido(5, "cachorro", "apelido de animal: so em posicao estritamente numerica"),
            nota = "caso-prova da Regra A: 'racao para cachorro' e item Pet, nunca 5",
        ),
        pes("ração para gato", listOf("racao para gato", "racao de gato", "racao felina"), categoria = "pet", colisao = ColisaoDeApelido(14, "gato", "apelido de animal: so em posicao estritamente numerica")),
        cont("areia sanitária", listOf("areia sanitaria", "areia para gato"), categoria = "pet", unidade = Unidade.PACOTE),
        cont("sachê para pet", listOf("sache para cachorro", "sache para gato", "sachê"), categoria = "pet"),
        cont("petisco para pet", listOf("petisco canino", "bifinho", "osso para cachorro"), categoria = "pet", unidade = Unidade.PACOTE),
        cont("tapete higiênico", listOf("tapete higienico"), categoria = "pet", unidade = Unidade.PACOTE),
        cont("antipulgas", listOf("anti pulgas", "coleira antipulgas"), categoria = "pet"),
        cont("shampoo para pet", listOf("shampoo de cachorro"), categoria = "pet"),
        pes("ração para passarinho", listOf("racao para passaro", "alpiste"), categoria = "pet"),
        pes("ração para peixe", listOf("racao de peixe"), categoria = "pet"),
    )

    // =================================================================================
    // Bazar e utilidades
    // =================================================================================
    private val bazar = listOf(
        cont("pilha", listOf("pilhas", "pilha aa", "pilha aaa"), categoria = "bazar", unidade = Unidade.PACOTE, variacao = "cartela com 2, 4 ou 8 pilhas"),
        cont("lâmpada", listOf("lampada", "lampada led"), categoria = "bazar"),
        cont("vela", listOf("velas"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("fósforo", listOf("fosforo", "caixa de fosforo"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("isqueiro", listOf("isqueiros"), categoria = "bazar"),
        cont("papel alumínio", listOf("papel aluminio", "papel-alumínio"), categoria = "bazar"),
        cont("filme plástico", listOf("filme plastico", "plastico filme", "rolopac"), categoria = "bazar"),
        cont("guardanapo", listOf("guardanapos"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("copo descartável", listOf("copo descartavel", "copo plastico"), categoria = "bazar", unidade = Unidade.PACOTE, variacao = "pacote com 50 ou 100 copos conforme o tamanho"),
        cont("prato descartável", listOf("prato descartavel"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("talher descartável", listOf("talher descartavel", "garfo descartavel"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("palito de dente", listOf("palito", "palitos de dente"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("canudo", listOf("canudos"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("parafuso", listOf("parafusos"), categoria = "bazar", nota = "falso positivo classico do coletivo 'par'"),
        cont("pano multiuso", listOf("flanela", "pano perfex"), categoria = "bazar", unidade = Unidade.PACOTE),
        cont("meia", listOf("meias", "meia-calça", "meia calca", "par de meias"), categoria = "bazar", nota = "falso positivo classico de 'meia' (6 / 0,5)"),
    )

    // =================================================================================
    // Indice
    // =================================================================================

    /** Todos os itens, na ordem das 14 categorias. */
    val itens: List<ItemDoLexico> = hortifruti + padaria + acougue + laticinios + congelados +
        mercearia + organicos + bebidas + limpeza + higiene + infantil + pet + bazar

    /**
     * nome normalizado -> item, em tabela de hash.
     *
     * A busca e por n-gramas (ver [encontrarNoTexto]): varrer 700 entradas a
     * cada palavra digitada nao caberia nos 16 ms do aparelho-alvo.
     */
    private val indice: Map<String, ItemDoLexico> = LinkedHashMap<String, ItemDoLexico>().apply {
        itens
            .flatMap { item ->
                (listOf(item.nomeCanonico) + item.variantes).map { TextoUtil.normalizar(it) to item }
            }
            .filter { it.first.isNotBlank() }
            .sortedByDescending { it.first.length }
            .forEach { (texto, item) -> putIfAbsent(texto, item) }
    }

    /** Maior nome catalogado, em palavras - limita a janela de busca. */
    private val maiorEmPalavras: Int = indice.keys.maxOf { it.count { c -> c == ' ' } + 1 }

    /** Pares (palavra-chave, categoria) para alimentar o dicionario da Secao 4.4. */
    val palavrasPorCategoria: List<Pair<String, String>> =
        indice.map { (palavra, item) -> palavra to item.categoria }

    /** Busca exata por nome ou variante. */
    fun porNome(texto: String): ItemDoLexico? {
        val normalizado = TextoUtil.normalizar(texto)
        if (normalizado.isBlank()) return null
        return indice[normalizado]
    }

    /**
     * Busca o item do lexico mencionado em [texto] (casamento pela expressao
     * mais longa: "leite de coco" vence "leite", "pao de queijo" vence "pao").
     */
    fun encontrarNoTexto(texto: String): ItemDoLexico? {
        val normalizado = TextoUtil.normalizar(texto)
        if (normalizado.isBlank()) return null
        buscarPorNgrama(normalizado)?.let { return it }
        // Ultima tentativa: plural simples que nao esta catalogado como variante
        // ("salgados" -> "salgado"). So depois da busca exata, para nao atropelar
        // nomes que terminam em "s" por natureza ("cuscuz", "arroz").
        val singular = singularizar(normalizado)
        if (singular != normalizado) return buscarPorNgrama(singular)
        return null
    }

    /**
     * Procura o maior trecho catalogado dentro do texto.
     *
     * Em vez de perguntar "o texto contem X?" para cada uma das 700 entradas,
     * pergunta "este trecho esta no indice?" para cada n-grama do texto - sao
     * poucas consultas O(1), e o resultado e o mesmo: vence o nome mais longo
     * ("leite de coco" antes de "leite").
     */
    private fun buscarPorNgrama(textoNormalizado: String): ItemDoLexico? {
        val palavras = textoNormalizado.split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return null
        val maior = minOf(palavras.size, maiorEmPalavras)
        for (tamanho in maior downTo 1) {
            for (inicio in 0..(palavras.size - tamanho)) {
                val trecho = palavras.subList(inicio, inicio + tamanho).joinToString(" ")
                indice[trecho]?.let { return it }
            }
        }
        return null
    }

    /** Plural regular do portugues, apenas para busca ("ovos" -> "ovo"). */
    private fun singularizar(textoNormalizado: String): String =
        textoNormalizado.split(' ').joinToString(" ") { palavra ->
            when {
                palavra.length <= 3 -> palavra
                palavra.endsWith("oes") -> palavra.dropLast(3) + "ao"
                palavra.endsWith("aes") -> palavra.dropLast(3) + "ao"
                palavra.endsWith("eis") -> palavra.dropLast(3) + "el"
                palavra.endsWith("ns") -> palavra.dropLast(2) + "m"
                palavra.endsWith("res") || palavra.endsWith("zes") -> palavra.dropLast(2)
                palavra.endsWith("s") -> palavra.dropLast(1)
                else -> palavra
            }
        }

    /** Classe semantica do item, para a validacao da Secao 7.3. */
    fun classeDe(texto: String): ClasseDeItem? = encontrarNoTexto(texto)?.classe

    /** Quantidade de itens por categoria - usado no teste de equilibrio do lexico. */
    fun contagemPorCategoria(): Map<String, Int> =
        itens.groupingBy { it.categoria }.eachCount()

    /** Itens cujo nome colide com apelido numerico popular. */
    fun colisoes(): List<ItemDoLexico> = itens.filter { it.colisaoApelido != null }

    val total: Int get() = itens.size
}

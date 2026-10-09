package br.com.comprix.domain.categoria

import br.com.comprix.domain.parser.LexicoDeItens
import br.com.comprix.util.TextoUtil

/**
 * Dicionario de palavras-chave que liga um nome de produto a uma secao do
 * mercado. E a primeira metade da categorizacao hibrida da Secao 4.4 - a
 * segunda e a memoria do que o usuario corrigiu (ver [CategorizadorAutomatico]).
 *
 * O casamento e pela palavra MAIS LONGA encontrada: "creme dental" vence
 * "creme", "leite de coco" vence "leite". Sem isso, pasta de dente iria parar
 * em laticinios.
 */
object DicionarioDeCategorias {

    /** Categoria usada quando nada e reconhecido. */
    const val CHAVE_PADRAO = "outros"

    /** chave da categoria -> palavras-chave que apontam para ela. */
    val dicionario: Map<String, List<String>> = mapOf(
        "hortifruti" to listOf(
            "alface", "tomate", "cebola", "batata", "batata doce", "cenoura", "banana", "maca",
            "laranja", "limao", "mamao", "melancia", "abacaxi", "uva", "manga", "abacate", "pera",
            "morango", "kiwi", "goiaba", "melao", "mexerica", "tangerina", "pessego", "ameixa",
            "maracuja", "abobora", "abobrinha", "beterraba", "brocolis", "couve", "couve flor",
            "repolho", "pepino", "pimentao", "berinjela", "chuchu", "quiabo", "vagem", "mandioca",
            "aipim", "inhame", "alho", "gengibre", "salsa", "salsinha", "cebolinha", "coentro",
            "manjericao", "rucula", "agriao", "espinafre", "acelga", "milho verde", "cogumelo",
            "champignon", "hortela", "tempero verde", "verdura", "legume", "fruta", "cheiro verde",
            "pimenta", "nabo", "rabanete", "jilo", "maxixe", "caqui", "figo", "acerola",
        ),
        "padaria" to listOf(
            "pao", "pao frances", "pao de forma", "pao integral", "pao de queijo", "baguete",
            "bisnaga", "croissant", "broa", "bolo", "rosca", "sonho", "torrada", "panetone",
            "massa folhada", "folhado", "cuca", "bolo de fuba", "pao doce", "pao sirio",
        ),
        "acougue" to listOf(
            "carne", "picanha", "alcatra", "patinho", "coxao", "acem", "musculo", "costela",
            "file", "file mignon", "fraldinha", "maminha", "contra file", "cupim", "linguica",
            "salsicha", "frango", "coxa", "sobrecoxa", "peito de frango", "asa de frango",
            "peru", "chester", "bacon", "bisteca", "lombo", "pernil", "costelinha", "carne moida",
            "hamburguer", "carne seca", "charque", "tilapia", "salmao", "sardinha fresca",
            "camarao", "bife", "coracao", "figado", "panceta", "miudos", "peixe",
        ),
        "laticinios" to listOf(
            "leite", "queijo", "mussarela", "queijo prato", "queijo minas",
            "parmesao", "requeijao", "iogurte", "manteiga", "margarina", "creme de leite",
            "leite condensado", "nata", "ricota", "catupiry", "presunto", "mortadela", "salame",
            "peito de peru", "apresuntado", "cream cheese", "coalhada", "bebida lactea",
            "doce de leite", "provolone", "cheddar", "queijo coalho", "gorgonzola", "frios",
        ),
        "congelados" to listOf(
            "congelado", "congelada", "nuggets", "pizza congelada", "lasanha", "sorvete", "acai",
            "polpa de fruta", "empanado", "batata congelada", "ervilha congelada", "petit gateau",
            "picole", "escondidinho", "torta congelada", "massa congelada",
        ),
        "mercearia" to listOf(
            "arroz", "feijao", "macarrao", "espaguete", "farinha", "farinha de trigo", "fuba",
            "acucar", "sal", "oleo", "azeite", "vinagre", "molho de tomate", "extrato de tomate",
            "ketchup", "mostarda", "maionese", "cafe", "achocolatado", "leite em po", "cereal",
            "aveia", "granola", "biscoito", "bolacha", "salgadinho", "chocolate", "bala",
            "chiclete", "amendoim", "castanha", "azeitona", "milho em conserva", "ervilha",
            "sardinha em lata", "atum", "lentilha", "grao de bico", "tempero", "caldo", "colorau",
            "oregano", "canela", "cravo", "gelatina", "pudim", "fermento", "polvilho", "tapioca",
            "rapadura", "mel", "geleia", "pasta de amendoim", "creme de avela", "sopa", "miojo",
            "lamen", "farofa", "pipoca", "leite de coco", "coco ralado", "amido de milho",
            "maizena", "bicarbonato", "shoyu", "molho ingles", "curry", "paprica", "cuscuz",
            "quinoa", "chia", "linhaca", "granulado", "cobertura", "leite de amendoas",
        ),
        "bebidas" to listOf(
            "agua", "agua mineral", "agua com gas", "refrigerante", "coca cola", "guarana",
            "pepsi", "fanta", "sprite", "suco", "nectar", "cerveja", "vinho", "cachaca", "vodka",
            "whisky", "energetico", "isotonico", "cha", "cha gelado", "agua de coco", "tonica",
            "soda", "espumante", "licor", "rum", "gin", "kombucha", "refresco", "xarope",
        ),
        "limpeza" to listOf(
            "detergente", "sabao", "sabao em po", "sabao liquido", "amaciante", "agua sanitaria",
            "desinfetante", "limpa vidro", "multiuso", "cloro", "alvejante", "esponja", "bucha",
            "saco de lixo", "papel toalha", "pano de chao", "rodo", "vassoura", "lustra moveis",
            "desengordurante", "tira manchas", "cera", "inseticida", "naftalina", "flanela",
            "luva de limpeza", "limpador", "sapolio", "odorizador",
        ),
        "higiene" to listOf(
            "sabonete", "shampoo", "xampu", "condicionador", "creme dental", "pasta de dente",
            "escova de dente", "fio dental", "enxaguante bucal", "desodorante", "papel higienico",
            "absorvente", "hidratante", "protetor solar", "creme de barbear", "gilete", "lamina",
            "algodao", "cotonete", "perfume", "talco", "creme de pentear", "oleo capilar",
            "alcool em gel", "repelente", "antisseptico", "mascara facial", "aparelho de barbear",
            "escova de cabelo", "cortador de unha", "esmalte", "acetona", "preservativo",
        ),
        "pet" to listOf(
            "racao", "petisco", "areia higienica", "osso para cachorro", "sache pet",
            "antipulgas", "brinquedo pet", "tapete higienico", "coleira", "shampoo pet",
            "ossinho", "comedouro", "bebedouro", "racao gato", "racao cachorro",
        ),
        "infantil" to listOf(
            "fralda", "lenco umedecido", "papinha", "formula infantil", "mamadeira", "chupeta",
            "pomada para assadura", "shampoo infantil", "sabonete infantil", "leite infantil",
            "mucilon", "cereal infantil", "danoninho", "petit suisse", "talco infantil",
        ),
        "bazar" to listOf(
            "pilha", "lampada", "vela", "fosforo", "isqueiro", "extensao", "tomada", "prego",
            "fita adesiva", "cola", "caderno", "caneta", "lapis", "borracha", "guardanapo",
            "copo descartavel", "prato descartavel", "talher", "filme plastico", "papel aluminio",
            "papel manteiga", "pote", "panela", "garrafa termica", "caneca", "abridor", "tesoura",
            "cabide", "balde", "bacia", "palito de dente", "canudo", "forma de gelo", "pano de prato",
        ),
        "organicos" to listOf(
            "organico", "organica", "organicos", "organicas", "agroecologico", "sem agrotoxico",
        ),
    )

    /**
     * Indice plano (palavra-chave -> categoria) ordenado da mais longa para a
     * mais curta.
     *
     * Reune **duas** fontes num unico indice - nao existe segundo dicionario no
     * app:
     * 1. [dicionario], as palavras-chave soltas do varejo ("detergente",
     *    "fralda"), que nao precisam virar item do lexico;
     * 2. [LexicoDeItens.palavrasPorCategoria], o lexico de 300+ itens do parser
     *    de texto livre, que traz tambem classe e unidade padrao.
     *
     * Quando a mesma palavra aparece nas duas, vence a do lexico: ela e a
     * entrada rica, com classe semantica e colisoes documentadas.
     */
    private val indiceOrdenado: Map<String, String> = LinkedHashMap<String, String>().apply {
        (
            LexicoDeItens.palavrasPorCategoria +
                dicionario.flatMap { (categoria, palavras) ->
                    palavras.map { TextoUtil.normalizar(it) to categoria }
                }
            )
            .sortedByDescending { it.first.length }
            .forEach { (palavra, categoria) -> putIfAbsent(palavra, categoria) }
    }

    /** Maior chave do indice, em palavras - limita a janela de busca por n-grama. */
    private val maiorEmPalavras: Int = indiceOrdenado.keys.maxOf { it.count { c -> c == ' ' } + 1 }

    /**
     * Sugere a chave de categoria para um nome de produto.
     *
     * @param nome texto cru digitado pelo usuario ou lido por OCR.
     * @return chave da categoria ("hortifruti", "mercearia", ...) ou
     * [CHAVE_PADRAO] quando nada e reconhecido.
     */
    fun sugerirChave(nome: String): String {
        val normalizado = TextoUtil.normalizar(nome)
        if (normalizado.isBlank()) return CHAVE_PADRAO
        val palavras = normalizado.split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return CHAVE_PADRAO
        // Consulta por n-grama, do trecho mais longo para o mais curto: mesma
        // regra de antes ("creme dental" vence "creme"), mas em tempo O(1) por
        // consulta em vez de varrer o indice inteiro.
        val maior = minOf(palavras.size, maiorEmPalavras)
        for (tamanho in maior downTo 1) {
            for (inicio in 0..(palavras.size - tamanho)) {
                val trecho = palavras.subList(inicio, inicio + tamanho).joinToString(" ")
                indiceOrdenado[trecho]?.let { return it }
            }
        }
        return CHAVE_PADRAO
    }

    /** Quantas palavras-chave o dicionario conhece (exibido em "Sobre" e usado em teste). */
    val totalDePalavras: Int get() = indiceOrdenado.size
}

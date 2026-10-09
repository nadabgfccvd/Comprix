package br.com.comprix.domain.modelo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// =====================================================================================
// Unidades
// =====================================================================================

/** Grandeza fisica de uma unidade. So e possivel comparar itens da mesma dimensao. */
enum class Dimensao(val unidadeBase: String) {
    MASSA("g"),
    VOLUME("mL"),
    CONTAGEM("un"),
    COMPRIMENTO("m"),
}

/**
 * Unidades aceitas pelo app.
 *
 * @property sigla o que aparece na tela ("kg", "mL").
 * @property descricao nome por extenso, usado nos seletores.
 * @property dimensao grandeza a que pertence.
 * @property fatorParaBase quanto vale 1 desta unidade na unidade-base da dimensao.
 */
enum class Unidade(
    val sigla: String,
    val descricao: String,
    val dimensao: Dimensao,
    val fatorParaBase: BigDecimal,
) {
    QUILO("kg", "quilograma", Dimensao.MASSA, BigDecimal("1000")),
    GRAMA("g", "grama", Dimensao.MASSA, BigDecimal.ONE),
    LITRO("L", "litro", Dimensao.VOLUME, BigDecimal("1000")),
    MILILITRO("mL", "mililitro", Dimensao.VOLUME, BigDecimal.ONE),
    UNIDADE("un", "unidade", Dimensao.CONTAGEM, BigDecimal.ONE),
    DUZIA("dz", "dúzia", Dimensao.CONTAGEM, BigDecimal("12")),
    PACOTE("pct", "pacote/fardo", Dimensao.CONTAGEM, BigDecimal.ONE),
    METRO("m", "metro", Dimensao.COMPRIMENTO, BigDecimal.ONE),
    ;

    /**
     * Unidade em que o preco por unidade-base e mostrado: ninguem pensa em
     * "R$ 0,005 por grama", e sim em "R$ 5,00 por kg".
     */
    val unidadeDeExibicao: Unidade
        get() = when (dimensao) {
            Dimensao.MASSA -> QUILO
            Dimensao.VOLUME -> LITRO
            Dimensao.CONTAGEM -> UNIDADE
            Dimensao.COMPRIMENTO -> METRO
        }

    companion object {
        /** Ordem amigavel para os seletores da interface. */
        fun paraSelecao(): List<Unidade> = listOf(UNIDADE, GRAMA, QUILO, MILILITRO, LITRO, PACOTE, DUZIA, METRO)

        /** Reconhece a unidade a partir de texto livre ("kg", "quilos", "LT", "un"). */
        fun porTexto(texto: String?): Unidade? {
            val limpo = texto?.trim()?.lowercase()?.removeSuffix(".") ?: return null
            return when (limpo) {
                "kg", "quilo", "quilos", "quilograma", "quilogramas", "kgs" -> QUILO
                "g", "gr", "grs", "grama", "gramas" -> GRAMA
                "l", "lt", "lts", "litro", "litros" -> LITRO
                "ml", "mls", "mililitro", "mililitros" -> MILILITRO
                "un", "unid", "unidade", "unidades", "und", "uni" -> UNIDADE
                "dz", "duzia", "duzias", "dúzia", "dúzias" -> DUZIA
                "pct", "pacote", "pacotes", "fardo", "fardos", "cx", "caixa" -> PACOTE
                "m", "metro", "metros" -> METRO
                else -> null
            }
        }
    }
}

// =====================================================================================
// Catalogo: categorias e produtos
// =====================================================================================

enum class OrigemCategoria { PADRAO, USUARIO }

/**
 * Secao do mercado. [chave] liga a categoria ao dicionario de palavras-chave
 * (vazia em categorias criadas pelo usuario, que so aprendem pela memoria).
 */
data class Categoria(
    val id: Long = 0,
    val nome: String,
    val icone: String,
    val ordemPadrao: Int,
    val origem: OrigemCategoria = OrigemCategoria.PADRAO,
    val chave: String = "",
)

enum class SeloAltoEm(val rotulo: String, val descricaoCurta: String) {
    ACUCAR_ADICIONADO("ALTO EM AÇÚCAR ADICIONADO", "Açúcar adicionado"),
    GORDURA_SATURADA("ALTO EM GORDURA SATURADA", "Gordura saturada"),
    SODIO("ALTO EM SÓDIO", "Sódio"),
}

/** Alergenicos de declaracao obrigatoria (RDC 26/2015) + palavras que os denunciam. */
enum class Alergeno(val rotulo: String, val palavrasChave: List<String>) {
    GLUTEN("Glúten", listOf("gluten", "trigo", "centeio", "cevada", "malte", "triticale", "farinha de trigo")),
    LEITE("Leite", listOf("leite", "lactose", "soro de leite", "caseina", "caseinato", "manteiga", "creme de leite", "queijo", "lactea")),
    OVO("Ovo", listOf("ovo", "ovos", "albumina", "clara de ovo", "gema")),
    SOJA("Soja", listOf("soja", "lecitina de soja", "proteina de soja", "extrato de soja")),
    AMENDOIM("Amendoim", listOf("amendoim", "pasta de amendoim")),
    CASTANHAS("Castanhas e nozes", listOf("castanha", "castanhas", "noz", "nozes", "amendoa", "amendoas", "avela", "pistache", "macadamia", "pecan", "pinoli")),
    PEIXE("Peixe", listOf("peixe", "atum", "sardinha", "bacalhau", "salmao", "anchova")),
    CRUSTACEOS("Crustáceos", listOf("camarao", "crustaceo", "crustaceos", "caranguejo", "lagosta", "siri")),
    GERGELIM("Gergelim", listOf("gergelim", "tahine", "sesamo")),
    AVEIA("Aveia", listOf("aveia", "farelo de aveia")),
    LATEX("Látex natural", listOf("latex natural", "latex")),
    SULFITOS("Sulfitos", listOf("sulfito", "sulfitos", "dioxido de enxofre", "metabissulfito")),
}

enum class IndicacaoGluten(val rotulo: String) {
    CONTEM("CONTÉM GLÚTEN"),
    NAO_CONTEM("NÃO CONTÉM GLÚTEN"),
    INDETERMINADO("Glúten não informado"),
}

/** Nutrientes da tabela nutricional (IN 75/2020). [chave] e o nome usado no JSON. */
enum class Nutriente(val rotulo: String, val unidade: String, val chave: String) {
    VALOR_ENERGETICO("Valor energético", "kcal", "energia"),
    CARBOIDRATOS("Carboidratos", "g", "carboidratos"),
    ACUCARES_TOTAIS("Açúcares totais", "g", "acucares_totais"),
    ACUCARES_ADICIONADOS("Açúcares adicionados", "g", "acucares_adicionados"),
    PROTEINAS("Proteínas", "g", "proteinas"),
    GORDURAS_TOTAIS("Gorduras totais", "g", "gorduras_totais"),
    GORDURAS_SATURADAS("Gorduras saturadas", "g", "gorduras_saturadas"),
    GORDURAS_TRANS("Gorduras trans", "g", "gorduras_trans"),
    FIBRA_ALIMENTAR("Fibra alimentar", "g", "fibras"),
    SODIO("Sódio", "mg", "sodio"),
    CALCIO("Cálcio", "mg", "calcio"),
    FERRO("Ferro", "mg", "ferro"),
    VITAMINA_C("Vitamina C", "mg", "vitamina_c"),
    ;

    companion object {
        fun porChave(chave: String): Nutriente? = entries.firstOrNull { it.chave == chave }

        /** O que vem marcado por padrao na comparacao nutricional. */
        fun selecaoPadrao(): List<Nutriente> = listOf(
            VALOR_ENERGETICO, CARBOIDRATOS, ACUCARES_TOTAIS, PROTEINAS, GORDURAS_SATURADAS, SODIO,
        )
    }
}

/** Tabela nutricional lida de um rotulo. */
data class InfoNutricional(
    val porcaoDescricao: String? = null,
    val valores: Map<Nutriente, BigDecimal> = emptyMap(),
) {
    val vazia: Boolean get() = valores.isEmpty() && porcaoDescricao.isNullOrBlank()
}

/**
 * Produto do catalogo local. Nasce de uma digitacao rapida ou de um escaneamento
 * e vai sendo enriquecido (codigo de barras, tabela, selos) conforme o usuario usa.
 */
data class Produto(
    val id: Long = 0,
    val nome: String,
    val nomeNormalizado: String,
    val categoriaId: Long,
    val codigoBarras: String? = null,
    val unidadePadrao: Unidade = Unidade.UNIDADE,
    val infoNutricional: InfoNutricional? = null,
    val selosAltoEm: List<SeloAltoEm> = emptyList(),
    val ingredientes: String? = null,
    val gluten: IndicacaoGluten = IndicacaoGluten.INDETERMINADO,
    val alergenos: List<Alergeno> = emptyList(),
    val dataValidade: LocalDate? = null,
    val dataFabricacao: LocalDate? = null,
    val pesoMedioEstimadoEmBase: BigDecimal? = null,
    /** Produto marcado como favorito: atalho "Favoritos" ao adicionar em listas. */
    val favorito: Boolean = false,
) {
    val temTabelaNutricional: Boolean get() = infoNutricional?.vazia == false
}

// =====================================================================================
// Listas de compras
// =====================================================================================

/** Como o usuario quer comparar aquele item especifico. */
enum class ModoComparacaoUnidade(val rotulo: String) {
    POR_UNIDADE("Por unidade"),
    POR_PESO("Por peso/volume"),
}

data class ListaDeCompras(
    val id: Long = 0,
    val nome: String,
    val criadaEm: LocalDateTime = LocalDateTime.now(),
    val finalizada: Boolean = false,
    val finalizadaEm: LocalDateTime? = null,
    /** Lista favorita: secao propria na tela de listas e alvo da selecao em massa. */
    val favorita: Boolean = false,
    /** Orcamento em centavos (null = sem orcamento); doca da lista avisa o estouro. */
    val orcamentoCentavos: Long? = null,
)

/**
 * Item dentro de uma lista.
 *
 * @property quantidade quantas EMBALAGENS o usuario vai levar.
 * @property pesoOuVolume conteudo de UMA embalagem (500 em "2 x 500 g").
 * @property itensPorKit unidades dentro do fardo/pack (12 em "fardo com 12").
 */
data class ItemDaLista(
    val id: Long = 0,
    val listaId: Long,
    val produtoId: Long,
    val quantidade: BigDecimal = BigDecimal.ONE,
    val unidade: Unidade = Unidade.UNIDADE,
    val pesoOuVolume: BigDecimal? = null,
    val ehKit: Boolean = false,
    val itensPorKit: Int? = null,
    val comprado: Boolean = false,
    val ordemManual: Int = 0,
    val modoComparacao: ModoComparacaoUnidade = ModoComparacaoUnidade.POR_UNIDADE,
    val observacao: String? = null,
)

/** Item ja resolvido com o produto correspondente - o que a interface consome. */
data class ItemComProduto(
    val item: ItemDaLista,
    val produto: Produto,
)

data class Estabelecimento(
    val id: Long = 0,
    val nome: String,
    val corHex: String = "#1E8E5A",
)

/** Preco de um item numa loja. [disponivel] = false significa "nao tinha". */
data class PrecoRegistrado(
    val id: Long = 0,
    val itemDaListaId: Long,
    val estabelecimentoId: Long,
    val preco: BigDecimal,
    val dataRegistro: LocalDateTime = LocalDateTime.now(),
    val disponivel: Boolean = true,
)

/** Linha da tela "Minhas listas". */
data class ResumoDeLista(
    val lista: ListaDeCompras,
    val totalDeItens: Int,
    val itensComprados: Int,
    val totalEstimado: BigDecimal,
    val itensSemPreco: Int,
) {
    val progresso: Float
        get() = if (totalDeItens == 0) 0f else itensComprados.toFloat() / totalDeItens
}

// =====================================================================================
// Historico
// =====================================================================================

/** Compra finalizada. Os gastos por categoria ficam serializados em JSON. */
data class CompraFinalizada(
    val id: Long = 0,
    val listaId: Long?,
    val nomeLista: String,
    val data: LocalDateTime,
    val totalPago: BigDecimal,
    val economia: BigDecimal,
    val quantidadeItens: Int,
    val descricaoEstabelecimento: String,
    val estabelecimentoPrincipalId: Long?,
    val gastosPorCategoria: Map<String, BigDecimal> = emptyMap(),
)

// =====================================================================================
// Preferencias
// =====================================================================================

enum class TipoTema(val rotulo: String) {
    SISTEMA("Seguir o sistema"),
    CLARO("Claro"),
    ESCURO("Escuro"),
}

/** Alergia ou restricao criada pelo usuario, fora da lista oficial RDC 26/2015. */
data class AlergiaCustomizada(
    val id: Long = 0,
    val nome: String,
    /** Palavras adicionais que denunciam a restricao; o proprio nome ja entra sempre. */
    val palavras: List<String> = emptyList(),
)

/** Perfil de restricoes alimentares - alimenta os alertas da comparacao. */
data class PerfilRestricoes(
    val alergenos: Set<Alergeno> = emptySet(),
    val semGluten: Boolean = false,
    /** Restricoes proprias do usuario, guardadas em tabela separada no banco. */
    val customizadas: List<AlergiaCustomizada> = emptyList(),
) {
    val ativo: Boolean get() = semGluten || alergenos.isNotEmpty() || customizadas.isNotEmpty()
}

/** Preferencias do app - tudo local, nada sai do aparelho. */
data class ConfiguracoesApp(
    val tema: TipoTema = TipoTema.SISTEMA,
    val altoContraste: Boolean = false,
    /**
     * Escala tipografica escolhida pela pessoa, de 100 a 160 (por cento).
     * Multiplica o corpo do texto *dentro* do app, somando-se a escala do
     * sistema. Fica em degraus de 5 para o controle deslizante nao produzir
     * valores impossiveis de reproduzir.
     */
    val escalaDaFonte: Int = 100,
    /** Material You: desligado por padrao para manter a identidade da marca. */
    val coresDinamicas: Boolean = false,
    val sons: Boolean = true,
    val vibracao: Boolean = true,
    /** Modo tecnico (comparacao nutricional) - desligado por padrao, Secao 8. */
    val modoTecnico: Boolean = false,
    val onboardingConcluido: Boolean = false,
    val nutrientesComparados: List<Nutriente> = Nutriente.selecaoPadrao(),
    val dicasVistas: Set<String> = emptySet(),
    val ultimoBackupMs: Long = 0L,
    /** Meta de economia mensal em centavos (null = sem meta); alimenta as Analíticas. */
    val metaEconomiaCentavos: Long? = null,
)

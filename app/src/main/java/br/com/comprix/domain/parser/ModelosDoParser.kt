package br.com.comprix.domain.parser

import br.com.comprix.domain.modelo.Unidade
import java.math.BigDecimal

/**
 * Modelos do parser de texto livre (modulo complementar "Parser de entrada em
 * texto livre", integrado a jornada B do app).
 *
 * O parser **nao cria entidade nova**: ele devolve [ItemInterpretado], que a
 * camada de apresentacao usa para popular `Produto` e `ItemDaLista` ja
 * existentes. O mapeamento e o da tabela 3.2 do documento:
 *
 * | Saida do parser   | Destino                      |
 * |-------------------|------------------------------|
 * | `nomeItem`        | `Produto.nome` / `nomeNormalizado` |
 * | `quantidade`      | `ItemDaLista.quantidade`     |
 * | `unidade`         | `ItemDaLista.unidade`        |
 * | `pesoOuVolume`    | `ItemDaLista.pesoOuVolume`   |
 * | `preco`           | `PrecoRegistrado.preco` (na loja ativa) |
 * | `ehKit`/`itensPorKit` | idem em `ItemDaLista`    |
 * | `categoriaSugerida` | `Produto.categoriaId` (via chave) |
 * | `confianca`/`motivo`/`outrasInterpretacoes`/`sugestao` | estado de UI, nao persistido |
 *
 * Tudo aqui e Kotlin puro: nenhum import de `android.*`, nenhuma chamada de
 * rede, nenhum modelo generativo. Mesma entrada produz sempre a mesma saida.
 */

enum class Confianca { ALTA, MEDIA, BAIXA }

/** De onde veio o texto. OCR tem regras mais conservadoras (nunca adivinha). */
enum class FonteDaEntrada { TEXTO, OCR }

/** Classe semantica do item, usada na validacao da secao 7.3 do documento. */
enum class ClasseDeItem { CONTAVEL, PESAVEL, AMBIGUO }

/**
 * Onde um token numerico pode aparecer para ser aceito.
 *
 * E o filtro duro que impede "racao para cachorro" de virar "5": apelidos de
 * animal so valem em [ISOLADO_POSICAO_NUMERICA].
 */
enum class PosicaoAceita {
    /** Sozinho onde se espera um numero: "cachorro de arroz" nao conta; "cachorro" numa celula de quantidade, sim. */
    ISOLADO_POSICAO_NUMERICA,

    /** Antes do item, com "de": "meia duzia de ovos". */
    ANTES_DO_ITEM,

    /** Depois do item: "ovos, meia duzia". */
    DEPOIS_DO_ITEM,

    /** Expressao completa seguida de "de" + item do lexico: "dois patinhos na lagoa de arroz". */
    ANTES_DO_ITEM_COM_DE,

    /** Frase claramente quantitativa, para apelidos nao animais. */
    FRASE_QUANTITATIVA,
}

/** De onde vem o apelido popular - decide onde ele pode ser aceito. */
enum class OrigemRepertorio {
    /** Jogo do bicho: a palavra tambem e um produto real ("cachorro", "peru", "cabra"). */
    ANIMAL,

    /** Frases, rimas e expressoes ("dois patinhos na lagoa", "idade de Cristo"). */
    NAO_ANIMAL,
}

/** Grau de verificacao da entrada - honestidade sobre a origem do dado. */
enum class Verificacao { CONFIRMADO_EM_FONTE, TRADICAO_POPULAR_SEM_FONTE, INFERENCIA }

/** Natureza do token numerico (informativo; nao reduz a validade). */
enum class DominioDoToken { COLETIVO, POPULAR, MEDIDA, NUMERAL, DECIMAL, EMBALAGEM }

/**
 * Token que representa uma quantidade: coletivo ("meia duzia"), embalagem
 * ("fardo"), apelido popular ("dois patinhos na lagoa") ou numeral.
 */
data class TokenDeQuantidade(
    val canonico: String,
    val variantes: List<String>,
    val valor: BigDecimal,
    val unidade: Unidade,
    val dominio: DominioDoToken,
    val origemRepertorio: OrigemRepertorio? = null,
    val posicoesAceitas: Set<PosicaoAceita> = setOf(
        PosicaoAceita.ANTES_DO_ITEM,
        PosicaoAceita.DEPOIS_DO_ITEM,
        PosicaoAceita.ISOLADO_POSICAO_NUMERICA,
    ),
    val classesAceitas: Set<ClasseDeItem> = ClasseDeItem.entries.toSet(),
    val confiancaBase: Confianca = Confianca.ALTA,
    val verificacao: Verificacao = Verificacao.CONFIRMADO_EM_FONTE,
    val fonte: String? = null,
    /** Quando a palavra tambem e um produto real. */
    val colisaoItem: String? = null,
    /** Apelido de origem pejorativa: entra na tabela, nunca e oferecido pela interface. */
    val sensivel: Boolean = false,
    /** Embalagem cujo conteudo depende do produto ("caixa de ovo" x "caixa de leite"). */
    val ehEmbalagem: Boolean = false,
)

/** Hipotese descartada, guardada para a sugestao e para auditoria. */
data class InterpretacaoAlternativa(
    val descricao: String,
    val quantidade: BigDecimal? = null,
    val unidade: Unidade? = null,
    val confianca: Confianca = Confianca.BAIXA,
    val motivo: String? = null,
)

/** Gatilhos G1..G8 da secao 9 do documento. */
enum class GatilhoDeSugestao { G1, G2, G3, G4, G5, G6, G7, G8 }

/**
 * Sugestao mostrada **na propria linha do item** (nunca modal, nunca dialogo),
 * com no maximo duas acoes.
 */
data class SugestaoNaoBloqueante(
    val gatilho: GatilhoDeSugestao,
    val pergunta: String,
    val opcoes: List<OpcaoDeSugestao>,
    /** Palavra que originou a duvida - chave do aprendizado local. */
    val termo: String,
) {
    init {
        require(opcoes.size <= 2) { "A sugestao inline aceita no maximo 2 opcoes (secao 9.2)." }
    }
}

data class OpcaoDeSugestao(
    val rotulo: String,
    val quantidade: BigDecimal? = null,
    val unidade: Unidade? = null,
    val itensPorKit: Int? = null,
    /** true = "manter como esta" (grava rejeicao e nao pergunta de novo). */
    val manterComoEsta: Boolean = false,
)

/**
 * Resultado da interpretacao de **um** item dentro da linha.
 *
 * `quantidade = null` e um valor legitimo: "um pouco de sal" nao e 1.
 */
data class ItemInterpretado(
    val nomeItem: String,
    val textoOriginal: String,
    val quantidade: BigDecimal? = null,
    val unidade: Unidade? = null,
    val pesoOuVolume: BigDecimal? = null,
    val ehKit: Boolean = false,
    val itensPorKit: Int? = null,
    /** Chave da categoria (sempre uma das 14 padrao); null quando desconhecida. */
    val categoriaSugerida: String? = null,
    val classeDoItem: ClasseDeItem? = null,
    val observacao: String? = null,
    val confianca: Confianca = Confianca.MEDIA,
    val motivo: String? = null,
    val outrasInterpretacoes: List<InterpretacaoAlternativa> = emptyList(),
    val sugestao: SugestaoNaoBloqueante? = null,
    val alerta: String? = null,
    /**
     * Preco monetario declarado NO FIM do proprio segmento ("uva 1kg 3,99").
     *
     * Cada item de uma linha multi-item carrega o seu preco - e o que faz
     * "uva 1kg 3,99, limao 1kg 6,50" anotar os dois valores. So e preco o
     * numero com separador decimal ou prefixo "R$"; inteiro solto no fim
     * ("cafe 3") e quantidade. null quando a linha nao declara preco.
     */
    val preco: BigDecimal? = null,
) {
    /** Item que o app consegue adicionar sem abrir o editor completo. */
    val prontoParaAdicionar: Boolean
        get() = nomeItem.isNotBlank() && confianca != Confianca.BAIXA
}

/**
 * Contrato do parser. Implementacao em [ParserDeLinhaDeCompra].
 *
 * Nunca descarta a linha: entrada irreconhecivel volta como um item com o
 * texto literal, `quantidade = null` e um [ItemInterpretado.motivo].
 */
interface ParserDeTextoLivre {
    fun interpretar(linha: String, fonte: FonteDaEntrada = FonteDaEntrada.TEXTO): List<ItemInterpretado>
}

/**
 * Memoria local do parser (mesmo padrao de aprendizado da categorizacao).
 *
 * - [rejeitados]: termos em que o usuario escolheu "manter como esta" - nao se
 *   pergunta de novo.
 * - [confirmados]: termo -> valor que o usuario aceitou, aplicado direto nas
 *   proximas vezes.
 *
 * Nada disso sai do aparelho.
 */
data class MemoriaDoParser(
    val rejeitados: Set<String> = emptySet(),
    val confirmados: Map<String, BigDecimal> = emptyMap(),
) {
    fun jaRejeitou(termo: String): Boolean = termo.lowercase() in rejeitados
    fun valorConfirmado(termo: String): BigDecimal? = confirmados[termo.lowercase()]

    companion object {
        val VAZIA = MemoriaDoParser()
    }
}

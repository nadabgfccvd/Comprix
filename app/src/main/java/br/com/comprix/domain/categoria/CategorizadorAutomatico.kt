package br.com.comprix.domain.categoria

import br.com.comprix.domain.catalogo.CatalogoSemente
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.util.TextoUtil

/**
 * Categorizacao hibrida (Secao 4.4): dicionario fixo + catalogo-semente +
 * aprendizado local.
 *
 * Ordem de decisao, da mais forte para a mais fraca:
 *
 * 1. **Memoria exata** - o usuario ja corrigiu este nome antes. Vale mais que
 *    qualquer heuristica: se ele disse que "leite de rosas" e higiene, e higiene.
 * 2. **Memoria aproximada** - nome parecido (>= 0,86 de similaridade) ja
 *    corrigido. Cobre plural, erro de digitacao e abreviacao.
 * 3. **Dicionario de palavras-chave** - ~400 termos do varejo brasileiro.
 * 4. **Catalogo-semente** - o nome (ou um SINONIMO do acervo: "mexerica",
 *    "1 kg de mexerica") existe no acervo de fabrica e o setor dele aponta
 *    uma das 14 categorias. Confianca media (0,6): e um palpite de lugar,
 *    nao a palavra do usuario.
 * 5. **"Outros"** - nunca falha nem bloqueia o fluxo.
 *
 * O aprendizado e por aparelho e offline; nada e enviado para lugar nenhum.
 */
class CategorizadorAutomatico(
    private val categoriasPorChave: Map<String, Categoria>,
    private val categoriaPadrao: Categoria,
    val catalogoSemente: CatalogoSemente? = null,
) {

    /** Como a categoria foi decidida - usado para explicar a sugestao na interface. */
    enum class Fonte { MEMORIA, MEMORIA_APROXIMADA, DICIONARIO, CATALOGO, PADRAO }

    data class Sugestao(
        val categoria: Categoria,
        val fonte: Fonte,
        val confianca: Float,
    )

    /**
     * @param nome nome do produto como o usuario digitou.
     * @param memoria correcoes anteriores: nome normalizado -> id da categoria.
     * @param categoriasPorId catalogo completo, para resolver os ids da memoria.
     */
    fun sugerir(
        nome: String,
        memoria: Map<String, Long> = emptyMap(),
        categoriasPorId: Map<Long, Categoria> = emptyMap(),
    ): Sugestao {
        val normalizado = TextoUtil.normalizar(nome)
        if (normalizado.isBlank()) return Sugestao(categoriaPadrao, Fonte.PADRAO, 0.2f)

        memoria[normalizado]?.let { id ->
            categoriasPorId[id]?.let { return Sugestao(it, Fonte.MEMORIA, 1f) }
        }

        if (memoria.isNotEmpty()) {
            val maisParecida = memoria.keys
                .map { chave -> chave to TextoUtil.similaridade(normalizado, chave) }
                .maxByOrNull { it.second }
            if (maisParecida != null && maisParecida.second >= LIMIAR_SIMILARIDADE) {
                memoria[maisParecida.first]?.let { id ->
                    categoriasPorId[id]?.let {
                        return Sugestao(it, Fonte.MEMORIA_APROXIMADA, maisParecida.second)
                    }
                }
            }
        }

        val chave = DicionarioDeCategorias.sugerirChave(nome)
        val doDicionario = categoriasPorChave[chave]
        if (doDicionario != null && chave != DicionarioDeCategorias.CHAVE_PADRAO) {
            return Sugestao(doDicionario, Fonte.DICIONARIO, 0.8f)
        }

        val doCatalogo = sugestaoDoCatalogo(normalizado, categoriasPorId)
        if (doCatalogo != null) return doCatalogo

        return Sugestao(categoriasPorChave[DicionarioDeCategorias.CHAVE_PADRAO] ?: categoriaPadrao, Fonte.PADRAO, 0.3f)
    }

    /**
     * Degrau CATALOGO: acha o nome no acervo de fabrica e traduz a categoria
     * Comprix do setor para uma categoria do app, casando por normalizacao.
     * A busca do acervo tambem resolve sinonimos ("mexerica"/"bergamota" ->
     * "Tangerina"), e o sinonimo so entra quando o nome direto nao casou.
     * Qualquer elo faltando devolve null e a cadeia cai para "Outros".
     */
    private fun sugestaoDoCatalogo(
        normalizado: String,
        categoriasPorId: Map<Long, Categoria>,
    ): Sugestao? {
        val semente = catalogoSemente ?: return null
        if (normalizado.isBlank()) return null
        val item = semente.buscar(normalizado, 1).firstOrNull() ?: return null
        val categoriaComprix = semente.categoriaDe(item) ?: return null
        val nomeDaCategoria = TABELA_DE_CATEGORIAS[
            TextoUtil.removerAcentos(categoriaComprix).lowercase().trim(),
        ] ?: return null
        val encontrada = categoriasPorId.values.firstOrNull { categoria ->
            TextoUtil.normalizar(categoria.nome) == TextoUtil.normalizar(nomeDaCategoria)
        } ?: return null
        return Sugestao(encontrada, Fonte.CATALOGO, 0.6f)
    }

    companion object {
        /** Abaixo disso a memoria aproximada erra mais do que acerta. */
        const val LIMIAR_SIMILARIDADE = 0.86f

        /**
         * Categoria Comprix do arquivo (sem acento e em minusculas, preservando
         * a barra) -> nome da categoria equivalente no app.
         */
        private val TABELA_DE_CATEGORIAS = mapOf(
            "hortifruti" to "Hortifrúti",
            "acougue/carnes" to "Açougue e peixaria",
            "padaria" to "Padaria",
            "laticinios e frios" to "Frios e laticínios",
            "mercearia/secos" to "Mercearia",
            "congelados" to "Congelados",
            "organicos" to "Orgânicos",
            "bebidas" to "Bebidas",
            "limpeza" to "Limpeza",
            "higiene e perfumaria" to "Higiene e beleza",
            "infantil" to "Infantil",
            "pet" to "Pet",
            "bazar/utilidades" to "Bazar e utilidades",
            "outros" to "Outros",
        )
    }
}

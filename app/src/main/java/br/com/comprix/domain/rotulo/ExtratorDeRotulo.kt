package br.com.comprix.domain.rotulo

import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.CampoRotulo
import br.com.comprix.domain.modelo.IndicacaoGluten
import br.com.comprix.domain.modelo.InfoNutricional
import br.com.comprix.domain.modelo.LeituraDeRotulo
import br.com.comprix.domain.modelo.LinhaOcr
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.SeloAltoEm
import br.com.comprix.domain.modelo.Unidade
import br.com.comprix.domain.unidade.ConversorDeUnidades
import br.com.comprix.util.Constantes
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.math.RoundingMode

/**
 * Transforma o texto cru do ML Kit em campos de produto (Secao 5.3).
 *
 * ## O problema
 * O OCR devolve uma sopa de linhas: nome do produto, preco da gondola, preco
 * do concorrente, "LEVE 3 PAGUE 2", codigo interno, CNPJ, tabela nutricional,
 * lote, validade. Nada vem rotulado. Este modulo usa posicao, tamanho de letra
 * e padroes de texto para decidir o que e o que.
 *
 * ## Heuristica de preco (a mais delicada)
 * Cada numero plausivel recebe uma pontuacao:
 *
 * | Sinal                              | Peso  |
 * |------------------------------------|-------|
 * | tem "R$" colado                    | +3,0  |
 * | altura relativa da linha           | +2,0 x altura |
 * | linha com cara de ruido (CNPJ, lote, telefone, validade) | -5,0 |
 * | valor acima de R$ 500              | -1,5  |
 * | valor acima de R$ 9.999            | descartado |
 *
 * Vence a maior pontuacao; empate fica com o valor mais alto, que em gondola
 * costuma ser o preco cheio (e nao o "por mes" ou a parcela).
 *
 * ## Datas
 * `dd/MM/yyyy`, `dd/MM/yy`, `MM/yyyy` e `dd.MM.yyyy`. Rotulos brasileiros
 * costumam trazer "VAL" e "FAB"; quando nao ha rotulo, a data mais distante no
 * futuro vira validade e a mais antiga vira fabricacao.
 *
 * Nenhum campo e obrigatorio: o que nao for reconhecido simplesmente fica nulo
 * e o usuario preenche na tela de revisao.
 */
object ExtratorDeRotulo {

    // ---------------------------------------------------------------------------------
    // Expressoes regulares
    // ---------------------------------------------------------------------------------

    // As âncoras (?<!...) e (?!...) impedem casar DENTRO de uma sequencia longa de
    // digitos: sem elas, o codigo de barras "78999123456" virava "R$ 456,00".
    private val REGEX_PRECO =
        Regex("""(?:r\$\s*)?(?<![\d.,])(\d{1,4}(?:[.,]\d{3})*(?:[.,]\d{2})?)(?![\d])""")
    private val REGEX_DATA = Regex("""(\d{2})[/.\-](\d{2})[/.\-](\d{2,4})""")
    private val REGEX_MES_ANO = Regex("""(?<![\d/])(\d{2})[/.\-](\d{4})(?![\d/])""")
    private val REGEX_RUIDO = Regex(
        """cnpj|cpf|lote|sac|tel|fone|cep|validade|fabric|venc|insc|código interno|codigo interno|""" +
            """ean|registro|ms |sif|cx |un:|peso liq|cartao|parcel|\d{2}/\d{2}/\d{2,4}""",
    )
    private val REGEX_SO_NUMEROS = Regex("""^[\d\s.,%:/-]+$""")
    private val REGEX_INGREDIENTES = Regex(
        """ingredientes?\s*[:\-]?\s*(.{10,800})""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val REGEX_PORCAO = Regex("""por(?:ç|c)(?:ã|a)o\s*(?:de)?\s*([^\n(]{1,40})""")
    private val REGEX_NUTRIENTE_VALOR = Regex("""(-?\d+(?:[.,]\d+)?)\s*(kcal|kj|mg|g\b|mcg)?""")

    private val PALAVRAS_CONTEM_GLUTEN = listOf("contem gluten", "contém glúten", "contem glutem")
    private val PALAVRAS_SEM_GLUTEN = listOf("nao contem gluten", "não contém glúten", "sem gluten", "zero gluten", "gluten free")

    // ---------------------------------------------------------------------------------
    // API principal
    // ---------------------------------------------------------------------------------

    /**
     * Extrai todos os campos possiveis de um conjunto de linhas reconhecidas.
     *
     * @param linhas saida do ML Kit, com altura e posicao relativas.
     * @param codigoBarras codigo lido pelo scanner de barras, quando houver.
     * @param quadros quantos quadros de video geraram este texto (1 na foto).
     */
    fun extrair(
        linhas: List<LinhaOcr>,
        codigoBarras: String? = null,
        formatoCodigoBarras: String? = null,
        quadros: Int = 1,
    ): LeituraDeRotulo {
        if (linhas.isEmpty() && codigoBarras.isNullOrBlank()) return LeituraDeRotulo(quadrosAnalisados = quadros)

        val textoBruto = linhas.joinToString("\n") { it.texto }
        val textoNormalizado = TextoUtil.normalizar(textoBruto)
        val confianca = HashMap<CampoRotulo, Float>()

        val preco = extrairPreco(linhas)?.also { confianca[CampoRotulo.PRECO] = 0.75f }
        val quantidade = extrairQuantidade(linhas)
        if (quantidade != null) confianca[CampoRotulo.QUANTIDADE] = 0.8f
        val nome = extrairNome(linhas)?.also { confianca[CampoRotulo.NOME] = 0.6f }

        val datas = extrairDatas(linhas)
        if (datas.validade != null) confianca[CampoRotulo.VALIDADE] = datas.confianca
        if (datas.fabricacao != null) confianca[CampoRotulo.FABRICACAO] = datas.confianca

        val ingredientes = extrairIngredientes(textoBruto)?.also { confianca[CampoRotulo.INGREDIENTES] = 0.7f }
        val gluten = detectarGluten(textoNormalizado, ingredientes)
        if (gluten != IndicacaoGluten.INDETERMINADO) confianca[CampoRotulo.GLUTEN] = 0.85f

        val alergenos = detectarAlergenos(textoNormalizado, ingredientes)
        if (alergenos.isNotEmpty()) confianca[CampoRotulo.ALERGENOS] = 0.65f

        val selos = detectarSelos(textoNormalizado)
        if (selos.isNotEmpty()) confianca[CampoRotulo.SELOS] = 0.9f

        val nutricional = extrairTabelaNutricional(linhas)
        if (nutricional != null) confianca[CampoRotulo.NUTRICIONAL] = 0.7f

        if (!codigoBarras.isNullOrBlank()) {
            confianca[CampoRotulo.CODIGO_BARRAS] = if (TextoUtil.codigoDeBarrasValido(codigoBarras)) 1f else 0.5f
        }

        return LeituraDeRotulo(
            nome = nome,
            preco = preco,
            quantidade = quantidade?.quantidade,
            unidade = quantidade?.unidade,
            itensPorEmbalagem = quantidade?.itensPorEmbalagem,
            codigoBarras = codigoBarras,
            formatoCodigoBarras = formatoCodigoBarras,
            selos = selos,
            dataValidade = datas.validade,
            dataFabricacao = datas.fabricacao,
            ingredientes = ingredientes,
            gluten = gluten,
            alergenos = alergenos,
            infoNutricional = nutricional,
            textoBruto = textoBruto,
            confiancaPorCampo = confianca,
            quadrosAnalisados = quadros,
        )
    }

    // ---------------------------------------------------------------------------------
    // Preco
    // ---------------------------------------------------------------------------------

    private data class Candidato(val valor: BigDecimal, val pontuacao: Double)

    /** Escolhe o preco mais provavel entre todos os numeros da imagem. */
    fun extrairPreco(linhas: List<LinhaOcr>): BigDecimal? {
        val candidatos = mutableListOf<Candidato>()

        linhas.forEach { linha ->
            // normalizarLeve preserva "," "." "R$" - essenciais para ler dinheiro.
            val textoLeve = TextoUtil.normalizarLeve(linha.texto)
            val ehRuido = REGEX_RUIDO.containsMatchIn(textoLeve)

            REGEX_PRECO.findAll(textoLeve).forEach { achado ->
                val bruto = achado.groupValues[1]
                val valor = interpretarValorMonetario(bruto) ?: return@forEach
                if (valor.signum() <= 0) return@forEach
                if (valor > BigDecimal(Constantes.PRECO_MAXIMO_ACEITO)) return@forEach
                // Numero inteiro curto sem virgula costuma ser gramatura/quantidade, nao preco.
                if (!bruto.contains(',') && !bruto.contains('.') && !achado.value.contains("r$") && valor < BigDecimal("3")) {
                    return@forEach
                }

                var pontuacao = 0.0
                if (achado.value.contains("r$")) pontuacao += 3.0
                pontuacao += 2.0 * linha.alturaRelativa
                if (ehRuido) pontuacao -= 5.0
                if (valor > BigDecimal(Constantes.PRECO_SUSPEITO)) pontuacao -= 1.5
                if (bruto.contains(',')) pontuacao += 0.5

                candidatos += Candidato(valor, pontuacao)
            }
        }

        return candidatos
            .filter { it.pontuacao > -2.0 }
            .sortedWith(compareByDescending<Candidato> { it.pontuacao }.thenByDescending { it.valor })
            .firstOrNull()
            ?.valor
            ?.setScale(Constantes.ESCALA_MOEDA, RoundingMode.HALF_EVEN)
    }

    /** "1.234,56" (pt-BR), "1,234.56" (en) e "12,90" viram o mesmo BigDecimal. */
    private fun interpretarValorMonetario(bruto: String): BigDecimal? = TextoUtil.paraDecimal(bruto)

    // ---------------------------------------------------------------------------------
    // Nome e quantidade
    // ---------------------------------------------------------------------------------

    /**
     * Nome do produto: a linha mais "textual" entre as maiores e mais ao alto.
     * Linhas so com numeros, com cara de ruido ou muito curtas sao descartadas.
     */
    fun extrairNome(linhas: List<LinhaOcr>): String? {
        val candidata = linhas
            .asSequence()
            .filter { it.texto.trim().length >= 3 }
            .filterNot { REGEX_SO_NUMEROS.matches(it.texto.trim()) }
            .filterNot { REGEX_RUIDO.containsMatchIn(TextoUtil.normalizar(it.texto)) }
            .filterNot { TextoUtil.normalizar(it.texto).startsWith("ingredientes") }
            .filterNot { TextoUtil.normalizar(it.texto).contains("informacao nutricional") }
            .maxByOrNull { linha ->
                val letras = linha.texto.count { it.isLetter() }.toDouble() / linha.texto.length.coerceAtLeast(1)
                linha.alturaRelativa * 2.0 + letras - linha.topoRelativo * 0.5
            }
            ?.texto
            ?.trim()
            ?: return null

        val semQuantidade = ConversorDeUnidades.nomeSemQuantidade(candidata)
        val limpo = semQuantidade.replace(Regex("""r\$\s*\d+[.,]?\d*""", RegexOption.IGNORE_CASE), "").trim()
        // Guarda "so quantidade": se sobrou menos de 3 letras, o nome nao serve.
        if (limpo.count { it.isLetter() } < 3) return null
        return TextoUtil.capitalizarTitulo(limpo)
    }

    /** Peso/volume declarado na embalagem ("500 g", "12 x 350 mL"). */
    fun extrairQuantidade(linhas: List<LinhaOcr>): ConversorDeUnidades.QuantidadeInterpretada? {
        linhas.sortedByDescending { it.alturaRelativa }.forEach { linha ->
            ConversorDeUnidades.interpretarTexto(linha.texto)?.let { return it }
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // Datas
    // ---------------------------------------------------------------------------------

    data class DatasDoRotulo(
        val validade: LocalDate? = null,
        val fabricacao: LocalDate? = null,
        val confianca: Float = 0f,
    )

    /** Le validade e fabricacao, usando os rotulos "VAL"/"FAB" quando existirem. */
    fun extrairDatas(linhas: List<LinhaOcr>): DatasDoRotulo {
        var validadeRotulada: LocalDate? = null
        var fabricacaoRotulada: LocalDate? = null
        val soltas = mutableListOf<LocalDate>()

        linhas.forEach { linha ->
            val leve = TextoUtil.normalizarLeve(linha.texto)
            val datas = lerDatas(leve)
            if (datas.isEmpty()) return@forEach

            val temVal = leve.contains("val") || leve.contains("venc") || leve.contains("consumir")
            val temFab = leve.contains("fab") || leve.contains("prod")

            when {
                temVal && !temFab -> validadeRotulada = validadeRotulada ?: datas.max()
                temFab && !temVal -> fabricacaoRotulada = fabricacaoRotulada ?: datas.min()
                temVal && temFab -> {
                    fabricacaoRotulada = fabricacaoRotulada ?: datas.min()
                    validadeRotulada = validadeRotulada ?: datas.max()
                }
                else -> soltas += datas
            }
        }

        if (validadeRotulada != null || fabricacaoRotulada != null) {
            return DatasDoRotulo(validadeRotulada, fabricacaoRotulada, 0.9f)
        }
        if (soltas.isEmpty()) return DatasDoRotulo()

        val hoje = LocalDate.now()
        val futuras = soltas.filter { !it.isBefore(hoje) }
        val passadas = soltas.filter { it.isBefore(hoje) }
        return DatasDoRotulo(
            validade = futuras.minOrNull() ?: soltas.maxOrNull(),
            fabricacao = passadas.maxOrNull(),
            confianca = 0.55f,
        )
    }

    private fun lerDatas(texto: String): List<LocalDate> {
        val encontradas = mutableListOf<LocalDate>()

        REGEX_DATA.findAll(texto).forEach { achado ->
            val dia = achado.groupValues[1].toIntOrNull() ?: return@forEach
            val mes = achado.groupValues[2].toIntOrNull() ?: return@forEach
            val anoBruto = achado.groupValues[3].toIntOrNull() ?: return@forEach
            val ano = if (anoBruto < 100) 2000 + anoBruto else anoBruto
            try {
                encontradas += LocalDate.of(ano, mes, dia)
            } catch (erro: DateTimeException) {
                // Data impossivel (32/13/2026): o OCR errou um digito, ignoramos.
            }
        }

        REGEX_MES_ANO.findAll(texto).forEach { achado ->
            val mes = achado.groupValues[1].toIntOrNull() ?: return@forEach
            val ano = achado.groupValues[2].toIntOrNull() ?: return@forEach
            try {
                // So mes/ano: assume o ultimo dia do mes (pratica usual em rotulo).
                encontradas += YearMonth.of(ano, mes).atEndOfMonth()
            } catch (erro: DateTimeException) {
                // idem
            }
        }
        return encontradas.filter { it.year in 2000..2100 }.distinct()
    }

    // ---------------------------------------------------------------------------------
    // Ingredientes, gluten, alergenicos e selos
    // ---------------------------------------------------------------------------------

    /** Trecho apos a palavra "INGREDIENTES", sem a cauda de outras secoes. */
    fun extrairIngredientes(textoBruto: String): String? {
        val achado = REGEX_INGREDIENTES.find(textoBruto.replace('\n', ' ')) ?: return null
        var trecho = achado.groupValues[1].trim()
        listOf("informacao nutricional", "informação nutricional", "alergicos", "alérgicos", "modo de preparo", "conservar", "fabricado", "industria").forEach { corte ->
            val posicao = TextoUtil.normalizar(trecho).indexOf(TextoUtil.normalizar(corte))
            if (posicao > 20) trecho = trecho.substring(0, posicao).trim()
        }
        return trecho.trim(' ', '.', ',', ';', '-').ifBlank { null }
    }

    /** "CONTEM GLUTEN" / "NAO CONTEM GLUTEN" - a negativa tem prioridade na leitura. */
    fun detectarGluten(textoNormalizado: String, ingredientes: String?): IndicacaoGluten {
        val alvo = textoNormalizado
        if (PALAVRAS_SEM_GLUTEN.any { TextoUtil.normalizar(it) in alvo }) return IndicacaoGluten.NAO_CONTEM
        if (PALAVRAS_CONTEM_GLUTEN.any { TextoUtil.normalizar(it) in alvo }) return IndicacaoGluten.CONTEM
        val nosIngredientes = TextoUtil.normalizar(ingredientes ?: "")
        if (nosIngredientes.isNotBlank() &&
            Alergeno.GLUTEN.palavrasChave.any { TextoUtil.contemPalavra(nosIngredientes, TextoUtil.normalizar(it)) }
        ) {
            return IndicacaoGluten.CONTEM
        }
        return IndicacaoGluten.INDETERMINADO
    }

    /** Alergenicos citados no aviso "ALERGICOS: CONTEM..." ou na lista de ingredientes. */
    fun detectarAlergenos(textoNormalizado: String, ingredientes: String?): List<Alergeno> {
        val alvo = (textoNormalizado + " " + TextoUtil.normalizar(ingredientes ?: "")).trim()
        if (alvo.isBlank()) return emptyList()
        return Alergeno.entries.filter { alergeno ->
            alergeno.palavrasChave.any { TextoUtil.contemPalavra(alvo, TextoUtil.normalizar(it)) }
        }
    }

    /** Lupas pretas da Anvisa. Informativas - nunca entram em ranking (Secao 13). */
    fun detectarSelos(textoNormalizado: String): List<SeloAltoEm> {
        if (!textoNormalizado.contains("alto em")) return emptyList()
        val selos = mutableListOf<SeloAltoEm>()
        if (textoNormalizado.contains("acucar")) selos += SeloAltoEm.ACUCAR_ADICIONADO
        if (textoNormalizado.contains("gordura saturada")) selos += SeloAltoEm.GORDURA_SATURADA
        if (textoNormalizado.contains("sodio")) selos += SeloAltoEm.SODIO
        return selos
    }

    // ---------------------------------------------------------------------------------
    // Tabela nutricional
    // ---------------------------------------------------------------------------------

    private val SINONIMOS_NUTRIENTES: List<Pair<String, Nutriente>> = listOf(
        "valor energetico" to Nutriente.VALOR_ENERGETICO,
        "energia" to Nutriente.VALOR_ENERGETICO,
        "carboidratos totais" to Nutriente.CARBOIDRATOS,
        "carboidrato" to Nutriente.CARBOIDRATOS,
        "acucares totais" to Nutriente.ACUCARES_TOTAIS,
        "acucares adicionados" to Nutriente.ACUCARES_ADICIONADOS,
        "proteinas" to Nutriente.PROTEINAS,
        "proteina" to Nutriente.PROTEINAS,
        "gorduras totais" to Nutriente.GORDURAS_TOTAIS,
        "gordura total" to Nutriente.GORDURAS_TOTAIS,
        "gorduras saturadas" to Nutriente.GORDURAS_SATURADAS,
        "gordura saturada" to Nutriente.GORDURAS_SATURADAS,
        "gorduras trans" to Nutriente.GORDURAS_TRANS,
        "fibra alimentar" to Nutriente.FIBRA_ALIMENTAR,
        "fibras" to Nutriente.FIBRA_ALIMENTAR,
        "sodio" to Nutriente.SODIO,
        "calcio" to Nutriente.CALCIO,
        "ferro" to Nutriente.FERRO,
        "vitamina c" to Nutriente.VITAMINA_C,
    ).sortedByDescending { it.first.length }

    /**
     * Le a tabela nutricional linha a linha ("Sodio .... 210 mg").
     *
     * Converte kJ para kcal quando so houver kJ, e ignora a coluna "%VD" (o
     * primeiro numero da linha e sempre a quantidade na porcao).
     *
     * @return null quando nenhum nutriente foi reconhecido.
     */
    fun extrairTabelaNutricional(linhas: List<LinhaOcr>): InfoNutricional? {
        val valores = LinkedHashMap<Nutriente, BigDecimal>()
        var porcao: String? = null

        linhas.forEach { linha ->
            // Duas versoes do mesmo texto, de proposito:
            // - `normalizada` (sem pontuacao) casa o nome do nutriente;
            // - `leve` (com pontuacao) le o numero, porque normalizar() apagaria
            //   a virgula e "3,2 g de proteina" viraria 3 g.
            val normalizada = TextoUtil.normalizar(linha.texto)
            val leve = TextoUtil.normalizarLeve(linha.texto)
            if (porcao == null) {
                REGEX_PORCAO.find(leve)?.let { achado ->
                    val bruto = achado.groupValues[1].trim()
                    if (bruto.any { it.isDigit() }) porcao = bruto.take(40)
                }
            }

            val nutriente = SINONIMOS_NUTRIENTES.firstOrNull { (sinonimo, _) ->
                normalizada.contains(sinonimo)
            }?.second ?: return@forEach
            if (valores.containsKey(nutriente)) return@forEach

            val sinonimo = SINONIMOS_NUTRIENTES
                .first { it.second == nutriente && normalizada.contains(it.first) }
                .first
            val restante = if (leve.contains(sinonimo)) {
                leve.substringAfter(sinonimo)
            } else {
                normalizada.substringAfter(sinonimo)
            }
            val achadoValor = REGEX_NUTRIENTE_VALOR.find(restante) ?: return@forEach
            var valor = TextoUtil.paraDecimal(achadoValor.groupValues[1]) ?: return@forEach
            val unidadeLida = achadoValor.groupValues[2]

            if (nutriente == Nutriente.VALOR_ENERGETICO && unidadeLida == "kj") {
                valor = valor.divide(BigDecimal("4.184"), 0, RoundingMode.HALF_UP)
            }
            if (valor.signum() < 0) return@forEach
            valores[nutriente] = valor
        }

        if (valores.isEmpty() && porcao == null) return null
        return InfoNutricional(porcaoDescricao = porcao, valores = valores)
    }

    /** Sobrecarga util para texto ja concatenado (backup, colagem manual). */
    fun extrairTabelaNutricional(texto: String): InfoNutricional? =
        extrairTabelaNutricional(texto.lines().map { LinhaOcr(it) })

    /** Unidade sugerida quando o rotulo so traz o numero. */
    fun unidadeProvavel(nome: String?): Unidade =
        if (nome != null && TextoUtil.normalizar(nome).let { it.contains("leite") || it.contains("suco") || it.contains("refrigerante") }) {
            Unidade.MILILITRO
        } else {
            Unidade.GRAMA
        }
}

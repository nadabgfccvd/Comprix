package br.com.comprix.util

/**
 * Constantes globais do Comprix.
 *
 * Regra do projeto: nenhum numero ou string "magica" espalhada pelo codigo -
 * tudo que tem significado de negocio mora aqui, com o porque ao lado.
 */
object Constantes {

    // --- Persistencia ---
    const val NOME_BANCO = "comprix.db"
    const val VERSAO_BANCO = 1
    const val PASTA_BACKUPS = "backups"
    const val PREFIXO_BACKUP = "comprix-backup-"

    /** Texto simples, uma linha JSON por registro (ver GerenciadorDeBackup). */
    const val EXTENSAO_BACKUP = ".cbk"
    const val CABECALHO_BACKUP = "#COMPRIX-BACKUP v1"
    const val MAX_BACKUPS_AUTOMATICOS = 5
    const val INTERVALO_BACKUP_AUTOMATICO_MS = 24L * 60 * 60 * 1000

    // --- Precisao numerica ---
    /** Casas decimais de dinheiro exibido ao usuario. */
    const val ESCALA_MOEDA = 2

    /** Casas decimais internas do preco por unidade-base (R$/g pode ser bem pequeno). */
    const val ESCALA_UNIDADE_BASE = 6

    /** Abaixo disso duas opcoes sao consideradas equivalentes (0,5%). */
    const val LIMIAR_EQUIVALENCIA_PERCENTUAL = 0.5

    // --- Camera / OCR ---
    const val DURACAO_MAXIMA_VIDEO_MS = 30_000L
    const val INTERVALO_ENTRE_QUADROS_MS = 600L
    const val MAXIMO_QUADROS_ANALISADOS = 36
    const val LARGURA_MAXIMA_QUADRO = 1280
    const val INTERVALO_MINIMO_ANALISE_AO_VIVO_MS = 350L

    /** Fracao minima de quadros em que um selo/alergeno precisa aparecer. */
    const val LIMIAR_PRESENCA = 0.3

    /** Preco acima disso numa etiqueta de mercado e quase certamente ruido. */
    const val PRECO_SUSPEITO = 500
    const val PRECO_MAXIMO_ACEITO = 9999

    // --- UX ---
    const val LIMITE_SUGESTOES_AUTOCOMPLETAR = 6
    const val MAXIMO_PRODUTOS_COMPARADOS = 3
    const val DURACAO_ANIMACAO_CURTA_MS = 150
    const val DURACAO_ANIMACAO_MEDIA_MS = 300

    // --- Chaves das dicas contextuais (exibidas uma unica vez) ---
    const val DICA_DETALHE_LISTA = "dica_detalhe_lista"
    const val DICA_COMPARACAO = "dica_comparacao"
    const val DICA_SCANNER_VIDEO = "dica_scanner_video"
    const val DICA_SCANNER_FOTO = "dica_scanner_foto"
    const val DICA_SCANNER_CODIGO = "dica_scanner_codigo"
    const val DICA_MODO_TECNICO = "dica_modo_tecnico"
    const val DICA_HISTORICO = "dica_historico"
}

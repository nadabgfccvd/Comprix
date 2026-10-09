package br.com.comprix.presentation.tema

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.comprix.R

/**
 * Conjunto vetorial do Comprix: os 65 icones originais da referencia visual.
 *
 * Os SVGs de `assets/icones/` foram convertidos para `VectorDrawable` por
 * `tools/svg_para_vetor.py`, preservando grade 24 x 24, traco 1,8, pontas e
 * juntas arredondadas. O traco e gravado em preto solido e a cor real vem do
 * `tint` aplicado no desenho - por isso um unico arquivo serve tema claro,
 * escuro e alto contraste.
 *
 * Nenhum icone do `material-icons-extended` entra no projeto: o conjunto da
 * referencia e a unica fonte de simbolos, o que mantem a linguagem coerente e
 * o APK menor.
 */
object Icones {
    val alerta = R.drawable.ic_alert
    val seta = R.drawable.ic_arrow
    val infantil = R.drawable.ic_baby
    val voltar = R.drawable.ic_back
    val codigoDeBarras = R.drawable.ic_barcode
    val cesta = R.drawable.ic_basket
    val garrafa = R.drawable.ic_bottle
    val caixa = R.drawable.ic_box
    val pao = R.drawable.ic_bread
    val calendario = R.drawable.ic_calendar
    val camera = R.drawable.ic_camera
    val carregando = R.drawable.ic_carregando
    val grafico = R.drawable.ic_chart
    val confirmar = R.drawable.ic_check
    val confirmarCirculo = R.drawable.ic_check_circle
    val expandir = R.drawable.ic_chevron
    val limpeza = R.drawable.ic_cleaning
    val fechar = R.drawable.ic_close
    val codigo = R.drawable.ic_code
    val comparar = R.drawable.ic_compare
    val contraste = R.drawable.ic_contrast
    val duplicar = R.drawable.ic_copy
    val copo = R.drawable.ic_cup
    val excluir = R.drawable.ic_delete
    val descer = R.drawable.ic_down
    val baixar = R.drawable.ic_download
    val arrastar = R.drawable.ic_drag
    val editar = R.drawable.ic_edit
    val estrela = R.drawable.ic_estrela
    val olho = R.drawable.ic_eye
    val coracao = R.drawable.ic_heart
    val historico = R.drawable.ic_history
    val informacao = R.drawable.ic_info
    val camadas = R.drawable.ic_layers
    val folha = R.drawable.ic_leaf
    val listas = R.drawable.ic_lists
    val carne = R.drawable.ic_meat
    val menu = R.drawable.ic_menu
    val leite = R.drawable.ic_milk
    val menos = R.drawable.ic_minus
    val lua = R.drawable.ic_moon
    val maisOpcoes = R.drawable.ic_more
    val nutricao = R.drawable.ic_nutrition
    val offline = R.drawable.ic_offline
    val pata = R.drawable.ic_paw
    val adicionar = R.drawable.ic_plus
    val recomecar = R.drawable.ic_refresh
    val escanear = R.drawable.ic_scan
    val buscar = R.drawable.ic_search
    val configuracoes = R.drawable.ic_settings
    val escudo = R.drawable.ic_shield
    val congelado = R.drawable.ic_snow
    val som = R.drawable.ic_sound
    val brilho = R.drawable.ic_sparkle
    val armazenamento = R.drawable.ic_storage
    val loja = R.drawable.ic_store
    val sol = R.drawable.ic_sun
    val sistema = R.drawable.ic_system
    val etiqueta = R.drawable.ic_tag
    val relogio = R.drawable.ic_time
    val trofeu = R.drawable.ic_trophy
    val tipografia = R.drawable.ic_type
    val indisponivel = R.drawable.ic_unavailable
    val enviar = R.drawable.ic_upload
    val video = R.drawable.ic_video
    val peso = R.drawable.ic_weight
    val trigo = R.drawable.ic_wheat
    val compartilhar = R.drawable.ic_compartilhar
    val catalogo = R.drawable.ic_catalogo
    val moldura = R.drawable.ic_moldura
    val desfazer = R.drawable.ic_undo
    val refazer = R.drawable.ic_redo
    val microfone = R.drawable.ic_microfone

    /**
     * Icone de cada uma das 14 categorias da Secao 4.4, pela chave estavel.
     *
     * Categoria criada pela pessoa cai em [etiqueta], que e neutro de proposito:
     * inventar um simbolo para um nome que o app nao conhece seria chute.
     */
    @DrawableRes
    fun daCategoria(chave: String): Int = when (chave) {
        "hortifruti" -> folha
        "padaria" -> pao
        "acougue" -> carne
        "laticinios" -> leite
        "congelados" -> congelado
        "mercearia" -> caixa
        "organicos" -> brilho
        "bebidas" -> garrafa
        "limpeza" -> limpeza
        "higiene" -> coracao
        "infantil" -> infantil
        "pet" -> pata
        "bazar" -> camadas
        else -> etiqueta
    }

    /**
     * Posicao da categoria na paleta de tintas, pela mesma chave.
     *
     * Fixar a posicao (em vez de usar o indice da lista) mantem a cor da
     * categoria igual depois de reordenar o mercado - a cor identifica a
     * categoria, nao o lugar dela na tela.
     */
    fun tomDaCategoria(chave: String): Int = when (chave) {
        "hortifruti" -> 0
        "padaria" -> 1
        "laticinios" -> 2
        "acougue" -> 3
        "mercearia" -> 4
        "bebidas" -> 5
        "limpeza" -> 6
        "higiene" -> 7
        "congelados" -> 8
        "pet" -> 9
        "organicos" -> 10
        "bazar" -> 11
        "infantil" -> 7
        else -> chave.hashCode().mod(12)
    }
}

/** Tamanhos de icone da referencia: `.icon.sm`, `.icon`, `.icon.lg`, `.icon.xl`. */
object TamanhoDeIcone {
    val pequeno: Dp = 18.dp
    val padrao: Dp = 24.dp
    val grande: Dp = 32.dp
    val enorme: Dp = 48.dp
}

/**
 * Desenha um icone do conjunto Comprix.
 *
 * @param descricao texto para leitor de tela. `null` marca o icone como
 *   decorativo - use somente quando o rotulo ao lado ja diz tudo, senao o
 *   TalkBack anuncia um botao sem nome.
 */
@Composable
fun IconeComprix(
    @DrawableRes recurso: Int,
    descricao: String?,
    modifier: Modifier = Modifier,
    tamanho: Dp = TamanhoDeIcone.padrao,
    tinta: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(recurso),
        contentDescription = descricao,
        modifier = modifier.size(tamanho),
        tint = tinta,
    )
}

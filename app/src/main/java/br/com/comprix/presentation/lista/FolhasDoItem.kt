package br.com.comprix.presentation.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.presentation.comum.ALVO_MINIMO
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.CampoDePreco
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.FolhaComprix
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.tocarSemRealce
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.presentation.tema.corDeHex
import br.com.comprix.presentation.tema.corDeTextoSobre
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.math.BigDecimal

/**
 * **Folhas de item da lista**: acoes rapidas do cartao (toque longo) e preco
 * de uma pendencia (aviso clicavel do painel).
 *
 * As duas entram no lugar de caminhos antigos que abriam o [EditorDeItem]
 * inteiro para tarefas de uma linha so. A edicao completa continua existindo -
 * a folha de acoes tem o atalho "Editar item" que a abre.
 */

/**
 * Folha de acoes de um item, aberta pelo TOQUE LONGO no cartao da lista.
 *
 * Duas secoes:
 *
 * 1. **Disponibilidade por loja** - uma linha por estabelecimento, com o chip
 *    colorido da loja, o estado atual (preco anotado, marcada como
 *    indisponivel ou sem preco) e a acao de uma linha so: "Nao tem nessa loja"
 *    ou "Permitir novamente" quando a marca ja existe. A folha NAO fecha ao
 *    marcar: as linhas se atualizam ao vivo (o estado observa o banco) e a
 *    pessoa pode revisar varias lojas seguidas.
 * 2. **Acoes do item** - favoritar o produto (estrela, liga o atalho da
 *    folha de Favoritos), tirar da lista inteira (com desfazer na torrada) e
 *    abrir a edicao completa.
 */
@Composable
fun FolhaDeAcoesDoItem(
    item: ItemComProduto,
    estabelecimentos: List<Estabelecimento>,
    precos: List<PrecoRegistrado>,
    aoFechar: () -> Unit,
    aoTirarDaLista: () -> Unit,
    aoIndisponibilizar: (estabelecimentoId: Long) -> Unit,
    aoReativar: (estabelecimentoId: Long) -> Unit,
    aoEditar: () -> Unit,
    produtoFavorito: Boolean = false,
    aoFavoritar: (() -> Unit)? = null,
) {
    val cores = Tema.cores

    FolhaComprix(
        titulo = "O que fazer com \"${item.produto.nome}\"?",
        aoFechar = aoFechar,
    ) {
        Text(
            "Disponibilidade por loja",
            style = MaterialTheme.typography.titleSmall,
            color = cores.texto,
        )
        EspacoVertical(8.dp)

        if (estabelecimentos.isEmpty()) {
            Legenda(
                "Nenhuma loja cadastrada ainda. Toque em “Escolher loja” no alto da lista " +
                    "para criar a primeira coluna de preços.",
            )
        } else {
            estabelecimentos.forEach { loja ->
                LinhaDeDisponibilidade(
                    loja = loja,
                    item = item,
                    precos = precos,
                    aoIndisponibilizar = aoIndisponibilizar,
                    aoReativar = aoReativar,
                )
                EspacoVertical(2.dp)
            }
        }

        Separador(espacoVertical = 10.dp)

        Text(
            "Ações do item",
            style = MaterialTheme.typography.titleSmall,
            color = cores.texto,
        )
        EspacoVertical(6.dp)
        if (aoFavoritar != null) {
            LinhaDeAcao(
                texto = if (produtoFavorito) "Remover dos favoritos" else "Favoritar este produto",
                icone = Icones.estrela,
                tinta = if (produtoFavorito) cores.verdeTinta else cores.texto,
                aoTocar = aoFavoritar,
            )
        }
        LinhaDeAcao(
            texto = "Tirar da lista inteira",
            icone = Icones.excluir,
            tinta = cores.vermelhoTinta,
            aoTocar = aoTirarDaLista,
        )
        LinhaDeAcao(
            texto = "Editar item",
            icone = Icones.editar,
            tinta = cores.texto,
            aoTocar = aoEditar,
        )

        EspacoVertical(10.dp)
        Legenda("Produtos favoritos ficam no menu “Favoritos”, no alto da lista.")
        Legenda("Toque e segure em qualquer produto para ver este menu.")
        EspacoVertical(4.dp)
    }
}

/**
 * Linha de uma loja na folha de acoes: chip colorido, estado do preco naquela
 * coluna e a acao de uma linha so (marcar indisponivel ou limpar a marca).
 */
@Composable
private fun LinhaDeDisponibilidade(
    loja: Estabelecimento,
    item: ItemComProduto,
    precos: List<PrecoRegistrado>,
    aoIndisponibilizar: (estabelecimentoId: Long) -> Unit,
    aoReativar: (estabelecimentoId: Long) -> Unit,
) {
    val cores = Tema.cores

    // Estado derivado dos precos da lista: registro com disponivel=false
    // significa "pesquisei e nao tinha"; sem registro significa "pendente".
    val registro = precos.firstOrNull {
        it.itemDaListaId == item.item.id && it.estabelecimentoId == loja.id
    }
    val indisponivel = registro != null && !registro.disponivel

    val estado: String
    val corDoEstado: Color
    when {
        indisponivel -> {
            estado = "Marcado como indisponível"
            corDoEstado = cores.vermelhoTinta
        }
        registro != null && registro.preco.signum() > 0 -> {
            estado = Formatadores.moeda(registro.preco)
            corDoEstado = cores.verdeTinta
        }
        else -> {
            estado = "Sem preço"
            corDoEstado = cores.apagado
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChipDaLoja(loja)
            Text(
                estado,
                style = MaterialTheme.typography.bodySmall,
                color = corDoEstado,
            )
        }
        if (indisponivel) {
            BotaoComprix(
                "Permitir novamente",
                { aoReativar(loja.id) },
                estilo = EstiloDeBotao.TONAL,
                compacto = true,
                icone = Icones.recomecar,
            )
        } else {
            BotaoComprix(
                "Não tem nessa loja",
                { aoIndisponibilizar(loja.id) },
                estilo = EstiloDeBotao.CONTORNADO,
                compacto = true,
                icone = Icones.indisponivel,
            )
        }
    }
}

/** Chip colorido com o nome da loja (a cor vem do cadastro do estabelecimento). */
@Composable
private fun ChipDaLoja(loja: Estabelecimento) {
    val cor = corDeHex(loja.corHex)
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            loja.nome,
            style = MaterialTheme.typography.labelMedium,
            color = corDeTextoSobre(cor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Linha de acao da folha: icone + rotulo, alvo de toque cheio, sem realce. */
@Composable
private fun LinhaDeAcao(
    texto: String,
    icone: Int,
    tinta: Color,
    aoTocar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ALVO_MINIMO)
            .clip(RoundedCornerShape(13.dp))
            .tocarSemRealce(aoTocar)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconeComprix(icone, null, tamanho = TamanhoDeIcone.padrao, tinta = tinta)
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = tinta,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Folha de **preco faltando**, aberta ao tocar numa pendencia do painel fixo
 * da lista.
 *
 * Resolve a lacuna no lugar: grava o preco daquela loja ou marca "nao tinha" -
 * os dois fecham a pendencia da auditoria. O botao "Salvar preço" parseia o
 * TEXTO VIVO espelhado via [CampoDePreco] (parametro aoDigitar) - nunca depende
 * do evento de foco, que chega depois do toque no botao e ja chegou a ler valor
 * velho (bug v1.2.0: digitava "6,80" e o botao dizia "preco maior que zero").
 * A confirmacao por perda de foco continua valendo: tocar fora do campo
 * grava o valor no proprio campo, com vibracao e som de sucesso.
 */
@Composable
fun FolhaDePrecoDaPendencia(
    nomeProduto: String,
    nomeDaLoja: String,
    aoFechar: () -> Unit,
    aoSalvarPreco: (BigDecimal) -> Unit,
    aoMarcarIndisponivel: () -> Unit,
) {
    val cores = Tema.cores
    val gerenciadorDeFoco = LocalFocusManager.current

    // Texto vivo do campo, espelhado a cada tecla. O botao salva parseando
    // ESTE texto com TextoUtil.paraDecimal (aceita "6,80" e "6.80") - sem
    // depender de clearFocus nem do onFocusChanged do campo.
    var textoDoPreco by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }

    FolhaComprix(
        titulo = "Preço faltando",
        aoFechar = aoFechar,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Não tinha nessa loja",
                    aoMarcarIndisponivel,
                    estilo = EstiloDeBotao.CONTORNADO,
                    icone = Icones.indisponivel,
                )
                BotaoComprix(
                    "Salvar preço",
                    {
                        val valor = TextoUtil.paraDecimal(textoDoPreco)
                        when {
                            valor == null || valor.signum() <= 0 ->
                                aviso = "Digite um preço maior que zero para salvar."
                            valor > BigDecimal(Constantes.PRECO_MAXIMO_ACEITO) ->
                                aviso = "Preço acima do limite aceito — confira os dígitos."
                            else -> {
                                aviso = null
                                // Fecha o teclado; a gravacao em si nao depende
                                // de foco - o valor ja veio do texto espelhado.
                                gerenciadorDeFoco.clearFocus()
                                aoSalvarPreco(valor)
                            }
                        }
                    },
                    bloco = true,
                    modifier = Modifier.weight(1f),
                    icone = Icones.confirmar,
                )
            }
        },
    ) {
        Text(
            nomeProduto,
            style = MaterialTheme.typography.titleMedium,
            color = cores.texto,
        )
        EspacoVertical(7.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Em",
                style = MaterialTheme.typography.bodyMedium,
                color = cores.apagado,
            )
            Selo(texto = nomeDaLoja, tom = TomDoSelo.NEUTRO, icone = Icones.loja)
        }
        EspacoVertical(9.dp)
        Legenda("O mesmo produto, com o campo de preço vazio para esta loja.")
        EspacoVertical(14.dp)

        CampoDePreco(
            valorInicial = null,
            rotulo = "Preço em $nomeDaLoja",
            descricaoAcessibilidade = "Tocar fora do campo também confirma o valor.",
            aoDigitar = { texto ->
                textoDoPreco = texto
                // O aviso sai enquanto o valor digitado for valido - feedback
                // imediato, sem tocar fora nem apertar nada.
                val valor = TextoUtil.paraDecimal(texto)
                if (valor != null && valor.signum() > 0) aviso = null
            },
            // Confirmacao por perda de foco (tap fora): continua valendo no
            // proprio campo (vibracao/som); aqui so alinha o aviso.
            aoConfirmar = { valor ->
                if (valor != null && valor.signum() > 0) aviso = null
            },
        )

        aviso?.let {
            EspacoVertical(6.dp)
            Legenda(it, cor = cores.vermelhoTinta)
        }
        EspacoVertical(8.dp)
    }
}

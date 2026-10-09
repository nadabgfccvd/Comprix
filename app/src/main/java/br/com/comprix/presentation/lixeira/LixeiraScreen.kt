package br.com.comprix.presentation.lixeira

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.data.local.RegistroDeLixeiraEntity
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstadoVazio
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Formatadores
import java.time.LocalDateTime

/**
 * **Lixeira** (nova na 1.4.0): tudo que o usuario excluiu fica aqui por 30
 * dias antes da exclusao permanente.
 *
 * Estrutura, de cima para baixo:
 *
 * 1. barra com voltar (a tela abre pelos Ajustes);
 * 2. aviso FIXO do prazo automatico de 30 dias;
 * 3. linha de chips rolaveis "Todas" + as seis categorias, cada uma com a
 *    contagem de registros;
 * 4. acoes de esvaziar (sempre com DUPLA confirmacao: aviso com o numero ->
 *    dialogo PERIGO definitivo);
 * 5. cartoes: titulo, detalhe, categoria, a data em que some para sempre,
 *    "Restaurar" e o "excluir agora" individual (confirmacao simples).
 */
@Composable
fun LixeiraScreen(viewModel: LixeiraViewModel, aoVoltar: () -> Unit) {
    val cores = Tema.cores
    val registros by viewModel.registros.collectAsStateWithLifecycle()
    val contagens by viewModel.contagens.collectAsStateWithLifecycle()
    val tipoSelecionado by viewModel.tipoSelecionado.collectAsStateWithLifecycle()
    val visiveis by viewModel.registrosVisiveis.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()

    // Confirmacao simples do "excluir agora" individual.
    var registroParaExcluir by remember { mutableStateOf<RegistroDeLixeiraEntity?>(null) }
    // Dupla confirmacao do esvaziar: o primeiro dialogo avisa com o numero e
    // o "Sim, apagar" abre o segundo, com o botao PERIGO definitivo.
    var esvaziandoTudo by remember { mutableStateOf(false) }
    var esvaziandoCategoria by remember { mutableStateOf<LixeiraRepositorio.TipoDeLixeira?>(null) }
    var etapaFinalDoEsvaziar by remember { mutableStateOf(false) }

    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            kotlinx.coroutines.delay(3_200)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = { BarraSimples("Lixeira", aoVoltar = aoVoltar) },
        sobreposicao = {
            if (mensagem != null) {
                Torrada(
                    mensagem.orEmpty(),
                    aoDescartar = { viewModel.mensagemExibida() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp)) {
                // Aviso fixo: fica no topo, acima do filtro, em qualquer rolagem.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                        .background(cores.verdeSuave)
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeComprix(
                        Icones.relogio,
                        null,
                        tamanho = TamanhoDeIcone.pequeno,
                        tinta = cores.verdeTinta,
                    )
                    Text(
                        "Tudo aqui será apagado automaticamente 30 dias depois de ir para a lixeira.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.verdeTinta,
                        modifier = Modifier.weight(1f),
                    )
                }

                EspacoVertical(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PastilhaSelecionavel(
                        texto = "Todas (${registros.size})",
                        selecionada = tipoSelecionado == null,
                        aoTocar = { viewModel.selecionarTipo(null) },
                    )
                    LixeiraRepositorio.TipoDeLixeira.entries.forEach { tipo ->
                        PastilhaSelecionavel(
                            texto = "${tipo.rotulo} (${contagens[tipo] ?: 0})",
                            selecionada = tipoSelecionado == tipo,
                            aoTocar = {
                                // Toque na pastilha ativa recolhe o filtro,
                                // como nos setores do catalogo.
                                viewModel.selecionarTipo(if (tipoSelecionado == tipo) null else tipo)
                            },
                        )
                    }
                }

                if (registros.isNotEmpty()) {
                    EspacoVertical(10.dp)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val categoriaAberta = tipoSelecionado
                        if (categoriaAberta != null && (contagens[categoriaAberta] ?: 0) > 0) {
                            BotaoComprix(
                                "Esvaziar “${categoriaAberta.rotulo}”",
                                {
                                    esvaziandoCategoria = categoriaAberta
                                    esvaziandoTudo = false
                                    etapaFinalDoEsvaziar = false
                                },
                                estilo = EstiloDeBotao.CONTORNADO,
                                compacto = true,
                                icone = Icones.excluir,
                            )
                        }
                        BotaoComprix(
                            "Esvaziar tudo",
                            {
                                esvaziandoTudo = true
                                esvaziandoCategoria = null
                                etapaFinalDoEsvaziar = false
                            },
                            estilo = EstiloDeBotao.CONTORNADO,
                            compacto = true,
                            icone = Icones.excluir,
                        )
                    }
                }
            }

            if (visiveis.isEmpty()) {
                EstadoVazio(
                    icone = Icones.excluir,
                    titulo = if (tipoSelecionado == null) {
                        "A lixeira está vazia."
                    } else {
                        "A lixeira desta categoria está vazia."
                    },
                    descricao = "O que você excluir no app aparece aqui e fica " +
                        "recuperável por 30 dias.",
                ) {
                    if (tipoSelecionado != null) {
                        BotaoComprix(
                            "Ver todas as categorias",
                            { viewModel.selecionarTipo(null) },
                            estilo = EstiloDeBotao.CONTORNADO,
                            icone = Icones.recomecar,
                        )
                    } else {
                        BotaoComprix("Voltar", aoVoltar, icone = Icones.voltar)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
                    items(visiveis, key = { it.id }) { registro ->
                        CartaoDeRegistro(
                            registro = registro,
                            rotuloDoTipo = rotuloDoTipo(registro.tipo),
                            expiraEm = viewModel.expiraEm(registro),
                            aoRestaurar = { viewModel.restaurar(registro.id) },
                            aoExcluirAgora = { registroParaExcluir = registro },
                        )
                        EspacoVertical(12.dp)
                    }
                    item { EspacoVertical(14.dp) }
                }
            }
        }
    }

    // --- confirmacao simples do excluir agora ---------------------------------------
    registroParaExcluir?.let { registro ->
        DialogoComprix(
            titulo = "Apagar \"${registro.titulo}\" agora?",
            aoFechar = { registroParaExcluir = null },
            icone = Icones.excluir,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { registroParaExcluir = null },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Apagar agora",
                        {
                            viewModel.removerRegistro(registro.id)
                            registroParaExcluir = null
                        },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                        icone = Icones.excluir,
                    )
                }
            },
        ) {
            Text(
                "Ele sai da lixeira e é apagado para sempre — depois disso não " +
                    "dá mais para restaurar.",
                style = MaterialTheme.typography.bodyMedium,
                color = cores.texto,
            )
        }
    }

    // --- dupla confirmacao do esvaziar: passo 1 (aviso com o numero) ----------------
    // O acesso a contagens usa safe-call (o filtro e nullable e o get do Map
    // nao aceita chave nula).
    val quantidadeDoEsvaziar = when {
        esvaziandoTudo -> registros.size
        else -> esvaziandoCategoria?.let { tipo -> contagens[tipo] } ?: 0
    }
    val esvaziarPendente = esvaziandoTudo || esvaziandoCategoria != null

    fun descartarEsvaziar() {
        esvaziandoTudo = false
        esvaziandoCategoria = null
        etapaFinalDoEsvaziar = false
    }

    if (esvaziarPendente && !etapaFinalDoEsvaziar) {
        DialogoComprix(
            titulo = "Isto apagará $quantidadeDoEsvaziar " +
                "${if (quantidadeDoEsvaziar == 1) "registro" else "registros"} para sempre. Continuar?",
            aoFechar = { descartarEsvaziar() },
            icone = Icones.excluir,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { descartarEsvaziar() },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Sim, apagar",
                        { etapaFinalDoEsvaziar = true },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            Text(
                if (esvaziandoTudo) {
                    "Todos os registros da lixeira sairão daqui agora, de todas as categorias."
                } else {
                    "Todos os registros de \"${esvaziandoCategoria?.rotulo.orEmpty()}\" sairão daqui agora."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cores.texto,
            )
        }
    }

    // --- dupla confirmacao do esvaziar: passo 2 (PERIGO definitivo) ------------------
    if (esvaziarPendente && etapaFinalDoEsvaziar) {
        DialogoComprix(
            titulo = "A exclusão será PERMANENTE. Não dá para desfazer.",
            aoFechar = { descartarEsvaziar() },
            icone = Icones.alerta,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { descartarEsvaziar() },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Apagar definitivamente",
                        {
                            if (esvaziandoTudo) {
                                viewModel.esvaziarTudo()
                            }
                            esvaziandoCategoria?.let { viewModel.esvaziarCategoria(it) }
                            descartarEsvaziar()
                        },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                        icone = Icones.excluir,
                    )
                }
            },
        ) {
            Text(
                "Depois de confirmar, os registros saem da lixeira e nada no app " +
                    "consegue trazê-los de volta.",
                style = MaterialTheme.typography.bodyMedium,
                color = cores.texto,
            )
        }
    }
}

/**
 * Cartao de um registro da lixeira: titulo, detalhe, categoria, a data da
 * exclusao permanente e as duas acoes (Restaurar / excluir agora).
 */
@Composable
private fun CartaoDeRegistro(
    registro: RegistroDeLixeiraEntity,
    rotuloDoTipo: String,
    expiraEm: LocalDateTime,
    aoRestaurar: () -> Unit,
    aoExcluirAgora: () -> Unit,
) {
    val cores = Tema.cores
    CartaoComprix(preenchimento = PaddingValues(15.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    registro.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = cores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                registro.detalhe?.let { detalhe ->
                    EspacoVertical(3.dp)
                    Legenda(detalhe)
                }
            }
            BotaoDeIcone(
                Icones.excluir,
                "Apagar agora",
                aoExcluirAgora,
                tinta = cores.vermelhoTinta,
            )
        }

        EspacoVertical(8.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Selo(texto = rotuloDoTipo, tom = TomDoSelo.NEUTRO)
            Legenda("Excluído permanentemente em ${Formatadores.dataSemAno(expiraEm)}")
        }

        EspacoVertical(9.dp)
        BotaoComprix(
            "Restaurar",
            aoRestaurar,
            estilo = EstiloDeBotao.TEXTO,
            compacto = true,
            icone = Icones.recomecar,
        )
    }
}

/** Rotulo legivel do tipo guardado no registro ("Listas", "Lojas", ...). */
private fun rotuloDoTipo(tipo: String): String =
    LixeiraRepositorio.TipoDeLixeira.entries.firstOrNull { it.name == tipo }?.rotulo ?: tipo

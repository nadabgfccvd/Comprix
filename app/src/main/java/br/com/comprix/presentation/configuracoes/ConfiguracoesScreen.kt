package br.com.comprix.presentation.configuracoes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.TipoTema
import br.com.comprix.presentation.comum.BarraSimples
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.BotaoDeIcone
import br.com.comprix.presentation.comum.CampoComprix
import br.com.comprix.presentation.comum.CartaoComprix
import br.com.comprix.presentation.comum.ChaveComprix
import br.com.comprix.presentation.comum.DeslizanteComprix
import br.com.comprix.presentation.comum.DialogoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.Legenda
import br.com.comprix.presentation.comum.LinhaDeAjuste
import br.com.comprix.presentation.comum.LinhaNavegavel
import br.com.comprix.presentation.comum.OpcaoSegmentada
import br.com.comprix.presentation.comum.PastilhaSelecionavel
import br.com.comprix.presentation.comum.PREENCHIMENTO_DA_TELA
import br.com.comprix.presentation.comum.Segmentado
import br.com.comprix.presentation.comum.Selo
import br.com.comprix.presentation.comum.Separador
import br.com.comprix.presentation.comum.TelaComprix
import br.com.comprix.presentation.comum.TituloDaTela
import br.com.comprix.presentation.comum.TomDoSelo
import br.com.comprix.presentation.comum.Torrada
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * **Configurações** (tela 08 da referencia).
 *
 * Quatro blocos, nesta ordem de uso real: perfil de restricoes, aparencia e
 * acessibilidade, avancado (modo tecnico) e backup local.
 *
 * ## Sobre o backup
 *
 * O arquivo e escrito pelo seletor do proprio sistema (`CreateDocument`), que
 * e o unico caminho que funciona sem pedir permissao de armazenamento e sem
 * `FileProvider`. O conteudo e texto simples, legivel, com os dados do
 * aparelho - inclusive o aprendizado do parser.
 */
@Composable
fun ConfiguracoesScreen(
    viewModel: ConfiguracoesViewModel,
    aoVoltar: () -> Unit,
    aoAbrirCategorias: () -> Unit,
    aoAbrirNutricional: () -> Unit,
    aoReverBoasVindas: () -> Unit,
    navegacao: @Composable () -> Unit,
    aoAbrirCatalogo: (() -> Unit)? = null,
    aoAbrirLixeira: (() -> Unit)? = null,
) {
    val cores = Tema.cores
    val contexto = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val alergiasCustomizadas by viewModel.alergiasCustomizadas.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val confirmarModoTecnico by viewModel.confirmarModoTecnico.collectAsStateWithLifecycle()

    var limpandoCategorias by remember { mutableStateOf(false) }
    var limpandoParser by remember { mutableStateOf(false) }

    // Formulario de "Minhas restricoes personalizadas" e a remocao em confirmacao.
    var nomeDaRestricao by remember { mutableStateOf("") }
    var palavrasDaRestricao by remember { mutableStateOf("") }
    var alergiaParaRemover by remember { mutableStateOf<AlergiaCustomizada?>(null) }

    // Dialogo da meta de economia mensal.
    var editandoMeta by remember { mutableStateOf(false) }
    var textoDaMeta by remember { mutableStateOf("") }

    val validades by viewModel.validadesProximas.collectAsStateWithLifecycle()

    val escolherDestino = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destino -> destino?.let { viewModel.exportarPara(contexto, it) } }

    val escolherOrigem = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { origem -> origem?.let { viewModel.restaurar(contexto, it) } }

    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            kotlinx.coroutines.delay(3_400)
            viewModel.mensagemExibida()
        }
    }

    TelaComprix(
        barra = { BarraSimples("Configurações", aoVoltar = aoVoltar) },
        navegacao = navegacao,
        sobreposicao = {
            if (mensagem != null) {
                Torrada(
                    mensagem.orEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PREENCHIMENTO_DA_TELA) {
            item {
                TituloDaTela("Configurações")
                EspacoVertical(16.dp)
            }

            // --- perfil de restricoes -------------------------------------------
            item {
                CartaoComprix(preenchimento = PaddingValues(16.dp)) {
                    Text(
                        "Perfil de restrições e alergênicos",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(5.dp)
                    Legenda(
                        "O Comprix avisa quando um item da lista conflita com o que " +
                            "você marcar aqui. Ele nunca esconde nem bloqueia produtos.",
                    )
                    EspacoVertical(12.dp)
                    PastilhaSelecionavel(
                        texto = "Sem glúten",
                        selecionada = estado.perfil.semGluten,
                        aoTocar = { viewModel.definirSemGluten(!estado.perfil.semGluten) },
                        icone = Icones.trigo,
                        dieta = true,
                    )
                    EspacoVertical(9.dp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Alergeno.entries.forEach { alergeno ->
                            PastilhaSelecionavel(
                                texto = alergeno.rotulo,
                                selecionada = alergeno in estado.perfil.alergenos,
                                aoTocar = { viewModel.alternarAlergeno(alergeno) },
                                dieta = true,
                            )
                        }
                    }

                    // --- Minhas restricoes personalizadas ----------------------------
                    EspacoVertical(14.dp)
                    Separador(espacoVertical = 0.dp)
                    EspacoVertical(13.dp)
                    Text(
                        "Minhas restrições personalizadas",
                        style = MaterialTheme.typography.titleSmall,
                        color = cores.texto,
                    )
                    EspacoVertical(5.dp)
                    Legenda(
                        "Restrições suas, fora da lista oficial. O app compara o nome e os " +
                            "ingredientes dos produtos com o que você cadastrar aqui.",
                    )
                    EspacoVertical(10.dp)
                    alergiasCustomizadas.forEach { alergia ->
                        LinhaDeRestricaoPersonalizada(
                            alergia = alergia,
                            aoRemover = { alergiaParaRemover = alergia },
                        )
                        EspacoVertical(7.dp)
                    }
                    CampoComprix(
                        valor = nomeDaRestricao,
                        aoMudar = { nomeDaRestricao = it },
                        rotulo = "Nome da restrição (ex.: corante amarelo)",
                    )
                    EspacoVertical(9.dp)
                    CampoComprix(
                        valor = palavrasDaRestricao,
                        aoMudar = { palavrasDaRestricao = it },
                        rotulo = "Palavras que denunciam, separadas por vírgula (opcional)",
                    )
                    EspacoVertical(11.dp)
                    BotaoComprix(
                        "Adicionar restrição",
                        {
                            viewModel.adicionarAlergiaCustomizada(nomeDaRestricao, palavrasDaRestricao)
                            if (nomeDaRestricao.isNotBlank()) {
                                nomeDaRestricao = ""
                                palavrasDaRestricao = ""
                            }
                        },
                        bloco = true,
                        icone = Icones.adicionar,
                    )
                    if (estado.perfil.ativo) {
                        EspacoVertical(10.dp)
                        Selo(
                            texto = "Perfil ativo — alertas ligados na lista e na comparação",
                            tom = TomDoSelo.VERDE,
                            icone = Icones.escudo,
                        )
                    }
                }
                EspacoVertical(12.dp)
            }

            // --- validades proximas -------------------------------------------------
            item {
                CartaoDeValidades(validades = validades, hoje = LocalDate.now())
                EspacoVertical(12.dp)
            }

            // --- meta de economia ----------------------------------------------------
            item {
                CartaoDaMeta(
                    metaCentavos = estado.configuracoes.metaEconomiaCentavos,
                    aoDefinir = { editandoMeta = true },
                    aoRemover = { viewModel.removerMetaEconomia() },
                )
                EspacoVertical(12.dp)
            }

            // --- aparencia e acessibilidade ---------------------------------------
            item {
                CartaoComprix(preenchimento = PaddingValues(16.dp)) {
                    Text(
                        "Aparência e acessibilidade",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(12.dp)
                    Legenda("Tema")
                    EspacoVertical(7.dp)
                    Segmentado(
                        opcoes = listOf(
                            OpcaoSegmentada("Sistema", Icones.sistema),
                            OpcaoSegmentada("Claro", Icones.sol),
                            OpcaoSegmentada("Escuro", Icones.lua),
                        ),
                        indiceSelecionado = TipoTema.entries.indexOf(estado.configuracoes.tema),
                        aoSelecionar = { viewModel.definirTema(TipoTema.entries[it]) },
                    )

                    EspacoVertical(6.dp)
                    LinhaDeAjuste(
                        titulo = "Alto contraste",
                        descricao = "Bordas mais fortes, texto mais escuro e nenhuma sombra.",
                        icone = Icones.contraste,
                    ) {
                        ChaveComprix(
                            estado.configuracoes.altoContraste,
                            viewModel::definirAltoContraste,
                            nomeAcessivel = "Alto contraste",
                        )
                    }

                    LinhaDeAjuste(
                        titulo = "Cores do sistema (Material You)",
                        descricao = "Desligado por padrão para manter a identidade do Comprix. " +
                            "Disponível no Android 12 ou mais novo.",
                        icone = Icones.brilho,
                    ) {
                        ChaveComprix(
                            estado.configuracoes.coresDinamicas,
                            viewModel::definirCoresDinamicas,
                            nomeAcessivel = "Cores do sistema",
                        )
                    }

                    Separador(espacoVertical = 10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tamanho do texto",
                            style = MaterialTheme.typography.titleSmall,
                            color = cores.texto,
                            modifier = Modifier.weight(1f),
                        )
                        Selo("${estado.configuracoes.escalaDaFonte}%", tom = TomDoSelo.VERDE)
                    }
                    DeslizanteComprix(
                        valor = estado.configuracoes.escalaDaFonte.toFloat(),
                        aoMudar = { viewModel.definirEscalaDaFonte(it.toInt()) },
                        faixa = 100f..160f,
                        passos = 11,
                        descricao = "Tamanho do texto, ${estado.configuracoes.escalaDaFonte} por cento",
                    )
                    Legenda(
                        "Soma-se ao tamanho de fonte do sistema. Todos os botões " +
                            "mantêm 48 dp de área de toque em qualquer escala.",
                    )

                    Separador(espacoVertical = 12.dp)
                    LinhaDeAjuste(
                        titulo = "Sons",
                        descricao = "Confirmação curta ao marcar item e ao gravar preço.",
                        icone = Icones.som,
                    ) {
                        ChaveComprix(
                            estado.configuracoes.sons,
                            viewModel::definirSons,
                            nomeAcessivel = "Sons",
                        )
                    }
                    LinhaDeAjuste(
                        titulo = "Vibração",
                        descricao = "Retorno tátil no scanner e nas ações destrutivas.",
                        icone = Icones.sistema,
                    ) {
                        ChaveComprix(
                            estado.configuracoes.vibracao,
                            viewModel::definirVibracao,
                            nomeAcessivel = "Vibração",
                        )
                    }
                }
                EspacoVertical(12.dp)
            }

            // --- avancado -----------------------------------------------------------
            item {
                CartaoComprix(preenchimento = PaddingValues(16.dp)) {
                    Text("Avançado", style = MaterialTheme.typography.titleMedium, color = cores.texto)
                    EspacoVertical(6.dp)
                    LinhaDeAjuste(
                        titulo = "Modo técnico",
                        descricao = "Habilita a comparação nutricional detalhada entre produtos.",
                        icone = Icones.nutricao,
                        destacada = estado.configuracoes.modoTecnico,
                    ) {
                        ChaveComprix(
                            estado.configuracoes.modoTecnico,
                            viewModel::pedirModoTecnico,
                            nomeAcessivel = "Modo técnico",
                        )
                    }
                    if (estado.configuracoes.modoTecnico) {
                        EspacoVertical(9.dp)
                        BotaoComprix(
                            "Comparar informações nutricionais",
                            aoAbrirNutricional,
                            bloco = true,
                            estilo = EstiloDeBotao.TECNICO,
                            icone = Icones.comparar,
                        )
                    }

                    EspacoVertical(12.dp)
                    LinhaNavegavel(
                        titulo = "Categorias e ordem do mercado",
                        resumo = "Renomear, criar e reordenar os grupos da lista",
                        aoTocar = aoAbrirCategorias,
                        icone = Icones.camadas,
                    )
                    if (aoAbrirCatalogo != null) {
                        EspacoVertical(9.dp)
                        LinhaNavegavel(
                            titulo = "Explorar catálogo",
                            resumo = "Buscar entre os produtos que já vêm no app e mandar direto para uma lista",
                            aoTocar = aoAbrirCatalogo,
                            icone = Icones.catalogo,
                        )
                    }
                    if (aoAbrirLixeira != null) {
                        EspacoVertical(9.dp)
                        LinhaNavegavel(
                            titulo = "Lixeira",
                            resumo = "Restaure o que você excluiu — listas, lojas e mais ficam recuperáveis por 30 dias",
                            aoTocar = aoAbrirLixeira,
                            icone = Icones.excluir,
                        )
                    }
                    EspacoVertical(9.dp)
                    LinhaNavegavel(
                        titulo = "Rever as boas-vindas",
                        resumo = "Abre novamente a apresentação em quatro passos",
                        aoTocar = aoReverBoasVindas,
                        icone = Icones.informacao,
                    )
                    EspacoVertical(9.dp)
                    LinhaNavegavel(
                        titulo = "Mostrar as dicas de novo",
                        resumo = "Traz de volta as explicações dispensadas nas telas",
                        aoTocar = { viewModel.reexibirDicas() },
                        icone = Icones.brilho,
                    )
                }
                EspacoVertical(12.dp)
            }

            // --- aprendizado local ---------------------------------------------------
            item {
                CartaoComprix(preenchimento = PaddingValues(16.dp)) {
                    // Secao centralizada: titulo e descricao no centro, linhas de
                    // decisao com separador fino e o Esquecer a direita.
                    Text(
                        "O que o Comprix aprendeu",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EspacoVertical(5.dp)
                    Text(
                        "Correções de categoria e respostas às sugestões de quantidade " +
                            "ficam gravadas só aqui, para o app não repetir a mesma pergunta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cores.apagado,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EspacoVertical(12.dp)
                    if (estado.decisoesDoParser.isEmpty()) {
                        Text(
                            "Nada aprendido ainda — use o app normalmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cores.apagado,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        estado.decisoesDoParser.take(8).forEachIndexed { indice, decisao ->
                            if (indice > 0) Separador(espacoVertical = 4.dp)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        decisao.termo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cores.texto,
                                    )
                                    Legenda(
                                        "${decisao.decisao}" +
                                            (decisao.valor?.let { " · $it" } ?: "") +
                                            " · ${decisao.vezes}×",
                                    )
                                }
                                BotaoComprix(
                                    "Esquecer",
                                    { viewModel.esquecerDecisao(decisao.termo) },
                                    estilo = EstiloDeBotao.TEXTO,
                                    compacto = true,
                                )
                            }
                        }
                    }
                    EspacoVertical(10.dp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BotaoComprix(
                            "Esquecer categorias",
                            { limpandoCategorias = true },
                            estilo = EstiloDeBotao.CONTORNADO,
                            compacto = true,
                            icone = Icones.recomecar,
                        )
                        BotaoComprix(
                            "Esquecer interpretações",
                            { limpandoParser = true },
                            estilo = EstiloDeBotao.CONTORNADO,
                            compacto = true,
                            icone = Icones.recomecar,
                        )
                    }
                }
                EspacoVertical(12.dp)
            }

            // --- backup ---------------------------------------------------------------
            item {
                CartaoComprix(preenchimento = PaddingValues(16.dp)) {
                    Text(
                        "Backup local (sem nuvem, 100% offline)",
                        style = MaterialTheme.typography.titleMedium,
                        color = cores.texto,
                    )
                    EspacoVertical(5.dp)
                    Legenda(estado.resumoDoUltimoBackup)
                    EspacoVertical(12.dp)
                    BotaoComprix(
                        "Exportar backup para arquivo",
                        {
                            escolherDestino.launch(
                                "comprix-backup-${Formatadores.carimboDeArquivo()}.cbk",
                            )
                        },
                        bloco = true,
                        icone = Icones.enviar,
                        habilitado = !estado.ocupado,
                    )
                    EspacoVertical(9.dp)
                    BotaoComprix(
                        "Restaurar de um arquivo",
                        { escolherOrigem.launch(arrayOf("*/*")) },
                        bloco = true,
                        estilo = EstiloDeBotao.CONTORNADO,
                        icone = Icones.baixar,
                        habilitado = !estado.ocupado,
                    )
                    EspacoVertical(10.dp)
                    Legenda(
                        "O arquivo sai do app pelo seletor do próprio Android, " +
                            "para onde você escolher. Nada é enviado para servidor nenhum.",
                    )

                    if (estado.backups.isNotEmpty()) {
                        Separador(espacoVertical = 12.dp)
                        Text(
                            "Backups automáticos neste aparelho",
                            style = MaterialTheme.typography.titleSmall,
                            color = cores.texto,
                        )
                        EspacoVertical(8.dp)
                        estado.backups.take(5).forEach { arquivo ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        arquivo.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cores.texto,
                                    )
                                    Legenda("${arquivo.length() / 1024} KB")
                                }
                                BotaoComprix(
                                    "Restaurar",
                                    { viewModel.restaurarDeArquivo(arquivo) },
                                    estilo = EstiloDeBotao.TEXTO,
                                    compacto = true,
                                )
                            }
                        }
                    }
                }
                EspacoVertical(16.dp)
            }

            item {
                Selo(
                    texto = "O Comprix não pede INTERNET no manifesto do Android",
                    tom = TomDoSelo.VERDE,
                    icone = Icones.offline,
                )
                EspacoVertical(20.dp)
            }
        }
    }

    // --- diálogos ----------------------------------------------------------------

    if (confirmarModoTecnico) {
        DialogoComprix(
            titulo = "Ligar o modo técnico?",
            aoFechar = viewModel::cancelarModoTecnico,
            icone = Icones.nutricao,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Agora não",
                        viewModel::cancelarModoTecnico,
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Ligar",
                        viewModel::confirmarModoTecnico,
                        estilo = EstiloDeBotao.TECNICO,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            Text(
                "O modo técnico mostra a tabela nutricional completa e permite " +
                    "comparar nutrientes entre produtos. Ele é uma ferramenta de " +
                    "leitura: o Comprix não dá nota, não classifica e não recomenda " +
                    "alimentos. Para decisões de saúde, fale com um profissional.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tema.cores.apagado,
            )
        }
    }

    if (limpandoCategorias) {
        DialogoDeConfirmacao(
            titulo = "Esquecer as correções de categoria?",
            corpo = "O Comprix volta a usar só o dicionário interno para adivinhar a " +
                "categoria dos produtos. As categorias já aplicadas aos itens não mudam.",
            aoFechar = { limpandoCategorias = false },
            aoConfirmar = {
                viewModel.esquecerCategorias()
                limpandoCategorias = false
            },
        )
    }

    if (limpandoParser) {
        DialogoDeConfirmacao(
            titulo = "Esquecer as interpretações?",
            corpo = "As respostas que você deu às sugestões de quantidade são apagadas. " +
                "O Comprix pode voltar a perguntar sobre termos como “dúzia” ou “fardo”.",
            aoFechar = { limpandoParser = false },
            aoConfirmar = {
                viewModel.esquecerDecisoesDoParser()
                limpandoParser = false
            },
        )
    }

    if (editandoMeta) {
        DialogoComprix(
            titulo = "Meta de economia mensal",
            aoFechar = { editandoMeta = false },
            icone = Icones.trofeu,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { editandoMeta = false },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        "Definir",
                        {
                            viewModel.definirMetaEconomia(textoDaMeta)
                            textoDaMeta = ""
                            editandoMeta = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            CampoComprix(
                valor = textoDaMeta,
                aoMudar = { textoDaMeta = it },
                rotulo = "Quanto você quer economizar por mês",
                prefixo = "R$",
                dica = "Aceita vírgula, como 120,50",
                tipoDeTeclado = KeyboardType.Decimal,
            )
        }
    }

    alergiaParaRemover?.let { alvo ->
        DialogoComprix(
            titulo = "Remover \"${alvo.nome}\"?",
            aoFechar = { alergiaParaRemover = null },
            icone = Icones.excluir,
            rodape = {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BotaoComprix(
                        "Cancelar",
                        { alergiaParaRemover = null },
                        estilo = EstiloDeBotao.CONTORNADO,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoComprix(
                        // Acao destrutiva: mesmo estilo PERIGO dos outros
                        // dialogos de remocao do app (listas, lojas, historico).
                        "Remover",
                        {
                            viewModel.removerAlergiaCustomizada(alvo.id)
                            alergiaParaRemover = null
                        },
                        estilo = EstiloDeBotao.PERIGO,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            Text(
                "O app deixa de procurar essa restrição no nome e nos ingredientes dos " +
                    "produtos. Você pode cadastrá-la de novo quando quiser.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tema.cores.apagado,
            )
        }
    }
}

@Composable
private fun DialogoDeConfirmacao(
    titulo: String,
    corpo: String,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    DialogoComprix(
        titulo = titulo,
        aoFechar = aoFechar,
        icone = Icones.recomecar,
        rodape = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotaoComprix(
                    "Cancelar",
                    aoFechar,
                    estilo = EstiloDeBotao.CONTORNADO,
                    modifier = Modifier.weight(1f),
                )
                BotaoComprix("Esquecer", aoConfirmar, modifier = Modifier.weight(1f))
            }
        },
    ) {
        Text(corpo, style = MaterialTheme.typography.bodyMedium, color = Tema.cores.apagado)
    }
}

/**
 * Linha discreta de "Minhas restricoes personalizadas": o nome da restricao,
 * as palavras-chave em texto pequeno e o X de remover.
 */
@Composable
private fun LinhaDeRestricaoPersonalizada(
    alergia: AlergiaCustomizada,
    aoRemover: () -> Unit,
) {
    val cores = Tema.cores
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cores.fundo)
            .border(1.dp, cores.contorno, RoundedCornerShape(12.dp))
            .padding(start = 13.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                alergia.nome,
                style = MaterialTheme.typography.bodyMedium,
                color = cores.texto,
            )
            if (alergia.palavras.isNotEmpty()) {
                Legenda(alergia.palavras.joinToString(", "))
            }
        }
        BotaoDeIcone(
            Icones.fechar,
            "Remover ${alergia.nome}",
            aoRemover,
            tinta = cores.apagado,
            tamanhoDoIcone = TamanhoDeIcone.pequeno,
        )
    }
}

/**
 * Painel "Validades proximas": produtos com data de validade vencendo nos
 * proximos 30 dias (ou ja vencidos), do mais urgente para o mais folgado.
 *
 * Codigo de cor da data: vermelho para vencido e para ate 7 dias, ambar para
 * o resto da janela de 30 dias.
 */
@Composable
private fun CartaoDeValidades(validades: List<Produto>, hoje: LocalDate) {
    val cores = Tema.cores
    CartaoComprix(preenchimento = PaddingValues(16.dp)) {
        Text(
            "Validades próximas",
            style = MaterialTheme.typography.titleMedium,
            color = cores.texto,
        )
        EspacoVertical(5.dp)
        if (validades.isEmpty()) {
            Legenda("Nada perto de vencer nos próximos 30 dias.")
            EspacoVertical(3.dp)
            Legenda("Cadastre a data de validade na ficha do produto (ícone de calendário).")
        } else {
            validades.take(15).forEach { produto ->
                LinhaDeValidade(produto, hoje)
                EspacoVertical(7.dp)
            }
            if (validades.size > 15) {
                Legenda("E mais ${validades.size - 15} produtos com validade nos próximos 30 dias.")
            }
        }
    }
}

/** Uma linha do painel: nome do produto a esquerda, selo do prazo a direita. */
@Composable
private fun LinhaDeValidade(produto: Produto, hoje: LocalDate) {
    val validade = produto.dataValidade ?: return
    val cores = Tema.cores
    val dias = ChronoUnit.DAYS.between(hoje, validade)
    val (textoDoSelo, tom) = when {
        dias < 0 -> "Venceu em ${Formatadores.dataSemAno(validade)}" to TomDoSelo.ALERTA
        dias == 0L -> "Vence hoje" to TomDoSelo.ALERTA
        dias == 1L -> "Vence amanhã" to TomDoSelo.ALERTA
        dias <= 7 -> "Vence em $dias dias" to TomDoSelo.ALERTA
        else -> "Vence em $dias dias" to TomDoSelo.OURO
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            produto.nome,
            style = MaterialTheme.typography.bodyMedium,
            color = cores.texto,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Selo(texto = textoDoSelo, tom = tom, icone = Icones.calendario)
    }
}

/**
 * Cartao da meta de economia mensal: mostra o valor vigente ou avisa que a
 * meta nao foi definida, e abre o dialogo de edicao; quando existe meta,
 * oferece o Remover.
 */
@Composable
private fun CartaoDaMeta(
    metaCentavos: Long?,
    aoDefinir: () -> Unit,
    aoRemover: () -> Unit,
) {
    val cores = Tema.cores
    CartaoComprix(preenchimento = PaddingValues(16.dp)) {
        Text(
            "Meta de economia mensal",
            style = MaterialTheme.typography.titleMedium,
            color = cores.texto,
        )
        EspacoVertical(5.dp)
        Legenda(
            "Quanto você quer economizar por mês, somando a economia que o " +
                "Comprix registra nas suas compras. Acompanhe na aba de histórico.",
        )
        EspacoVertical(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Legenda("Meta atual")
                Text(
                    metaCentavos?.let { Formatadores.moeda(BigDecimal.valueOf(it, Constantes.ESCALA_MOEDA)) }
                        ?: "Não definida",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (metaCentavos != null) cores.verdeTinta else cores.apagado,
                )
            }
            if (metaCentavos != null) {
                BotaoComprix(
                    "Remover",
                    aoRemover,
                    estilo = EstiloDeBotao.TEXTO,
                    compacto = true,
                )
            }
            BotaoComprix(
                if (metaCentavos == null) "Definir" else "Alterar",
                aoDefinir,
                estilo = EstiloDeBotao.CONTORNADO,
                compacto = true,
            )
        }
    }
}

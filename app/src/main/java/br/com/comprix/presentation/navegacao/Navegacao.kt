package br.com.comprix.presentation.navegacao

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.comprix.data.camera.LeitorDeImagem
import br.com.comprix.di.ServiceLocator
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.presentation.categorias.CategoriasScreen
import br.com.comprix.presentation.categorias.CategoriasViewModel
import br.com.comprix.presentation.catalogo.CatalogoScreen
import br.com.comprix.presentation.catalogo.CatalogoViewModel
import br.com.comprix.presentation.comparacao.ComparacaoScreen
import br.com.comprix.presentation.comparacao.ComparacaoSemLista
import br.com.comprix.presentation.comum.DestinoInferior
import br.com.comprix.presentation.comum.NavegacaoInferior
import br.com.comprix.presentation.comum.fabricaDe
import br.com.comprix.presentation.configuracoes.ConfiguracoesScreen
import br.com.comprix.presentation.configuracoes.ConfiguracoesViewModel
import br.com.comprix.presentation.historico.HistoricoScreen
import br.com.comprix.presentation.historico.HistoricoViewModel
import br.com.comprix.presentation.lista.ListaScreen
import br.com.comprix.presentation.lista.ListaViewModel
import br.com.comprix.presentation.listas.ListasScreen
import br.com.comprix.presentation.listas.ListasViewModel
import br.com.comprix.presentation.lixeira.LixeiraScreen
import br.com.comprix.presentation.lixeira.LixeiraViewModel
import br.com.comprix.presentation.lojas.LojasScreen
import br.com.comprix.presentation.lojas.LojasViewModel
import br.com.comprix.presentation.nutricional.NutricionalScreen
import br.com.comprix.presentation.nutricional.NutricionalViewModel
import br.com.comprix.presentation.onboarding.OnboardingScreen
import br.com.comprix.presentation.precos.PrecosViewModel
import br.com.comprix.presentation.produto.ProdutoScreen
import br.com.comprix.presentation.produto.ProdutoViewModel
import br.com.comprix.presentation.scanner.ModoScanner
import br.com.comprix.presentation.scanner.ScannerScreen
import br.com.comprix.presentation.scanner.ScannerViewModel
import br.com.comprix.presentation.tema.Icones

/**
 * As dez rotas do app.
 *
 * Rota e string com argumento tipado - `navigation-compose` puro, sem
 * biblioteca de geracao de codigo. Cada funcao auxiliar monta a rota concreta,
 * entao nenhum chamador precisa montar string na mao (e errar).
 *
 * Telas 12 (kit x avulso) e 17 (peso medio estimado) da referencia **nao** sao
 * rotas: a propria referencia as abre a partir do selo de kit e da edicao de um
 * item, entao elas sao folhas do editor. Assim continuam compartilhando o
 * ViewModel da lista, sem recriar estado nem duplicar o caminho de gravacao.
 */
object Rotas {
    const val ONBOARDING = "onboarding"
    const val LISTAS = "listas"
    const val LISTA = "lista/{listaId}"
    const val COMPARACAO = "comparacao/{listaId}?finalizar={finalizar}"

    /**
     * Comparar SEM nenhuma lista no aparelho: cai no estado de lojas (cartoes
     * da rede, renomear/excluir, gerenciar), nunca num vazio sem saida.
     * Sem barra no nome de proposito: assim nao colide com o padrao
     * "comparacao/{listaId}" e o destaque da aba Comparar continua casando
     * pelo prefixo "comparacao".
     */
    const val COMPARACAO_SEM_LISTA = "comparacaoSemLista"

    const val SCANNER = "scanner/{listaId}?modo={modo}"
    const val HISTORICO = "historico"
    const val NUTRICIONAL = "nutricional/{listaId}"
    const val CONFIGURACOES = "configuracoes"
    const val PRODUTO = "produto/{produtoId}"
    const val CATEGORIAS = "categorias"
    const val CATALOGO = "catalogo?listaId={listaId}"
    const val LOJAS = "lojas"
    const val LIXEIRA = "lixeira"

    fun lista(listaId: Long) = "lista/$listaId"
    fun comparacao(listaId: Long, finalizar: Boolean = false) = "comparacao/$listaId?finalizar=$finalizar"
    fun scanner(listaId: Long, modo: ModoScanner = ModoScanner.FOTO) = "scanner/$listaId?modo=${modo.name}"
    fun nutricional(listaId: Long) = "nutricional/$listaId"
    fun produto(produtoId: Long) = "produto/$produtoId"
    fun catalogo(listaId: Long = -1L) = "catalogo?listaId=$listaId"
}

/** Os cinco destinos fixos da navegacao inferior, na ordem da referencia. */
private enum class Aba(val rotulo: String, val icone: Int, val raiz: String) {
    LISTAS("Listas", Icones.listas, "listas"),
    COMPARAR("Comparar", Icones.comparar, "comparacao"),
    SCANNER("Scanner", Icones.codigoDeBarras, "scanner"),
    HISTORICO("Histórico", Icones.historico, "historico"),
    AJUSTES("Ajustes", Icones.configuracoes, "configuracoes"),
}

/**
 * Grafo de navegacao.
 *
 * Os ViewModels sao criados aqui com [fabricaDe], puxando as dependencias do
 * [ServiceLocator]. Fica tudo num lugar so: para saber o que uma tela usa,
 * basta ler a rota dela.
 *
 * ## A lista ativa
 *
 * Os destinos **Comparar** e **Scanner** da barra inferior precisam de uma
 * lista. O app guarda a ultima lista aberta em [listaAtiva] e, enquanto nao
 * houver nenhuma, cai na primeira lista em aberto do banco. Sem lista alguma:
 * o **Scanner** leva a **Listas** (convite para criar a primeira) e o
 * **Comparar** abre o estado de lojas ([Rotas.COMPARACAO_SEM_LISTA]) - as
 * lojas da rede em cartoes, nunca um vazio sem saida.
 */
@Composable
fun NavegacaoComprix(
    navegador: NavHostController,
    destinoInicial: String,
) {
    val resumos by ServiceLocator.listaRepositorio.resumos.collectAsStateWithLifecycle(emptyList())
    var listaAtiva by rememberSaveable { mutableStateOf<Long?>(null) }

    val listaAlvo = listaAtiva?.takeIf { id -> resumos.any { it.lista.id == id } }
        ?: resumos.firstOrNull { !it.lista.finalizada }?.lista?.id
        ?: resumos.firstOrNull()?.lista?.id

    val pilha by navegador.currentBackStackEntryAsState()
    val rotaAtual = pilha?.destination?.route

    // Destinos de detalhe nao tem entrada propria na barra inferior. Sem essa
    // normalizacao nenhuma aba fica marcada com a lista aberta ("lista/{id}"
    // nao casa com "listas"), o que alimentava a sensacao de a aba Listas
    // estar perdida/travada. Tudo que se abre a partir da lista pertence a
    // Listas; "lojas" tambem, pois e acionada a partir dela.
    val raizDaAba = when {
        rotaAtual == null -> null
        rotaAtual == Rotas.LISTAS || rotaAtual.startsWith("lista/") ||
            rotaAtual.startsWith("produto/") || rotaAtual.startsWith("nutricional/") ||
            rotaAtual == Rotas.CATEGORIAS || rotaAtual.startsWith("catalogo") ||
            rotaAtual == Rotas.LOJAS -> Aba.LISTAS.raiz

        rotaAtual.startsWith("comparacao") -> Aba.COMPARAR.raiz
        rotaAtual.startsWith("scanner") -> Aba.SCANNER.raiz
        rotaAtual == Rotas.HISTORICO -> Aba.HISTORICO.raiz
        // A Lixeira abre pelos Ajustes, entao a aba dela e a mesma.
        rotaAtual == Rotas.CONFIGURACOES || rotaAtual == Rotas.LIXEIRA -> Aba.AJUSTES.raiz
        else -> null
    }

    /**
     * Troca de aba sem empilhar: volta a raiz e nao duplica destino.
     *
     * Correcao do travamento da aba Listas: com a ficha da lista ("lista/{id}")
     * e a comparacao empilhadas sobre "listas" (abrir lista -> tocar em
     * "Comparar estabelecimentos"), o navigate para o PROPRIO destino do
     * popUpTo ("listas") combinado com saveState/restoreState/launchSingleTop
     * nao desmontava a pilha e a tela continuava na comparacao. Como "listas"
     * e a raiz do grafo e esta SEMPRE na pilha, voltar direto para ela com
     * [NavHostController.popBackStack] resolve de vez e ainda guarda o estado
     * das demais abas (saveState = true), como o restante do irPara faz.
     */
    fun irPara(rota: String) {
        if (rota == Rotas.LISTAS &&
            navegador.popBackStack(Rotas.LISTAS, inclusive = false, saveState = true)
        ) {
            return
        }
        navegador.navigate(rota) {
            popUpTo(Rotas.LISTAS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val barraInferior: @Composable () -> Unit = {
        NavegacaoInferior(
            destinos = Aba.entries.map { DestinoInferior(it.rotulo, it.icone, it.raiz) },
            rotaAtual = raizDaAba,
            aoNavegar = { destino ->
                when (destino.rota) {
                    Aba.LISTAS.raiz -> irPara(Rotas.LISTAS)
                    Aba.HISTORICO.raiz -> irPara(Rotas.HISTORICO)
                    Aba.AJUSTES.raiz -> irPara(Rotas.CONFIGURACOES)
                    Aba.COMPARAR.raiz ->
                        // Com lista abre a matriz; sem lista nenhuma abre o
                        // estado de lojas da propria aba Comparar.
                        irPara(
                            listaAlvo?.let { Rotas.comparacao(it) }
                                ?: Rotas.COMPARACAO_SEM_LISTA,
                        )

                    Aba.SCANNER.raiz ->
                        irPara(listaAlvo?.let { Rotas.scanner(it) } ?: Rotas.LISTAS)
                }
            },
        )
    }

    NavHost(navController = navegador, startDestination = destinoInicial) {

        composable(Rotas.ONBOARDING) {
            OnboardingScreen(
                aoConcluir = {
                    ServiceLocator.escopoDeAplicacao.lancar {
                        ServiceLocator.configuracoesRepositorio.concluirOnboarding()
                    }
                    navegador.navigate(Rotas.LISTAS) {
                        popUpTo(Rotas.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Rotas.LISTAS) {
            val vm: ListasViewModel = viewModel(
                factory = fabricaDe {
                    ListasViewModel(
                        ServiceLocator.listaRepositorio,
                        ServiceLocator.configuracoesRepositorio,
                    )
                },
            )
            ListasScreen(
                viewModel = vm,
                aoAbrirLista = { id ->
                    listaAtiva = id
                    navegador.navigate(Rotas.lista(id))
                },
                aoAbrirLojas = { navegador.navigate(Rotas.LOJAS) },
                navegacao = barraInferior,
            )
        }

        composable(Rotas.LOJAS) {
            val vm: LojasViewModel = viewModel(
                factory = fabricaDe {
                    LojasViewModel(
                        ServiceLocator.precoRepositorio,
                        ServiceLocator.listaRepositorio,
                    )
                },
            )
            LojasScreen(
                viewModel = vm,
                aoVoltar = { if (!navegador.popBackStack()) navegador.navigate(Rotas.LISTAS) },
            )
        }

        composable(
            route = Rotas.LISTA,
            arguments = listOf(navArgument("listaId") { type = NavType.LongType }),
        ) { entrada ->
            val listaId = entrada.arguments?.getLong("listaId") ?: return@composable
            listaAtiva = listaId
            val vm: ListaViewModel = viewModel(
                key = "lista-$listaId",
                factory = fabricaDe {
                    ListaViewModel(
                        listaId = listaId,
                        listaRepositorio = ServiceLocator.listaRepositorio,
                        catalogoRepositorio = ServiceLocator.catalogoRepositorio,
                        precoRepositorio = ServiceLocator.precoRepositorio,
                        configuracoesRepositorio = ServiceLocator.configuracoesRepositorio,
                        entradaDeTextoLivre = ServiceLocator.entradaDeTextoLivre,
                    )
                },
            )
            val vmPrecos: PrecosViewModel = viewModel(
                key = "precos-$listaId",
                factory = fabricaDe { precosDaLista(listaId) },
            )
            ListaScreen(
                viewModel = vm,
                precos = vmPrecos,
                aoVoltar = { navegador.popBackStack() },
                aoComparar = { navegador.navigate(Rotas.comparacao(listaId)) },
                aoEscanear = { modo -> navegador.navigate(Rotas.scanner(listaId, modo)) },
                aoAbrirNutricional = { navegador.navigate(Rotas.nutricional(listaId)) },
                aoAbrirProduto = { produtoId -> navegador.navigate(Rotas.produto(produtoId)) },
                aoAbrirCategorias = { navegador.navigate(Rotas.CATEGORIAS) },
                aoFinalizarCompra = { navegador.navigate(Rotas.comparacao(listaId, finalizar = true)) },
                aoAbrirCatalogo = { navegador.navigate(Rotas.catalogo(listaId)) },
            )
        }

        composable(
            route = Rotas.COMPARACAO,
            arguments = listOf(
                navArgument("listaId") { type = NavType.LongType },
                navArgument("finalizar") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entrada ->
            val listaId = entrada.arguments?.getLong("listaId") ?: return@composable
            listaAtiva = listaId
            val finalizar = entrada.arguments?.getBoolean("finalizar") ?: false
            val vm: PrecosViewModel = viewModel(
                key = "precos-$listaId",
                factory = fabricaDe { precosDaLista(listaId) },
            )
            // Fonte de gerencia das lojas (renomear/excluir dentro do Comparar).
            val vmLojas: LojasViewModel = viewModel(
                key = "lojas-da-comparacao-$listaId",
                factory = fabricaDe {
                    LojasViewModel(
                        ServiceLocator.precoRepositorio,
                        ServiceLocator.listaRepositorio,
                    )
                },
            )
            ComparacaoScreen(
                viewModel = vm,
                viewModelDasLojas = vmLojas,
                abrirEmFinalizar = finalizar,
                aoVoltar = {
                    if (!navegador.popBackStack()) navegador.navigate(Rotas.LISTAS)
                },
                aoVerHistorico = {
                    navegador.navigate(Rotas.HISTORICO) { popUpTo(Rotas.LISTAS) }
                },
                aoVoltarParaListas = {
                    navegador.navigate(Rotas.LISTAS) { popUpTo(Rotas.LISTAS) { inclusive = true } }
                },
                aoGerenciarLojas = { navegador.navigate(Rotas.LOJAS) },
                navegacao = barraInferior,
            )
        }

        composable(Rotas.COMPARACAO_SEM_LISTA) {
            val vmLojas: LojasViewModel = viewModel(
                factory = fabricaDe {
                    LojasViewModel(
                        ServiceLocator.precoRepositorio,
                        ServiceLocator.listaRepositorio,
                    )
                },
            )
            ComparacaoSemLista(
                viewModel = vmLojas,
                aoGerenciarLojas = { navegador.navigate(Rotas.LOJAS) },
                aoVoltar = {
                    if (!navegador.popBackStack()) navegador.navigate(Rotas.LISTAS)
                },
                navegacao = barraInferior,
            )
        }

        composable(
            route = Rotas.SCANNER,
            arguments = listOf(
                navArgument("listaId") { type = NavType.LongType },
                navArgument("modo") {
                    type = NavType.StringType
                    defaultValue = ModoScanner.FOTO.name
                },
            ),
        ) { entrada ->
            val listaId = entrada.arguments?.getLong("listaId") ?: return@composable
            listaAtiva = listaId
            val modo = runCatching {
                ModoScanner.valueOf(entrada.arguments?.getString("modo") ?: ModoScanner.FOTO.name)
            }.getOrDefault(ModoScanner.FOTO)

            val vm: ScannerViewModel = viewModel(
                key = "scanner-$listaId",
                factory = fabricaDe {
                    ScannerViewModel(
                        listaId = listaId,
                        leitor = LeitorDeImagem(),
                        catalogoRepositorio = ServiceLocator.catalogoRepositorio,
                        listaRepositorio = ServiceLocator.listaRepositorio,
                        precoRepositorio = ServiceLocator.precoRepositorio,
                        configuracoesRepositorio = ServiceLocator.configuracoesRepositorio,
                    )
                },
            )
            ScannerScreen(
                viewModel = vm,
                modoInicial = modo,
                aoVoltar = {
                    if (!navegador.popBackStack()) navegador.navigate(Rotas.lista(listaId))
                },
                aoConcluir = {
                    if (!navegador.popBackStack()) navegador.navigate(Rotas.lista(listaId))
                },
            )
        }

        composable(Rotas.HISTORICO) {
            val vm: HistoricoViewModel = viewModel(
                factory = fabricaDe {
                    HistoricoViewModel(
                        ServiceLocator.historicoRepositorio,
                        ServiceLocator.configuracoesRepositorio,
                    )
                },
            )
            HistoricoScreen(
                viewModel = vm,
                aoVoltar = { if (!navegador.popBackStack()) navegador.navigate(Rotas.LISTAS) },
                aoAbrirListas = { navegador.navigate(Rotas.LISTAS) { popUpTo(Rotas.LISTAS) } },
                navegacao = barraInferior,
            )
        }

        composable(
            route = Rotas.NUTRICIONAL,
            arguments = listOf(navArgument("listaId") { type = NavType.LongType }),
        ) { entrada ->
            val listaId = entrada.arguments?.getLong("listaId") ?: return@composable
            val vm: NutricionalViewModel = viewModel(
                key = "nutricional-$listaId",
                factory = fabricaDe {
                    NutricionalViewModel(
                        listaId = listaId,
                        listaRepositorio = ServiceLocator.listaRepositorio,
                        configuracoesRepositorio = ServiceLocator.configuracoesRepositorio,
                    )
                },
            )
            NutricionalScreen(
                viewModel = vm,
                aoVoltar = { navegador.popBackStack() },
                aoEscanearRotulo = { navegador.navigate(Rotas.scanner(listaId, ModoScanner.FOTO)) },
            )
        }

        composable(Rotas.CONFIGURACOES) {
            val vm: ConfiguracoesViewModel = viewModel(
                factory = fabricaDe {
                    ConfiguracoesViewModel(
                        ServiceLocator.configuracoesRepositorio,
                        ServiceLocator.catalogoRepositorio,
                        ServiceLocator.gerenciadorDeBackup,
                        ServiceLocator.precoRepositorio,
                    )
                },
            )
            ConfiguracoesScreen(
                viewModel = vm,
                aoVoltar = { if (!navegador.popBackStack()) navegador.navigate(Rotas.LISTAS) },
                aoAbrirCategorias = { navegador.navigate(Rotas.CATEGORIAS) },
                aoAbrirNutricional = {
                    listaAlvo?.let { navegador.navigate(Rotas.nutricional(it)) }
                        ?: navegador.navigate(Rotas.LISTAS)
                },
                aoReverBoasVindas = { navegador.navigate(Rotas.ONBOARDING) },
                aoAbrirCatalogo = { navegador.navigate(Rotas.catalogo()) },
                aoAbrirLixeira = { navegador.navigate(Rotas.LIXEIRA) },
                navegacao = barraInferior,
            )
        }

        composable(Rotas.LIXEIRA) {
            val vm: LixeiraViewModel = viewModel(
                factory = fabricaDe {
                    LixeiraViewModel(ServiceLocator.lixeiraRepositorio)
                },
            )
            LixeiraScreen(
                viewModel = vm,
                aoVoltar = { if (!navegador.popBackStack()) navegador.navigate(Rotas.CONFIGURACOES) },
            )
        }

        composable(
            route = Rotas.PRODUTO,
            arguments = listOf(navArgument("produtoId") { type = NavType.LongType }),
        ) { entrada ->
            val produtoId = entrada.arguments?.getLong("produtoId") ?: return@composable
            val vm: ProdutoViewModel = viewModel(
                key = "produto-$produtoId",
                factory = fabricaDe {
                    ProdutoViewModel(
                        produtoId = produtoId,
                        catalogoRepositorio = ServiceLocator.catalogoRepositorio,
                        precoRepositorio = ServiceLocator.precoRepositorio,
                        configuracoesRepositorio = ServiceLocator.configuracoesRepositorio,
                    )
                },
            )
            ProdutoScreen(viewModel = vm, aoVoltar = { navegador.popBackStack() })
        }

        composable(Rotas.CATEGORIAS) {
            val vm: CategoriasViewModel = viewModel(
                factory = fabricaDe {
                    CategoriasViewModel(ServiceLocator.catalogoRepositorio)
                },
            )
            CategoriasScreen(viewModel = vm, aoVoltar = { navegador.popBackStack() })
        }

        composable(
            route = Rotas.CATALOGO,
            arguments = listOf(
                navArgument("listaId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entrada ->
            val listaId = entrada.arguments?.getLong("listaId") ?: -1L
            val vm: CatalogoViewModel = viewModel(
                factory = fabricaDe {
                    CatalogoViewModel(
                        catalogoRepositorio = ServiceLocator.catalogoRepositorio,
                        adicionarNaLista = { alvoListaId, produto ->
                            ServiceLocator.listaRepositorio.adicionarItem(
                                ItemDaLista(
                                    listaId = alvoListaId,
                                    produtoId = produto.id,
                                    unidade = produto.unidadePadrao,
                                ),
                            )
                        },
                    )
                },
            )
            CatalogoScreen(
                viewModel = vm,
                listaId = listaId,
                aoVoltar = { navegador.popBackStack() },
            )
        }
    }
}

/** Construtor unico do [PrecosViewModel] - a lista e a comparacao compartilham o mesmo. */
private fun precosDaLista(listaId: Long) = PrecosViewModel(
    listaId = listaId,
    listaRepositorio = ServiceLocator.listaRepositorio,
    precoRepositorio = ServiceLocator.precoRepositorio,
    catalogoRepositorio = ServiceLocator.catalogoRepositorio,
    configuracoesRepositorio = ServiceLocator.configuracoesRepositorio,
    historicoRepositorio = ServiceLocator.historicoRepositorio,
)

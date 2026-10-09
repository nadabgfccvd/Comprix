package br.com.comprix.di

import android.content.Context
import br.com.comprix.data.backup.GerenciadorDeBackup
import br.com.comprix.data.catalogo.FonteDoCatalogoSemente
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.parser.EntradaDeTextoLivre
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.HistoricoRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.LixeiraRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Injecao de dependencias manual (Service Locator).
 *
 * ## Por que nao Hilt
 * O app tem 5 repositorios e 1 banco. Hilt traria um processador de anotacoes,
 * ~2 s a mais em cada build incremental e algumas centenas de KB no APK para
 * resolver um grafo que cabe em 30 linhas. Em aparelho de 2 GB (Moto E5, o
 * alvo da Secao 2), cada KB e cada classe a menos no `dex` contam no tempo de
 * abertura.
 *
 * Tudo e criado sob demanda e mantido como singleton pelo tempo de vida do
 * processo. [preparar] e chamado uma unica vez em `ComprixApp.onCreate`.
 */
object ServiceLocator {

    @Volatile
    private var contextoApp: Context? = null

    private val trava = Any()

    fun preparar(contexto: Context) {
        if (contextoApp == null) {
            synchronized(trava) {
                if (contextoApp == null) contextoApp = contexto.applicationContext
            }
        }
    }

    private fun contexto(): Context =
        contextoApp ?: error("ServiceLocator.preparar() precisa ser chamado em ComprixApp.onCreate().")

    val banco: ComprixDatabase by lazy { ComprixDatabase.obter(contexto()) }

    /**
     * Fonte do catalogo-semente (`res/raw/catalogo_semente.txt`). A leitura e o
     * parse acontecem uma unica vez, no primeiro acesso ao [catalogo] da fonte -
     * e, como o repositorio abaixo so toca nele dentro do proprio `lazy`, quem
     * nunca abre o catalogo tambem nunca paga o parse.
     */
    val fonteDoCatalogoSemente: FonteDoCatalogoSemente by lazy { FonteDoCatalogoSemente(contexto()) }

    val catalogoRepositorio: CatalogoRepositorio by lazy {
        CatalogoRepositorio(
            banco.categoriaDao(),
            banco.produtoDao(),
            banco.memoriaCategoriaDao(),
            banco.decisaoDoParserDao(),
            ServiceLocator.fonteDoCatalogoSemente.catalogo,
            lixeiraRepositorio,
        )
    }

    /**
     * Lixeira: tudo que o usuario exclui passa por aqui e volta a purga de
     * 30 dias no arranque do processo (idempotente, so toca na tabela `lixeira`).
     */
    val lixeiraRepositorio: LixeiraRepositorio by lazy {
        LixeiraRepositorio(
            banco.lixeiraDao(),
            banco.listaDao(),
            banco.itemDao(),
            banco.produtoDao(),
            banco.categoriaDao(),
            banco.estabelecimentoDao(),
            banco.precoDao(),
            banco.compraDao(),
            banco.alergiaCustomizadaDao(),
        ).also { repositorio ->
            escopoDeAplicacao.lancar { repositorio.purgarExpirados() }
        }
    }

    val listaRepositorio: ListaRepositorio by lazy {
        ListaRepositorio(banco.listaDao(), banco.itemDao(), banco.produtoDao(), banco.precoDao(), lixeiraRepositorio)
    }

    /** Ponte parser puro -> banco (entrada por texto livre da tela de lista). */
    val entradaDeTextoLivre: EntradaDeTextoLivre by lazy {
        EntradaDeTextoLivre(catalogoRepositorio, listaRepositorio)
    }

    val precoRepositorio: PrecoRepositorio by lazy {
        PrecoRepositorio(banco.precoDao(), banco.estabelecimentoDao(), lixeiraRepositorio)
    }

    val historicoRepositorio: HistoricoRepositorio by lazy {
        HistoricoRepositorio(banco.compraDao(), lixeiraRepositorio)
    }

    val configuracoesRepositorio: ConfiguracoesRepositorio by lazy {
        ConfiguracoesRepositorio(banco.configuracoesDao(), banco.alergiaCustomizadaDao(), lixeiraRepositorio)
    }

    val gerenciadorDeBackup: GerenciadorDeBackup by lazy {
        GerenciadorDeBackup(contexto(), banco)
    }

    /**
     * Escopo do processo, para acoes que nao podem morrer com a tela.
     *
     * Usado so para gravacoes curtas e idempotentes - concluir o onboarding,
     * por exemplo. Trabalho longo continua no ViewModel, que o sistema sabe
     * cancelar.
     */
    val escopoDeAplicacao: EscopoDeAplicacao by lazy { EscopoDeAplicacao() }

    /** Pequeno invólucro para nao vazar `CoroutineScope` pelas telas. */
    class EscopoDeAplicacao internal constructor() {
        private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun lancar(bloco: suspend () -> Unit) {
            escopo.launch { bloco() }
        }
    }
}

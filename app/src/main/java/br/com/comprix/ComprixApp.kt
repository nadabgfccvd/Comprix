package br.com.comprix

import android.app.Application
import android.util.Log
import br.com.comprix.di.ServiceLocator
import br.com.comprix.presentation.widget.ListaWidgetProvider
import br.com.comprix.util.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ponto de entrada do processo.
 *
 * O `onCreate` precisa ser rapido: num Moto E5 (SD425, 2 GB) qualquer trabalho
 * aqui aparece como atraso na tela inicial. Por isso so o Service Locator e
 * preparado de forma sincrona; o backup automatico vai para uma corrotina de
 * IO, e o banco so e aberto quando a primeira tela pedir dados.
 */
class ComprixApp : Application() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.preparar(this)
        Feedback.inicializar(this)
        espelharConfiguracoesDeFeedback()
        agendarBackupAutomatico()
        acompanharWidgetDaLista()
    }

    /**
     * Mantem o widget da tela inicial sincronizado com o banco.
     *
     * O coletor nao usa os dados da emissao: ela serve de gatilho - qualquer
     * mudanca em listas/itens (o mesmo Flow de resumos que alimenta a tela
     * inicial) pede um redesenho, e o provider rele o resumo direto do Room
     * (ver [ListaWidgetProvider.atualizarTudo]). Falha nao derruba o processo.
     */
    private fun acompanharWidgetDaLista() {
        ServiceLocator.escopoDeAplicacao.lancar {
            runCatching {
                ServiceLocator.listaRepositorio.resumos.collect {
                    ListaWidgetProvider.atualizarTudo(this@ComprixApp)
                }
            }.onFailure { erro ->
                Log.w(TAG, "Widget da lista não acompanhou o banco: ${erro.message}")
            }
        }
    }

    /**
     * Espelha "Sons" e "Vibracao" (Ajustes) no motor de [Feedback].
     *
     * O Flow do repositorio emite a cada mudanca gravada no banco; aqui so
     * copiamos os dois booleanos para os `@Volatile` do motor, que as telas
     * leem sem tocar no banco. Falha de leitura nao derruba o processo.
     */
    private fun espelharConfiguracoesDeFeedback() {
        ServiceLocator.escopoDeAplicacao.lancar {
            runCatching {
                ServiceLocator.configuracoesRepositorio.configuracoes
                    .collect { Feedback.configurar(it.sons, it.vibracao) }
            }.onFailure { erro ->
                Log.w(TAG, "Espelho de feedback não acompanhou as configurações: ${erro.message}")
            }
        }
    }

    /** Backup diario em arquivo local (Secao 10) - silencioso e sem rede. */
    private fun agendarBackupAutomatico() {
        escopo.launch {
            runCatching {
                val configuracoes = ServiceLocator.configuracoesRepositorio.carregar()
                val arquivo = ServiceLocator.gerenciadorDeBackup
                    .backupAutomaticoSeNecessario(configuracoes.ultimoBackupMs)
                if (arquivo != null) {
                    ServiceLocator.configuracoesRepositorio.registrarBackup()
                }
            }.onFailure { erro ->
                // Falha de backup nunca pode derrubar o app nem interromper o usuario.
                Log.w(TAG, "Backup automático não pôde ser gravado: ${erro.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ComprixApp"
    }
}

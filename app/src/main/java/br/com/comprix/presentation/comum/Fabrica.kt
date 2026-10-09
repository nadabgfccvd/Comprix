package br.com.comprix.presentation.comum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Fabrica simples de ViewModels.
 *
 * Com Service Locator (ver `di/ServiceLocator.kt`) nao ha grafo de injecao:
 * cada tela declara como construir o seu ViewModel e pronto. Isso evita um
 * processador de anotacoes so para resolver cinco dependencias.
 */
class FabricaDeViewModel<VM : ViewModel>(
    private val construtor: () -> VM,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        construtor() as T

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = construtor() as T
}

/** Acucar sintatico: `viewModel(factory = fabricaDe { MeuViewModel(...) })`. */
fun <VM : ViewModel> fabricaDe(construtor: () -> VM): ViewModelProvider.Factory =
    FabricaDeViewModel(construtor)

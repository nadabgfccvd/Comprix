package br.com.comprix.presentation

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import br.com.comprix.di.ServiceLocator
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.presentation.navegacao.NavegacaoComprix
import br.com.comprix.presentation.navegacao.Rotas
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.Tema
import br.com.comprix.presentation.tema.TemaComprix
import br.com.comprix.util.Feedback

/**
 * Unica Activity do app.
 *
 * Ela faz tres coisas e so: aplica o tema escolhido, decide se a primeira tela
 * e o onboarding e entrega o controle ao grafo de navegacao. Qualquer logica
 * de negocio aqui seria logica fora de lugar.
 *
 * Nada de leitura bloqueante no `onCreate`: num Moto E5, abrir o banco na
 * thread principal custa decimos de segundo visiveis. Enquanto as preferencias
 * nao chegam (poucos milissegundos), a tela mostra a marca - sem piscar o
 * onboarding para quem ja passou por ele.
 *
 * O `configChanges` declarado no manifesto evita recriar a Activity no giro de
 * tela, o que tambem economiza esse tempo todo de novo.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        garantirPermissaoDeVibracao()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { AplicativoComprix() }
    }

    /**
     * Permissao de vibracao pedida na abertura do app.
     *
     * A VIBRATE e permissao de instalacao normal: nao abre dialogo e ja vem
     * concedida, mas a chamada explicita registra o estado no sistema (e
     * garante o pedido do usuario por "permissoes ao abrir o app"). Falha
     * aqui e inofensiva: o motor de feedback simplesmente nao vibra.
     */
    private fun garantirPermissaoDeVibracao() {
        runCatching {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.VIBRATE), 1001)
            Log.d("Comprix", Feedback.statusDaVibracao(this))
        }
    }
}

@Composable
private fun AplicativoComprix() {
    val configuracoes by ServiceLocator.configuracoesRepositorio.configuracoes
        .collectAsStateWithLifecycle(initialValue = null)

    val preferencias = configuracoes ?: ConfiguracoesApp()

    TemaComprix(configuracoes = preferencias) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (configuracoes == null) {
                TelaDeAbertura()
            } else {
                val navegador = rememberNavController()
                val destinoInicial = remember(preferencias.onboardingConcluido) {
                    if (preferencias.onboardingConcluido) Rotas.LISTAS else Rotas.ONBOARDING
                }
                NavegacaoComprix(navegador = navegador, destinoInicial = destinoInicial)
            }
        }
    }
}

/** Abertura curtissima enquanto o Room entrega a linha de preferencias. */
@Composable
private fun TelaDeAbertura() {
    val cores = Tema.cores
    Box(
        Modifier.fillMaxSize().background(cores.fundo),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(cores.marca),
                contentAlignment = Alignment.Center,
            ) {
                IconeComprix(Icones.listas, null, tamanho = 38.dp, tinta = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Comprix",
                style = MaterialTheme.typography.displaySmall,
                color = cores.marca,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Sua lista, o melhor preço, sempre offline.",
                style = MaterialTheme.typography.bodySmall,
                color = cores.apagado,
            )
        }
    }
}

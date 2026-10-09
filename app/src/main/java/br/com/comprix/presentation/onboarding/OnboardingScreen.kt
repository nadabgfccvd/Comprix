package br.com.comprix.presentation.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.comprix.presentation.comum.BotaoComprix
import br.com.comprix.presentation.comum.EspacoVertical
import br.com.comprix.presentation.comum.EstiloDeBotao
import br.com.comprix.presentation.comum.SeloOffline
import br.com.comprix.presentation.tema.IconeComprix
import br.com.comprix.presentation.tema.Icones
import br.com.comprix.presentation.tema.TamanhoDeIcone
import br.com.comprix.presentation.tema.Tema
import kotlinx.coroutines.launch

/** Um passo das boas-vindas. */
private data class PassoDeAbertura(
    @DrawableRes val icone: Int,
    val titulo: String,
    val descricao: String,
)

private val PASSOS = listOf(
    PassoDeAbertura(
        Icones.cesta,
        "Comprar bem começa\ncom uma boa lista.",
        "Digite do seu jeito — “2 kg de arroz”, “meia dúzia de ovos”, " +
            "“leite 3 caixas” — que o Comprix separa quantidade, unidade e categoria.",
    ),
    PassoDeAbertura(
        Icones.escanear,
        "Escaneie preços\ne rótulos.",
        "Foto, vídeo guiado de 30 s ou código de barras. O reconhecimento " +
            "roda dentro do aparelho: nada sai daqui, nem precisa de internet.",
    ),
    PassoDeAbertura(
        Icones.comparar,
        "Compare quantos\nmercados quiser.",
        "Uma lista, uma coluna de preços por loja. O Comprix mostra o menor " +
            "preço de cada item, o total de cada mercado e a compra mista mais barata.",
    ),
    PassoDeAbertura(
        Icones.grafico,
        "Veja quanto\nvocê economizou.",
        "Cada compra finalizada vira histórico com gráficos de gasto por " +
            "mercado e por categoria. Tudo guardado só no seu aparelho.",
    ),
)

private val RECURSOS = listOf(
    Icones.listas to "Crie listas rápidas",
    Icones.codigoDeBarras to "Escaneie preços e rótulos",
    Icones.comparar to "Compare mercados",
    Icones.grafico to "Histórico de economia",
)

/**
 * Boas-vindas em quatro passos (tela 01 da referencia).
 *
 * Primeira tela de todas, e a unica que pode ser pulada: "Pular" fica sempre
 * visivel no topo, porque obrigar alguem a passar quatro paginas para usar o
 * app seria um pedagio, nao uma apresentacao.
 */
@Composable
fun OnboardingScreen(aoConcluir: () -> Unit) {
    val cores = Tema.cores
    val paginas = rememberPagerState { PASSOS.size }
    val escopo = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(cores.fundo)
            .padding(horizontal = 20.dp)
            .padding(top = 44.dp, bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeloOffline(sobreMarca = false)
            Spacer(Modifier.weight(1f))
            BotaoComprix("Pular", aoConcluir, estilo = EstiloDeBotao.CONTORNADO, compacto = true)
        }

        HorizontalPager(
            state = paginas,
            modifier = Modifier.weight(1f),
        ) { indice ->
            val passo = PASSOS[indice]
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IlustracaoDeAbertura(passo.icone)
                EspacoVertical(22.dp)
                if (indice == 0) {
                    Text(
                        "Comprix",
                        style = MaterialTheme.typography.displayMedium,
                        color = cores.marca,
                    )
                    EspacoVertical(4.dp)
                    Text(
                        "Sua lista, o melhor preço,\nsempre offline.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cores.texto,
                        textAlign = TextAlign.Center,
                    )
                    EspacoVertical(20.dp)
                    RECURSOS.forEach { (icone, rotulo) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 320.dp)
                                .padding(vertical = 4.dp)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .border(1.dp, cores.contorno, CircleShape)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconeComprix(icone, null, tamanho = 20.dp, tinta = cores.acao)
                            Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = cores.texto)
                        }
                    }
                } else {
                    Text(
                        passo.titulo,
                        style = MaterialTheme.typography.displayMedium,
                        color = cores.texto,
                        textAlign = TextAlign.Center,
                    )
                    EspacoVertical(12.dp)
                    Text(
                        passo.descricao,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cores.apagado,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                    if (indice == 1) {
                        EspacoVertical(16.dp)
                        // Os tres formatos de escaneamento, um por linha: a pessoa
                        // sai do passo sabendo o que apontar para a camera.
                        listOf(
                            Icones.camera to "Foto: etiquetas",
                            Icones.video to "Vídeo: embalagem girando 30 s",
                            Icones.codigoDeBarras to "Código: barras",
                        ).forEach { (icone, rotulo) ->
                            Row(
                                Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                IconeComprix(icone, null, tamanho = TamanhoDeIcone.pequeno, tinta = cores.acao)
                                Text(
                                    rotulo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cores.apagado,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .semantics { contentDescription = "Passo ${paginas.currentPage + 1} de ${PASSOS.size}" },
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            repeat(PASSOS.size) { indice ->
                val ativa = indice == paginas.currentPage
                Box(
                    Modifier
                        .size(if (ativa) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (ativa) cores.marca else cores.contorno),
                )
            }
        }

        val ultima = paginas.currentPage == PASSOS.lastIndex
        BotaoComprix(
            texto = if (ultima) "Começar agora" else "Continuar",
            aoTocar = {
                if (ultima) {
                    aoConcluir()
                } else {
                    escopo.launch { paginas.animateScrollToPage(paginas.currentPage + 1) }
                }
            },
            bloco = true,
        )
    }
}

/**
 * Ilustracao das boas-vindas.
 *
 * Montada com o proprio conjunto vetorial em vez de um PNG: escala sem
 * serrilhado em qualquer densidade, acompanha o tema escuro sozinha e nao
 * acrescenta um unico byte de bitmap ao APK.
 */
@Composable
private fun IlustracaoDeAbertura(@DrawableRes icone: Int, modifier: Modifier = Modifier) {
    val cores = Tema.cores
    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(cores.verdeSuave),
        )
        Box(
            Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(cores.cartao)
                .border(1.dp, cores.contorno, RoundedCornerShape(38.dp)),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(icone, null, tamanho = 58.dp, tinta = cores.marca)
        }
        Box(
            Modifier
                .offset(x = 58.dp, y = (-54).dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(cores.ambar),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(
                Icones.codigoDeBarras,
                null,
                tamanho = TamanhoDeIcone.pequeno,
                tinta = cores.ambarTinta,
            )
        }
        Box(
            Modifier
                .offset(x = (-62).dp, y = 50.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(cores.marca),
            contentAlignment = Alignment.Center,
        ) {
            IconeComprix(Icones.confirmar, null, tamanho = 15.dp, tinta = cores.cartao)
        }
    }
}

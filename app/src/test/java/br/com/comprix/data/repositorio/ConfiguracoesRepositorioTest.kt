package br.com.comprix.data.repositorio

import android.app.Application
import androidx.room.Room
import br.com.comprix.data.local.ComprixDatabase
import br.com.comprix.data.local.DadosIniciais
import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.TipoTema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `ConfiguracoesRepositorio`: o contrato das preferências e do perfil de
 * restrições, de ponta a ponta com Room em memória.
 *
 * Duas tabelas de linha única (id = 1): em banco recém-criado, sem linha
 * nenhuma, `carregar()`/`carregarPerfil()` devolvem os padrões do domínio
 * (mapeamento de entidade nula). Cada ajuste usa o mesmo padrão `alterar` —
 * lê a linha, copia, grava — então nunca sobrescreve as outras preferências.
 * O perfil de linha única nunca toca nas alergias customizadas (tabela
 * própria, que entra no `perfil` via combine) e a criação de restrição própria
 * normaliza as palavras e ignora duplicatas normalizadas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ConfiguracoesRepositorioTest {
    private lateinit var banco: ComprixDatabase
    private lateinit var configuracoes: ConfiguracoesRepositorio
    private lateinit var lixeira: LixeiraRepositorio

    @Before
    fun preparar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, ComprixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DadosIniciais.semear(banco.openHelper.writableDatabase)
        lixeira = LixeiraRepositorio(
            lixeiraDao = banco.lixeiraDao(),
            listaDao = banco.listaDao(),
            itemDao = banco.itemDao(),
            produtoDao = banco.produtoDao(),
            categoriaDao = banco.categoriaDao(),
            estabelecimentoDao = banco.estabelecimentoDao(),
            precoDao = banco.precoDao(),
            compraDao = banco.compraDao(),
            alergiaDao = banco.alergiaCustomizadaDao(),
        )
        configuracoes = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
            lixeira = lixeira,
        )
    }

    @After
    fun fechar() {
        banco.close()
    }

    // --- preferências -----------------------------------------------------------------

    @Test
    fun `carregar num banco novo devolve os padroes`() = runBlocking {
        assertEquals(ConfiguracoesApp(), configuracoes.carregar())
    }

    @Test
    fun `definirTema muda so o tema`() = runBlocking {
        configuracoes.definirTema(TipoTema.ESCURO)

        assertEquals(ConfiguracoesApp(tema = TipoTema.ESCURO), configuracoes.carregar())
    }

    @Test
    fun `definirEscalaDaFonte cai no degrau de 5 e prende nos limites`() = runBlocking {
        configuracoes.definirEscalaDaFonte(147)
        assertEquals(145, configuracoes.carregar().escalaDaFonte)

        configuracoes.definirEscalaDaFonte(90)
        assertEquals(100, configuracoes.carregar().escalaDaFonte)

        configuracoes.definirEscalaDaFonte(200)
        assertEquals(160, configuracoes.carregar().escalaDaFonte)

        configuracoes.definirEscalaDaFonte(160)
        assertEquals(160, configuracoes.carregar().escalaDaFonte)

        configuracoes.definirEscalaDaFonte(100)
        assertEquals(100, configuracoes.carregar().escalaDaFonte)
    }

    @Test
    fun `os ajustes se preservam entre si`() = runBlocking {
        configuracoes.definirAltoContraste(true)
        configuracoes.definirCoresDinamicas(true)
        configuracoes.definirSons(false)
        configuracoes.definirVibracao(false)
        configuracoes.definirModoTecnico(true)

        assertEquals(
            ConfiguracoesApp(
                altoContraste = true,
                coresDinamicas = true,
                sons = false,
                vibracao = false,
                modoTecnico = true,
            ),
            configuracoes.carregar(),
        )
    }

    @Test
    fun `definirNutrientes lista vazia volta para a selecao padrao`() = runBlocking {
        configuracoes.definirNutrientes(listOf(Nutriente.SODIO))
        assertEquals(listOf(Nutriente.SODIO), configuracoes.carregar().nutrientesComparados)

        configuracoes.definirNutrientes(emptyList())
        assertEquals(Nutriente.selecaoPadrao(), configuracoes.carregar().nutrientesComparados)
    }

    @Test
    fun `marcarDicaVista acumula e reexibirDicas limpa`() = runBlocking {
        configuracoes.marcarDicaVista("dica-a")
        configuracoes.marcarDicaVista("dica-b")
        assertEquals(setOf("dica-a", "dica-b"), configuracoes.carregar().dicasVistas)

        configuracoes.reexibirDicas()
        assertEquals(emptySet<String>(), configuracoes.carregar().dicasVistas)
    }

    @Test
    fun `definirMetaEconomia grava e null remove`() = runBlocking {
        configuracoes.definirMetaEconomia(15_000)
        assertEquals(15_000L, configuracoes.carregar().metaEconomiaCentavos)

        configuracoes.definirMetaEconomia(null)
        assertEquals(null, configuracoes.carregar().metaEconomiaCentavos)
    }

    @Test
    fun `concluirOnboarding e registrarBackup com momento fixo`() = runBlocking {
        configuracoes.concluirOnboarding()
        configuracoes.registrarBackup(1_234_567_890L)

        val salvas = configuracoes.carregar()
        assertTrue(salvas.onboardingConcluido)
        assertEquals(1_234_567_890L, salvas.ultimoBackupMs)
    }

    // --- perfil e alergias customizadas -------------------------------------------------

    @Test
    fun `salvarPerfil nao apaga as alergias customizadas`() = runBlocking {
        configuracoes.adicionarAlergiaCustomizada("Lactose", listOf("leite"))
        assertEquals(1, banco.alergiaCustomizadaDao().listarTodas().size)

        configuracoes.definirSemGluten(true)

        assertEquals(1, banco.alergiaCustomizadaDao().listarTodas().size)
        assertTrue(configuracoes.carregarPerfil().semGluten)
        assertEquals(emptySet<Alergeno>(), configuracoes.carregarPerfil().alergenos)
    }

    @Test
    fun `adicionarAlergiaCustomizada normaliza as palavras e descarta vazias e duplicadas`() = runBlocking {
        configuracoes.adicionarAlergiaCustomizada("Lactose", listOf("Leite ", "", "leite", "Soja"))

        val salva = banco.alergiaCustomizadaDao().listarTodas().single()
        assertEquals(listOf("leite", "soja"), salva.palavras.split("|").filter { it.isNotBlank() })
    }

    @Test
    fun `adicionarAlergiaCustomizada ignora nome em branco e duplicado normalizado`() = runBlocking {
        configuracoes.adicionarAlergiaCustomizada("   ")
        assertEquals(0, banco.alergiaCustomizadaDao().listarTodas().size)

        configuracoes.adicionarAlergiaCustomizada("Lactose")
        configuracoes.adicionarAlergiaCustomizada(" lactose ")
        assertEquals(1, banco.alergiaCustomizadaDao().listarTodas().size)
    }

    @Test
    fun `removerAlergiaCustomizada com lixeira manda para a lixeira e sem ela apaga direto`() = runBlocking {
        configuracoes.adicionarAlergiaCustomizada("Lactose")
        val id = banco.alergiaCustomizadaDao().listarTodas().single().id

        configuracoes.removerAlergiaCustomizada(id)

        assertEquals(0, banco.alergiaCustomizadaDao().listarTodas().size)
        assertEquals(1, lixeira.contar())

        banco.alergiaCustomizadaDao().inserir(
            br.com.comprix.data.local.AlergiaCustomizadaEntity(nome = "Outra", palavras = ""),
        )
        val direto = ConfiguracoesRepositorio(
            dao = banco.configuracoesDao(),
            alergias = banco.alergiaCustomizadaDao(),
        )
        val idOutra = banco.alergiaCustomizadaDao().listarTodas().single().id

        direto.removerAlergiaCustomizada(idOutra)

        assertEquals(0, banco.alergiaCustomizadaDao().listarTodas().size)
        assertEquals(1, lixeira.contar())
    }

    @Test
    fun `perfil combina a linha unica com as alergias customizadas`() = runBlocking {
        configuracoes.adicionarAlergiaCustomizada("Lactose", listOf("leite"))
        configuracoes.definirSemGluten(true)

        val perfil = configuracoes.perfil.first()

        assertTrue(perfil.semGluten)
        assertEquals(1, perfil.customizadas.size)
        assertEquals("Lactose", perfil.customizadas.single().nome)
        assertTrue(perfil.ativo)
    }
}

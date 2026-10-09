package br.com.comprix.presentation.configuracoes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.backup.GerenciadorDeBackup
import br.com.comprix.data.local.DecisaoDoParserEntity
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.TipoTema
import br.com.comprix.util.Formatadores
import br.com.comprix.util.TextoUtil
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configurações, dados e privacidade.
 *
 * Três grupos: aparência (tema, contraste, cores dinâmicas), uso (sons,
 * vibração, modo técnico, dicas) e dados (backup, restauração, aprendizados).
 * Tudo que o app "aprendeu" pode ser visto e apagado aqui - é o aparelho do
 * usuário, não o nosso.
 */
class ConfiguracoesViewModel(
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
    private val catalogoRepositorio: CatalogoRepositorio,
    private val backup: GerenciadorDeBackup,
) : ViewModel() {

    data class EstadoDasConfiguracoes(
        val configuracoes: ConfiguracoesApp = ConfiguracoesApp(),
        val perfil: PerfilRestricoes = PerfilRestricoes(),
        val backups: List<File> = emptyList(),
        val decisoesDoParser: List<DecisaoDoParserEntity> = emptyList(),
        val ocupado: Boolean = false,
    ) {
        val resumoDoUltimoBackup: String
            get() = if (configuracoes.ultimoBackupMs <= 0) {
                "Nenhum backup feito ainda"
            } else {
                "Último backup: " + Formatadores.dataHora(
                    java.time.Instant.ofEpochMilli(configuracoes.ultimoBackupMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime(),
                )
            }
    }

    /** Restricoes criadas pelo usuario - alimenta "Minhas restricoes personalizadas". */
    val alergiasCustomizadas: StateFlow<List<AlergiaCustomizada>> =
        configuracoesRepositorio.alergiasCustomizadas.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /**
     * Produtos com validade preenchida e vencendo nos proximos 30 dias (ou ja
     * vencidos). E um retrato carregado no init (e por [atualizarValidades]),
     * nao um Flow: a lista de validades muda raramente e a tela revalida ao
     * reabrir, o que cobre o caso de voltar do cadastro de um produto.
     */
    private val _validadesProximas = MutableStateFlow<List<Produto>>(emptyList())
    val validadesProximas: StateFlow<List<Produto>> = _validadesProximas.asStateFlow()

    private val _extras = MutableStateFlow(
        Triple<List<File>, List<DecisaoDoParserEntity>, Boolean>(emptyList(), emptyList(), false),
    )

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    /** Pedido de confirmação na primeira vez que o modo técnico é ligado. */
    private val _confirmarModoTecnico = MutableStateFlow(false)
    val confirmarModoTecnico: StateFlow<Boolean> = _confirmarModoTecnico.asStateFlow()

    val estado: StateFlow<EstadoDasConfiguracoes> = combine(
        configuracoesRepositorio.configuracoes,
        configuracoesRepositorio.perfil,
        _extras,
    ) { config, perfil, extras ->
        EstadoDasConfiguracoes(
            configuracoes = config,
            perfil = perfil,
            backups = extras.first,
            decisoesDoParser = extras.second,
            ocupado = extras.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDasConfiguracoes())

    init {
        recarregarExtras()
        atualizarValidades()
    }

    private fun recarregarExtras(ocupado: Boolean = false) {
        viewModelScope.launch {
            _extras.value = Triple(
                backup.listarBackups(),
                catalogoRepositorio.decisoesDoParser(),
                ocupado,
            )
        }
    }

    /** Recarrega o painel de validades (chamado no init; ponto de revalidacao). */
    fun atualizarValidades() {
        viewModelScope.launch {
            runCatching { catalogoRepositorio.validadesProximas() }
                .onSuccess { _validadesProximas.value = it }
        }
    }

    // --- aparência -----------------------------------------------------------------------

    fun definirTema(tema: TipoTema) = viewModelScope.launch { configuracoesRepositorio.definirTema(tema) }

    fun definirAltoContraste(ativo: Boolean) =
        viewModelScope.launch { configuracoesRepositorio.definirAltoContraste(ativo) }

    /** Escala tipográfica do app, de 100 % a 160 %, em degraus de 5. */
    fun definirEscalaDaFonte(porcentagem: Int) =
        viewModelScope.launch { configuracoesRepositorio.definirEscalaDaFonte(porcentagem) }

    fun definirCoresDinamicas(ativo: Boolean) =
        viewModelScope.launch { configuracoesRepositorio.definirCoresDinamicas(ativo) }

    // --- uso ------------------------------------------------------------------------------

    fun definirSons(ativo: Boolean) = viewModelScope.launch { configuracoesRepositorio.definirSons(ativo) }

    fun definirVibracao(ativo: Boolean) = viewModelScope.launch { configuracoesRepositorio.definirVibracao(ativo) }

    /**
     * Modo técnico desligado por padrão; na primeira vez que alguém liga, o app
     * explica o que muda antes de ligar de fato.
     */
    fun pedirModoTecnico(ativo: Boolean) {
        if (!ativo) {
            viewModelScope.launch { configuracoesRepositorio.definirModoTecnico(false) }
            return
        }
        _confirmarModoTecnico.value = true
    }

    fun confirmarModoTecnico() {
        _confirmarModoTecnico.value = false
        viewModelScope.launch {
            configuracoesRepositorio.definirModoTecnico(true)
            _mensagem.value = "Modo técnico ligado: a comparação nutricional aparece nas listas."
        }
    }

    fun cancelarModoTecnico() {
        _confirmarModoTecnico.value = false
    }

    fun reexibirDicas() = viewModelScope.launch {
        configuracoesRepositorio.reexibirDicas()
        _mensagem.value = "As dicas voltarão a aparecer."
    }

    // --- restrições alimentares --------------------------------------------------------------

    fun alternarAlergeno(alergeno: Alergeno) =
        viewModelScope.launch { configuracoesRepositorio.alternarAlergeno(alergeno) }

    fun definirSemGluten(ativo: Boolean) =
        viewModelScope.launch { configuracoesRepositorio.definirSemGluten(ativo) }

    /**
     * Cria uma restricao personalizada a partir do formulario dos Ajustes.
     * As palavras-chave chegam cruas, separadas por virgula; aqui viram uma
     * lista limpa que o repositorio normaliza e guarda.
     */
    fun adicionarAlergiaCustomizada(nome: String, palavras: String) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) {
            _mensagem.value = "Dê um nome para a restrição."
            return
        }
        val termos = palavras.split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            configuracoesRepositorio.adicionarAlergiaCustomizada(nomeLimpo, termos)
            _mensagem.value =
                "Restrição \"$nomeLimpo\" adicionada. O app vai avisar quando um produto puder contê-la."
        }
    }

    fun removerAlergiaCustomizada(id: Long) = viewModelScope.launch {
        configuracoesRepositorio.removerAlergiaCustomizada(id)
        _mensagem.value = "Restrição removida — fica na lixeira por 30 dias."
    }

    // --- meta de economia ---------------------------------------------------------------

    /**
     * Define a meta mensal a partir do texto digitado em reais (aceita virgula
     * e ponto, como o resto do app). Texto sem numero vira aviso, nunca excecao.
     */
    fun definirMetaEconomia(texto: String) {
        val valor = TextoUtil.paraDecimal(texto)
        if (valor == null || valor.signum() <= 0) {
            _mensagem.value = "Digite um valor em reais, como 120,00."
            return
        }
        viewModelScope.launch {
            val centavos = Mapeadores.reaisParaCentavos(valor)
            configuracoesRepositorio.definirMetaEconomia(centavos)
            _mensagem.value = "Meta mensal definida: ${Formatadores.moeda(valor)}."
        }
    }

    fun removerMetaEconomia() {
        viewModelScope.launch {
            configuracoesRepositorio.definirMetaEconomia(null)
            _mensagem.value = "Meta de economia removida."
        }
    }

    // --- dados -------------------------------------------------------------------------------

    /** Exporta para a pasta privada do app (não precisa de permissão). */
    fun exportarBackup() {
        viewModelScope.launch {
            _extras.value = _extras.value.copy(third = true)
            val arquivo = File(backup.pastaDeBackups(), backup.nomeSugerido())
            val resultado = runCatching {
                withContext(Dispatchers.IO) { arquivo.outputStream().use { backup.exportar(it) } }
            }
            configuracoesRepositorio.registrarBackup()
            _mensagem.value = if (resultado.isSuccess) {
                "Backup salvo em ${arquivo.name}."
            } else {
                "Não consegui salvar o backup."
            }
            recarregarExtras()
        }
    }

    /** Exporta para um destino escolhido pelo usuário (SAF). */
    fun exportarPara(contexto: Context, destino: Uri) {
        viewModelScope.launch {
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openOutputStream(destino)?.use { backup.exportar(it) }
                        ?: error("destino inválido")
                }
            }
            configuracoesRepositorio.registrarBackup()
            _mensagem.value = if (resultado.isSuccess) "Backup exportado." else "Não consegui exportar."
            recarregarExtras()
        }
    }

    fun restaurar(contexto: Context, origem: Uri) {
        viewModelScope.launch {
            _extras.value = _extras.value.copy(third = true)
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(origem)?.use { backup.restaurar(it) }
                        ?: error("arquivo inválido")
                }
            }
            _mensagem.value = resultado.getOrNull()?.mensagem ?: "Não consegui ler o arquivo."
            recarregarExtras()
        }
    }

    fun restaurarDeArquivo(arquivo: File) {
        viewModelScope.launch {
            _extras.value = _extras.value.copy(third = true)
            val resultado = runCatching {
                withContext(Dispatchers.IO) { arquivo.inputStream().use { backup.restaurar(it) } }
            }
            _mensagem.value = resultado.getOrNull()?.mensagem ?: "Não consegui ler o backup."
            recarregarExtras()
        }
    }

    fun esquecerCategorias() {
        viewModelScope.launch {
            catalogoRepositorio.esquecerCategorias()
            _mensagem.value = "Correções de categoria apagadas."
            recarregarExtras()
        }
    }

    fun esquecerDecisoesDoParser() {
        viewModelScope.launch {
            catalogoRepositorio.esquecerDecisoesDoParser()
            _mensagem.value = "O app voltará a perguntar sobre quantidades."
            recarregarExtras()
        }
    }

    fun esquecerDecisao(termo: String) {
        viewModelScope.launch {
            catalogoRepositorio.esquecerDecisaoDoParser(termo)
            recarregarExtras()
        }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }
}

/** Açúcar para trocar só o terceiro campo da tripla de extras. */
private fun <A, B, C> Triple<A, B, C>.copy(third: C): Triple<A, B, C> = Triple(first, second, third)

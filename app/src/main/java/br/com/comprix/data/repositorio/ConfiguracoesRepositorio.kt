package br.com.comprix.data.repositorio

import br.com.comprix.data.local.AlergiaCustomizadaDao
import br.com.comprix.data.local.ConfiguracoesDao
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.domain.modelo.Alergeno
import br.com.comprix.domain.modelo.AlergiaCustomizada
import br.com.comprix.domain.modelo.ConfiguracoesApp
import br.com.comprix.domain.modelo.Nutriente
import br.com.comprix.domain.modelo.PerfilRestricoes
import br.com.comprix.domain.modelo.TipoTema
import br.com.comprix.util.TextoUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Preferencias do app e perfil de restricoes.
 *
 * Guardadas em duas tabelas de linha unica (id = 1) em vez de DataStore: o
 * banco ja esta aberto, o acesso e reativo pelos mesmos Flows e evitamos mais
 * uma dependencia e mais um arquivo para o backup carregar.
 *
 * As alergias customizadas criadas pelo usuario ficam em tabela propria
 * (`alergias_customizadas`, varias linhas) e entram no [perfil] via combine -
 * salvar o perfil de linha unica nunca as apaga.
 */
class ConfiguracoesRepositorio(
    private val dao: ConfiguracoesDao,
    private val alergias: AlergiaCustomizadaDao,
    private val lixeira: LixeiraRepositorio? = null,
) {

    val configuracoes: Flow<ConfiguracoesApp> = dao.observar().map(Mapeadores::paraDominio)

    val alergiasCustomizadas: Flow<List<AlergiaCustomizada>> =
        alergias.observarTodas().map { lista ->
            with(Mapeadores) { lista.map { it.paraModelo() } }
        }

    val perfil: Flow<PerfilRestricoes> =
        combine(dao.observarPerfil(), alergiasCustomizadas) { entidade, customizadas ->
            Mapeadores.paraDominio(entidade).copy(customizadas = customizadas)
        }

    suspend fun carregar(): ConfiguracoesApp = Mapeadores.paraDominio(dao.carregar())

    suspend fun carregarPerfil(): PerfilRestricoes = Mapeadores.paraDominio(dao.carregarPerfil())

    suspend fun salvar(configuracoes: ConfiguracoesApp) = dao.salvar(Mapeadores.paraEntidade(configuracoes))

    /** So toca a tabela de linha unica; as alergias customizadas nao sao afetadas. */
    suspend fun salvarPerfil(perfil: PerfilRestricoes) = dao.salvarPerfil(Mapeadores.paraEntidade(perfil))

    private suspend fun alterar(bloco: (ConfiguracoesApp) -> ConfiguracoesApp) {
        salvar(bloco(carregar()))
    }

    suspend fun definirTema(tema: TipoTema) = alterar { it.copy(tema = tema) }

    suspend fun definirAltoContraste(ativo: Boolean) = alterar { it.copy(altoContraste = ativo) }

    /** Escala tipografica do app, presa a degraus de 5 entre 100 e 160 por cento. */
    suspend fun definirEscalaDaFonte(porcentagem: Int) = alterar {
        it.copy(escalaDaFonte = (porcentagem / 5 * 5).coerceIn(100, 160))
    }

    suspend fun definirCoresDinamicas(ativo: Boolean) = alterar { it.copy(coresDinamicas = ativo) }

    suspend fun definirSons(ativo: Boolean) = alterar { it.copy(sons = ativo) }

    suspend fun definirVibracao(ativo: Boolean) = alterar { it.copy(vibracao = ativo) }

    /** Modo tecnico (Secao 8): desligado por padrao, com dialogo na 1a ativacao. */
    suspend fun definirModoTecnico(ativo: Boolean) = alterar { it.copy(modoTecnico = ativo) }

    suspend fun concluirOnboarding() = alterar { it.copy(onboardingConcluido = true) }

    suspend fun definirNutrientes(nutrientes: List<Nutriente>) =
        alterar { it.copy(nutrientesComparados = nutrientes.ifEmpty { Nutriente.selecaoPadrao() }) }

    /** Marca uma dica contextual como ja vista (nunca mais aparece). */
    suspend fun marcarDicaVista(chave: String) = alterar { it.copy(dicasVistas = it.dicasVistas + chave) }

    suspend fun reexibirDicas() = alterar { it.copy(dicasVistas = emptySet()) }

    suspend fun registrarBackup(quando: Long = System.currentTimeMillis()) =
        alterar { it.copy(ultimoBackupMs = quando) }

    /**
     * Meta de economia mensal em centavos; `null` remove a meta. Usa o mesmo
     * padrao [alterar] dos demais ajustes: le a linha unica, copia e grava - a
     * meta nunca sobrescreve as outras preferencias.
     */
    suspend fun definirMetaEconomia(centavos: Long?) = alterar { it.copy(metaEconomiaCentavos = centavos) }

    suspend fun definirSemGluten(ativo: Boolean) {
        salvarPerfil(carregarPerfil().copy(semGluten = ativo))
    }

    suspend fun alternarAlergeno(alergeno: Alergeno) {
        val atual = carregarPerfil()
        val novos = if (alergeno in atual.alergenos) atual.alergenos - alergeno else atual.alergenos + alergeno
        salvarPerfil(atual.copy(alergenos = novos))
    }

    /**
     * Cria uma restricao propria do usuario. Ignorado quando o nome (ja
     * normalizado) existe ou fica vazio; as palavras extras entram
     * normalizadas, prontas para o [br.com.comprix.domain.alergia.VerificadorDeAlergias].
     */
    suspend fun adicionarAlergiaCustomizada(nome: String, palavras: List<String> = emptyList()) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) return
        val chave = TextoUtil.normalizar(nomeLimpo)
        val existentes = alergias.listarTodas()
        if (existentes.any { TextoUtil.normalizar(it.nome) == chave }) return
        val termos = palavras.map { TextoUtil.normalizar(it) }.filter { it.isNotBlank() }.distinct()
        val nova = with(Mapeadores) { AlergiaCustomizada(nome = nomeLimpo, palavras = termos).paraEntidade() }
        alergias.inserir(nova)
    }

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias). */
    suspend fun removerAlergiaCustomizada(id: Long) {
        if (lixeira != null) {
            lixeira.enviarAlergia(id)
            return
        }
        alergias.remover(id)
    }
}

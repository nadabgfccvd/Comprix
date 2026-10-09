package br.com.comprix.presentation.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.comprix.data.parser.EntradaDeTextoLivre
import br.com.comprix.data.repositorio.CatalogoRepositorio
import br.com.comprix.data.repositorio.ConfiguracoesRepositorio
import br.com.comprix.data.repositorio.ListaRepositorio
import br.com.comprix.data.repositorio.PrecoRepositorio
import br.com.comprix.domain.lista.HistoricoDeAdicoes
import br.com.comprix.domain.lista.InterpretadorDeAdicaoRapida
import br.com.comprix.domain.lista.OrganizadorDeLista
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.Estabelecimento
import br.com.comprix.domain.modelo.ItemComProduto
import br.com.comprix.domain.modelo.ItemDaLista
import br.com.comprix.domain.modelo.ListaDeCompras
import br.com.comprix.domain.modelo.ModoComparacaoUnidade
import br.com.comprix.domain.modelo.PrecoRegistrado
import br.com.comprix.domain.modelo.Produto
import br.com.comprix.domain.modelo.VereditoDeOpcoes
import br.com.comprix.domain.parser.ItemInterpretado
import br.com.comprix.domain.parser.OpcaoDeSugestao
import br.com.comprix.domain.preco.AuditoriaDeCobertura
import br.com.comprix.domain.preco.MotorDePrecos
import br.com.comprix.util.Constantes
import br.com.comprix.util.Formatadores
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Quantas sugestoes o cartao "Comprar de novo" mostra, no maximo (top do historico). */
private const val LIMITE_DE_RECOMPRA = 8

/**
 * Tela de uma lista: adicionar, organizar e marcar itens.
 *
 * ## Quem interpreta o que o usuário digita
 * Um interpretador só: o `domain/parser`, através de [EntradaDeTextoLivre].
 * O [InterpretadorDeAdicaoRapida] entra apenas para destacar o preço digitado
 * no fim da linha ("arroz 5kg 24,90"), que o parser não trata de propósito.
 *
 * ## Sugestões
 * O item **sempre** entra na lista. Quando o parser fica em dúvida, a pergunta
 * aparece na linha do próprio item (nunca em diálogo) e fica em
 * [EstadoDaLista.sugestoes] até o usuário responder ou ignorar.
 */
class ListaViewModel(
    private val listaId: Long,
    private val listaRepositorio: ListaRepositorio,
    private val catalogoRepositorio: CatalogoRepositorio,
    private val precoRepositorio: PrecoRepositorio,
    private val configuracoesRepositorio: ConfiguracoesRepositorio,
    private val entradaDeTextoLivre: EntradaDeTextoLivre,
) : ViewModel() {

    data class EstadoDaLista(
        val lista: ListaDeCompras? = null,
        val grupos: List<OrganizadorDeLista.GrupoDeItens> = emptyList(),
        val itens: List<ItemComProduto> = emptyList(),
        val categorias: Map<Long, Categoria> = emptyMap(),
        val estabelecimentos: List<Estabelecimento> = emptyList(),
        val precos: List<PrecoRegistrado> = emptyList(),
        val relatorio: AuditoriaDeCobertura.Relatorio =
            AuditoriaDeCobertura.Relatorio(emptyList(), emptyList(), emptyList(), emptyList(), 0),
        val sugestoes: Map<Long, ItemInterpretado> = emptyMap(),
        val consulta: String = "",
        val carregando: Boolean = true,
        val mostrarDica: Boolean = false,
    ) {
        val totalDeItens: Int get() = itens.size
        val comprados: Int get() = itens.count { it.item.comprado }
        val progresso: Float get() = OrganizadorDeLista.progresso(itens)
        val vazia: Boolean get() = !carregando && itens.isEmpty()
        val podeComparar: Boolean get() = itens.isNotEmpty()

        /** Orcamento da lista em centavos (null = sem orcamento); espelho de [lista]. */
        val orcamentoCentavos: Long? get() = lista?.orcamentoCentavos
    }

    private val _consulta = MutableStateFlow("")
    private val _sugestoes = MutableStateFlow<Map<Long, ItemInterpretado>>(emptyMap())

    /**
     * Sugestoes do autocomplete do campo de adicao rapida (nomes do catalogo
     * local + acervo de fabrica). Separado de [EstadoDaLista.sugestoes], que
     * guarda as perguntas do parser por item.
     */
    private val _sugestoesDeDigitacao = MutableStateFlow<List<String>>(emptyList())
    val sugestoesDeDigitacao: StateFlow<List<String>> = _sugestoesDeDigitacao.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    /** Último item removido, para a faixa de desfazer. */
    private val _itemRemovido = MutableStateFlow<ItemDaLista?>(null)
    val itemRemovido: StateFlow<ItemDaLista?> = _itemRemovido.asStateFlow()

    val estado: StateFlow<EstadoDaLista> = combine(
        listaRepositorio.observarLista(listaId),
        listaRepositorio.observarItens(listaId),
        catalogoRepositorio.categorias,
        combine(
            precoRepositorio.estabelecimentos,
            precoRepositorio.observarPrecosDaLista(listaId),
        ) { lojas, precos -> lojas to precos },
        combine(
            _consulta,
            _sugestoes,
            configuracoesRepositorio.configuracoes,
        ) { consulta, sugestoes, config ->
            Triple(consulta, sugestoes, Constantes.DICA_DETALHE_LISTA !in config.dicasVistas)
        },
    ) { lista, itens, categorias, lojasEPrecos, extras ->
        val (lojas, precos) = lojasEPrecos
        val (consulta, sugestoes, mostrarDica) = extras
        val porId = categorias.associateBy { it.id }
        val filtrados = OrganizadorDeLista.filtrar(itens, consulta)
        EstadoDaLista(
            lista = lista,
            grupos = OrganizadorDeLista.agrupar(filtrados, porId),
            itens = itens,
            categorias = porId,
            estabelecimentos = lojas,
            precos = precos,
            relatorio = AuditoriaDeCobertura.auditar(itens, lojas, precos),
            sugestoes = sugestoes.filterKeys { id -> itens.any { it.item.id == id } },
            consulta = consulta,
            carregando = false,
            mostrarDica = mostrarDica && itens.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDaLista())

    // --- adicionar ---------------------------------------------------------------------

    /**
     * Adiciona o que foi digitado na barra de adição rápida.
     *
     * Uma linha pode virar vários itens ("arroz, feijão e 2 leite") e cada
     * item pode trazer o próprio preço ("uva 1kg 3,99, limao 1kg 6,50") -
     * preços por item são anotados na loja ativa (a primeira cadastrada, que
     * é a mesma que a tela de comparação abre por padrão). A adição entra no
     * histórico de desfazer/refazer.
     */
    fun adicionarTextoLivre(texto: String) {
        val linha = texto.trim()
        if (linha.isBlank()) return
        viewModelScope.launch {
            val ids = adicionarTextoInterno(linha)
            if (ids.isNotEmpty()) {
                HistoricoDeAdicoes.registrar(
                    listaId,
                    HistoricoDeAdicoes.AdicaoHistorica(linha, ids),
                )
            }
        }
    }

    /**
     * Núcleo da adição, reusado por adicionar e refazer. Devolve os ids
     * criados, na ordem do texto.
     */
    private suspend fun adicionarTextoInterno(linha: String): List<Long> {
        val comPreco = InterpretadorDeAdicaoRapida.extrairPreco(linha)
        val interpretados = entradaDeTextoLivre.interpretar(comPreco.restante.ifBlank { linha })
        if (interpretados.isEmpty()) {
            _mensagem.value = "Não entendi \"$linha\". Tente algo como \"2 kg de arroz\"."
            return emptyList()
        }

        val novasSugestoes = _sugestoes.value.toMutableMap()
        val alertas = mutableListOf<String>()
        val ids = mutableListOf<Long>()

        interpretados.forEach { interpretado ->
            val id = entradaDeTextoLivre.adicionar(listaId, interpretado)
            ids += id
            interpretado.sugestao?.let { novasSugestoes[id] = interpretado }
            interpretado.alerta?.let { alertas += it }
        }
        _sugestoes.value = novasSugestoes

        // Preço por item: o parser agora entrega o preço de cada segmento
        // ("uva 1kg 3,99" -> preco 3,99). Cada um grava na loja ativa/primeira.
        val loja = precoRepositorio.listarEstabelecimentos().firstOrNull()
        var precosGravados = 0
        if (loja != null) {
            interpretados.forEachIndexed { indice, interpretado ->
                val precoDoItem = interpretado.preco ?: return@forEachIndexed
                val gravado = listaRepositorio.item(ids[indice]) ?: return@forEachIndexed
                precoRepositorio.registrarPreco(
                    item = gravado,
                    produtoId = gravado.produtoId,
                    estabelecimentoId = loja.id,
                    preco = precoDoItem,
                )
                precosGravados++
            }
        }
        // Compat: item único cujo preço foi tirado do restante pelo
        // InterpretadorDeAdicaoRapida (o parser não o enxerga nesse caso).
        if (precosGravados == 0 && comPreco.preco != null && loja != null && ids.size == 1) {
            val gravado = listaRepositorio.item(ids[0])
            if (gravado != null) {
                precoRepositorio.registrarPreco(
                    item = gravado,
                    produtoId = gravado.produtoId,
                    estabelecimentoId = loja.id,
                    preco = comPreco.preco,
                )
                precosGravados++
            }
        }

        _mensagem.value = when {
            precosGravados > 0 && interpretados.size == 1 && loja != null -> {
                val precoUnico = interpretados.first().preco ?: comPreco.preco
                "Item adicionado. ${Formatadores.moeda(precoUnico!!)} anotado em ${loja.nome}."
            }
            precosGravados > 0 && loja != null ->
                "${interpretados.size} itens adicionados. " +
                    "$precosGravados ${if (precosGravados == 1) "preço anotado" else "preços anotados"} em ${loja.nome}."
            alertas.isNotEmpty() -> alertas.first()
            interpretados.size > 1 -> "${interpretados.size} itens adicionados."
            else -> null
        }
        return ids
    }

    /** Entrada vinda do scanner (código de barras ou rótulo já lido). */
    fun adicionarProdutoExistente(produto: Produto, preco: BigDecimal?, estabelecimentoId: Long?) {
        viewModelScope.launch {
            val id = listaRepositorio.adicionarItem(
                ItemDaLista(
                    listaId = listaId,
                    produtoId = produto.id,
                    unidade = produto.unidadePadrao,
                ),
            )
            val gravado = listaRepositorio.item(id)
            if (preco != null && estabelecimentoId != null && gravado != null) {
                precoRepositorio.registrarPreco(
                    item = gravado,
                    produtoId = produto.id,
                    estabelecimentoId = estabelecimentoId,
                    preco = preco,
                )
            }
            _mensagem.value = "${produto.nome} entrou na lista."
        }
    }

    // --- favoritos do catalogo -----------------------------------------------------------

    /**
     * Produtos com estrela, prontos para o atalho de 1 toque da folha
     * "Favoritos" (menu da lista). Observa o banco: favoritar um produto em
     * qualquer tela (folha do item, ficha do produto) atualiza aqui sozinho.
     */
    val favoritosDoCatalogo: StateFlow<List<Produto>> = catalogoRepositorio.produtosFavoritos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Caminho de 1 toque da folha de Favoritos.
     *
     * Se o produto JA esta na lista e ela nao esta finalizada, NAO duplica o
     * item: soma 1 a quantidade da primeira ocorrencia
     * ([ListaRepositorio.somarQuantidadeDoProduto]). Se nao esta (ou a lista
     * esta finalizada), entra como item novo, pelo mesmo caminho do catalogo -
     * unidade padrao do produto, sem preco anotado.
     */
    fun adicionarFavorito(produto: Produto) {
        viewModelScope.launch {
            val listaFinalizada = estado.value.lista?.finalizada == true
            val somou = !listaFinalizada && listaRepositorio.somarQuantidadeDoProduto(listaId, produto.id)
            if (somou) {
                _mensagem.value = "Já estava na lista — quantidade somada."
            } else {
                listaRepositorio.adicionarItem(
                    ItemDaLista(
                        listaId = listaId,
                        produtoId = produto.id,
                        unidade = produto.unidadePadrao,
                    ),
                )
                _mensagem.value = "${produto.nome} adicionado."
            }
        }
    }

    /**
     * Favorita (ou desfavorita) um produto: acao da folha de acoes do item
     * (toque longo no cartao) e da estrela da propria folha de Favoritos.
     * Grava a estrela no PRODUTO, entao a folha de Favoritos, os cartoes e a
     * ficha enxergam o mesmo estado sem recarregar nada.
     */
    fun marcarFavorito(produtoId: Long) {
        viewModelScope.launch {
            val produto = catalogoRepositorio.produtoPorId(produtoId) ?: return@launch
            val favorito = !produto.favorito
            catalogoRepositorio.marcarProdutoFavorito(produtoId, favorito)
            _mensagem.value = if (favorito) {
                "\"${produto.nome}\" favoritado — aparece no menu Favoritos."
            } else {
                "\"${produto.nome}\" saiu dos favoritos."
            }
        }
    }

    // --- comprar de novo -----------------------------------------------------------------

    /**
     * Sugestoes do cartao "Comprar de novo": os produtos mais registrados no
     * historico de precos (top [LIMITE_DE_RECOMPRA], na ordem do ranking),
     * EXCETO os que ja estao nesta lista. E a versao domesticada do memory:
     * quem compra arroz toda semana adiciona com 1 toque, sem digitar.
     *
     * Carregada no init e recalculada por [atualizarSugestoesDeRecompra]
     * (a tela chama quando a composicao da lista muda) - manter aqui um
     * fluxo simples de lista, sem combine com o estado inteiro.
     */
    private val _sugestoesParaComprarDeNovo = MutableStateFlow<List<Produto>>(emptyList())
    val sugestoesParaComprarDeNovo: StateFlow<List<Produto>> =
        _sugestoesParaComprarDeNovo.asStateFlow()

    init {
        // Primeira carga das sugestoes "Comprar de novo" (o bloco fica DEPOIS
        // da propriedade que ele alimenta); depois disso a tela chama
        // atualizarSugestoesDeRecompra() sempre que a composicao muda.
        atualizarSugestoesDeRecompra()
    }

    /**
     * Recarrega as sugestoes "Comprar de novo" a partir do historico.
     *
     * A ordem segue o ranking do banco (mais registrados primeiro); quem ja
     * tem item nesta lista sai do cartao. Produto apagado do catalogo depois
     * do registro simplesmente nao volta no mapa por id (mapNotNull).
     */
    fun atualizarSugestoesDeRecompra() {
        viewModelScope.launch {
            val contagens = listaRepositorio.produtosMaisRegistrados(LIMITE_DE_RECOMPRA)
            if (contagens.isEmpty()) {
                _sugestoesParaComprarDeNovo.value = emptyList()
                return@launch
            }
            val jaNaLista = estado.value.itens.mapTo(HashSet()) { it.item.produtoId }
            val porId = catalogoRepositorio
                .produtosPorIds(contagens.map { it.produtoId })
                .associateBy { it.id }
            _sugestoesParaComprarDeNovo.value =
                contagens.mapNotNull { porId[it.produtoId] }.filter { it.id !in jaNaLista }
        }
    }

    // --- sugestões do parser ------------------------------------------------------------

    fun responderSugestao(itemId: Long, opcao: OpcaoDeSugestao) {
        val interpretado = _sugestoes.value[itemId] ?: return
        viewModelScope.launch {
            val ajustado = entradaDeTextoLivre.responder(interpretado, opcao)
            val item = listaRepositorio.item(itemId)
            if (item != null && !opcao.manterComoEsta) {
                listaRepositorio.atualizarItem(
                    item.copy(
                        quantidade = ajustado.quantidade ?: item.quantidade,
                        unidade = ajustado.unidade ?: item.unidade,
                        ehKit = ajustado.ehKit,
                        itensPorKit = ajustado.itensPorKit,
                    ),
                )
            }
            _sugestoes.value = _sugestoes.value - itemId
        }
    }

    fun ignorarSugestao(itemId: Long) {
        _sugestoes.value = _sugestoes.value - itemId
    }

    // --- manutenção dos itens ------------------------------------------------------------

    fun alternarComprado(item: ItemComProduto) {
        viewModelScope.launch {
            listaRepositorio.marcarComprado(item.item.id, !item.item.comprado)
        }
    }

    fun desmarcarTodos() {
        viewModelScope.launch {
            listaRepositorio.desmarcarTodos(listaId)
            _mensagem.value = "Todos os itens voltaram para a lista."
        }
    }

    /**
     * Reordena por arrastar: reposiciona o item dentro do próprio grupo e
     * reatribui a ordem manual de todo o grupo (dezenas, mesma convenção da
     * adição). A ordem visual do OrganizadorDeLista mantém comprados por
     * último, então cruzar essa fronteira ao arrastar não quebra nada - a
     * ordem manual é só o desempate dentro de cada lado.
     */
    fun moverItemNoGrupo(
        grupo: OrganizadorDeLista.GrupoDeItens,
        idMovido: Long,
        indiceVisual: Int,
    ) {
        viewModelScope.launch {
            val movido = grupo.itens.firstOrNull { it.item.id == idMovido } ?: return@launch
            val restantes = grupo.itens.filter { it.item.id != idMovido }
            val indice = indiceVisual.coerceIn(0, restantes.size)
            val novaOrdem = restantes.take(indice) + movido + restantes.drop(indice)
            listaRepositorio.aplicarOrdem(
                novaOrdem.mapIndexed { posicao, item -> item.item.id to (posicao + 1) * 10 }.toMap(),
            )
        }
    }

    /**
     * Move um item para OUTRA categoria ao soltar o arrasto sobre o cabecalho
     * dela. A categoria mora no PRODUTO (catalogo global), entao o caminho e o
     * mesmo de [salvarEdicao]: `catalogoRepositorio.salvarProduto` com o
     * `categoriaId` novo + `lembrarCategoria` para o app aprender o padrao -
     * todas as listas que mostram esse produto o veem na nova categoria.
     *
     * A ordem manual e por item (desempate dentro do grupo), entao os dois
     * grupos sao regravados de uma vez: o destino recebe o movido na posicao
     * visual em que foi solto e a origem compacta os restantes, sem deixar
     * buraco. Nada muda durante o arrasto - a troca acontece inteira aqui.
     */
    fun moverItemEntreGrupos(
        origem: OrganizadorDeLista.GrupoDeItens,
        destino: OrganizadorDeLista.GrupoDeItens,
        idMovido: Long,
        indiceNoDestino: Int,
    ) {
        viewModelScope.launch {
            val movido = origem.itens.firstOrNull { it.item.id == idMovido } ?: return@launch
            val produto = movido.produto
            if (produto.categoriaId != destino.categoria.id) {
                catalogoRepositorio.salvarProduto(
                    produto.copy(categoriaId = destino.categoria.id),
                )
                catalogoRepositorio.lembrarCategoria(produto.nome, destino.categoria.id)
            }
            val doDestino = destino.itens.filter { it.item.id != idMovido }
            val indice = indiceNoDestino.coerceIn(0, doDestino.size)
            val novaOrdem = doDestino.take(indice) + movido + doDestino.drop(indice)
            val mapaDeOrdem = novaOrdem
                .mapIndexed { posicao, item -> item.item.id to (posicao + 1) * 10 }
                .toMap()
                .toMutableMap()
            // Origem regravada do zero: sem o movido, sem buraco na ordem.
            origem.itens
                .filter { it.item.id != idMovido }
                .forEachIndexed { posicao, item -> mapaDeOrdem[item.item.id] = (posicao + 1) * 10 }
            listaRepositorio.aplicarOrdem(mapaDeOrdem)
            _mensagem.value = "\"${produto.nome}\" movido para ${destino.categoria.nome}."
        }
    }

    /** Salva a edição completa do item (quantidade, unidade, kit, categoria, nome). */
    fun salvarEdicao(original: ItemComProduto, item: ItemDaLista, nome: String, categoriaId: Long) {
        viewModelScope.launch {
            listaRepositorio.atualizarItem(item)
            val nomeLimpo = nome.trim()
            if (nomeLimpo.isNotBlank() &&
                (nomeLimpo != original.produto.nome || categoriaId != original.produto.categoriaId)
            ) {
                catalogoRepositorio.salvarProduto(
                    original.produto.copy(nome = nomeLimpo, categoriaId = categoriaId),
                )
                if (categoriaId != original.produto.categoriaId) {
                    catalogoRepositorio.lembrarCategoria(nomeLimpo, categoriaId)
                }
            }
            _mensagem.value = "Item atualizado."
        }
    }

    /**
     * Grava o peso médio estimado de uma unidade do produto (tela 17).
     *
     * O peso vai para o **produto**, não para o item: se a mesma alface entra
     * em outra lista no mês que vem, a estimativa continua valendo. Junto, o
     * item passa a ser comparado por peso — é para isso que o número existe.
     *
     * `null` apaga a estimativa e volta a comparação para “por unidade”.
     */
    fun definirPesoEstimado(itemComProduto: ItemComProduto, pesoEmBase: java.math.BigDecimal?) {
        viewModelScope.launch {
            catalogoRepositorio.definirPesoMedioEstimado(itemComProduto.produto.id, pesoEmBase)
            listaRepositorio.atualizarItem(
                itemComProduto.item.copy(
                    modoComparacao = if (pesoEmBase != null) {
                        ModoComparacaoUnidade.POR_PESO
                    } else {
                        ModoComparacaoUnidade.POR_UNIDADE
                    },
                ),
            )
            _mensagem.value = if (pesoEmBase != null) {
                "Peso estimado de ${Formatadores.quantidade(pesoEmBase)} g por unidade aplicado."
            } else {
                "Voltou a comparar por unidade."
            }
        }
    }

    /**
     * Remove o produto da lista inteira - ação explícita, separada de limpar o
     * preço de uma loja (que fica na tela de comparação).
     */
    fun removerItem(item: ItemComProduto) {
        viewModelScope.launch {
            listaRepositorio.removerItem(item.item)
            _itemRemovido.value = item.item
            _mensagem.value = "${item.produto.nome} saiu da lista."
        }
    }

    fun desfazerRemocao() {
        val item = _itemRemovido.value ?: return
        viewModelScope.launch {
            listaRepositorio.adicionarItem(item.copy(id = 0))
            _itemRemovido.value = null
            _mensagem.value = "Item devolvido à lista."
        }
    }

    fun descartarDesfazer() {
        _itemRemovido.value = null
    }

    // --- desfazer/refazer da adicao -------------------------------------------------------

    /**
     * O historico de adicoes (desfazer/refazer) nao mora neste ViewModel:
     * vive em [HistoricoDeAdicoes], objeto de processo, chaveado por [listaId].
     *
     * Motivo: o irPara da navegacao troca de aba com popUpTo(Rotas.LISTAS), o
     * que desempilha a entrada "lista/{id}" e destroi este ViewModel junto com
     * o ViewModelStore dele. Pilhas locais morriam a cada ida e volta entre
     * abas (o ViewModel novo nascia com o historico vazio). No objeto, o
     * historico sobrevive a navegacao e a recriacao do ViewModel, e cada
     * lista tem a propria pilha - desfazer numa lista nao toca no historico
     * de outra.
     */
    val podeDesfazer: StateFlow<Boolean> = HistoricoDeAdicoes.pilhaDeDesfazer(listaId)
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val podeRefazer: StateFlow<Boolean> = HistoricoDeAdicoes.pilhaDeRefazer(listaId)
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Texto a devolver a caixa de adicao: null = nada; string = substitui (mesmo vazia). */
    private val _textoParaRestaurar = MutableStateFlow<String?>(null)
    val textoParaRestaurar: StateFlow<String?> = _textoParaRestaurar.asStateFlow()

    /** A tela avisa que ja recolocou o texto na caixa (evento de uma vez). */
    fun textoRestauradoConsumido() {
        _textoParaRestaurar.value = null
    }

    /**
     * Desfaz a ultima adicao: tira o topo do historico (no objeto) e remove
     * os itens criados, devolvendo TUDO que estava escrito para a caixa de
     * adicao. O pop acontece ANTES da remocao no banco, para toques rapidos
     * seguidos nunca desfazerem a mesma adicao duas vezes. Nao usa a torrada
     * de item removido - tem mensagem propria.
     */
    fun desfazerUltimaAdicao() {
        val historica = HistoricoDeAdicoes.desfazer(listaId) ?: return
        viewModelScope.launch {
            historica.idsDosItens.forEach { id ->
                listaRepositorio.item(id)?.let { listaRepositorio.removerItem(it) }
            }
            _textoParaRestaurar.value = historica.texto
            _mensagem.value = "Adição desfeita — o texto voltou para a caixa."
        }
    }

    /**
     * Refaz a ultima adicao desfeita: reexecuta o mesmo texto e limpa a
     * caixa. A reexecucao cria itens com ids NOVOS, entao a entrada volta para
     * o desfazer (via empilharNoDesfazer, que preserva o resto do refazer e
     * permite refazer em cadeia) carregando esses ids novos, nunca os antigos.
     */
    fun refazerUltimaAdicao() {
        val historica = HistoricoDeAdicoes.refazer(listaId) ?: return
        viewModelScope.launch {
            val ids = adicionarTextoInterno(historica.texto)
            if (ids.isNotEmpty()) {
                HistoricoDeAdicoes.empilharNoDesfazer(
                    listaId,
                    HistoricoDeAdicoes.AdicaoHistorica(historica.texto, ids),
                )
            }
            _textoParaRestaurar.value = ""
            _mensagem.value = "Adição refeita."
        }
    }

    // --- modo compras ---------------------------------------------------------------------

    /**
     * Modo compras: cartoes maiores, banner de progresso no topo e a opcao de
     * esconder o que ja foi colocado no carrinho. Vive SO EM MEMORIA neste
     * ViewModel (por lista): e um estado de sessao da compra, nao uma
     * preferencia - desligar o app no mercado e reabrir com o modo ligado
     * atrapalharia quem so queria conferir a lista.
     */
    private val _modoCompras = MutableStateFlow(false)
    val modoCompras: StateFlow<Boolean> = _modoCompras.asStateFlow()

    /** Ocultar comprados: ligado dentro do modo compras, some ao desliga-lo. */
    private val _ocultarComprados = MutableStateFlow(false)
    val ocultarComprados: StateFlow<Boolean> = _ocultarComprados.asStateFlow()

    /** Liga/desliga o modo compras; desligar devolve tambem o filtro de comprados. */
    fun alternarModoCompras() {
        val novoEstado = !_modoCompras.value
        _modoCompras.value = novoEstado
        if (!novoEstado) _ocultarComprados.value = false
    }

    /** Liga/desliga a ocultacao de itens comprados (controle do banner do modo compras). */
    fun alternarOcultarComprados() {
        _ocultarComprados.value = !_ocultarComprados.value
    }

    // --- orcamento ------------------------------------------------------------------------

    /**
     * Grava o orcamento da lista em centavos; `null` remove.
     *
     * A doca da tela compara o total estimado com este valor (ver
     * [ListaRepositorio.nivelDoOrcamento]) e avisa o estouro na hora, sem
     * esperar a compra ser finalizada.
     */
    fun definirOrcamento(centavos: Long?) {
        viewModelScope.launch {
            listaRepositorio.definirOrcamento(listaId, centavos)
            _mensagem.value = if (centavos == null) {
                "Orçamento removido da lista."
            } else {
                "Orçamento da lista: ${Formatadores.moeda(BigDecimal(centavos).movePointLeft(Constantes.ESCALA_MOEDA))}."
            }
        }
    }

    // --- disponibilidade e preco direto da lista ------------------------------------------

    /**
     * Marca "nao tinha nessa loja" para um item - vindo da folha de acoes
     * (toque longo no cartao) ou da folha de pendencia.
     *
     * Diferente de limpar o preco: a marca e um DADO (a loja nao tem o
     * produto), e a auditoria para de cobrar aquela coluna.
     */
    fun marcarIndisponivel(item: ItemDaLista, estabelecimentoId: Long) {
        viewModelScope.launch {
            precoRepositorio.marcarIndisponivel(item.id, estabelecimentoId)
            val nome = estado.value.itens
                .firstOrNull { it.item.id == item.id }?.produto?.nome ?: "Item"
            _mensagem.value = "\"$nome\" marcado como indisponível nessa loja."
        }
    }

    /**
     * Remove a marca de indisponivel da loja: o item volta a ficar "sem preco"
     * naquela coluna, pronto para ser pesquisado de novo.
     */
    fun reativarDisponibilidade(item: ItemDaLista, estabelecimentoId: Long) {
        viewModelScope.launch {
            precoRepositorio.removerPreco(item.id, estabelecimentoId)
            _mensagem.value = "Marca de indisponível removida."
        }
    }

    /**
     * Grava o preco de uma pendencia direto da lista, sem abrir a comparacao.
     * O nome da loja da mensagem vem do estado (a tela ja o tem carregado).
     */
    fun salvarPrecoDaPendencia(
        item: ItemDaLista,
        produtoId: Long,
        estabelecimentoId: Long,
        valor: BigDecimal,
    ) {
        viewModelScope.launch {
            precoRepositorio.registrarPreco(item, produtoId, estabelecimentoId, valor)
            val nome = estado.value.itens
                .firstOrNull { it.item.id == item.id }?.produto?.nome ?: "item"
            val loja = estado.value.estabelecimentos
                .firstOrNull { it.id == estabelecimentoId }?.nome ?: "loja"
            _mensagem.value = "Preço salvo para \"$nome\" em $loja."
        }
    }

    // --- tela --------------------------------------------------------------------------

    fun definirConsulta(valor: String) {
        _consulta.value = valor
    }

    /**
     * Sinonimos de busca do acervo para o nome do produto ("Tangerina" ->
     * ["mexerica", "bergamota"]). Leitura pura em memoria (CatalogoRepositorio
     * -> CatalogoSemente), sem suspensao: a tela chama por cartao, na hora de
     * renderizar, e a legenda "tambem buscar: ..." reaparece sob o nome.
     */
    fun sinonimosDo(nome: String): List<String> = catalogoRepositorio.sinonimosDe(nome)

    /**
     * Veredito kit x avulso para o item, derivado dos precos JA ANOTADOS na
     * lista: e a mesma computacao que alimenta `LinhaComparativa.vereditoKit`
     * da matriz (dois tamanhos do mesmo produto, menor preco anotado de cada).
     * A folha de kit usa como dica imediata, antes de a pessoa digitar o preco
     * avulso; null quando nao ha duas embalagens com preco para comparar.
     */
    fun vereditoDeEmbalagem(itemComProduto: ItemComProduto): VereditoDeOpcoes? {
        val atual = estado.value
        return MotorDePrecos.vereditosDeEmbalagem(atual.itens, atual.precos)[itemComProduto.item.id]
    }

    /**
     * Pede sugestoes de digitacao para o termo atual. Termo em branco limpa a
     * lista - e o mesmo caminho usado quando a tela escolhe uma sugestao.
     */
    fun atualizarSugestoes(termo: String) {
        val limpo = termo.trim()
        if (limpo.isBlank()) {
            _sugestoesDeDigitacao.value = emptyList()
            return
        }
        viewModelScope.launch {
            _sugestoesDeDigitacao.value = entradaDeTextoLivre.sugestoesDeDigitacao(limpo)
        }
    }

    /**
     * Texto plano da lista para compartilhar (ACTION_SEND, sem foto nem
     * arquivo): cabecalho, itens agrupados por categoria e, se ja ha precos
     * registrados, o total estimado pelo menor preco de cada item.
     */
    fun textoParaCompartilhar(nomeDaLoja: String?): String {
        val atual = estado.value
        val lista = atual.lista ?: return ""
        val texto = StringBuilder("Lista ${lista.nome} (Comprix)")
        if (!nomeDaLoja.isNullOrBlank()) texto.append(" — ").append(nomeDaLoja)
        if (atual.itens.isEmpty()) return texto.toString()

        val grupos = atual.itens
            .groupBy { item -> atual.categorias[item.produto.categoriaId] }
            .entries
            .sortedBy { entrada -> entrada.key?.ordemPadrao ?: 999 }

        grupos.forEach { (categoria, itens) ->
            texto.append('\n').append('\n').append(categoria?.nome ?: "Outros").append('\n')
            itens.forEach { itemComProduto ->
                texto.append("• ")
                    .append(itemComProduto.produto.nome)
                    .append(" — ")
                    .append(Formatadores.quantidade(itemComProduto.item.quantidade))
                    .append(' ')
                    .append(itemComProduto.item.unidade.sigla)
                    .append('\n')
            }
        }

        val precosPorItem = atual.precos
            .filter { it.disponivel && it.preco.signum() > 0 }
            .groupBy { it.itemDaListaId }
        val total = atual.itens.fold(BigDecimal.ZERO) { soma, itemComProduto ->
            val preco = precosPorItem[itemComProduto.item.id]?.minByOrNull { it.preco }?.preco
                ?: return@fold soma
            soma.add(MotorDePrecos.totalDaLinha(preco, itemComProduto.item))
        }
        if (total.signum() > 0) {
            texto.append('\n').append("Total estimado: ").append(Formatadores.moeda(total))
        }
        return texto.toString()
    }

    fun renomearLista(novoNome: String) {
        val lista = estado.value.lista ?: return
        val limpo = novoNome.trim()
        if (limpo.isBlank()) return
        viewModelScope.launch { listaRepositorio.renomearLista(lista, limpo) }
    }

    fun dispensarDica() {
        viewModelScope.launch { configuracoesRepositorio.marcarDicaVista(Constantes.DICA_DETALHE_LISTA) }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }
}

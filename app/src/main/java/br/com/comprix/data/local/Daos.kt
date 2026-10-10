package br.com.comprix.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acesso ao banco. Toda leitura que alimenta a interface devolve [Flow]: a tela
 * reage sozinha a qualquer escrita, sem polling e sem "puxar para atualizar".
 */

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias ORDER BY ordemPadrao, nome")
    fun observarTodas(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias ORDER BY ordemPadrao, nome")
    suspend fun listarTodas(): List<CategoriaEntity>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirTodas(categorias: List<CategoriaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(categoria: CategoriaEntity): Long

    @Update
    suspend fun atualizar(categoria: CategoriaEntity)

    @Query("UPDATE categorias SET ordemPadrao = :ordem WHERE id = :id")
    suspend fun definirOrdem(id: Long, ordem: Int)

    @Query("DELETE FROM categorias WHERE id = :id AND origem = 'USUARIO'")
    suspend fun removerDoUsuario(id: Long)
}

@Dao
interface ProdutoDao {
    @Query("SELECT * FROM produtos ORDER BY nome")
    fun observarTodos(): Flow<List<ProdutoEntity>>

    @Query("SELECT * FROM produtos WHERE favorito = 1 ORDER BY nome")
    fun observarFavoritos(): Flow<List<ProdutoEntity>>

    @Query("SELECT * FROM produtos ORDER BY id")
    suspend fun listarTodos(): List<ProdutoEntity>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun porId(id: Long): ProdutoEntity?

    @Query("SELECT * FROM produtos WHERE id IN (:ids)")
    suspend fun porIds(ids: List<Long>): List<ProdutoEntity>

    @Query("SELECT * FROM produtos WHERE nomeNormalizado = :nomeNormalizado LIMIT 1")
    suspend fun porNomeNormalizado(nomeNormalizado: String): ProdutoEntity?

    @Query("SELECT * FROM produtos WHERE codigoBarras = :codigo LIMIT 1")
    suspend fun porCodigoBarras(codigo: String): ProdutoEntity?

    @Query(
        "SELECT * FROM produtos WHERE nomeNormalizado LIKE '%' || :termo || '%' " +
            "ORDER BY atualizadoEm DESC LIMIT :limite",
    )
    suspend fun buscar(termo: String, limite: Int = 20): List<ProdutoEntity>

    @Query("SELECT * FROM produtos WHERE infoNutricionalJson IS NOT NULL ORDER BY nome")
    fun observarComTabelaNutricional(): Flow<List<ProdutoEntity>>

    /**
     * Produtos com data de validade preenchida ate [limite] (inclusive),
     * ordenados do mais perto de vencer para o mais longe. Como a coluna guarda
     * epoch day, o limite vem de [Mapeadores.dataParaEpoch]; vencidos entram
     * tambem (dataValidade <= limite cobre o passado).
     */
    @Query(
        "SELECT * FROM produtos WHERE dataValidade IS NOT NULL AND dataValidade <= :limite " +
            "ORDER BY dataValidade ASC",
    )
    suspend fun porValidadeAte(limite: Long): List<ProdutoEntity>

    @Upsert
    suspend fun salvar(produto: ProdutoEntity): Long

    @Query("DELETE FROM produtos WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("SELECT COUNT(*) FROM produtos")
    suspend fun contar(): Int

    @Query("SELECT categoriaId AS categoriaId, COUNT(*) AS total FROM produtos GROUP BY categoriaId")
    suspend fun contarPorCategoria(): List<ContagemPorCategoria>
}

@Dao
interface ListaDao {
    @Query("SELECT * FROM listas ORDER BY finalizada, criadaEm DESC")
    fun observarTodas(): Flow<List<ListaEntity>>

    @Query("SELECT * FROM listas WHERE favorita = 1 ORDER BY criadaEm DESC")
    fun observarFavoritas(): Flow<List<ListaEntity>>

    @Query("SELECT * FROM listas WHERE id = :id")
    fun observar(id: Long): Flow<ListaEntity?>

    @Query("SELECT * FROM listas ORDER BY id")
    suspend fun listarTodas(): List<ListaEntity>

    @Query("SELECT * FROM listas WHERE id = :id")
    suspend fun porId(id: Long): ListaEntity?

    @Insert
    suspend fun inserir(lista: ListaEntity): Long

    @Update
    suspend fun atualizar(lista: ListaEntity)

    @Query("DELETE FROM listas WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("SELECT COUNT(*) FROM listas")
    suspend fun contar(): Int
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM itens WHERE listaId = :listaId ORDER BY ordemManual, id")
    fun observarDaLista(listaId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM itens WHERE listaId = :listaId ORDER BY ordemManual, id")
    suspend fun listarDaLista(listaId: Long): List<ItemEntity>

    @Query("SELECT * FROM itens")
    fun observarTodos(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM itens ORDER BY id")
    suspend fun listarTodos(): List<ItemEntity>

    @Query("SELECT * FROM itens WHERE id = :id")
    suspend fun porId(id: Long): ItemEntity?

    @Insert
    suspend fun inserir(item: ItemEntity): Long

    @Insert
    suspend fun inserirTodos(itens: List<ItemEntity>): List<Long>

    @Update
    suspend fun atualizar(item: ItemEntity)

    @Update
    suspend fun atualizarTodos(itens: List<ItemEntity>)

    @Delete
    suspend fun remover(item: ItemEntity)

    @Query("DELETE FROM itens WHERE listaId = :listaId")
    suspend fun removerDaLista(listaId: Long)

    @Query("UPDATE itens SET comprado = :comprado WHERE id = :id")
    suspend fun marcarComprado(id: Long, comprado: Boolean)

    @Query("UPDATE itens SET comprado = 0 WHERE listaId = :listaId")
    suspend fun desmarcarTodos(listaId: Long)

    @Query("SELECT COUNT(*) FROM itens WHERE listaId = :listaId")
    suspend fun contarDaLista(listaId: Long): Int
}

@Dao
interface EstabelecimentoDao {
    @Query("SELECT * FROM estabelecimentos ORDER BY nome")
    fun observarTodos(): Flow<List<EstabelecimentoEntity>>

    @Query("SELECT * FROM estabelecimentos ORDER BY nome")
    suspend fun listarTodos(): List<EstabelecimentoEntity>

    @Query("SELECT * FROM estabelecimentos WHERE nome = :nome LIMIT 1")
    suspend fun porNome(nome: String): EstabelecimentoEntity?

    @Query("SELECT * FROM estabelecimentos WHERE id = :id")
    suspend fun porId(id: Long): EstabelecimentoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(estabelecimento: EstabelecimentoEntity): Long

    @Update
    suspend fun atualizar(estabelecimento: EstabelecimentoEntity)

    @Query("DELETE FROM estabelecimentos WHERE id = :id")
    suspend fun remover(id: Long)
}

@Dao
interface PrecoDao {
    @Query(
        "SELECT p.* FROM precos p INNER JOIN itens i ON i.id = p.itemDaListaId WHERE i.listaId = :listaId",
    )
    fun observarDaLista(listaId: Long): Flow<List<PrecoEntity>>

    @Query(
        "SELECT p.* FROM precos p INNER JOIN itens i ON i.id = p.itemDaListaId WHERE i.listaId = :listaId",
    )
    suspend fun listarDaLista(listaId: Long): List<PrecoEntity>

    @Query("SELECT * FROM precos ORDER BY id")
    suspend fun listarTodos(): List<PrecoEntity>

    @Query("SELECT * FROM precos WHERE itemDaListaId = :itemId")
    suspend fun doItem(itemId: Long): List<PrecoEntity>

    @Query("SELECT * FROM precos WHERE itemDaListaId = :itemId AND estabelecimentoId = :lojaId LIMIT 1")
    suspend fun doItemNaLoja(itemId: Long, lojaId: Long): PrecoEntity?

    @Upsert
    suspend fun salvar(preco: PrecoEntity)

    @Query("DELETE FROM precos WHERE itemDaListaId = :itemId AND estabelecimentoId = :lojaId")
    suspend fun remover(itemId: Long, lojaId: Long)

    @Query("DELETE FROM precos WHERE estabelecimentoId = :lojaId")
    suspend fun removerDaLoja(lojaId: Long)

    @Insert
    suspend fun registrarHistorico(historico: HistoricoPrecoEntity)

    @Query(
        "SELECT * FROM historico_precos WHERE produtoId = :produtoId ORDER BY registradoEm DESC LIMIT :limite",
    )
    suspend fun historicoDoProduto(produtoId: Long, limite: Int = 20): List<HistoricoPrecoEntity>

    @Query(
        "SELECT MIN(precoCentavos) FROM historico_precos WHERE produtoId = :produtoId AND registradoEm >= :desde",
    )
    suspend fun menorPrecoRecente(produtoId: Long, desde: Long): Long?

    /**
     * Menor preco JA registrado para o produto em QUALQUER loja, sem janela de
     * tempo. Base do aviso "voce ja viu mais barato": o historico e por produto
     * (a coluna de loja serve de contexto), e o preco bom de uma rede em outra
     * unidade continua sendo referencia valida para quem compra de novo.
     */
    @Query("SELECT MIN(precoCentavos) FROM historico_precos WHERE produtoId = :produtoId")
    suspend fun menorPrecoRegistrado(produtoId: Long): Long?

    /**
     * Produtos com mais registros no historico, do mais frequente para o
     * menos; empate cai para o registro mais recente (MAX(registradoEm)).
     * Alimenta o cartao "Comprar de novo" da tela da lista.
     */
    @Query(
        "SELECT produtoId, COUNT(*) AS total FROM historico_precos " +
            "GROUP BY produtoId ORDER BY total DESC, MAX(registradoEm) DESC LIMIT :limite",
    )
    suspend fun produtosMaisRegistrados(limite: Int): List<ContagemPorProduto>

    /**
     * Historico global de precos com o nome do produto e da loja ja resolvidos
     * (join com produtos e estabelecimentos), do mais recente para o mais
     * antigo. E a fonte do CSV de precos: um registro por anotacao, com a
     * quantidade base do momento e a data - abre no Excel/Calc e permite
     * comparar "onde e quando esse item custou menos" fora do app.
     */
    @Query(
        "SELECT prd.nome AS nomeProduto, e.nome AS nomeLoja, h.precoCentavos AS precoCentavos, " +
            "h.quantidadeBase AS quantidadeBase, h.registradoEm AS registradoEm " +
            "FROM historico_precos h " +
            "INNER JOIN produtos prd ON prd.id = h.produtoId " +
            "INNER JOIN estabelecimentos e ON e.id = h.estabelecimentoId " +
            "ORDER BY h.registradoEm DESC",
    )
    suspend fun listarHistoricoDePrecos(): List<PrecoHistoricoComRotulo>
}

@Dao
interface CompraDao {
    @Query("SELECT * FROM compras ORDER BY data DESC")
    fun observarTodas(): Flow<List<CompraEntity>>

    @Query("SELECT * FROM compras WHERE id = :id")
    suspend fun porId(id: Long): CompraEntity?

    @Insert
    suspend fun inserir(compra: CompraEntity): Long

    @Query("DELETE FROM compras WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("SELECT * FROM compras ORDER BY data DESC")
    suspend fun listarTodas(): List<CompraEntity>
}

@Dao
interface MemoriaCategoriaDao {
    @Query("SELECT * FROM memoria_categorias")
    suspend fun listarTodas(): List<MemoriaCategoriaEntity>

    @Query("SELECT * FROM memoria_categorias")
    fun observarTodas(): Flow<List<MemoriaCategoriaEntity>>

    @Query("SELECT * FROM memoria_categorias WHERE nomeNormalizado = :nome LIMIT 1")
    suspend fun porNome(nome: String): MemoriaCategoriaEntity?

    @Upsert
    suspend fun salvar(memoria: MemoriaCategoriaEntity)

    @Query("DELETE FROM memoria_categorias")
    suspend fun limpar()
}

@Dao
interface ConfiguracoesDao {
    @Query("SELECT * FROM configuracoes WHERE id = 1")
    fun observar(): Flow<ConfiguracoesEntity?>

    @Query("SELECT * FROM configuracoes WHERE id = 1")
    suspend fun carregar(): ConfiguracoesEntity?

    @Upsert
    suspend fun salvar(configuracoes: ConfiguracoesEntity)

    @Query("SELECT * FROM perfil_restricoes WHERE id = 1")
    fun observarPerfil(): Flow<PerfilRestricoesEntity?>

    @Query("SELECT * FROM perfil_restricoes WHERE id = 1")
    suspend fun carregarPerfil(): PerfilRestricoesEntity?

    @Upsert
    suspend fun salvarPerfil(perfil: PerfilRestricoesEntity)
}

/** Alergias e restricoes criadas pelo proprio usuario (fora da lista RDC 26/2015). */
@Dao
interface AlergiaCustomizadaDao {
    @Query("SELECT * FROM alergias_customizadas ORDER BY nome")
    fun observarTodas(): Flow<List<AlergiaCustomizadaEntity>>

    @Query("SELECT * FROM alergias_customizadas ORDER BY nome")
    suspend fun listarTodas(): List<AlergiaCustomizadaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(alergia: AlergiaCustomizadaEntity): Long

    @Query("DELETE FROM alergias_customizadas WHERE id = :id")
    suspend fun remover(id: Long)
}

/** Operacoes que tocam varias tabelas de uma vez (backup e restauracao). */
@Dao
interface DecisaoDoParserDao {
    @Query("SELECT * FROM decisoes_parser")
    suspend fun listarTodas(): List<DecisaoDoParserEntity>

    @Query("SELECT * FROM decisoes_parser")
    fun observarTodas(): Flow<List<DecisaoDoParserEntity>>

    @Query("SELECT * FROM decisoes_parser WHERE termo = :termo LIMIT 1")
    suspend fun porTermo(termo: String): DecisaoDoParserEntity?

    @Upsert
    suspend fun salvar(decisao: DecisaoDoParserEntity)

    @Query("DELETE FROM decisoes_parser WHERE termo = :termo")
    suspend fun remover(termo: String)

    @Query("DELETE FROM decisoes_parser")
    suspend fun limpar()
}

@Dao
interface ManutencaoDao {
    @Transaction
    @Query("DELETE FROM listas")
    suspend fun apagarListas()

    @Query("DELETE FROM itens")
    suspend fun apagarItens()

    @Query("DELETE FROM precos")
    suspend fun apagarPrecos()

    @Query("DELETE FROM produtos")
    suspend fun apagarProdutos()

    @Query("DELETE FROM compras")
    suspend fun apagarCompras()

    @Query("DELETE FROM estabelecimentos")
    suspend fun apagarEstabelecimentos()

    @Query("DELETE FROM historico_precos")
    suspend fun apagarHistoricoPrecos()

    @Query("DELETE FROM categorias WHERE origem = 'USUARIO'")
    suspend fun apagarCategoriasDoUsuario()
}

/**
 * Lixeira: registros excluidos que aguardam restauracao ou a purga permanente
 * de 30 dias (ver `LixeiraRepositorio`). Leitura cruza os tipos; a UI filtra.
 */
@Dao
interface LixeiraDao {

    @Query("SELECT * FROM lixeira ORDER BY enviadaEm DESC")
    fun observarTodos(): Flow<List<RegistroDeLixeiraEntity>>

    @Query("SELECT * FROM lixeira WHERE tipo = :tipo ORDER BY enviadaEm DESC")
    fun observarPorTipo(tipo: String): Flow<List<RegistroDeLixeiraEntity>>

    @Query("SELECT * FROM lixeira")
    suspend fun listarTodas(): List<RegistroDeLixeiraEntity>

    @Query("SELECT * FROM lixeira WHERE id = :id")
    suspend fun porId(id: Long): RegistroDeLixeiraEntity?

    @Query("SELECT COUNT(*) FROM lixeira")
    suspend fun contar(): Int

    @Insert
    suspend fun inserir(registro: RegistroDeLixeiraEntity): Long

    @Query("DELETE FROM lixeira WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("DELETE FROM lixeira WHERE tipo = :tipo")
    suspend fun removerPorTipo(tipo: String): Int

    @Query("DELETE FROM lixeira")
    suspend fun removerTudo(): Int

    /** Purga permanente dos registros enviados ate [momento] (inclusive). */
    @Query("DELETE FROM lixeira WHERE enviadaEm <= :momento")
    suspend fun removerEnviadosAntesDe(momento: Long): Int
}

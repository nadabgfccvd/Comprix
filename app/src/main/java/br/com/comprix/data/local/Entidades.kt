package br.com.comprix.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabelas do banco local.
 *
 * ## Convencoes de tipo (sem TypeConverters, de proposito)
 * | Dado            | Como e guardado                         | Por que |
 * |-----------------|-----------------------------------------|---------|
 * | dinheiro        | `Long` em centavos                      | exato, ordenavel e indexavel pelo SQLite |
 * | quantidade      | `String` do BigDecimal                  | preserva "0,5" e "1,500" sem erro binario |
 * | data/hora       | `Long` epoch millis (UTC)               | comparavel e imune a fuso |
 * | enum            | `String` com o `name()`                 | legivel no backup e estavel entre versoes |
 * | lista pequena   | `String` separada por `\|`              | evita tabela extra para 3 itens |
 * | tabela nutric.  | `String` JSON plano                     | ver `util/JsonSimples` |
 *
 * TypeConverters globais fariam o mesmo trabalho escondendo o custo; aqui a
 * conversao e explicita em `Mapeadores.kt` e e facil de auditar.
 *
 * ## Politica de migracao
 * A versao 1 e a inicial. Toda mudanca de esquema deve vir com uma
 * `Migration` escrita a mao em [ComprixDatabase] - o app NUNCA usa
 * `fallbackToDestructiveMigration`, porque os dados do usuario (historico de
 * precos de meses) sao insubstituiveis e nao existe copia na nuvem.
 */

@Entity(tableName = "categorias", indices = [Index(value = ["chave"])])
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val icone: String,
    val ordemPadrao: Int,
    val origem: String,
    val chave: String,
)

@Entity(
    tableName = "produtos",
    indices = [
        Index(value = ["nomeNormalizado"]),
        Index(value = ["codigoBarras"]),
        Index(value = ["categoriaId"]),
    ],
)
data class ProdutoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val nomeNormalizado: String,
    val categoriaId: Long,
    val codigoBarras: String?,
    val unidadePadrao: String,
    /** JSON plano: {"porcao":"30 g","energia":"120",...} */
    val infoNutricionalJson: String?,
    /** "ACUCAR_ADICIONADO|SODIO" */
    val selosAltoEm: String,
    val ingredientes: String?,
    val gluten: String,
    /** "LEITE|SOJA" */
    val alergenos: String,
    val dataValidade: Long?,
    val dataFabricacao: Long?,
    val pesoMedioEstimadoEmBase: String?,
    val atualizadoEm: Long,
    /** Produto marcado como favorito pelo usuario (atalho para adicionar a listas). */
    val favorito: Boolean = false,
)

/** Projecao de `SELECT categoriaId, COUNT(*)` - so para a tela de categorias. */
data class ContagemPorCategoria(val categoriaId: Long, val total: Int)

/**
 * Projecao de `SELECT produtoId, COUNT(*)` do historico de precos - alimenta
 * o cartao "Comprar de novo" da tela da lista (produto mais anotado primeiro).
 */
data class ContagemPorProduto(val produtoId: Long, val total: Int)

@Entity(tableName = "listas")
data class ListaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val criadaEm: Long,
    val finalizada: Boolean,
    val finalizadaEm: Long?,
    /** Lista marcada como favorita pelo usuario (secao propria na tela de listas). */
    val favorita: Boolean = false,
    /** Orcamento da lista em centavos (null = sem orcamento); alimenta o alerta de estouro. */
    val orcamentoCentavos: Long? = null,
)

/**
 * Registro enviado para a Lixeira. O payload e JSON plano do [JsonSimples]
 * com os dados minimos para reconstruir o original; a purga permanente acontece
 * 30 dias depois de [enviadaEm] (ver `LixeiraRepositorio.PRAZO_EM_DIAS`).
 */
@Entity(
    tableName = "lixeira",
    indices = [Index(value = ["tipo"]), Index(value = ["enviadaEm"])],
)
data class RegistroDeLixeiraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nome do tipo (use as constantes de `LixeiraRepositorio.TipoDeLixeira`). */
    val tipo: String,
    val titulo: String,
    val detalhe: String?,
    /** Id original do registro quando existia (0 quando desconhecido). */
    val refId: Long,
    val payload: String,
    val enviadaEm: Long,
)

@Entity(
    tableName = "itens",
    foreignKeys = [
        ForeignKey(
            entity = ListaEntity::class,
            parentColumns = ["id"],
            childColumns = ["listaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["listaId"]), Index(value = ["produtoId"])],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listaId: Long,
    val produtoId: Long,
    val quantidade: String,
    val unidade: String,
    val pesoOuVolume: String?,
    val ehKit: Boolean,
    val itensPorKit: Int?,
    val comprado: Boolean,
    val ordemManual: Int,
    val modoComparacao: String,
    val observacao: String?,
)

@Entity(tableName = "estabelecimentos", indices = [Index(value = ["nome"], unique = true)])
data class EstabelecimentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val corHex: String,
)

@Entity(
    tableName = "precos",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemDaListaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["itemDaListaId", "estabelecimentoId"], unique = true), Index(value = ["estabelecimentoId"])],
)
data class PrecoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemDaListaId: Long,
    val estabelecimentoId: Long,
    /** Centavos. */
    val precoCentavos: Long,
    val dataRegistro: Long,
    val disponivel: Boolean,
)

/** Historico de precos por produto - sobrevive ao apagar a lista. */
@Entity(tableName = "historico_precos", indices = [Index(value = ["produtoId"]), Index(value = ["registradoEm"])])
data class HistoricoPrecoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long,
    val estabelecimentoId: Long,
    val precoCentavos: Long,
    val quantidadeBase: String,
    val registradoEm: Long,
)

@Entity(tableName = "compras", indices = [Index(value = ["data"])])
data class CompraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listaId: Long?,
    val nomeLista: String,
    val data: Long,
    val totalPagoCentavos: Long,
    val economiaCentavos: Long,
    val quantidadeItens: Int,
    val descricaoEstabelecimento: String,
    val estabelecimentoPrincipalId: Long?,
    /** JSON plano: {"Hortifruti":"3450"} em centavos. */
    val gastosPorCategoriaJson: String,
)

/** Aprendizado da categorizacao: nome normalizado -> categoria escolhida pelo usuario. */
@Entity(tableName = "memoria_categorias")
data class MemoriaCategoriaEntity(
    @PrimaryKey val nomeNormalizado: String,
    val categoriaId: Long,
    val vezes: Int,
    val atualizadoEm: Long,
)

/**
 * Aprendizado do parser de texto livre: o que o usuario respondeu nas sugestoes
 * inline (Secao 9.3 do documento do parser).
 *
 * E a **mesma ideia** de [MemoriaCategoriaEntity] aplicada a quantidade - nao
 * um segundo mecanismo de aprendizado: termo normalizado, decisao e quantas
 * vezes ela se repetiu. Fica so no aparelho, sai no backup e pode ser apagada.
 */
@Entity(tableName = "decisoes_parser")
data class DecisaoDoParserEntity(
    @PrimaryKey val termo: String,
    /** "REJEITADO" (manter como esta) ou "CONFIRMADO" (aplicar). */
    val decisao: String,
    /** Valor confirmado, em texto (BigDecimal); null quando rejeitado. */
    val valor: String?,
    val vezes: Int,
    val atualizadoEm: Long,
)

/** Linha unica (id = 1) com as preferencias do app. */
@Entity(tableName = "configuracoes")
data class ConfiguracoesEntity(
    @PrimaryKey val id: Int = 1,
    val tema: String,
    val altoContraste: Boolean,
    val escalaDaFonte: Int,
    val coresDinamicas: Boolean,
    val sons: Boolean,
    val vibracao: Boolean,
    val modoTecnico: Boolean,
    val onboardingConcluido: Boolean,
    /** "VALOR_ENERGETICO|SODIO" */
    val nutrientesComparados: String,
    /** "DICA_LISTA|DICA_SCANNER" */
    val dicasVistas: String,
    val ultimoBackupMs: Long,
    /** Meta de economia mensal em centavos (null = sem meta); aparece nas Analíticas. */
    val metaEconomiaCentavos: Long? = null,
)

/** Linha unica (id = 1) com o perfil de restricoes alimentares. */
@Entity(tableName = "perfil_restricoes")
data class PerfilRestricoesEntity(
    @PrimaryKey val id: Int = 1,
    /** "LEITE|AMENDOIM" */
    val alergenos: String,
    val semGluten: Boolean,
)

/** Alergia ou restricao criada pelo usuario, fora da lista oficial RDC 26/2015. */
@Entity(tableName = "alergias_customizadas")
data class AlergiaCustomizadaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val palavras: String, // separado por "|", como em PerfilRestricoesEntity.alergenos
)

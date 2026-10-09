package br.com.comprix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Banco local do Comprix. Unico ponto de verdade do app: nao existe servidor,
 * cache remoto nem sincronizacao.
 *
 * ## Politica de migracao
 * `fallbackToDestructiveMigration` esta **proibido** neste projeto. O usuario
 * acumula meses de precos e historico, e nao ha backup automatico na nuvem para
 * socorre-lo. Qualquer alteracao de esquema precisa de uma [androidx.room.migration.Migration]
 * escrita a mao e registrada em [MIGRACOES]. O arquivo `.cbk` (exportacao
 * manual) e a rede de seguranca do usuario, nao a politica de migracao.
 */
@Database(
    entities = [
        CategoriaEntity::class,
        ProdutoEntity::class,
        ListaEntity::class,
        ItemEntity::class,
        EstabelecimentoEntity::class,
        PrecoEntity::class,
        HistoricoPrecoEntity::class,
        CompraEntity::class,
        MemoriaCategoriaEntity::class,
        DecisaoDoParserEntity::class,
        ConfiguracoesEntity::class,
        PerfilRestricoesEntity::class,
        AlergiaCustomizadaEntity::class,
        RegistroDeLixeiraEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class ComprixDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun produtoDao(): ProdutoDao
    abstract fun listaDao(): ListaDao
    abstract fun itemDao(): ItemDao
    abstract fun estabelecimentoDao(): EstabelecimentoDao
    abstract fun precoDao(): PrecoDao
    abstract fun compraDao(): CompraDao
    abstract fun memoriaCategoriaDao(): MemoriaCategoriaDao
    abstract fun decisaoDoParserDao(): DecisaoDoParserDao
    abstract fun configuracoesDao(): ConfiguracoesDao
    abstract fun alergiaCustomizadaDao(): AlergiaCustomizadaDao
    abstract fun manutencaoDao(): ManutencaoDao
    abstract fun lixeiraDao(): LixeiraDao

    companion object {
        private const val NOME_DO_ARQUIVO = "comprix.db"

        /**
         * Migracoes escritas a mao - uma por salto de versao, sem excecao.
         *
         * **1 -> 2:** entra `decisoes_parser`, o aprendizado do parser de texto
         * livre. Tabela nova e aditiva: nada e reescrito, nada e perdido.
         */
        private val MIGRACAO_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decisoes_parser` (
                        `termo` TEXT NOT NULL,
                        `decisao` TEXT NOT NULL,
                        `valor` TEXT,
                        `vezes` INTEGER NOT NULL,
                        `atualizadoEm` INTEGER NOT NULL,
                        PRIMARY KEY(`termo`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * **2 -> 3:** entra `configuracoes.escalaDaFonte`, a escala tipografica
         * de 100 a 160 por cento pedida pela referencia visual. Coluna nova com
         * `DEFAULT 100`: quem ja tinha banco continua no tamanho atual.
         */
        private val MIGRACAO_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `configuracoes` ADD COLUMN `escalaDaFonte` INTEGER NOT NULL DEFAULT 100",
                )
            }
        }

        /**
         * **3 -> 4:** entra `alergias_customizadas`, as restricoes alimentares
         * criadas pelo proprio usuario (fora da lista oficial RDC 26/2015).
         * Tabela nova e aditiva: nada e reescrito, nada e perdido.
         */
        private val MIGRACAO_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alergias_customizadas` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nome` TEXT NOT NULL,
                        `palavras` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * **4 -> 5:** entram favoritos (listas e produtos) e a Lixeira com
         * purga automatica de 30 dias. Tudo aditivo: colunas novas com DEFAULT
         * e tabela nova - nada e reescrito, nada e perdido.
         */
        private val MIGRACAO_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `listas` ADD COLUMN `favorita` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `listas` ADD COLUMN `orcamentoCentavos` INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE `produtos` ADD COLUMN `favorito` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `configuracoes` ADD COLUMN `metaEconomiaCentavos` INTEGER",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lixeira` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `titulo` TEXT NOT NULL,
                        `detalhe` TEXT,
                        `refId` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        `enviadaEm` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lixeira_tipo` ON `lixeira` (`tipo`)" )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lixeira_enviadaEm` ON `lixeira` (`enviadaEm`)" )
            }
        }

        private val MIGRACOES = arrayOf<androidx.room.migration.Migration>(
            MIGRACAO_1_2,
            MIGRACAO_2_3,
            MIGRACAO_3_4,
            MIGRACAO_4_5,
        )

        @Volatile
        private var instancia: ComprixDatabase? = null

        fun obter(contexto: Context): ComprixDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construir(contexto.applicationContext).also { instancia = it }
            }

        private fun construir(contexto: Context): ComprixDatabase =
            Room.databaseBuilder(contexto, ComprixDatabase::class.java, NOME_DO_ARQUIVO)
                .addMigrations(*MIGRACOES)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // As 14 categorias padrao entram ja na criacao do arquivo:
                        // o app nunca abre vazio, nem na primeira execucao offline.
                        DadosIniciais.semear(db)
                    }
                })
                .build()
    }
}

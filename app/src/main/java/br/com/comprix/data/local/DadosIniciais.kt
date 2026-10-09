package br.com.comprix.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.comprix.domain.modelo.Categoria
import br.com.comprix.domain.modelo.OrigemCategoria

/**
 * Semente do banco: as 14 categorias padrao, na ordem do caminho fisico de um
 * supermercado brasileiro (entrada -> fundo -> frente de caixa).
 *
 * A [Categoria.chave] e o elo com o dicionario de palavras-chave da
 * categorizacao automatica; o [Categoria.icone] e um nome logico resolvido na
 * camada de interface (nenhum recurso de Android chega aqui).
 */
object DadosIniciais {

    val categorias: List<Categoria> = listOf(
        Categoria(nome = "Hortifrúti", icone = "hortifruti", ordemPadrao = 10, chave = "hortifruti"),
        Categoria(nome = "Padaria", icone = "padaria", ordemPadrao = 20, chave = "padaria"),
        Categoria(nome = "Açougue e peixaria", icone = "acougue", ordemPadrao = 30, chave = "acougue"),
        Categoria(nome = "Frios e laticínios", icone = "laticinios", ordemPadrao = 40, chave = "laticinios"),
        Categoria(nome = "Congelados", icone = "congelados", ordemPadrao = 50, chave = "congelados"),
        Categoria(nome = "Mercearia", icone = "mercearia", ordemPadrao = 60, chave = "mercearia"),
        Categoria(nome = "Orgânicos", icone = "organicos", ordemPadrao = 65, chave = "organicos"),
        Categoria(nome = "Bebidas", icone = "bebidas", ordemPadrao = 70, chave = "bebidas"),
        Categoria(nome = "Limpeza", icone = "limpeza", ordemPadrao = 80, chave = "limpeza"),
        Categoria(nome = "Higiene e beleza", icone = "higiene", ordemPadrao = 90, chave = "higiene"),
        Categoria(nome = "Infantil", icone = "infantil", ordemPadrao = 100, chave = "infantil"),
        Categoria(nome = "Pet", icone = "pet", ordemPadrao = 110, chave = "pet"),
        Categoria(nome = "Bazar e utilidades", icone = "bazar", ordemPadrao = 120, chave = "bazar"),
        Categoria(nome = "Outros", icone = "outros", ordemPadrao = 999, chave = "outros"),
    )

    /**
     * Insere as categorias direto no SQLite, dentro do `onCreate` do Room
     * (nao da para usar os DAOs ali: o banco ainda esta sendo aberto).
     */
    fun semear(db: SupportSQLiteDatabase) {
        categorias.forEach { categoria ->
            db.execSQL(
                "INSERT INTO categorias (nome, icone, ordemPadrao, origem, chave) VALUES (?, ?, ?, ?, ?)",
                arrayOf(
                    categoria.nome,
                    categoria.icone,
                    categoria.ordemPadrao,
                    OrigemCategoria.PADRAO.name,
                    categoria.chave,
                ),
            )
        }
    }

    /** Versao para DAO - usada ao restaurar um backup num banco ja existente. */
    fun entidades(): List<CategoriaEntity> = categorias.map { categoria ->
        CategoriaEntity(
            nome = categoria.nome,
            icone = categoria.icone,
            ordemPadrao = categoria.ordemPadrao,
            origem = OrigemCategoria.PADRAO.name,
            chave = categoria.chave,
        )
    }

    /** Nome da loja criada sozinha quando o usuario registra o primeiro preco. */
    const val ESTABELECIMENTO_PADRAO = "Meu mercado"

    /**
     * Paleta para diferenciar lojas na matriz.
     *
     * Todos os tons foram escolhidos com contraste >= 4.5:1 contra texto branco,
     * para que o nome da loja seja legivel dentro do chip colorido (o ambar puro
     * #F4B400 ficou de fora por esse motivo: 1,85:1 com branco).
     */
    val CORES_DE_LOJA = listOf(
        "#1C8454", "#A05E00", "#1565C0", "#8E24AA", "#C62828", "#00796B", "#5D4037", "#455A64",
    )
}

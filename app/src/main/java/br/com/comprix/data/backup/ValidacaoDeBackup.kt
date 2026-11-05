package br.com.comprix.data.backup

/** Tabelas presentes desde o formato v1; tabelas adicionadas depois são opcionais. */
internal fun backupPossuiTabelasEssenciais(tabelas: Set<String>): Boolean =
    setOf("categorias", "produtos", "estabelecimentos", "listas", "itens", "precos", "compras")
        .all { it in tabelas }

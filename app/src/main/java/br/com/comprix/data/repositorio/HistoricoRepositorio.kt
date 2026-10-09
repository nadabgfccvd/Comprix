package br.com.comprix.data.repositorio

import br.com.comprix.data.local.CompraDao
import br.com.comprix.data.local.Mapeadores
import br.com.comprix.domain.compra.AnalisadorDeHistorico
import br.com.comprix.domain.compra.CalculadoraDeCompra
import br.com.comprix.domain.modelo.CompraFinalizada
import br.com.comprix.domain.modelo.ResumoHistorico
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/** Historico de compras finalizadas e os numeros dos graficos. */
class HistoricoRepositorio(private val compraDao: CompraDao, private val lixeira: LixeiraRepositorio? = null) {

    val compras: Flow<List<CompraFinalizada>> =
        compraDao.observarTodas().map { lista -> lista.map(Mapeadores::paraDominio) }

    fun resumo(periodo: AnalisadorDeHistorico.Periodo): Flow<ResumoHistorico> =
        compras.map { AnalisadorDeHistorico.resumir(it, periodo) }

    suspend fun listarCompras(): List<CompraFinalizada> = compraDao.listarTodas().map(Mapeadores::paraDominio)

    suspend fun compra(id: Long): CompraFinalizada? = compraDao.porId(id)?.let(Mapeadores::paraDominio)

    /** Grava o fechamento de uma lista no historico. */
    suspend fun registrar(
        listaId: Long?,
        nomeLista: String,
        resultado: CalculadoraDeCompra.ResultadoDaCompra,
        momento: LocalDateTime = LocalDateTime.now(),
    ): Long = compraDao.inserir(
        Mapeadores.paraEntidade(
            CompraFinalizada(
                listaId = listaId,
                nomeLista = nomeLista,
                data = momento,
                totalPago = resultado.totalPago,
                economia = resultado.economia,
                quantidadeItens = resultado.quantidadeItens,
                descricaoEstabelecimento = resultado.descricaoEstabelecimento,
                estabelecimentoPrincipalId = resultado.estabelecimentoPrincipalId,
                gastosPorCategoria = resultado.gastosPorCategoria.associate { it.rotulo to it.valor },
            ),
        ),
    )

    /** Exclusão com passagem pela Lixeira (recuperável por 30 dias). */
    suspend fun remover(id: Long) {
        if (lixeira != null) {
            lixeira.enviarCompra(id)
            return
        }
        compraDao.remover(id)
    }
}

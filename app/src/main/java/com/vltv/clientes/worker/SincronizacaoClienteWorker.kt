package com.vltv.clientes.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.network.BuscaClienteResultado
import com.vltv.clientes.network.XtreamCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// Sincroniza UM único cliente logo depois que ele é salvo (cadastro novo ou
// edição), em segundo plano, sem travar a tela de cadastro. Se não tiver
// internet ou a VPS estiver fora do ar nesse instante, o cliente já ficou
// salvo normalmente - esse worker só tenta de novo sozinho (via WorkManager,
// com a constraint de rede conectada + backoff) assim que a conexão voltar.
// Se mesmo assim continuar falhando, a checagem diária e o botão
// "Sincronizar" da tela principal também pegam esse cliente pendente.
class SincronizacaoClienteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val clienteId = inputData.getLong(KEY_CLIENTE_ID, -1L)
        if (clienteId == -1L) return@withContext Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.clienteDao()
        val cliente = dao.buscarPorId(clienteId) ?: return@withContext Result.success()

        try {
            val resultado = if (cliente.dns.isBlank()) {
                // Cliente novo (ou credenciais trocadas) - ainda não sabemos
                // em qual servidor ele está, então testa todos.
                XtreamCheck.buscarCliente(applicationContext, cliente.usuario, cliente.senha)
            } else {
                XtreamCheck.reconsultar(applicationContext, cliente.dns, cliente.usuario, cliente.senha)
            }

            when (resultado) {
                is BuscaClienteResultado.Sucesso -> {
                    val r = resultado.resultado
                    dao.atualizar(
                        cliente.copy(
                            dns = r.dns,
                            expDateUnix = r.expDateUnix,
                            diasRestantes = r.diasRestantes,
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = null
                        )
                    )
                }
                is BuscaClienteResultado.CredenciaisInvalidas -> {
                    dao.atualizar(
                        cliente.copy(
                            diasRestantes = cliente.diasRestantes ?: DIAS_SENTINELA_VENCIDO_SEM_DATA,
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = "Usuário/senha não encontrados em nenhum servidor"
                        )
                    )
                }
                is BuscaClienteResultado.Erro -> {
                    // Não encontrado em nenhum servidor quase sempre é conta
                    // desativada/vencida (não erro de rede) - trata como
                    // Vencido sem data exata, igual ao VerificacaoVencimentoWorker.
                    dao.atualizar(
                        cliente.copy(
                            diasRestantes = cliente.diasRestantes ?: DIAS_SENTINELA_VENCIDO_SEM_DATA,
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = resultado.mensagem
                        )
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            // Sem internet ou VPS fora do ar agora - não marca como erro
            // definitivo, só deixa como "Pendente" e deixa o WorkManager
            // tentar de novo sozinho mais tarde.
            Result.retry()
        }
    }

    companion object {
        private const val KEY_CLIENTE_ID = "cliente_id"

        // Mesmo valor-sentinela usado no VerificacaoVencimentoWorker: marca
        // "Vencido, mas sem data exata conhecida" sem precisar de campo novo
        // no banco.
        private const val DIAS_SENTINELA_VENCIDO_SEM_DATA = -1

        fun sincronizar(context: Context, clienteId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val dados = Data.Builder().putLong(KEY_CLIENTE_ID, clienteId).build()

            val request = OneTimeWorkRequestBuilder<SincronizacaoClienteWorker>()
                .setConstraints(constraints)
                .setInputData(dados)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

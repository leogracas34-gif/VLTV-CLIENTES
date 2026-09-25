package com.vltv.clientes.worker

import android.content.Context
import androidx.work.*
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaClienteResultado
import com.vltv.clientes.network.XtreamCheck
import com.vltv.clientes.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Roda uma vez por dia, de manhã (mais uma checagem extra sempre que o app é
// aberto - ver MainActivity, e sob demanda pelo botão "Sincronizar") e faz,
// pra cada cliente ativo:
//  1. Se o cliente ainda não tem DNS (pendente, acabou de ser salvo sem
//     conexão), testa todos os servidores; senão reconsulta usando o DNS já
//     conhecido primeiro, com fallback pra lista inteira se não responder.
//  2. Atualiza o cache local (dias restantes etc.)
//  3. Se bater 3/2/1 dia ou tiver acabado de vencer, e ainda não avisou
//     hoje: dispara notificação nativa do Android + manda mensagem pro
//     cliente via backend (fila do WhatsApp)
class VerificacaoVencimentoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.clienteDao()
        val clientes = dao.listarAtivos()

        for (cliente in clientes) {
            try {
                val resultado = if (cliente.dns.isBlank()) {
                    XtreamCheck.buscarCliente(applicationContext, cliente.usuario, cliente.senha)
                } else {
                    XtreamCheck.reconsultar(applicationContext, cliente.dns, cliente.usuario, cliente.senha)
                }

                when (resultado) {
                    is BuscaClienteResultado.Sucesso -> {
                        val r = resultado.resultado
                        val atualizado = cliente.copy(
                            dns = r.dns,
                            expDateUnix = r.expDateUnix,
                            diasRestantes = r.diasRestantes,
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = null
                        )
                        dao.atualizar(atualizado)

                        val dias = r.diasRestantes
                        if (dias != null) {
                            val precisaAvisar = (dias in 1..3 || dias < 0) && atualizado.ultimoAvisoDias != dias
                            if (precisaAvisar) {
                                NotificationHelper.notificarVencimento(applicationContext, cliente.id, cliente.nome, dias)

                                val mensagem = AppConfig.montarMensagemPara(applicationContext, dias, cliente.nome)
                                if (mensagem != null) {
                                    BackendApi.enviarMensagem(applicationContext, cliente.whatsapp, mensagem)
                                }

                                dao.atualizar(atualizado.copy(ultimoAvisoDias = dias))
                            }
                        }
                    }
                    is BuscaClienteResultado.CredenciaisInvalidas -> {
                        dao.atualizar(cliente.copy(
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = "Usuário/senha não encontrados em nenhum servidor"
                        ))
                    }
                    is BuscaClienteResultado.Erro -> {
                        dao.atualizar(cliente.copy(
                            ultimaChecagemEm = System.currentTimeMillis(),
                            ultimoErro = resultado.mensagem
                        ))
                    }
                }
            } catch (e: Exception) {
                // ✅ CORREÇÃO: antes esse catch era vazio — qualquer exceção
                // fora do tratamento normal (ex.: dentro do XtreamCheck)
                // fazia o worker terminar "com sucesso" sem NUNCA gravar
                // nada nesse cliente. dns continuava "" e ultimoErro
                // continuava null pra sempre, e o card ficava preso em
                // "Pendente" mesmo depois de "Sincronização concluída".
                // Agora o motivo real fica registrado e aparece na tela.
                try {
                    dao.atualizar(cliente.copy(
                        ultimaChecagemEm = System.currentTimeMillis(),
                        ultimoErro = "${e.javaClass.simpleName}: ${e.message ?: "erro desconhecido ao sincronizar"}"
                    ))
                } catch (e2: Exception) {
                    // Se nem isso conseguir gravar, aí sim desiste desse cliente.
                }
            }
        }

        Result.success()
    }

    companion object {
        private const val WORK_NAME_PERIODICA = "verificacao_vencimento_diaria"

        // Horário-alvo do disparo diário (hora local do aparelho). Ajuste
        // aqui se quiser outro horário - ex.: HORA_ALVO = 9 pra rodar 9h.
        private const val HORA_ALVO = 8
        private const val MINUTO_ALVO = 0

        // Nome único usado pela sincronização manual (botão "Sincronizar" e
        // pull-to-refresh da tela principal) - a MainActivity observa esse
        // nome pra mostrar o spinner e o aviso de "concluído".
        const val WORK_NAME_MANUAL = "verificacao_vencimento_manual"

        // ✅ NOVO: calcula quanto tempo falta (em ms) até a próxima ocorrência
        // do horário-alvo (hoje, se ainda não passou; amanhã, se já passou).
        // É esse valor que vira o "atraso inicial" do trabalho periódico, pra
        // garantir que a primeira execução (e, por consequência, todas as
        // seguintes, já que são 24h em 24h a partir daí) caia sempre de manhã.
        private fun calcularAtrasoAteProximoHorarioAlvo(): Long {
            val agora = Calendar.getInstance()
            val alvo = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, HORA_ALVO)
                set(Calendar.MINUTE, MINUTO_ALVO)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (alvo.timeInMillis <= agora.timeInMillis) {
                alvo.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alvo.timeInMillis - agora.timeInMillis
        }

        fun agendar(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val atrasoInicial = calcularAtrasoAteProximoHorarioAlvo()

            val request = PeriodicWorkRequestBuilder<VerificacaoVencimentoWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(atrasoInicial, TimeUnit.MILLISECONDS)
                .build()

            // ExistingPeriodicWorkPolicy.KEEP: se já existir um agendamento
            // (ex.: em versões anteriores do app, sem esse horário fixo),
            // ele é mantido como está - não reagenda a cada abertura do app.
            // Se quiser forçar a migração pro novo horário numa atualização,
            // troque KEEP por UPDATE uma única vez e depois volte pra KEEP.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODICA,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        // Dispara uma checagem imediata (ex: assim que o app abre), sem
        // esperar o ciclo de 24h - roda em paralelo ao trabalho agendado.
        fun executarAgora(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<VerificacaoVencimentoWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        // Sincronização manual disparada pelo botão "Sincronizar" (ou pelo
        // pull-to-refresh) da tela principal - usa nome único com política
        // KEEP pra não empilhar várias execuções se o usuário tocar o botão
        // de novo enquanto uma sincronização anterior ainda está rodando.
        fun executarManual(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<VerificacaoVencimentoWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_MANUAL,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

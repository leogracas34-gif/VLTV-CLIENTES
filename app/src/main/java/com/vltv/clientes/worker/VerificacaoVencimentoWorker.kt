package com.vltv.clientes.worker

import android.content.Context
import androidx.work.*
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteDao
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaClienteResultado
import com.vltv.clientes.network.EnvioResultado
import com.vltv.clientes.network.XtreamCheck
import com.vltv.clientes.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Roda a cada 1 hora (mais uma checagem extra sempre que o app é aberto -
// ver MainActivity, e sob demanda pelo botão "Sincronizar") e faz, pra cada
// cliente ativo:
//  1. Se o cliente ainda não tem DNS (pendente, acabou de ser salvo sem
//     conexão), testa todos os servidores; senão reconsulta usando o DNS já
//     conhecido primeiro, com fallback pra lista inteira se não responder.
//  2. Atualiza o cache local (dias restantes etc.) - isso roda em toda
//     execução (a cada 1h), pra refletir rápido qualquer alteração de
//     vencimento/login feita manualmente no servidor.
//  3. Se bater 3/2/1 dia ou tiver acabado de vencer, ainda não avisou hoje
//     E o horário atual está na janela das 9h-10h: dispara notificação
//     nativa do Android + manda mensagem pro cliente via backend (fila do
//     WhatsApp). O aviso em si continua sendo só 1x por dia, mesmo rodando
//     a checagem várias vezes.
class VerificacaoVencimentoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.clienteDao()
        val clientes = dao.listarAtivos()

        // Quando disparado pelo botão "Enviar avisos agora" (forcarEnvio=true
        // nos inputData), ignora a janela de horário 9h-11h e manda pra
        // qualquer cliente que ainda não recebeu aviso hoje pro valor atual
        // de dias - útil quando o ciclo automático falhou silenciosamente ou
        // o usuário só quer confirmar/reenviar na hora.
        val forcarEnvio = inputData.getBoolean(CHAVE_FORCAR_ENVIO, false)

        // ✅ ALTERADO: antes era um cliente de cada vez (for sequencial), então
        // o tempo total virava a SOMA do tempo de todo mundo - com vários
        // clientes, e principalmente os que demoram pra falhar (conta
        // indisponível em todos os servidores), a sincronização inteira
        // ficava lenta. Agora todos disparam ao mesmo tempo; o OkHttp já
        // enfileira as chamadas de rede sozinho (não sobrecarrega o
        // aparelho nem o servidor).
        coroutineScope {
            clientes.map { cliente ->
                async { processarCliente(cliente, dao, forcarEnvio) }
            }.awaitAll()
        }

        Result.success()
    }

    private suspend fun processarCliente(cliente: ClienteEntity, dao: ClienteDao, forcarEnvio: Boolean = false) {
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
                        // ✅ ALTERADO: "dias in 0..3" (era "1..3") - agora
                        // manda mensagem também no dia exato do vencimento
                        // ("vence hoje"), não só nos 3 dias anteriores. O
                        // "dias < 0" continua cobrindo tanto o vencido com
                        // data exata quanto o vencido sem data (sentinela
                        // -1, quando o servidor Xtream não retorna mais os
                        // dados da conta) - nenhuma mudança nesse caso.
                        //
                        // forcarEnvio (botão "Enviar avisos agora") pula a
                        // checagem de horário, mas mantém a checagem de
                        // "já avisado hoje" - não manda duas vezes se o
                        // ciclo automático já tiver mandado com sucesso.
                        val precisaAvisar = (dias in 0..3 || dias < 0) &&
                            atualizado.ultimoAvisoDias != dias &&
                            (forcarEnvio || estaNoHorarioDeAviso())
                        if (precisaAvisar) {
                            val mensagem = AppConfig.montarMensagemPara(applicationContext, dias, cliente.nome)
                            if (mensagem != null) {
                                // ✅ CORRIGIDO: bug real - antes o resultado do
                                // envio não era conferido, então se o envio
                                // pro backend falhasse (rede, bot reconectando
                                // etc.) o app marcava "já avisado" do mesmo
                                // jeito e NUNCA mais tentava reenviar pra esse
                                // cliente/dia - a notificação local aparecia
                                // (ela dispara antes, sem depender do envio),
                                // mas a mensagem de WhatsApp nunca saía de
                                // verdade. Agora só grava ultimoAvisoDias
                                // quando o backend confirma o recebimento; se
                                // falhar, tenta de novo na próxima execução
                                // (1h depois) ou quando o usuário forçar.
                                val resultado = BackendApi.enviarMensagem(applicationContext, cliente.whatsapp, mensagem)
                                if (resultado is EnvioResultado.Ok) {
                                    NotificationHelper.notificarVencimento(applicationContext, cliente.id, cliente.nome, dias)
                                    dao.atualizar(atualizado.copy(ultimoAvisoDias = dias))
                                } else if (resultado is EnvioResultado.Falha) {
                                    dao.atualizar(atualizado.copy(
                                        ultimoErro = "Falha ao enviar aviso de vencimento: ${resultado.motivo}"
                                    ))
                                }
                            }
                        }
                    }
                }
                is BuscaClienteResultado.CredenciaisInvalidas -> {
                    dao.atualizar(cliente.copy(
                        diasRestantes = cliente.diasRestantes ?: DIAS_SENTINELA_VENCIDO_SEM_DATA,
                        ultimaChecagemEm = System.currentTimeMillis(),
                        ultimoErro = "Usuário/senha não encontrados em nenhum servidor"
                    ))
                }
                is BuscaClienteResultado.Erro -> {
                    // Na prática, "não encontrado em NENHUM servidor" quase
                    // sempre é conta desativada/vencida (o painel corta o
                    // acesso à API inteira), não um problema de rede - o
                    // mesmo comportamento que o VLTV Play já trata como
                    // "Expirado" direto, sem mostrar diagnóstico técnico.
                    // Se ainda não tínhamos NENHUMA data de vencimento
                    // conhecida desse cliente, marca como Vencido (sem
                    // data exata) em vez de deixar preso em "Erro". Se já
                    // tínhamos uma data de antes, mantém ela como está.
                    dao.atualizar(cliente.copy(
                        diasRestantes = cliente.diasRestantes ?: DIAS_SENTINELA_VENCIDO_SEM_DATA,
                        ultimaChecagemEm = System.currentTimeMillis(),
                        ultimoErro = resultado.mensagem
                    ))
                }
            }
        } catch (e: Exception) {
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

    companion object {
        private const val WORK_NAME_PERIODICA = "verificacao_vencimento_diaria"

        // Horário-alvo apenas pra ANCORAR o início do ciclo de 1 em 1 hora -
        // é essa execução, entre HORA_ALVO e HORA_ALVO+JANELA_AVISO_HORAS
        // (09h-11h), que de fato manda a mensagem/notificação de vencimento -
        // ver estaNoHorarioDeAviso().
        private const val HORA_ALVO = 9
        private const val MINUTO_ALVO = 30

        // ✅ ALTERADO: 1h em vez de 5h - mais assertivo pra detectar rápido
        // uma alteração de vencimento/login feita manualmente no servidor.
        // O envio de mensagem ao cliente continua sendo só 1x por dia,
        // gated por estaNoHorarioDeAviso().
        private const val INTERVALO_HORAS = 1L

        // ✅ ALTERADO: 2h em vez de 1h - margem de segurança caso o
        // WorkManager atrase a execução (Doze mode/otimização de bateria),
        // pra não pular o aviso do dia por causa de um atraso do sistema.
        private const val JANELA_AVISO_HORAS = 2

        private fun estaNoHorarioDeAviso(): Boolean {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hora in HORA_ALVO until (HORA_ALVO + JANELA_AVISO_HORAS)
        }

        const val WORK_NAME_MANUAL = "verificacao_vencimento_manual"

        // Nome de trabalho separado do WORK_NAME_MANUAL (sincronização normal
        // via botão de atualizar/pull-to-refresh) - assim o spinner de
        // "sincronizando" e o botão "Enviar avisos agora" não ficam
        // observando/disparando o mesmo WorkInfo um do outro.
        const val WORK_NAME_ENVIO_MANUAL = "envio_avisos_manual"
        private const val CHAVE_FORCAR_ENVIO = "forcar_envio"

        // Valor usado em diasRestantes quando sabemos que a conta está
        // vencida/indisponível mas NUNCA descobrimos a data exata (conta já
        // chegou desativada, sem nenhum histórico de exp_date válido). -1
        // já entra na faixa "Vencido" em toda a lógica existente (badge,
        // filtro da aba Vencidos), sem precisar de um campo novo no banco.
        private const val DIAS_SENTINELA_VENCIDO_SEM_DATA = -1

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
                INTERVALO_HORAS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(atrasoInicial, TimeUnit.MILLISECONDS)
                .build()

            // UPDATE continua necessário aqui: quem já tinha o agendamento
            // de 5h precisa migrar pro novo ciclo de 1h.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODICA,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun executarAgora(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<VerificacaoVencimentoWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

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

        // Botão "Enviar avisos agora": manda pra qualquer cliente com 3/2/1/0
        // dias (ou vencido) que ainda não recebeu aviso hoje, sem esperar a
        // janela de horário 9h-11h. Se o ciclo automático já mandou com
        // sucesso pra um cliente, ele é pulado normalmente (mesma checagem
        // de ultimoAvisoDias) - não duplica mensagem.
        fun executarEnvioAgora(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val dados = Data.Builder()
                .putBoolean(CHAVE_FORCAR_ENVIO, true)
                .build()
            val request = OneTimeWorkRequestBuilder<VerificacaoVencimentoWorker>()
                .setConstraints(constraints)
                .setInputData(dados)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ENVIO_MANUAL,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

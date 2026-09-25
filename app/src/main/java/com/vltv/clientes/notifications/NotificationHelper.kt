package com.vltv.clientes.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vltv.clientes.MainActivity
import com.vltv.clientes.R

object NotificationHelper {

    private const val CHANNEL_ID = "vencimentos_clientes"
    private const val CHANNEL_NAME = "Vencimentos de Clientes"

    // ✅ NOVO: canal separado só pra diagnóstico temporário de sincronização
    // — assim não se mistura com as notificações normais de vencimento.
    private const val CHANNEL_ID_DIAGNOSTICO = "diagnostico_sincronizacao"
    private const val CHANNEL_NAME_DIAGNOSTICO = "Diagnóstico de Sincronização"

    fun garantirCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val canal = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos de clientes próximos do vencimento ou já vencidos"
                }
                manager.createNotificationChannel(canal)
            }
            if (manager.getNotificationChannel(CHANNEL_ID_DIAGNOSTICO) == null) {
                val canalDiag = NotificationChannel(
                    CHANNEL_ID_DIAGNOSTICO, CHANNEL_NAME_DIAGNOSTICO, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Mostra o resultado exato de cada tentativa de sincronização — temporário, pra debug"
                }
                manager.createNotificationChannel(canalDiag)
            }
        }
    }

    fun notificarVencimento(context: Context, clienteId: Long, nomeCliente: String, diasRestantes: Int) {
        garantirCanal(context)

        val titulo: String
        val texto: String
        if (diasRestantes < 0) {
            titulo = "Cliente vencido"
            texto = "$nomeCliente já venceu — hora de cobrar a renovação."
        } else if (diasRestantes == 1) {
            titulo = "Vence amanhã"
            texto = "$nomeCliente vence em 1 dia."
        } else {
            titulo = "Vencimento próximo"
            texto = "$nomeCliente vence em $diasRestantes dias."
        }

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, clienteId.toInt(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notificacao = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(clienteId.toInt(), notificacao)
        } catch (e: SecurityException) {
            // Permissão POST_NOTIFICATIONS não concedida (Android 13+) - segue sem notificar.
        }
    }

    // ✅ NOVO — TEMPORÁRIO PRA DEBUG: dispara uma notificação com o
    // resultado EXATO de uma tentativa de sincronização (sucesso, motivo da
    // falha, ou a exceção real). Usa setStyle(BigTextStyle) pra caber texto
    // longo (o resumo de diagnóstico pode ser grande). ID bem distante dos
    // IDs de vencimento (que usam clienteId.toInt()) pra nunca colidir.
    fun notificarDiagnostico(context: Context, nomeCliente: String, mensagem: String) {
        garantirCanal(context)

        val notificacao = NotificationCompat.Builder(context, CHANNEL_ID_DIAGNOSTICO)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Sincronização: $nomeCliente")
            .setContentText(mensagem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(900_000 + nomeCliente.hashCode(), notificacao)
        } catch (e: SecurityException) {
            // Permissão POST_NOTIFICATIONS não concedida - segue sem notificar.
        }
    }
}

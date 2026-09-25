package com.vltv.clientes.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class EnvioResultado {
    object Ok : EnvioResultado()
    data class Falha(val motivo: String) : EnvioResultado()
}

// Fala com o backend Node.js/Baileys que fica na VPS - o único trabalho
// dele é receber "manda essa mensagem pra esse WhatsApp" e enfileirar.
object BackendApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun enviarMensagem(context: Context, telefone: String, mensagem: String): EnvioResultado {
        val baseUrl = AppConfig.getBackendUrl(context)
        val apiKey = AppConfig.getApiKey(context)

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return EnvioResultado.Falha("Servidor de envio não configurado. Vá em Configurações → Servidor de Envio.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("telefone", telefone)
                    put("mensagem", mensagem)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/enviar")
                    .header("x-api-key", apiKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        EnvioResultado.Ok
                    } else {
                        EnvioResultado.Falha("Servidor respondeu ${response.code}")
                    }
                }
            } catch (e: Exception) {
                EnvioResultado.Falha("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // Chama GET /saude - usado no botão "Testar Conexão" da tela de config.
    suspend fun testarConexao(baseUrl: String): EnvioResultado {
        return withContext(Dispatchers.IO) {
            try {
                var url = baseUrl.trim()
                if (url.endsWith("/")) url = url.dropLast(1)

                val request = Request.Builder().url("$url/saude").get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val conectado = JSONObject(body).optBoolean("whatsappConectado", false)
                        if (conectado) EnvioResultado.Ok
                        else EnvioResultado.Falha("Servidor no ar, mas o WhatsApp ainda não está conectado (escaneie o QR no Telegram).")
                    } else {
                        EnvioResultado.Falha("Servidor respondeu ${response.code}")
                    }
                }
            } catch (e: Exception) {
                EnvioResultado.Falha("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}

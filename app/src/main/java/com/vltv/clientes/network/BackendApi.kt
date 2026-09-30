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

data class TmdbResultado(
    val id: Int,
    val tipo: String, // "filme" ou "serie"
    val titulo: String,
    val ano: String,
    val sinopse: String,
    val thumbUrl: String,
    val posterUrl: String
)

sealed class BuscaTmdbResultado {
    data class Ok(val resultados: List<TmdbResultado>) : BuscaTmdbResultado()
    data class Falha(val motivo: String) : BuscaTmdbResultado()
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

    // imagemBase64: string base64 (sem o prefixo "data:image/...;base64,")
    // de uma imagem JPEG opcional a ser enviada junto com a mensagem.
    suspend fun enviarMensagem(
        context: Context,
        telefone: String,
        mensagem: String,
        imagemBase64: String? = null
    ): EnvioResultado {
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
                    if (!imagemBase64.isNullOrBlank()) put("imagem", imagemBase64)
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

    // Baixa uma imagem pública (usado pra miniaturas e pôsteres do TMDB - a
    // imagem em si vem direto do CDN deles, sem passar pelo nosso backend).
    suspend fun baixarBitmap(url: String): android.graphics.Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Busca filmes/séries pelo nome, pra usar como pôster no Gerador de Banner.
    // A chave do TMDB fica só no backend - o app nunca vê ela.
    suspend fun buscarTmdb(context: Context, termo: String): BuscaTmdbResultado {
        val baseUrl = AppConfig.getBackendUrl(context)
        val apiKey = AppConfig.getApiKey(context)

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return BuscaTmdbResultado.Falha("Servidor de envio não configurado. Vá em Configurações → Servidor de Envio.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val urlBusca = "$baseUrl/tmdb/buscar?q=${java.net.URLEncoder.encode(termo, "UTF-8")}"
                val request = Request.Builder()
                    .url(urlBusca)
                    .header("x-api-key", apiKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val corpo = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val erro = runCatching { JSONObject(corpo).optString("erro") }.getOrNull()
                        return@use BuscaTmdbResultado.Falha(erro?.takeIf { it.isNotBlank() } ?: "Servidor respondeu ${response.code}")
                    }
                    val json = JSONObject(corpo)
                    val array = json.optJSONArray("resultados") ?: org.json.JSONArray()
                    val lista = (0 until array.length()).map { i ->
                        val item = array.getJSONObject(i)
                        TmdbResultado(
                            id = item.getInt("id"),
                            tipo = item.getString("tipo"),
                            titulo = item.getString("titulo"),
                            ano = item.optString("ano"),
                            sinopse = item.optString("sinopse"),
                            thumbUrl = item.getString("thumbUrl"),
                            posterUrl = item.getString("posterUrl")
                        )
                    }
                    BuscaTmdbResultado.Ok(lista)
                }
            } catch (e: Exception) {
                BuscaTmdbResultado.Falha("${e.javaClass.simpleName}: ${e.message}")
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

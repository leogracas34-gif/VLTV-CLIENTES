package com.vltv.clientes.network

import android.content.Context
import com.vltv.clientes.data.ClienteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Gateway do VLTV Play (DNS mascarado). Duas coisas passam por ele:
//  1. Consultar o vencimento do cliente (XtreamCheck) - o app não conhece mais nenhum DNS real.
//  2. Liberar/remover logins: só quem está liberado consegue entrar no VLTV Play. O app avisa o
//     backend da VPS (mesmo "Servidor de Envio" já configurado), e o backend repassa ao gateway
//     com a chave secreta guardada só na VPS.
object GatewayApi {

    // Mesmo endereço do config.js do app da TV. Se mudar o nome do gateway, mude aqui também.
    const val URL = "https://tv.vltvplay.tech"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun item(c: ClienteEntity): JSONObject =
        JSONObject().put("user", c.usuario).put("pass", c.senha).put("obs", c.nome)

    // true = o backend confirmou. false = sem configuração, sem rede ou backend fora do ar.
    private suspend fun postar(context: Context, caminho: String, corpo: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val base = AppConfig.getBackendUrl(context)
            val chave = AppConfig.getApiKey(context)
            if (base.isBlank() || chave.isBlank()) return@withContext false
            try {
                val request = Request.Builder()
                    .url("$base/gateway/$caminho")
                    .header("x-api-key", chave)
                    .post(corpo.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    fun configurado(context: Context): Boolean =
        AppConfig.getBackendUrl(context).isNotBlank() && AppConfig.getApiKey(context).isNotBlank()

    // Libera (ou atualiza a senha de) um cliente.
    suspend fun liberar(context: Context, cliente: ClienteEntity): Boolean =
        postar(context, "liberar", item(cliente))

    suspend fun remover(context: Context, usuario: String): Boolean =
        postar(context, "remover", JSONObject().put("user", usuario))

    // Manda TODOS os clientes ativos: o gateway fica igual a essa lista (quem foi excluído ou
    // desativado no app perde o acesso). Lista vazia nunca é enviada, por segurança.
    suspend fun espelho(context: Context, clientes: List<ClienteEntity>): Boolean {
        if (clientes.isEmpty()) return true
        val lista = JSONArray()
        clientes.forEach { lista.put(item(it)) }
        return postar(context, "espelho", JSONObject().put("lista", lista))
    }
}

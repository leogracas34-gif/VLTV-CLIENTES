package com.vltv.clientes.network

import android.content.Context
import com.vltv.clientes.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Envia UMA VEZ todos os clientes deste aparelho para o painel Admin (admin.vltvplay.tech),
// que passa a ser a fonte única dos dados. Depois disso o app do celular não é mais necessário.
object PainelApi {

    const val URL = "https://admin.vltvplay.tech"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val JSON = "application/json".toMediaType()

    // Retorna uma frase pronta para mostrar na tela (sucesso ou motivo do erro).
    suspend fun enviarClientes(context: Context, usuario: String, senha: String): String =
        withContext(Dispatchers.IO) {
            try {
                // 1) Entra no painel com o mesmo usuário e senha do painel.
                val loginBody = JSONObject().put("username", usuario).put("password", senha)
                val loginReq = Request.Builder()
                    .url("$URL/api/admin/login")
                    .post(loginBody.toString().toRequestBody(JSON))
                    .build()
                val token = client.newCall(loginReq).execute().use { r ->
                    val texto = r.body?.string() ?: ""
                    if (!r.isSuccessful) {
                        return@withContext "✗ Usuário ou senha do painel incorretos (HTTP ${r.code})."
                    }
                    JSONObject(texto).optString("token")
                }
                if (token.isBlank()) return@withContext "✗ O painel não devolveu o acesso."

                // 2) Junta todos os clientes do banco do aparelho.
                val clientes = AppDatabase.getDatabase(context).clienteDao().listarTodosParaExportar()
                if (clientes.isEmpty()) return@withContext "✗ Não há clientes neste aparelho para enviar."
                val lista = JSONArray()
                clientes.forEach { c ->
                    val o = JSONObject()
                        .put("nome", c.nome)
                        .put("whatsapp", c.whatsapp)
                        .put("usuario", c.usuario)
                        .put("senha", c.senha)
                        .put("ativo", c.ativo)
                        .put("plano", c.plano)
                        .put("valorPlano", c.valorPlano)
                        .put("observacao", c.observacao ?: JSONObject.NULL)
                        .put("expDateUnix", c.expDateUnix ?: JSONObject.NULL)
                        .put("diasRestantes", c.diasRestantes ?: JSONObject.NULL)
                        .put("ultimoAvisoDias", c.ultimoAvisoDias ?: JSONObject.NULL)
                        .put("criadoEm", c.criadoEm)
                    lista.put(o)
                }

                // 3) Textos das mensagens e chave Pix (o painel só usa onde ainda estiver vazio).
                val config = JSONObject()
                    .put("pix", AppConfig.getChavePix(context))
                    .put("msg_3", AppConfig.getMensagem3Dias(context))
                    .put("msg_2", AppConfig.getMensagem2Dias(context))
                    .put("msg_1", AppConfig.getMensagem1Dia(context))
                    .put("msg_hoje", AppConfig.getMensagemHoje(context))
                    .put("msg_vencido", AppConfig.getMensagemVencido(context))
                    .put("msg_renovacao", AppConfig.getMensagemRenovacao(context))

                val corpo = JSONObject().put("clientes", lista).put("config", config)
                val req = Request.Builder()
                    .url("$URL/api/admin/clientes/importar")
                    .header("Authorization", "Bearer $token")
                    .post(corpo.toString().toRequestBody(JSON))
                    .build()
                client.newCall(req).execute().use { r ->
                    val texto = r.body?.string() ?: ""
                    if (!r.isSuccessful) {
                        val motivo = try { JSONObject(texto).optString("error") } catch (e: Exception) { "" }
                        return@withContext "✗ O painel recusou (HTTP ${r.code}) $motivo"
                    }
                    val j = JSONObject(texto)
                    "✓ Enviado! Novos: ${j.optInt("novos")}, atualizados: ${j.optInt("atualizados")}, " +
                        "ignorados: ${j.optInt("ignorados")}. Confira em Admin → Clientes."
                }
            } catch (e: Exception) {
                "✗ Falha ao enviar: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
        }
}

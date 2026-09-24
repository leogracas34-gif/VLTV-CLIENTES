package com.vltv.clientes.network

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ResultadoBusca(
    val dns: String,
    val expDateUnix: Long?,
    val diasRestantes: Int?
)

sealed class BuscaClienteResultado {
    data class Sucesso(val resultado: ResultadoBusca) : BuscaClienteResultado()
    object CredenciaisInvalidas : BuscaClienteResultado()
    data class Erro(val mensagem: String) : BuscaClienteResultado()
}

// Mesma lógica de "testar todos os DNS em paralelo, depois em série com
// timeout maior" já usada no LoginActivity.kt do VLTV+ — reaproveitada
// aqui pra achar automaticamente em qual servidor um usuário/senha está
// ativo, sem o operador precisar saber de antemão qual é o DNS certo.
object XtreamCheck {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val clientRapido = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val clientLento = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizarBaseUrl(dns: String): String {
        var url = dns.trim()
        if (url.contains("player_api.php")) url = url.substringBefore("player_api.php")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url += "/"
        return url
    }

    // Retorna: ResultadoBusca se achou e está ATIVO; null se esse servidor
    // específico não respondeu ou a conta não existe nele.
    // "credenciaisExpiradas" é setado (via referência externa) quando a
    // conta EXISTE nesse servidor mas está vencida — pra diferenciar de
    // "usuário/senha errados" no resultado final.
    private fun testarServidor(
        baseUrl: String,
        user: String,
        pass: String,
        httpClient: OkHttpClient
    ): ResultadoBusca? {
        val urlBase = normalizarBaseUrl(baseUrl)
        val urlSemBarra = urlBase.removeSuffix("/")
        return try {
            val request = Request.Builder()
                .url("$urlSemBarra/player_api.php?username=$user&password=$pass")
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                val temUserInfo = body.contains("user_info") && body.contains("server_info")
                if (!temUserInfo) return null

                val authZero = Regex("\"auth\"\\s*:\\s*\"?0\"?").containsMatchIn(body)
                if (authZero) return null // usuário/senha não batem NESTE servidor

                // Conta existe e autenticou — pega o exp_date, mesmo se
                // estiver Expired/Disabled (queremos mostrar o vencimento
                // de qualquer forma, só marcando como vencido).
                val expDateMatch = Regex("\"exp_date\"\\s*:\\s*\"?(\\d+)\"?").find(body)
                val expDateUnix = expDateMatch?.groupValues?.get(1)?.toLongOrNull()

                val diasRestantes = if (expDateUnix != null) {
                    val diffMs = (expDateUnix * 1000L) - System.currentTimeMillis()
                    Math.ceil(diffMs / (1000.0 * 60 * 60 * 24)).toInt()
                } else null

                ResultadoBusca(dns = urlBase, expDateUnix = expDateUnix, diasRestantes = diasRestantes)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Testa todos os DNS conhecidos (fase rápida em paralelo, depois fase
    // lenta em série como fallback) até achar onde esse usuário/senha
    // funciona. Suspende até terminar - chamar de uma coroutine em
    // Dispatchers.IO.
    suspend fun buscarCliente(context: Context, usuario: String, senha: String): BuscaClienteResultado =
        withContext(Dispatchers.IO) {
            DnsConfig.refresh(context)
            val servidores = DnsConfig.servers(context)

            var resultado: ResultadoBusca? = null

            try {
                val canal = Channel<ResultadoBusca>(Channel.UNLIMITED)
                val jobs = servidores.map { url ->
                    launch(Dispatchers.IO) {
                        val r = testarServidor(url, usuario, senha, clientRapido)
                        if (r != null) canal.trySend(r)
                    }
                }
                resultado = withTimeoutOrNull(18_000L) { canal.receive() }
                jobs.forEach { it.cancel() }
                canal.close()
            } catch (e: Exception) {
                // segue pro fallback
            }

            if (resultado == null) {
                for (servidor in servidores) {
                    val r = testarServidor(servidor, usuario, senha, clientLento)
                    if (r != null) { resultado = r; break }
                }
            }

            if (resultado != null) {
                BuscaClienteResultado.Sucesso(resultado)
            } else {
                BuscaClienteResultado.CredenciaisInvalidas
            }
        }

    // Reconsulta um cliente já cadastrado (usa o DNS já conhecido dele
    // como primeira tentativa, antes de sair testando tudo de novo, pra
    // ser mais rápido nas checagens diárias de rotina).
    suspend fun reconsultar(context: Context, dnsConhecido: String, usuario: String, senha: String): BuscaClienteResultado =
        withContext(Dispatchers.IO) {
            val direto = testarServidor(dnsConhecido, usuario, senha, clientRapido)
            if (direto != null) return@withContext BuscaClienteResultado.Sucesso(direto)

            // DNS antigo não respondeu mais (pode ter saído do ar) — busca
            // de novo em toda a lista, igual ao cadastro inicial.
            buscarCliente(context, usuario, senha)
        }
}

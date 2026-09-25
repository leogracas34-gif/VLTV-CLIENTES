package com.vltv.clientes.network

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
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

// Mesma lógica de "testar todos os DNS em paralelo, o que responder OK
// primeiro ganha" do LoginActivity.kt do VLTV+, mas guardando o MOTIVO de
// cada servidor que falhar — pra saber de verdade se é credencial errada,
// timeout, erro de DNS, painel fora do ar, etc., em vez de só "não achei".
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

    // Extrai só o domínio (sem http://) pra usar como chave no mapa de
    // diagnóstico, ex.: "fibercdn.sbs".
    private fun dominioDe(url: String): String =
        url.removePrefix("http://").removePrefix("https://").removeSuffix("/")

    // Retorna o resultado (se achou e está ativo) OU o motivo exato da
    // falha nesse servidor específico — nunca os dois nulos ao mesmo tempo.
    private fun testarServidor(
        baseUrl: String,
        user: String,
        pass: String,
        httpClient: OkHttpClient
    ): Pair<ResultadoBusca?, String> {
        val urlBase = normalizarBaseUrl(baseUrl)
        val urlSemBarra = urlBase.removeSuffix("/")
        return try {
            val request = Request.Builder()
                .url("$urlSemBarra/player_api.php?username=$user&password=$pass")
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null to "HTTP ${response.code}"
                }
                val body = response.body?.string() ?: return null to "resposta vazia"

                val temUserInfo = body.contains("user_info") && body.contains("server_info")
                if (!temUserInfo) {
                    return null to "não é um painel Xtream válido (resposta sem user_info)"
                }

                val authZero = Regex("\"auth\"\\s*:\\s*\"?0\"?").containsMatchIn(body)
                if (authZero) {
                    return null to "usuário/senha não encontrados neste servidor"
                }

                val expDateMatch = Regex("\"exp_date\"\\s*:\\s*\"?(\\d+)\"?").find(body)
                val expDateUnix = expDateMatch?.groupValues?.get(1)?.toLongOrNull()

                val diasRestantes = if (expDateUnix != null) {
                    val diffMs = (expDateUnix * 1000L) - System.currentTimeMillis()
                    Math.ceil(diffMs / (1000.0 * 60 * 60 * 24)).toInt()
                } else null

                ResultadoBusca(dns = urlBase, expDateUnix = expDateUnix, diasRestantes = diasRestantes) to "ok"
            }
        } catch (e: Exception) {
            null to "${e.javaClass.simpleName}: ${e.message ?: "sem detalhes"}"
        }
    }

    // Testa um lote de servidores em paralelo, registrando o motivo de cada
    // um que falhar no mapa "diagnosticos" (compartilhado entre as fases).
    private suspend fun testarLoteEmParalelo(
        servidores: List<String>,
        usuario: String,
        senha: String,
        httpClient: OkHttpClient,
        timeoutMs: Long,
        diagnosticos: ConcurrentHashMap<String, String>
    ): ResultadoBusca? = coroutineScope {
        var resultado: ResultadoBusca? = null
        try {
            val canal = Channel<ResultadoBusca>(Channel.UNLIMITED)
            val jobs = servidores.map { url ->
                launch(Dispatchers.IO) {
                    val (r, motivo) = testarServidor(url, usuario, senha, httpClient)
                    if (r != null) canal.trySend(r) else diagnosticos[dominioDe(url)] = motivo
                }
            }
            resultado = withTimeoutOrNull(timeoutMs) { canal.receive() }
            jobs.forEach { it.cancel() }
            canal.close()
        } catch (e: Exception) {
            // segue com resultado null
        }
        resultado
    }

    // Monta um resumo legível a partir do mapa de diagnóstico: agrupa por
    // tipo de motivo e mostra quantos servidores caíram em cada um, além de
    // 1 exemplo de domínio por grupo — assim dá pra ver de cara se é
    // credencial errada (a maioria diz "usuário/senha não encontrados") ou
    // se é rede/DNS (a maioria diz timeout/erro de conexão).
    private fun resumirDiagnosticos(diagnosticos: Map<String, String>): String {
        if (diagnosticos.isEmpty()) return "Nenhum servidor respondeu (sem diagnóstico)."

        val porMotivo = diagnosticos.entries.groupBy { it.value }
        return porMotivo.entries
            .sortedByDescending { it.value.size }
            .joinToString("; ") { (motivo, entradas) ->
                val exemplo = entradas.first().key
                "$motivo (${entradas.size}/${diagnosticos.size}, ex.: $exemplo)"
            }
    }

    // Testa todos os DNS conhecidos em paralelo (fase rápida, até 18s) e,
    // se ninguém responder OK nesse tempo, tenta de novo TODOS em paralelo
    // (fallback, até 25s) com timeout maior — e, se mesmo assim não achar,
    // retorna um resumo real do motivo de cada servidor ter falhado.
    suspend fun buscarCliente(context: Context, usuario: String, senha: String): BuscaClienteResultado =
        withContext(Dispatchers.IO) {
            DnsConfig.refresh(context)
            val servidores = DnsConfig.servers(context)
            val diagnosticos = ConcurrentHashMap<String, String>()

            var resultado = testarLoteEmParalelo(servidores, usuario, senha, clientRapido, 18_000L, diagnosticos)

            if (resultado == null) {
                resultado = testarLoteEmParalelo(servidores, usuario, senha, clientLento, 25_000L, diagnosticos)
            }

            if (resultado != null) {
                BuscaClienteResultado.Sucesso(resultado)
            } else {
                BuscaClienteResultado.Erro(resumirDiagnosticos(diagnosticos))
            }
        }

    // Reconsulta um cliente já cadastrado (usa o DNS já conhecido dele
    // como primeira tentativa, antes de sair testando tudo de novo, pra
    // ser mais rápido nas checagens diárias de rotina).
    suspend fun reconsultar(context: Context, dnsConhecido: String, usuario: String, senha: String): BuscaClienteResultado =
        withContext(Dispatchers.IO) {
            val (direto, _) = testarServidor(dnsConhecido, usuario, senha, clientRapido)
            if (direto != null) return@withContext BuscaClienteResultado.Sucesso(direto)

            // DNS antigo não respondeu mais (pode ter saído do ar) — busca
            // de novo em toda a lista, igual ao cadastro inicial.
            buscarCliente(context, usuario, senha)
        }
}

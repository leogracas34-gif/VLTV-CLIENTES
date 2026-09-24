package com.vltv.clientes.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Reaproveita EXATAMENTE a mesma fonte de DNS que o app VLTV+ já usa
// (https://vltvplay.tech/dns_config.json) — qualquer alteração feita na VPS
// (trocar, remover ou adicionar um servidor) vale automaticamente pros dois
// apps, sem precisar mexer em nenhum código.
object DnsConfig {

    private const val CONFIG_URL = "https://vltvplay.tech/dns_config.json"
    private const val PREFS_NAME = "vltv_clientes_dns"
    private const val KEY_JSON = "servers_json"
    private const val INTERVALO_MIN_MS = 60_000L

    // Lista de emergência, só usada se a 1ª abertura do app não tiver
    // internet ainda ou a VPS estiver fora do ar nesse instante.
    private val FALLBACK = listOf(
        "http://fibercdn.sbs",
        "http://ranos.sbs",
        "http://cmdtv.casa",
        "http://cmdtv.pro",
        "http://cmdtv.sbs",
        "http://cmdtv.top",
        "http://cmdbr.life",
        "http://supertv.red",
        "http://kodexk.click",
        "http://maisplaytech.space",
        "http://pthdtv.sbs",
        "http://pthdtv.top",
        "http://cdnsec.cyou",
        "http://fx12.sbs",
        "http://anotaai.lol",
        "http://brtx.beauty",
        "http://fuiali.vip",
        "http://dogshow.club",
        "http://cdnsec.click",
        "http://sivimcdn.click",
        "http://cybertronplay.space"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    @Volatile private var cache: List<String>? = null
    @Volatile private var ultimoRefreshOk = 0L

    private fun parse(raw: String): List<String>? {
        return try {
            val arr = JSONObject(raw).optJSONArray("servers") ?: return null
            val lista = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.startsWith("http://") || s.startsWith("https://")) lista.add(s)
            }
            lista.distinct().takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    // Lista atual, sem rede - sempre devolve algo utilizável.
    fun servers(context: Context): List<String> {
        cache?.let { return it }

        val salva = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null)
                ?.let { parse(it) }
        } catch (e: Exception) { null }

        if (salva != null) {
            cache = salva
            return salva
        }
        return FALLBACK
    }

    // Baixa a lista mais atual da VPS. BLOQUEANTE — chamar em thread de
    // fundo/IO. Não baixa de novo se já deu certo há menos de 1 minuto.
    @Synchronized
    fun refresh(context: Context, force: Boolean = false): Boolean {
        val agora = System.currentTimeMillis()
        if (!force && agora - ultimoRefreshOk < INTERVALO_MIN_MS) return true

        return try {
            val request = Request.Builder()
                .url(CONFIG_URL)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val raw = response.body?.string().orEmpty()
                val lista = parse(raw) ?: return false

                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_JSON, raw).apply()

                cache = lista
                ultimoRefreshOk = agora
                true
            }
        } catch (e: Exception) { false }
    }
}

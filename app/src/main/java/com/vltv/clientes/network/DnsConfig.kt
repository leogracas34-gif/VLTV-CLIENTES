package com.vltv.clientes.network

import android.content.Context

// Antes baixava a lista pública de DNS (https://vltvplay.tech/dns_config.json) e tinha uma
// lista de emergência dentro do código. Agora o app só conhece o gateway da VPS: os DNS
// reais ficam escondidos lá e nunca chegam ao aparelho.
object DnsConfig {

    private const val PREFS_ANTIGO = "vltv_clientes_dns"

    // Sempre só o gateway.
    fun servers(context: Context): List<String> {
        limparListaAntiga(context)
        return listOf(GatewayApi.URL)
    }

    // Mantido para o resto do app: não há mais nada para baixar.
    fun refresh(context: Context, force: Boolean = false): Boolean = true

    // Apaga a lista de DNS que versões anteriores guardaram no aparelho.
    private fun limparListaAntiga(context: Context) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_ANTIGO, Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (e: Exception) { /* ignora */ }
    }
}

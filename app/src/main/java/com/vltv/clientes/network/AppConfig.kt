package com.vltv.clientes.network

import android.content.Context

// Guarda as configurações do app: endereço do backend de WhatsApp, chave
// Pix do negócio e os templates de mensagem editáveis pelo usuário na tela
// de Configurações.
object AppConfig {
    private const val PREFS_NAME = "vltv_clientes_config"

    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_API_KEY = "backend_api_key"

    private const val KEY_PIX_CHAVE = "pix_chave"

    private const val KEY_MSG_3_DIAS = "msg_3_dias"
    private const val KEY_MSG_2_DIAS = "msg_2_dias"
    private const val KEY_MSG_1_DIA = "msg_1_dia"
    private const val KEY_MSG_VENCIDO = "msg_vencido"

    const val PADRAO_MSG_3_DIAS = "Olá, {nome}! 👋 Seu plano vence em 3 dias. Renove com antecedência pra não perder o acesso!"
    const val PADRAO_MSG_2_DIAS = "Olá, {nome}! Faltam apenas 2 dias para o vencimento do seu plano. Já pensou em renovar?"
    const val PADRAO_MSG_1_DIA = "Olá, {nome}! ⚠️ Seu plano vence amanhã! Renove agora mesmo pra continuar sem interrupção."
    const val PADRAO_MSG_VENCIDO = "Olá, {nome}. Seu plano venceu. Entre em contato para renovar e voltar a ter acesso."

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBackendUrl(context: Context): String =
        prefs(context).getString(KEY_BACKEND_URL, "") ?: ""

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun salvarBackend(context: Context, url: String, apiKey: String) {
        var limpa = url.trim()
        if (limpa.endsWith("/")) limpa = limpa.dropLast(1)
        prefs(context).edit()
            .putString(KEY_BACKEND_URL, limpa)
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    // Chave Pix do negócio (ex: um e-mail dedicado) - usada futuramente nas
    // mensagens automáticas pra facilitar o pagamento do cliente.
    fun getChavePix(context: Context): String =
        prefs(context).getString(KEY_PIX_CHAVE, "") ?: ""

    fun salvarChavePix(context: Context, chave: String) {
        prefs(context).edit().putString(KEY_PIX_CHAVE, chave.trim()).apply()
    }

    fun getMensagem3Dias(context: Context) = prefs(context).getString(KEY_MSG_3_DIAS, PADRAO_MSG_3_DIAS) ?: PADRAO_MSG_3_DIAS
    fun getMensagem2Dias(context: Context) = prefs(context).getString(KEY_MSG_2_DIAS, PADRAO_MSG_2_DIAS) ?: PADRAO_MSG_2_DIAS
    fun getMensagem1Dia(context: Context) = prefs(context).getString(KEY_MSG_1_DIA, PADRAO_MSG_1_DIA) ?: PADRAO_MSG_1_DIA
    fun getMensagemVencido(context: Context) = prefs(context).getString(KEY_MSG_VENCIDO, PADRAO_MSG_VENCIDO) ?: PADRAO_MSG_VENCIDO

    fun salvarMensagens(context: Context, msg3: String, msg2: String, msg1: String, msgVencido: String) {
        prefs(context).edit()
            .putString(KEY_MSG_3_DIAS, msg3)
            .putString(KEY_MSG_2_DIAS, msg2)
            .putString(KEY_MSG_1_DIA, msg1)
            .putString(KEY_MSG_VENCIDO, msgVencido)
            .apply()
    }

    // Monta a mensagem certa pro número de dias restantes, substituindo
    // {nome} e {dias} pelos valores reais. Retorna null se não houver
    // mensagem configurada para esse valor de dias (não deve avisar).
    fun montarMensagemPara(context: Context, diasRestantes: Int, nome: String): String? {
        val template = when (diasRestantes) {
            3 -> getMensagem3Dias(context)
            2 -> getMensagem2Dias(context)
            1 -> getMensagem1Dia(context)
            else -> if (diasRestantes < 0) getMensagemVencido(context) else null
        } ?: return null

        return template
            .replace("{nome}", nome)
            .replace("{dias}", diasRestantes.toString())
    }
}

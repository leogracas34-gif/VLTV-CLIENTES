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
    private const val KEY_MSG_HOJE = "msg_hoje"
    private const val KEY_MSG_VENCIDO = "msg_vencido"

    const val PADRAO_MSG_3_DIAS = "Olá, {nome}! 👋\n\nSeu plano vence em 3 dias. Para não perder o acesso, renove com antecedência.\n\n💳 Chave Pix para pagamento: {pix}\n\nQualquer dúvida, estou à disposição!"
    const val PADRAO_MSG_2_DIAS = "Olá, {nome}!\n\nFaltam apenas 2 dias para o vencimento do seu plano. Já pensou em renovar?\n\n💳 Chave Pix para pagamento: {pix}"
    const val PADRAO_MSG_1_DIA = "Olá, {nome}! ⚠️\n\nSeu plano vence amanhã! Renove agora mesmo para continuar sem interrupção.\n\n💳 Chave Pix para pagamento: {pix}"
    const val PADRAO_MSG_HOJE = "Olá, {nome}! ⚠️\n\nSeu plano vence hoje! Renove ainda hoje para continuar sem interrupção no acesso.\n\n💳 Chave Pix para pagamento: {pix}"
    const val PADRAO_MSG_VENCIDO = "Olá, {nome}.\n\nSeu plano venceu. Para voltar a ter acesso, é só renovar.\n\n💳 Chave Pix para pagamento: {pix}\n\nQualquer dúvida, estou à disposição!"

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

    // Chave Pix do negócio (ex: um e-mail dedicado) - entra automaticamente
    // nas mensagens automáticas de vencimento, no lugar de {pix}.
    fun getChavePix(context: Context): String =
        prefs(context).getString(KEY_PIX_CHAVE, "") ?: ""

    fun salvarChavePix(context: Context, chave: String) {
        prefs(context).edit().putString(KEY_PIX_CHAVE, chave.trim()).apply()
    }

    fun getMensagem3Dias(context: Context) = prefs(context).getString(KEY_MSG_3_DIAS, PADRAO_MSG_3_DIAS) ?: PADRAO_MSG_3_DIAS
    fun getMensagem2Dias(context: Context) = prefs(context).getString(KEY_MSG_2_DIAS, PADRAO_MSG_2_DIAS) ?: PADRAO_MSG_2_DIAS
    fun getMensagem1Dia(context: Context) = prefs(context).getString(KEY_MSG_1_DIA, PADRAO_MSG_1_DIA) ?: PADRAO_MSG_1_DIA
    fun getMensagemHoje(context: Context) = prefs(context).getString(KEY_MSG_HOJE, PADRAO_MSG_HOJE) ?: PADRAO_MSG_HOJE
    fun getMensagemVencido(context: Context) = prefs(context).getString(KEY_MSG_VENCIDO, PADRAO_MSG_VENCIDO) ?: PADRAO_MSG_VENCIDO

    fun salvarMensagens(context: Context, msg3: String, msg2: String, msg1: String, msgHoje: String, msgVencido: String) {
        prefs(context).edit()
            .putString(KEY_MSG_3_DIAS, msg3)
            .putString(KEY_MSG_2_DIAS, msg2)
            .putString(KEY_MSG_1_DIA, msg1)
            .putString(KEY_MSG_HOJE, msgHoje)
            .putString(KEY_MSG_VENCIDO, msgVencido)
            .apply()
    }

    // Monta a mensagem certa pro número de dias restantes, substituindo
    // {nome}, {dias} e {pix} pelos valores reais. Retorna null se não houver
    // mensagem configurada para esse valor de dias (não deve avisar).
    //
    // IMPORTANTE: diasRestantes == 0 aqui é APENAS o caso em que sabemos a
    // data exata de vencimento e ela é hoje. Isso é completamente separado
    // do valor-sentinela -1 (DIAS_SENTINELA_VENCIDO_SEM_DATA) usado quando o
    // servidor Xtream não retorna mais os dados do cliente (conta
    // desativada) - aquele caso continua caindo em "diasRestantes < 0" e
    // usando a mensagem de "vencido", sem nenhuma mudança de comportamento.
    fun montarMensagemPara(context: Context, diasRestantes: Int, nome: String): String? {
        val template = when (diasRestantes) {
            3 -> getMensagem3Dias(context)
            2 -> getMensagem2Dias(context)
            1 -> getMensagem1Dia(context)
            0 -> getMensagemHoje(context)
            else -> if (diasRestantes < 0) getMensagemVencido(context) else null
        } ?: return null

        val comNomeEDias = template
            .replace("{nome}", nome)
            .replace("{dias}", diasRestantes.toString())

        return aplicarChavePix(context, comNomeEDias)
    }

    // Substitui {pix} pela chave configurada. Se não houver chave Pix
    // cadastrada ainda, remove a linha inteira do template em vez de mandar
    // "Chave Pix para pagamento: " vazio pro cliente.
    private fun aplicarChavePix(context: Context, texto: String): String {
        val chave = getChavePix(context)
        return if (chave.isBlank()) {
            texto.lines().filterNot { it.contains("{pix}") }.joinToString("\n")
        } else {
            texto.replace("{pix}", chave)
        }
    }
}

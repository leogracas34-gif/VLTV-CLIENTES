package com.vltv.clientes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Planos disponíveis para o cliente, com o valor padrão sugerido de cada um.
// O valor pode ser ajustado manualmente por cliente na hora do cadastro
// (ex: desconto combinado), então valorPadrao é só o ponto de partida.
enum class PlanoCliente(val label: String, val valorPadrao: Double) {
    MENSAL("Mensal (30 dias)", 40.0),
    TRIMESTRAL("Trimestral (3 meses)", 110.0),
    SEMESTRAL("Semestral (6 meses)", 210.0),
    ANUAL("Anual (12 meses)", 400.0)
}

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val whatsapp: String,       // só dígitos, com DDI+DDD, ex: 5531999998888
    // Preenchido automaticamente quando a sincronização em segundo plano
    // encontra o servidor certo. Fica "" (vazio) entre o momento em que o
    // cliente é salvo e o momento em que a primeira sincronização termina -
    // é assim que o app sabe que esse cliente está "Pendente".
    val dns: String = "",
    val usuario: String,
    val senha: String,
    val ativo: Boolean = true,

    // Plano contratado (nome do PlanoCliente, ex: "MENSAL") e valor cobrado
    // desse cliente especificamente - pré-preenchido com o padrão do plano
    // escolhido, mas editável.
    val plano: String = PlanoCliente.MENSAL.name,
    val valorPlano: Double = PlanoCliente.MENSAL.valorPadrao,

    // Observação livre sobre o cliente (uso interno - nunca entra nas
    // mensagens automáticas de WhatsApp).
    val observacao: String? = null,

    // Cache do último status verificado
    val expDateUnix: Long? = null,
    val diasRestantes: Int? = null,
    val ultimaChecagemEm: Long? = null,
    val ultimoErro: String? = null,

    // Evita reenviar o mesmo aviso no mesmo dia
    val ultimoAvisoDias: Int? = null,

    val criadoEm: Long = System.currentTimeMillis()
)

package com.vltv.clientes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val whatsapp: String,       // só dígitos, com DDI+DDD, ex: 5531999998888
    val dns: String,            // preenchido automaticamente após encontrar o servidor certo
    val usuario: String,
    val senha: String,
    val ativo: Boolean = true,

    // Cache do último status verificado
    val expDateUnix: Long? = null,
    val diasRestantes: Int? = null,
    val ultimaChecagemEm: Long? = null,
    val ultimoErro: String? = null,

    // Evita reenviar o mesmo aviso no mesmo dia
    val ultimoAvisoDias: Int? = null,

    val criadoEm: Long = System.currentTimeMillis()
)

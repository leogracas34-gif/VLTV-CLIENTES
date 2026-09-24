package com.vltv.clientes.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ClienteDao {

    @Query("SELECT * FROM clientes ORDER BY diasRestantes ASC")
    fun observarTodos(): LiveData<List<ClienteEntity>>

    @Query("SELECT * FROM clientes WHERE ativo = 1")
    suspend fun listarAtivos(): List<ClienteEntity>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun buscarPorId(id: Long): ClienteEntity?

    @Insert
    suspend fun inserir(cliente: ClienteEntity): Long

    @Update
    suspend fun atualizar(cliente: ClienteEntity)

    @Delete
    suspend fun excluir(cliente: ClienteEntity)
}

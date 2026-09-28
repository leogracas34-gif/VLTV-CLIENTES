package com.vltv.clientes.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vltv.clientes.R
import com.vltv.clientes.data.ClienteEntity

// Adapter de seleção múltipla pra tela de Transmissão - cada card mostra um
// indicador de marcado/desmarcado (não é um Checkbox nativo, pra manter o
// visual customizado igual ao resto do app) e o próprio card inteiro é
// clicável pra alternar a seleção.
class TransmissaoClienteAdapter(
    private val onToggle: (ClienteEntity) -> Unit
) : ListAdapter<ClienteEntity, TransmissaoClienteAdapter.VH>(DIFF) {

    // IDs dos clientes atualmente selecionados. Mantido fora da lista de
    // itens em si (que só muda quando o cadastro de clientes muda) pra não
    // precisar reconstruir os ClienteEntity a cada toque.
    private val selecionados = mutableSetOf<Long>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cardItem: View = view.findViewById(R.id.cardItem)
        val tvAvatar: android.widget.TextView = view.findViewById(R.id.tvAvatarInicial)
        val tvNome: android.widget.TextView = view.findViewById(R.id.tvNomeCliente)
        val tvWhatsapp: android.widget.TextView = view.findViewById(R.id.tvWhatsappCliente)
        val ivMarcado: android.widget.ImageView = view.findViewById(R.id.ivMarcado)
        val viewDesmarcado: View = view.findViewById(R.id.viewDesmarcado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transmissao_cliente, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cliente = getItem(position)
        val marcado = selecionados.contains(cliente.id)

        holder.tvAvatar.text = cliente.nome.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvNome.text = cliente.nome
        holder.tvWhatsapp.text = formatarTelefone(cliente.whatsapp)

        holder.ivMarcado.visibility = if (marcado) View.VISIBLE else View.GONE
        holder.viewDesmarcado.visibility = if (marcado) View.GONE else View.VISIBLE
        holder.cardItem.setBackgroundResource(if (marcado) R.drawable.bg_card_selecionado else R.drawable.bg_card)

        holder.cardItem.setOnClickListener {
            if (selecionados.contains(cliente.id)) selecionados.remove(cliente.id) else selecionados.add(cliente.id)
            notifyItemChanged(position)
            onToggle(cliente)
        }
    }

    fun selecionarTodos() {
        selecionados.clear()
        selecionados.addAll(currentList.map { it.id })
        notifyDataSetChanged()
    }

    fun desmarcarTodos() {
        selecionados.clear()
        notifyDataSetChanged()
    }

    fun getClientesSelecionados(): List<ClienteEntity> =
        currentList.filter { selecionados.contains(it.id) }

    fun quantidadeSelecionada(): Int = selecionados.size

    private fun formatarTelefone(numero: String): String {
        val d = numero.filter { it.isDigit() }
        return when (d.length) {
            13 -> "+${d.substring(0, 2)} ${d.substring(2, 4)} ${d.substring(4, 9)}-${d.substring(9)}"
            12 -> "+${d.substring(0, 2)} ${d.substring(2, 4)} ${d.substring(4, 8)}-${d.substring(8)}"
            11 -> "${d.substring(0, 2)} ${d.substring(2, 7)}-${d.substring(7)}"
            10 -> "${d.substring(0, 2)} ${d.substring(2, 6)}-${d.substring(6)}"
            else -> numero
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ClienteEntity>() {
            override fun areItemsTheSame(oldItem: ClienteEntity, newItem: ClienteEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ClienteEntity, newItem: ClienteEntity) = oldItem == newItem
        }
    }
}

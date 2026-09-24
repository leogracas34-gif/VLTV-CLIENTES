package com.vltv.clientes.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vltv.clientes.R
import com.vltv.clientes.data.ClienteEntity
import java.text.SimpleDateFormat
import java.util.*

class ClienteAdapter(
    private val onClick: (ClienteEntity) -> Unit,
    private val onMenuClick: (ClienteEntity, View) -> Unit
) : ListAdapter<ClienteEntity, ClienteAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNome: android.widget.TextView = view.findViewById(R.id.tvNomeCliente)
        val tvWhatsapp: android.widget.TextView = view.findViewById(R.id.tvWhatsappCliente)
        val tvBadge: android.widget.TextView = view.findViewById(R.id.tvBadgeStatus)
        val tvUsuario: android.widget.TextView = view.findViewById(R.id.tvUsuarioCliente)
        val tvVencimento: android.widget.TextView = view.findViewById(R.id.tvVencimentoCliente)
        val btnMenu: android.widget.ImageButton = view.findViewById(R.id.btnMenuCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cliente, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cliente = getItem(position)

        holder.tvNome.text = cliente.nome
        holder.tvWhatsapp.text = formatarTelefone(cliente.whatsapp)
        holder.tvUsuario.text = "Login: ${cliente.usuario}"

        val dias = cliente.diasRestantes
        when {
            cliente.ultimoErro != null && dias == null -> {
                holder.tvBadge.text = "Erro"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_vermelho)
                holder.tvVencimento.text = cliente.ultimoErro
            }
            dias == null -> {
                holder.tvBadge.text = "..."
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amarelo)
                holder.tvVencimento.text = "Aguardando primeira checagem"
            }
            dias < 0 -> {
                holder.tvBadge.text = "Vencido"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_vermelho)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            dias <= 3 -> {
                holder.tvBadge.text = if (dias == 1) "1 dia" else "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_laranja)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            dias <= 10 -> {
                holder.tvBadge.text = "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amarelo)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            else -> {
                holder.tvBadge.text = "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_verde)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
        }

        holder.itemView.setOnClickListener { onClick(cliente) }
        holder.btnMenu.setOnClickListener { onMenuClick(cliente, it) }
    }

    private fun formatarTelefone(numero: String): String {
        // Formatação simples "+55 31 99999-8888" a partir de dígitos puros.
        val d = numero.filter { it.isDigit() }
        return if (d.length >= 12) {
            val ddi = d.substring(0, d.length - 10)
            val ddd = d.substring(d.length - 10, d.length - 8)
            val parte1 = d.substring(d.length - 8, d.length - 4)
            val parte2 = d.substring(d.length - 4)
            "+$ddi $ddd $parte1-$parte2"
        } else numero
    }

    private fun formatarDataVencimento(expUnix: Long?): String {
        if (expUnix == null) return ""
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return "Vence em ${sdf.format(Date(expUnix * 1000L))}"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ClienteEntity>() {
            override fun areItemsTheSame(oldItem: ClienteEntity, newItem: ClienteEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ClienteEntity, newItem: ClienteEntity) = oldItem == newItem
        }
    }
}

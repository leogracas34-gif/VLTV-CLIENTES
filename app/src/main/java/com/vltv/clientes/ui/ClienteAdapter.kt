package com.vltv.clientes.ui

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
    private val onMenuClick: (ClienteEntity, View) -> Unit,
    private val onWhatsappClick: (ClienteEntity) -> Unit
) : ListAdapter<ClienteEntity, ClienteAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: android.widget.TextView = view.findViewById(R.id.tvAvatarInicial)
        val tvNome: android.widget.TextView = view.findViewById(R.id.tvNomeCliente)
        val tvWhatsapp: android.widget.TextView = view.findViewById(R.id.tvWhatsappCliente)
        val tvBadge: android.widget.TextView = view.findViewById(R.id.tvBadgeStatus)
        val tvUsuario: android.widget.TextView = view.findViewById(R.id.tvUsuarioCliente)
        val tvVencimento: android.widget.TextView = view.findViewById(R.id.tvVencimentoCliente)
        val btnWhatsapp: android.widget.ImageButton = view.findViewById(R.id.btnWhatsappCliente)
        val btnMenu: android.widget.ImageButton = view.findViewById(R.id.btnMenuCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cliente, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cliente = getItem(position)

        holder.tvAvatar.text = inicial(cliente.nome)
        holder.tvNome.text = cliente.nome
        holder.tvWhatsapp.text = formatarTelefone(cliente.whatsapp)
        holder.tvUsuario.text = "Login: ${cliente.usuario}"

        val dias = cliente.diasRestantes
        when {
            // ✅ CORRIGIDO: antes, "dns em branco" era checado ANTES de "dias
            // restantes conhecidos", então um cliente que já tinha um
            // vencimento sabido (ex: expirou e a conta some do servidor,
            // fazendo a resincronização falhar e zerar o dns de novo) ficava
            // preso em "Pendente" pra sempre, escondendo que ele já estava
            // "Vencido". Agora, se já sabemos os dias restantes (mesmo que
            // a sincronização mais recente tenha falhado), o status por data
            // manda - "Pendente"/"Erro" só aparecem quando NUNCA descobrimos
            // o vencimento desse cliente.
            dias != null && dias < 0 -> {
                holder.tvBadge.text = "Vencido"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_vermelho)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            dias != null && dias <= 3 -> {
                holder.tvBadge.text = if (dias == 1) "1 dia" else "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_laranja)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            dias != null && dias <= 10 -> {
                holder.tvBadge.text = "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amarelo)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            dias != null -> {
                holder.tvBadge.text = "$dias dias"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_verde)
                holder.tvVencimento.text = formatarDataVencimento(cliente.expDateUnix)
            }
            // A partir daqui, dias == null: nunca conseguimos descobrir o
            // vencimento desse cliente ainda.
            cliente.dns.isBlank() && cliente.ultimoErro == null -> {
                // Acabou de ser salvo (offline, VPS fora do ar, ou aguardando
                // a primeira tentativa) - ainda não tentou nem falhou.
                holder.tvBadge.text = "Pendente"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_azul)
                holder.tvVencimento.text = "Aguardando sincronização"
            }
            cliente.ultimoErro != null -> {
                holder.tvBadge.text = "Erro"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_vermelho)
                holder.tvVencimento.text = cliente.ultimoErro
            }
            else -> {
                holder.tvBadge.text = "..."
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amarelo)
                holder.tvVencimento.text = "Aguardando primeira checagem"
            }
        }

        holder.itemView.setOnClickListener { onClick(cliente) }
        holder.btnMenu.setOnClickListener { onMenuClick(cliente, it) }
        holder.btnWhatsapp.setOnClickListener { onWhatsappClick(cliente) }
    }

    private fun inicial(nome: String): String =
        nome.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    // ✅ CORRIGIDO: a versão anterior assumia sempre 8 dígitos de número local
    // (d.length - 10 pro DDI), mas celular brasileiro tem 9 dígitos locais
    // (com o "9" na frente) - um número de 13 dígitos totais (55+DDD+9
    // dígitos) ficava com o DDI "553" e o DDD "19" em vez de "55"/"31".
    // Agora trata explicitamente os tamanhos possíveis de número brasileiro
    // (com ou sem o "55" na frente, com ou sem o 9º dígito).
    private fun formatarTelefone(numero: String): String {
        val d = numero.filter { it.isDigit() }
        return when (d.length) {
            13 -> { // 55 + DDD(2) + 9 dígitos locais (celular padrão atual)
                val ddi = d.substring(0, 2)
                val ddd = d.substring(2, 4)
                val parte1 = d.substring(4, 9)
                val parte2 = d.substring(9)
                "+$ddi $ddd $parte1-$parte2"
            }
            12 -> { // 55 + DDD(2) + 8 dígitos locais (fixo, ou celular sem o 9)
                val ddi = d.substring(0, 2)
                val ddd = d.substring(2, 4)
                val parte1 = d.substring(4, 8)
                val parte2 = d.substring(8)
                "+$ddi $ddd $parte1-$parte2"
            }
            11 -> { // sem DDI: DDD(2) + 9 dígitos locais
                val ddd = d.substring(0, 2)
                val parte1 = d.substring(2, 7)
                val parte2 = d.substring(7)
                "$ddd $parte1-$parte2"
            }
            10 -> { // sem DDI: DDD(2) + 8 dígitos locais
                val ddd = d.substring(0, 2)
                val parte1 = d.substring(2, 6)
                val parte2 = d.substring(6)
                "$ddd $parte1-$parte2"
            }
            else -> numero
        }
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

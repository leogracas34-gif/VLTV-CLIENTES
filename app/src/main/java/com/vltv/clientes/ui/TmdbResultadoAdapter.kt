package com.vltv.clientes.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.vltv.clientes.R
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.TmdbResultado
import kotlinx.coroutines.launch

// Lista horizontal dos resultados da busca no TMDB - cada card mostra a
// miniatura do pôster (baixada sob demanda, sem biblioteca de imagem) e ao
// tocar dispara a escolha desse título pro banner.
class TmdbResultadoAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onEscolher: (TmdbResultado) -> Unit
) : RecyclerView.Adapter<TmdbResultadoAdapter.VH>() {

    private var itens: List<TmdbResultado> = emptyList()

    fun submitList(novaLista: List<TmdbResultado>) {
        itens = novaLista
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: android.widget.ImageView = view.findViewById(R.id.ivPoster)
        val tvTitulo: android.widget.TextView = view.findViewById(R.id.tvTituloResultado)
        val tvAno: android.widget.TextView = view.findViewById(R.id.tvAnoResultado)
        val card: View = view.findViewById(R.id.cardResultado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tmdb_resultado, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = itens[position]
        holder.tvTitulo.text = item.titulo
        holder.tvAno.text = if (item.tipo == "filme") "Filme · ${item.ano}" else "Série · ${item.ano}"
        holder.ivPoster.setImageDrawable(null)

        scope.launch {
            val bitmap = BackendApi.baixarBitmap(item.thumbUrl)
            // Confere se essa ViewHolder ainda representa o mesmo item (evita
            // imagem errada aparecendo se a lista já rolou/reciclou a view).
            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION && itens.getOrNull(holder.bindingAdapterPosition)?.id == item.id) {
                holder.ivPoster.setImageBitmap(bitmap)
            }
        }

        holder.card.setOnClickListener { onEscolher(item) }
    }

    override fun getItemCount(): Int = itens.size
}

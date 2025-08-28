package com.inovatickets.validador.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inovatickets.validador.R
import com.inovatickets.validador.databinding.ItemEventoBinding
import com.inovatickets.validador.data.database.entities.Evento
import java.text.SimpleDateFormat
import java.util.*

class EventoAdapter(
    private val onEventoClick: (Evento) -> Unit
) : ListAdapter<Evento, EventoAdapter.EventoViewHolder>(EventoDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventoViewHolder {
        val binding = ItemEventoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: EventoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class EventoViewHolder(
        private val binding: ItemEventoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        fun bind(evento: Evento) {
            binding.apply {
                tvNomeEvento.text = evento.nome
                tvDataEvento.text = evento.data ?: ""
                
                // Status do evento
                tvStatusEvento.text = if (evento.status == "ativo") "Ativo" else "Inativo"
                tvStatusEvento.setBackgroundResource(
                    if (evento.status == "ativo") R.drawable.status_background else R.drawable.status_background
                )
                
                // Click listeners
                root.setOnClickListener { onEventoClick(evento) }
            }
        }
    }
    
    private class EventoDiffCallback : DiffUtil.ItemCallback<Evento>() {
        override fun areItemsTheSame(oldItem: Evento, newItem: Evento): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Evento, newItem: Evento): Boolean {
            return oldItem == newItem
        }
    }
}
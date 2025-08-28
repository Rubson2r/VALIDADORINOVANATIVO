package com.inovatickets.validador.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inovatickets.validador.R
import com.inovatickets.validador.databinding.ItemLogBinding
import com.inovatickets.validador.data.database.entities.LogEntry
import com.inovatickets.validador.data.database.entities.TipoLog
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class LogViewHolder(
        private val binding: ItemLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        
        fun bind(log: LogEntry) {
            binding.apply {
                textAcaoLog.text = log.acao
                textDetalhesLog.text = log.detalhes ?: "Sem detalhes"
                textDataLog.text = dateTimeFormat.format(log.timestamp)
                textUsuarioLog.text = log.usuario ?: "Sistema"
                
                // Configurar cor e ícone baseado no tipo
                when (log.tipo) {
                    TipoLog.SUCCESS -> {
                        iconTipoLog.setImageResource(R.drawable.ic_check_circle)
                        iconTipoLog.setColorFilter(ContextCompat.getColor(root.context, R.color.success))
                        textTipoLog.text = "SUCESSO"
                        textTipoLog.setTextColor(ContextCompat.getColor(root.context, R.color.success))
                    }
                    TipoLog.ERROR -> {
                        iconTipoLog.setImageResource(R.drawable.ic_error)
                        iconTipoLog.setColorFilter(ContextCompat.getColor(root.context, R.color.error))
                        textTipoLog.text = "ERRO"
                        textTipoLog.setTextColor(ContextCompat.getColor(root.context, R.color.error))
                    }
                    TipoLog.WARNING -> {
                        iconTipoLog.setImageResource(R.drawable.ic_warning)
                        iconTipoLog.setColorFilter(ContextCompat.getColor(root.context, R.color.orange))
                        textTipoLog.text = "AVISO"
                        textTipoLog.setTextColor(ContextCompat.getColor(root.context, R.color.orange))
                    }
                    TipoLog.INFO -> {
                        iconTipoLog.setImageResource(R.drawable.ic_info)
                        iconTipoLog.setColorFilter(ContextCompat.getColor(root.context, R.color.primary))
                        textTipoLog.text = "INFO"
                        textTipoLog.setTextColor(ContextCompat.getColor(root.context, R.color.primary))
                    }
                }
                
                // Indicador de sincronização
                iconSincronizado.visibility = if (log.sincronizado) {
                    iconSincronizado.setImageResource(R.drawable.ic_cloud_done)
                    iconSincronizado.setColorFilter(ContextCompat.getColor(root.context, R.color.success))
                    android.view.View.VISIBLE
                } else {
                    iconSincronizado.setImageResource(R.drawable.ic_cloud_off)
                    iconSincronizado.setColorFilter(ContextCompat.getColor(root.context, R.color.orange))
                    android.view.View.VISIBLE
                }
            }
        }
    }
    
    private class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
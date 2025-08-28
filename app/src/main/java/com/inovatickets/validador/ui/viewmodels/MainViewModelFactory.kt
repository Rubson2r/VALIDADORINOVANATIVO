package com.inovatickets.validador.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.inovatickets.validador.data.repository.EventoRepository
import com.inovatickets.validador.data.repository.SyncRepository

class MainViewModelFactory(
    private val eventoRepository: EventoRepository,
    private val syncRepository: SyncRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(eventoRepository, syncRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
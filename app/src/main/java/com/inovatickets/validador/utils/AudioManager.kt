package com.inovatickets.validador.utils

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import com.inovatickets.validador.R

class AudioManager(private val context: Context) {
    
    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0
    private var isLoaded = false
    
    init {
        initializeSoundPool()
    }
    
    private fun initializeSoundPool() {
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .build()
            
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isLoaded = true
            }
        }
        
        // Carregar sons dos recursos
        successSoundId = soundPool?.load(context, R.raw.success_beep, 1) ?: 0
        errorSoundId = soundPool?.load(context, R.raw.error_beep, 1) ?: 0
    }
    
    fun playSuccessSound() {
        if (isLoaded && soundPool != null) {
            soundPool?.play(successSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }
    
    fun playErrorSound() {
        if (isLoaded && soundPool != null) {
            soundPool?.play(errorSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
package com.example.oyebajaya

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Maneja el sonido y la vibración de la alarma.
 *
 * - El sonido es el tono de alarma que el usuario ya tiene configurado en
 *   Ajustes > Sonido > Alarma predeterminada (RingtoneManager.TYPE_ALARM),
 *   no un archivo propio de la app.
 * - El MediaPlayer se configura con USAGE_ALARM y nunca se llama a
 *   setVolume(): el volumen lo controla el slider "Alarmas" del sistema,
 *   igual que cualquier despertador nativo.
 */
class AlarmSoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    fun startAlarm() {
        stopAlarm()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        pedirEnfoqueAudio(audioAttributes)

        val sonidoAlarma = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getValidRingtoneUri(context)

        if (sonidoAlarma == null) {
            // Caso extremo: el dispositivo no tiene ningún tono disponible.
            // Al menos la vibración sigue funcionando.
            vibrar()
            return
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(context, sonidoAlarma)
            isLooping = true
            setOnPreparedListener { it.start() }
            prepareAsync()
        }

        vibrar()
    }

    fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        detenerVibracion()

        focusRequest?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }
        focusRequest = null
    }

    /** Útil para avisar en la UI si el usuario tiene el volumen de alarmas en 0. */
    fun volumenDeAlarmaEnCero(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    private fun pedirEnfoqueAudio(audioAttributes: AudioAttributes) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .build()
        audioManager.requestAudioFocus(focusRequest!!)
    }

    private fun vibrar() {
        val patron = longArrayOf(0, 800, 400, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(patron, 0))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(patron, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(patron, 0)
            }
        }
    }

    private fun detenerVibracion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.cancel()
        }
    }
}

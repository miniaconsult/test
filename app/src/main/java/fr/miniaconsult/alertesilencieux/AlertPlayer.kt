package fr.miniaconsult.alertesilencieux

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Joue les alertes sur le canal "alarme" : il reste audible en mode silencieux ou vibreur.
 * Si un volume propre aux alertes est réglé, le volume Alarme est monté le temps de l'alerte
 * puis remis exactement comme avant. La vibration utilise elle aussi le type "alarme",
 * qui n'est pas bloqué par le mode silencieux.
 */
class AlertPlayer(private val context: Context) {
    enum class Kind { MESSAGE, PHONE_CALL, APP_CALL }

    private val prefs = Prefs(context)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private val timeout = Runnable { stop() }

    var playing: Kind? = null
        private set

    fun play(kind: Kind, maxDurationMs: Long) {
        stop()
        val isCall = kind != Kind.MESSAGE
        val sound = if (isCall) prefs.callSound else prefs.messageSound
        val uri = sound ?: if (isCall) Settings.System.DEFAULT_RINGTONE_URI else Settings.System.DEFAULT_NOTIFICATION_URI

        if (prefs.customVolume) applyVolume(if (isCall) prefs.callVolume else prefs.messageVolume)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, uri)
                isLooping = isCall
                // Pour un message : on laisse finir la vibration avant de tout remettre en place.
                setOnCompletionListener { if (!isCall) handler.postDelayed({ stop(Kind.MESSAGE) }, 1_000) }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Son introuvable ou illisible : bip de secours sur le canal alarme.
            player?.release()
            player = null
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100).apply {
                startTone(if (isCall) ToneGenerator.TONE_SUP_RINGTONE else ToneGenerator.TONE_PROP_BEEP2)
            }
        }
        if (if (isCall) prefs.callVibrate else prefs.messageVibrate) vibrate(isCall)
        playing = kind
        handler.postDelayed(timeout, maxDurationMs)
    }

    fun stop(kind: Kind) {
        if (playing == kind) stop()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
        tone?.run {
            stopTone()
            release()
        }
        tone = null
        if (playing != null) vibrator.cancel()
        playing = null
        restoreVolume()
    }

    /** Monte (ou baisse) le volume Alarme au niveau voulu, en mémorisant l'ancien. */
    private fun applyVolume(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0
        val target = (max * percent / 100.0).toInt().coerceIn(min, max)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (target == current) return
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            // Mémorisé sur le disque : si l'appli est arrêtée en pleine alerte, le volume est remis au redémarrage.
            prefs.savedAlarmVolume = current
            prefs.appliedAlarmVolume = target
        } catch (e: SecurityException) {
            EventLog.add(context, "Volume des alertes non appliqué : ${e.message}")
        }
    }

    /** Remet le volume Alarme d'avant l'alerte (sauf si vous l'avez changé vous-même entre-temps). */
    fun restoreVolume() {
        val saved = prefs.savedAlarmVolume
        if (saved < 0) return
        try {
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == prefs.appliedAlarmVolume) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
            }
        } catch (_: SecurityException) {
        }
        prefs.savedAlarmVolume = -1
        prefs.appliedAlarmVolume = -1
    }

    private fun vibrate(isCall: Boolean) {
        if (!vibrator.hasVibrator()) return
        val effect = if (isCall) {
            VibrationEffect.createWaveform(longArrayOf(0, 1000, 800), 0) // en boucle jusqu'à l'arrêt
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }
}

package fr.miniaconsult.alertesilencieux

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Joue les alertes sur le canal "alarme" : il reste audible en mode silencieux ou vibreur,
 * sans modifier les réglages de sonnerie du téléphone. Le volume utilisé est celui des alarmes.
 */
class AlertPlayer(private val context: Context) {
    enum class Kind { MESSAGE, PHONE_CALL, APP_CALL }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private val timeout = Runnable { stop() }

    var playing: Kind? = null
        private set

    fun play(kind: Kind, sound: Uri?, maxDurationMs: Long) {
        stop()
        val loop = kind != Kind.MESSAGE
        val uri = sound ?: if (loop) Settings.System.DEFAULT_RINGTONE_URI else Settings.System.DEFAULT_NOTIFICATION_URI
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, uri)
                isLooping = loop
                setOnCompletionListener { if (!loop) stop() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Son introuvable ou illisible : bip de secours sur le canal alarme.
            player?.release()
            player = null
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100).apply {
                startTone(if (loop) ToneGenerator.TONE_SUP_RINGTONE else ToneGenerator.TONE_PROP_BEEP2)
            }
        }
        playing = kind
        handler.postDelayed(timeout, maxDurationMs)
    }

    fun stop(kind: Kind) {
        if (playing == kind) stop()
    }

    fun stop() {
        handler.removeCallbacks(timeout)
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
        playing = null
    }
}

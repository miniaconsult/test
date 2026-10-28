package fr.miniaconsult.alertesilencieux

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

object SoundState {
    /** Vrai si la sonnerie est en mode normal avec un volume non nul sur ce flux. */
    fun ringerAudible(context: Context, stream: Int): Boolean {
        val am = context.getSystemService(AudioManager::class.java)
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL && am.getStreamVolume(stream) > 0
    }

    /** Vrai si "Ne pas déranger" (ou un mode équivalent) filtre les interruptions. */
    fun doNotDisturbActive(context: Context): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Vrai pendant un appel téléphonique ou un appel Teams / visio. */
    fun inCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    fun alarmVolume(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM)
}

package fr.miniaconsult.alertesilencieux

import android.Manifest
import android.app.Notification
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager

/**
 * Service principal. Android le garde actif tant que l'accès aux notifications est accordé.
 * - Lit les notifications de Google Messages et de Teams.
 * - Écoute l'état du téléphone pour les appels classiques (Téléphone Google).
 * Une alerte n'est jouée que si le son du téléphone n'est pas déjà actif.
 */
class AlertListenerService : NotificationListenerService() {

    private lateinit var prefs: Prefs
    private lateinit var player: AlertPlayer
    private val handler = Handler(Looper.getMainLooper())

    private var receiverRegistered = false
    private var callDecided = false
    private var teamsCallKey: String? = null
    private val lastMessageTime = HashMap<String, Long>()
    private var lastMessageAlertAt = 0L

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onPhoneState(intent)
    }

    // Si le numéro n'arrive pas (numéro masqué), on décide quand même après un court délai.
    private val numberFallback = Runnable { if (!callDecided) decidePhoneCall(null, numberKnown = false) }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled" && !prefs.enabled) player.stop()
    }
    private lateinit var settings: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        player = AlertPlayer(this)
        settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onListenerConnected() {
        if (!receiverRegistered) {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(phoneReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(phoneReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        cleanup()
    }

    override fun onDestroy() {
        cleanup()
        settings.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    private fun cleanup() {
        if (receiverRegistered) {
            unregisterReceiver(phoneReceiver)
            receiverRegistered = false
        }
        handler.removeCallbacks(numberFallback)
        player.stop()
    }

    // ---------- Appels téléphoniques (Téléphone Google) ----------

    private fun onPhoneState(intent: Intent) {
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (callDecided) return
                handler.removeCallbacks(numberFallback)
                // Android envoie l'événement deux fois : sans numéro, puis avec (grâce au journal d'appels).
                @Suppress("DEPRECATION")
                if (intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                    decidePhoneCall(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), numberKnown = true)
                } else {
                    handler.postDelayed(numberFallback, 1500)
                }
            }
            else -> {
                // Décroché ou raccroché : on arrête l'alerte.
                handler.removeCallbacks(numberFallback)
                callDecided = false
                player.stop(AlertPlayer.Kind.PHONE_CALL)
            }
        }
    }

    private fun decidePhoneCall(number: String?, numberKnown: Boolean) {
        callDecided = true
        val who = if (number.isNullOrBlank()) "numéro masqué" else number
        if (!prefs.enabled || !prefs.phoneCalls) return
        if (SoundState.ringerAudible(this, AudioManager.STREAM_RING) && !SoundState.doNotDisturbActive(this)) {
            EventLog.add(this, "Appel de $who : son déjà actif, rien à faire")
            return
        }
        val hasCallLog = checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val allowed = when {
            numberKnown && !number.isNullOrBlank() -> Matching.allows(prefs, emptyList(), listOf(number))
            numberKnown || hasCallLog -> prefs.alertPrivateNumbers
            // Sans l'autorisation "journal d'appels", le numéro est inconnu.
            else -> !prefs.onlyListed
        }
        if (!allowed) {
            EventLog.add(this, "Appel de $who : ignoré (filtre des personnes)")
            return
        }
        EventLog.add(this, "Appel de $who : ALERTE")
        player.play(AlertPlayer.Kind.PHONE_CALL, prefs.callSound, CALL_MAX_MS)
    }

    // ---------- Notifications (Google Messages, Teams) ----------

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        when (sbn.packageName) {
            PKG_MESSAGES -> if (n.category == Notification.CATEGORY_MESSAGE) {
                handleMessage(sbn, rankingMap, "SMS", prefs.smsMessages)
            }
            PKG_TEAMS -> if (n.category == Notification.CATEGORY_CALL) {
                handleTeamsCall(sbn, rankingMap)
            } else if (n.category == Notification.CATEGORY_MESSAGE || n.extras.containsKey(Notification.EXTRA_MESSAGES)) {
                handleMessage(sbn, rankingMap, "Teams", prefs.teamsMessages)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastMessageTime.remove(sbn.key)
        if (sbn.key == teamsCallKey) {
            teamsCallKey = null
            player.stop(AlertPlayer.Kind.TEAMS_CALL)
        }
    }

    private fun handleMessage(sbn: StatusBarNotification, rankingMap: RankingMap, source: String, sourceEnabled: Boolean) {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        // Une même conversation met à jour la même notification : on n'alerte que pour un message plus récent.
        val latest = latestMessage(n.extras)
        val time = latest?.getLong("time")?.takeIf { it > 0 } ?: n.`when`.takeIf { it > 0 } ?: sbn.postTime
        val previous = lastMessageTime[sbn.key]
        lastMessageTime[sbn.key] = maxOf(time, previous ?: 0)
        if (previous != null && time <= previous) return
        // Message envoyé par moi-même (réponse depuis la notification) : pas d'expéditeur.
        if (latest != null && latest.getCharSequence("sender") == null && senderPerson(latest) == null) return

        if (!prefs.enabled || !sourceEnabled) return

        val names = senderNames(n.extras, latest)
        val numbers = senderNumbers(n.extras, latest) + names
        val who = names.firstOrNull() ?: "?"

        if (SoundState.inCall(this)) {
            EventLog.add(this, "$source de $who : ignoré (appel en cours)")
            return
        }
        if (notificationAudible(sbn, rankingMap)) {
            EventLog.add(this, "$source de $who : son déjà actif, rien à faire")
            return
        }
        if (!Matching.allows(prefs, names, numbers)) {
            EventLog.add(this, "$source de $who : ignoré (filtre des personnes)")
            return
        }
        if (player.playing == AlertPlayer.Kind.PHONE_CALL || player.playing == AlertPlayer.Kind.TEAMS_CALL) return
        val now = System.currentTimeMillis()
        if (now - lastMessageAlertAt < MESSAGE_COOLDOWN_MS) return
        lastMessageAlertAt = now

        EventLog.add(this, "$source de $who : ALERTE")
        player.play(AlertPlayer.Kind.MESSAGE, prefs.messageSound, MESSAGE_MAX_MS)
    }

    private fun handleTeamsCall(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val n = sbn.notification
        val callType = n.extras.getInt(Notification.EXTRA_CALL_TYPE, CALL_TYPE_UNKNOWN)
        val incoming = when (callType) {
            Notification.CallStyle.CALL_TYPE_INCOMING -> true
            CALL_TYPE_UNKNOWN -> n.fullScreenIntent != null
            else -> false
        }
        if (!incoming) {
            // Appel décroché (devenu "en cours") : on arrête l'alerte.
            if (sbn.key == teamsCallKey) {
                teamsCallKey = null
                player.stop(AlertPlayer.Kind.TEAMS_CALL)
            }
            return
        }
        if (sbn.key == teamsCallKey) return
        teamsCallKey = sbn.key

        if (!prefs.enabled || !prefs.teamsCalls) return
        val names = senderNames(n.extras, null)
        val who = names.firstOrNull() ?: "?"
        if (SoundState.ringerAudible(this, AudioManager.STREAM_RING) && matchesInterruptionFilter(sbn, rankingMap)) {
            EventLog.add(this, "Appel Teams de $who : son déjà actif, rien à faire")
            return
        }
        if (!Matching.allows(prefs, names, emptyList())) {
            EventLog.add(this, "Appel Teams de $who : ignoré (filtre des personnes)")
            return
        }
        EventLog.add(this, "Appel Teams de $who : ALERTE")
        player.play(AlertPlayer.Kind.TEAMS_CALL, prefs.callSound, CALL_MAX_MS)
    }

    // ---------- Outils ----------

    private fun notificationAudible(sbn: StatusBarNotification, rankingMap: RankingMap): Boolean =
        SoundState.ringerAudible(this, AudioManager.STREAM_NOTIFICATION) && matchesInterruptionFilter(sbn, rankingMap)

    /** Faux si "Ne pas déranger" bloque cette notification précise. */
    private fun matchesInterruptionFilter(sbn: StatusBarNotification, rankingMap: RankingMap): Boolean {
        val ranking = Ranking()
        return if (rankingMap.getRanking(sbn.key, ranking)) ranking.matchesInterruptionFilter() else true
    }

    private fun latestMessage(extras: Bundle): Bundle? {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        return messages.filterIsInstance<Bundle>().maxByOrNull { it.getLong("time") }
    }

    @Suppress("DEPRECATION")
    private fun senderPerson(message: Bundle): Person? = message.getParcelable("sender_person")

    private fun people(extras: Bundle): List<Person> {
        @Suppress("DEPRECATION")
        val list = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST) ?: arrayListOf()
        @Suppress("DEPRECATION")
        val caller = extras.getParcelable<Person>(Notification.EXTRA_CALL_PERSON)
        return list + listOfNotNull(caller)
    }

    private fun senderNames(extras: Bundle, latest: Bundle?): List<CharSequence> = listOfNotNull(
        latest?.getCharSequence("sender"),
        latest?.let { senderPerson(it)?.name },
        extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
        extras.getCharSequence(Notification.EXTRA_TITLE),
    ).plus(people(extras).mapNotNull { it.name }).filter { it.isNotBlank() }.distinctBy { it.toString() }

    private fun senderNumbers(extras: Bundle, latest: Bundle?): List<CharSequence> =
        (people(extras) + listOfNotNull(latest?.let { senderPerson(it) }))
            .mapNotNull { it.uri }
            .filter { it.startsWith("tel:") }
            .map { it.removePrefix("tel:") }

    companion object {
        const val PKG_MESSAGES = "com.google.android.apps.messaging"
        const val PKG_TEAMS = "com.microsoft.teams"
        private const val CALL_TYPE_UNKNOWN = -1
        private const val CALL_MAX_MS = 60_000L
        private const val MESSAGE_MAX_MS = 15_000L
        private const val MESSAGE_COOLDOWN_MS = 3_000L
    }
}

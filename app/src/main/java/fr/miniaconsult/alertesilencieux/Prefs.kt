package fr.miniaconsult.alertesilencieux

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Une personne de la liste : un nom (pour Teams / Messages) et des numéros (pour les appels / SMS). */
data class Person(val name: String, val numbers: List<String>)

/**
 * Une application surveillée. [calls] / [messages] valent null quand le type ne s'applique pas
 * (ex. pas de messages pour l'application Téléphone).
 */
data class WatchedApp(val pkg: String, val label: String, val calls: Boolean?, val messages: Boolean?, val builtIn: Boolean)

enum class AlertType { CALL, MESSAGE }

/** Réglages de l'application, stockés localement sur le téléphone. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Interrupteur général (aussi piloté par la tuile « Alerte son »). */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    /** Pause temporaire : heure (ms) jusqu'à laquelle aucune alerte n'est jouée. 0 = pas de pause. */
    var pausedUntil: Long
        get() = sp.getLong("paused_until", 0)
        set(value) = sp.edit().putLong("paused_until", value).apply()

    /** Interrupteurs généraux par type (tuiles « Alerte appels » / « Alerte messages »). */
    var callsEnabled: Boolean
        get() = sp.getBoolean("calls_enabled", true)
        set(value) = sp.edit().putBoolean("calls_enabled", value).apply()

    var messagesEnabled: Boolean
        get() = sp.getBoolean("messages_enabled", true)
        set(value) = sp.edit().putBoolean("messages_enabled", value).apply()

    fun isPaused(now: Long = System.currentTimeMillis()) = pausedUntil > now

    /** Vrai si l'application est activée et pas en pause. */
    fun isActive(now: Long = System.currentTimeMillis()) = enabled && !isPaused(now)

    fun typeEnabled(type: AlertType) = if (type == AlertType.CALL) callsEnabled else messagesEnabled

    fun setTypeEnabled(type: AlertType, value: Boolean) {
        if (type == AlertType.CALL) callsEnabled = value else messagesEnabled = value
    }

    /** La règle complète : général, pause, type, puis application. */
    fun allows(type: AlertType, pkg: String): Boolean {
        if (!isActive() || !typeEnabled(type)) return false
        val app = apps.firstOrNull { it.pkg == pkg } ?: return false
        return (if (type == AlertType.CALL) app.calls else app.messages) == true
    }

    var apps: List<WatchedApp>
        get() {
            val raw = sp.getString("apps", null) ?: return defaultApps()
            val array = JSONArray(raw)
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                WatchedApp(
                    pkg = o.getString("pkg"),
                    label = o.getString("label"),
                    calls = if (o.has("calls")) o.getBoolean("calls") else null,
                    messages = if (o.has("messages")) o.getBoolean("messages") else null,
                    builtIn = o.optBoolean("builtIn"),
                )
            }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { a ->
                val o = JSONObject().put("pkg", a.pkg).put("label", a.label).put("builtIn", a.builtIn)
                a.calls?.let { o.put("calls", it) }
                a.messages?.let { o.put("messages", it) }
                array.put(o)
            }
            sp.edit().putString("apps", array.toString()).apply()
        }

    // Reprend les réglages de la première version s'ils existent.
    private fun defaultApps() = listOf(
        WatchedApp(PKG_PHONE, "Téléphone", calls = sp.getBoolean("phone_calls", true), messages = null, builtIn = true),
        WatchedApp(PKG_MESSAGES, "Google Messages", calls = null, messages = sp.getBoolean("sms_messages", true), builtIn = true),
        WatchedApp(
            PKG_TEAMS, "Teams",
            calls = sp.getBoolean("teams_calls", true), messages = sp.getBoolean("teams_messages", true), builtIn = true,
        ),
    )

    fun updateApp(pkg: String, transform: (WatchedApp) -> WatchedApp) {
        apps = apps.map { if (it.pkg == pkg) transform(it) else it }
    }

    fun addApp(pkg: String, label: String) {
        if (apps.none { it.pkg == pkg }) apps = apps + WatchedApp(pkg, label, calls = true, messages = true, builtIn = false)
    }

    fun removeApp(pkg: String) {
        apps = apps.filterNot { it.pkg == pkg && !it.builtIn }
    }

    /** Coche (ou décoche) le type donné dans toutes les applications ; null = les deux types. */
    fun setAllApps(type: AlertType?, value: Boolean) {
        apps = apps.map {
            it.copy(
                calls = if (type != AlertType.MESSAGE) it.calls?.let { value } else it.calls,
                messages = if (type != AlertType.CALL) it.messages?.let { value } else it.messages,
            )
        }
    }

    /** false : tout le monde sauf la liste. true : uniquement les personnes de la liste. */
    var onlyListed: Boolean
        get() = sp.getBoolean("only_listed", false)
        set(value) = sp.edit().putBoolean("only_listed", value).apply()

    /** Alerter pour les numéros masqués / privés / inconnus. */
    var alertPrivateNumbers: Boolean
        get() = sp.getBoolean("alert_private", false)
        set(value) = sp.edit().putBoolean("alert_private", value).apply()

    var messageSound: Uri?
        get() = sp.getString("message_sound", null)?.let(Uri::parse)
        set(value) = sp.edit().putString("message_sound", value?.toString()).apply()

    var callSound: Uri?
        get() = sp.getString("call_sound", null)?.let(Uri::parse)
        set(value) = sp.edit().putString("call_sound", value?.toString()).apply()

    /** Vrai : volume propre aux alertes. Faux : on joue au volume Alarme actuel, sans y toucher. */
    var customVolume: Boolean
        get() = sp.getBoolean("custom_volume", true)
        set(value) = sp.edit().putBoolean("custom_volume", value).apply()

    /** Volume des alertes en % du maximum (appliqué au volume Alarme le temps de l'alerte). */
    var callVolume: Int
        get() = sp.getInt("call_volume", 80)
        set(value) = sp.edit().putInt("call_volume", value).apply()

    var messageVolume: Int
        get() = sp.getInt("message_volume", 60)
        set(value) = sp.edit().putInt("message_volume", value).apply()

    var callVibrate: Boolean
        get() = sp.getBoolean("call_vibrate", true)
        set(value) = sp.edit().putBoolean("call_vibrate", value).apply()

    var messageVibrate: Boolean
        get() = sp.getBoolean("message_vibrate", true)
        set(value) = sp.edit().putBoolean("message_vibrate", value).apply()

    /** Volume Alarme d'avant l'alerte, à remettre ensuite (-1 = rien à remettre). */
    var savedAlarmVolume: Int
        get() = sp.getInt("saved_alarm_volume", -1)
        set(value) = sp.edit().putInt("saved_alarm_volume", value).commit().let { }

    /** Volume Alarme mis par l'appli pendant l'alerte. */
    var appliedAlarmVolume: Int
        get() = sp.getInt("applied_alarm_volume", -1)
        set(value) = sp.edit().putInt("applied_alarm_volume", value).commit().let { }

    var people: List<Person>
        get() {
            val array = JSONArray(sp.getString("people", "[]"))
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val nums = o.optJSONArray("numbers") ?: JSONArray()
                Person(o.getString("name"), (0 until nums.length()).map { nums.getString(it) })
            }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { p ->
                array.put(JSONObject().put("name", p.name).put("numbers", JSONArray(p.numbers)))
            }
            sp.edit().putString("people", array.toString()).apply()
        }

    /** Ajoute une personne, ou complète ses numéros si le nom existe déjà. */
    fun addPerson(name: String, number: String?) {
        val list = people.toMutableList()
        val index = list.indexOfFirst { Matching.normalizeName(it.name) == Matching.normalizeName(name) }
        if (index >= 0) {
            val existing = list[index]
            if (number != null && existing.numbers.none { Matching.sameNumber(it, number) }) {
                list[index] = existing.copy(numbers = existing.numbers + number)
            }
        } else {
            list.add(Person(name, listOfNotNull(number)))
        }
        people = list
    }

    fun removePerson(person: Person) {
        people = people.filterNot { it == person }
    }

    companion object {
        /** Les appels classiques sont détectés via l'état du téléphone, quelle que soit l'appli Téléphone. */
        const val PKG_PHONE = "com.google.android.dialer"
        const val PKG_MESSAGES = "com.google.android.apps.messaging"
        const val PKG_TEAMS = "com.microsoft.teams"
    }
}

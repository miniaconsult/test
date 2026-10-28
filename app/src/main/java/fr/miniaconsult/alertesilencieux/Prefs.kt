package fr.miniaconsult.alertesilencieux

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Une personne de la liste : un nom (pour Teams / Messages) et des numéros (pour les appels / SMS). */
data class Person(val name: String, val numbers: List<String>)

/** Réglages de l'application, stockés localement sur le téléphone. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Interrupteur général (aussi piloté par la tuile des réglages rapides). */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    var phoneCalls: Boolean
        get() = sp.getBoolean("phone_calls", true)
        set(value) = sp.edit().putBoolean("phone_calls", value).apply()

    var smsMessages: Boolean
        get() = sp.getBoolean("sms_messages", true)
        set(value) = sp.edit().putBoolean("sms_messages", value).apply()

    var teamsMessages: Boolean
        get() = sp.getBoolean("teams_messages", true)
        set(value) = sp.edit().putBoolean("teams_messages", value).apply()

    var teamsCalls: Boolean
        get() = sp.getBoolean("teams_calls", true)
        set(value) = sp.edit().putBoolean("teams_calls", value).apply()

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
}

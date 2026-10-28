package fr.miniaconsult.alertesilencieux

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petit journal des derniers événements, affiché dans l'application pour comprendre ce qui s'est passé. */
object EventLog {
    private const val MAX = 40

    fun add(context: Context, message: String) {
        val sp = context.getSharedPreferences("log", Context.MODE_PRIVATE)
        val old = JSONArray(sp.getString("entries", "[]"))
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date())
        val entries = JSONArray().put("$time  $message")
        for (i in 0 until minOf(old.length(), MAX - 1)) entries.put(old.getString(i))
        sp.edit().putString("entries", entries.toString()).apply()
    }

    fun read(context: Context): List<String> {
        val array = JSONArray(context.getSharedPreferences("log", Context.MODE_PRIVATE).getString("entries", "[]"))
        return (0 until array.length()).map { array.getString(it) }
    }

    fun clear(context: Context) {
        context.getSharedPreferences("log", Context.MODE_PRIVATE).edit().clear().apply()
    }
}

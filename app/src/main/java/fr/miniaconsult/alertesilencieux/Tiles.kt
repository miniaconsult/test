package fr.miniaconsult.alertesilencieux

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Tiles {
    /** Met à jour les tuiles affichées après un changement de réglage depuis l'application. */
    fun refreshAll(context: Context) {
        listOf(ToggleTileService::class.java, CallsTileService::class.java, MessagesTileService::class.java).forEach {
            TileService.requestListeningState(context, ComponentName(context, it))
        }
    }

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))
}

/** Tuile « Alerte son » : un appui active ou désactive tout (et lève une pause en cours). */
class ToggleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val prefs = Prefs(this)
        if (prefs.isPaused()) {
            prefs.pausedUntil = 0
            prefs.enabled = true
        } else {
            prefs.enabled = !prefs.enabled
        }
        EventLog.add(this, if (prefs.isActive()) "Application activée (tuile)" else "Application désactivée (tuile)")
        Tiles.refreshAll(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        tile.state = if (prefs.isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !prefs.enabled -> "Désactivée"
            prefs.isPaused() -> "Pause → ${Tiles.time(prefs.pausedUntil)}"
            else -> "Activée"
        }
        tile.updateTile()
    }
}

/** Tuiles « Alerte appels » / « Alerte messages » : activent ou désactivent un type. */
abstract class TypeTileService(private val type: AlertType, private val label: String) : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val prefs = Prefs(this)
        val value = !prefs.typeEnabled(type)
        prefs.setTypeEnabled(type, value)
        // Activer un type depuis la tuile réactive aussi l'application si elle était coupée.
        if (value && !prefs.isActive()) {
            prefs.enabled = true
            prefs.pausedUntil = 0
        }
        EventLog.add(this, "$label ${if (value) "activés" else "désactivés"} (tuile)")
        Tiles.refreshAll(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        val on = prefs.typeEnabled(type)
        tile.state = if (on && prefs.isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !on -> "Désactivés"
            !prefs.enabled -> "Appli coupée"
            prefs.isPaused() -> "Pause → ${Tiles.time(prefs.pausedUntil)}"
            else -> "Activés"
        }
        tile.updateTile()
    }
}

class CallsTileService : TypeTileService(AlertType.CALL, "Appels")

class MessagesTileService : TypeTileService(AlertType.MESSAGE, "Messages")

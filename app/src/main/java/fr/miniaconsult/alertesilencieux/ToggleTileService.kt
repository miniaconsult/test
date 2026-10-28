package fr.miniaconsult.alertesilencieux

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile des réglages rapides : un appui active ou désactive l'application. */
class ToggleTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val prefs = Prefs(this)
        prefs.enabled = !prefs.enabled
        EventLog.add(this, if (prefs.enabled) "Application activée (tuile)" else "Application désactivée (tuile)")
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = Prefs(this).enabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (enabled) "Activée" else "Désactivée"
        tile.updateTile()
    }
}

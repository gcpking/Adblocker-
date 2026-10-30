package com.gabriel.adblocker

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ToggleTileService : TileService() {
    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (BlockerService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.app_name)
        t.subtitle = if (BlockerService.running) "On" else "Off"
        t.updateTile()
    }

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (BlockerService.running) {
            startService(Intent(this, BlockerService::class.java).setAction(BlockerService.ACTION_STOP))
            BlockerService.running = false
            refresh()
        } else {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).putExtra("turn_on", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

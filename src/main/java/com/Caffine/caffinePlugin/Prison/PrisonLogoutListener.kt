package com.Caffine.caffinePlugin.Prison

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import com.Caffine.caffinePlugin.System.Database
import java.util.*

class PrisonLogoutListener(private val database: Database) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (database.isPlayerJailed(player.uniqueId)) {
            val jailInfo = database.getPlayerJailInfo(player.uniqueId)
            if (jailInfo != null) {
                val (isJailed, remainingTime, lastLoginTime) = jailInfo
                val currentTime = System.currentTimeMillis()
                val newRemainingTime = remainingTime - (currentTime - lastLoginTime)
                database.updateJailPlayerStatus(player.uniqueId, player.name, isJailed, newRemainingTime)
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val jailInfo = database.getPlayerJailInfo(player.uniqueId)
        if (jailInfo != null && jailInfo.first) {
            val (isJailed, remainingTime, lastLoginTime) = jailInfo
            val currentTime = System.currentTimeMillis()
            val newRemainingTime = remainingTime - (currentTime - lastLoginTime).coerceAtLeast(0)
            database.updateJailPlayerStatus(player.uniqueId, player.name, isJailed, newRemainingTime)
            if (newRemainingTime > 0) {
                // Teleport player to prison and show remaining time
                val prisonLocation = database.getPosition("prison")
                if (prisonLocation != null) {
                    player.teleport(prisonLocation)
                }
                val remainingMinutes = newRemainingTime / 60000
                val remainingSeconds = (newRemainingTime % 60000) / 1000
                player.sendMessage("You are still in prison. Remaining time: ${remainingMinutes} minutes ${remainingSeconds} seconds")
                // Show boss bar
                PrisonUtils.showBossBar(player, newRemainingTime)
            } else {
                PrisonUtils.releasePlayer(player)
            }
        }
    }
}
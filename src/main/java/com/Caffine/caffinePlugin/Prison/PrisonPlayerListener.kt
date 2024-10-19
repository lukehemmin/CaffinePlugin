package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin


class PrisonPlayerListener(private val plugin: JavaPlugin, private val database: Database) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val jailInfo = database.getPlayerJailInfo(player.uniqueId)

        if (jailInfo != null && jailInfo.first) {
            val (isJailed, remainingTime, lastLoginTime) = jailInfo
            val currentTime = System.currentTimeMillis()
            val newRemainingTime = remainingTime

            database.updatePlayerJailStatus(player.uniqueId, isJailed, newRemainingTime, currentTime)

            if (newRemainingTime > 0) {
                // 플레이어를 감옥으로 텔레포트
                val prisonLocation = database.getPosition("prison")
                if (prisonLocation != null) {
                    player.teleport(prisonLocation)
                }
                player.sendMessage("당신은 아직 감옥에 있습니다. 남은 시간: ${newRemainingTime / 1000 / 60}분")
            } else {
                PrisonUtils.releasePlayer(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val jailInfo = database.getPlayerJailInfo(player.uniqueId)

        if (jailInfo != null && jailInfo.first) {
            val (isJailed, remainingTime, lastLoginTime) = jailInfo
            val currentTime = System.currentTimeMillis()
            val timeSpentOnline = currentTime - lastLoginTime
            val newRemainingTime = remainingTime - timeSpentOnline

            database.updatePlayerJailStatus(player.uniqueId, isJailed, newRemainingTime, currentTime)
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (database.isPlayerJailed(player.uniqueId)) {
            val prisonLocation = database.getPosition("prison")
            if (prisonLocation != null) {
                event.respawnLocation = prisonLocation
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c당신은 여전히 감옥에 있습니다.")
                })
            } else {
                plugin.logger.warning("감옥 위치가 설정되지 않았습니다.")
            }
        }
    }
}

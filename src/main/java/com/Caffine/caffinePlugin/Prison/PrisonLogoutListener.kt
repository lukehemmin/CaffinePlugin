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
            val remainingTime = database.getPlayerJailTime(player.uniqueId)
            database.updatePlayerJailStatus(player.uniqueId, true, remainingTime, System.currentTimeMillis())
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val jailInfo = database.getPlayerJailInfo(player.uniqueId)

        if (jailInfo != null && jailInfo.first) {
            val (isJailed, remainingTime, lastLoginTime) = jailInfo
            val currentTime = System.currentTimeMillis()

            if (remainingTime > 0) {
                // 플레이어를 감옥으로 텔레포트
                val prisonLocation = database.getPosition("prison")
                if (prisonLocation != null) {
                    player.teleport(prisonLocation)
                }

                // 남은 시간 안내
                val remainingMinutes = remainingTime / 60000
                player.sendMessage("당신은 아직 감옥에 있습니다. 남은 시간: ${remainingMinutes}분")

                // 석방 예약
                PrisonUtils.scheduleRelease(player, remainingTime)

                // 감옥 상태 업데이트 (마지막 로그인 시간 갱신)
                database.updatePlayerJailStatus(player.uniqueId, true, remainingTime, currentTime)
            } else {
                // 감옥 시간이 끝났으므로 석방
                PrisonUtils.releasePlayer(player)
            }
        }
    }
}

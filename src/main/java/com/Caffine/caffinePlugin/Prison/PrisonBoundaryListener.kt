package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class PrisonBoundaryListener(private val plugin: JavaPlugin, private val database: Database) : Listener {

    private data class Selection(val locations: MutableList<Location> = mutableListOf(), var lastSelectTime: Long = System.currentTimeMillis())
    private val playerSelections = mutableMapOf<UUID, Selection>()

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        if (item?.type != Material.STICK || item.itemMeta?.displayName != "§e§l감옥 범위 설정 도구") {
            return
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        if (!player.hasPermission("prison.admin.setboundary")) {
            player.sendMessage("§c감옥 범위를 설정할 권한이 없습니다.")
            return
        }

        event.isCancelled = true

        val clickedBlock = event.clickedBlock ?: return
        val location = clickedBlock.location

        val selection = playerSelections.getOrPut(player.uniqueId) { Selection() }
        selection.locations.add(location)
        selection.lastSelectTime = System.currentTimeMillis()

        when (selection.locations.size) {
            1 -> player.sendMessage("§a첫 번째 지점이 선택되었습니다. (${location.blockX}, ${location.blockY}, ${location.blockZ})")
            2 -> {
                player.sendMessage("§a두 번째 지점이 선택되었습니다. (${location.blockX}, ${location.blockY}, ${location.blockZ})")
                if (isValidBoundary(selection.locations)) {
                    savePrisonBoundary(player.uniqueId, selection.locations)
                } else {
                    player.sendMessage("§c선택한 두 지점이 유효한 범위를 형성하지 않습니다. 다시 선택해주세요.")
                }
                playerSelections.remove(player.uniqueId)
            }
        }
    }

    private fun isValidBoundary(locations: List<Location>): Boolean {
        return locations[0].world == locations[1].world &&
                locations[0].distance(locations[1]) >= 5 // 최소 5블록 이상 떨어져 있어야 함
    }

    private fun savePrisonBoundary(playerUUID: UUID, locations: List<Location>) {
        try {
            database.savePosition("prison_boundary_1", locations[0])
            database.savePosition("prison_boundary_2", locations[1])
            plugin.server.getPlayer(playerUUID)?.sendMessage("§a감옥 범위가 성공적으로 설정되었습니다.")
            plugin.logger.info("감옥 범위가 설정되었습니다: ${locations[0]} - ${locations[1]}")
        } catch (e: Exception) {
            plugin.server.getPlayer(playerUUID)?.sendMessage("§c감옥 범위 설정 중 오류가 발생했습니다. 관리자에게 문의하세요.")
            plugin.logger.severe("감옥 범위 설정 중 오류 발생: ${e.message}")
        }
    }
}
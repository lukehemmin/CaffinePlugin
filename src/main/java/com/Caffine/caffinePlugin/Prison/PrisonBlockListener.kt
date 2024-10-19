package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.java.JavaPlugin

class PrisonBlockListener(private val plugin: JavaPlugin, private val database: Database) : Listener {
    private var boundary1: org.bukkit.Location? = null
    private var boundary2: org.bukkit.Location? = null
    private val regenerationDelay = 20L // 1초 (구성 파일에서 읽어올 수 있음)

    init {
        loadBoundaries()
    }

    private fun loadBoundaries() {
        try {
            boundary1 = database.getPosition("prison_boundary_1")
            boundary2 = database.getPosition("prison_boundary_2")
            if (boundary1 == null || boundary2 == null) {
                plugin.logger.warning("감옥 범위가 설정되지 않았습니다.")
            }
        } catch (e: Exception) {
            plugin.logger.severe("감옥 범위를 로드하는 중 오류 발생: ${e.message}")
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.COAL_ORE) return

        if (boundary1 == null || boundary2 == null) {
            loadBoundaries()
            return
        }

        if (isInPrisonBoundary(block.location, boundary1!!, boundary2!!)) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (block.type == Material.AIR) {
                    block.type = Material.COAL_ORE
                    plugin.logger.info("감옥 내 석탄 광석이 재생성되었습니다. 위치: ${block.location}")
                }
            }, regenerationDelay)
        }
    }

    private fun isInPrisonBoundary(loc: org.bukkit.Location, boundary1: org.bukkit.Location, boundary2: org.bukkit.Location): Boolean {
        val minX = minOf(boundary1.x, boundary2.x)
        val minY = minOf(boundary1.y, boundary2.y)
        val minZ = minOf(boundary1.z, boundary2.z)
        val maxX = maxOf(boundary1.x, boundary2.x)
        val maxY = maxOf(boundary1.y, boundary2.y)
        val maxZ = maxOf(boundary1.z, boundary2.z)

        return loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ
    }
}

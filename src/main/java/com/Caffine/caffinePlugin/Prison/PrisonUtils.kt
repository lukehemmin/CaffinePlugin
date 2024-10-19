package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.ByteArrayInputStream
import java.util.*

object PrisonUtils {
    lateinit var plugin: JavaPlugin
    lateinit var database: Database

    fun init(plugin: JavaPlugin, database: Database) {
        this.plugin = plugin
        this.database = database
    }

    fun releasePlayer(player: Player) {
        val originalLocation = database.getPlayerLocation(player.uniqueId)
        if (originalLocation != null) {
            player.teleport(originalLocation)
        }

        // 인벤토리, 레벨, 경험치 복원
        val (savedInventory, savedLevel, savedExp) = database.getPlayerInventory(player.uniqueId)
        if (savedInventory != null) {
            player.inventory.contents = savedInventory
            player.level = savedLevel
            player.exp = savedExp
        } else {
            player.inventory.clear() // 저장된 인벨토리가 없으면 초기화
            player.level = savedLevel
            player.exp = savedExp
        }

        // 감옥 상태 업데이트
        database.updatePlayerJailStatus(player.uniqueId, false, 0, System.currentTimeMillis())

        // 저장된 데이터 삭제
        database.removePlayerJailData(player.uniqueId)

        player.sendMessage("당신은 이제 자유입니다.")
    }

    fun scheduleRelease(player: Player, remainingTime: Long) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            releasePlayer(player)
        }, remainingTime / 50) // 밀리초를 틱으로 변환 (1틱 = 50ms)
    }

    fun imprisonPlayer(prisoner: Player, duration: Int) {
        try {
            val prisonLocation = database.getPosition("prison")
            if (prisonLocation == null) {
                prisoner.sendMessage("감옥 위치가 설정되지 않았습니다.")
                return
            }

            // 현재 위치 저장
            val originalLocation = prisoner.location
            database.savePlayerLocation(prisoner.uniqueId, originalLocation)

            // 인벤토리, 레벨, 경험치 저장
            database.savePlayerInventory(prisoner.uniqueId, prisoner.inventory.contents, prisoner.level, prisoner.exp)

            // 인벤토리 초기화
            prisoner.inventory.clear()

            // 레벨과 경험치 초기화
            prisoner.level = 0
            prisoner.exp = 0f

            // 감옥으로 이동
            prisoner.teleport(prisonLocation)

            // 감옥용 곡괭이 지급
            giveJailPickaxe(prisoner)

            // 감옥 상태 업데이트
            database.updatePlayerJailStatus(prisoner.uniqueId, true, duration * 60 * 1000L, System.currentTimeMillis())

            prisoner.sendMessage("당신은 ${duration}분 동안 감옥에 수감되었습니다.")

            // 석방 예약
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                releasePlayer(prisoner)
            }, duration * 20L * 60L) // 틱 단위로 변환 (20틱 = 1초)

            plugin.logger.info("${prisoner.name}이(가) ${duration}분 동안 감옥에 수감되었습니다.")
        } catch (e: Exception) {
            plugin.logger.severe("플레이어 ${prisoner.name}을(를) 감옥에 가두는 중 오류 발생: ${e.message}")
            prisoner.sendMessage("감옥 시스템 오류가 발생했습니다. 관리자에게 문의하세요.")
        }
    }

    private fun giveJailPickaxe(prisoner: Player) {
        val pickaxeData = database.getData("jail_pickaxe")
        if (pickaxeData != null) {
            val pickaxe = deserializeItemStack(pickaxeData)
            prisoner.inventory.addItem(pickaxe)
        } else {
            prisoner.inventory.addItem(ItemStack(Material.WOODEN_PICKAXE))
        }
    }

    private fun deserializeItemStack(data: String): ItemStack {
        val bais = ByteArrayInputStream(Base64.getDecoder().decode(data))
        val ois = BukkitObjectInputStream(bais)
        val item = ois.readObject() as ItemStack
        ois.close()
        return item
    }
}

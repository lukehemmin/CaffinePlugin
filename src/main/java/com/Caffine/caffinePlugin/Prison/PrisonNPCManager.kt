package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

object PrisonNPCManager : Listener {
    private lateinit var plugin: JavaPlugin
    private lateinit var database: Database
    private var isSelectingNPC = false
    private var selectorUUID: String? = null

    fun init(plugin: JavaPlugin, database: Database) {
        this.plugin = plugin
        this.database = database
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun startNPCSelection(player: Player) {
        isSelectingNPC = true
        selectorUUID = player.uniqueId.toString()
    }

    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val player = event.clicker
        if (isSelectingNPC && player.uniqueId.toString() == selectorUUID) {
            val npcId = event.npc.id
            database.saveData("prison_npc", npcId.toString())
            player.sendMessage("§a감옥 NPC가 성공적으로 지정되었습니다. (NPC ID: $npcId)")
            isSelectingNPC = false
            selectorUUID = null
        } else if (database.getData("prison_npc") == event.npc.id.toString()) {
            handlePrisonerInteraction(player)
        }
    }

    private fun handlePrisonerInteraction(player: Player) {
        if (!database.isPlayerJailed(player.uniqueId)) {
            player.sendMessage("§c당신은 현재 감옥에 있지 않습니다.")
            return
        }

        val coalCount = player.inventory.all(Material.COAL).values.sumOf { it.amount }
        if (coalCount > 0) {
            val timeReduction = coalCount.toLong() // 1초씩 감소
            val remainingTime = database.getPlayerJailTime(player.uniqueId)
            val newTime = maxOf(0, remainingTime - timeReduction * 1000) // 밀리초 단위로 변환

            database.updatePlayerJailStatus(player.uniqueId, true, newTime, System.currentTimeMillis())
            player.inventory.remove(ItemStack(Material.COAL, coalCount))
            player.sendMessage("§a${coalCount}개의 석탄을 제출하여 ${coalCount}초의 시간이 감소되었습니다.")

            if (newTime == 0L) {
                PrisonUtils.releasePlayer(player)
            } else {
                val remainingMinutes = newTime / 60000 // 분 단위로 변환
                player.sendMessage("§a남은 시간: ${remainingMinutes}분")
            }
        } else {
            player.sendMessage("§c석탄이 없습니다. 석탄을 모아오세요.")
        }
    }
}
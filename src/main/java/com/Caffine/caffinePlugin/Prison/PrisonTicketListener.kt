package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import net.citizensnpcs.api.CitizensAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.ByteArrayInputStream
import java.util.*

class PrisonTicketListener(private val plugin: JavaPlugin, private val database: Database) : Listener {
    private val cooldowns = mutableMapOf<UUID, Long>()
    private val COOLDOWN_TIME = 3000L // 3초

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val currentTime = System.currentTimeMillis()

        if (currentTime - (cooldowns[player.uniqueId] ?: 0) < COOLDOWN_TIME) {
            // 쿨다운 중일 때의 처리
            // 메시지를 비활성화하려면 이 부분을 주석 처리하거나 제거하세요
            // player.sendMessage("§c아직 티켓을 사용할 수 없습니다.")
            event.isCancelled = true
            return
        }

        val clickedEntity = event.rightClicked

        if (clickedEntity !is Player) return

        // NPC 체크
        if (CitizensAPI.getNPCRegistry().isNPC(clickedEntity)) {
            player.sendMessage("§cNPC에게는 감옥 티켓을 사용할 수 없습니다.")
            return
        }

        val itemInHand = player.inventory.itemInMainHand
        if (!isPrisonTicket(itemInHand)) return

        event.isCancelled = true

        // 클릭된 플레이어가 이미 감옥에 있는지 확인
        if (database.isPlayerJailed(clickedEntity.uniqueId)) {
            player.sendMessage("§c${clickedEntity.name}은(는) 이미 감옥에 있습니다.")
            return
        }

        val duration = getTicketDuration(itemInHand)
        if (duration == 0) return

        try {
            PrisonUtils.imprisonPlayer(clickedEntity, duration)
            removeTicketFromHand(player)
            player.sendMessage("${clickedEntity.name}을(를) ${duration}분 동안 감옥에 가두었습니다.")
            plugin.logger.info("${player.name}이(가) ${clickedEntity.name}을(를) ${duration}분 동안 감옥에 가두었습니다.")

            // 쿨다운 설정
            cooldowns[player.uniqueId] = currentTime
        } catch (e: Exception) {
            player.sendMessage("§c오류가 발생했습니다. 관리자에게 문의하세요.")
            plugin.logger.severe("플레이어를 감옥에 가두는 중 오류 발생: ${e.message}")
        }
    }

    private fun isPrisonTicket(item: ItemStack): Boolean {
        return item.type != Material.AIR && item.itemMeta?.displayName?.startsWith("§e§l감옥 티켓") == true
    }

    private fun getTicketDuration(item: ItemStack): Int {
        val displayName = item.itemMeta?.displayName ?: return 0
        return displayName.substringAfter("(").substringBefore("분)").toIntOrNull() ?: 0
    }

    private fun deserializeItemStack(data: String): ItemStack {
        val bais = ByteArrayInputStream(Base64.getDecoder().decode(data))
        val ois = BukkitObjectInputStream(bais)
        val item = ois.readObject() as ItemStack
        ois.close()
        return item
    }

    private fun removeTicketFromHand(player: Player) {
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }
    }
}
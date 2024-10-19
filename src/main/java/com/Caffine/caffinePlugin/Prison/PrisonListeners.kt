package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.Main
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import net.citizensnpcs.api.event.NPCRightClickEvent
import net.citizensnpcs.api.npc.NPC
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.Villager
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.*
import org.bukkit.persistence.PersistentDataType
import net.citizensnpcs.api.CitizensAPI

class PrisonListeners : Listener {
    private val npcSelectionMode = mutableMapOf<Player, Boolean>()
    private val lastTicketUseAttempt = mutableMapOf<Player, Long>()
    private val lastMessageSent = mutableMapOf<Player, Long>()
    private val COOLDOWN_MILLIS = 1000L // 1초 쿨다운
    private val MESSAGE_COOLDOWN_MILLIS = 2000L // 2초 메시지 쿨다운

    companion object {
        private const val TICKET_PREFIX = "감옥 티켓"
        private const val COAL_REDUCTION_RATE = 1 // 1초씩 감소
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        val target = event.rightClicked

        Main.instance.debugLog("PlayerInteractEntityEvent: ${player.name} interacted with ${target.type}")

        if (isPrisonTicket(item) && target is Player) {
            // NPC 체크 추가
            if (CitizensAPI.getNPCRegistry().isNPC(target)) {
                Main.instance.debugLog("Interacted entity is an NPC, ignoring prison ticket use")
                return
            }

            Main.instance.debugLog("Prison ticket detected in PlayerInteractEntityEvent")
            if (handlePrisonTicketUse(player, target, item)) {
                event.isCancelled = true
            }
        }
    }

    private fun isCoalSubmission(player: Player, item: ItemStack, clickedEntity: Entity): Boolean {
        return clickedEntity is Villager && item.type == Material.COAL && Prison.isPrisoner(player.uniqueId)
    }

    private fun handleCoalSubmission(event: PlayerInteractEntityEvent, player: Player, item: ItemStack) {
        val amount = item.amount
        player.inventory.removeItem(ItemStack(Material.COAL, amount))
        Prison.submitCoal(player.uniqueId, amount)
        player.sendMessage("§a${amount}개의 석탄을 제출했습니다.")
        event.isCancelled = true
    }

    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val player = event.clicker
        val npc = event.npc

        if (npcSelectionMode[player] == true) {
            handleNPCSelection(player, npc)
            event.isCancelled = true
            return
        }

        val prisonNPC = Prison.getPrisonNPC()
        if (prisonNPC != null && npc.id == prisonNPC.id) {
            handlePrisonNPCInteraction(player)
            event.isCancelled = true
        }
    }

    fun setNPCSelectionMode(player: Player, mode: Boolean) {
        npcSelectionMode[player] = mode
        player.sendMessage(if (mode) "NPC를 우클릭하여 감옥 NPC로 지정하세요." else "NPC 선택 모드가 비활성화되었습니다.")
    }

    private fun isPrisonTicket(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false

        // 커스텀 데이터 확인
        val container = meta.persistentDataContainer
        val isPrisonTicketKey = NamespacedKey(Main.instance, "isPrisonTicket")

        return container.has(isPrisonTicketKey, PersistentDataType.BOOLEAN) &&
                container.get(isPrisonTicketKey, PersistentDataType.BOOLEAN) == true
    }

    private fun handlePrisonTicketUse(attacker: Player, target: Player, item: ItemStack?): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - (lastTicketUseAttempt[attacker] ?: 0) < COOLDOWN_MILLIS) {
            return false // 쿨다운 중이면 처리하지 않음
        }
        lastTicketUseAttempt[attacker] = currentTime

        Main.instance.debugLog("Handling prison ticket use: ${attacker.name} -> ${target.name}")

        if (item == null || !item.hasItemMeta()) {
            sendCooldownMessage(attacker, "${ChatColor.RED}유효한 감옥 티켓이 아닙니다.")
            return false
        }

        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val prisonTimeKey = NamespacedKey(Main.instance, "prisonTime")

        if (!container.has(prisonTimeKey, PersistentDataType.INTEGER)) {
            sendCooldownMessage(attacker, "${ChatColor.RED}유효한 감옥 티켓이 아닙니다.")
            return false
        }

        val duration = container.get(prisonTimeKey, PersistentDataType.INTEGER) ?: return false

        val distance = attacker.location.distance(target.location)
        Main.instance.debugLog("Distance between players: $distance")

        if (distance <= 3) {
            Prison.sendToPrison(target, duration)
            sendCooldownMessage(attacker, "${ChatColor.GREEN}${target.name}을(를) ${duration}분 동안 감옥에 보냈습니다.")
            sendCooldownMessage(target, "${ChatColor.RED}당신은 ${duration}분 동안 감옥에 수감되었습니다.")

            // 티켓 아이템 제거
            val itemInHand = attacker.inventory.itemInMainHand
            if (itemInHand.amount > 1) {
                itemInHand.amount = itemInHand.amount - 1
            } else {
                attacker.inventory.setItemInMainHand(null)
            }
            return true
        } else {
            sendCooldownMessage(attacker, "${ChatColor.RED}대상 플레이어가 너무 멀리 있습니다. 더 가까이 가세요.")
            return false
        }
    }

    private fun sendCooldownMessage(player: Player, message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - (lastMessageSent[player] ?: 0) >= MESSAGE_COOLDOWN_MILLIS) {
            player.sendMessage(message)
            lastMessageSent[player] = currentTime
        }
    }

    private fun extractTicketDuration(item: ItemStack?): Int {
        if (item == null || !item.hasItemMeta()) return 0
        val meta = item.itemMeta ?: return 0
        val displayName = meta.displayName

        // "감옥 티켓 (10분)" 형식에서 숫자 추출
        val regex = Regex("$TICKET_PREFIX \\((\\d+)분\\)")
        val matchResult = regex.find(displayName)
        return matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun handleNPCSelection(player: Player, npc: NPC) {
        Prison.setNpcId(npc.id)
        player.sendMessage("감옥 NPC가 설정되었습니다. (NPC 이름: ${npc.name})")
        npcSelectionMode[player] = false
    }

    private fun handlePrisonNPCInteraction(player: Player) {
        val coalCount = player.inventory.all(Material.COAL).values.sumOf { it.amount }
        if (coalCount > 0) {
            val timeReduction = coalCount * COAL_REDUCTION_RATE
            Prison.reduceTime(player, timeReduction)
            player.inventory.remove(Material.COAL)
            player.sendMessage("${coalCount}개의 석탄을 제출하여 감옥 시간이 ${timeReduction}초 줄어들었습니다.")
        } else {
            player.sendMessage("석탄이 없습니다. 석탄을 모아 감옥 시간을 줄이세요.")
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        if (block.type == Material.COAL_ORE && Prison.isInPrisonBounds(block.location)) {
            event.isCancelled = true
            block.type = Material.AIR
            player.inventory.addItem(ItemStack(Material.COAL))
            Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
                block.type = Material.COAL_ORE
            }, Prison.coalRegenerationTime * 20) // config에서 설정한 시간 후 재생성
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        Prison.onPlayerQuit(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Prison.onPlayerJoin(event.player)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (Prison.isPrisoner(player.uniqueId)) {
            event.respawnLocation = Prison.location
        }
    }
}

package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.Main
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import net.citizensnpcs.api.event.NPCRightClickEvent
import net.citizensnpcs.api.npc.NPC
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PrisonListeners : Listener {
    private val npcSelectionMode = mutableMapOf<Player, Boolean>()

    companion object {
        private const val TICKET_PREFIX = "감옥 티켓"
        private const val COAL_REDUCTION_RATE = 1 // 1초씩 감소
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (isPrisonTicket(item)) {
            val target = event.rightClicked
            if (target is Player) {
                handlePrisonTicketUse(player, target, item)
            } else {
                player.sendMessage("플레이어에게만 티켓을 사용할 수 있습니다.")
            }
            event.isCancelled = true
        }
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

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val item = event.item
            if (isPrisonTicket(item)) {
                event.isCancelled = true
                event.player.sendMessage("${ChatColor.YELLOW}플레이어를 우클릭하여 감옥에 보내세요.")
            }
        }
    }

    fun setNPCSelectionMode(player: Player, mode: Boolean) {
        npcSelectionMode[player] = mode
        player.sendMessage(if (mode) "NPC를 우클릭하여 감옥 NPC로 지정하세요." else "NPC 선택 모드가 비활성화되었습니다.")
    }

    private fun isPrisonTicket(item: ItemStack?): Boolean {
        return item?.type == Material.PAPER && item.itemMeta?.displayName?.startsWith(TICKET_PREFIX) == true
    }

    private fun handlePrisonTicketUse(player: Player, target: Player, item: ItemStack) {
        val duration = item.itemMeta?.displayName?.split(": ")?.get(1)?.split(" ")?.get(0)?.toIntOrNull()
        if (duration != null) {
            Prison.sendToPrison(target, duration)
            player.inventory.removeItem(ItemStack(item.type, 1))
            player.sendMessage("${target.name}을(를) ${duration}분 동안 감옥에 보냈습니다.")
        } else {
            player.sendMessage("티켓에 문제가 있습니다.")
        }
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
}

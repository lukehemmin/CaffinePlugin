package com.Caffine.caffinePlugin

import me.caffeine.prison_Caffeine.util.FileUtil
import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.HashMap
import java.util.UUID

object UserEvent : Listener {
    private val prefix: String = (FileUtil.getDataFile("", "config", "prefix") as? String)?.replace("&", "§") ?: ""
    val prisonMap: HashMap<UUID, Int> = HashMap()
    private lateinit var plugin: Main

    // plugin 초기화 메서드 추가
    fun initialize(mainPlugin: Main) {
        plugin = mainPlugin
    }

    @EventHandler
    fun onUserJoin(e: PlayerJoinEvent) {
        val userID = e.player.uniqueId
        if (FileUtil.existsFile("userData", userID.toString())) {
            val time = FileUtil.getDataFile("userData", userID.toString(), "Prison.Time") as? Int
            time?.let {
                prisonMap[userID] = it
            }
            FileUtil.deleteFile("userData", userID.toString())
        }
    }

    @EventHandler
    fun onUserQuit(e: PlayerQuitEvent) {
        val userID = e.player.uniqueId
        val prisonTime = prisonMap[userID]
        if (prisonTime != null && prisonTime > 0) {
            if (!FileUtil.existsFile("userData", userID.toString())) {
                FileUtil.createFile("userData", userID.toString())
            }
            FileUtil.setDataFile("userData", userID.toString(), "Prison.Time", prisonTime)
            prisonMap.remove(userID)
        }
    }

    @EventHandler
    fun onPrisonItemUse(e: PlayerInteractEntityEvent) {
        val usePlayer = e.player
        if (e.rightClicked is Player) {
            val targetPlayer = e.rightClicked as Player
            if (targetPlayer.isOnline) {
                val itemInHand = usePlayer.inventory.itemInMainHand
                if (itemInHand != null && itemInHand.type != Material.AIR) {
                    val tickets = listOf("5", "10", "15")
                    for (ticket in tickets) {
                        val ticketItem = FileUtil.getDataFile("", "dataFile", "Prison.Ticket.$ticket") as? ItemStack
                        if (ticketItem != null && itemInHand.isSimilar(ticketItem)) {
                            val teleportLocation = FileUtil.getDataFile("", "dataFile", "Prison.teleportLocation") as? Location
                            val backLocation = FileUtil.getDataFile("", "dataFile", "Prison.backLocation") as? Location
                            val pickaxe = FileUtil.getDataFile("", "dataFile", "Prison.Pickaxe") as? ItemStack
                            when {
                                teleportLocation == null -> {
                                    usePlayer.sendMessage("$prefix 감옥에 위치가 지정되어 있지 않습니다. 관리자에게 문의하세요.")
                                }
                                backLocation == null -> {
                                    usePlayer.sendMessage("$prefix 돌아올 위치가 지정되어 있지 않습니다. 관리자에게 문의하세요.")
                                }
                                pickaxe == null -> {
                                    usePlayer.sendMessage("$prefix 지급 곡괭이가 지정되어 있지 않습니다. 관리자에게 문의 하세요.")
                                }
                                !prisonMap.containsKey(targetPlayer.uniqueId) -> {
                                    usePlayer.inventory.removeItem(ticketItem)
                                    val time = 60 * ticket.toInt()
                                    prisonMap[targetPlayer.uniqueId] = time
                                    targetPlayer.teleport(teleportLocation)
                                    saveInventory(targetPlayer)
                                    targetPlayer.inventory.addItem(pickaxe)
                                    break
                                }
                                else -> {
                                    usePlayer.sendMessage("$prefix 해당 플레이어는 이미 감옥에 있습니다.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun saveInventory(player: Player) {
        val userId = player.uniqueId
        val inventory = player.inventory.contents
        if (!FileUtil.existsFile("tempData", userId.toString())) {
            FileUtil.createFile("tempData", userId.toString())
            inventory.forEachIndexed { index, item ->
                if (item != null && item.type != Material.AIR) {
                    FileUtil.setDataFile("tempData", userId.toString(), "Inventory.$index", item)
                    player.inventory.clear(index)
                }
            }
            // 플레이어의 위치 저장
            FileUtil.setDataFile("tempData", userId.toString(), "Location", player.location)
            FileUtil.setDataFile("tempData", userId.toString(), "Level", player.level)
            FileUtil.setDataFile("tempData", userId.toString(), "Experience", player.totalExperience)
        }
    }

    fun restoreInventory(player: Player) {
        val userId = player.uniqueId
        if (FileUtil.existsFile("tempData", userId.toString())) {
            // 먼저 현재 인벤토리 클리어
            player.inventory.clear()

            // 위치 복원
            val originalLocation = FileUtil.getDataFile("tempData", userId.toString(), "Location") as? Location
            if (originalLocation != null) {
                player.teleport(originalLocation)
                plugin.logger.info("[Prison] ${player.name}의 위치를 ${originalLocation.x}, ${originalLocation.y}, ${originalLocation.z}로 복원")
            }

            // 인벤토리 복원
            for (i in 0..35) { // 메인 인벤토리 슬롯
                val item = FileUtil.getDataFile("tempData", userId.toString(), "Inventory.$i") as? ItemStack
                if (item != null) {
                    player.inventory.setItem(i, item)
                }
            }

            // 레벨과 경험치 복원
            val level = FileUtil.getDataFile("tempData", userId.toString(), "Level") as? Int
            val experience = FileUtil.getDataFile("tempData", userId.toString(), "Experience") as? Int

            level?.let {
                player.level = it
                plugin.logger.info("[Prison] ${player.name}의 레벨을 $it 로 복원")
            }
            experience?.let {
                player.totalExperience = it
                plugin.logger.info("[Prison] ${player.name}의 경험치를 $it 로 복원")
            }

            // 복원 후 임시 데이터 삭제
            FileUtil.deleteFile("tempData", userId.toString())
            plugin.logger.info("[Prison] ${player.name}의 임시 데이터 파일 삭제 완료")
        } else {
            plugin.logger.warning("[Prison] ${player.name}의 임시 데이터 파일을 찾을 수 없습니다.")
        }
    }

    @EventHandler
    fun onBreakCoal(e: BlockBreakEvent) {
        val p = e.player
        val block = e.block
        if (block.type == Material.COAL_ORE) {
            val prisonTime = prisonMap[p.uniqueId]
            if (prisonTime != null && prisonTime > 0) {
                val coalRegenTime = FileUtil.getDataFile("", "config", "coalRegen") as? Int ?: return
                val time = 20L * coalRegenTime
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Main::class.java), Runnable {
                    block.location.block.type = Material.COAL_ORE
                }, time)
            }
        }
    }

    @EventHandler
    fun onNpcCoal(e: NPCRightClickEvent) {
        val npcID = e.npc.id
        val prisonNPC = FileUtil.getDataFile("", "dataFile", "Prison.NPC") as? Int
        if (prisonNPC != null && npcID == prisonNPC) {
            val removeTimePerCoal = 1
            val inventory = e.clicker.inventory.contents
            var totalCoalRemoved = 0
            for (item in inventory) {
                if (item != null && item.type == Material.COAL) {
                    totalCoalRemoved += item.amount
                    e.clicker.inventory.removeItem(ItemStack(Material.COAL, item.amount))
                }
            }
            if (totalCoalRemoved > 0) {
                val totalRemoveTime = totalCoalRemoved * removeTimePerCoal
                val playerId = e.clicker.uniqueId
                if (prisonMap.containsKey(playerId)) {
                    val currentPrisonTime = prisonMap[playerId]!!
                    prisonMap[playerId] = maxOf(0, currentPrisonTime - totalRemoveTime)
                    e.clicker.sendMessage("$prefix 시간이 $totalRemoveTime 초 줄어들었습니다.")
                }
            }
        }
    }
}
package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayOutputStream
import java.util.*

class PrisonCommand(private val plugin: JavaPlugin, private val database: Database) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (!sender.hasPermission("prison.admin")) {
            sender.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("사용법: /감옥 <위치설정|범위설정|티켓|곡괭이지정|강제종료>")
            return true
        }

        when (args[0]) {
            "위치설정" -> setPrisonLocation(sender)
            "범위설정" -> setPrisonBoundary(sender)
            "티켓" -> createPrisonTicket(sender, args)
            "곡괭이지정" -> setJailPickaxe(sender)
            "강제종료" -> forceReleasePrisoner(sender, args)
            "npc지정" -> designatePrisonNPC(sender)
            else -> sender.sendMessage("알 수 없는 서브 명령어입니다. 사용법: /감옥 <위치설정|범위설정|티켓|곡괭이지정|강제종료>")
        }

        return true
    }

    private fun setPrisonLocation(player: Player) {
        val location = player.location
        database.savePosition("prison", location)
        player.sendMessage("감옥 위치가 성공적으로 설정되었습니다.")
    }

    private fun setPrisonBoundary(player: Player) {
        val stick = ItemStack(Material.STICK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§e§l감옥 범위 설정 도구")
                lore = listOf("§7이 막대기로 두 지점을 우클릭하여", "§7감옥의 범위를 설정하세요.")
            }
        }
        player.inventory.addItem(stick)
        player.sendMessage("감옥 범위 설정 도구가 지급되었습니다. 두 지점을 우클릭하여 범위를 설정하세요.")
    }

    private fun createPrisonTicket(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("사용법: /감옥 티켓 <5|10|15>")
            return
        }

        val duration = args[1].toIntOrNull()
        if (duration !in setOf(5, 10, 15)) {
            player.sendMessage("유효한 감옥 시간을 입력해주세요: 5, 10, 또는 15")
            return
        }

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type == Material.AIR) {
            player.sendMessage("티켓으로 만들 아이템을 들고 있어야 합니다.")
            return
        }

        val ticketItem = itemInHand.clone()
        val meta = ticketItem.itemMeta
        meta?.setDisplayName("§e§l감옥 티켓 (${duration}분)")
        meta?.lore = listOf(
            "§7이 티켓을 사용하면 플레이어를",
            "§7${duration}분 동안 감옥에 가둘 수 있습니다.",
            "§c우클릭으로 사용"
        )
        ticketItem.itemMeta = meta

        player.inventory.setItemInMainHand(ticketItem)
        player.sendMessage("성공적으로 ${duration}분 감옥 티켓을 생성했습니다.")
    }

    private fun setJailPickaxe(player: Player) {
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type == Material.AIR || !itemInHand.type.name.endsWith("_PICKAXE")) {
            player.sendMessage("곡괭이를 들고 명령어를 사용해주세요.")
            return
        }

        val pickaxeData = serializeItemStack(itemInHand)
        database.saveData("jail_pickaxe", pickaxeData)
        player.sendMessage("감옥용 곡괭이가 성공적으로 지정되었습니다.")
    }

    private fun serializeItemStack(item: ItemStack): String {
        val baos = ByteArrayOutputStream()
        val oos = BukkitObjectOutputStream(baos)
        oos.writeObject(item)
        oos.close()
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun forceReleasePrisoner(sender: Player, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("사용법: /감옥 강제종료 <닉네임>")
            return
        }

        val targetName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetName)

        if (targetPlayer == null) {
            sender.sendMessage("플레이어 '$targetName'을(를) 찾을 수 없습니다.")
            return
        }

        val isJailed = database.isPlayerJailed(targetPlayer.uniqueId)
        if (!isJailed) {
            sender.sendMessage("${targetName}은(는) 현재 감옥에 있지 않습니다.")
            return
        }

        PrisonUtils.releasePlayer(targetPlayer)
        sender.sendMessage("${targetName}을(를) 감옥에서 강제 석방했습니다.")
    }

    private fun designatePrisonNPC(sender: Player) {
        if (!sender.hasPermission("prison.admin.setnpc")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return
        }

        sender.sendMessage("§aNPC를 우클릭하여 감옥 NPC로 지정하세요.")
        PrisonNPCManager.startNPCSelection(sender)
    }
}
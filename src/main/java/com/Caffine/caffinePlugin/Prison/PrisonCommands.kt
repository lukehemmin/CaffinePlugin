package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.Main
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PrisonCommands(private val prisonListeners: PrisonListeners) : CommandExecutor {

    private val VALID_DURATIONS = listOf(5, 10, 15)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("사용법: /감옥관리 [감옥지정|티켓|npc지정|곡괭이지정|강제종료|리로드]")
            return true
        }

        when (args[0].toLowerCase()) {
            "감옥지정" -> handleSetPrisonLocation(sender)
            "티켓" -> handleCreatePrisonTicket(sender, args)
            "npc지정" -> handleSetNPC(sender)
            "곡괭이지정" -> handleSetPrisonPickaxe(sender)
            "강제종료" -> handleReleasePrisoner(sender, args)
            "리로드" -> handleReloadConfig(sender)
            "pos1" -> handleSetPos1(sender as? Player)
            "pos2" -> handleSetPos2(sender as? Player)
            "석탄재생시간" -> handleSetCoalRegenerationTime(sender, args)
            else -> sender.sendMessage("알 수 없는 하위 명령어입니다. 사용법: /감옥관리 [감옥지정|티켓|npc지정|곡괭이지정|강제종료|리로드]")
        }

        return true
    }

    private fun handleSetPrisonLocation(player: Player) {
        if (!player.hasPermission("prison.set.location")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        setPrisonLocation(player)
        logCommand(player, "감옥 위치 설정")
    }

    private fun handleCreatePrisonTicket(player: Player, args: Array<out String>) {
        if (!player.hasPermission("prison.create.ticket")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        createPrisonTicket(player, args)
        logCommand(player, "감옥 티켓 생성")
    }

    private fun handleSetNPC(player: Player) {
        if (!player.hasPermission("prison.set.npc")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        setNPC(player)
        logCommand(player, "감옥 NPC 설정")
    }

    private fun handleSetPrisonPickaxe(player: Player) {
        if (!player.hasPermission("prison.set.pickaxe")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        setPrisonPickaxe(player)
        logCommand(player, "감옥 곡괭이 설정")
    }

    private fun handleReleasePrisoner(player: Player, args: Array<out String>) {
        if (!player.hasPermission("prison.release")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        releasePrisoner(player, args)
        logCommand(player, "죄수 석방")
    }

    private fun handleReloadConfig(player: Player) {
        if (!player.hasPermission("prison.reload")) {
            player.sendMessage("이 명령어를 사용할 권한이 없습니다.")
            return
        }
        reloadConfig(player)
        logCommand(player, "설정 리로드")
    }

    private fun setPrisonLocation(player: Player) {
        Prison.updateLocation(player.location)
        player.sendMessage("감옥 위치가 ${player.location.x}, ${player.location.y}, ${player.location.z}로 설정되었습니다.")
    }

    private fun setPrisonPickaxe(player: Player) {
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            player.sendMessage("손에 곡괭이를 들고 명령어를 사용하세요.")
            return
        }
        Prison.updatePickaxe(item.clone())  // setPickaxe 대신 updatePickaxe 사용
        player.sendMessage("감옥 곡괭이가 ${item.type}로 설정되었습니다.")
    }

    private fun reloadConfig(player: Player) {
        Main.instance.reloadConfig()
        Prison.loadPrisonData(Main.instance.config)
        player.sendMessage("설정과 감옥 데이터가 리로드되었습니다.")
    }

    private fun createPrisonTicket(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("사용법: /감옥관리 티켓 [5|10|15]")
            return
        }
        val duration = args[1].toIntOrNull()
        if (duration !in VALID_DURATIONS) {
            player.sendMessage("티켓 시간은 5, 10, 15분 중 하나여야 합니다.")
            return
        }
        val ticket = ItemStack(Material.PAPER)
        val meta = ticket.itemMeta
        meta?.setDisplayName("감옥 티켓: $duration 분")
        meta?.lore = listOf("우클릭으로 플레이어를 감옥에 보냅니다.")
        ticket.itemMeta = meta
        player.inventory.addItem(ticket)
        player.sendMessage("$duration 분짜리 감옥 티켓이 생성되었습니다.")
    }

    private fun setNPC(player: Player) {
        player.sendMessage("NPC를 우클릭하여 감옥 NPC로 지정하세요.")
        prisonListeners.setNPCSelectionMode(player, true)
    }

    private fun releasePrisoner(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("사용법: /감옥관리 강제종료 <플레이어이름>")
            return
        }
        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            player.sendMessage("플레이어를 찾을 수 없습니다: ${args[1]}")
            return
        }
        Prison.releasePrisoner(targetPlayer)
        player.sendMessage("${targetPlayer.name}을(를) 감옥에서 석방했습니다.")
    }

    private fun logCommand(player: Player, action: String) {
        Main.instance.logger.info("${player.name}이(가) $action 명령어를 실행했습니다.")
    }

    private fun handleSetPos1(player: Player?) {
        if (player == null) {
            Main.instance.logger.info("이 명령어는 콘솔에서 사용할 수 없습니다.")
            return
        }
        Prison.pos1 = player.location
        player.sendMessage("감옥의 첫 번째 지점이 설정되었습니다.")
    }

    private fun handleSetPos2(player: Player?) {
        if (player == null) {
            Main.instance.logger.info("이 명령어는 콘솔에서 사용할 수 없습니다.")
            return
        }
        Prison.pos2 = player.location
        player.sendMessage("감옥의 두 번째 지점이 설정되었습니다.")
        Prison.setPrisonBounds(Prison.pos1, Prison.pos2)
    }

    private fun handleSetCoalRegenerationTime(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("사용법: /감옥관리 석탄재생시간 <초>")
            return
        }
        val seconds = args[1].toLongOrNull()
        if (seconds == null || seconds < 1) {
            sender.sendMessage("유효한 시간(초)를 입력해주세요.")
            return
        }
        Prison.setCoalRegenerationTime(seconds)
        sender.sendMessage("석탄 재생성 시간이 ${seconds}초로 설정되었습니다.")
    }
}

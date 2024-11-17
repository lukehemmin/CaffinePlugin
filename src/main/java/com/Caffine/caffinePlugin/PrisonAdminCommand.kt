package com.Caffine.caffinePlugin

import me.caffeine.prison_Caffeine.util.FileUtil
import net.citizensnpcs.api.CitizensAPI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PrisonAdminCommand : CommandExecutor {
    private val prefix: String = (FileUtil.getDataFile("", "config", "prefix") as? String)?.replace("&", "§") ?: ""

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.isOp) {
            sender.sendMessage("$prefix 당신은 관리자가 아닙니다.")
            return false
        }

        if (args.isEmpty()) {
            helpusage(sender)
            return false
        }

        return when {
            args.size == 2 && args[0] == "보내기" -> {
                sendToPrison(sender, args)
                true
            }
            else -> {
                runPrison(sender, args)
                false
            }
        }
    }

    private fun helpusage(sender: CommandSender) {
        sender.sendMessage("$prefix /감옥관리 감옥지정 | 현재 위치로 감옥 티피 장소로 지정 합니다.")
        sender.sendMessage("$prefix /감옥관리 NPC지정 | 바라보고 있는 NPC를 석탄 수거 NPC로 지정합니다.")
        sender.sendMessage("$prefix /감옥관리 곡괭이지정 | 손에 들고 있는 아이템을 곡괭이로 설정 합니다.")
        sender.sendMessage("$prefix /감옥관리 티켓 [시간] | 손에 들고 있는 아이템을 티켓으로 지정합니다.")
        sender.sendMessage("$prefix /감옥관리 강제종료 [플레이어] | [플레이어]님의 감옥 시간을 강제 종료합니다.")
        sender.sendMessage("$prefix /감옥관리 보내기 [플레이어] | 특정 플레이어를 10분 동안 감옥으로 보냅니다.")
    }

    private fun sendToPrison(sender: CommandSender, args: Array<String>) {
        val targetName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetName)
        if (targetPlayer != null && targetPlayer.isOnline) {
            val time = 10
            val totalTime = time * 60
            val uuid = targetPlayer.uniqueId

            if (UserEvent.prisonMap.containsKey(uuid)) {
                val currentTime = UserEvent.prisonMap[uuid] ?: 0
                val newTime = currentTime + totalTime
                UserEvent.prisonMap[uuid] = newTime
                targetPlayer.sendMessage("$prefix 당신의 감옥 시간이 $time 분 추가되었습니다.")
                sender.sendMessage("$prefix $targetName 님의 감옥 시간이 $time 분 추가되었습니다.")
            } else {
                UserEvent.prisonMap[uuid] = totalTime
                val prisonLocation = FileUtil.getDataFile("", "dataFile", "Prison.teleportLocation") as? Location
                if (prisonLocation == null) {
                    sender.sendMessage("$prefix 감옥 위치가 지정되지 않았습니다.")
                    return
                }

                targetPlayer.teleport(prisonLocation)
                targetPlayer.sendMessage("$prefix 당신은 $time 분 동안 감옥에 보내졌습니다.")
                saveInventory(targetPlayer)
                val pickaxe = FileUtil.getDataFile("", "dataFile", "Prison.Pickaxe") as? ItemStack
                if (pickaxe != null) {
                    targetPlayer.inventory.addItem(pickaxe)
                }

                sender.sendMessage("$prefix $targetName 님을 $time 분 동안 감옥으로 보냈습니다.")
            }
        } else {
            sender.sendMessage("$prefix $targetName 님은 온라인이 아닙니다.")
        }
    }

    private fun saveInventory(player: Player) {
        UserEvent.saveInventory(player)
    }

    private fun runPrison(sender: CommandSender, args: Array<String>) {
        val command = args[0]
        when (command) {
            "감옥지정" -> setPrisonLocation(sender)
            "NPC지정" -> setNpc(sender)
            "곡괭이지정" -> setPickaxe(sender)
            "티켓" -> setPrisonTicket(sender)
            "강제종료" -> resetPrison(sender, if (args.size > 1) args[1] else null)
            else -> helpusage(sender)
        }
    }

    private fun setPrisonLocation(sender: CommandSender) {
        if (sender is Player) {
            val location = sender.location
            FileUtil.setDataFile("", "dataFile", "Prison.teleportLocation", location)
            sender.sendMessage("$prefix 현재 위치로 감옥 티피 위치로 지정 되었습니다.")
        } else {
            sender.sendMessage("$prefix 이 명령어는 인게임에서만 사용할 수 있습니다.")
        }
    }

    private fun setNpc(sender: CommandSender) {
        if (sender is Player) {
            val entities: List<Entity> = sender.getNearbyEntities(5.0, 5.0, 5.0)
            for (entity in entities) {
                val npc = CitizensAPI.getNPCRegistry().getNPC(entity)
                if (npc != null) {
                    FileUtil.setDataFile("", "dataFile", "Prison.NPC", npc.id)
                    sender.sendMessage("$prefix 근처 NPC로 NPC를 지정하였습니다. | ${npc.name}")
                    return
                }
            }
            sender.sendMessage("$prefix 근처에 NPC가 존재하지 않습니다.")
        } else {
            sender.sendMessage("$prefix 이 명령어는 인게임에서만 사용할 수 있습니다.")
        }
    }

    private fun setPickaxe(sender: CommandSender) {
        if (sender is Player) {
            val itemInHand = sender.inventory.itemInMainHand
            if (itemInHand.type == Material.AIR) {
                sender.sendMessage("$prefix 손에 아이템을 들고 커맨드를 입력해주시기 바랍니다.")
            } else {
                FileUtil.setDataFile("", "dataFile", "Prison.Pickaxe", itemInHand)
                sender.sendMessage("$prefix 손에 들고 있는 아이템을 곡괭이로 설정하였습니다.")
            }
        } else {
            sender.sendMessage("$prefix 이 명령어는 인게임에서만 사용할 수 있습니다.")
        }
    }

    private fun setPrisonTicket(sender: CommandSender) {
        if (sender is Player) {
            val itemInHand = sender.inventory.itemInMainHand
            if (itemInHand.type == Material.AIR) {
                sender.sendMessage("$prefix 손에 아이템을 들고 커맨드를 입력해주시기 바랍니다.")
            } else {
                FileUtil.setDataFile("", "dataFile", "Prison.ticket", itemInHand)
                sender.sendMessage("$prefix 손에 들고 있는 아이템을 티켓으로 설정하였습니다.")
            }
        } else {
            sender.sendMessage("$prefix 이 명령어는 인게임에서만 사용할 수 있습니다.")
        }
    }

    private fun resetPrison(sender: CommandSender, playerName: String?) {
        val targetPlayer: Player? = if (sender is ConsoleCommandSender) {
            Bukkit.getPlayer(playerName ?: return)
        } else if (sender is Player) {
            Bukkit.getPlayer(playerName ?: return)
        } else {
            sender.sendMessage("$prefix 이 명령어는 콘솔이나 플레이어에게만 사용 가능합니다.")
            return
        }

        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage("$prefix ${playerName ?: "플레이어"}님은 온라인이 아닙니다.")
            return
        }

        val uuid = targetPlayer.uniqueId
        if (UserEvent.prisonMap.containsKey(uuid)) {
            UserEvent.prisonMap[uuid] = 10
            sender.sendMessage("$prefix ${playerName}님의 감옥 시간을 10초로 설정했습니다.")
            targetPlayer.sendMessage("$prefix 당신의 감옥 시간이 10초로 설정되었습니다.")
        } else {
            sender.sendMessage("$prefix ${playerName}님은 감옥에 있지 않습니다.")
        }
    }
}
package com.Caffine.caffinePlugin

import me.caffeine.prison_Caffeine.util.FileUtil
import org.bukkit.plugin.java.JavaPlugin
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class Main : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        if (!FileUtil.existsFile("", "dataFile")) {
            FileUtil.createFile("", "dataFile")
        }

        // UserEvent 초기화 추가
        UserEvent.initialize(this)

        getCommand("감옥관리")?.setExecutor(PrisonAdminCommand())
        server.pluginManager.registerEvents(UserEvent, this)
        runPrison()
        logger.info("[Prison] ${description.version} 활성화")
    }

    override fun onDisable() {
        logger.info("[Prison] ${description.version} 비활성화")
        for (user in Bukkit.getOnlinePlayers()) {
            val prisonTime = UserEvent.prisonMap[user.uniqueId]
            if (prisonTime != null && !FileUtil.existsFile("userData", user.uniqueId.toString())) {
                FileUtil.createFile("userData", user.uniqueId.toString())
                FileUtil.setDataFile("userData", user.uniqueId.toString(), "Prison.Time", prisonTime)
            }
        }
    }

    fun runPrison() {
        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            for (user in Bukkit.getOnlinePlayers()) {
                val userId = user.uniqueId
                val prisonTime = UserEvent.prisonMap[userId]
                if (prisonTime != null) {
                    if (prisonTime <= 0) {
                        UserEvent.prisonMap.remove(userId)
                        logger.info("[Prison] ${user.name}의 감옥 시간이 만료되어 복원을 시도합니다.")

                        // 인벤토리 클리어 전에 복원 실행
                        UserEvent.restoreInventory(user)

                        // 액션바 메시지 제거
                        user.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(""))

                        // 복원 완료 메시지
                        user.sendMessage("${ChatColor.GREEN}감옥 시간이 종료되어 원래 상태로 복원되었습니다.")
                    } else {
                        UserEvent.prisonMap[userId] = prisonTime - 1
                        val msg = timeReturn(prisonTime - 1)
                        user.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(msg))
                    }
                }
            }
        }, 0L, 20L)
    }

    fun timeReturn(sec: Int): String {
        val min = sec / 60
        val second = sec % 60
        var total = "${second}초 남았습니다."
        if (min > 0) {
            total = "${min}분 $total"
        }
        return total
    }
}
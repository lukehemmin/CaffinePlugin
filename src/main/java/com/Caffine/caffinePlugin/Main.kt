package com.Caffine.caffinePlugin

import me.caffeine.prison_Caffeine.util.FileUtil
import org.bukkit.plugin.java.JavaPlugin
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
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
                        UserEvent.restoreInventory(user)
                        user.inventory.clear()

                        user.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(""))

                        // 인벤토리 복원은 restoreInventory 메서드에서 처리되므로 여기서 다시 할 필요 없음
                        // FileUtil.deleteFile("tempData", userId.toString())
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
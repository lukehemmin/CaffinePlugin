package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.System.Database
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarFlag
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.ByteArrayInputStream
import java.util.*

object PrisonUtils {
    lateinit var plugin: JavaPlugin
    lateinit var database: Database
    private val playerBossBars = mutableMapOf<UUID, BossBar>()
    private val playerRemainingTimes = mutableMapOf<UUID, Long>()

    fun init(plugin: JavaPlugin, database: Database) {
        this.plugin = plugin
        this.database = database
    }

    fun releasePlayer(player: Player) {
        val originalLocation = database.getPlayerLocation(player.uniqueId)
        if (originalLocation != null) {
            player.teleport(originalLocation)
        }

        // 인벤토리, 레벨, 경험치 복원
        val (savedInventory, savedLevel, savedExp) = database.getPlayerInventory(player.uniqueId)
        if (savedInventory != null) {
            player.inventory.contents = savedInventory
            player.level = savedLevel
            player.exp = savedExp
        } else {
            player.inventory.clear() // 저장된 인벨토리가 없으면 초기화
            player.level = savedLevel
            player.exp = savedExp
        }

        // 감옥 상태 업데이트
        database.updateJailPlayerStatus(player.uniqueId, player.name, false, 0)

        // 저장된 데이터 삭제
        database.removePlayerJailData(player.uniqueId)

        player.sendMessage("당신은 이제 자유입니다.")
    }

    fun scheduleRelease(player: Player, remainingTime: Long) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            releasePlayer(player)
        }, remainingTime / 50) // 밀리초를 틱으로 변환 (1틱 = 50ms)
    }

    fun imprisonPlayer(prisoner: Player, duration: Int) {
        try {
            val prisonLocation = database.getPosition("prison")
            if (prisonLocation == null) {
                prisoner.sendMessage("감옥 위치가 설정되지 않았습니다.")
                return
            }

            // 현재 위치 저장
            val originalLocation = prisoner.location
            database.savePlayerLocation(prisoner.uniqueId, originalLocation)

            // 인벤토리, 레벨, 경험치 저장
            database.savePlayerInventory(prisoner.uniqueId, prisoner.inventory.contents, prisoner.level, prisoner.exp)

            // 인벤토리 초기화
            prisoner.inventory.clear()

            // 레벨과 경험치 초기화
            prisoner.level = 0
            prisoner.exp = 0f

            // 감옥으로 이동
            prisoner.teleport(prisonLocation)

            // 감옥용 곡괭이 지급
            giveJailPickaxe(prisoner)

            // 감옥 상태 업데이트
            val remainingTime = duration * 60 * 1000L
            database.updateJailPlayerStatus(prisoner.uniqueId, prisoner.name, true, remainingTime)
            playerRemainingTimes[prisoner.uniqueId] = remainingTime

            prisoner.sendMessage("당신은 ${duration}분 동안 감옥에 수감되었습니다.")

            // 석방 예약
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                releasePlayer(prisoner)
            }, duration * 20L * 60L) // 틱 단위로 변환 (20틱 = 1초)

            plugin.logger.info("${prisoner.name}이(가) ${duration}분 동안 감옥에 수감되었습니다.")
        } catch (e: Exception) {
            plugin.logger.severe("플레이어 ${prisoner.name}을(를) 감옥에 가두는 중 오류 발생: ${e.message}")
            prisoner.sendMessage("감옥 시스템 오류가 발생했습니다. 관리자에게 문의하세요.")
        }
    }

    private fun giveJailPickaxe(prisoner: Player) {
        val pickaxeData = database.getData("jail_pickaxe")
        if (pickaxeData != null) {
            val pickaxe = deserializeItemStack(pickaxeData)
            prisoner.inventory.addItem(pickaxe)
        } else {
            prisoner.inventory.addItem(ItemStack(Material.WOODEN_PICKAXE))
        }
    }

    private fun deserializeItemStack(data: String): ItemStack {
        val bais = ByteArrayInputStream(Base64.getDecoder().decode(data))
        val ois = BukkitObjectInputStream(bais)
        val item = ois.readObject() as ItemStack
        ois.close()
        return item
    }

    fun showBossBar(player: Player, remainingTime: Long) {
        val bossBar = Bukkit.createBossBar(
            "남은 감옥 시간: ${remainingTime / 60000}분 ${remainingTime % 60000 / 1000}초",
            BarColor.RED,
            BarStyle.SOLID,
            BarFlag.CREATE_FOG
        )
        bossBar.addPlayer(player)
        playerBossBars[player.uniqueId] = bossBar

        // 보스바 업데이트 스케줄러 시작
        startBossBarUpdater(player, remainingTime)
    }

    fun getRemainingTime(player: Player): Long? {
        return playerRemainingTimes[player.uniqueId]
    }

    private fun startBossBarUpdater(player: Player, remainingTime: Long) {
        val taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, object : Runnable {
            var timeLeft = remainingTime

            override fun run() {
                if (timeLeft <= 0) {
                    removeBossBar(player)
                    Bukkit.getScheduler().cancelTask(this.hashCode())
                    return
                }

                updateBossBar(player, timeLeft)
                playerRemainingTimes[player.uniqueId] = timeLeft
                timeLeft -= 1000 // 1초 감소
            }
        }, 0L, 20L) // 20틱(1초)마다 실행

        // 플레이어의 보스바 업데이트 태스크 ID 저장
        playerBossBars[player.uniqueId]?.setProgress(1.0)
    }

    fun updateBossBar(player: Player, remainingTime: Long) {
        playerBossBars[player.uniqueId]?.let { bossBar ->
            bossBar.setTitle("남은 감옥 시간: ${remainingTime / 60000}분 ${remainingTime % 60000 / 1000}초")
            bossBar.progress = remainingTime / (remainingTime + 1000.0)
        }
    }

    fun removeBossBar(player: Player) {
        playerBossBars[player.uniqueId]?.let { bossBar ->
            bossBar.removeAll()
            playerBossBars.remove(player.uniqueId)
        }
    }
}

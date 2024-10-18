package com.Caffine.caffinePlugin

import com.Caffine.caffinePlugin.Prison.Prison
import com.Caffine.caffinePlugin.Prison.PrisonCommands
import com.Caffine.caffinePlugin.Prison.PrisonListeners
import com.Caffine.caffinePlugin.System.Database
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    companion object {
        lateinit var instance: Main
            private set
    }

    private lateinit var prisonListeners: PrisonListeners

    override fun onEnable() {
        instance = this

        // 설정 파일 로드
        saveDefaultConfig()

        // 데이터베이스 초기화
        Database.initialize(config)

        // 감옥 시스템
        prisonListeners = PrisonListeners()
        server.pluginManager.registerEvents(prisonListeners, this)
        getCommand("감옥관리")?.setExecutor(PrisonCommands(prisonListeners))
        Prison.initialize(config) // 감옥 시스템 초기화

        logger.info("카페인 플러그인 활성화")
    }

    override fun onDisable() {
        // 정리 작업
        Database.close()
        Prison.saveData() // 감옥 데이터 저장 (필요한 경우)

        logger.info("카페인 플러그인 비활성화")
    }

    // reloadConfig 함수를 클래스 레벨로 이동
    override fun reloadConfig() {
        super.reloadConfig() // 부모 클래스의 reloadConfig 호출
        Database.reconnect(config)
        Prison.loadPrisonData(config)
        logger.info("설정 및 데이터가 리로드되었습니다.")
    }
}

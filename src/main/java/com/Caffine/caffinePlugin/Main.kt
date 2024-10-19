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
        var debugMode: Boolean = false
    }

    private lateinit var prisonListeners: PrisonListeners

    override fun onEnable() {
        instance = this

        // 설정 파일 로드
        saveDefaultConfig()
        debugMode = config.getBoolean("debug_mode", false)
        logger.info("Debug mode: ${if (debugMode) "enabled" else "disabled"}")
        logger.info("설정 파일 로드 완료")

        try {
            // 데이터베이스 초기화
            logger.info("데이터베이스 초기화 시작")
            Database.initialize(config)
            logger.info("데이터베이스 초기화 완료")

            // 감옥 시스템
            logger.info("감옥 시스템 초기화 시작")
            prisonListeners = PrisonListeners()
            server.pluginManager.registerEvents(prisonListeners, this)
            server.pluginManager.registerEvents(PrisonListeners(), this)
            getCommand("감옥관리")?.setExecutor(PrisonCommands(prisonListeners))
            Prison.initialize(config) // 감옥 시스템 초기화
            logger.info("감옥 시스템 초기화 완료")

            logger.info("카페인 플러그인 활성화 완료")
        } catch (e: Exception) {
            logger.severe("플러그인 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        // 정리 작업
        Prison.saveData() // 감옥 데이터 저장 (필요한 경우)
        Database.close() // 데이터베이스 연결 종료

        logger.info("카페인 플러그인 비활성화")
    }

    // reloadConfig 함수를 클래스 레벨로 이동
    override fun reloadConfig() {
        super.reloadConfig() // 부모 클래스의 reloadConfig 호출
        Prison.loadPrisonData(config)
        Database.reconnect(config)
        logger.info("설정 및 데이터가 리로드되었습니다.")
    }

    fun debugLog(message: String) {
        if (debugMode) {
            logger.info("[DEBUG] $message")
        }
    }
}

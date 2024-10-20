package com.Caffine.caffinePlugin

import com.Caffine.caffinePlugin.Prison.*
import com.Caffine.caffinePlugin.System.Database
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    lateinit var database: Database

    override fun onEnable() {
        // 설정 파일 저장
        saveDefaultConfig()

        // 데이터베이스 초기화
        database = Database(config)
        PrisonUtils.init(this, database)

        PrisonNPCManager.init(this, database)
        getCommand("감옥")?.setExecutor(PrisonCommand(this, database)) // 명령어 등록
        server.pluginManager.registerEvents(PrisonBoundaryListener(this, database), this) // 리스너 등록
        server.pluginManager.registerEvents(PrisonBlockListener(this, database), this) // 리스너 등록
        server.pluginManager.registerEvents(PrisonTicketListener(this, database), this)
        server.pluginManager.registerEvents(PrisonPlayerListener(this, database), this)
        server.pluginManager.registerEvents(PrisonLogoutListener(database), this)

        logger.info("카페인 플러그인 활성화 완료")
    }

    override fun onDisable() {
        // 데이터베이스 연결 종료
        database.close()

        logger.info("카페인 플러그인 비활성화")
    }
}
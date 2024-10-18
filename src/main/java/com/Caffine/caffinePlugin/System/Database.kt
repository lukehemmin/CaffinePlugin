package com.Caffine.caffinePlugin.System

import com.Caffine.caffinePlugin.Main
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.sql.SQLException

object Database {
    private lateinit var dataSource: HikariDataSource

    fun initialize(config: FileConfiguration) {
        try {
            dataSource = createDataSource(config)
            createTables()
            Main.instance.logger.info("데이터베이스 연결이 성공적으로 초기화되었습니다.")
        } catch (e: Exception) {
            Main.instance.logger.severe("데이터베이스 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    fun reconnect(config: FileConfiguration) {
        try {
            close()
            dataSource = createDataSource(config)
            Main.instance.logger.info("데이터베이스 연결이 성공적으로 재설정되었습니다.")
        } catch (e: Exception) {
            Main.instance.logger.severe("데이터베이스 재연결 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createDataSource(config: FileConfiguration): HikariDataSource {
        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = config.getString("database.jdbcUrl", "jdbc:mariadb://localhost:3306/prison_db")
        hikariConfig.username = config.getString("database.username", "username")
        hikariConfig.password = config.getString("database.password", "password")
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250")
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true")
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true")
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true")
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true")
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true")
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true")
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false")

        // 연결 풀 설정
        hikariConfig.maximumPoolSize = 10
        hikariConfig.minimumIdle = 5
        hikariConfig.idleTimeout = 300000 // 5분
        hikariConfig.maxLifetime = 1800000 // 30분

        return HikariDataSource(hikariConfig)
    }

    fun getConnection(): Connection {
        return try {
            dataSource.connection
        } catch (e: SQLException) {
            Main.instance.logger.severe("데이터베이스 연결 획득 중 오류 발생: ${e.message}")
            throw e
        }
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            try {
                dataSource.close()
                Main.instance.logger.info("데이터베이스 연결이 성공적으로 종료되었습니다.")
            } catch (e: Exception) {
                Main.instance.logger.severe("데이터베이스 연결 종료 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createTables() {
        executeTransaction { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS prisoners (
                    uuid VARCHAR(36) PRIMARY KEY,
                    release_time BIGINT,
                    original_location TEXT
                )
            """)

                stmt.execute("""
                CREATE TABLE IF NOT EXISTS prisoner_inventories (
                    uuid VARCHAR(36) PRIMARY KEY,
                    inventory TEXT
                )
            """)
            }
        }
        Main.instance.logger.info("필요한 데이터베이스 테이블이 생성되었습니다.")
    }


    fun <T> executeTransaction(block: (Connection) -> T): T {
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                return result
            } catch (e: Exception) {
                conn.rollback()
                Main.instance.logger.severe("트랜잭션 실행 중 오류 발생: ${e.message}")
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }
}

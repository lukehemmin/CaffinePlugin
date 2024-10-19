package com.Caffine.caffinePlugin.System

import com.Caffine.caffinePlugin.Main
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.sql.SQLException

object Database {
    private lateinit var dataSource: HikariDataSource
    private var isInitialized = false

    fun isInitialized(): Boolean = isInitialized

    fun initialize(config: FileConfiguration, maxRetries: Int = 3) {
        try {
            val host = config.getString("database.host")
            val port = config.getInt("database.port")
            val dbName = config.getString("database.dbname")
            val username = config.getString("database.username")
            val password = config.getString("database.password")

            if (host == null || dbName == null || username == null || password == null) {
                throw IllegalArgumentException("Database configuration is incomplete")
            }

            Main.instance.logger.info("Attempting to connect to database: $host:$port/$dbName")

            dataSource = createDataSource(config)
            createTables()
            isInitialized = true
            Main.instance.logger.info("데이터베이스가 성공적으로 초기화되었습니다.")
            return
        } catch (e: Exception) {
            Main.instance.logger.severe("데이터베이스 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }


    fun reconnect(config: FileConfiguration) {
        try {
            close()
            dataSource = createDataSource(config)
            createTables()  // 테이블 재생성 확인
            isInitialized = true  // 초기화 상태 업데이트
            Main.instance.logger.info("데이터베이스 연결이 성공적으로 재설정되었습니다.")
        } catch (e: Exception) {
            Main.instance.logger.severe("데이터베이스 재연결 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createDataSource(config: FileConfiguration): HikariDataSource {
        val hikariConfig = HikariConfig()

        val host = config.getString("database.host", "localhost")
        val port = config.getInt("database.port", 3306)
        val dbName = config.getString("database.dbname", "prison_db")
        val username = config.getString("database.username", "root")
        val password = config.getString("database.password", "")

        hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$dbName"
        hikariConfig.username = username
        hikariConfig.password = password
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

        // 연결 테스트 쿼리 추가
        hikariConfig.connectionTestQuery = "SELECT 1"

        return HikariDataSource(hikariConfig)
    }

    fun getConnection(): Connection {
        if (!isInitialized) {
            throw IllegalStateException("Database has not been initialized")
        }
        return try {
            dataSource.connection
        } catch (e: SQLException) {
            Main.instance.logger.severe("데이터베이스 연결 획득 중 오류 발생: ${e.message}")
            throw e
        }
    }

    fun close() {
        if (isInitialized && ::dataSource.isInitialized && !dataSource.isClosed) {
            try {
                dataSource.close()
                isInitialized = false
                Main.instance.logger.info("데이터베이스 연결이 성공적으로 종료되었습니다.")
            } catch (e: Exception) {
                Main.instance.logger.severe("데이터베이스 연결 종료 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createTables() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                // prisoners 테이블 생성
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS prisoners (
                    uuid VARCHAR(36) PRIMARY KEY,
                    release_time BIGINT,
                    original_location TEXT
                )
            """)

                // prisoner_inventories 테이블 생성
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS prisoner_inventories (
                    uuid VARCHAR(36) PRIMARY KEY,
                    inventory TEXT
                )
            """)

                // 필요한 경우 여기에 더 많은 테이블을 추가할 수 있습니다.
            }
        }
        Main.instance.logger.info("필요한 데이터베이스 테이블이 생성되었습니다.")
    }


    fun <T> executeTransaction(block: (Connection) -> T): T {
        if (!isInitialized) {
            throw IllegalStateException("Database has not been initialized")
        }
        return getConnection().use { conn ->
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

package com.Caffine.caffinePlugin.System

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.SQLException
import java.util.*

class Database(config: FileConfiguration) {
    private val dataSource: HikariDataSource

    init {
        val hikariConfig = HikariConfig()
        val host = config.getString("database.host") ?: "localhost"
        val port = config.getInt("database.port", 3306)
        val dbName = config.getString("database.name") ?: "minecraft"
        hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$dbName"
        hikariConfig.username = config.getString("database.username") ?: "root"
        hikariConfig.password = config.getString("database.password") ?: ""
        hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
        hikariConfig.maximumPoolSize = 10

        dataSource = HikariDataSource(hikariConfig)

        initTables()
    }

    private fun initTables() {
        try {
            getConnection().use { conn ->
                val statement = conn.createStatement()
                statement.use {
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS positions (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(50) NOT NULL UNIQUE,
                            world VARCHAR(50) NOT NULL,
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL
                        )
                    """)
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS JailPlayer (
                            uuid VARCHAR(36) PRIMARY KEY,
                            nickname VARCHAR(16) NOT NULL,
                            is_jailed BOOLEAN DEFAULT FALSE,
                            remaining_time BIGINT DEFAULT 0,
                            original_location_world VARCHAR(50),
                            original_location_x DOUBLE,
                            original_location_y DOUBLE,
                            original_location_z DOUBLE,
                            last_login_time BIGINT DEFAULT 0
                        )
                    """)
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS JailInventory (
                            uuid VARCHAR(36) PRIMARY KEY,
                            inventory_data LONGTEXT,
                            level INT,
                            exp FLOAT,
                            FOREIGN KEY (uuid) REFERENCES JailPlayer(uuid) ON DELETE CASCADE
                        )
                    """)
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS other_data (
                            name VARCHAR(50) PRIMARY KEY,
                            data TEXT NOT NULL
                        )
                    """)
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS PlayerCooldowns (
                            uuid VARCHAR(36) PRIMARY KEY,
                            cooldown_time BIGINT
                        )
                    """)
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("데이터베이스 테이블 초기화 중 오류 발생: ${e.message}")
        }
    }

    fun getConnection(): Connection = dataSource.connection

    fun close() {
        dataSource.close()
    }

    fun savePosition(name: String, location: Location) {
        try {
            getConnection().use { conn ->
                val statement = conn.prepareStatement(
                    """
                    INSERT INTO positions (name, world, x, y, z) 
                    VALUES (?, ?, ?, ?, ?) 
                    ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?
                    """
                )
                statement.use {
                    it.setString(1, name)
                    it.setString(2, location.world?.name ?: "")
                    it.setDouble(3, location.x)
                    it.setDouble(4, location.y)
                    it.setDouble(5, location.z)
                    it.setString(6, location.world?.name ?: "")
                    it.setDouble(7, location.x)
                    it.setDouble(8, location.y)
                    it.setDouble(9, location.z)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("위치 저장 중 오류 발생: ${e.message}")
        }
    }

    fun getPosition(name: String): Location? {
        try {
            getConnection().use { conn ->
                val statement = conn.prepareStatement("SELECT * FROM positions WHERE name = ?")
                statement.use {
                    it.setString(1, name)
                    val resultSet = it.executeQuery()
                    resultSet.use { rs ->
                        if (rs.next()) {
                            val world = rs.getString("world")
                            val x = rs.getDouble("x")
                            val y = rs.getDouble("y")
                            val z = rs.getDouble("z")
                            return Location(Bukkit.getWorld(world), x, y, z)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("위치 조회 중 오류 발생: ${e.message}")
        }
        return null
    }

    fun savePlayerLocation(uuid: UUID, location: Location) {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT INTO JailPlayer (uuid, original_location_world, original_location_x, original_location_y, original_location_z)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    original_location_world = VALUES(original_location_world), 
                    original_location_x = VALUES(original_location_x), 
                    original_location_y = VALUES(original_location_y), 
                    original_location_z = VALUES(original_location_z)
                """)
                stmt.use {
                    it.setString(1, uuid.toString())
                    it.setString(2, location.world?.name)
                    it.setDouble(3, location.x)
                    it.setDouble(4, location.y)
                    it.setDouble(5, location.z)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 위치 저장 중 오류 발생: ${e.message}")
        }
    }

    fun getPlayerLocation(uuid: UUID): Location? {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT original_location_world, original_location_x, original_location_y, original_location_z FROM JailPlayer WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            val world = Bukkit.getWorld(rs.getString("original_location_world"))
                            val x = rs.getDouble("original_location_x")
                            val y = rs.getDouble("original_location_y")
                            val z = rs.getDouble("original_location_z")
                            return Location(world, x, y, z)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 위치 조회 중 오류 발생: ${e.message}")
        }
        return null
    }

    fun savePlayerInventory(uuid: UUID, contents: Array<ItemStack?>, level: Int, exp: Float) {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT INTO JailInventory (uuid, inventory_data, level, exp)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    inventory_data = VALUES(inventory_data), 
                    level = VALUES(level), 
                    exp = VALUES(exp)
                """)
                stmt.use {
                    val inventoryData = Base64.getEncoder().encodeToString(serializeInventory(contents))
                    it.setString(1, uuid.toString())
                    it.setString(2, inventoryData)
                    it.setInt(3, level)
                    it.setFloat(4, exp)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 인벤토리 저장 중 오류 발생: ${e.message}")
        }
    }

    fun getPlayerInventory(uuid: UUID): Triple<Array<ItemStack?>?, Int, Float> {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT inventory_data, level, exp FROM JailInventory WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            val inventoryData = rs.getString("inventory_data")
                            val level = rs.getInt("level")
                            val exp = rs.getFloat("exp")
                            return Triple(
                                deserializeInventory(Base64.getDecoder().decode(inventoryData)),
                                level,
                                exp
                            )
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 인벤토리 조회 중 오류 발생: ${e.message}")
        }
        return Triple(null, 0, 0f)
    }

    fun updateJailPlayerStatus(uuid: UUID, nickname: String, isJailed: Boolean, remainingTime: Long) {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT INTO JailPlayer (uuid, nickname, is_jailed, remaining_time, last_login_time)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    nickname = VALUES(nickname), 
                    is_jailed = VALUES(is_jailed), 
                    remaining_time = VALUES(remaining_time), 
                    last_login_time = VALUES(last_login_time)
                """)
                stmt.use {
                    val currentTime = System.currentTimeMillis()
                    it.setString(1, uuid.toString())
                    it.setString(2, nickname)
                    it.setBoolean(3, isJailed)
                    it.setLong(4, remainingTime)
                    it.setLong(5, currentTime)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 감옥 상태 업데이트 중 오류 발생: ${e.message}")
        }
    }

    private fun serializeInventory(contents: Array<ItemStack?>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val dataOutput = BukkitObjectOutputStream(outputStream)
        dataOutput.use {
            it.writeInt(contents.size)
            for (item in contents) {
                it.writeObject(item)
            }
        }
        return outputStream.toByteArray()
    }

    private fun deserializeInventory(data: ByteArray): Array<ItemStack?> {
        val inputStream = ByteArrayInputStream(data)
        val dataInput = BukkitObjectInputStream(inputStream)
        dataInput.use {
            val size = it.readInt()
            val contents = arrayOfNulls<ItemStack>(size)
            for (i in 0 until size) {
                contents[i] = it.readObject() as? ItemStack
            }
            return contents
        }
    }

    fun saveData(name: String, data: String) {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT INTO other_data (name, data) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE data = ?
                """)
                stmt.use {
                    it.setString(1, name)
                    it.setString(2, data)
                    it.setString(3, data)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("데이터 저장 중 오류 발생: ${e.message}")
        }
    }

    fun getData(name: String): String? {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT data FROM other_data WHERE name = ?")
                stmt.use {
                    it.setString(1, name)
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            return rs.getString("data")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("데이터 조회 중 오류 발생: ${e.message}")
        }
        return null
    }

    fun isPlayerJailed(uuid: UUID): Boolean {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT is_jailed FROM JailPlayer WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            return rs.getBoolean("is_jailed")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 감옥 상태 조회 중 오류 발생: ${e.message}")
        }
        return false
    }

    fun getPlayerJailTime(uuid: UUID): Long {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT remaining_time FROM JailPlayer WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            return rs.getLong("remaining_time")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 감옥 시간 조회 중 오류 발생: ${e.message}")
        }
        return 0L
    }

    fun getPlayerJailInfo(uuid: UUID): Triple<Boolean, Long, Long>? {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT is_jailed, remaining_time, last_login_time FROM JailPlayer WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            return Triple(
                                rs.getBoolean("is_jailed"),
                                rs.getLong("remaining_time"),
                                rs.getLong("last_login_time")
                            )
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 감옥 정보 조회 중 오류 발생: ${e.message}")
        }
        return null
    }

    fun removePlayerJailData(playerUUID: UUID) {
        try {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM JailPlayer WHERE uuid = ?").use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM JailInventory WHERE uuid = ?").use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.executeUpdate()
                }
                Bukkit.getLogger().info("플레이어 ${playerUUID}의 감옥 관련 데이터가 모두 삭제되었습니다.")
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 감옥 데이터 삭제 중 오류 발생: ${e.message}")
        }
    }

    fun getPlayerCooldown(uuid: UUID): Long {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("SELECT cooldown_time FROM PlayerCooldowns WHERE uuid = ?")
                stmt.use {
                    it.setString(1, uuid.toString())
                    val rs = it.executeQuery()
                    rs.use {
                        if (rs.next()) {
                            return rs.getLong("cooldown_time")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 쿨다운 시간 조회 중 오류 발생: ${e.message}")
        }
        return 0L
    }

    fun setPlayerCooldown(uuid: UUID, cooldownTime: Long) {
        try {
            getConnection().use { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT INTO PlayerCooldowns (uuid, cooldown_time) 
                    VALUES (?, ?) 
                    ON DUPLICATE KEY UPDATE cooldown_time = ?
                """)
                stmt.use {
                    it.setString(1, uuid.toString())
                    it.setLong(2, cooldownTime)
                    it.setLong(3, cooldownTime)
                    it.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("플레이어 쿨다운 시간 설정 중 오류 발생: ${e.message}")
        }
    }
}
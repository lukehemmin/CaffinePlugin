package com.Caffine.caffinePlugin.System

import com.Caffine.caffinePlugin.Prison.PrisonUtils.plugin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import org.bukkit.Location
import java.sql.SQLException
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS positions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) NOT NULL UNIQUE,
                        world VARCHAR(50) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL
                    )
                    """)
                // JailPlayer 테이블 생성
                statement.execute("""
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

                // JailInventory 테이블 생성
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS JailInventory (
                        uuid VARCHAR(36) PRIMARY KEY,
                        inventory_data LONGTEXT,
                        level INT,
                        exp FLOAT,
                        FOREIGN KEY (uuid) REFERENCES JailPlayer(uuid) ON DELETE CASCADE
                    )
                """)

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS other_data (
                        name VARCHAR(50) PRIMARY KEY,
                        data TEXT NOT NULL
                    )
                """)

                // PlayerCooldowns 테이블 생성
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS PlayerCooldowns (
                        uuid VARCHAR(36) PRIMARY KEY,
                        cooldown_time BIGINT
                    )
                """)
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
                statement.setString(1, name)
                statement.setString(2, location.world?.name ?: "")
                statement.setDouble(3, location.x)
                statement.setDouble(4, location.y)
                statement.setDouble(5, location.z)
                statement.setString(6, location.world?.name ?: "")
                statement.setDouble(7, location.x)
                statement.setDouble(8, location.y)
                statement.setDouble(9, location.z)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("위치 저장 중 오류 발생: ${e.message}")
        }
    }

    fun getPosition(name: String): Location? {
        try {
            getConnection().use { conn ->
                val statement = conn.prepareStatement("SELECT * FROM positions WHERE name = ?")
                statement.setString(1, name)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    val world = resultSet.getString("world")
                    val x = resultSet.getDouble("x")
                    val y = resultSet.getDouble("y")
                    val z = resultSet.getDouble("z")
                    return Location(Bukkit.getWorld(world), x, y, z)
                }
            }
        } catch (e: SQLException) {
            Bukkit.getLogger().severe("위치 조회 중 오류 발생: ${e.message}")
        }
        return null
    }

    fun savePlayerLocation(uuid: UUID, location: Location) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO JailPlayer (uuid, original_location_world, original_location_x, original_location_y, original_location_z)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                original_location_world = ?, original_location_x = ?, original_location_y = ?, original_location_z = ?
            """)
            stmt.setString(1, uuid.toString())
            stmt.setString(2, location.world?.name)
            stmt.setDouble(3, location.x)
            stmt.setDouble(4, location.y)
            stmt.setDouble(5, location.z)
            stmt.setString(6, location.world?.name)
            stmt.setDouble(7, location.x)
            stmt.setDouble(8, location.y)
            stmt.setDouble(9, location.z)
            stmt.executeUpdate()
        }
    }

    fun getPlayerLocation(uuid: UUID): Location? {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT original_location_world, original_location_x, original_location_y, original_location_z FROM JailPlayer WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val world = Bukkit.getWorld(rs.getString("original_location_world"))
                val x = rs.getDouble("original_location_x")
                val y = rs.getDouble("original_location_y")
                val z = rs.getDouble("original_location_z")
                return Location(world, x, y, z)
            }
        }
        return null
    }

    fun savePlayerInventory(uuid: UUID, contents: Array<ItemStack?>, level: Int, exp: Float) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
            INSERT INTO JailInventory (uuid, inventory_data, level, exp)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE inventory_data = ?, level = ?, exp = ?
        """)
            val inventoryData = Base64.getEncoder().encodeToString(serializeInventory(contents))
            stmt.setString(1, uuid.toString())
            stmt.setString(2, inventoryData)
            stmt.setInt(3, level)
            stmt.setFloat(4, exp)
            stmt.setString(5, inventoryData)
            stmt.setInt(6, level)
            stmt.setFloat(7, exp)
            stmt.executeUpdate()
        }
    }

    fun getPlayerInventory(uuid: UUID): Triple<Array<ItemStack?>?, Int, Float> {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT inventory_data, level, exp FROM JailInventory WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
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
        return Triple(null, 0, 0f)
    }

    fun updatePlayerJailStatus(uuid: UUID, isJailed: Boolean, remainingTime: Long) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
                UPDATE JailPlayer
                SET is_jailed = ?, remaining_time = ?
                WHERE uuid = ?
            """)
            stmt.setBoolean(1, isJailed)
            stmt.setLong(2, remainingTime)
            stmt.setString(3, uuid.toString())
            stmt.executeUpdate()
        }
    }

    private fun serializeInventory(contents: Array<ItemStack?>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val dataOutput = BukkitObjectOutputStream(outputStream)
        dataOutput.writeInt(contents.size)
        for (item in contents) {
            dataOutput.writeObject(item)
        }
        dataOutput.close()
        return outputStream.toByteArray()
    }

    private fun deserializeInventory(data: ByteArray): Array<ItemStack?> {
        val inputStream = ByteArrayInputStream(data)
        val dataInput = BukkitObjectInputStream(inputStream)
        val size = dataInput.readInt()
        val contents = arrayOfNulls<ItemStack>(size)
        for (i in 0 until size) {
            contents[i] = dataInput.readObject() as? ItemStack
        }
        dataInput.close()
        return contents
    }

    fun saveData(name: String, data: String) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO other_data (name, data) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE data = ?
            """)
            stmt.setString(1, name)
            stmt.setString(2, data)
            stmt.setString(3, data)
            stmt.executeUpdate()
        }
    }

    fun getData(name: String): String? {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT data FROM other_data WHERE name = ?")
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getString("data")
            }
        }
        return null
    }

    fun isPlayerJailed(uuid: UUID): Boolean {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT is_jailed FROM JailPlayer WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getBoolean("is_jailed")
            }
        }
        return false
    }

    fun getPlayerJailTime(uuid: UUID): Long {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT remaining_time FROM JailPlayer WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getLong("remaining_time")
            }
        }
        return 0L
    }

    fun updatePlayerJailStatus(uuid: UUID, isJailed: Boolean, remainingTime: Long, lastLoginTime: Long) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
            UPDATE JailPlayer
            SET is_jailed = ?, remaining_time = ?, last_login_time = ?
            WHERE uuid = ?
        """)
            stmt.setBoolean(1, isJailed)
            stmt.setLong(2, remainingTime)
            stmt.setLong(3, lastLoginTime)
            stmt.setString(4, uuid.toString())
            stmt.executeUpdate()
        }
    }

    fun getPlayerJailInfo(uuid: UUID): Triple<Boolean, Long, Long>? {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT is_jailed, remaining_time, last_login_time FROM JailPlayer WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return Triple(
                    rs.getBoolean("is_jailed"),
                    rs.getLong("remaining_time"),
                    rs.getLong("last_login_time")
                )
            }
        }
        return null
    }

    fun removePlayerJailData(playerUUID: UUID) {
        val connection = getConnection()
        connection.use { conn ->
            // 플레이어 위치 데이터 삭제
            conn.prepareStatement("DELETE FROM player_locations WHERE player_uuid = ?").use { stmt ->
                stmt.setString(1, playerUUID.toString())
                stmt.executeUpdate()
            }

            // 플레이어 인벤토리 데이터 삭제
            conn.prepareStatement("DELETE FROM player_inventories WHERE player_uuid = ?").use { stmt ->
                stmt.setString(1, playerUUID.toString())
                stmt.executeUpdate()
            }

            // 플레이어 감옥 상태 데이터 삭제
            conn.prepareStatement("DELETE FROM jail_status WHERE player_uuid = ?").use { stmt ->
                stmt.setString(1, playerUUID.toString())
                stmt.executeUpdate()
            }

            // 필요한 경우 추가 테이블에서도 데이터 삭제
            // 예: conn.prepareStatement("DELETE FROM other_jail_related_table WHERE player_uuid = ?")...

            plugin.logger.info("플레이어 ${playerUUID}의 감옥 관련 데이터가 모두 삭제되었습니다.")
        }
    }

    fun getPlayerCooldown(uuid: UUID): Long {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT cooldown_time FROM PlayerCooldowns WHERE uuid = ?")
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getLong("cooldown_time")
            }
        }
        return 0L
    }

    fun setPlayerCooldown(uuid: UUID, cooldownTime: Long) {
        val connection = getConnection()
        connection.use { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO PlayerCooldowns (uuid, cooldown_time) 
                VALUES (?, ?) 
                ON DUPLICATE KEY UPDATE cooldown_time = ?
            """)
            stmt.setString(1, uuid.toString())
            stmt.setLong(2, cooldownTime)
            stmt.setLong(3, cooldownTime)
            stmt.executeUpdate()
        }
    }
}
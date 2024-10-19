package com.Caffine.caffinePlugin.Prison

import com.Caffine.caffinePlugin.Main
import com.Caffine.caffinePlugin.System.Database
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Prison {
    lateinit var location: Location
        private set

    var pickaxe: ItemStack? = null
        private set

    lateinit var pos1: Location
    lateinit var pos2: Location

    var npcId: Int? = null
    private val prisoners = ConcurrentHashMap<UUID, Long>() // UUID to release time
    private val prisonerTimers = mutableMapOf<UUID, BukkitTask>()
    private val prisonerBossBars = mutableMapOf<UUID, BossBar>()

    private var _coalRegenerationTime: Long = 60 // 기본값 60초
    private const val COAL_TIME_REDUCTION = 1 // 석탄 1개당 60초 감소
    var coalRegenerationTime: Long
        get() = _coalRegenerationTime
        set(value) {
            _coalRegenerationTime = value
            savePrisonData(Main.instance.config)
        }

    data class Prisoner(
        var releaseTime: LocalDateTime,
        var coalSubmitted: Int = 0
    )

    fun isPrisoner(uuid: UUID): Boolean {
        return prisoners.containsKey(uuid)
    }

    fun submitCoal(uuid: UUID, amount: Int) {
        val releaseTime = prisoners[uuid] ?: return
        val timeReduction = amount * COAL_TIME_REDUCTION * 1000L // 밀리초 단위로 변환
        val newReleaseTime = (releaseTime - timeReduction).coerceAtLeast(System.currentTimeMillis())
        prisoners[uuid] = newReleaseTime

        val player = Bukkit.getPlayer(uuid)
        player?.let {
            val remainingTime = (newReleaseTime - System.currentTimeMillis()) / 1000
            it.sendMessage("§a${amount}개의 석탄을 제출했습니다. 감소된 시간: ${amount}초")
            it.sendMessage("§a남은 시간: ${formatTime(remainingTime)}")
            updatePrisonBossBar(it, newReleaseTime - System.currentTimeMillis())
        }

        // 데이터베이스 업데이트
        try {
            Database.executeTransaction { conn ->
                conn.prepareStatement("UPDATE prisoners SET release_time = ? WHERE uuid = ?").use { stmt ->
                    stmt.setLong(1, newReleaseTime)
                    stmt.setString(2, uuid.toString())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logError("석탄 제출 후 데이터베이스 업데이트 중 오류 발생", e)
        }

        // 만약 석방 시간이 현재 시간보다 이전이라면 즉시 석방
        if (newReleaseTime <= System.currentTimeMillis()) {
            player?.let { releasePrisoner(it) }
        }
    }

    fun initialize(config: FileConfiguration) {
        try {
            Main.instance.logger.info("감옥 시스템 초기화 시작")
            loadPrisonData(config)
            Bukkit.getScheduler().runTaskTimer(Main.instance, ::checkPrisoners, 20L, 20L)
            Main.instance.logger.info("감옥 시스템이 초기화되었습니다.")
        } catch (e: Exception) {
            logError("감옥 시스템 초기화 중 오류 발생", e)
            // 기본값 설정
            if (!::location.isInitialized) location = Location(Bukkit.getWorlds()[0], 0.0, 64.0, 0.0)
            if (!::pos1.isInitialized) pos1 = location
            if (!::pos2.isInitialized) pos2 = location
            if (pickaxe == null) pickaxe = ItemStack(Material.STONE_PICKAXE)
        }
    }

    fun saveData() {
        try {
            savePrisonData(Main.instance.config)
            savePrisonersData()
            Main.instance.logger.info("감옥 데이터가 성공적으로 저장되었습니다.")
        } catch (e: Exception) {
            logError("감옥 데이터 저장 중 오류 발생", e)
        }
    }

    private fun savePrisonersData() {
        Database.executeTransaction { conn ->
            conn.prepareStatement("DELETE FROM prisoners").use { it.executeUpdate() }
            conn.prepareStatement("INSERT INTO prisoners (uuid, release_time) VALUES (?, ?)").use { stmt ->
                for ((uuid, releaseTime) in prisoners) {
                    stmt.setString(1, uuid.toString())
                    stmt.setLong(2, releaseTime)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    fun loadPrisonData(config: FileConfiguration) {
        try {
            Main.instance.logger.info("감옥 데이터 로드 시작")

            val world = Bukkit.getWorld(config.getString("prison.world", "world") ?: "world")
            val x = config.getDouble("prison.x", 0.0)
            val y = config.getDouble("prison.y", 64.0)
            val z = config.getDouble("prison.z", 0.0)
            val yaw = config.getDouble("prison.yaw", 0.0).toFloat()
            val pitch = config.getDouble("prison.pitch", 0.0).toFloat()
            location = Location(world, x, y, z, yaw, pitch)
            Main.instance.logger.info("감옥 위치 로드: $location")

            pos1 = config.getSerializable("prison.pos1", Location::class.java) ?: location
            pos2 = config.getSerializable("prison.pos2", Location::class.java) ?: location
            Main.instance.logger.info("감옥 범위 로드: pos1=$pos1, pos2=$pos2")

            coalRegenerationTime = config.getLong("prison.coalRegenerationTime", 60)
            Main.instance.logger.info("석탄 재생성 시간 로드: $coalRegenerationTime 초")

            // pickaxe 로딩 부분 수정
            pickaxe = try {
                val pickaxeString = config.getString("prison.pickaxe")
                if (pickaxeString.isNullOrEmpty()) {
                    Main.instance.logger.info("곡괭이 정보가 없습니다. 기본 곡괭이로 설정합니다.")
                    ItemStack(Material.STONE_PICKAXE)
                } else {
                    Main.instance.logger.info("곡괭이 정보 로드 중")
                    deserializeFromString(pickaxeString)
                }
            } catch (e: Exception) {
                Main.instance.logger.warning("곡괭이 정보 로드 중 오류 발생, 기본 곡괭이로 설정됩니다: ${e.message}")
                ItemStack(Material.STONE_PICKAXE)
            }
            Main.instance.logger.info("곡괭이 설정 완료: ${pickaxe?.type}")

            npcId = config.getInt("prison.npcId", -1).takeIf { it != -1 }
            Main.instance.logger.info("NPC ID 로드: $npcId")

            if (Database.isInitialized()) {
                loadPrisonersData()
            } else {
                Main.instance.logger.warning("데이터베이스가 초기화되지 않아 감옥 유저 데이터를 로드할 수 없습니다.")
            }

            Main.instance.logger.info("감옥 정보가 성공적으로 로드되었습니다.")
        } catch (e: Exception) {
            logError("감옥 정보 로드 중 오류 발생", e)
            // 기본값 설정
            if (!::location.isInitialized) location = Location(Bukkit.getWorlds()[0], 0.0, 64.0, 0.0)
            if (!::pos1.isInitialized) pos1 = location
            if (!::pos2.isInitialized) pos2 = location
            if (pickaxe == null) pickaxe = ItemStack(Material.STONE_PICKAXE)
        }
    }

    private fun loadPrisonersData() {
        Database.executeTransaction { conn ->
            conn.prepareStatement("SELECT uuid, release_time FROM prisoners").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val uuid = UUID.fromString(rs.getString("uuid"))
                    val releaseTime = rs.getLong("release_time")
                    prisoners[uuid] = releaseTime
                }
            }
        }
    }

    fun setPrisonBounds(pos1: Location, pos2: Location) {
        this.pos1 = pos1
        this.pos2 = pos2
        savePrisonData(Main.instance.config)
    }

    fun isInPrisonBounds(location: Location): Boolean {
        val world = pos1.world
        val minX = minOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxX = maxOf(pos1.x, pos2.x)
        val maxY = maxOf(pos1.y, pos2.y)
        val maxZ = maxOf(pos1.z, pos2.z)

        return location.world == world &&
                location.x in minX..maxX &&
                location.y in minY..maxY &&
                location.z in minZ..maxZ
    }

    fun savePrisonData(config: FileConfiguration) {
        config.set("prison.world", location.world?.name)
        config.set("prison.x", location.x)
        config.set("prison.y", location.y)
        config.set("prison.z", location.z)
        config.set("prison.yaw", location.yaw)
        config.set("prison.pitch", location.pitch)
        config.set("prison.pickaxe", pickaxe?.serializeAsString())  // 여기를 수정
        config.set("prison.npcId", npcId)
        config.set("prison.pos1", pos1.serialize())
        config.set("prison.pos2", pos2.serialize())
        config.set("prison.coalRegenerationTime", _coalRegenerationTime)

        Main.instance.saveConfig()
        Main.instance.logger.info("감옥 정보가 저장되었습니다.")
    }

    private fun createPrisonBossBar(player: Player, duration: Int) {
        val bossBar = Bukkit.createBossBar(
            "감옥: ${formatTime((duration * 60).toLong())}",
            BarColor.RED,
            BarStyle.SOLID
        )
        bossBar.addPlayer(player)
        prisonerBossBars[player.uniqueId] = bossBar
    }

    // ItemStack을 문자열로 직렬화하는 함수
    private fun ItemStack?.serializeAsString(): String? {
        if (this == null) return null
        val outputStream = ByteArrayOutputStream()
        val dataOutput = BukkitObjectOutputStream(outputStream)
        dataOutput.writeObject(this)
        dataOutput.close()
        return Base64Coder.encodeLines(outputStream.toByteArray())
    }

    // 문자열에서 ItemStack으로 역직렬화하는 함수
    private fun deserializeFromString(data: String): ItemStack {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        val dataInput = BukkitObjectInputStream(inputStream)
        val item = dataInput.readObject() as ItemStack
        dataInput.close()
        return item
    }

    private fun updatePrisonBossBar(player: Player, remainingTime: Long) {
        val bossBar = prisonerBossBars[player.uniqueId] ?: return
        val totalTime = prisoners[player.uniqueId]?.minus(System.currentTimeMillis()) ?: return
        bossBar.progress = remainingTime.toDouble() / totalTime.toDouble()
        bossBar.setTitle("감옥: ${formatTime(remainingTime / 1000)}")
    }

    private fun removePrisonBossBar(player: Player) {
        prisonerBossBars.remove(player.uniqueId)?.removeAll()
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun updateLocation(newLocation: Location) {
        location = newLocation
        savePrisonData(Main.instance.config)
    }

    fun updatePickaxe(newPickaxe: ItemStack) {
        pickaxe = newPickaxe.clone()
        savePrisonData(Main.instance.config)
    }

    fun setNpcId(newNpcId: Int) {
        npcId = newNpcId
        savePrisonData(Main.instance.config)
    }

    fun getPrisonNPC(): NPC? {
        return npcId?.let { CitizensAPI.getNPCRegistry().getById(it) }
    }

    fun sendToPrison(player: Player, duration: Int) {
        val releaseTime = System.currentTimeMillis() + (duration * 60 * 1000)
        prisoners[player.uniqueId] = releaseTime

        player.teleport(location)
        player.inventory.clear()
        pickaxe?.let { player.inventory.addItem(it) }

        createPrisonBossBar(player, duration)
        startPrisonTimer(player)

        // 데이터베이스에 저장
        try {
            Database.executeTransaction { conn ->
                insertPrisonerData(conn, player.uniqueId, releaseTime, player.location)
                saveInventoryToDatabase(conn, player)
            }
        } catch (e: Exception) {
            logError("감옥 유저 데이터 저장 중 오류 발생", e)
        }
    }

    private fun startPrisonTimer(player: Player) {
        val task = Bukkit.getScheduler().runTaskTimer(Main.instance, Runnable {
            val releaseTime = prisoners[player.uniqueId] ?: return@Runnable
            val remainingTime = releaseTime - System.currentTimeMillis()
            if (remainingTime <= 0) {
                releasePrisoner(player)
                prisonerTimers[player.uniqueId]?.cancel()
                prisonerTimers.remove(player.uniqueId)
            } else {
                updatePrisonBossBar(player, remainingTime)
            }
        }, 0L, 20L) // 1초마다 체크
        prisonerTimers[player.uniqueId] = task
    }

    private fun saveInventoryToDatabase(conn: Connection, player: Player) {
        val inventoryJson = serializeInventory(player.inventory)
        conn.prepareStatement("INSERT INTO prisoner_inventories (uuid, inventory) VALUES (?, ?) ON DUPLICATE KEY UPDATE inventory = ?").use { stmt ->
            stmt.setString(1, player.uniqueId.toString())
            stmt.setString(2, inventoryJson)
            stmt.setString(3, inventoryJson)
            stmt.executeUpdate()
        }
    }

    fun reduceTime(player: Player, seconds: Int) {
        try {
            Database.executeTransaction { conn ->
                conn.prepareStatement("SELECT release_time FROM prisoners WHERE uuid = ?").use { stmt ->
                    stmt.setString(1, player.uniqueId.toString())
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val currentReleaseTime = rs.getLong("release_time")
                        val newReleaseTime = (currentReleaseTime - seconds * 1000).coerceAtLeast(System.currentTimeMillis())

                        conn.prepareStatement("UPDATE prisoners SET release_time = ? WHERE uuid = ?").use { updateStmt ->
                            updateStmt.setLong(1, newReleaseTime)
                            updateStmt.setString(2, player.uniqueId.toString())
                            updateStmt.executeUpdate()
                        }

                        prisoners[player.uniqueId] = newReleaseTime
                        val remainingTime = (newReleaseTime - System.currentTimeMillis()) / 1000
                        player.sendMessage("감옥 시간이 줄어들었습니다. 남은 시간: ${remainingTime}초")

                        if (newReleaseTime <= System.currentTimeMillis()) {
                            releasePrisoner(player)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError("감옥 시간 감소 중 오류 발생", e)
        }
    }

    fun releasePrisoner(player: Player) {
        try {
            Database.executeTransaction { conn ->
                val originalLocation = getOriginalLocation(conn, player.uniqueId)
                val restoredInventory = restoreInventoryFromDatabase(conn, player.uniqueId)
                deletePrisonerData(conn, player.uniqueId)
                removePrisonBossBar(player)
                prisoners.remove(player.uniqueId)
                prisonerTimers.remove(player.uniqueId)?.cancel()

                // 인벤토리 복원
                player.inventory.clear()
                restoredInventory?.let { items ->
                    for (i in 0 until player.inventory.size.coerceAtMost(items.size - 4)) {
                        player.inventory.setItem(i, items[i])
                    }
                    // 갑옷 슬롯 복원
                    player.inventory.helmet = items[items.size - 4]
                    player.inventory.chestplate = items[items.size - 3]
                    player.inventory.leggings = items[items.size - 2]
                    player.inventory.boots = items[items.size - 1]
                }

                originalLocation?.let { player.teleport(it) }
                player.sendMessage("당신은 이제 자유입니다.")
            }
        } catch (e: Exception) {
            logError("죄수 석방 중 오류 발생", e)
        }
    }


    fun onPlayerQuit(player: Player) {
        if (prisoners.containsKey(player.uniqueId)) {
            val remainingTime = prisoners[player.uniqueId]!! - System.currentTimeMillis()
            if (remainingTime > 0) {
                Database.executeTransaction { conn ->
                    updatePrisonerReleaseTime(conn, player.uniqueId, remainingTime)
                }
            }
            prisonerTimers.remove(player.uniqueId)?.cancel()
        }
        removePrisonBossBar(player)
    }

    fun onPlayerJoin(player: Player) {
        Database.executeTransaction { conn ->
            val releaseTime = getPrisonerReleaseTime(conn, player.uniqueId)
            if (releaseTime != null && releaseTime > System.currentTimeMillis()) {
                prisoners[player.uniqueId] = releaseTime
                player.teleport(location)
                player.inventory.clear()
                pickaxe?.let { player.inventory.addItem(it) }
                val remainingTime = (releaseTime - System.currentTimeMillis()) / 1000
                player.sendMessage("당신은 감옥에 ${formatTime(remainingTime)} 동안 더 있어야 합니다.")
                createPrisonBossBar(player, (remainingTime / 60).toInt())
                startPrisonTimer(player)
            }
        }
    }

    private fun checkPrisoners() {
        val currentTime = System.currentTimeMillis()
        val playersToRelease = prisoners.filter { (_, releaseTime) -> currentTime >= releaseTime }

        for ((uuid, _) in playersToRelease) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                releasePrisoner(player)
            } else {
                releaseOfflinePrisoner(uuid)
            }
        }
    }

    private fun updatePrisonerReleaseTime(conn: Connection, uuid: UUID, remainingTime: Long) {
        conn.prepareStatement("UPDATE prisoners SET release_time = ? WHERE uuid = ?").use { stmt ->
            stmt.setLong(1, System.currentTimeMillis() + remainingTime)
            stmt.setString(2, uuid.toString())
            stmt.executeUpdate()
        }
    }

    private fun getPrisonerReleaseTime(conn: Connection, uuid: UUID): Long? {
        conn.prepareStatement("SELECT release_time FROM prisoners WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getLong("release_time")
            }
        }
        return null
    }


    private fun restoreInventoryFromDatabase(conn: Connection, uuid: UUID): Array<ItemStack?>? {
        conn.prepareStatement("SELECT inventory FROM prisoner_inventories WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val inventoryJson = rs.getString("inventory")
                return deserializeInventory(inventoryJson)
            }
        }
        conn.prepareStatement("DELETE FROM prisoner_inventories WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeUpdate()
        }
        return null
    }

    private fun insertPrisonerData(conn: java.sql.Connection, uuid: UUID, releaseTime: Long, location: Location) {
        conn.prepareStatement("INSERT INTO prisoners (uuid, release_time, original_location) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE release_time = ?, original_location = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.setLong(2, releaseTime)
            stmt.setString(3, "${location.world?.name},${location.x},${location.y},${location.z}")
            stmt.setLong(4, releaseTime)
            stmt.setString(5, "${location.world?.name},${location.x},${location.y},${location.z}")
            stmt.executeUpdate()
        }
    }

    private fun getOriginalLocation(conn: java.sql.Connection, uuid: UUID): Location? {
        conn.prepareStatement("SELECT original_location FROM prisoners WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val locString = rs.getString("original_location")
                val locParts = locString.split(",")
                if (locParts.size == 4) {
                    val world = Bukkit.getWorld(locParts[0])
                    val x = locParts[1].toDouble()
                    val y = locParts[2].toDouble()
                    val z = locParts[3].toDouble()
                    return Location(world, x, y, z)
                }
            }
        }
        return null
    }

    private fun deletePrisonerData(conn: java.sql.Connection, uuid: UUID) {
        conn.prepareStatement("DELETE FROM prisoners WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeUpdate()
        }
    }

    private fun releaseOfflinePrisoner(uuid: UUID) {
        try {
            Database.executeTransaction { conn ->
                deletePrisonerData(conn, uuid)
                prisoners.remove(uuid)
            }
        } catch (e: Exception) {
            logError("오프라인 죄수 석방 중 오류 발생", e)
        }
    }

    private fun logError(message: String, e: Exception) {
        Main.instance.logger.severe("$message: ${e.message}")
        e.printStackTrace()
    }

    private fun serializeInventory(inventory: PlayerInventory): String {
        val outputStream = ByteArrayOutputStream()
        val dataOutput = BukkitObjectOutputStream(outputStream)

        // 인벤토리 크기 저장
        dataOutput.writeInt(inventory.size)

        // 각 아이템 저장
        for (i in 0 until inventory.size) {
            dataOutput.writeObject(inventory.getItem(i))
        }

        // 특수 슬롯 저장 (갑옷 등)
        dataOutput.writeObject(inventory.helmet)
        dataOutput.writeObject(inventory.chestplate)
        dataOutput.writeObject(inventory.leggings)
        dataOutput.writeObject(inventory.boots)

        dataOutput.close()
        return Base64Coder.encodeLines(outputStream.toByteArray())
    }

    private fun deserializeInventory(data: String): Array<ItemStack?> {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        val dataInput = BukkitObjectInputStream(inputStream)

        val items = ArrayList<ItemStack?>()

        // 인벤토리 크기 읽기
        val invSize = dataInput.readInt()

        // 각 아이템 읽기
        for (i in 0 until invSize) {
            items.add(dataInput.readObject() as? ItemStack)
        }

        // 특수 슬롯 읽기 (갑옷 등)
        val helmet = dataInput.readObject() as? ItemStack
        val chestplate = dataInput.readObject() as? ItemStack
        val leggings = dataInput.readObject() as? ItemStack
        val boots = dataInput.readObject() as? ItemStack

        dataInput.close()

        // 특수 슬롯 아이템을 배열의 적절한 위치에 추가
        items.add(helmet)
        items.add(chestplate)
        items.add(leggings)
        items.add(boots)

        return items.toTypedArray()
    }


}

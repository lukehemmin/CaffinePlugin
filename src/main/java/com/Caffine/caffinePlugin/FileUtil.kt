package me.caffeine.prison_Caffeine.util

import com.Caffine.caffinePlugin.Main
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object FileUtil {
    private val plugin: Main = JavaPlugin.getPlugin(Main::class.java)
    private var file: File? = null
    private var config: FileConfiguration? = null

    fun existsFile(dir: String, path: String): Boolean {
        val folder = File(plugin.dataFolder, "/$dir")
        if (!folder.exists()) {
            folder.mkdirs()
        }

        file = File(folder, "$path.yml")
        return file?.exists() ?: false
    }

    fun createFile(dir: String, path: String) {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        if (!existsFile(dir, path)) {
            try {
                file?.createNewFile()
                config = YamlConfiguration.loadConfiguration(file!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFile(dir: String, path: String) {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        if (existsFile(dir, path)) {
            file?.delete()
        }
    }

    fun saveDataFile(dir: String, path: String) {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        if (existsFile(dir, path)) {
            try {
                config?.save(file!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDataFile(dir: String, path: String, key: String, value: Any?) {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        if (existsFile(dir, path)) {
            config = YamlConfiguration.loadConfiguration(file!!)
            config?.set(key, value)
            saveDataFile(dir, path)
        }
    }

    fun getDataFile(dir: String, path: String, key: String): Any? {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        return if (existsFile(dir, path)) {
            config = YamlConfiguration.loadConfiguration(file!!)
            config?.get(key)
        } else {
            false
        }
    }

    fun getItemList(dir: String, path: String, key: String): Set<String>? {
        file = File(plugin.dataFolder, "/$dir/$path.yml")
        config = YamlConfiguration.loadConfiguration(file!!)
        val datas: ConfigurationSection? = config?.getConfigurationSection(key)
        return datas?.getKeys(false)
    }
}
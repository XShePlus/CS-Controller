package io.github.xsheeee.cs_controller.Tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Objects

object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val DEFAULT_MODE = "fast"
    private const val DEFAULT_FLOATING_WINDOW = true

    fun loadConfig(configPath: String?): ConfigData {
        val configData = ConfigData()

        try {
            val path = Paths.get(configPath)
            if (!Files.exists(path)) {
                Log.w(TAG, "Config file doesn't exist, using defaults")
                return configData
            }

            val jsonContent = String(Files.readAllBytes(path))
            val config = JSONObject(jsonContent)

            configData.defaultMode = config.optString("default", DEFAULT_MODE)
            configData.floatingWindowEnabled =
                config.optBoolean("floatingWindow", DEFAULT_FLOATING_WINDOW)

            val modes = arrayOf("powersave", "balance", "performance", "fast")
            for (mode in modes) {
                val apps = config.optJSONArray(mode)
                if (apps != null) {
                    for (i in 0 until apps.length()) {
                        val packageName = apps.getString(i)
                        configData.appModeMap[packageName] = mode
                    }
                }
            }

            Log.d(TAG, "Config loaded successfully")
            return configData
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config: " + e.message)
            return configData
        }
    }

    fun saveConfig(configPath: String?, configData: ConfigData) {
        try {
            val config = JSONObject()
            config.put("default", configData.defaultMode)
            config.put("floatingWindow", configData.floatingWindowEnabled)

            // 按模式分组应用
            val modeAppsMap: MutableMap<String, JSONArray> = HashMap()
            for ((packageName, mode) in configData.appModeMap) {
                if (!modeAppsMap.containsKey(mode)) {
                    modeAppsMap[mode] = JSONArray()
                }
                Objects.requireNonNull(modeAppsMap[mode])?.put(packageName)
            }

            // 添加应用列表到配置
            for (mode in arrayOf<String>("powersave", "balance", "performance", "fast")) {
                val apps = modeAppsMap[mode]
                if (apps != null && apps.length() > 0) {
                    config.put(mode, apps)
                }
            }

            // 写入文件
            val path = Paths.get(configPath)
            Files.createDirectories(path.parent)
            Files.write(path, config.toString(2).toByteArray())

            Log.d(TAG, "Config saved successfully")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving config: " + e.message)
        } catch (e: JSONException) {
            Log.e(TAG, "Error saving config: " + e.message)
        }
    }

    fun updateAppMode(configPath: String?, packageName: String, newMode: String) {
        val configData = loadConfig(configPath)

        // 从所有模式中移除该应用
        configData.appModeMap.remove(packageName)

        // 如果新模式不是默认模式，则添加到新模式
        if ("" != newMode && configData.defaultMode != newMode) {
            configData.appModeMap[packageName] = newMode
        }

        saveConfig(configPath, configData)
    }

    class ConfigData {
        var appModeMap: MutableMap<String, String> =
            HashMap()
        var defaultMode: String = DEFAULT_MODE
        var floatingWindowEnabled: Boolean = DEFAULT_FLOATING_WINDOW
    }
}
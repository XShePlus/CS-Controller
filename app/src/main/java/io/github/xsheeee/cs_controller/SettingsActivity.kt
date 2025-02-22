package io.github.xsheeee.cs_controller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.xsheeee.cs_controller.Tools.Logger
import io.github.xsheeee.cs_controller.Tools.Values
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private val switchKeys: MutableList<String> = ArrayList()
    private val configMap: MutableMap<String, Boolean> = HashMap()
    private val keyTranslations: MutableMap<String, String> = HashMap()
    private val keyDisplayMap: MutableMap<String, String> = HashMap()
    private var adapter: SwitchAdapter? = null

    private val filePickerLauncher = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data!!.data
            if (uri != null) {
                importTranslationFile(uri)
            }
        }
    }

    private fun initSwitchKeys() {
        switchKeys.clear()
        switchKeys.addAll(configMap.keys)
    }

    private fun setupPermissionSwitches() {
        // 检查无障碍权限
        val isAccessibilityEnabled = isAccessibilityServiceEnabled
        val switchAccessibility = findViewById<MaterialSwitch>(R.id.switchAccessibility)
        switchAccessibility.isChecked = isAccessibilityEnabled
        switchAccessibility.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                enableAccessibilityService()
            } else {
                disableAccessibilityService()
            }
        }

        // 读取配置文件控制悬浮窗开关
        val appConfigPath = Values.appConfig
        val isFloatingWindowEnabled = readFloatingWindowConfig(appConfigPath)
        val switchFloatingWindow = findViewById<MaterialSwitch>(R.id.switchFloatingWindow)
        switchFloatingWindow.isChecked = isFloatingWindowEnabled

        switchFloatingWindow.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            updateFloatingWindowConfig(appConfigPath, isChecked)
        }
    }

    private fun readFloatingWindowConfig(configPath: String): Boolean {
        try {
            val jsonContent = String(Files.readAllBytes(Paths.get(configPath)))
            val config = JSONObject(jsonContent)
            return config.optBoolean("floatingWindow", false)
        } catch (e: Exception) {
            showToast("读取悬浮窗配置失败：" + e.message)
            return false
        }
    }

    private fun updateFloatingWindowConfig(configPath: String, isEnabled: Boolean) {
        try {
            val jsonContent = String(Files.readAllBytes(Paths.get(configPath)))
            val config = JSONObject(jsonContent)
            config.put("floatingWindow", isEnabled)
            BufferedWriter(FileWriter(configPath)).use { writer ->
                writer.write(config.toString(2))
                writer.flush()
            }
        } catch (e: Exception) {
            showToast("更新悬浮窗配置失败：" + e.message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.backButton5)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }

        loadConfig()
        initSwitchKeys()

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SwitchAdapter(this, switchKeys, configMap, CONFIG_FILE_PATH)
        recyclerView.adapter = adapter

        loadTranslationsFromPreferences()
        reorderKeys()

        // 设置权限开关
        setupPermissionSwitches()
    }

    private val isAccessibilityServiceEnabled: Boolean
        get() {
            val enabledServices =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            return enabledServices != null
                    && enabledServices.contains(
                "io.github.xsheeee.cs_controller/io.github.xsheeee.cs_controller.MyAccessibilityService"
            )
        }

    private fun enableAccessibilityService() {
        try {
            // 使用Root权限开启无障碍服务
            var command =
                "settings put secure enabled_accessibility_services io.github.xsheeee.cs_controller/io.github.xsheeee.cs_controller.MyAccessibilityService"
            executeRootCommand(command)
            command = "settings put secure accessibility_enabled 1"
            executeRootCommand(command)
        } catch (e: Exception) {
            showToast("启用无障碍服务失败：" + e.message)
        }
    }

    private fun disableAccessibilityService() {
        try {
            // 使用Root权限关闭无障碍服务
            var command = "settings put secure enabled_accessibility_services ''"
            executeRootCommand(command)
            command = "settings put secure accessibility_enabled 0"
            executeRootCommand(command)
        } catch (e: Exception) {
            showToast("禁用无障碍服务失败：" + e.message)
        }
    }

    private fun executeRootCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            writer.write(command)
            writer.flush()
            writer.close()
            process.waitFor()
        } catch (e: Exception) {
            showToast("ERROR" + e.message)
        }
    }

    private fun loadConfig() {
        configMap.clear()
        try {
            BufferedReader(FileReader(CONFIG_FILE_PATH)).use { reader ->
                var line: String
                var inFunctionSection = false
                while ((reader.readLine().also { line = it }) != null) {
                    line = line.trim { it <= ' ' }
                    if (line == "[function]") {
                        inFunctionSection = true
                        continue
                    } else if (line.startsWith("[") && line != "[function]") {
                        inFunctionSection = false
                        continue
                    }
                    if (inFunctionSection && line.contains("=")) {
                        val parts = line.split("=".toRegex(), limit = 2).toTypedArray()
                        val key = parts[0].trim { it <= ' ' }
                        val value =
                            parts[1].trim { it <= ' ' }.lowercase(Locale.getDefault()).toBoolean()
                        configMap[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            Logger.showToast(
                this@SettingsActivity, getString(R.string.read_mode_error) + ": " + e.message
            )
        }
    }

    private fun saveConfig() {
        try {
            BufferedWriter(FileWriter(CONFIG_FILE_PATH)).use { writer ->
                writer.write("[function]\n")
                for ((key, value) in configMap) {
                    writer.write("$key=$value\n")
                }
            }
        } catch (e: Exception) {
            Logger.showToast(
                this@SettingsActivity, getString(R.string.save_mode_error) + ": " + e.message
            )
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("text/plain")
        filePickerLauncher.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_topbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.import_translation) {
            openFilePicker()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun importTranslationFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String
                while ((reader.readLine().also { line = it }) != null) {
                    line = line.trim { it <= ' ' }
                    if (line.contains("=")) {
                        val parts = line.split("=".toRegex(), limit = 2).toTypedArray()
                        val key = parts[0].trim { it <= ' ' }
                        val value = parts[1].trim { it <= ' ' }
                        keyTranslations[key] = value
                    }
                }
                inputStream.close()
                saveTranslationsToPreferences()
                applyTranslations()
                showToast("翻译文件导入成功！")
            }
        } catch (e: Exception) {
            showToast("翻译文件导入失败：" + e.message)
        }
    }

    private fun applyTranslations() {
        for (key in switchKeys) {
            if (keyTranslations.containsKey(key)) {
                // 如果有翻译，使用翻译，否则使用原始键
                keyDisplayMap[key] = keyTranslations[key] ?: key
            } else {
                keyDisplayMap[key] = key // 如果没有翻译，直接使用键
            }
        }
        adapter?.setKeyDisplayMap(keyDisplayMap)
        adapter?.notifyDataSetChanged()
    }


    private fun loadTranslationsFromPreferences() {
        val sharedPreferences = getSharedPreferences("Translations", MODE_PRIVATE)
        for (key in switchKeys) {
            val translation = sharedPreferences.getString(key, null)
            if (translation != null) {
                keyTranslations[key] = translation
            }
        }
        applyTranslations()
    }

    private fun saveTranslationsToPreferences() {
        val sharedPreferences = getSharedPreferences("Translations", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        for ((key, value) in keyTranslations) {
            editor.putString(key, value)
        }
        editor.apply()
    }

    private fun reorderKeys() {
        val validKeys: MutableList<String> = ArrayList()
        val invalidKeys: MutableList<String> = ArrayList()

        for (key in switchKeys) {
            if (configMap.containsKey(key)) {
                validKeys.add(key)
            } else {
                invalidKeys.add(key)
            }
        }

        switchKeys.clear()
        switchKeys.addAll(validKeys)
        switchKeys.addAll(invalidKeys)
    }

    private fun showToast(message: String) {
        Logger.showToast(this@SettingsActivity, message)
    }

    companion object {
        private const val CONFIG_FILE_PATH = Values.csSettingsPath
    }
}

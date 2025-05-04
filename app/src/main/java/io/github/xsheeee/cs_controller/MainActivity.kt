package io.github.xsheeee.cs_controller

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Tools
import io.github.xsheeee.cs_controller.tools.Values
import io.github.xsheeee.cs_controller.tools.SuManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class MainActivity : BaseActivity() {
    private lateinit var tools: Tools
    private lateinit var configTextView: TextView
    private lateinit var menu: AutoCompleteTextView
    private lateinit var versionTextView: TextView
    private lateinit var processStatusTextView: TextView
    private lateinit var processStatusCard: MaterialCardView
    private lateinit var rootWarningCard: MaterialCardView
    private lateinit var runCsServiceSh: MaterialCardView

    private var shouldKillMainProcess = false


    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
        private val CONFIG_FILE_PATH = Values.appConfig
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkStoragePermission()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                    excludeFromRecents(true)
                    shouldKillMainProcess = true

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (shouldKillMainProcess) {
                            killMainProcess()
                        }
                    }, 1000)

                    finish()
                }
            }
        )

        initializeViews()
        setupUI()
        setupListeners()
    }

    private fun initializeViews() {
        tools = Tools(applicationContext)
        configTextView = findViewById(R.id.config_text_view)
        versionTextView = findViewById(R.id.version_text_view)
        processStatusTextView = findViewById(R.id.process_status_text_view)
        processStatusCard = findViewById(R.id.process_status_card)
        rootWarningCard = findViewById(R.id.root_warning_card)
        runCsServiceSh = findViewById(R.id.run_service_sh)
        menu = findViewById<TextInputLayout>(R.id.menu).editText as AutoCompleteTextView
    }

    private fun setupUI() {
        setupDropdownMenu()
        updateConfigTextView()
        updateVersionTextView()
        updateProcessStatusTextView()
        checkRootStatus()
        runServiceSh()
    }

    private fun setupListeners() {
        findViewById<AppCompatImageView>(R.id.main_logo).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.go_app_list).setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.go_log).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.go_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.run_service_sh).setOnClickListener {
                try {
                    SuManager.exec("sh ${Values.CsServicePath}")
                } catch (e: Exception) {
                    log(e.message ?: "Unknown error","E")
                }
            updateConfigTextView()
        }
    }

    private fun setupDropdownMenu() {
        val items = resources.getStringArray(R.array.select_mode)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        menu.setAdapter(adapter)

        menu.setOnItemClickListener { _, _, position, _ ->
            changeModeInMainActivity(position + 1)
        }
    }

    private fun changeModeInMainActivity(mode: Int) {
        val modeName = tools.getModeName(mode)
        modeName?.let {
            tools.changeMode(it)
            val configFile = File(CONFIG_FILE_PATH)

            if (!configFile.exists()) {
                createDefaultConfigFile()
            }

            tools.readFileWithShell(CONFIG_FILE_PATH)?.takeIf { content -> content.isNotEmpty() }?.let { content ->
                try {
                    val jsonObject = JSONObject(content).apply {
                        put("default", modeName)
                    }

                    BufferedWriter(FileWriter(configFile)).use { writer ->
                        writer.write(jsonObject.toString(2))
                    }
                    updateConfigTextView()
                } catch (e: Exception) {
                    log(e.message ?: "Unknown error","E")
                    configTextView.setText("ERROR")
                }
            } ?: createDefaultConfigFile()
        } ?: run {
            configTextView.setText("ERROR")
        }
    }

    private fun createDefaultConfigFile() {
        try {
            val jsonObject = JSONObject().apply {
                put("default", "powersave")
                put("log", "Disable")
                put("floatingWindow", false)
                put("powersave", JSONArray())
                put("balance", JSONArray())
                put("performance", JSONArray())
                put("fast", JSONArray())
            }

            BufferedWriter(FileWriter(CONFIG_FILE_PATH)).use { writer ->
                writer.write(jsonObject.toString(2))
            }
        } catch (e: Exception) {
            log(e.message ?: "Unknown error","E")
            configTextView.setText("ERROR")
        }
    }

    private fun updateConfigTextView() {
        val configFile = File(CONFIG_FILE_PATH)
        if (configFile.exists()) {
            tools.readFileWithShell(CONFIG_FILE_PATH)?.let { content ->
                val modeString = getString(R.string.now_mode)
                val defaultMode = getDefaultModeFromConfig(content)
                configTextView.text = "$modeString$defaultMode"
            } ?: run {
                configTextView.setText(R.string.read_mode_error)
            }
        } else {
            configTextView.setText(R.string.read_mode_error)
        }

        updateVersionTextView()
        updateProcessStatusTextView()
        runServiceSh()
        updateProcessStatusTextView()
    }

    private fun getDefaultModeFromConfig(fileContent: String): String {
        return try {
            val jsonObject = JSONObject(fileContent)
            if (jsonObject.has("default")) jsonObject.getString("default") else "Unknown"
        } catch (e: JSONException) {
            "Unknown"
        }
    }
    // cs版本
    private fun updateVersionTextView() {
        tools.versionFromModuleProp?.let { version ->
            val versionString = getString(R.string.cs_version)
            versionTextView.text = "$versionString$version"
        } ?: run {
            versionTextView.setText(R.string.read_version_error)
        }
    }
    // 更新进程状态文本视图
    fun updateProcessStatusTextView() {
        val statusPrefix = getString(R.string.cs_work)
        if (tools.isProcessRunning()) {
            processStatusTextView.text = "$statusPrefix${getString(R.string.cs_work_true)}"
            val typedValue = TypedValue()
            theme.resolveAttribute(R.attr.colorSecondaryContainer, typedValue, true)
            processStatusCard.setCardBackgroundColor(typedValue.data)
        } else {
            processStatusTextView.text = "$statusPrefix${getString(R.string.cs_work_false)}"
            val typedValue = TypedValue()
            theme.resolveAttribute(R.attr.colorErrorContainer, typedValue, true)
            processStatusCard.setCardBackgroundColor(typedValue.data)
        }
        processStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    }
    // 运行service.sh(救活cs调度)
    fun runServiceSh() {
        if (tools.isProcessRunning()) {
            val alphaAnimation = AlphaAnimation(1f, 0f).apply {
                duration = 100
            }
            // 消失动画
            val scaleAnimation = ScaleAnimation(
                1f, 0.5f, 1f, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 100
            }

            AnimationSet(true).apply {
                addAnimation(alphaAnimation)
                addAnimation(scaleAnimation)
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        runCsServiceSh.visibility = MaterialCardView.GONE
                    }
                    override fun onAnimationRepeat(animation: Animation) {}
                })
            }.also { runCsServiceSh.startAnimation(it) }
        } else {
            runCsServiceSh.visibility = MaterialCardView.VISIBLE
        }
    }
    // ROOT 检查
    private fun checkRootStatus() {
        rootWarningCard.visibility = if (SuManager.isSuAvailable()) {
            MaterialCardView.GONE
        } else {
            MaterialCardView.VISIBLE
        }
    }
    // 检查文件读写权限
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Logger.showToast(this, "需要开启管理所有文件权限")
                requestManageAllFilesPermission()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }
    }
    // 管理所有文件权限
    private fun requestManageAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
    // 存储权限
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.showToast(this, "存储权限已授予")
            } else {
                Logger.showToast(this, "存储权限被拒绝")
            }
        }
    }

    private fun excludeFromRecents(exclude: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.appTasks?.firstOrNull()?.setExcludeFromRecents(exclude)
        }
    }

    override fun onResume() {
        super.onResume()
        excludeFromRecents(false)
    }
    // 杀死进程
    private fun killMainProcess() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

}
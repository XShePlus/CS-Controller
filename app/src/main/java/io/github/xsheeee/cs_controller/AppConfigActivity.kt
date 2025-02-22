package io.github.xsheeee.cs_controller

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import io.github.xsheeee.cs_controller.Tools.Values
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class AppConfigActivity : AppCompatActivity() {
    private var appNameTextView: TextView? = null
    private var packageNameTextView: TextView? = null
    private var versionTextView: TextView? = null
    private var versionCodeTextView: TextView? = null
    private var appIconImageView: ImageView? = null
    private var dropdownButton: MaterialButton? = null
    private var currentPackageName: String? = null
    private var configJson: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_config)

        initializeViews()
        setupToolbar()

        currentPackageName = intent.getStringExtra("pName")
        if (currentPackageName != null) {
            fetchAppInfo(currentPackageName!!)
            loadConfigurations()
            setupModeSelection()
        }
    }

    private fun initializeViews() {
        appNameTextView = findViewById(R.id.appNameTextView)
        packageNameTextView = findViewById(R.id.packageNameTextView)
        versionTextView = findViewById(R.id.versionTextView)
        versionCodeTextView = findViewById(R.id.versionCodeTextView)
        appIconImageView = findViewById(R.id.iconImageView)
        dropdownButton = findViewById(R.id.dropdownButton)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.backButton2)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }
    }

    private fun loadConfigurations() {
        val configFile = File(Values.appConfig)
        if (!configFile.exists()) {
            createDefaultConfigFile()
            return
        }

        try {
            val content = StringBuilder()
            BufferedReader(FileReader(configFile)).use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    content.append(line)
                }
            }
            configJson = JSONObject(content.toString())
        } catch (e: IOException) {
            createDefaultConfigFile()
        } catch (e: JSONException) {
            createDefaultConfigFile()
        }
    }

    private fun saveConfigurations() {
        try {
            FileWriter(Values.appConfig).use { writer ->
                writer.write(
                    configJson!!.toString(2)
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private val currentMode: String
        get() {
            if (configJson == null) return "跟随全局"

            try {
                for (mode in MODES) {
                    if (mode == "跟随全局") continue
                    val modeArray = configJson!!.optJSONArray(mode)
                    if (modeArray != null) {
                        for (i in 0 until modeArray.length()) {
                            if (currentPackageName == modeArray.getString(i)) {
                                return mode
                            }
                        }
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return "跟随全局"
        }

    private fun setupModeSelection() {
        val currentMode = currentMode
        dropdownButton!!.text = currentMode
        dropdownButton!!.setOnClickListener { v: View ->
            this.showModeMenu(
                v
            )
        }
    }

    private fun showModeMenu(v: View) {
        val popup = PopupMenu(this, v)
        for (i in MODES.indices) {
            popup.menu.add(Menu.NONE, i, i, MODES[i])
        }

        popup.setOnMenuItemClickListener { item: MenuItem ->
            val selectedMode = MODES[item.itemId]
            updateAppMode(selectedMode)
            dropdownButton!!.text = selectedMode
            true
        }

        popup.show()
    }

    private fun updateAppMode(newMode: String) {
        try {
            // Remove package from all modes first
            for (mode in MODES) {
                if (mode == "跟随全局") continue
                val modeArray = configJson!!.optJSONArray(mode)
                if (modeArray != null) {
                    val newArray = JSONArray()
                    for (i in 0 until modeArray.length()) {
                        val pkg = modeArray.getString(i)
                        if (pkg != currentPackageName) {
                            newArray.put(pkg)
                        }
                    }
                    configJson!!.put(mode, newArray)
                }
            }

            // Add to new mode if not "跟随全局"
            if (newMode != "跟随全局") {
                var modeArray = configJson!!.optJSONArray(newMode)
                if (modeArray == null) {
                    modeArray = JSONArray()
                    configJson!!.put(newMode, modeArray)
                }
                modeArray.put(currentPackageName)
            }

            saveConfigurations()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAppInfo(packageName: String) {
        val pm = packageManager

        try {
            val applicationInfo = pm.getApplicationInfo(packageName, 0)
            val packageInfo = pm.getPackageInfo(packageName, 0)

            val icon = pm.getApplicationIcon(applicationInfo)
            val appName = pm.getApplicationLabel(applicationInfo).toString()
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.longVersionCode.toInt()

            updateAppInfoDisplay(icon, appName, packageName, versionName, versionCode)

            if (supportActionBar != null) {
                supportActionBar!!.subtitle = appName
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun updateAppInfoDisplay(
        icon: Drawable, appName: String, packageName: String,
        versionName: String?, versionCode: Int
    ) {
        appNameTextView!!.text = appName
        packageNameTextView!!.text = packageName
        versionTextView!!.text = getString(R.string.cs_version) + versionName
        versionCodeTextView!!.text = getString(R.string.version_code) + versionCode
        appIconImageView!!.setImageDrawable(icon)
    }

    private fun createDefaultConfigFile() {
        try {
            val defaultConfig = JSONObject()
            defaultConfig.put("default", true)
            defaultConfig.put("floatingWindow", false)

            for (mode in MODES) {
                if (mode != "跟随全局") {
                    defaultConfig.put(mode, JSONArray())
                }
            }

            configJson = defaultConfig
            saveConfigurations()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    companion object {
        private val MODES = arrayOf("跟随全局", "powersave", "balance", "performance", "fast")
    }
}
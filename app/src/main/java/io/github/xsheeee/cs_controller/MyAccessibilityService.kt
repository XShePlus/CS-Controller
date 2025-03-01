package io.github.xsheeee.cs_controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import io.github.xsheeee.cs_controller.Tools.ConfigManager
import io.github.xsheeee.cs_controller.Tools.ConfigManager.ConfigData
import io.github.xsheeee.cs_controller.Tools.Logger
import io.github.xsheeee.cs_controller.Tools.Values
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MyAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var handler: Handler? = null
    private var previousWindow = "未知"
    private var notificationManager: NotificationManager? = null
    private var appModeMap: MutableMap<String, String> = HashMap()
    private var defaultMode = "fast"
    private var currentMode = defaultMode
    private var configObserver: FileObserver? = null
    private val cachedDesktopApps: MutableSet<String> = HashSet()

    private var floatingWindowEnabled = true
    private val floatingWindowLogicEnabled = true
    private var isServiceInitialized = false

    private var configData: ConfigData? = null

    private fun loadAppConfig() {
        configData = ConfigManager.loadConfig(Values.appConfig)
        appModeMap = configData?.appModeMap as MutableMap<String, String>
        defaultMode = configData!!.defaultMode
        floatingWindowEnabled = configData!!.floatingWindowEnabled

        updateFloatingWindowState()
    }

    private fun updateAppMode(packageName: String, mode: String) {
        ConfigManager.updateAppMode(Values.appConfig, packageName, mode)
        loadAppConfig() // 重新加载配置
    }

    override fun onCreate() {
        super.onCreate()
        try {
            initializeService()
        } catch (e: Exception) {
            logError("Service initialization failed: " + e.message,"E")
            // 延迟3秒后重试初始化
            if (handler != null) {
                handler!!.postDelayed({ this.initializeService() }, 3000)
            } else {
                handler = Handler(Looper.getMainLooper())
                handler!!.postDelayed({ this.initializeService() }, 3000)
            }
        }
    }

    private fun initializeService() {
        if (isServiceInitialized) {
            return
        }

        try {
            handler = Handler(Looper.getMainLooper())
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // 创建必要的目录
            createRequiredDirectories()

            loadAppConfig()
            setupConfigObserver()
            showNotification()
            checkOverlayPermission()

            isServiceInitialized = true
            Log.d(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            logError("Failed to initialize service: " + e.message,"E")
            handler!!.postDelayed({ this.initializeService() }, 3000)
        }
    }

    private fun createRequiredDirectories() {
        try {
            val configParent = File(Values.appConfig).parentFile

            if (configParent != null && !configParent.exists()) {
                val created = configParent.mkdirs()
                Log.d(
                    TAG,
                    "Config parent directory created: $created"
                )
            }
        } catch (e: Exception) {
            logError("Error creating directories: " + e.message,"E")
            throw e
        }
    }

    private fun updateFloatingWindowState() {
        try {
            if (floatingWindowEnabled && floatingWindowLogicEnabled) {
                if (floatingView == null) {
                    createFloatingWindow()
                }
            } else {
                removeFloatingWindow()
            }
        } catch (e: Exception) {
            logError("Error updating floating window state: " + e.message,"E")
        }
    }

    private fun createFloatingWindow() {
        if (floatingView != null || !floatingWindowEnabled || !floatingWindowLogicEnabled) {
            return
        }

        try {
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED),
                    -3
                )
            params.gravity = Gravity.TOP
            params.alpha = 0.8f

            windowManager!!.addView(floatingView, params)
        } catch (e: Exception) {
            logError("Error creating floating window: " + e.message,"E")
            floatingView = null
        }
    }

    private fun removeFloatingWindow() {
        if (floatingView != null) {
            try {
                windowManager!!.removeView(floatingView)
            } catch (e: Exception) {
                logError("Error removing floating window: " + e.message,"E")
            } finally {
                floatingView = null
            }
        }
    }

    private fun setupConfigObserver() {
        val configFile = File(Values.appConfig)
        val configDirectory = configFile.parentFile

        if (configObserver != null) {
            configObserver!!.stopWatching()
        }

        if (configDirectory != null) {
            configObserver =
                object : FileObserver(
                    configDirectory,
                    (MODIFY
                            or CREATE
                            or DELETE
                            or MOVED_TO)
                ) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && path == configFile.name) {
                            handler!!.post {
                                Log.d(
                                    TAG,
                                    "Config file changed, event: $event"
                                )
                                try {
                                    loadAppConfig()
                                } catch (e: Exception) {
                                    logError("Failed to reload config: " + e.message,"E")
                                }
                            }
                        }
                    }
                }

            try {
                (configObserver as FileObserver).startWatching()
                Log.d(TAG, "File observer started successfully")
            } catch (e: Exception) {
                logError("Failed to start file observer: " + e.message,"E")
                handler!!.postDelayed({ this.setupConfigObserver() }, 5000)
            }
        }
    }

    private fun applyConfigChanges(
        newAppModeMap: MutableMap<String, String>,
        newDefaultMode: String,
        newFloatingWindowEnabled: Boolean
    ) {
        appModeMap = newAppModeMap
        defaultMode = newDefaultMode
        floatingWindowEnabled = newFloatingWindowEnabled
        updateFloatingWindowState()
    }

    private fun updateCSConfig(mode: String) {
        if (mode != currentMode) {
            try {
                FileWriter(Values.CSConfigPath).use { writer ->
                    writer.write(mode)
                    currentMode = mode
                    Log.d(
                        TAG,
                        "CS config updated to: $mode"
                    )
                }
            } catch (e: IOException) {
                logError("Error updating CS config: " + e.message,"E")
            }
        }
    }

    private fun getAppMode(packageName: String): String? {
        return configData!!.appModeMap.getOrDefault(packageName, configData!!.defaultMode)
    }

    private fun isDefaultDesktopApp(packageName: String): Boolean {
        if (cachedDesktopApps.contains(packageName)) {
            return true
        }

        try {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfoList =
                pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)

            for (resolveInfo in resolveInfoList) {
                if (packageName == resolveInfo.activityInfo.packageName) {
                    cachedDesktopApps.add(packageName)
                    return true
                }
            }
        } catch (e: Exception) {
            logError("Error checking desktop app: " + e.message,"E")
        }

        return false
    }

    override fun onServiceConnected() {
        try {
            val info = AccessibilityServiceInfo()
            info.eventTypes =
                (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        or AccessibilityEvent.TYPE_VIEW_CLICKED
                        or AccessibilityEvent.TYPE_VIEW_FOCUSED)
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            serviceInfo = info
            Log.d(TAG, "Service connected successfully")
        } catch (e: Exception) {
            logError("Error in onServiceConnected: " + e.message,"E")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.source == null) return

        try {
            val currentWindow =
                if (event.packageName != null) event.packageName.toString() else "未知"

            if (currentWindow != previousWindow) {
                val debugInfo = generateDebugInfo(event, currentWindow)
                updateFloatingWindow(debugInfo)
                previousWindow = currentWindow
                Log.d(TAG, debugInfo)

                if (currentWindow != "未知") {
                    val actualPackage =
                        if (shouldExclude(event, currentWindow)) previousWindow else currentWindow
                    val mode = getAppMode(actualPackage)
                    updateCSConfig(mode!!)
                }
            }
        } catch (e: Exception) {
            logError("Error processing accessibility event: " + e.message,"E")
        }
    }

    private fun generateDebugInfo(event: AccessibilityEvent, currentWindow: String): String {
        if (event.source == null) return "-1"

        try {
            val rect = Rect()
            event.source!!.getBoundsInScreen(rect)

            val actualPackage =
                if (shouldExclude(event, currentWindow)) previousWindow else currentWindow
            val mode = getAppMode(actualPackage)

            return String.format(
                "屏幕：%d x %d\n事件: %s\n来源：%s\n层级: %d\n类型：%d\n此前: %s\n现在: %s\n实际: %s\n当前模式: %s",
                realScreenHeight,
                realScreenWidth,
                AccessibilityEvent.eventTypeToString(event.eventType),
                event.className,
                event.source!!.childCount,
                event.eventType,
                previousWindow,
                currentWindow,
                actualPackage,
                mode
            )
        } catch (e: Exception) {
            logError("Error generating debug info: " + e.message,"E")
            return "-1"
        }
    }

    private fun shouldExclude(event: AccessibilityEvent, packageName: String): Boolean {
        return event.className != null
                && (event.className.toString() == "android.inputmethodservice.SoftInputWindow"
                || event.className.toString() == "android.widget.FrameLayout"
                || previousWindow == "android")
    }

    private fun updateFloatingWindow(debugInfo: String) {
        if (floatingView != null) {
            handler!!.post {
                try {
                    val textView = floatingView!!.findViewById<TextView>(R.id.debug_info)
                    if (textView != null) {
                        textView.text = debugInfo
                    }
                } catch (e: Exception) {
                    logError("Error updating floating window text: " + e.message,"E")
                }
            }
        }
    }

    private fun showNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID, "CS Controller Service", NotificationManager.IMPORTANCE_LOW
                    )
                notificationManager!!.createNotificationChannel(channel)
            }

            val notification =
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("CS Controller 正在运行")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            logError("Error showing notification: " + e.message,"E")
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    Toast.makeText(this, "请开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                    val intent =
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    logError("Error requesting overlay permission: " + e.message,"E")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // 如果服务还没初始化，尝试初始化
        if (!isServiceInitialized) {
            initializeService()
        }

        // 确保服务被系统杀死后会重新创建
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        try {
            // 在应用被从最近任务列表移除时重启服务
            val restartServiceIntent = Intent(applicationContext, this.javaClass)
            restartServiceIntent.setPackage(packageName)
            startService(restartServiceIntent)
        } catch (e: Exception) {
            logError("Error restarting service: " + e.message,"E")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        // 服务中断时的处理
        handler!!.postDelayed({ this.initializeService() }, 3000)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service being destroyed")
        cleanup()

        // 尝试重启服务
        try {
            val restartServiceIntent = Intent(applicationContext, this.javaClass)
            restartServiceIntent.setPackage(packageName)
            startService(restartServiceIntent)
        } catch (e: Exception) {
            logError("Failed to restart service: " + e.message,"E")
        }

        super.onDestroy()
    }

    private fun cleanup() {
        try {
            isServiceInitialized = false

            if (floatingView != null) {
                windowManager!!.removeView(floatingView)
                floatingView = null
            }

            if (configObserver != null) {
                configObserver!!.stopWatching()
                configObserver = null
            }

            notificationManager!!.cancel(NOTIFICATION_ID)

            // 清理其他资源
            appModeMap.clear()
            cachedDesktopApps.clear()

            Log.d(TAG, "Service cleanup completed")
        } catch (e: Exception) {
            logError("Error during cleanup: " + e.message,"E")
        }
    }

    private val realScreenWidth: Int
        get() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                    return windowManager!!.currentWindowMetrics.bounds.width()
                } else { // Android 10-
                    val display = windowManager!!.defaultDisplay
                    val size = Point()
                    display.getSize(size)
                    return size.x
                }
            } catch (e: Exception) {
                logError("Error getting screen width: " + e.message,"E")
                return -1
            }
        }

    private val realScreenHeight: Int
        get() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                    return windowManager!!.currentWindowMetrics.bounds.height()
                } else { // Android 10-
                    val display = windowManager!!.defaultDisplay
                    val size = Point()
                    display.getSize(size)
                    return size.y
                }
            } catch (e: Exception) {
                logError("Error getting screen height: " + e.message,"E")
                return -2
            }
        }

    private fun logError(message: String,level: String) {
        Logger.writeLog(TAG,level,message)
    }

    companion object {
        private const val TAG = "Accessibility"
        private const val CHANNEL_ID = "cs_controller_channel"
        private const val NOTIFICATION_ID = 1
    }
}

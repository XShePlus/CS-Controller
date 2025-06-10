package io.github.xsheeee.cs_controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import io.github.xsheeee.cs_controller.tools.ConfigManager
import io.github.xsheeee.cs_controller.tools.ConfigManager.ConfigData
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Values
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

class MyAccessibilityService : AccessibilityService() {
    // 核心组件
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var notificationManager: NotificationManager? = null

    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 状态管理
    private val isServiceInitialized = AtomicBoolean(false)
    private val serviceIsConnected = AtomicBoolean(false)

    // 应用配置
    private var configData: ConfigData? = null
    @Volatile private var appModeMap: MutableMap<String, String> = HashMap()
    @Volatile private var defaultMode = "fast"
    @Volatile private var currentMode = defaultMode
    @Volatile private var floatingWindowEnabled = true
    private val floatingWindowLogicEnabled = true

    // 文件观察器
    private var configObserver: FileObserver? = null

    // 窗口和应用状态
    @Volatile private var previousWindow = "未知"
    private val launcherPackages = mutableSetOf<String>()
    private val inputMethods = mutableSetOf<String>()

    // 屏幕信息
    @Volatile private var displayWidth = 1080
    @Volatile private var displayHeight = 2340
    @Volatile private var isLandscape = false
    @Volatile private var isTablet = false

    // 缓存优化
    private val windowIdCaches = LruCache<Int, String>(20)

    // 事件时间戳管理
    private val lastEventTime = AtomicLong(0)
    private val lastWindowChanged = AtomicLong(0)
    private val lastAnalyseThread = AtomicLong(0)

    // 轮询管理
    private var pollingJob: Job? = null
    private val pollingTimeout = 7000L
    private val pollingInterval = 3000L

    // 黑名单窗口类型
    private val blackTypeList = setOf(
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
        AccessibilityWindowInfo.TYPE_INPUT_METHOD,
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
        AccessibilityWindowInfo.TYPE_SYSTEM
    )

    private val blackTypeListBasic = setOf(
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
        AccessibilityWindowInfo.TYPE_INPUT_METHOD,
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
    )

    companion object {
        private const val TAG = "Accessibility"
        private const val CHANNEL_ID = "cs_controller_channel"
        private const val NOTIFICATION_ID = 1

        // 防抖动相关常量
        private const val DEBOUNCE_DELAY = 100L
        private const val MIN_WINDOW_CHANGE_INTERVAL = 500L
    }

    // 使用 java.io 的文件操作工具方法
    private fun writeToFile(filePath: String, content: String): Boolean {
        return try {
            val file = File(filePath)
            // 确保父目录存在
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }

            BufferedWriter(FileWriter(file)).use { writer ->
                writer.write(content)
                writer.flush()
            }
            true
        } catch (e: IOException) {
            Logger.e(TAG, "写入文件失败: ${e.message}")
            false
        } catch (e: Exception) {
            Logger.e(TAG, "写入文件异常: ${e.message}")
            false
        }
    }

    private fun readFromFile(filePath: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return ""
            }

            BufferedReader(FileReader(file)).use { reader ->
                reader.readText()
            }
        } catch (e: IOException) {
            Logger.e(TAG, "读取文件失败: ${e.message}")
            ""
        } catch (e: Exception) {
            Logger.e(TAG, "读取文件异常: ${e.message}")
            ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeService()
    }

    private fun initializeService() {
        if (isServiceInitialized.get()) return

        serviceScope.launch {
            try {
                // 初始化基础组件
                initializeBasicComponents()

                // 获取屏幕信息
                updateDisplayInfo()

                // 并行执行初始化任务
                val jobs = listOf(
                    async { initLauncherPackages() },
                    async { loadInputMethods() },
                    async { createRequiredDirectories() },
                    async { loadAppConfig() }
                )

                // 等待所有异步任务完成
                jobs.awaitAll()

                // 完成初始化
                setupConfigObserver()
                showNotification()
                checkOverlayPermissionAsync()

                isServiceInitialized.set(true)
                Logger.d(TAG, "Service initialized successfully")

            } catch (e: Exception) {
                Logger.e(TAG, "Service initialization failed: ${e.message}")
                // 延迟重试初始化
                delay(3000)
                if (!isServiceInitialized.get()) {
                    initializeService()
                }
            }
        }
    }

    private fun initializeBasicComponents() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun updateCSConfig(mode: String) {
        if (mode == currentMode) return

        backgroundScope.launch {
            try {
                Logger.d(TAG, "模式变化: $currentMode -> $mode")
                val success = writeToFile(Values.CSConfigPath, mode)
                if (success) {
                    currentMode = mode
                    Logger.d(TAG, "CS config成功写入: $mode")
                } else {
                    Logger.e(TAG, "文件写入失败")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "updateCSConfig异常: ${e.message}")
            }
        }
    }

    private suspend fun loadAppConfig() {
        withContext(Dispatchers.IO) {
            try {
                configData = ConfigManager.loadConfig(Values.appConfig)
                configData?.let { config ->
                    appModeMap = config.appModeMap as MutableMap<String, String>
                    defaultMode = config.defaultMode
                    floatingWindowEnabled = config.floatingWindowEnabled

                    // 读取当前模式
                    val storedMode = readFromFile(Values.CSConfigPath)
                    currentMode = if (storedMode.isNotEmpty()) storedMode else defaultMode

                    Logger.d(TAG, "配置加载完成，当前模式: $currentMode")
                }

                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    updateFloatingWindowState()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "配置加载失败: ${e.message}")
                currentMode = defaultMode
            }
        }
    }

    private suspend fun loadInputMethods() {
        withContext(Dispatchers.IO) {
            try {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE)
                val method = inputMethodManager.javaClass.getMethod("getInputMethodList")
                val inputMethodInfos = method.invoke(inputMethodManager) as List<*>

                val newInputMethods = mutableSetOf<String>()
                for (imi in inputMethodInfos) {
                    val packageName = imi?.javaClass?.getMethod("getPackageName")?.invoke(imi) as? String
                    packageName?.let { newInputMethods.add(it) }
                }

                synchronized(inputMethods) {
                    inputMethods.clear()
                    inputMethods.addAll(newInputMethods)
                }

                Logger.d(TAG, "输入法加载完成: ${inputMethods.size}个")
            } catch (e: Exception) {
                Logger.e(TAG, "输入法加载失败: ${e.message}")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenConfiguration(newConfig)
        updateDisplayInfo()
    }

    private fun updateScreenConfiguration(newConfig: Configuration) {
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun updateDisplayInfo() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val point = Point()
            wm.defaultDisplay.getRealSize(point)

            if (point.x != displayWidth || point.y != displayHeight) {
                displayWidth = point.x
                displayHeight = point.y
                Logger.d(TAG, "屏幕尺寸更新: ${displayWidth}x${displayHeight}")
            }

            isTablet = resources.configuration.screenLayout and
                    Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE

        } catch (e: Exception) {
            Logger.e(TAG, "获取屏幕尺寸失败: ${e.message}")
        }
    }

    private suspend fun initLauncherPackages() {
        withContext(Dispatchers.IO) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }

                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                val newLauncherPackages = mutableSetOf<String>()

                for (resolveInfo in resolveInfos) {
                    resolveInfo.activityInfo?.packageName?.let { packageName ->
                        newLauncherPackages.add(packageName)
                    }
                }

                // 添加系统包名
                val systemPackages = setOf(
                    "com.android.systemui", "android", "com.android.settings.overlay",
                    "com.android.phone", "com.android.incallui", "com.android.server.telecom"
                )
                newLauncherPackages.addAll(systemPackages)

                synchronized(launcherPackages) {
                    launcherPackages.clear()
                    launcherPackages.addAll(newLauncherPackages)
                }

                Logger.d(TAG, "桌面包名加载完成: ${launcherPackages.size}个")
            } catch (e: Exception) {
                Logger.e(TAG, "桌面包名加载失败: ${e.message}")
            }
        }
    }

    private suspend fun createRequiredDirectories() {
        withContext(Dispatchers.IO) {
            try {
                val configParent = File(Values.appConfig).parentFile
                if (configParent != null && !configParent.exists()) {
                    // 先尝试使用 java.io 创建目录
                    val created = configParent.mkdirs()
                    if (!created) {
                        // 如果普通方式失败，再尝试使用 root 权限
                        Shell.getShell { shell ->
                            if (shell.isRoot) {
                                shell.newJob()
                                    .add("mkdir -p ${configParent.absolutePath}")
                                    .exec()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "创建目录失败: ${e.message}")
            }
        }
    }

    private fun updateFloatingWindowState() {
        if (floatingWindowEnabled && floatingWindowLogicEnabled) {
            if (floatingView == null) {
                createFloatingWindow()
            }
        } else {
            removeFloatingWindow()
        }
    }

    @SuppressLint("InflateParams")
    private fun createFloatingWindow() {
        if (floatingView != null || !floatingWindowEnabled || !floatingWindowLogicEnabled) {
            return
        }

        try {
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED),
                -3
            ).apply {
                gravity = Gravity.TOP
                alpha = 0.8f
            }

            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Logger.e(TAG, "创建悬浮窗失败: ${e.message}")
            floatingView = null
        }
    }

    private fun removeFloatingWindow() {
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Logger.e(TAG, "移除悬浮窗失败: ${e.message}")
            } finally {
                floatingView = null
            }
        }
    }

    private fun setupConfigObserver() {
        val configFile = File(Values.appConfig)
        val configDirectory = configFile.parentFile ?: return

        configObserver?.stopWatching()

        configObserver = object : FileObserver(
            configDirectory,
            MODIFY or CREATE or DELETE or MOVED_TO
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == configFile.name) {
                    serviceScope.launch {
                        delay(DEBOUNCE_DELAY) // 防抖动
                        Logger.d(TAG, "配置文件变化，重新加载")
                        loadAppConfig()
                    }
                }
            }
        }

        try {
            configObserver?.startWatching()
            Logger.d(TAG, "文件观察器启动成功")
        } catch (e: Exception) {
            Logger.e(TAG, "文件观察器启动失败: ${e.message}")
        }
    }

    private fun getAppMode(packageName: String): String {
        return configData?.appModeMap?.get(packageName) ?: configData?.defaultMode ?: defaultMode
    }

    override fun onServiceConnected() {
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            }

            serviceInfo = info
            serviceIsConnected.set(true)

            updateDisplayInfo()

            // 延迟检测当前前台应用
            serviceScope.launch {
                delay(1000)
                modernModeEvent()
            }

            Log.d(TAG, "服务连接成功")
        } catch (e: Exception) {
            Logger.e(TAG, "服务连接失败: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 过滤特定包名和事件类型
        val packageName = event.packageName?.toString()
        if (packageName == "com.omarea.gesture" || packageName == "com.omarea.filter") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) return

        // 防抖动处理
        val currentTime = System.currentTimeMillis()
        val lastTime = lastEventTime.get()
        if (currentTime - lastTime < MIN_WINDOW_CHANGE_INTERVAL) return

        if (event.eventTime > 0 && lastEventTime.compareAndSet(lastTime, event.eventTime)) {
            lastWindowChanged.set(currentTime)
            modernModeEvent(event)
        }
    }

    private fun getEffectiveWindows(includeSystemApp: Boolean = false): List<AccessibilityWindowInfo> {
        return try {
            val windowsList = windows ?: return emptyList()
            val blackList = if (includeSystemApp) blackTypeListBasic else blackTypeList
            windowsList.filter { !blackList.contains(it.type) }
        } catch (e: Exception) {
            Logger.e(TAG, "获取有效窗口失败: ${e.message}")
            emptyList()
        }
    }

    private fun modernModeEvent(event: AccessibilityEvent? = null) {
        if (!serviceIsConnected.get()) return

        serviceScope.launch {
            try {
                val effectiveWindows = getEffectiveWindows()
                if (effectiveWindows.isEmpty()) return@launch

                analyzeWindows(effectiveWindows, event)

                if (event != null) {
                    startActivityPolling()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "modernModeEvent异常: ${e.message}")
            }
        }
    }

    private suspend fun analyzeWindows(effectiveWindows: List<AccessibilityWindowInfo>, event: AccessibilityEvent?) {
        // 检查是否有焦点窗口
        if (effectiveWindows.none { it.isActive || it.isFocused }) return

        var bestWindow: AccessibilityWindowInfo? = null
        var bestWindowSize = 0
        var bestWindowFocused = false

        val minWindowSize = calculateMinWindowSize()
        val debugInfo = StringBuilder().apply {
            append("屏幕: ${displayHeight}x${displayWidth}")
            append(if (isLandscape) " 横向" else " 竖向")
            if (isTablet) append(" Tablet")
            append("\n")
            event?.let { append("事件: ${it.source?.packageName}\n") }
                ?: append("事件: 主动轮询\n")
        }

        // 分析每个窗口
        for (window in effectiveWindows) {
            val outBounds = Rect()
            window.getBoundsInScreen(outBounds)
            val windowFocused = window.isActive || window.isFocused
            val windowSize = (outBounds.right - outBounds.left) * (outBounds.bottom - outBounds.top)

            val packageName = try {
                window.root?.packageName
            } catch (e: Exception) {
                null
            }

            debugInfo.append("\n层级: ${window.layer} $packageName Focused: $windowFocused")
            debugInfo.append("\n类型: ${window.type} Rect[${outBounds.left},${outBounds.top},${outBounds.right},${outBounds.bottom}]")

            // 选择最佳窗口的逻辑
            val shouldSelectWindow = if (isLandscape) {
                windowSize >= bestWindowSize
            } else {
                if (bestWindowFocused && !windowFocused) {
                    false
                } else {
                    windowSize >= bestWindowSize || (windowFocused && !bestWindowFocused)
                }
            }

            if (shouldSelectWindow) {
                bestWindow = window
                bestWindowSize = windowSize
                bestWindowFocused = windowFocused
            }
        }

        // 处理最佳窗口
        bestWindow?.let { window ->
            if (bestWindowSize >= minWindowSize) {
                processSelectedWindow(window, event, debugInfo)
            }
        }
    }

    private fun calculateMinWindowSize(): Int {
        return if (isLandscape && !isTablet) {
            displayHeight * displayWidth / 2
        } else {
            (displayHeight * displayWidth * 8) / 10
        }
    }

    private suspend fun processSelectedWindow(
        window: AccessibilityWindowInfo,
        event: AccessibilityEvent?,
        debugInfo: StringBuilder
    ) {
        val currentAnalyseTime = System.currentTimeMillis()
        lastAnalyseThread.set(currentAnalyseTime)

        // 异步分析窗口
        val packageName = withContext(Dispatchers.IO) {
            analyzeWindowPackage(window, currentAnalyseTime)
        }

        packageName?.let { pkg ->
            // MIUI优化检查
            if (pkg == "com.miui.home" && isMiuiTaskSwitcher(window)) {
                return
            }

            debugInfo.append("\n此前: $previousWindow")

            if (shouldUpdateMode(pkg)) {
                previousWindow = pkg
                val newMode = getAppMode(pkg)
                Log.d(TAG, "应用切换: $pkg, 模式: $newMode")
                updateCSConfig(newMode)
            }

            debugInfo.append("\n现在: $previousWindow")
            debugInfo.append("\n当前模式: $currentMode")

            updateFloatingWindow(debugInfo.toString())
        }
    }

    private suspend fun analyzeWindowPackage(window: AccessibilityWindowInfo, analyzeTime: Long): String? {
        return try {
            val windowId = window.id

            // 先检查缓存
            windowIdCaches.get(windowId)?.let { return it }

            // 获取窗口包名（可能耗时）
            val root = withContext(Dispatchers.IO) {
                try {
                    window.root
                } catch (e: Exception) {
                    null
                }
            }

            val packageName = root?.packageName?.toString()

            // 只有在分析任务仍然有效时才返回结果
            if (lastAnalyseThread.get() == analyzeTime && packageName != null) {
                windowIdCaches.put(windowId, packageName)
                packageName
            } else null

        } catch (e: Exception) {
            Logger.e(TAG, "窗口分析失败: ${e.message}")
            null
        }
    }

    private fun isMiuiTaskSwitcher(window: AccessibilityWindowInfo): Boolean {
        return try {
            if (!window.isFocused) return true
            val root = window.root
            val node = root?.findAccessibilityNodeInfosByViewId("com.miui.home:id/txtSmallWindowContainer")?.firstOrNull()
            node != null
        } catch (e: Exception) {
            false
        }
    }

    private fun shouldUpdateMode(packageName: String): Boolean {
        return !(isLandscape && inputMethods.contains(packageName)) &&
                packageName != previousWindow &&
                packageName != "未知"
    }

    private fun startActivityPolling() {
        pollingJob?.cancel()

        pollingJob = serviceScope.launch {
            lastEventTime.set(System.currentTimeMillis())

            while (isActive) {
                delay(pollingInterval)

                val interval = System.currentTimeMillis() - lastEventTime.get()
                if (interval > pollingTimeout) {
                    break
                } else {
                    modernModeEvent()
                }
            }
        }
    }

    private fun updateFloatingWindow(debugInfo: String) {
        floatingView?.let { view ->
            mainHandler.post {
                try {
                    view.findViewById<TextView>(R.id.debug_info)?.text = debugInfo
                } catch (e: Exception) {
                    Logger.e(TAG, "更新悬浮窗失败: ${e.message}")
                }
            }
        }
    }

    private fun showNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "CS Controller Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager?.createNotificationChannel(channel)
            }

            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CS Controller 正在运行")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "显示通知失败: ${e.message}")
        }
    }

    private fun checkOverlayPermissionAsync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            serviceScope.launch {
                try {
                    Toast.makeText(this@MyAccessibilityService, "请开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "请求悬浮窗权限失败: ${e.message}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceInitialized.get()) {
            initializeService()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        restartService()
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务中断")
        serviceScope.launch {
            delay(3000)
            if (!isServiceInitialized.get()) {
                initializeService()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "服务销毁")
        cleanup()
        restartService()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceIsConnected.set(false)
        cleanup()
        stopSelf()
        return super.onUnbind(intent)
    }

    private fun restartService() {
        try {
            val restartIntent = Intent(applicationContext, this.javaClass).apply {
                setPackage(packageName)
            }
            startService(restartIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "重启服务失败: ${e.message}")
        }
    }

    private fun cleanup() {
        try {
            isServiceInitialized.set(false)
            serviceIsConnected.set(false)

            // 取消所有协程
            serviceScope.cancel()
            backgroundScope.cancel()
            pollingJob?.cancel()

            // 清理UI组件
            removeFloatingWindow()

            // 停止文件观察器
            configObserver?.stopWatching()
            configObserver = null

            // 取消通知
            notificationManager?.cancel(NOTIFICATION_ID)

            // 清理缓存和集合
            windowIdCaches.evictAll()
            synchronized(launcherPackages) { launcherPackages.clear() }
            synchronized(inputMethods) { inputMethods.clear() }
            appModeMap.clear()

            Log.d(TAG, "服务清理完成")
        } catch (e: Exception) {
            Logger.e(TAG, "服务清理失败: ${e.message}")
        }
    }
}
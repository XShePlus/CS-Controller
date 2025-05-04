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
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import android.widget.Toast
import io.github.xsheeee.cs_controller.tools.ConfigManager
import io.github.xsheeee.cs_controller.tools.ConfigManager.ConfigData
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Values
import java.io.File
import java.util.Date
import java.util.Timer
import java.util.TimerTask

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

    // 屏幕相关
    private var displayWidth = 1080
    private var displayHeight = 2340
    private var isLandscape = false
    private var isTablet = false

    // 输入法列表
    private var inputMethods = ArrayList<String>()

    // 桌面包名列表
    private val launcherPackages = mutableSetOf<String>()

    // 窗口id缓存
    private val windowIdCaches = LruCache<Int, String>(10)
    
    // 轮询定时器
    private var pollingTimer: Timer? = null 
    private var lastEventTime: Long = 0
    private var lastWindowChanged: Long = 0 // 记录最后一次窗口变化的时间
    private val pollingTimeout: Long = 7000
    private val pollingInterval: Long = 3000
    
    // 记录服务是否已连接
    private var serviceIsConnected = false
    
    // 用于记录最后分析线程时间
    companion object {
        private var lastAnalyseThread: Long = 0
        private const val TAG = "Accessibility"
        private const val CHANNEL_ID = "cs_controller_channel"
        private const val NOTIFICATION_ID = 1
    }

    private fun updateCSConfig(mode: String) {
        try {
            // 详细记录日志，便于调试
            Log.d(TAG, "updateCSConfig调用: 请求模式=$mode, 当前模式=$currentMode")
            
            // 只有当模式发生变化时才写入文件
            if (mode != currentMode) {
                Log.d(TAG, "模式变化: $currentMode -> $mode, 准备写入文件")
                try {
                    // 使用JNI方法写入文件，替代FileWriter
                    val success = io.github.xsheeee.cs_controller.tools.FileUtils.writeToFile(Values.CSConfigPath, mode)
                    if (success) {
                        Log.d(TAG, "CS config成功写入(JNI): $mode")
                        // 更新当前模式变量
                        currentMode = mode
                    } else {
                        logError("文件写入失败: 使用JNI方法", "E")
                    }
                } catch (e: Exception) {
                    logError("文件写入异常: ${e.message}", "E")
                }
            }
        } catch (e: Exception) {
            logError("updateCSConfig异常: ${e.message}", "E")
        }
    }

    /**
     * 加载应用配置文件
     * 读取配置中的应用模式映射、默认模式和悬浮窗设置
     */
    private fun loadAppConfig() {
        configData = ConfigManager.loadConfig(Values.appConfig)
        appModeMap = configData?.appModeMap as MutableMap<String, String>
        defaultMode = configData!!.defaultMode
        floatingWindowEnabled = configData!!.floatingWindowEnabled

        // 尝试读取CS配置文件，获取当前模式的初始值
        try {
            val storedMode = io.github.xsheeee.cs_controller.tools.FileUtils.readFromFile(Values.CSConfigPath)
            if (storedMode.isNotEmpty()) {
                currentMode = storedMode
                Log.d(TAG, "从文件读取当前模式: $currentMode")
            } else {
                currentMode = defaultMode
                Log.d(TAG, "CS配置文件为空，使用默认模式: $defaultMode")
            }
        } catch (e: Exception) {
            currentMode = defaultMode
            logError("读取CS配置文件失败，使用默认模式: $defaultMode, 错误: ${e.message}", "E")
        }

        updateFloatingWindowState()
    }

    /**
     * 初始化服务
     * 设置基本组件、加载配置、初始化界面等
     */
    override fun onCreate() {
        super.onCreate()
        try {
            initializeService()
        } catch (e: Exception) {
            logError("Service initialization failed: " + e.message, "E")
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

            // 获取屏幕相关信息
            getDisplaySize()
            onScreenConfigurationChanged(resources.configuration)

            // 初始化桌面包名列表
            initLauncherPackages()

            // 创建必要的目录
            createRequiredDirectories()

            // 加载配置
            loadAppConfig()
            
            setupConfigObserver()
            showNotification()
            checkOverlayPermission()

            // 获取输入法
            Thread {
                inputMethods = getInputMethods()
            }.start()

            isServiceInitialized = true
            Log.d(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            logError("Failed to initialize service: " + e.message, "E")
            handler!!.postDelayed({ this.initializeService() }, 3000)
        }
    }

    // 获取输入法列表
    private fun getInputMethods(): ArrayList<String> {
        val inputMethodList = ArrayList<String>()
        try {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE)
            val method = inputMethodManager.javaClass.getMethod("getInputMethodList")
            val inputMethodInfos = method.invoke(inputMethodManager) as List<*>
            
            for (imi in inputMethodInfos) {
                val packageName = imi?.javaClass?.getMethod("getPackageName")!!.invoke(imi) as String
                inputMethodList.add(packageName)
                Log.d(TAG, "Input method detected: $packageName")
            }
        } catch (e: Exception) {
            logError("Error getting input methods: " + e.message, "E")
        }
        return inputMethodList
    }

    // 屏幕配置变化
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenConfigurationChanged(newConfig)
    }

    // 处理屏幕配置变化
    private fun onScreenConfigurationChanged(newConfig: Configuration) {
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            isLandscape = false
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isLandscape = true
        }
        getDisplaySize()
    }

    // 获取屏幕大小
    @Suppress("DEPRECATION")
    private fun getDisplaySize() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val point = Point()
            wm.defaultDisplay.getRealSize(point)
            if (point.x != displayWidth || point.y != displayHeight) {
                displayWidth = point.x
                displayHeight = point.y
            }

            isTablet = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
            Log.d(TAG, "Screen size: $displayWidth x $displayHeight, isTablet: $isTablet")
        } catch (e: Exception) {
            logError("Error getting screen size: " + e.message, "E")
            displayWidth = 1080  // 设置一个默认值
            displayHeight = 2340 // 设置一个默认值
        }
    }

    // 初始化桌面包名列表
    private fun initLauncherPackages() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfos) {
            resolveInfo.activityInfo?.packageName?.let { packageName ->
                launcherPackages.add(packageName)
                Log.d(TAG, "Launcher package detected: $packageName")
            }
        }

        // 一些常见的系统UI包名，这些通常不应被视为前台应用
        val systemPackages = listOf(
            "com.android.systemui",
            "android",
            "com.android.settings.overlay",
            "com.android.phone",
            "com.android.incallui",
            "com.android.server.telecom"
        )
        systemPackages.forEach { launcherPackages.add(it) }
    }

    private fun createRequiredDirectories() {
        try {
            val configParent = File(Values.appConfig).parentFile

            if (configParent != null && !configParent.exists()) {
                val created = configParent.mkdirs()
                Log.d(TAG, "Config parent directory created: $created")
            }
        } catch (e: Exception) {
            logError("Error creating directories: " + e.message, "E")
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
            logError("Error updating floating window state: " + e.message, "E")
        }
    }

    @SuppressLint("InflateParams")
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
            logError("Error creating floating window: " + e.message, "E")
            floatingView = null
        }
    }

    private fun removeFloatingWindow() {
        if (floatingView != null) {
            try {
                windowManager!!.removeView(floatingView)
            } catch (e: Exception) {
                logError("Error removing floating window: " + e.message, "E")
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
                                Log.d(TAG, "Config file changed, event: $event")
                                try {
                                    loadAppConfig()
                                } catch (e: Exception) {
                                    logError("Failed to reload config: " + e.message, "E")
                                }
                            }
                        }
                    }
                }

            try {
                (configObserver as FileObserver).startWatching()
                Log.d(TAG, "File observer started successfully")
            } catch (e: Exception) {
                logError("Failed to start file observer: " + e.message, "E")
                handler!!.postDelayed({ this.setupConfigObserver() }, 5000)
            }
        }
    }

    private fun getAppMode(packageName: String): String {
        return configData!!.appModeMap.getOrDefault(packageName, configData!!.defaultMode)
    }

    override fun onServiceConnected() {
        try {
            val info = AccessibilityServiceInfo()
            info.eventTypes = (
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            or AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    )
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 添加更多的事件类型和标志
            info.flags = (
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                            or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            or AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
                    )

            serviceInfo = info
            serviceIsConnected = true
            
            // 获取屏幕方向
            onScreenConfigurationChanged(resources.configuration)
            getDisplaySize()
            
            // 服务连接时，主动检测当前前台应用并更新模式
            handler?.postDelayed({ modernModeEvent() }, 1000)
            Log.d(TAG, "Service connected successfully")
        } catch (e: Exception) {
            logError("Error in onServiceConnected: " + e.message, "E")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 过滤特定包名
        val packageName = event.packageName
        if (packageName?.toString() == "com.omarea.gesture" || packageName?.toString() == "com.omarea.filter") {
            return
        }

        // 只处理窗口状态变化事件
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return
        }

        // 避免重复处理相同的事件
        val t = event.eventTime
        if (t > 0 && t != lastEventTime) {
            lastEventTime = t
            lastWindowChanged = System.currentTimeMillis()
            modernModeEvent(event)
        }
    }
    
    // 黑名单窗口类型（不应被视为前台应用的窗口类型）
    private val blackTypeList = arrayListOf(
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
        AccessibilityWindowInfo.TYPE_INPUT_METHOD,
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
        AccessibilityWindowInfo.TYPE_SYSTEM
    )

    private val blackTypeListBasic = arrayListOf(
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
        AccessibilityWindowInfo.TYPE_INPUT_METHOD,
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
    )
    
    // 获取有效窗口列表
    private fun getEffectiveWindows(includeSystemApp: Boolean = false): List<AccessibilityWindowInfo> {
        val windowsList = windows
        if (windowsList != null && windowsList.size > 1) {
            val effectiveWindows = windowsList.filter {
                if (includeSystemApp) {
                    !blackTypeListBasic.contains(it.type)
                } else {
                    !blackTypeList.contains(it.type)
                }
            }
            return effectiveWindows
        }
        return ArrayList()
    }
    
    // 获取所有前台应用包名
    private fun getForegroundApps(): Array<String> {
        val windows = this.getEffectiveWindows(true)
        return windows.map {
            it.root?.packageName
        }.filter { it != null && it != "com.android.systemui" }.map { it.toString() }.toTypedArray()
    }
    
    /**
     * 新的前台应用检测逻辑
     * 分析窗口信息找出当前前台应用并更新模式
     * @param event 触发检测的辅助功能事件，可为null
     */
    private fun modernModeEvent(event: AccessibilityEvent? = null) {
        val effectiveWindows = this.getEffectiveWindows()

        if (effectiveWindows.isNotEmpty()) {
            try {
                var lastWindow: AccessibilityWindowInfo? = null
                // 最小窗口分辨率要求
                val minWindowSize = if (isLandscape && !isTablet) {
                    // 横屏时关注窗口大小，以显示区域大的主应用（平板设备不过滤窗口大小）
                    // 屏幕一半大小，用于判断窗口是否是小窗（比屏幕一半大小小的的应用认为是窗口化运行）
                    displayHeight * displayWidth / 2
                } else {
                    (displayHeight * displayWidth * 8) / 10
                }

                // 构建日志信息
                val logs = StringBuilder()
                logs.append("屏幕: ${displayHeight}x${displayWidth}")
                if (isLandscape) {
                    logs.append(" 横向")
                } else {
                    logs.append(" 竖向")
                }
                if (isTablet) {
                    logs.append(" Tablet")
                }
                logs.append("\n")
                if (event != null) {
                    logs.append("事件: ${event.source?.packageName}\n")
                } else {
                    logs.append("事件: 主动轮询${Date().time / 1000}\n")
                }

                var lastWindowSize = 0
                var lastWindowFocus = false

                // 无焦点窗口（一般处于过渡动画或窗口切换过程中）
                if (effectiveWindows.find { it.isActive || it.isFocused } == null) {
                    return
                }

                // 遍历所有有效窗口，找出最可能是前台应用的窗口
                for (window in effectiveWindows) {
                    if (isLandscape) {
                        // 横屏模式下的窗口处理逻辑
                        val outBounds = Rect()
                        window.getBoundsInScreen(outBounds)

                        val windowFocused = (window.isActive || window.isFocused)

                        val wp = try {
                            window.root?.packageName
                        } catch (ex: java.lang.Exception) {
                            null
                        }
                        logs.append("\n层级: ${window.layer} $wp Focused：${windowFocused}\n类型: ${window.type} Rect[${outBounds.left},${outBounds.top},${outBounds.right},${outBounds.bottom}]")

                        // 计算窗口面积，选择最大的窗口
                        val size = (outBounds.right - outBounds.left) * (outBounds.bottom - outBounds.top)
                        if (size >= lastWindowSize) {
                            lastWindow = window
                            lastWindowSize = size
                        }
                    } else {
                        // 竖屏模式下的窗口处理逻辑
                        val windowFocused = (window.isActive || window.isFocused)

                        val outBounds = Rect()
                        window.getBoundsInScreen(outBounds)

                        val wp = try {
                            window.root?.packageName
                        } catch (ex: java.lang.Exception) {
                            null
                        }
                        logs.append("\n层级: ${window.layer} $wp Focused：${windowFocused}\n类型: ${window.type} Rect[${outBounds.left},${outBounds.top},${outBounds.right},${outBounds.bottom}]")

                        // 如果已经找到了有焦点的窗口，则跳过无焦点的窗口
                        if (lastWindowFocus && !windowFocused) {
                            continue
                        }

                        // 计算窗口面积，在相同焦点状态下选择最大的窗口
                        val size = (outBounds.right - outBounds.left) * (outBounds.bottom - outBounds.top)
                        if (size >= lastWindowSize || (windowFocused && !lastWindowFocus)) {
                            lastWindow = window
                            lastWindowSize = size
                            lastWindowFocus = windowFocused
                        }
                    }
                }
                logs.append("\n")
                
                if (lastWindow != null && lastWindowSize >= minWindowSize) {
                    // 使用窗口分析
                    lastAnalyseThread = System.currentTimeMillis()
                    windowAnalyse(lastWindow, lastAnalyseThread)
                    
                    // 获取当前窗口的包名
                    val eventWindowId = event?.windowId
                    val lastWindowId = lastWindow.id

                    val wp = if (eventWindowId == lastWindowId) {
                        event.packageName
                    } else {
                        try {
                            lastWindow.root.packageName
                        } catch (ex: java.lang.Exception) {
                            null
                        }
                    }
                    
                    // MIUI 优化，打开MIUI多任务界面时当做没有发生应用切换
                    if (wp?.equals("com.miui.home") == true) {
                        val node = lastWindow.root?.findAccessibilityNodeInfosByViewId("com.miui.home:id/txtSmallWindowContainer")?.firstOrNull()
                        if (node != null) {
                            return
                        }
                    }
                    
                    if (wp != null) {
                        logs.append("\n此前: $previousWindow")
                        val pa = wp.toString()
                        if (!(isLandscape && inputMethods.contains(pa))) {
                            if (pa != previousWindow) {
                                // 应用切换，需要更新模式
                                previousWindow = pa
                                if (pa != "未知") {
                                    val newMode = getAppMode(pa)
                                    Log.d(TAG, "应用切换: $pa, 模式: $newMode")
                                    updateCSConfig(newMode)
                                }
                            }
                            // 移除不必要的模式更新检查，避免冗余调用
                        }
                        if (event != null) {
                            startActivityPolling()
                        }
                    }

                    // 获取前台应用列表用于调试
                    val foregroundApps = getForegroundApps()
                    if (foregroundApps.isNotEmpty()) {
                        logs.append("\n前台应用: ${foregroundApps.joinToString()}")
                    }

                    logs.append("\n现在: $previousWindow")
                    logs.append("\n当前模式: $currentMode")
                    updateFloatingWindow(logs.toString())
                }
            } catch (ex: Exception) {
                logError("Error in modernModeEvent: " + ex.message, "E")
                return
            }
        }
    }

    private fun windowAnalyse(windowInfo: AccessibilityWindowInfo, tid: Long) {
        Thread {
            var root: AccessibilityNodeInfo? = null
            val windowId = windowInfo.id
            val wp = (try {
                // 尝试从缓存中获取窗口包名
                val cache = windowIdCaches.get(windowId)
                if (cache == null) {
                    // 如果当前window锁属的APP处于未响应状态，此过程可能会等待5秒后超时返回null，因此需要在线程中异步进行此操作
                    root = (try {
                        windowInfo.root
                    } catch (ex: Exception) {
                        null
                    })
                    root?.packageName.apply {
                        if (this != null) {
                            windowIdCaches.put(windowId, toString())
                        }
                    }
                } else {
                    cache
                }
            } catch (ex: Exception) {
                null
            })
            
            // MIUI 优化，打开MIUI多任务界面时当做没有发生应用切换
            if (wp?.equals("com.miui.home") == true) {
                // 手势滑动过程中，桌面面处于非Focused状态
                if (!windowInfo.isFocused) {
                    return@Thread
                }
                
                val node = root?.findAccessibilityNodeInfosByViewId("com.miui.home:id/txtSmallWindowContainer")?.firstOrNull()
                if (node != null) {
                    return@Thread
                }
            }

            // 确认分析结果有效且是最新的分析任务
            if (lastAnalyseThread == tid && wp != null) {
                val pa = wp.toString()
                if (!(isLandscape && inputMethods.contains(pa)) && pa != previousWindow) {
                    previousWindow = pa
                    if (pa != "未知") {
                        val newMode = getAppMode(pa)
                        Log.d(TAG, "窗口分析检测到应用切换: $pa, 模式: $newMode")
                        updateCSConfig(newMode)
                    }
                }
            }
        }.start()
    }
    
    // 启动活动轮询
    private fun startActivityPolling(delay: Long? = null) {
        stopActivityPolling()
        synchronized(this) {
            lastEventTime = System.currentTimeMillis()
            if (pollingTimer == null) {
                pollingTimer = Timer()
                pollingTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        val interval = System.currentTimeMillis() - lastEventTime
                        if (interval <= pollingTimeout) {
                            modernModeEvent()
                        } else {
                            stopActivityPolling()
                        }
                    }
                }, delay ?: pollingInterval, pollingInterval)
            }
        }
    }

    // 停止活动轮询
    private fun stopActivityPolling() {
        synchronized(this) {
            if (pollingTimer != null) {
                pollingTimer?.cancel()
                pollingTimer?.purge()
                pollingTimer = null
            }
        }
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
                    logError("Error updating floating window text: " + e.message, "E")
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
            logError("Error showing notification: " + e.message, "E")
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
                    logError("Error requesting overlay permission: " + e.message, "E")
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
            logError("Error restarting service: " + e.message, "E")
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
            logError("Failed to restart service: " + e.message, "E")
        }

        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceIsConnected = false
        cleanup()
        stopSelf()
        return super.onUnbind(intent)
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
            launcherPackages.clear()
            inputMethods.clear()
            windowIdCaches.evictAll()
            stopActivityPolling()

            Log.d(TAG, "Service cleanup completed")
        } catch (e: Exception) {
            logError("Error during cleanup: " + e.message, "E")
        }
    }

    @Suppress("SameParameterValue")
    private fun logError(message: String, level: String) {
        Logger.writeLog(TAG, level, message)
    }
}
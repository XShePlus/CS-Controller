package io.github.xsheeee.cs_controller

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.xsheeee.cs_controller.Tools.AppInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min

class AppListActivity : BaseActivity() {
    private var recyclerView: RecyclerView? = null
    private val data: MutableList<AppInfo> = Collections.synchronizedList(ArrayList())
    private val filteredData: MutableList<AppInfo> = Collections.synchronizedList(ArrayList())
    private var loadingView: FrameLayout? = null
    private var packageManager: PackageManager? = null
    private var adapter: AppListAdapter? = null
    private var executorService: ExecutorService? = null
    private var iconCache: LruCache<String?, WeakReference<Drawable?>?>? = null
    private val performanceModes: MutableMap<String, String> =
    Collections.synchronizedMap(HashMap())
    private var configFileObserver: FileObserver? = null

    @Volatile
    var isLoading = false
    private set

    private var searchRunnable: Runnable? = null
    private val searchHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        initializeViews()
        initializeCache()
        setupRecyclerView()
        setupConfigFileObserver()
        loadAppInfos()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        loadingView = findViewById(R.id.loading_view)
        packageManager = getPackageManager()

        // 优化线程池配置
        val executor = ThreadPoolExecutor(
                LOADING_POOL_SIZE,
                LOADING_POOL_SIZE * 2,
                30L, TimeUnit.SECONDS,
                LinkedBlockingQueue(40),
                ThreadPoolExecutor.CallerRunsPolicy()
        )
        executor.allowCoreThreadTimeOut(true)
        executorService = executor

        val toolbar = findViewById<Toolbar>(R.id.backButton)
                setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }
    }

    private fun initializeCache() {
        iconCache = object : LruCache<String?, WeakReference<Drawable?>?>(
                ICON_CACHE_SIZE
                ) {
                override fun sizeOf(key: String?, value: WeakReference<Drawable?>?): Int {
            return 1 // 简化大小计算
        }
        }

        // 预加载系统应用图标
        executorService!!.execute {
            try {
                val apps =
                        packageManager!!.getInstalledApplications(0)
                for (app in apps) {
                    if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        val icon = app.loadIcon(packageManager)
                        (iconCache as LruCache<String?, WeakReference<Drawable?>?>).put(app.packageName, WeakReference(icon))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupConfigFileObserver() {
        val configDir = CONFIG_FILE_PATH.substring(0, CONFIG_FILE_PATH.lastIndexOf('/'))
        val configFile = File(configDir)

        configFileObserver = object : FileObserver(
                configFile,
                MODIFY or CLOSE_WRITE
        ) {
            @SuppressLint("SuspiciousIndentation")
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                        val fileName = CONFIG_FILE_PATH.substring(CONFIG_FILE_PATH.lastIndexOf('/') + 1)
                if (path != fileName) return

                if ((event and MODIFY) != 0 ||
                        (event and CLOSE_WRITE) != 0
                ) {
                    runOnUiThread {
                        performanceModes.clear()
                        loadAppInfos()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager

        recyclerView!!.setHasFixedSize(true)
        recyclerView!!.setItemViewCacheSize(20)
        recyclerView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        recyclerView!!.itemAnimator = null

        adapter = AppListAdapter(
                this,
                filteredData,
                executorService
        ) { appInfo -> loadIconForAppInfo(appInfo) }

        recyclerView!!.adapter = adapter

        recyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isLoading) {
                    loadVisibleIcons()
                }
            }
        })
    }

    private fun readConfigFile() {
        val configFile = File(CONFIG_FILE_PATH)
        if (!configFile.exists()) return

        try {
            BufferedReader(
                    InputStreamReader(FileInputStream(configFile))
            ).use { reader ->
                    val sb = StringBuilder(1024)
                val buffer = CharArray(1024)
                var read: Int

                while ((reader.read(buffer).also { read = it }) != -1) {
                    sb.append(buffer, 0, read)
                }

                val json = JSONObject(sb.toString())
                performanceModes.clear()

                val modes = arrayOf("powersave", "balance", "performance", "fast")
                for (mode in modes) {
                    val apps = json.optJSONArray(mode)
                    if (apps != null) {
                        for (i in 0 until apps.length()) {
                            performanceModes[apps.getString(i)] = mode
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAppInfos() {
        if (isLoading) return
                isLoading = true
        loadingView!!.visibility = View.VISIBLE

        executorService!!.execute {
            readConfigFile()
            val loadedData =
                    allAppInfos

            Collections.sort(
                    loadedData
            ) { app1: AppInfo, app2: AppInfo ->
                if (app1.isPriority && !app2.isPriority) return@sort -1
                if (!app1.isPriority && app2.isPriority) return@sort 1
                app2.appName?.let { app1.appName!!.compareTo(it) }!!
            }
            runOnUiThread {
                updateAppData(loadedData)
                loadingView!!.visibility = View.GONE
                isLoading = false
            }
        }
    }

    protected val allAppInfos: List<AppInfo>
    get() {
        val resolveInfos =
                packageManager!!.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
        )

        return resolveInfos.parallelStream()
                .map { ri: ResolveInfo ->
                val packageName = ri.activityInfo.packageName
            val appName = ri.loadLabel(packageManager!!).toString()
            val appInfo =
                    AppInfo(null, appName, packageName)

            if (performanceModes.containsKey(packageName)) {
                appInfo.performanceMode = performanceModes[packageName]
                appInfo.isPriority = true
            }
            appInfo
        }
                .collect(
                Collectors.toList()
        )
    }

    private fun loadVisibleIcons() {
        val layoutManager = recyclerView!!.layoutManager as LinearLayoutManager? ?: return

                val firstVisible =
                max(0.0, layoutManager.findFirstVisibleItemPosition().toDouble()).toInt()
        val lastVisible = min(
                layoutManager.findLastVisibleItemPosition().toDouble(),
                (filteredData.size - 1).toDouble()
        ).toInt()

        // 使用批量加载
        val toLoad: MutableList<AppInfo> = ArrayList()
        for (i in firstVisible..lastVisible) {
            val appInfo = filteredData[i]
            if (appInfo.icon == null && !isIconLoading(appInfo)) {
                toLoad.add(appInfo)
            }
        }

        // 预加载
        val endPosition = min(
                (lastVisible + PRELOAD_AHEAD_ITEMS).toDouble(),
                filteredData.size.toDouble()
        ).toInt()
        for (i in lastVisible + 1 until endPosition) {
            val appInfo = filteredData[i]
            if (appInfo.icon == null && !isIconLoading(appInfo)) {
                toLoad.add(appInfo)
            }
        }

        // 批量加载图标
        if (!toLoad.isEmpty()) {
            executorService!!.execute {
                for (appInfo in toLoad) {
                    loadIconForAppInfo(appInfo)
                }
            }
        }
    }

    private fun isIconLoading(appInfo: AppInfo): Boolean {
        return appInfo.icon != null || executorService!!.isShutdown
    }

    fun loadIconForAppInfo(appInfo: AppInfo) {
        try {
            val cachedIconRef = iconCache!![appInfo.packageName]
            if (cachedIconRef != null) {
                val cachedIcon = cachedIconRef.get()
                if (cachedIcon != null) {
                    appInfo.icon = cachedIcon
                    notifyItemChanged(appInfo)
                    return
                }
                iconCache!!.remove(appInfo.packageName)
            }

            val icon = appInfo.packageName?.let { packageManager!!.getApplicationIcon(it) }
            appInfo.icon = icon
            iconCache!!.put(appInfo.packageName, WeakReference(icon))
            notifyItemChanged(appInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAppData(loadedData: List<AppInfo>) {
        val diffResult = DiffUtil.calculateDiff(
                DiffCallback(data, loadedData), true
        )

        data.clear()
        data.addAll(loadedData)
        filteredData.clear()
        filteredData.addAll(data)

        diffResult.dispatchUpdatesTo(adapter!!)
        loadVisibleIcons()
    }

    private fun notifyItemChanged(appInfo: AppInfo) {
        runOnUiThread {
            val position = filteredData.indexOf(appInfo)
            if (position != -1) {
                adapter!!.notifyItemChanged(position)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        setupSearchView(menu)
        return true
    }

    private fun setupSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView?

        Objects.requireNonNull(searchView)?.queryHint =
                getString(R.string.search_text)
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable!!)
                }
                searchRunnable = Runnable { filter(newText) }
                searchHandler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_TIME_MS)
                return true
            }
        })
    }

    private fun filter(text: String) {
        if (isLoading) return

                val lowerCaseText = text.lowercase(Locale.getDefault())
        val newFilteredData = if (text.isEmpty())
            ArrayList(data)
        else
            data.parallelStream()
                    .filter { appInfo: AppInfo ->
                appInfo.appName?.lowercase(Locale.getDefault())!!.contains(lowerCaseText) ||
                appInfo.packageName!!.lowercase(Locale.getDefault())
                .contains(lowerCaseText)
        }
                .collect(Collectors.toList())

        val diffResult = DiffUtil.calculateDiff(
                DiffCallback(filteredData, newFilteredData), true
        )

        filteredData.clear()
        filteredData.addAll(newFilteredData)
        diffResult.dispatchUpdatesTo(adapter!!)
        loadVisibleIcons()
    }

    override fun onResume() {
        super.onResume()
        if (configFileObserver != null) {
            configFileObserver!!.startWatching()
        }
        refreshPerformanceModes()
    }

    override fun onPause() {
        super.onPause()
        if (configFileObserver != null) {
            configFileObserver!!.stopWatching()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (configFileObserver != null) {
            configFileObserver!!.stopWatching()
            configFileObserver = null
        }
        if (executorService != null) {
            executorService!!.shutdown()
            try {
                if (!executorService!!.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService!!.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executorService!!.shutdownNow()
            }
        }
        if (iconCache != null) {
            iconCache!!.evictAll()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun refreshPerformanceModes() {
        if (isLoading) return

                executorService!!.execute {
            readConfigFile()
            var needsSort = false
            for (appInfo in data) {
                val newMode = performanceModes[appInfo.packageName]
                if (appInfo.performanceMode != newMode) {
                    appInfo.performanceMode = newMode ?: ""
                    appInfo.isPriority = newMode != null
                    needsSort = true
                }
            }
            if (needsSort) {
                Collections.sort(
                        data
                ) { app1: AppInfo, app2: AppInfo ->
                    if (app1.isPriority && !app2.isPriority) return@sort -1
                    if (!app1.isPriority && app2.isPriority) return@sort 1
                    app2.appName?.let { app1.appName!!.compareTo(it) }!!
                }

                runOnUiThread {
                    filteredData.clear()
                    filteredData.addAll(data)
                    adapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    internal class DiffCallback(
            private val oldList: List<AppInfo>,
            private val newList: List<AppInfo>
    ) :
    DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return (oldList[oldItemPosition].packageName
                    == newList[newItemPosition].packageName)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            if (oldItem.appName != newItem.appName) return false
            if (oldItem.performanceMode != newItem.performanceMode) return false

            val oldIcon = oldItem.icon
            val newIcon = newItem.icon
            if (oldIcon == null && newIcon == null) return true
            if (oldIcon == null || newIcon == null) return false

            return oldIcon.constantState === newIcon.constantState
        }
    }

    companion object {
        private const val CONFIG_FILE_PATH =
                "/storage/emulated/0/Android/CSController/app_config.json"
        private const val PRELOAD_AHEAD_ITEMS = 20
        private const val ICON_CACHE_SIZE = 200
        private const val SEARCH_DEBOUNCE_TIME_MS: Long = 300
        private val LOADING_POOL_SIZE = Runtime.getRuntime().availableProcessors()
    }
}
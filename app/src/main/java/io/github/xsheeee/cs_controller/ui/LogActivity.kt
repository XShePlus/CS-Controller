package io.github.xsheeee.cs_controller.ui

import android.os.Bundle
import android.os.FileObserver
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import io.github.xsheeee.cs_controller.R
import io.github.xsheeee.cs_controller.tools.Values
import java.io.File
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LogActivity : BaseActivity() {
    private lateinit var logTextView: TextView
    private lateinit var scrollView: NestedScrollView
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private var fileObserver: FileObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // 初始化 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.backButton4)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { finish() }

        // 初始化视图
        logTextView = findViewById(R.id.log_text_view)
        scrollView = findViewById(R.id.scroll_view)

        // 加载初始日志
        executorService.submit { loadLogFile() }

        // 启动文件监听
        startFileObserver()
    }

    private fun startFileObserver() {
        val logFile = File(Values.csLog)
        if (!logFile.exists()) return

        fileObserver = object : FileObserver(logFile.path, MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (event == MODIFY) {
                    executorService.submit {
                        loadLogFile()
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun loadLogFile() {
        val logFile = File(Values.csLog)
        if (!logFile.exists()) {
            updateLogText("日志文件不存在：${logFile.absolutePath}")
            return
        }

        try {
            val content = StringBuilder()
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    content.append(line).append('\n')
                }
            }
            updateLogText(content.toString())
        } catch (e: IOException) {
            updateLogText("读取日志文件时发生错误：${e.message}")
        }
    }

    private fun updateLogText(text: String) {
        runOnUiThread {
            logTextView.text = text
            // 始终滚动到底部显示最新内容
            scrollView.post {
                scrollView.fullScroll(NestedScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdown()
        fileObserver?.stopWatching()
    }
}

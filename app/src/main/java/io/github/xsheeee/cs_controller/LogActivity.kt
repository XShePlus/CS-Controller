package io.github.xsheeee.cs_controller

import android.os.Bundle
import android.os.FileObserver
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import io.github.xsheeee.cs_controller.Tools.Values
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LogActivity : BaseActivity() {
    private var logTextView: TextView? = null
    private var scrollView: NestedScrollView? = null // 用来滚动的容器
    private var executorService: ExecutorService? = null
    private var fileObserver: FileObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // 初始化 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.backButton4)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }

        logTextView = findViewById(R.id.log_text_view)
        scrollView = findViewById(R.id.scroll_view)
        executorService = Executors.newSingleThreadExecutor()
        executorService?.submit(Runnable { this.loadLogFile() })
        startFileObserver()
    }

    private fun startFileObserver() {
        val logFile = File(Values.csLog)

        fileObserver = object : FileObserver(logFile, MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (event == MODIFY) {
                    executorService!!.submit { this@LogActivity.loadLogFile() }
                }
            }
        }
        (fileObserver as FileObserver).startWatching()
    }

    private fun loadLogFile() {
        val logContent = readLogFile()

        runOnUiThread {
            logTextView!!.text = logContent ?: "无法读取日志文件"
            // 更新日志后滚动到底部
            scrollView!!.post { scrollView!!.fullScroll(NestedScrollView.FOCUS_DOWN) }
        }
    }

    private fun readLogFile(): String {
        val content = StringBuilder()
        val logFile = File(Values.csLog)

        if (!logFile.exists()) {
            return "日志文件不存在：" + logFile.absolutePath
        }

        try {
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    content.append(line).append("\n")
                }
                return content.toString()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return "读取日志文件时发生错误：" + e.message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 关闭 ExecutorService
        if (executorService != null && !executorService!!.isShutdown) {
            executorService!!.shutdown()
        }

        // 停止 FileObserver
        if (fileObserver != null) {
            fileObserver!!.stopWatching()
        }
    }
}
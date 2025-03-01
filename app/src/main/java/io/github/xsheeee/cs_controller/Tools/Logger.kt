package io.github.xsheeee.cs_controller.Tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

object Logger {
    // 统一日志文件路径
    private const val LOG_DIRECTORY = "/storage/emulated/0/Android/CSController"
    private const val LOG_FILE = "$LOG_DIRECTORY/app_log.txt"
    private const val CONFIG_FILE = "$LOG_DIRECTORY/app_config.json"
    private const val MAX_LOG_SIZE: Long = 10 * 1024 * 1024 // 10MB

    // 日志级别常量
    private const val LEVEL_DEBUG = "DEBUG"
    private const val LEVEL_INFO = "INFO"
    private const val LEVEL_WARNING = "WARNING"
    private const val LEVEL_ERROR = "ERROR"

    @JvmStatic
    fun writeLog(TAG: String, level: String, logMessage: String) {
        val logFile = File(LOG_FILE)
        val logDir = logFile.parentFile

        // 确保目录存在
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }

        // 读取配置文件中的logLevel
        val currentLogLevel = readLogLevelFromConfig()

        // 判断是否应该写入
        if (shouldLog(currentLogLevel, level)) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp][$TAG][$level] $logMessage\n"

            try {
                // 检查文件大小并在必要时进行裁剪
                checkAndTrimLogFile(logFile)

                // 写入日志
                BufferedWriter(FileWriter(logFile, true)).use { writer ->
                    writer.write(logEntry)
                }
            } catch (e: IOException) {
                // 如果写入失败，尝试记录错误
                try {
                    BufferedWriter(FileWriter(logFile, true)).use { errorWriter ->
                        errorWriter.write(
                            "[$timestamp][ERROR] Log write failed: ${e.message}\n"
                        )
                    }
                } catch (ignored: IOException) {
                }
            }
        }
    }

    // 读取配置文件中的 logLevel
    private fun readLogLevelFromConfig(): String {
        val configFile = File(CONFIG_FILE)
        if (configFile.exists()) {
            try {
                BufferedReader(FileReader(configFile)).use { reader ->
                    val content = reader.readText()
                    val jsonObject = JSONObject(content)
                    return jsonObject.optString("logLevel", LEVEL_ERROR).uppercase(Locale.ROOT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return LEVEL_ERROR // 默认日志级别
    }

    // 判断是否根据级别进行写入
    private fun shouldLog(currentLevel: String, level: String): Boolean {
        val logLevels = mapOf(
            LEVEL_DEBUG to 1,
            LEVEL_INFO to 2,
            LEVEL_WARNING to 3,
            LEVEL_ERROR to 4
        )

        val normalizedLevel = when (level.uppercase(Locale.ROOT)) {
            "D" -> LEVEL_DEBUG
            "I" -> LEVEL_INFO
            "W" -> LEVEL_WARNING
            "E" -> LEVEL_ERROR
            else -> level.uppercase(Locale.ROOT)
        }

        val currentLevelValue = logLevels[currentLevel] ?: 4 // 默认Error级别
        val levelValue = logLevels[normalizedLevel] ?: 4

        return levelValue >= currentLevelValue
    }

    // 检查并裁剪日志文件
    private fun checkAndTrimLogFile(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_SIZE) {
            return
        }

        val tempFile = File("${logFile.absolutePath}.tmp")
        try {
            // 计算需要跳过的行数（大约裁剪30%的内容）
            val skipLines = (logFile.length() * 0.3 / 100).toInt().coerceAtLeast(100)

            BufferedReader(FileReader(logFile)).use { reader ->
                BufferedWriter(FileWriter(tempFile)).use { writer ->
                    // 跳过指定行数
                    for (i in 0 until skipLines) {
                        reader.readLine() ?: break
                    }

                    // 复制剩余内容
                    var line: String? = reader.readLine()
                    while (line != null) {
                        writer.write("$line\n")
                        line = reader.readLine()
                    }
                }
            }

            // 删除原文件并重命名临时文件
            if (logFile.delete() && tempFile.renameTo(logFile)) {
                // 裁剪成功
                writeLog("Logger", LEVEL_INFO, "Log file trimmed successfully")
            } else {
                // 文件操作失败
                writeLog("Logger", LEVEL_ERROR, "Failed to replace log file after trimming")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun showToast(context: Context?, message: String?) {
        if (context == null || message == null) return

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 便捷日志方法
    @JvmStatic
    fun d(tag: String, message: String) = writeLog(tag, LEVEL_DEBUG, message)

    @JvmStatic
    fun i(tag: String, message: String) = writeLog(tag, LEVEL_INFO, message)

    @JvmStatic
    fun w(tag: String, message: String) = writeLog(tag, LEVEL_WARNING, message)

    @JvmStatic
    fun e(tag: String, message: String) = writeLog(tag, LEVEL_ERROR, message)
}
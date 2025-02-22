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

object Logger {
    const val LOG_PATH: String = "/sdcard/Android/CSController/log.txt"
    const val MAX_LOG_SIZE: Long = (10 * 1024 * 1024 // 10MB
            ).toLong()

    @JvmStatic
    fun writeLog(TAG: String, type: String, logMessage: String) {
        val logFile = File(LOG_PATH)
        val logDir = logFile.parentFile

        // 确保目录存在
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp][$TAG][$type] $logMessage\n"

        try {
            BufferedWriter(FileWriter(logFile, true)).use { writer ->
                writer.write(logEntry)
            }
        } catch (e: IOException) {
            try {
                BufferedWriter(FileWriter(logFile, true)).use { errorWriter ->
                    errorWriter.write(
                        "[$timestamp][ERROR] Log write failed: $e\n"
                    )
                }
            } catch (ignored: IOException) {
            }
        }

        checkAndTrimLogFile(logFile)
    }

    // 超过 10MB 删除最早一行
    private fun checkAndTrimLogFile(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_SIZE) {
            return
        }

        val tempFile = File(logFile.absolutePath + ".tmp")
        try {
            BufferedReader(FileReader(logFile)).use { reader ->
                BufferedWriter(FileWriter(tempFile)).use { writer ->
                    reader.readLine()
                    var line: String
                    while ((reader.readLine().also { line = it }) != null) {
                        writer.write(line + "\n")
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
    }

    @JvmStatic
    fun showToast(context: Context?, message: String?) {
        Handler(Looper.getMainLooper())
            .post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
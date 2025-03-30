package io.github.xsheeee.cs_controller.tools

import kotlinx.coroutines.*
import android.util.Log
import java.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SuManager {
    private const val TAG = "io.github.xsheeee.cs_controller.Tools.SuManager"
    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: BufferedReader? = null
    private var errorStream: BufferedReader? = null

    private val mutex = Mutex()  // 一个互斥锁

    // 检查 su 是否可用
    fun isSuAvailable(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec("su")
        true
    } catch (e: Exception) {
        false
    }
}

    // 初始化 su 进程
    private suspend fun initSu(): Boolean {
        return mutex.withLock {  // 确保此方法在同一时间内只能有一个协程访问
            try {
                if (process == null) {
                    process = Runtime.getRuntime().exec("su")
                    outputStream = DataOutputStream(process!!.outputStream)
                    inputStream = BufferedReader(InputStreamReader(process!!.inputStream))
                    errorStream = BufferedReader(InputStreamReader(process!!.errorStream))
                }
                process != null
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start su process", e)
                false
            }
        }
    }

    // 执行 su 命令，创建协程
    fun exec(command: String): String {
        CoroutineScope(Dispatchers.IO).launch {  // 自动创建协程
            if (process == null || outputStream == null) {
                if (!initSu()) return@launch
            }

            val result = StringBuilder()
            val errorResult = StringBuilder()

            try {
                outputStream!!.writeBytes("$command\n")
                outputStream!!.flush()

                val timeout = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < timeout) {
                    if (inputStream?.ready() == true) {
                        val line = inputStream!!.readLine()
                        if (line != null) result.append(line).append("\n")
                    }
                    if (errorStream?.ready() == true) {
                        val errorLine = errorStream!!.readLine()
                        if (errorLine != null) errorResult.append(errorLine).append("\n")
                    }
                }

                if (errorResult.isNotEmpty()) {
                    Log.e(TAG, "Shell Error: $errorResult")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
            }

            Log.d(TAG, "Command result: ${result.toString().trim()}")
        }

        return "Command is being executed"
    }

    // 关闭 su 进程
    suspend fun close() {
        mutex.withLock {
            try {
                outputStream?.writeBytes("exit\n")
                outputStream?.flush()
                outputStream?.close()
                inputStream?.close()
                errorStream?.close()
                process?.destroy()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing su process", e)
            } finally {
                process = null
                outputStream = null
                inputStream = null
                errorStream = null
            }
        }
    }
}

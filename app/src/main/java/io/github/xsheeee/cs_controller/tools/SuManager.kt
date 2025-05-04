package io.github.xsheeee.cs_controller.tools

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean

object SuManager {
    private const val TAG = "SuManager"

    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: BufferedReader? = null
    private var errorStream: BufferedReader? = null

    private val initialized = AtomicBoolean(false)
    private val mutex = Mutex()
    private val suScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 初始化su（协程友好）
    private suspend fun initSu(): Boolean {
        if (initialized.get()) return true

        return mutex.withLock {
            if (initialized.get()) return true
            return try {
                process = Runtime.getRuntime().exec("su")
                outputStream = DataOutputStream(process!!.outputStream)
                inputStream = BufferedReader(InputStreamReader(process!!.inputStream))
                errorStream = BufferedReader(InputStreamReader(process!!.errorStream))
                initialized.set(true)
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start su", e)
                false
            }
        }
    }

    // 同步执行命令（阻塞）
    fun exec(command: String, timeout: Long = 3000): String {
        if (!initialized.get() && !runBlocking { initSu() }) {
            return "Failed to initialize su"
        }

        synchronized(this) {
            val result = StringBuilder()
            val error = StringBuilder()
            val endTag = "__END_OF_COMMAND__"

            try {
                val out = outputStream ?: return "Output stream is null"
                val `in` = inputStream ?: return "Input stream is null"
                val err = errorStream ?: return "Error stream is null"

                out.writeBytes("$command\necho $endTag\n")
                out.flush()

                val endTime = System.currentTimeMillis() + timeout
                while (System.currentTimeMillis() < endTime) {
                    val line = `in`.readLine() ?: break
                    if (line.trim() == endTag) break
                    result.appendLine(line)
                }

                while (System.currentTimeMillis() < endTime && err.ready()) {
                    val line = err.readLine() ?: break
                    error.appendLine(line)
                }

                if (error.isNotEmpty()) {
                    Log.e(TAG, "Error: ${error.trim()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exec exception", e)
                return "Error: ${e.message}"
            }

            return result.toString().trim()
        }
    }

    // 异步执行命令（返回Deferred）
    fun execAsync(command: String, waitForResult: Boolean = false, timeout: Long = 3000): Deferred<String> {
        return suScope.async {
            if (!initSu()) return@async "Failed to initialize su"
            executeCommand(command, waitForResult, timeout)
        }
    }

    // 异步执行 + 回调
    fun execAsyncWithCallback(
        command: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        timeout: Long = 3000,
        callback: (String) -> Unit
    ): Job {
        return suScope.launch(dispatcher) {
            val result = execAsync(command, true, timeout).await()
            callback(result)
        }
    }

    // 内部实际执行逻辑
    private suspend fun executeCommand(command: String, waitForResult: Boolean, timeout: Long): String {
        return mutex.withLock {
            val result = StringBuilder()
            val error = StringBuilder()

            try {
                outputStream?.writeBytes("$command\n")
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "ExecAsync exception during command write", e)
                return@withLock "Error: ${e.message}"
            }

            if (waitForResult) {
                try {
                    withTimeoutOrNull(timeout) {
                        readStreamOutput(result, error)
                    }
                    if (error.isNotEmpty()) {
                        Log.e(TAG, "Error: ${error.trim()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ExecAsync exception during read", e)
                    return@withLock "Error: ${e.message}"
                }
            }

            result.toString().trim()
        }
    }

    // 封装输出读取（标准输出 & 错误输出）
    private suspend fun readStreamOutput(result: StringBuilder, error: StringBuilder) = coroutineScope {
        val input = inputStream
        val err = errorStream
        if (input == null || err == null) return@coroutineScope

        val job1 = launch {
            try {
                while (isActive) {
                    val line = input.readLine() ?: break
                    result.appendLine(line)
                    yield()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading stdout", e)
            }
        }

        val job2 = launch {
            try {
                while (isActive) {
                    val line = err.readLine() ?: break
                    error.appendLine(line)
                    yield()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading stderr", e)
            }
        }

        joinAll(job1, job2)
    }

    // 关闭 su 进程
    fun close() {
        synchronized(this) {
            try {
                outputStream?.writeBytes("exit\n")
                outputStream?.flush()
            } catch (_: IOException) {
            }

            try {
                outputStream?.close()
                inputStream?.close()
                errorStream?.close()
                process?.destroy()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing su", e)
            } finally {
                outputStream = null
                inputStream = null
                errorStream = null
                process = null
                initialized.set(false)
                suScope.cancel()
            }
        }
    }

    // 检查是否有 su 权限
    fun isSuAvailable(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec("su")
            proc.destroy()
            true
        } catch (e: Exception) {
            false
        }
    }
}

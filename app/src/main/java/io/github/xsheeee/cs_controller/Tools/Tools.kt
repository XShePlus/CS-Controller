package io.github.xsheeee.cs_controller.Tools

import android.content.Context
import com.topjohnwu.superuser.Shell
import io.github.xsheeee.cs_controller.Tools.Logger.showToast
import io.github.xsheeee.cs_controller.Tools.Logger.writeLog
import java.util.Arrays

class Tools(private val context: Context) {
    fun getModeName(mode: Int): String? {
        return MODE_MAP[mode]
    }

    fun init() {
        val paths = Arrays.asList(CS_CONFIG_PATH, Values.CSCPath)
        ensureDirectoriesAndFilesExist(paths)

        if (!checkDirectoryAndFileWithLibSu()) {
            createPathWithLibSu(LOG_PATH, true)
        }
    }

    private fun ensureDirectoriesAndFilesExist(paths: List<String>) {
        for (path in paths) {
            if (!executeShellCommand("test -e $path")) {
                handlePathError(path)
            }
        }
    }

    private fun handlePathError(path: String) {
        if (CS_CONFIG_PATH == path) {
            showToast("未安装 CS 调度")
        } else {
            createPathWithLibSu(path, Values.CSCPath == path)
        }
    }

    private fun createPathWithLibSu(path: String, isDirectory: Boolean) {
        val command = if (isDirectory) "su -c mkdir -p " else "su -c touch "
        if (!executeShellCommand(command + path)) {
            showToast("创建路径失败：$path")
        }
    }

    private fun checkDirectoryAndFileWithLibSu(): Boolean {
        return executeShellCommand("ls " + LOG_PATH)
    }

    val sU: Boolean
        get() = executeShellCommand("su -c id")

    fun changeMode(modeName: String?) {
        if (modeName == null || modeName.isEmpty()) {
            showToast("模式名称不能为空")
            return
        }

        val mode = getModeByName(modeName)
        if (mode != null) {
            writeToFile(CS_CONFIG_PATH, modeName)
        } else {
            showToast("无效的模式名称：$modeName")
        }
    }

    private fun getModeByName(modeName: String): Int? {
        for ((key, value) in MODE_MAP) {
            if (value == modeName) {
                return key
            }
        }
        return null
    }

    fun readFileWithShell(filePath: String): String? {
        val result = Shell.cmd("cat $filePath").exec()
        if (result.isSuccess && !result.out.isEmpty()) {
            return java.lang.String.join("\n", result.out)
        } else {
            logError("读取文件失败：$filePath", result)
            return null
        }
    }

    fun updateConfigEntry(filePath: String, key: String, newValue: String) {
        val content = readFileWithShell(filePath)
        if (content == null) {
            showToast("无法读取配置文件：$filePath")
            return
        }

        var updatedContent = content.replace(
            ("(?m)^$key =.*$").toRegex(),
            "$key = $newValue"
        )
        if (!updatedContent.contains("$key =")) {
            updatedContent += "\n$key = $newValue"
        }

        writeToFile(filePath, updatedContent.trim { it <= ' ' })
    }

    fun writeToFile(filePath: String, content: String) {
        if (!executeShellCommand("echo \"" + content.replace("\"", "\\\"") + "\" > " + filePath)) {
            showToast("写入失败：$filePath")
        }
    }

    val versionFromModuleProp: String?
        get() {
            val filePath = Values.csmodulePath
            val result = Shell.cmd("grep version= $filePath").exec()

            if (result.isSuccess && !result.out.isEmpty()) {
                return result.out[0].replace("version=", "").trim { it <= ' ' }
            } else {
                logError("读取 module.prop 失败：$filePath", result)
                return null
            }
        }

    fun isProcessRunning(processPath: String): Boolean {
        val result = Shell.cmd("pgrep -f $processPath").exec()
        return result.isSuccess && !result.out.isEmpty()
    }

    fun readLogFile(): String? {
        return readFileWithShell(LOG_PATH)
    }

    fun executeShellCommand(command: String): Boolean {
        val result = Shell.cmd(command).exec()
        if (!result.isSuccess) {
            logError("Shell 命令执行失败：$command", result)
        }
        return result.isSuccess
    }

    private fun showToast(message: String) {
        showToast(context, message)
    }

    private fun logError(message: String, result: Shell.Result) {
        writeLog("ERROR", TAG, message + " | Error: " + java.lang.String.join("\n", result.err))
    }

    companion object {
        private const val LOG_PATH = Values.csLog
        private val MODE_MAP: MutableMap<Int, String> = HashMap()
        private const val TAG = "Tools"
        private const val CS_CONFIG_PATH = Values.CSConfigPath

        init {
            MODE_MAP[1] = Values.powersaveName
            MODE_MAP[2] = Values.balanceName
            MODE_MAP[3] = Values.performanceName
            MODE_MAP[4] = Values.fastName
        }
    }
}
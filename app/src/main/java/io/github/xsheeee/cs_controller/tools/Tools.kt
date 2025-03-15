package io.github.xsheeee.cs_controller.tools

import android.content.Context
import com.topjohnwu.superuser.Shell
import io.github.xsheeee.cs_controller.tools.Logger.showToast
import io.github.xsheeee.cs_controller.tools.Logger.writeLog

class Tools(private val context: Context) {
    fun getModeName(mode: Int): String? {
        return MODE_MAP[mode]
    }

    fun changeMode(modeName: String?) {
        if (modeName.isNullOrEmpty()) {
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
        if (result.isSuccess && result.out.isNotEmpty()) {
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

    private fun writeToFile(filePath: String, content: String) {
        if (!executeShellCommand("echo \"" + content.replace("\"", "\\\"") + "\" > " + filePath)) {
            showToast("写入失败：$filePath")
        }
    }

    val versionFromModuleProp: String?
        get() {
            val filePath = Values.csmodulePath
            val result = Shell.cmd("grep version= $filePath").exec()

            if (result.isSuccess && result.out.isNotEmpty()) {
                return result.out[0].replace("version=", "").trim { it <= ' ' }
            } else {
                logError("读取 module.prop 失败：$filePath", result)
                return null
            }
        }

    fun isProcessRunning(processPath: String): Boolean {
        val result = Shell.cmd("pgrep -f $processPath").exec()
        return result.isSuccess && result.out.isNotEmpty()
    }

    private fun executeShellCommand(command: String): Boolean {
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
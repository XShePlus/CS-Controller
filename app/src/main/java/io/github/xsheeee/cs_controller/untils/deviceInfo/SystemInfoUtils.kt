package io.github.xsheeee.cs_controller.untils.deviceInfo

import io.github.xsheeee.cs_controller.tools.SuManager
import java.io.File

object SystemInfoUtils {

    fun getAndroidVersion(): String {
        return SuManager.exec("getprop ro.build.version.release")
    }
    fun getSocCode(): String {
        return SuManager.exec("getprop ro.soc.model")
    }
    fun getDeviceName(): String {
        return SuManager.exec("getprop ro.product.marketname")
    }
    fun getIOScheduler(): String {
        return SuManager.exec("cat /sys/block/sda/queue/scheduler")
    }
    fun getPolicyFrequencies(): String {
        val builder = StringBuilder()
        val basePath = "/sys/devices/system/cpu/cpufreq"
        val policies = File(basePath).listFiles { file -> file.name.startsWith("policy") } ?: return ""

        policies.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { policyDir ->
                val freqFile = File(policyDir, "scaling_available_frequencies")
                val policyName = policyDir.name
                if (freqFile.exists()) {
                    val content = SuManager.exec("cat ${freqFile.absolutePath}")
                    if (content.isNotEmpty()) {
                        builder.append("[$policyName] 可用频率：\n$content\n")
                    } else {
                        builder.append("[$policyName] 无法获取频率信息\n")
                    }
                } else {
                    builder.append("[$policyName] 不存在 scaling_available_frequencies 文件\n")
                }
            }
        return builder.toString().trim()
    }
}

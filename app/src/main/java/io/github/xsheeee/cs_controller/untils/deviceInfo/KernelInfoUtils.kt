package io.github.xsheeee.cs_controller.untils.deviceInfo

import io.github.xsheeee.cs_controller.tools.SuManager

object KernelInfoUtils {

    fun getKernelVersion(): String {
        return SuManager.exec("uname -r")
    }
}

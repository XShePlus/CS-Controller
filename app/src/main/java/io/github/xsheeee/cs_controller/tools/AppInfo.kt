package io.github.xsheeee.cs_controller.tools

import android.graphics.drawable.Drawable

class AppInfo(var icon: Drawable?, var appName: String?, var packageName: String?) {
    var performanceMode: String? = null
    var isPriority: Boolean = false

    init {
        this.performanceMode = ""
        this.isPriority = false
    }

    override fun toString(): String {
        return ("AppInfo [icon=" + icon + ", appName=" + appName + ", packageName=" + packageName
                + ", performanceMode=" + performanceMode + ", isPriority=" + isPriority + "]")
    }
}
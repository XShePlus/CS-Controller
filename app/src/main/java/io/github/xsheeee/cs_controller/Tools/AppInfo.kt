package io.github.xsheeee.cs_controller.Tools

import android.graphics.drawable.Drawable

class AppInfo {
    var icon: Drawable? = null
    var appName: String? = null
    var packageName: String? = null
    var performanceMode: String? = null
    var isPriority: Boolean = false

    constructor(icon: Drawable?, appName: String?, packageName: String?) {
        this.icon = icon
        this.appName = appName
        this.packageName = packageName
        this.performanceMode = ""
        this.isPriority = false
    }

    constructor()

    override fun toString(): String {
        return ("AppInfo [icon=" + icon + ", appName=" + appName + ", packageName=" + packageName
                + ", performanceMode=" + performanceMode + ", isPriority=" + isPriority + "]")
    }
}
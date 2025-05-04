package io.github.xsheeee.cs_controller.ui

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    companion object;

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

}
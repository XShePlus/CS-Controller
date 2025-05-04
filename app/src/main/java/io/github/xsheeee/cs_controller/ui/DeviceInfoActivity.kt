package io.github.xsheeee.cs_controller.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.xsheeee.cs_controller.ui.screen.DeviceInfoUI
import io.github.xsheeee.cs_controller.ui.ui.theme.CSControllerTheme

class DeviceInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CSControllerTheme {
                DeviceInfoUI(onBackClick = { finish() })
            }
        }
    }
}
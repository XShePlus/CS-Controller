package io.github.xsheeee.cs_controller.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues.TAG
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xsheeee.cs_controller.R
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Logger.showToast
import io.github.xsheeee.cs_controller.untils.deviceInfo.KernelInfoUtils
import io.github.xsheeee.cs_controller.untils.deviceInfo.SystemInfoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceInfo(
    val manufacturer: String = Build.MANUFACTURER,
    val deviceName: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = Build.VERSION.SDK_INT,
    val kernelVersion: String = "",
    val socCode: String = "",
    val hardware: String = Build.HARDWARE,
    val policyFrequencies: String = "",
    val iOScheduler: String = ""
)

class DeviceInfoViewModel {
    private val _deviceInfo = mutableStateOf<DeviceInfo?>(null)
    val deviceInfo: State<DeviceInfo?> = _deviceInfo

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    suspend fun loadDeviceInfo() {
        try {
            _isLoading.value = true
            val info = withContext(Dispatchers.IO) {
                DeviceInfo(
                    androidVersion = SystemInfoUtils.getAndroidVersion(),
                    kernelVersion = KernelInfoUtils.getKernelVersion(),
                    policyFrequencies = SystemInfoUtils.getPolicyFrequencies(),
                    socCode = SystemInfoUtils.getSocCode(),
                    iOScheduler = SystemInfoUtils.getIOScheduler(),
                    deviceName = SystemInfoUtils.getDeviceName()
                )
            }
            _deviceInfo.value = info
        } catch (e: Exception) {
            Logger.writeLog(TAG,"E","获取失败")
        } finally {
            _isLoading.value = false
        }
    }

    fun getClipboardText(): String {
        val info = _deviceInfo.value ?: return ""
        return buildString {
            append("设备制造商: ${info.manufacturer}\n")
            append("设备名称: ${info.deviceName}\n")
            append("Android版本: ${info.androidVersion} (SDK: ${info.sdkVersion})\n")
            append("内核版本: ${info.kernelVersion}\n\n")
            append("SoCInfo:\n")
            append("SocCode: ${info.socCode}\n")
            append("hardWear: ${info.hardware}\n")
            append("Policy: ${info.policyFrequencies}\n\n")
            append("I/O Info:\n")
            append("Schedule: ${info.iOScheduler}")
        }
    }
}

@Composable
fun DeviceInfoUI(onBackClick: () -> Unit) {
    val viewModel = remember { DeviceInfoViewModel() }
    val deviceInfo by viewModel.deviceInfo
    val isLoading by viewModel.isLoading
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadDeviceInfo()
    }

    DeviceInfoLayout(
        deviceInfo = deviceInfo,
        isLoading = isLoading,
        onBackClick = onBackClick,
        onCopyClick = {
            if (deviceInfo != null) {
                copyToClipboard(
                    context,
                    "设备信息",
                    viewModel.getClipboardText(),
                    "设备信息已复制到剪贴板"
                )
            }
        }
    )
}

@Composable
fun DeviceInfoLayout(
    deviceInfo: DeviceInfo?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    Scaffold(
        topBar = {
            DeviceInfoTopBar(
                onBackClick = onBackClick,
                onCopyClick = onCopyClick,
                showCopyButton = deviceInfo != null
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator()
            }

            AnimatedVisibility(
                visible = !isLoading && deviceInfo != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                deviceInfo?.let { DeviceInfoContent(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoTopBar(
    onBackClick: () -> Unit,
    onCopyClick: () -> Unit,
    showCopyButton: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.device_info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_arrow_back_24),
                    contentDescription = stringResource(id = R.string.device_code)
                )
            }
        },
        actions = {
            if (showCopyButton) {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.content_copy_24px),
                        contentDescription = "复制信息"
                    )
                }
            }
        }
    )
}

@Composable
fun DeviceInfoContent(info: DeviceInfo) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 设备基本信息卡片
        InfoCard(
            title = "设备信息",
            content = {
                InfoItem(label = "设备制造商: ", value = info.manufacturer)
                InfoItem(label = "设备代号: ", value = info.deviceName)
                InfoItem(
                    label = "Android版本: ",
                    value = "${info.androidVersion} (SDK: ${info.sdkVersion})"
                )
                InfoItem(label = "内核版本: ", value = info.kernelVersion)
            }
        )

        InfoCard(
            title = "SoC Info",
            content = {
                InfoItem(label = "SocCode: ", value = info.socCode)
                InfoItem(label = "HardWear: ", value = info.hardware)
                InfoItem(label = "Policy", value = info.policyFrequencies, multiLine = true)
            }
        )

        InfoCard(
            title = "I/O Info",
            content = {
                InfoItem(label = "Scheduler", value = info.iOScheduler, multiLine = true)
            }
        )
    }
}

@Composable
fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Card(
        colors = cardColors,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            content()
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    multiLine: Boolean = false
) {
    Text(
        text = if (multiLine) "$label: \n$value" else "$label$value",
        style = MaterialTheme.typography.bodyLarge
    )
}

fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String? = null
) {
    try {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clipData)

        toastMessage?.let {
            showToast(context,it)
        }
    } catch (e: Exception) {
        showToast(context,"复制失败")
        Logger.writeLog(TAG,"E","复制失败")
    }
}
package io.github.xsheeee.cs_controller.ui.screen

import android.os.FileObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xsheeee.cs_controller.R
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Values
import io.github.xsheeee.cs_controller.ui.ui.theme.CSControllerTheme
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import kotlinx.coroutines.launch

enum class LogType {
    MODULE_LOG,
    APP_LOG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBackPressed: () -> Unit) {
    CSControllerTheme {
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState { 2 }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                    topBar = {
                        Column {
                            CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                                text = stringResource(R.string.module_log),
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = onBackPressed) {
                                            Icon(
                                                    painter =
                                                            painterResource(
                                                                    id =
                                                                            R.drawable
                                                                                    .outline_arrow_back_24
                                                            ),
                                                    contentDescription = "返回",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    actions = {
                                        if (pagerState.currentPage == 1) {
                                            IconButton(
                                                    onClick = {
                                                        clearLogFile(LogType.APP_LOG)
                                                        loadLogFile(LogType.APP_LOG) { content -> }
                                                    }
                                            ) {
                                                Icon(
                                                        painter =
                                                                painterResource(
                                                                        id =
                                                                                R.drawable
                                                                                        .baseline_clear_all_24
                                                                ),
                                                        contentDescription = "清空",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    },
                                    colors =
                                            TopAppBarDefaults.centerAlignedTopAppBarColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.surface,
                                                    titleContentColor =
                                                            MaterialTheme.colorScheme.onSurface
                                            )
                            )
                            TabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tab(
                                        selected = pagerState.currentPage == 0,
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        },
                                        text = {
                                            Text(
                                                    "模块日志",
                                                    color =
                                                            if (pagerState.currentPage == 0)
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .onSurfaceVariant
                                            )
                                        }
                                )
                                Tab(
                                        selected = pagerState.currentPage == 1,
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                        },
                                        text = {
                                            Text(
                                                    "应用日志",
                                                    color =
                                                            if (pagerState.currentPage == 1)
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .onSurfaceVariant
                                            )
                                        }
                                )
                            }
                        }
                    }
            ) { paddingValues ->
                HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) { page ->
                    when (page) {
                        0 -> LogContent(LogType.MODULE_LOG)
                        1 -> LogContent(LogType.APP_LOG)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogContent(logType: LogType) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = Int.MAX_VALUE)
    var logContent by remember { mutableStateOf("") }
    var fileObserver by remember { mutableStateOf<FileObserver?>(null) }

    LaunchedEffect(logContent) {
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    LaunchedEffect(logType) { loadLogFile(logType) { content -> logContent = content } }

    DisposableEffect(logType) {
        val logFile =
                File(
                        if (logType == LogType.MODULE_LOG) Values.csLog
                        else "/storage/emulated/0/Android/CSController/app_log.txt"
                )
        if (logFile.exists()) {
            fileObserver =
                    object : FileObserver(logFile.path, FileObserver.MODIFY) {
                        override fun onEvent(event: Int, path: String?) {
                            if (event == FileObserver.MODIFY) {
                                loadLogFile(logType) { content -> logContent = content }
                            }
                        }
                    }
            fileObserver?.startWatching()
        }

        onDispose { fileObserver?.stopWatching() }
    }

    LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        item {
            BasicTextField(
                    value = logContent,
                    onValueChange = {},
                    readOnly = true,
                    textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                            ),
                    modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun loadLogFile(logType: LogType, onContentLoaded: (String) -> Unit) {
    val logFile =
            File(
                    if (logType == LogType.MODULE_LOG) Values.csLog
                    else "/storage/emulated/0/Android/CSController/app_log.txt"
            )
    if (!logFile.exists()) {
        onContentLoaded("日志文件不存在：${logFile.absolutePath}")
        return
    }

    try {
        val content = StringBuilder()
        BufferedReader(FileReader(logFile)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                content.append(line).append('\n')
            }
        }
        onContentLoaded(content.toString())
    } catch (e: IOException) {
        onContentLoaded("读取日志文件时发生错误：${e.message}")
    }
}

private fun clearLogFile(logType: LogType) {
    val logFile =
            File(
                    if (logType == LogType.MODULE_LOG) Values.csLog
                    else "/storage/emulated/0/Android/CSController/app_log.txt"
            )
    if (!logFile.exists()) return

    try {
        FileWriter(logFile, false).use { writer -> writer.write("") }
    } catch (e: IOException) {
        Logger.e("LogScreen", "清空日志文件时发生错误：${e.message}")
    }
}

package io.github.xsheeee.cs_controller;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.FileObserver;
import io.github.xsheeee.cs_controller.Tools.Values;

public class LogActivity extends AppCompatActivity {

    private TextView logTextView;
    private NestedScrollView scrollView; // 用来滚动的容器
    private ExecutorService executorService;
    private FileObserver fileObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // 初始化 Toolbar
        Toolbar toolbar = findViewById(R.id.backButton4);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        logTextView = findViewById(R.id.log_text_view);
        scrollView = findViewById(R.id.scroll_view);
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::loadLogFile);
        startFileObserver();
    }

    private void startFileObserver() {
        File logFile = new File(Values.csLog);

        fileObserver = new FileObserver(logFile, FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if (event == FileObserver.MODIFY) {
                    executorService.submit(LogActivity.this::loadLogFile);
                }
            }
        };
        fileObserver.startWatching();
    }

    private void loadLogFile() {
        String logContent = readLogFile();

        runOnUiThread(() -> {
            logTextView.setText(logContent != null ? logContent : "无法读取日志文件");

            // 更新日志后滚动到底部
            scrollView.post(() -> scrollView.fullScroll(NestedScrollView.FOCUS_DOWN));
        });
    }

    private String readLogFile() {
        StringBuilder content = new StringBuilder();
        File logFile = new File(Values.csLog);

        if (!logFile.exists()) {
            return "日志文件不存在：" + logFile.getAbsolutePath();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "读取日志文件时发生错误：" + e.getMessage();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭 ExecutorService
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        // 停止 FileObserver
        if (fileObserver != null) {
            fileObserver.stopWatching();
        }
    }
}
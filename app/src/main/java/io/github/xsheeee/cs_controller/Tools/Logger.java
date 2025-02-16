package io.github.xsheeee.cs_controller.Tools;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    public static final String LOG_PATH = "/sdcard/Android/CSController/log.txt";
    public static final long MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB

    public static void writeLog(String TAG, String type, String logMessage) {
        File logFile = new File(LOG_PATH);
        File logDir = logFile.getParentFile();

        // 确保目录存在
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "][" + TAG + "][" + type + "] " + logMessage + "\n";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logEntry);
        } catch (IOException e) {
            try (BufferedWriter errorWriter = new BufferedWriter(new FileWriter(logFile, true))) {
                errorWriter.write("[" + timestamp + "][ERROR] Log write failed: " + e.toString() + "\n");
            } catch (IOException ignored) {
            }
        }

        checkAndTrimLogFile(logFile);
    }

    // 超过 10MB 删除最早一行
    private static void checkAndTrimLogFile(File logFile) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_SIZE) {
            return;
        }

        File tempFile = new File(logFile.getAbsolutePath() + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            reader.readLine();
	  
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

    }

    public static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper())
            .post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}
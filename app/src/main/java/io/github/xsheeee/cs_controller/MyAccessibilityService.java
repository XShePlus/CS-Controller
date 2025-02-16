package io.github.xsheeee.cs_controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;
import io.github.xsheeee.cs_controller.Tools.ConfigManager;
import io.github.xsheeee.cs_controller.Tools.ConfigManager.ConfigData;
import io.github.xsheeee.cs_controller.Tools.Logger;
import io.github.xsheeee.cs_controller.Tools.Values;
import java.io.*;
import java.util.*;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.Rect;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class MyAccessibilityService extends AccessibilityService {
  private static final String TAG = "Accessibility";
  private static final String CHANNEL_ID = "cs_controller_channel";
  private static final int NOTIFICATION_ID = 1;
  private WindowManager windowManager;
  private View floatingView;
  private Handler handler;
  private String previousWindow = "未知";
  private NotificationManager notificationManager;
  private Map<String, String> appModeMap = new HashMap<>();
  private String defaultMode = "fast";
  private String currentMode = defaultMode;
  private FileObserver configObserver;
  private Set<String> cachedDesktopApps = new HashSet<>();

  private boolean floatingWindowEnabled = true;
  private boolean floatingWindowLogicEnabled = true;
  private boolean isServiceInitialized = false;

  private ConfigData configData;

  private void loadAppConfig() {
    configData = ConfigManager.loadConfig(Values.appConfig);
    appModeMap = configData.appModeMap;
    defaultMode = configData.defaultMode;
    floatingWindowEnabled = configData.floatingWindowEnabled;

    updateFloatingWindowState();
  }

  private void updateAppMode(String packageName, String mode) {
    ConfigManager.updateAppMode(Values.appConfig, packageName, mode);
    loadAppConfig(); // 重新加载配置
  }

  @Override
  public void onCreate() {
    super.onCreate();
    try {
      initializeService();
    } catch (Exception e) {
      logError("Service initialization failed: " + e.getMessage());
      // 延迟3秒后重试初始化
      if (handler != null) {
        handler.postDelayed(this::initializeService, 3000);
      } else {
        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::initializeService, 3000);
      }
    }
  }

  private void initializeService() {
    if (isServiceInitialized) {
      return;
    }

    try {
      handler = new Handler(Looper.getMainLooper());
      windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
      notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

      // 创建必要的目录
      createRequiredDirectories();

      loadAppConfig();
      setupConfigObserver();
      showNotification();
      checkOverlayPermission();

      isServiceInitialized = true;
      Log.d(TAG, "Service initialized successfully");
    } catch (Exception e) {
      logError("Failed to initialize service: " + e.getMessage());
      handler.postDelayed(this::initializeService, 3000);
    }
  }

  private void createRequiredDirectories() {
    try {
      File configParent = new File(Values.appConfig).getParentFile();

      if (configParent != null && !configParent.exists()) {
        boolean created = configParent.mkdirs();
        Log.d(TAG, "Config parent directory created: " + created);
      }

    } catch (Exception e) {
      logError("Error creating directories: " + e.getMessage());
      throw e;
    }
  }

  private void updateFloatingWindowState() {
    try {
      if (floatingWindowEnabled && floatingWindowLogicEnabled) {
        if (floatingView == null) {
          createFloatingWindow();
        }
      } else {
        removeFloatingWindow();
      }
    } catch (Exception e) {
      logError("Error updating floating window state: " + e.getMessage());
    }
  }

  private void createFloatingWindow() {
    if (floatingView != null || !floatingWindowEnabled || !floatingWindowLogicEnabled) {
      return;
    }

    try {
      floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
      WindowManager.LayoutParams params =
          new WindowManager.LayoutParams(
              WindowManager.LayoutParams.MATCH_PARENT,
              WindowManager.LayoutParams.WRAP_CONTENT,
              WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                  | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                  | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              -3);
      params.gravity = Gravity.TOP;
      params.alpha = 0.8f;

      windowManager.addView(floatingView, params);
    } catch (Exception e) {
      logError("Error creating floating window: " + e.getMessage());
      floatingView = null;
    }
  }

  private void removeFloatingWindow() {
    if (floatingView != null) {
      try {
        windowManager.removeView(floatingView);
      } catch (Exception e) {
        logError("Error removing floating window: " + e.getMessage());
      } finally {
        floatingView = null;
      }
    }
  }

  private void setupConfigObserver() {
    File configFile = new File(Values.appConfig);
    File configDirectory = configFile.getParentFile();

    if (configObserver != null) {
      configObserver.stopWatching();
    }

    if (configDirectory != null) {
      configObserver =
          new FileObserver(
              configDirectory,
              FileObserver.MODIFY
                  | FileObserver.CREATE
                  | FileObserver.DELETE
                  | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String path) {
              if (path != null && path.equals(configFile.getName())) {
                handler.post(
                    () -> {
                      Log.d(TAG, "Config file changed, event: " + event);
                      try {
                        loadAppConfig();
                      } catch (Exception e) {
                        logError("Failed to reload config: " + e.getMessage());
                      }
                    });
              }
            }
          };

      try {
        configObserver.startWatching();
        Log.d(TAG, "File observer started successfully");
      } catch (Exception e) {
        logError("Failed to start file observer: " + e.getMessage());
        handler.postDelayed(this::setupConfigObserver, 5000);
      }
    }
  }

  private void applyConfigChanges(
      Map<String, String> newAppModeMap, String newDefaultMode, boolean newFloatingWindowEnabled) {
    appModeMap = newAppModeMap;
    defaultMode = newDefaultMode;
    floatingWindowEnabled = newFloatingWindowEnabled;
    updateFloatingWindowState();
  }

  private void updateCSConfig(String mode) {
    if (!mode.equals(currentMode)) {
      try (FileWriter writer = new FileWriter(Values.CSConfigPath)) {
        writer.write(mode);
        currentMode = mode;
        Log.d(TAG, "CS config updated to: " + mode);
      } catch (IOException e) {
        logError("Error updating CS config: " + e.getMessage());
      }
    }
  }

  private String getAppMode(String packageName) {
    return configData.appModeMap.getOrDefault(packageName, configData.defaultMode);
  }

  private boolean isDefaultDesktopApp(String packageName) {
    if (cachedDesktopApps.contains(packageName)) {
      return true;
    }

    try {
      PackageManager pm = getPackageManager();
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.addCategory(Intent.CATEGORY_HOME);
      List<ResolveInfo> resolveInfoList =
          pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);

      for (ResolveInfo resolveInfo : resolveInfoList) {
        if (packageName.equals(resolveInfo.activityInfo.packageName)) {
          cachedDesktopApps.add(packageName);
          return true;
        }
      }
    } catch (Exception e) {
      logError("Error checking desktop app: " + e.getMessage());
    }

    return false;
  }

  @Override
  protected void onServiceConnected() {
    try {
      AccessibilityServiceInfo info = new AccessibilityServiceInfo();
      info.eventTypes =
          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
              | AccessibilityEvent.TYPE_VIEW_CLICKED
              | AccessibilityEvent.TYPE_VIEW_FOCUSED;
      info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
      setServiceInfo(info);
      Log.d(TAG, "Service connected successfully");
    } catch (Exception e) {
      logError("Error in onServiceConnected: " + e.getMessage());
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null || event.getSource() == null) return;

    try {
      String currentWindow =
          event.getPackageName() != null ? event.getPackageName().toString() : "未知";

      if (!currentWindow.equals(previousWindow)) {
        String debugInfo = generateDebugInfo(event, currentWindow);
        updateFloatingWindow(debugInfo);
        previousWindow = currentWindow;
        Log.d(TAG, debugInfo);

        if (!currentWindow.equals("未知")) {
          String actualPackage =
              shouldExclude(event, currentWindow) ? previousWindow : currentWindow;
          String mode = getAppMode(actualPackage);
          updateCSConfig(mode);
        }
      }
    } catch (Exception e) {
      logError("Error processing accessibility event: " + e.getMessage());
    }
  }

  private String generateDebugInfo(AccessibilityEvent event, String currentWindow) {
    if (event.getSource() == null) return "-1";

    try {
      Rect rect = new Rect();
      event.getSource().getBoundsInScreen(rect);

      String actualPackage = shouldExclude(event, currentWindow) ? previousWindow : currentWindow;
      String mode = getAppMode(actualPackage);

      return String.format(
          "屏幕：%d x %d\n事件: %s\n来源：%s\n层级: %d\n类型：%d\n此前: %s\n现在: %s\n实际: %s\n当前模式: %s",
          getRealScreenHeight(),
          getRealScreenWidth(),
          AccessibilityEvent.eventTypeToString(event.getEventType()),
          event.getClassName(),
          event.getSource().getChildCount(),
          event.getEventType(),
          previousWindow,
          currentWindow,
          actualPackage,
          mode);
    } catch (Exception e) {
      logError("Error generating debug info: " + e.getMessage());
      return "-1";
    }
}
  private boolean shouldExclude(AccessibilityEvent event, String packageName) {
    return event.getClassName() != null
        && (event.getClassName().toString().equals("android.inputmethodservice.SoftInputWindow")
            || event.getClassName().toString().equals("android.widget.FrameLayout")
            || previousWindow.equals("android"));
  }

  private void updateFloatingWindow(String debugInfo) {
    if (floatingView != null) {
      handler.post(
          () -> {
            try {
              TextView textView = floatingView.findViewById(R.id.debug_info);
              if (textView != null) {
                textView.setText(debugInfo);
              }
            } catch (Exception e) {
              logError("Error updating floating window text: " + e.getMessage());
            }
          });
    }
  }

  private void showNotification() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL_ID, "CS Controller Service", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
      }

      Notification notification =
          new Notification.Builder(this, CHANNEL_ID)
              .setContentTitle("CS Controller 正在运行")
              .setSmallIcon(R.drawable.ic_notification)
              .setOngoing(true)
              .build();

      startForeground(NOTIFICATION_ID, notification);
    } catch (Exception e) {
      logError("Error showing notification: " + e.getMessage());
    }
  }

  private void checkOverlayPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!Settings.canDrawOverlays(this)) {
        try {
          Toast.makeText(this, "请开启悬浮窗权限", Toast.LENGTH_SHORT).show();
          Intent intent =
              new Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:" + getPackageName()));
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        } catch (Exception e) {
          logError("Error requesting overlay permission: " + e.getMessage());
        }
      }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);

    // 如果服务还没初始化，尝试初始化
    if (!isServiceInitialized) {
      initializeService();
    }

    // 确保服务被系统杀死后会重新创建
    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);
    try {
      // 在应用被从最近任务列表移除时重启服务
      Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
      restartServiceIntent.setPackage(getPackageName());
      startService(restartServiceIntent);
    } catch (Exception e) {
      logError("Error restarting service: " + e.getMessage());
    }
  }

  @Override
  public void onInterrupt() {
    Log.w(TAG, "Service interrupted");
    // 服务中断时的处理
    handler.postDelayed(this::initializeService, 3000);
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "Service being destroyed");
    cleanup();

    // 尝试重启服务
    try {
      Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
      restartServiceIntent.setPackage(getPackageName());
      startService(restartServiceIntent);
    } catch (Exception e) {
      logError("Failed to restart service: " + e.getMessage());
    }

    super.onDestroy();
  }

  private void cleanup() {
    try {
      isServiceInitialized = false;

      if (floatingView != null) {
        windowManager.removeView(floatingView);
        floatingView = null;
      }

      if (configObserver != null) {
        configObserver.stopWatching();
        configObserver = null;
      }

      notificationManager.cancel(NOTIFICATION_ID);

      // 清理其他资源
      appModeMap.clear();
      cachedDesktopApps.clear();

      Log.d(TAG, "Service cleanup completed");
    } catch (Exception e) {
      logError("Error during cleanup: " + e.getMessage());
    }
  }

  private int getRealScreenWidth() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
        return windowManager.getCurrentWindowMetrics().getBounds().width();
      } else { // Android 10-
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
      }
    } catch (Exception e) {
      logError("Error getting screen width: " + e.getMessage());
      return -1;
    }
  }

  private int getRealScreenHeight() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
        return windowManager.getCurrentWindowMetrics().getBounds().height();
      } else { // Android 10-
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
      }
    } catch (Exception e) {
      logError("Error getting screen height: " + e.getMessage());
      return -2;
    }
  }

  private void logError(String message) {
    Logger.writeLog("ERROR", TAG, message);
  }
}

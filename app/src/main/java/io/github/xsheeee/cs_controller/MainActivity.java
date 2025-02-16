package io.github.xsheeee.cs_controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.printservice.PrintService;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.content.ContextCompat;
import android.os.Environment;
import android.app.ActivityManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import io.github.xsheeee.cs_controller.Tools.Tools;
import io.github.xsheeee.cs_controller.Tools.Values;
import io.github.xsheeee.cs_controller.Tools.Logger;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;

public class MainActivity extends AppCompatActivity {
  private Tools tools;
  private TextView configTextView;
  private AutoCompleteTextView menu;
  private TextView versionTextView;
  private TextView processStatusTextView;
  private MaterialCardView processStatusCard;
  private MaterialCardView rootWarningCard;
  private MaterialCardView runCsServiceSh;
  private static final String TAG = "MainActivity";

  private static final int REQUEST_STORAGE_PERMISSION = 100;
  private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 1;
  private static final String CONFIG_FILE_PATH = Values.appConfig;
  private boolean shouldKillMainProcess = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    checkStoragePermission();
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                moveTaskToBack(true);
                excludeFromRecents(true);
                shouldKillMainProcess = true; // 返回键退出

                new Handler(Looper.getMainLooper())
                    .postDelayed(
                        () -> {
                          if (shouldKillMainProcess) {
                            killMainProcess();
                          }
                        },
                        1000);

                finish();
              }
            });

    tools = new Tools(getApplicationContext());

    configTextView = findViewById(R.id.config_text_view);
    versionTextView = findViewById(R.id.version_text_view);
    processStatusTextView = findViewById(R.id.process_status_text_view);
    processStatusCard = findViewById(R.id.process_status_card);
    rootWarningCard = findViewById(R.id.root_warning_card);
    runCsServiceSh = findViewById(R.id.run_service_sh);
    TextInputLayout textInputLayout = findViewById(R.id.menu);
    menu = (AutoCompleteTextView) textInputLayout.getEditText();

    setupDropdownMenu();
    updateConfigTextView();
    updateVersionTextView();
    updateProcessStatusTextView();
    checkRootStatus();
    runServiceSh();

    AppCompatImageView logo = findViewById(R.id.main_logo);
    logo.setOnClickListener(
        v -> {
          Intent intent = new Intent(this, AboutActivity.class);
          startActivity(intent);
        });

    MaterialCardView appListConfigButton = findViewById(R.id.go_app_list);
    appListConfigButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(this, AppListActivity.class);
          startActivity(intent);
        });

    MaterialCardView goLogButton = findViewById(R.id.go_log);
    goLogButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(this, LogActivity.class);
          startActivity(intent);
        });

    MaterialCardView goSettingsButton = findViewById(R.id.go_settings);
    goSettingsButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(this, SettingsActivity.class);
          startActivity(intent);
        });
    MaterialCardView runCsServiceSh = findViewById(R.id.run_service_sh);
    runCsServiceSh.setOnClickListener(
        v -> {
          new Thread(
                  () -> {
                    try {
                      new ProcessBuilder("su", "-c", "sh", Values.CsServicePath).start().waitFor();
                    } catch (Exception e) {
                      logError(e.getMessage());
                    }
                  })
              .start();
          updateConfigTextView();
        });
  }

  private void setupDropdownMenu() {
    String[] items = getResources().getStringArray(R.array.select_mode);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, items);
    menu.setAdapter(adapter);

    menu.setOnItemClickListener(
        (parent, view, position, id) -> {
          int mode = position + 1;
          changeModeInMainActivity(mode);
        });
  }

  private void changeModeInMainActivity(int mode) {
    String modeName = tools.getModeName(mode); 
    if (modeName != null) {
      tools.changeMode(modeName);

      // 检查文件是否存在
      File configFile = new File(CONFIG_FILE_PATH);
      if (!configFile.exists()) {
        createDefaultConfigFile();
      }

      String fileContent = tools.readFileWithShell(CONFIG_FILE_PATH);
      if (fileContent != null && !fileContent.isEmpty()) {
        try {
          // 解析 JSON 数据
          JSONObject jsonObject = new JSONObject(fileContent);
          jsonObject.put("default", modeName);

          String formattedJson = jsonObject.toString(2);

          // 将 JSON 写入文件
          FileWriter writer = new FileWriter(configFile);
          BufferedWriter bufferedWriter = new BufferedWriter(writer);
          bufferedWriter.write(formattedJson);
          bufferedWriter.close();
          updateConfigTextView();
        } catch (JSONException e) {
          logError(e.getMessage());
          configTextView.setText("ERROR");
        } catch (IOException e) {
          logError(e.getMessage());
          configTextView.setText("ERROR");
        }
      } else {
        createDefaultConfigFile();
      }
    } else {
      configTextView.setText("ERROR");
    }
  }

  private void createDefaultConfigFile() {
    // 创建默认的 JSON 配置文件
    JSONObject jsonObject = new JSONObject();
    try {
      jsonObject.put("default", "powersave");
	    jsonObject.put("log","Disable");
      jsonObject.put("floatingWindow", false);
      jsonObject.put("powersave", new JSONArray());
      jsonObject.put("balance", new JSONArray());
      jsonObject.put("performance", new JSONArray());
      jsonObject.put("fast", new JSONArray());

      String formattedJson = jsonObject.toString(2);

      // 写入文件
      FileWriter writer = new FileWriter(CONFIG_FILE_PATH);
      BufferedWriter bufferedWriter = new BufferedWriter(writer);
      bufferedWriter.write(formattedJson);
      bufferedWriter.close();
    } catch (JSONException | IOException e) {
      logError(e.getMessage());
      configTextView.setText("ERROR");
    }
  }

  private void updateConfigTextView() {
    File configFile = new File(CONFIG_FILE_PATH);
    if (configFile.exists()) {
      String fileContent = tools.readFileWithShell(CONFIG_FILE_PATH);
      if (fileContent != null) {
        String modeString = getString(R.string.now_mode);
        String defaultMode = getDefaultModeFromConfig(fileContent);
        configTextView.setText(String.format("%s%s", modeString, defaultMode));
      } else {
        configTextView.setText(R.string.read_mode_error);
      }
    } else {
      configTextView.setText(R.string.read_mode_error);
    }

    // 更新相关 UI 元素
    updateVersionTextView();
    updateProcessStatusTextView();
    runServiceSh();
    updateProcessStatusTextView();
  }

  private String getDefaultModeFromConfig(String fileContent) {
    try {
      JSONObject jsonObject = new JSONObject(fileContent);
      return jsonObject.has("default") ? jsonObject.getString("default") : "Unknown";
    } catch (JSONException e) {
      return "Unknown";
    }
  }

  private void updateVersionTextView() {
    String version = tools.getVersionFromModuleProp();
    if (version != null) {
      String versionString = getString(R.string.cs_version);
      versionTextView.setText(String.format("%s%s", versionString, version));
    } else {
      versionTextView.setText(R.string.read_version_error);
    }
  }

  public void updateProcessStatusTextView() {
    String statusPrefix = getString(R.string.cs_work);
    if (tools.isProcessRunning(Values.csProcess)) {
      processStatusTextView.setText(
          String.format("%s%s", statusPrefix, getString(R.string.cs_work_true)));
      TypedValue typedValue = new TypedValue();
      getTheme().resolveAttribute(R.attr.colorSecondaryContainer, typedValue, true);
      int color = typedValue.data;
      processStatusCard.setCardBackgroundColor(color);
    } else {
      processStatusTextView.setText(
          String.format("%s%s", statusPrefix, getString(R.string.cs_work_false)));
      TypedValue typedValue = new TypedValue();
      getTheme().resolveAttribute(R.attr.colorErrorContainer, typedValue, true);
      int color = typedValue.data;
      processStatusCard.setCardBackgroundColor(color);
    }
    processStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
  }

  public void runServiceSh() {
    if (tools.isProcessRunning(Values.csProcess)) {
      // 创建透明度和缩小动画
      AlphaAnimation alphaAnimation = new AlphaAnimation(1, 0);
      alphaAnimation.setDuration(100);

      ScaleAnimation scaleAnimation =
          new ScaleAnimation(
              1,
              0.5f, // 横向缩小
              1,
              0.5f, // 纵向缩小
              Animation.RELATIVE_TO_SELF,
              0.5f,
              Animation.RELATIVE_TO_SELF,
              0.5f);
      scaleAnimation.setDuration(100);

      AnimationSet animationSet = new AnimationSet(true);
      animationSet.addAnimation(alphaAnimation);
      animationSet.addAnimation(scaleAnimation);

      animationSet.setAnimationListener(
          new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
              runCsServiceSh.setVisibility(MaterialCardView.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
          });

      runCsServiceSh.startAnimation(animationSet);
    } else {
      runCsServiceSh.setVisibility(MaterialCardView.VISIBLE);
    }
  }

  private void checkRootStatus() {
    if (tools.getSU()) {
      rootWarningCard.setVisibility(MaterialCardView.GONE);
    } else {
      rootWarningCard.setVisibility(MaterialCardView.VISIBLE);
    }
  }

  private void checkStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Android 11 +
      if (!Environment.isExternalStorageManager()) {
        Logger.showToast(MainActivity.this, "需要开启管理所有文件权限");
        requestManageAllFilesPermission();
      }
    } else {
      // Android 10 -
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
            REQUEST_STORAGE_PERMISSION);
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  private void requestManageAllFilesPermission() {
    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
    intent.setData(Uri.parse("package:" + getPackageName()));
    startActivity(intent);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_STORAGE_PERMISSION) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Logger.showToast(MainActivity.this, "存储权限已授予");
      } else {
        Logger.showToast(MainActivity.this, "存储权限被拒绝");
      }
    }
  }

  private void excludeFromRecents(boolean exclude) {
    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    if (am != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      List<ActivityManager.AppTask> tasks = am.getAppTasks();
      if (tasks != null && !tasks.isEmpty()) {
        tasks.get(0).setExcludeFromRecents(exclude);
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    excludeFromRecents(false);
  }

  private void killMainProcess() {
    android.os.Process.killProcess(android.os.Process.myPid());
  }

  private void logError(String message) {
    Logger.writeLog("ERROR", TAG, message);
  }
}

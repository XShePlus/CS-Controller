package io.github.xsheeee.cs_controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.xsheeee.cs_controller.Tools.Values;
import io.github.xsheeee.cs_controller.Tools.Logger;

public class SettingsActivity extends AppCompatActivity {
  private static final String CONFIG_FILE_PATH = Values.csSettingsPath;
  private final List<String> switchKeys = new ArrayList<>();
  private Map<String, Boolean> configMap = new HashMap<>();
  private final Map<String, String> keyTranslations = new HashMap<>();
  private final Map<String, String> keyDisplayMap = new HashMap<>();
  private SwitchAdapter adapter;

  private final ActivityResultLauncher<Intent> filePickerLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
              Uri uri = result.getData().getData();
              if (uri != null) {
                importTranslationFile(uri);
              }
            }
          });

  private void initSwitchKeys() {
    switchKeys.clear();
    switchKeys.addAll(configMap.keySet());
  }

  private void setupPermissionSwitches() {
    // 检查无障碍权限
    boolean isAccessibilityEnabled = isAccessibilityServiceEnabled();
    MaterialSwitch switchAccessibility = findViewById(R.id.switchAccessibility);
    switchAccessibility.setChecked(isAccessibilityEnabled);
    switchAccessibility.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked) {
            enableAccessibilityService();
          } else {
            disableAccessibilityService();
          }
        });

    // 读取配置文件控制悬浮窗开关
    String appConfigPath = Values.appConfig;
    boolean isFloatingWindowEnabled = readFloatingWindowConfig(appConfigPath);
    MaterialSwitch switchFloatingWindow = findViewById(R.id.switchFloatingWindow);
    switchFloatingWindow.setChecked(isFloatingWindowEnabled);

    switchFloatingWindow.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          updateFloatingWindowConfig(appConfigPath, isChecked);
        });
  }

  private boolean readFloatingWindowConfig(String configPath) {
    try {
      String jsonContent = new String(Files.readAllBytes(Paths.get(configPath)));
      JSONObject config = new JSONObject(jsonContent);
      return config.optBoolean("floatingWindow", false);
    } catch (Exception e) {
      showToast("读取悬浮窗配置失败：" + e.getMessage());
      return false;
    }
  }

  private void updateFloatingWindowConfig(String configPath, boolean isEnabled) {
    try {
      String jsonContent = new String(Files.readAllBytes(Paths.get(configPath)));
      JSONObject config = new JSONObject(jsonContent);
      config.put("floatingWindow", isEnabled);
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath))) {
        writer.write(config.toString(2));
        writer.flush();
      }
    } catch (Exception e) {
      showToast("更新悬浮窗配置失败：" + e.getMessage());
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    Toolbar toolbar = findViewById(R.id.backButton5);
    setSupportActionBar(toolbar);
    toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24);
    toolbar.setNavigationOnClickListener(v -> finish());

    loadConfig();
    initSwitchKeys();

    RecyclerView recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    adapter = new SwitchAdapter(this, switchKeys, configMap, CONFIG_FILE_PATH);
    recyclerView.setAdapter(adapter);

    loadTranslationsFromPreferences();
    reorderKeys();

    // 设置权限开关
    setupPermissionSwitches();
  }

  private boolean isAccessibilityServiceEnabled() {
    String enabledServices =
        Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return enabledServices != null
        && enabledServices.contains(
            "io.github.xsheeee.cs_controller/io.github.xsheeee.cs_controller.MyAccessibilityService");
  }

  private void enableAccessibilityService() {
    try {
      // 使用Root权限开启无障碍服务
      String command =
          "settings put secure enabled_accessibility_services io.github.xsheeee.cs_controller/io.github.xsheeee.cs_controller.MyAccessibilityService";
      executeRootCommand(command);
      command = "settings put secure accessibility_enabled 1";
      executeRootCommand(command);
    } catch (Exception e) {
      showToast("启用无障碍服务失败：" + e.getMessage());
    }
  }

  private void disableAccessibilityService() {
    try {
      // 使用Root权限关闭无障碍服务
      String command = "settings put secure enabled_accessibility_services ''";
      executeRootCommand(command);
      command = "settings put secure accessibility_enabled 0";
      executeRootCommand(command);
    } catch (Exception e) {
      showToast("禁用无障碍服务失败：" + e.getMessage());
    }
  }

  private void executeRootCommand(String command) {
    try {
      Process process = Runtime.getRuntime().exec("su");
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
      writer.write(command);
      writer.flush();
      writer.close();
      process.waitFor();
    } catch (Exception e) {
      showToast("ERROR" + e.getMessage());
    }
  }

  private void loadConfig() {
    configMap.clear();
    try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH))) {
      String line;
      boolean inFunctionSection = false;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.equals("[function]")) {
          inFunctionSection = true;
          continue;
        } else if (line.startsWith("[") && !line.equals("[function]")) {
          inFunctionSection = false;
          continue;
        }
        if (inFunctionSection && line.contains("=")) {
          String[] parts = line.split("=", 2);
          String key = parts[0].trim();
          boolean value = Boolean.parseBoolean(parts[1].trim().toLowerCase());
          configMap.put(key, value);
        }
      }
    } catch (Exception e) {
      Logger.showToast(
          SettingsActivity.this, getString(R.string.read_mode_error) + ": " + e.getMessage());
    }
  }

  private void saveConfig() {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE_PATH))) {
      writer.write("[function]\n");
      for (Map.Entry<String, Boolean> entry : configMap.entrySet()) {
        writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
      }
    } catch (Exception e) {
      Logger.showToast(
          SettingsActivity.this, getString(R.string.save_mode_error) + ": " + e.getMessage());
    }
  }

  private void openFilePicker() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/plain");
    filePickerLauncher.launch(intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.settings_topbar, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.import_translation) {
      openFilePicker();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void importTranslationFile(Uri uri) {
    try {
      InputStream inputStream = getContentResolver().openInputStream(uri);
      if (inputStream != null) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.contains("=")) {
            String[] parts = line.split("=", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();
            keyTranslations.put(key, value);
          }
        }
        inputStream.close();
        saveTranslationsToPreferences();
        applyTranslations();
        showToast("翻译文件导入成功！");
      }
    } catch (Exception e) {
      showToast("翻译文件导入失败：" + e.getMessage());
    }
  }

  private void applyTranslations() {
    for (String key : switchKeys) {
      if (keyTranslations.containsKey(key)) {
        keyDisplayMap.put(key, keyTranslations.get(key));
      } else {
        keyDisplayMap.put(key, key);
      }
    }
    if (adapter != null) {
      adapter.setKeyDisplayMap(keyDisplayMap);
      adapter.notifyDataSetChanged();
    }
  }

  private void loadTranslationsFromPreferences() {
    SharedPreferences sharedPreferences = getSharedPreferences("Translations", MODE_PRIVATE);
    for (String key : switchKeys) {
      String translation = sharedPreferences.getString(key, null);
      if (translation != null) {
        keyTranslations.put(key, translation);
      }
    }
    applyTranslations();
  }

  private void saveTranslationsToPreferences() {
    SharedPreferences sharedPreferences = getSharedPreferences("Translations", MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    for (Map.Entry<String, String> entry : keyTranslations.entrySet()) {
      editor.putString(entry.getKey(), entry.getValue());
    }
    editor.apply();
  }

  private void reorderKeys() {
    List<String> validKeys = new ArrayList<>();
    List<String> invalidKeys = new ArrayList<>();

    for (String key : switchKeys) {
      if (configMap.containsKey(key)) {
        validKeys.add(key);
      } else {
        invalidKeys.add(key);
      }
    }

    switchKeys.clear();
    switchKeys.addAll(validKeys);
    switchKeys.addAll(invalidKeys);
  }
    
  private void showToast(String message) {
    Logger.showToast(SettingsActivity.this, message);
  }
}

package io.github.xsheeee.cs_controller.Tools;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final String TAG = "ConfigManager";
    private static final String DEFAULT_MODE = "fast";
    private static final boolean DEFAULT_FLOATING_WINDOW = true;

    public static class ConfigData {
        public Map<String, String> appModeMap;
        public String defaultMode;
        public boolean floatingWindowEnabled;

        public ConfigData() {
            this.appModeMap = new HashMap<>();
            this.defaultMode = DEFAULT_MODE;
            this.floatingWindowEnabled = DEFAULT_FLOATING_WINDOW;
        }
    }

    public static ConfigData loadConfig(String configPath) {
        ConfigData configData = new ConfigData();

        try {
            Path path = Paths.get(configPath);
            if (!Files.exists(path)) {
                Log.w(TAG, "Config file doesn't exist, using defaults");
                return configData;
            }

            String jsonContent = new String(Files.readAllBytes(path));
            JSONObject config = new JSONObject(jsonContent);

            configData.defaultMode = config.optString("default", DEFAULT_MODE);
            configData.floatingWindowEnabled = config.optBoolean("floatingWindow", DEFAULT_FLOATING_WINDOW);

            String[] modes = {"powersave", "balance", "performance", "fast"};
            for (String mode : modes) {
                JSONArray apps = config.optJSONArray(mode);
                if (apps != null) {
                    for (int i = 0; i < apps.length(); i++) {
                        String packageName = apps.getString(i);
                        configData.appModeMap.put(packageName, mode);
                    }
                }
            }

            Log.d(TAG, "Config loaded successfully");
            return configData;

        } catch (Exception e) {
            Log.e(TAG, "Error loading config: " + e.getMessage());
            return configData;
        }
    }

    public static boolean saveConfig(String configPath, ConfigData configData) {
        try {
            JSONObject config = new JSONObject();
            config.put("default", configData.defaultMode);
            config.put("floatingWindow", configData.floatingWindowEnabled);

            // 按模式分组应用
            Map<String, JSONArray> modeAppsMap = new HashMap<>();
            for (Map.Entry<String, String> entry : configData.appModeMap.entrySet()) {
                String packageName = entry.getKey();
                String mode = entry.getValue();

                if (!modeAppsMap.containsKey(mode)) {
                    modeAppsMap.put(mode, new JSONArray());
                }
                modeAppsMap.get(mode).put(packageName);
            }

            // 添加应用列表到配置
            for (String mode : new String[]{"powersave", "balance", "performance", "fast"}) {
                JSONArray apps = modeAppsMap.get(mode);
                if (apps != null && apps.length() > 0) {
                    config.put(mode, apps);
                }
            }

            // 写入文件
            Path path = Paths.get(configPath);
            Files.createDirectories(path.getParent());
            Files.write(path, config.toString(2).getBytes());

            Log.d(TAG, "Config saved successfully");
            return true;

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving config: " + e.getMessage());
            return false;
        }
    }

    public static void updateAppMode(String configPath, String packageName, String newMode) {
        ConfigData configData = loadConfig(configPath);

        // 从所有模式中移除该应用
        configData.appModeMap.remove(packageName);

        // 如果新模式不是默认模式，则添加到新模式
        if (!"".equals(newMode) && !configData.defaultMode.equals(newMode)) {
            configData.appModeMap.put(packageName, newMode);
        }

        saveConfig(configPath, configData);
    }
}
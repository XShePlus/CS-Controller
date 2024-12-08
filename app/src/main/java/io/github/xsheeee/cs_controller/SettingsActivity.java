package io.github.xsheeee.cs_controller;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.xsheeee.cs_controller.Tools.Tools;
import io.github.xsheeee.cs_controller.Tools.Values;

public class SettingsActivity extends AppCompatActivity {
    private Tools tools;
    private static final String CONFIG_FILE_PATH = Values.csSettings;
    private List<String> switchKeys = new ArrayList<>();
    private Map<String, Boolean> configMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.backButton5);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        tools = new Tools(this);

        initSwitchKeys();
        addMissingKeysFromConfig(); // 动态添加配置文件中缺失的键值
        loadConfig();

        reorderKeys();

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        SwitchAdapter adapter = new SwitchAdapter(this, switchKeys, configMap, CONFIG_FILE_PATH);
        recyclerView.setAdapter(adapter);

        removeLeadingSpacesInFile();
    }

    private void initSwitchKeys() {
        // 添加默认的配置键
        switchKeys.add("Enable_Feas");
        switchKeys.add("Disable_qcom_GpuBoost");
        switchKeys.add("Core_allocation");
        switchKeys.add("Load_balancing");
        switchKeys.add("Disable_UFS_clock_gate");
        switchKeys.add("TouchBoost");
        switchKeys.add("CFS_Scheduler");
        switchKeys.add("Dynamic_Response");
        switchKeys.add("Adj_CpuIdle");
        switchKeys.add("New_Uclamp_Strategy");
        switchKeys.add("Disable_Detailed_Log");
    }

    private void addMissingKeysFromConfig() {
        // 读取配置文件内容
        String configContent = tools.readFileWithShell(CONFIG_FILE_PATH);
        if (configContent != null) {
            String[] lines = configContent.split("\n");
            for (String line : lines) {
                line = line.trim();
                // 忽略以 "name =" 或 "author =" 开头的行
                if (line.startsWith("name =") || line.startsWith("author =")) {
                    continue;
                }
                if (line.contains("=")) {
                    String key = line.split("=")[0].trim();
                    if (!switchKeys.contains(key)) {
                        switchKeys.add(key);
                    }
                }
            }
        } else {
            showToast(getString(R.string.read_mode_error));
        }
    }

    private void loadConfig() {
        String configContent = tools.readFileWithShell(CONFIG_FILE_PATH);
        if (configContent != null) {
            configMap = parseConfigContent(configContent);
        } else {
            showToast(getString(R.string.read_mode_error));
        }
    }

    private Map<String, Boolean> parseConfigContent(String content) {
        Map<String, Boolean> configMap = new HashMap<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("name =") || line.startsWith("author =")) {
                continue;
            }
            if (line.contains("=")) {
                String[] parts = line.split("=");
                String key = parts[0].trim();
                boolean value = parts[1].trim().equalsIgnoreCase("true");
                configMap.put(key, value);
            }
        }
        return configMap;
    }

    private void reorderKeys() {
        List<String> validKeys = new ArrayList<>();
        List<String> invalidKeys = new ArrayList<>();

        for (String key : switchKeys) {
            if (configMap.containsKey(key)) {
                validKeys.add(key); // 有效
            } else {
                invalidKeys.add(key); // 无效
            }
        }

        switchKeys.clear();
        switchKeys.addAll(validKeys);
        switchKeys.addAll(invalidKeys); // 无效选项追加到末尾
    }

    private void removeLeadingSpacesInFile() {
        String configContent = tools.readFileWithShell(CONFIG_FILE_PATH);
        if (configContent != null) {
            StringBuilder cleanedContent = new StringBuilder();
            String[] lines = configContent.split("\n");

            for (String line : lines) {
                String cleanedLine = line.replaceAll("^\\s+", "");
                cleanedContent.append(cleanedLine).append("\n");
            }

            tools.writeToFile(CONFIG_FILE_PATH, cleanedContent.toString());
        } else {
            showToast(getString(R.string.read_mode_error));
        }
    }

    private void showToast(String message) {
        Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
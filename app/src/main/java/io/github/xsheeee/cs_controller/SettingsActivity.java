package io.github.xsheeee.cs_controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.xsheeee.cs_controller.Tools.Tools;
import io.github.xsheeee.cs_controller.Tools.Values;

public class SettingsActivity extends AppCompatActivity {
    private Tools tools;
    private static final String CONFIG_FILE_PATH = Values.csSettings;

    private final List<String> switchKeys = new ArrayList<>();
    private Map<String, Boolean> configMap = new HashMap<>();
    private final Map<String, String> keyTranslations = new HashMap<>();
    private final Map<String, String> keyDisplayMap = new HashMap<>();

    private SwitchAdapter adapter;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    importTranslationFile(uri);
                }
            }
        }
    );

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
        addMissingKeysFromConfig();
        loadConfig();

        // 初始化 adapter
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SwitchAdapter(this, switchKeys, configMap, CONFIG_FILE_PATH);
        recyclerView.setAdapter(adapter);

        loadTranslationsFromPreferences();
        reorderKeys();
    }

    // 加载菜单
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.settings_topbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.import_translation) {
            openFilePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initSwitchKeys() {
        switchKeys.clear();
    }

    private void addMissingKeysFromConfig() {
        String configContent = tools.readFileWithShell(CONFIG_FILE_PATH);
        if (configContent != null) {
            String[] lines = configContent.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("=")) {
                    String key = line.split("=")[0].trim();
                    // 忽略包含 "name" 和 "author" 的行
                    if (!key.toLowerCase().contains("name") && !key.toLowerCase().contains("author") && !switchKeys.contains(key)) {
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
            if (line.contains("=")) {
                String[] parts = line.split("=");
                String key = parts[0].trim();
                boolean value = parts[1].trim().equalsIgnoreCase("true");
                configMap.put(key, value);
            }
        }
        return configMap;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        filePickerLauncher.launch(intent);
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
                keyDisplayMap.put(key, keyTranslations.get(key)); // 原始键 -> 翻译名称
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
        Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
package io.github.xsheeee.cs_controller;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import io.github.xsheeee.cs_controller.Tools.Values;
import io.github.xsheeee.cs_controller.Tools.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AppConfigActivity extends AppCompatActivity {
    private TextView appNameTextView, packageNameTextView, versionTextView, versionCodeTextView;
    private ImageView appIconImageView;
    private MaterialButton dropdownButton;
    private String currentPackageName;
    private static final String[] MODES = {"跟随全局", "powersave", "balance", "performance", "fast"};
    private JSONObject configJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_config);

        initializeViews();
        setupToolbar();

        currentPackageName = getIntent().getStringExtra("pName");
        if (currentPackageName != null) {
            fetchAppInfo(currentPackageName);
            loadConfigurations();
            setupModeSelection();
        }
    }

    private void initializeViews() {
        appNameTextView = findViewById(R.id.appNameTextView);
        packageNameTextView = findViewById(R.id.packageNameTextView);
        versionTextView = findViewById(R.id.versionTextView);
        versionCodeTextView = findViewById(R.id.versionCodeTextView);
        appIconImageView = findViewById(R.id.iconImageView);
        dropdownButton = findViewById(R.id.dropdownButton);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.backButton2);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadConfigurations() {
        File configFile = new File(Values.appConfig);
        if (!configFile.exists()) {
            createDefaultConfigFile();
            return;
        }

        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            configJson = new JSONObject(content.toString());
        } catch (IOException | JSONException e) {
            
            createDefaultConfigFile();
        }
    }

    private void saveConfigurations() {
        try (FileWriter writer = new FileWriter(Values.appConfig)) {
            writer.write(configJson.toString(2));
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentMode() {
        if (configJson == null) return "跟随全局";
        
        try {
            for (String mode : MODES) {
                if (mode.equals("跟随全局")) continue;
                JSONArray modeArray = configJson.optJSONArray(mode);
                if (modeArray != null) {
                    for (int i = 0; i < modeArray.length(); i++) {
                        if (currentPackageName.equals(modeArray.getString(i))) {
                            return mode;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "跟随全局";
    }

    private void setupModeSelection() {
        String currentMode = getCurrentMode();
        dropdownButton.setText(currentMode);
        dropdownButton.setOnClickListener(this::showModeMenu);
    }

    private void showModeMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        for (int i = 0; i < MODES.length; i++) {
            popup.getMenu().add(Menu.NONE, i, i, MODES[i]);
        }

        popup.setOnMenuItemClickListener(item -> {
            String selectedMode = MODES[item.getItemId()];
            updateAppMode(selectedMode);
            dropdownButton.setText(selectedMode);
            return true;
        });

        popup.show();
    }

    private void updateAppMode(String newMode) {
        try {
            // Remove package from all modes first
            for (String mode : MODES) {
                if (mode.equals("跟随全局")) continue;
                JSONArray modeArray = configJson.optJSONArray(mode);
                if (modeArray != null) {
                    JSONArray newArray = new JSONArray();
                    for (int i = 0; i < modeArray.length(); i++) {
                        String pkg = modeArray.getString(i);
                        if (!pkg.equals(currentPackageName)) {
                            newArray.put(pkg);
                        }
                    }
                    configJson.put(mode, newArray);
                }
            }

            // Add to new mode if not "跟随全局"
            if (!newMode.equals("跟随全局")) {
                JSONArray modeArray = configJson.optJSONArray(newMode);
                if (modeArray == null) {
                    modeArray = new JSONArray();
                    configJson.put(newMode, modeArray);
                }
                modeArray.put(currentPackageName);
            }

            saveConfigurations();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetTextI18n")
    private void fetchAppInfo(String packageName) {
        PackageManager pm = getPackageManager();

        try {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);

            Drawable icon = pm.getApplicationIcon(applicationInfo);
            String appName = pm.getApplicationLabel(applicationInfo).toString();
            String versionName = packageInfo.versionName;
            int versionCode = (int) packageInfo.getLongVersionCode();

            updateAppInfoDisplay(icon, appName, packageName, versionName, versionCode);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(appName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updateAppInfoDisplay(Drawable icon, String appName, String packageName,
                                    String versionName, int versionCode) {
        appNameTextView.setText(appName);
        packageNameTextView.setText(packageName);
        versionTextView.setText(getString(R.string.cs_version) + versionName);
        versionCodeTextView.setText(getString(R.string.version_code) + versionCode);
        appIconImageView.setImageDrawable(icon);
    }

    private void createDefaultConfigFile() {
        try {
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("default", true);
            defaultConfig.put("floatingWindow", false);
            
            for (String mode : MODES) {
                if (!mode.equals("跟随全局")) {
                    defaultConfig.put(mode, new JSONArray());
                }
            }

            configJson = defaultConfig;
            saveConfigurations();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
package io.github.xsheeee.cs_controller.Tools;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private Drawable icon;
    private String appName;
    private String packageName;
    private String performanceMode;
    private boolean isPriority;

    public AppInfo(Drawable icon, String appName, String packageName) {
        this.icon = icon;
        this.appName = appName;
        this.packageName = packageName;
        this.performanceMode = "";
        this.isPriority = false;
    }

    public AppInfo() {
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPerformanceMode() {
        return performanceMode;
    }

    public void setPerformanceMode(String performanceMode) {
        this.performanceMode = performanceMode;
    }

    public boolean isPriority() {
        return isPriority;
    }

    public void setPriority(boolean priority) {
        isPriority = priority;
    }

    @Override
    public String toString() {
        return "AppInfo [icon=" + icon + ", appName=" + appName + ", packageName=" + packageName
                + ", performanceMode=" + performanceMode + ", isPriority=" + isPriority + "]";
    }
}
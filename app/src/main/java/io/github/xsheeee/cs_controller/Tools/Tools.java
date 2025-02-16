package io.github.xsheeee.cs_controller.Tools;

import android.content.Context;
import com.topjohnwu.superuser.Shell;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tools {
  private final Context context;
  private static final String LOG_PATH = Values.csLog;
  private static final Map<Integer, String> MODE_MAP = new HashMap<>();
  private static final String TAG = "Tools";
  private static final String CS_CONFIG_PATH = Values.CSConfigPath;

  static {
    MODE_MAP.put(1, Values.powersaveName);
    MODE_MAP.put(2, Values.balanceName);
    MODE_MAP.put(3, Values.performanceName);
    MODE_MAP.put(4, Values.fastName);
  }

  public Tools(Context context) {
    this.context = context;
  }

  public String getModeName(int mode) {
    return MODE_MAP.get(mode);
  }

  public void init() {
    List<String> paths = Arrays.asList(CS_CONFIG_PATH, Values.CSCPath);
    ensureDirectoriesAndFilesExist(paths);

    if (!checkDirectoryAndFileWithLibSu()) {
      createPathWithLibSu(LOG_PATH, true);
    }
  }

  private void ensureDirectoriesAndFilesExist(List<String> paths) {
    for (String path : paths) {
      if (!executeShellCommand("test -e " + path)) {
        handlePathError(path);
      }
    }
  }

  private void handlePathError(String path) {
    if (CS_CONFIG_PATH.equals(path)) {
      showToast("未安装 CS 调度");
    } else {
      createPathWithLibSu(path, Values.CSCPath.equals(path));
    }
  }

  private void createPathWithLibSu(String path, boolean isDirectory) {
    String command = isDirectory ? "su -c mkdir -p " : "su -c touch ";
    if (!executeShellCommand(command + path)) {
      showToast("创建路径失败：" + path);
    }
  }

  private boolean checkDirectoryAndFileWithLibSu() {
    return executeShellCommand("ls " + LOG_PATH);
  }

  public boolean getSU() {
    return executeShellCommand("su -c id");
  }

  public void changeMode(String modeName) {
    if (modeName == null || modeName.isEmpty()) {
      showToast("模式名称不能为空");
      return;
    }

    Integer mode = getModeByName(modeName);
    if (mode != null) {
      writeToFile(CS_CONFIG_PATH, modeName);
    } else {
      showToast("无效的模式名称：" + modeName);
    }
  }

  private Integer getModeByName(String modeName) {
    for (Map.Entry<Integer, String> entry : MODE_MAP.entrySet()) {
      if (entry.getValue().equals(modeName)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public String readFileWithShell(String filePath) {
    Shell.Result result = Shell.cmd("cat " + filePath).exec();
    if (result.isSuccess() && !result.getOut().isEmpty()) {
      return String.join("\n", result.getOut());
    } else {
      logError("读取文件失败：" + filePath, result);
      return null;
    }
  }

  public void updateConfigEntry(String filePath, String key, String newValue) {
    String content = readFileWithShell(filePath);
    if (content == null) {
      showToast("无法读取配置文件：" + filePath);
      return;
    }

    String updatedContent = content.replaceAll("(?m)^" + key + " =.*$", key + " = " + newValue);
    if (!updatedContent.contains(key + " =")) {
      updatedContent += "\n" + key + " = " + newValue;
    }

    writeToFile(filePath, updatedContent.trim());
  }

  public void writeToFile(String filePath, String content) {
    if (!executeShellCommand("echo \"" + content.replace("\"", "\\\"") + "\" > " + filePath)) {
      showToast("写入失败：" + filePath);
    }
  }

  public String getVersionFromModuleProp() {
    String filePath = Values.csmodulePath;
    Shell.Result result = Shell.cmd("grep version= " + filePath).exec();

    if (result.isSuccess() && !result.getOut().isEmpty()) {
      return result.getOut().get(0).replace("version=", "").trim();
    } else {
      logError("读取 module.prop 失败：" + filePath, result);
      return null;
    }
  }

  public boolean isProcessRunning(String processPath) {
    Shell.Result result = Shell.cmd("pgrep -f " + processPath).exec();
    return result.isSuccess() && !result.getOut().isEmpty();
  }

  public String readLogFile() {
    return readFileWithShell(LOG_PATH);
  }

  public boolean executeShellCommand(String command) {
    Shell.Result result = Shell.cmd(command).exec();
    if (!result.isSuccess()) {
      logError("Shell 命令执行失败：" + command, result);
    }
    return result.isSuccess();
  }

  private void showToast(String message) {
    Logger.showToast(context, message);
  }

  private void logError(String message, Shell.Result result) {
    Logger.writeLog("ERROR",TAG, message + " | Error: " + String.join("\n", result.getErr()));
  }
}
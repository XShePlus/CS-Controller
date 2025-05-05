#include <jni.h>
#include <string>
#include <fstream>
#include <android/log.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include <cerrno>
#include <cstring>
#include <dirent.h>

#define LOG_TAG "FileUtilsNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xsheeee_cs_1controller_tools_FileUtils_writeStringToFileNative(
        JNIEnv *env,
        jobject /* this */,
        jstring filePath,
        jstring content) {

    const char *nativeFilePath = env->GetStringUTFChars(filePath, nullptr);
    const char *nativeContent = env->GetStringUTFChars(content, nullptr);
    
    bool success = false;

    int fd = open(nativeFilePath, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd >= 0) {
        size_t contentLength = strlen(nativeContent);
        ssize_t bytesWritten = write(fd, nativeContent, contentLength);
        success = (bytesWritten == static_cast<ssize_t>(contentLength));
        
        if (!success) {
            LOGE("写入文件失败: %s", strerror(errno));
        } else {
            LOGI("成功写入文件: %s", nativeFilePath);
        }
        
        close(fd);
    } else {
        LOGE("打开文件失败: %s, 错误: %s", nativeFilePath, strerror(errno));
    }

    env->ReleaseStringUTFChars(filePath, nativeFilePath);
    env->ReleaseStringUTFChars(content, nativeContent);

    return static_cast<jboolean>(success);
}
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_xsheeee_cs_1controller_tools_FileUtils_readFromFileNative(
        JNIEnv *env,
        jobject /* this */,
        jstring filePath) {

    const char *nativeFilePath = env->GetStringUTFChars(filePath, nullptr);
    
    std::string result = "";

    int fd = open(nativeFilePath, O_RDONLY);
    if (fd >= 0) {
        char buffer[4096];
        ssize_t bytesRead;
        
        while ((bytesRead = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
            buffer[bytesRead] = '\0';
            result.append(buffer);
        }
        
        close(fd);
        LOGI("成功读取文件: %s, 长度: %zu", nativeFilePath, result.length());
    } else {
        LOGE("打开文件失败: %s, 错误: %s", nativeFilePath, strerror(errno));
    }

    env->ReleaseStringUTFChars(filePath, nativeFilePath);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xsheeee_cs_1controller_tools_FileUtils_fileExistsNative(
        JNIEnv *env,
        jobject /* this */,
        jstring filePath) {

    const char *nativeFilePath = env->GetStringUTFChars(filePath, nullptr);

    struct stat fileStat;
    bool exists = (stat(nativeFilePath, &fileStat) == 0 && S_ISREG(fileStat.st_mode));
    
    LOGI("检查文件是否存在: %s, 结果: %d", nativeFilePath, exists);

    env->ReleaseStringUTFChars(filePath, nativeFilePath);

    return static_cast<jboolean>(exists);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xsheeee_cs_1controller_tools_FileUtils_mkdirsNative(
        JNIEnv *env,
        jobject /* this */,
        jstring dirPath) {

    const char *nativeDirPath = env->GetStringUTFChars(dirPath, nullptr);
    
    bool success = false;

    struct stat dirStat;
    if (stat(nativeDirPath, &dirStat) == 0 && S_ISDIR(dirStat.st_mode)) {
        LOGI("目录已存在: %s", nativeDirPath);
        env->ReleaseStringUTFChars(dirPath, nativeDirPath);
        return JNI_TRUE;
    }

    std::string path = nativeDirPath;
    size_t pos = 0;
    std::string dir;

    while ((pos = path.find('/', pos)) != std::string::npos) {
        dir = path.substr(0, pos++);
        if (dir.empty()) continue; // 跳过根目录
        
        if (mkdir(dir.c_str(), 0777) != 0 && errno != EEXIST) {
            LOGE("创建目录失败: %s, 错误: %s", dir.c_str(), strerror(errno));
        }
    }

    if (mkdir(path.c_str(), 0777) == 0 || errno == EEXIST) {
        success = true;
        LOGI("成功创建目录: %s", nativeDirPath);
    } else {
        LOGE("创建最终目录失败: %s, 错误: %s", path.c_str(), strerror(errno));
    }

    bool exists = (stat(nativeDirPath, &dirStat) == 0 && S_ISDIR(dirStat.st_mode));

    env->ReleaseStringUTFChars(dirPath, nativeDirPath);

    return static_cast<jboolean>(exists || success);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xsheeee_cs_1controller_tools_FileUtils_appendToFileNative(
        JNIEnv *env,
        jobject /* this */,
        jstring filePath,
        jstring content) {

    const char *nativeFilePath = env->GetStringUTFChars(filePath, nullptr);
    const char *nativeContent = env->GetStringUTFChars(content, nullptr);
    
    bool success = false;
    int fd = open(nativeFilePath, O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd >= 0) {
        size_t contentLength = strlen(nativeContent);
        ssize_t bytesWritten = write(fd, nativeContent, contentLength);
        success = (bytesWritten == static_cast<ssize_t>(contentLength));
        
        if (!success) {
            LOGE("追加文件失败: %s", strerror(errno));
        } else {
            LOGI("成功追加文件: %s", nativeFilePath);
        }
        
        close(fd);
    } else {
        LOGE("打开文件失败: %s, 错误: %s", nativeFilePath, strerror(errno));
    }

    env->ReleaseStringUTFChars(filePath, nativeFilePath);
    env->ReleaseStringUTFChars(content, nativeContent);

    return static_cast<jboolean>(success);
}

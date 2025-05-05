package io.github.xsheeee.cs_controller.tools

import android.util.Log
import java.io.File

/**
 * 文件工具类，提供文件操作的本地方法，全部使用JNI实现
 */
class FileUtils {
    companion object {
        private const val TAG = "FileUtils"

        // 加载本地库
        init {
            try {
                System.loadLibrary("cs-controller-native")
                Log.d(TAG, "本地库加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "加载本地库失败: ${e.message}")
            }
        }

        fun writeToFile(filePath: String, content: String): Boolean {
            try {
                // 确保文件父目录存在
                val file = File(filePath)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    val created = mkdirsNative(parent.absolutePath)
                    Log.d(TAG, "使用JNI创建父目录: $created")
                }

                // 调用本地方法写入文件
                val result = writeStringToFileNative(filePath, content)
                Log.d(TAG, "使用JNI方法写入文件 $filePath: $result")
                return result
            } catch (e: Exception) {
                Log.e(TAG, "写入文件失败: ${e.message}")
                return false
            }
        }

        fun readFromFile(filePath: String): String {
            try {
                if (fileExistsNative(filePath)) {
                    return readFromFileNative(filePath).trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取文件失败: ${e.message}")
            }
            return ""
        }

        @JvmStatic
        private external fun writeStringToFileNative(filePath: String, content: String): Boolean

        @JvmStatic
        private external fun readFromFileNative(filePath: String): String

        @JvmStatic
        private external fun fileExistsNative(filePath: String): Boolean

        @JvmStatic
        private external fun mkdirsNative(dirPath: String): Boolean

        @JvmStatic
        private external fun appendToFileNative(filePath: String, content: String): Boolean
    }
}
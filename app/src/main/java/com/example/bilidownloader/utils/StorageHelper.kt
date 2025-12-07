package com.example.bilidownloader.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri // 【新增导入】
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream // 【新增导入】

/**
 * 仓库管理员助手
 * 负责把临时文件搬运到手机相册或音乐库
 */
object StorageHelper {

    /**
     * 保存视频到系统相册
     */
    suspend fun saveVideoToGallery(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BiliDownloader")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                FileInputStream(sourceFile).use { inputStream ->
                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * 【新功能】保存音频到系统音乐文件夹
     */
    suspend fun saveAudioToMusic(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg") // 告诉系统这是 MP3
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/BiliDownloader")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                FileInputStream(sourceFile).use { inputStream ->
                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // --- 新增复印机方法 ---

    /**
     * 【新增】把 Uri 指向的文件复制到 APP 的缓存目录
     * 解决权限问题和 FFmpeg 路径问题
     * @param context 应用上下文
     * @param uri 待复制文件的 Content Uri
     * @param fileName 在缓存目录中创建的文件名
     * @return 复制成功后的本地 File 对象，或 null (失败)
     */
    fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheDir = context.cacheDir
            // 创建一个临时文件
            val tempFile = File(cacheDir, fileName)

            // 开始复制：输入流 -> 输出流
            // context.contentResolver.openInputStream(uri) 从 Content Uri 获取输入流
            context.contentResolver.openInputStream(uri)?.use { input ->
                // FileOutputStream(tempFile) 创建文件输出流到缓存文件
                java.io.FileOutputStream(tempFile).use { output ->
                    input.copyTo(output) // Kotlin 标准库的 IO 扩展函数，高效复制
                }
            }
            tempFile // 返回复印好的文件
        } catch (e: Exception) {
            e.printStackTrace()
            // 打印失败信息（如权限不足、文件不存在等）
            println("文件复制到缓存失败: ${e.message}")
            null
        }
    }
}
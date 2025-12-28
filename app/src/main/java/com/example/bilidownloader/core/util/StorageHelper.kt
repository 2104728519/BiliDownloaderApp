package com.example.bilidownloader.core.util

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 媒体存储助手.
 *
 * 负责处理 Android 分区存储 (Scoped Storage) 逻辑，将文件保存到系统相册或音乐库。
 * 兼容 Android 10 (Q) 及以上版本的 MediaStore API 变更。
 */
object StorageHelper {

    sealed class StorageResult {
        object Success : StorageResult()
        object Error : StorageResult()
        // 需要用户授权（Android 10+ 删除文件时常见）
        data class RequiresPermission(val intentSender: IntentSender) : StorageResult()
    }

    /**
     * 保存视频到系统相册.
     * 自动识别 MIME 类型 (mp4, mkv, webm 等).
     */
    suspend fun saveVideoToGallery(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 自动获取 MIME 类型
                val extension = fileName.substringAfterLast('.', "")
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "video/mp4"

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    // Android Q+: 使用 RELATIVE_PATH 并设置 IS_PENDING 状态以独占写入
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BiliDownloader")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                // 选择合适的 Collection Uri
                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                // 写入数据
                FileInputStream(sourceFile).use { inputStream ->
                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 写入完成，发布文件 (Android Q+)
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
     * 保存 GIF/图片到系统相册.
     */
    suspend fun saveGifToGallery(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BiliDownloader")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                FileInputStream(sourceFile).use { inputStream ->
                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
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
     * 保存音频到系统音乐库.
     * 自动根据文件后缀识别 MIME 类型 (mp3, flac, m4a).
     */
    suspend fun saveAudioToMusic(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val mimeType = when {
                    fileName.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                    fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                    fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                    else -> "audio/mpeg"
                }

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
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

    /**
     * 将 Content Uri 复制到应用私有缓存目录.
     * 用于处理用户从外部选择的文件（因为直接读取外部 Uri 可能受限）.
     */
    fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            println("文件复制到缓存失败: ${e.message}")
            null
        }
    }

    /**
     * 检查 Uri 指向的文件是否仍然存在于 MediaStore 中.
     */
    suspend fun isFileExisting(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null,
                    null,
                    null
                )
                val exists = cursor?.use { it.moveToFirst() } ?: false
                exists
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 删除音频文件.
     * 处理了 Android 10+ 的 RecoverableSecurityException，可能需要向用户申请权限.
     */
    suspend fun deleteAudioFile(context: Context, uri: Uri): StorageResult {
        return withContext(Dispatchers.IO) {
            try {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) return@withContext StorageResult.Success

                // 若数据库记录已不存在，也视为成功
                if (!isFileExisting(context, uri)) {
                    return@withContext StorageResult.Success
                }
                StorageResult.Error
            } catch (securityException: SecurityException) {
                // 捕获权限异常，准备 IntentSender 供 UI 调用
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException -> {
                        securityException.userAction.actionIntent.intentSender
                    }
                    else -> null
                }

                if (intentSender != null) {
                    StorageResult.RequiresPermission(intentSender)
                } else {
                    securityException.printStackTrace()
                    StorageResult.Error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                StorageResult.Error
            }
        }
    }

    /**
     * 重命名音频文件.
     * 同样涉及权限处理逻辑.
     */
    suspend fun renameAudioFile(context: Context, uri: Uri, newName: String): StorageResult {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                }
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) StorageResult.Success else StorageResult.Error
            } catch (securityException: SecurityException) {
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException -> {
                        securityException.userAction.actionIntent.intentSender
                    }
                    else -> null
                }
                if (intentSender != null) {
                    StorageResult.RequiresPermission(intentSender)
                } else {
                    securityException.printStackTrace()
                    StorageResult.Error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                StorageResult.Error
            }
        }
    }
}
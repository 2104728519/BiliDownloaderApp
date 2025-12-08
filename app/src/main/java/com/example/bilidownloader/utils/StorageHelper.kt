package com.example.bilidownloader.utils

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 仓库管理员助手
 * 负责把临时文件搬运到手机相册或音乐库
 */
object StorageHelper {

    sealed class StorageResult {
        object Success : StorageResult()
        object Error : StorageResult()
        data class RequiresPermission(val intentSender: IntentSender) : StorageResult()
    }

    // --- 以下部分保留不变 ---
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

    suspend fun saveAudioToMusic(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
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
    // --- 以上部分保留不变 ---

    /**
     * 【新增】检查文件是否存在
     * 用来在 Android 11+ 删除操作后确认结果
     */
    suspend fun isFileExisting(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试查询该文件的 ID
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
     * 【修改后】删除音频文件
     */
    suspend fun deleteAudioFile(context: Context, uri: Uri): StorageResult {
        return withContext(Dispatchers.IO) {
            try {
                val rows = context.contentResolver.delete(uri, null, null)
                // 如果删除了 >0 行，说明成功
                if (rows > 0) return@withContext StorageResult.Success

                // 【优化】如果 delete 返回 0，有可能是文件本来就不存在了（已经被系统删了）
                // 此时我们也应该视为成功，避免报错
                if (!isFileExisting(context, uri)) {
                    return@withContext StorageResult.Success
                }

                // 如果行数为0，且文件还在，那才算是真的失败
                StorageResult.Error
            } catch (securityException: SecurityException) {
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
     * 重命名音频文件
     * (Rename 逻辑在 Android 11+ 依然需要 App 自己执行 update，所以不需要改动)
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
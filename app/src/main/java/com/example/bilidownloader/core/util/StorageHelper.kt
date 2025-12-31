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
 * 媒体存储助手 (StorageHelper)
 *
 * 负责处理 Android 分区存储 (Scoped Storage) 逻辑，将文件保存到系统相册、音乐库或下载目录。
 * 兼容 Android 10 (Q) 及以上版本的 MediaStore API 变更。
 */
object StorageHelper {

    /**
     * 存储操作结果封装
     */
    sealed class StorageResult {
        object Success : StorageResult()
        object Error : StorageResult()
        // 需要用户授权（Android 10+ 操作非本应用创建的文件时常见）
        data class RequiresPermission(val intentSender: IntentSender) : StorageResult()
    }

    /**
     * 将纯文本保存到系统下载文件夹的 Transcription 子目录.
     * 路径：/Downloads/BiliDownloader/Transcription/
     * * @param textContent 要保存的文本内容
     * @param fileName 文件名（应包含 .txt 后缀）
     */
    suspend fun saveTextToDownloads(
        context: Context,
        textContent: String,
        fileName: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // 存放在专门的 Transcription 文件夹
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/BiliDownloader/Transcription"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                resolver.openOutputStream(itemUri)?.use { outputStream ->
                    outputStream.write(textContent.toByteArray(Charsets.UTF_8))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 将 JSON 字符串保存到系统下载文件夹的 Presets 子目录.
     * 路径：/Downloads/BiliDownloader/Presets/
     */
    suspend fun saveJsonToDownloads(context: Context, jsonString: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/BiliDownloader/Presets"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val itemUri = resolver.insert(collection, values) ?: return@withContext false

                resolver.openOutputStream(itemUri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 保存视频文件到系统相册 (Movies 目录).
     * 自动识别 MIME 类型，默认 video/mp4.
     */
    suspend fun saveVideoToGallery(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val extension = fileName.substringAfterLast('.', "")
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "video/mp4"

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MOVIES}/BiliDownloader"
                        )
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
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 保存 GIF 或图片到系统相册 (Pictures 目录).
     */
    suspend fun saveGifToGallery(context: Context, sourceFile: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/BiliDownloader"
                        )
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
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 保存音频文件到系统音乐库 (Music 目录).
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
                        put(
                            MediaStore.Audio.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MUSIC}/BiliDownloader"
                        )
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
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 将 Content Uri 指向的文件复制到应用私有缓存目录.
     * 常用于处理外部选中的文件。
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
            null
        }
    }

    /**
     * 检查 MediaStore Uri 指向的文件是否仍然存在.
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
     * 删除媒体文件.
     * 针对 Android 10+ 可能会抛出 RecoverableSecurityException，需返回相应 Result 处理权限请求。
     */
    suspend fun deleteAudioFile(context: Context, uri: Uri): StorageResult {
        return withContext(Dispatchers.IO) {
            try {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) return@withContext StorageResult.Success

                if (!isFileExisting(context, uri)) {
                    return@withContext StorageResult.Success
                }
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
     * 重命名媒体文件.
     * 同删除逻辑，可能涉及分区存储权限变更。
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
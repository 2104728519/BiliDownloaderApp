package com.example.bilidownloader.features.ffmpeg

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 本地媒体数据模型
 */
data class LocalMedia(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long,
    val isVideo: Boolean // true=视频, false=音频
)

class FfmpegViewModel(
    application: Application,
    private val repository: FfmpegRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FfmpegUiState())
    val uiState = _uiState.asStateFlow()

    private val _localMediaList = MutableStateFlow<List<LocalMedia>>(emptyList())
    val localMediaList = _localMediaList.asStateFlow()

    private val _isMediaLoading = MutableStateFlow(false)
    val isMediaLoading = _isMediaLoading.asStateFlow()

    private var cachedInputPath: String? = null

    init {
        val presetArgs = savedStateHandle.get<String>("preset_args")
        if (!presetArgs.isNullOrBlank()) {
            try {
                val decodedArgs = java.net.URLDecoder.decode(presetArgs, "UTF-8")
                _uiState.update { it.copy(arguments = decodedArgs) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // region --- 媒体库扫描逻辑 ---
    fun loadLocalMedia(isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isMediaLoading.value = true
            val context = getApplication<Application>()
            val list = mutableListOf<LocalMedia>()

            try {
                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DURATION,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED
                )

                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

                context.contentResolver.query(
                    collection, projection, null, null, sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val duration = cursor.getLong(durCol)
                        if (duration > 0) {
                            list.add(LocalMedia(
                                id = id,
                                uri = uri,
                                name = cursor.getString(nameCol) ?: "Unknown",
                                duration = duration,
                                size = cursor.getLong(sizeCol),
                                isVideo = isVideo
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _localMediaList.value = list
                _isMediaLoading.value = false
            }
        }
    }
    // endregion

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var name = "unknown_file"
            var sizeStr = "0 B"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) {
                        sizeStr = formatSize(cursor.getLong(sizeIndex))
                    }
                }
            }

            val mimeType = context.contentResolver.getType(uri)
            val defaultExt = if (mimeType?.startsWith("audio") == true) ".mp3" else ".mp4"

            val cacheFile = StorageHelper.copyUriToCache(context, uri, "ffmpeg_input_temp")

            if (cacheFile != null) {
                cachedInputPath = cacheFile.absolutePath
                val probeInfo = repository.getMediaInfo(cacheFile.absolutePath)

                _uiState.update {
                    it.copy(
                        inputFileUri = uri,
                        inputFileName = name,
                        inputFileSize = sizeStr,
                        outputExtension = defaultExt,
                        taskState = FfmpegTaskState.Idle,
                        mediaInfo = probeInfo
                    )
                }
            } else {
                _uiState.update {
                    it.copy(taskState = FfmpegTaskState.Error("文件读取失败", emptyList()))
                }
            }
        }
    }

    fun onArgumentsChanged(newArgs: String) {
        _uiState.update { it.copy(arguments = newArgs) }
    }

    fun onExtensionChanged(newExt: String) {
        val validExt = if (newExt.startsWith(".")) newExt else ".$newExt"
        _uiState.update { it.copy(outputExtension = validExt) }
    }

    fun executeCommand() {
        val currentState = _uiState.value
        val inputPath = cachedInputPath

        if (inputPath == null) {
            _uiState.update { it.copy(taskState = FfmpegTaskState.Error("请先选择文件", emptyList())) }
            return
        }

        viewModelScope.launch {
            repository.executeCommand(
                inputUri = "file://$inputPath",
                args = currentState.arguments,
                outputExtension = currentState.outputExtension
            ).collect { taskState ->
                _uiState.update { it.copy(taskState = taskState) }

                if (taskState is FfmpegTaskState.Success) {
                    saveToGallery(File(taskState.outputUri))
                }
            }
        }
    }

    /**
     * [重写] 自动保存结果到系统媒体库，支持智能分类
     */
    private suspend fun saveToGallery(file: File) {
        val context = getApplication<Application>()
        val fileName = "FFmpeg_${System.currentTimeMillis()}_${file.name}"

        // 1. 获取后缀并转为小写，处理兼容性
        val ext = _uiState.value.outputExtension.lowercase()

        // 2. 智能分类存储
        val success = when {
            // 视频类：存入 Movies/BiliDownloader
            ext in listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".3gp") -> {
                StorageHelper.saveVideoToGallery(context, file, fileName)
            }
            // 图片/动图类：存入 Pictures/BiliDownloader
            ext in listOf(".gif", ".png", ".jpg", ".jpeg", ".webp") -> {
                StorageHelper.saveGifToGallery(context, file, fileName)
            }
            // 音频或其他：存入 Music/BiliDownloader
            else -> {
                StorageHelper.saveAudioToMusic(context, file, fileName)
            }
        }

        if (success) {
            _uiState.update { s ->
                if (s.taskState is FfmpegTaskState.Success) {
                    val typeMsg = when {
                        ext == ".gif" -> "相册(图片/GIF)"
                        ext in listOf(".mp4", ".mkv") -> "相册(视频)"
                        else -> "音乐库"
                    }
                    val newLogs = s.taskState.logs + ">>> ✅ 文件已保存到系统 $typeMsg"
                    s.copy(taskState = s.taskState.copy(logs = newLogs))
                } else s
            }
        }
    }

    private fun formatSize(size: Long): String {
        val mb = size / 1024.0 / 1024.0
        return if (mb >= 1) String.format("%.1f MB", mb) else "${size / 1024} KB"
    }
}
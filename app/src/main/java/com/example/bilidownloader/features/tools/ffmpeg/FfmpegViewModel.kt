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
    savedStateHandle: SavedStateHandle // 用于接收路由参数
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FfmpegUiState())
    val uiState = _uiState.asStateFlow()

    // --- 新增：媒体选择相关状态 ---
    private val _localMediaList = MutableStateFlow<List<LocalMedia>>(emptyList())
    val localMediaList = _localMediaList.asStateFlow()

    private val _isMediaLoading = MutableStateFlow(false)
    val isMediaLoading = _isMediaLoading.asStateFlow()

    // 缓存文件的绝对路径，供 Repository 使用
    private var cachedInputPath: String? = null

    init {
        // --- 核心设计：接收外部预设参数 ---
        // 如果是从“命令助手”或“预设列表”跳转过来的，这里会自动获取参数并填入
        val presetArgs = savedStateHandle.get<String>("preset_args")
        if (!presetArgs.isNullOrBlank()) {
            try {
                // 需要 URL 解码，因为路由传参通常经过编码
                val decodedArgs = java.net.URLDecoder.decode(presetArgs, "UTF-8")
                _uiState.update { it.copy(arguments = decodedArgs) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // region --- 媒体库扫描逻辑 ---

    /**
     * 扫描本地媒体文件
     * @param isVideo true扫描视频，false扫描音频
     */
    fun loadLocalMedia(isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isMediaLoading.value = true
            val context = getApplication<Application>()
            val list = mutableListOf<LocalMedia>()

            try {
                // 1. 确定查询目标
                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                // 2. 字段投影
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DURATION,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED
                )

                // 3. 排序 (最新的在前)
                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

                // 4. 执行查询
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
                        // 过滤掉损坏或无效的文件
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

    /**
     * 用户选择了文件 (无论是通过 SAF 还是本地媒体库列表)
     */
    fun onFileSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // 1. 获取文件名和大小用于 UI 展示
            var name = "unknown_file"
            var sizeStr = "0 B"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) {
                        val size = cursor.getLong(sizeIndex)
                        sizeStr = formatSize(size)
                    }
                }
            }

            // 2. 智能推断输出格式
            val mimeType = context.contentResolver.getType(uri)
            val defaultExt = if (mimeType?.startsWith("audio") == true) ".mp3" else ".mp4"

            // 3. 将文件复制到私有缓存区 (FFmpeg 执行需要真实文件路径)
            val cacheFile = StorageHelper.copyUriToCache(context, uri, "ffmpeg_input_temp")

            if (cacheFile != null) {
                cachedInputPath = cacheFile.absolutePath
                _uiState.update {
                    it.copy(
                        inputFileUri = uri,
                        inputFileName = name,
                        inputFileSize = sizeStr,
                        outputExtension = defaultExt,
                        taskState = FfmpegTaskState.Idle
                    )
                }
            } else {
                _uiState.update {
                    it.copy(taskState = FfmpegTaskState.Error("文件读取失败", emptyList()))
                }
            }
        }
    }

    /**
     * 更新命令参数输入框
     */
    fun onArgumentsChanged(newArgs: String) {
        _uiState.update { it.copy(arguments = newArgs) }
    }

    /**
     * 更新输出后缀
     */
    fun onExtensionChanged(newExt: String) {
        val validExt = if (newExt.startsWith(".")) newExt else ".$newExt"
        _uiState.update { it.copy(outputExtension = validExt) }
    }

    /**
     * 执行命令
     */
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

                // 任务成功后自动保存到相册/音乐库
                if (taskState is FfmpegTaskState.Success) {
                    saveToGallery(File(taskState.outputUri))
                }
            }
        }
    }

    /**
     * 将生成的媒体文件保存到系统库
     */
    private suspend fun saveToGallery(file: File) {
        val context = getApplication<Application>()
        val fileName = "FFmpeg_${System.currentTimeMillis()}_${file.name}"

        // 根据后缀判断保存类型
        val success = if (_uiState.value.outputExtension.lowercase() == ".mp4") {
            StorageHelper.saveVideoToGallery(context, file, fileName)
        } else {
            StorageHelper.saveAudioToMusic(context, file, fileName)
        }

        if (success) {
            _uiState.update { s ->
                if (s.taskState is FfmpegTaskState.Success) {
                    val newLogs = s.taskState.logs + ">>> ✅ 结果已保存至系统媒体库"
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
// 文件: features/ffmpeg/FfmpegViewModel.kt
package com.example.bilidownloader.features.ffmpeg

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class FfmpegViewModel(
    application: Application,
    private val repository: FfmpegRepository,
    savedStateHandle: SavedStateHandle // 用于接收路由参数
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FfmpegUiState())
    val uiState = _uiState.asStateFlow()

    // 缓存文件的绝对路径，供 Repository 使用
    private var cachedInputPath: String? = null

    init {
        // --- 核心设计：接收外部预设参数 ---
        // 如果是从“命令助手”或“预设列表”跳转过来的，这里会自动获取参数并填入
        val presetArgs = savedStateHandle.get<String>("preset_args")
        if (!presetArgs.isNullOrBlank()) {
            // 需要 URL 解码，因为路由传参通常经过编码
            try {
                val decodedArgs = java.net.URLDecoder.decode(presetArgs, "UTF-8")
                _uiState.update { it.copy(arguments = decodedArgs) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 用户选择了文件
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
            // 如果输入是音频，默认输出改为 .mp3；如果是视频，默认 .mp4
            val mimeType = context.contentResolver.getType(uri)
            val defaultExt = if (mimeType?.startsWith("audio") == true) ".mp3" else ".mp4"

            // 3. 将文件复制到私有缓存区 (FFmpeg 需要真实路径)
            // 使用 StorageHelper 现有的工具
            val cacheFile = StorageHelper.copyUriToCache(context, uri, "ffmpeg_input_temp")

            if (cacheFile != null) {
                cachedInputPath = cacheFile.absolutePath
                _uiState.update {
                    it.copy(
                        inputFileUri = uri,
                        inputFileName = name,
                        inputFileSize = sizeStr,
                        outputExtension = defaultExt,
                        // 如果切换了文件，重置任务状态
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
     * 更新输出后缀 (如用户手动改为 .gif)
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
                inputUri = "file://$inputPath", // 构造为 file:// 协议供 Repository 处理
                args = currentState.arguments,
                outputExtension = currentState.outputExtension
            ).collect { taskState ->

                _uiState.update { it.copy(taskState = taskState) }

                // 如果成功，自动保存到相册
                if (taskState is FfmpegTaskState.Success) {
                    saveToGallery(File(taskState.outputUri))
                }
            }
        }
    }

    /**
     * 自动保存结果到系统媒体库
     */
    private suspend fun saveToGallery(file: File) {
        val context = getApplication<Application>()
        val fileName = "FFmpeg_${System.currentTimeMillis()}_${file.name}"

        val success = if (_uiState.value.outputExtension == ".mp4") {
            StorageHelper.saveVideoToGallery(context, file, fileName)
        } else {
            // 音频或其他格式统一走音频入库，或者你需要扩展 StorageHelper 支持通用文件
            StorageHelper.saveAudioToMusic(context, file, fileName)
        }

        if (success) {
            // 可以发送一个 Toast 或更新状态提示保存成功
            // 这里简单追加一条日志到状态中
            _uiState.update { s ->
                if (s.taskState is FfmpegTaskState.Success) {
                    val newLogs = s.taskState.logs + ">>> ✅ 文件已保存到系统相册/音乐库"
                    s.copy(taskState = s.taskState.copy(logs = newLogs))
                } else s
            }
        }

        // 清理临时输出文件
        // file.delete() // StorageHelper 内部是复制流，这里可以删，也可以保留给用户预览
    }

    private fun formatSize(size: Long): String {
        val mb = size / 1024.0 / 1024.0
        return if (mb >= 1) String.format("%.1f MB", mb) else "${size / 1024} KB"
    }
}
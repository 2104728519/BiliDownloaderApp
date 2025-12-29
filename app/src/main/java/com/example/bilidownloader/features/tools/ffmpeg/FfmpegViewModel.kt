package com.example.bilidownloader.features.ffmpeg

import android.app.Application
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.database.FfmpegPresetDao
import com.example.bilidownloader.core.database.FfmpegPresetEntity
import com.example.bilidownloader.core.util.StorageHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isVideo: Boolean
)

class FfmpegViewModel(
    application: Application,
    private val repository: FfmpegRepository,
    private val presetDao: FfmpegPresetDao,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FfmpegUiState())
    val uiState = _uiState.asStateFlow()

    private val _localMediaList = MutableStateFlow<List<LocalMedia>>(emptyList())
    val localMediaList = _localMediaList.asStateFlow()

    private val _isMediaLoading = MutableStateFlow(false)
    val isMediaLoading = _isMediaLoading.asStateFlow()

    private var cachedInputPath: String? = null

    val presetList = presetDao.getAllPresets().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

        viewModelScope.launch {
            FfmpegSession.taskState.collect { taskState ->
                _uiState.update { it.copy(taskState = taskState) }
                if (taskState is FfmpegTaskState.Success) {
                    saveToGallery(File(taskState.outputUri))
                }
            }
        }
    }

    // region --- 预设管理核心逻辑 ---

    fun saveCurrentAsPreset(name: String) {
        val currentState = _uiState.value
        if (name.isBlank() || currentState.arguments.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val entity = FfmpegPresetEntity(
                name = name,
                commandArgs = currentState.arguments.trim(),
                outputExtension = currentState.outputExtension
            )
            presetDao.insertPreset(entity)
        }
    }

    fun deletePreset(preset: FfmpegPresetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            presetDao.deletePreset(preset)
        }
    }

    fun applyPreset(preset: FfmpegPresetEntity) {
        _uiState.update {
            it.copy(
                arguments = preset.commandArgs,
                outputExtension = preset.outputExtension
            )
        }
    }

    // endregion

    // region --- [新增] 导入导出逻辑 ---

    /**
     * 导出所有预设为 JSON 文件并保存至下载目录
     */
    fun exportPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 获取当前所有预设
                val currentList = presetDao.getAllPresets().first()
                if (currentList.isEmpty()) {
                    showToast("没有预设可导出")
                    return@launch
                }

                // 2. 转换为传输模型并序列化
                val exportList = currentList.map {
                    PresetExportModel(it.name, it.commandArgs, it.outputExtension)
                }
                val jsonString = Gson().toJson(exportList)

                // 3. 调用 StorageHelper 保存文件
                val fileName = "ffmpeg_presets_${System.currentTimeMillis()}.json"
                val success = StorageHelper.saveJsonToDownloads(getApplication(), jsonString, fileName)

                if (success) showToast("已导出至下载目录: $fileName")
                else showToast("导出失败")

            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导出异常: ${e.message}")
            }
        }
    }

    /**
     * 从 URL 远程导入预设
     */
    fun importPresetsFromUrl(url: String) {
        if (url.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                showToast("正在下载预设文件...")

                // 1. 通过 repository 下载文本
                val jsonString = repository.fetchTextFromUrl(url)

                // 2. 使用 GSON 解析
                val type = object : TypeToken<List<PresetExportModel>>() {}.type
                val importList: List<PresetExportModel> = Gson().fromJson(jsonString, type)

                // 3. 查重入库
                val currentList = presetDao.getAllPresets().first()
                var successCount = 0
                var skipCount = 0

                importList.forEach { newItem ->
                    val exists = currentList.any { it.name == newItem.name }
                    if (!exists) {
                        presetDao.insertPreset(
                            FfmpegPresetEntity(
                                name = newItem.name,
                                commandArgs = newItem.commandArgs,
                                outputExtension = newItem.outputExtension
                            )
                        )
                        successCount++
                    } else {
                        skipCount++
                    }
                }

                showToast("导入完成: 新增 $successCount 个，跳过重复 $skipCount 个")

            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导入失败: ${e.message}")
            }
        }
    }

    /**
     * [新增] 获取预设 JSON 字符串 (用于复制到剪贴板)
     */
    fun getPresetsJson(onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = presetDao.getAllPresets().first()
                if (currentList.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }

                val exportList = currentList.map {
                    PresetExportModel(it.name, it.commandArgs, it.outputExtension)
                }
                val jsonString = Gson().toJson(exportList)

                withContext(Dispatchers.Main) { onResult(jsonString) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }
    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    // endregion

    // region --- 媒体选择与执行逻辑 (保持不变) ---

    fun loadLocalMedia(isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isMediaLoading.value = true
            val context = getApplication<Application>()
            val list = mutableListOf<LocalMedia>()

            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val duration = cursor.getLong(durCol)
                        if (duration > 0) {
                            list.add(LocalMedia(id, uri, cursor.getString(nameCol) ?: "Unknown", duration, cursor.getLong(sizeCol), isVideo))
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
                    if (sizeIndex != -1) sizeStr = formatSize(cursor.getLong(sizeIndex))
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

        val cleanArgs = currentState.arguments.replace(Regex("\\s+"), " ").trim()
        if (cleanArgs != currentState.arguments) {
            _uiState.update { it.copy(arguments = cleanArgs) }
        }

        val context = getApplication<Application>()
        val intent = Intent(context, FfmpegService::class.java).apply {
            action = FfmpegService.ACTION_START
            putExtra(FfmpegService.EXTRA_INPUT_URI, "file://$inputPath")
            putExtra(FfmpegService.EXTRA_ARGS, cleanArgs)
            putExtra(FfmpegService.EXTRA_EXT, currentState.outputExtension)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopCommand() {
        val context = getApplication<Application>()
        val intent = Intent(context, FfmpegService::class.java).apply {
            action = FfmpegService.ACTION_STOP
        }
        context.startService(intent)
    }

    private suspend fun saveToGallery(file: File) {
        val context = getApplication<Application>()
        val fileName = "FFmpeg_${System.currentTimeMillis()}_${file.name}"
        val ext = _uiState.value.outputExtension.lowercase()

        val success = when {
            ext in listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".3gp") -> StorageHelper.saveVideoToGallery(context, file, fileName)
            ext in listOf(".gif", ".png", ".jpg", ".jpeg", ".webp") -> StorageHelper.saveGifToGallery(context, file, fileName)
            else -> StorageHelper.saveAudioToMusic(context, file, fileName)
        }

        if (success) {
            _uiState.update { s ->
                if (s.taskState is FfmpegTaskState.Success) {
                    val newLogs = s.taskState.logs + ">>> ✅ 文件已保存至系统媒体库"
                    s.copy(taskState = s.taskState.copy(logs = newLogs))
                } else s
            }
        }
    }

    private fun formatSize(size: Long): String {
        val mb = size / 1024.0 / 1024.0
        return if (mb >= 1) String.format("%.1f MB", mb) else "${size / 1024} KB"
    }
    // endregion
}
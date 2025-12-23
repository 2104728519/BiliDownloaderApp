package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.model.AudioEntity
import com.example.bilidownloader.data.repository.MediaRepository
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音频选择器 ViewModel.
 *
 * 核心挑战：处理 Android 10+ (Scoped Storage) 下的文件删除和重命名权限。
 * 当应用试图修改非自己创建的文件时，系统会抛出 `RecoverableSecurityException`，
 * 必须捕获该异常并通过 `startIntentSenderForResult` 请求用户授权。
 */
class AudioPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()

    // 内存缓存，用于支持无网络/快速搜索过滤
    private var allAudiosCache: List<AudioEntity> = emptyList()
    private var currentQuery: String = ""

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isAscending = MutableStateFlow(false)
    val isAscending = _isAscending.asStateFlow()

    // UI 状态保存 (防止屏幕旋转丢失滚动位置)
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0

    private var currentSortType = SortType.DATE

    enum class SortType { DATE, SIZE, DURATION }

    // 权限请求相关状态
    var selectedAudioForAction: AudioEntity? by mutableStateOf(null)
    var showDeleteDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var pendingPermissionIntent: IntentSender? by mutableStateOf(null)

    private var lastAction: ActionType? = null
    private var lastRenameNewName: String? = null
    private enum class ActionType { DELETE, RENAME }

    fun loadAudios() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getAllAudio()
                allAudiosCache = list
                currentQuery = ""
                _audioList.value = sortList(list, currentSortType, _isAscending.value)
            } catch (e: Exception) {
                e.printStackTrace()
                _audioList.value = emptyList()
                allAudiosCache = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 纯内存过滤，无需重查数据库
    fun searchAudio(query: String) {
        currentQuery = query
        val filteredList = if (query.isBlank()) {
            allAudiosCache
        } else {
            allAudiosCache.filter { it.title.contains(query, ignoreCase = true) }
        }
        _audioList.value = sortList(filteredList, currentSortType, _isAscending.value)
    }

    fun changeSortType(type: SortType) {
        currentSortType = type
        _audioList.value = sortList(_audioList.value, type, _isAscending.value)
    }

    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
        _audioList.value = sortList(_audioList.value, currentSortType, _isAscending.value)
    }

    private fun sortList(list: List<AudioEntity>, type: SortType, ascending: Boolean): List<AudioEntity> {
        return when (type) {
            SortType.DATE -> if (ascending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            SortType.SIZE -> if (ascending) list.sortedBy { it.size } else list.sortedByDescending { it.size }
            SortType.DURATION -> if (ascending) list.sortedBy { it.duration } else list.sortedByDescending { it.duration }
        }
    }

    // region Scoped Storage Permission Handling (分区存储权限处理)

    fun deleteSelectedAudio() {
        val audio = selectedAudioForAction ?: return
        lastAction = ActionType.DELETE
        viewModelScope.launch {
            val result = StorageHelper.deleteAudioFile(getApplication(), audio.uri)
            handleStorageResult(result)
        }
        showDeleteDialog = false
    }

    fun renameSelectedAudio(newName: String) {
        val audio = selectedAudioForAction ?: return
        val finalName = if (newName.contains(".")) newName else "$newName.mp3"
        lastAction = ActionType.RENAME
        lastRenameNewName = finalName
        viewModelScope.launch {
            val result = StorageHelper.renameAudioFile(getApplication(), audio.uri, finalName)
            handleStorageResult(result)
        }
        showRenameDialog = false
    }

    private fun handleStorageResult(result: StorageHelper.StorageResult) {
        when (result) {
            is StorageHelper.StorageResult.Success -> {
                Toast.makeText(getApplication(), "操作成功", Toast.LENGTH_SHORT).show()
                updateListInMemory()
                selectedAudioForAction = null
                lastAction = null
                lastRenameNewName = null
            }
            is StorageHelper.StorageResult.RequiresPermission -> {
                // 将 IntentSender 暴露给 Activity/Composable 启动
                pendingPermissionIntent = result.intentSender
            }
            is StorageHelper.StorageResult.Error -> {
                Toast.makeText(getApplication(), "操作失败", Toast.LENGTH_SHORT).show()
                selectedAudioForAction = null
            }
        }
    }

    /**
     * UI 层处理完权限请求回调后，调用此方法重试之前的操作.
     */
    fun onPermissionGranted() {
        // 重试逻辑...
        val audio = selectedAudioForAction ?: return
        viewModelScope.launch {
            if (lastAction == ActionType.DELETE) {
                // Android 11+ (R) 删除文件不直接返回行数，需再次检查文件是否存在
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    kotlinx.coroutines.delay(200)
                    if (!StorageHelper.isFileExisting(getApplication(), audio.uri)) {
                        handleStorageResult(StorageHelper.StorageResult.Success)
                    } else {
                        handleStorageResult(StorageHelper.StorageResult.Error)
                    }
                } else {
                    deleteSelectedAudio()
                }
            } else if (lastAction == ActionType.RENAME) {
                lastRenameNewName?.let { renameSelectedAudio(it) }
            }
        }
    }

    fun permissionRequestHandled() {
        pendingPermissionIntent = null
    }

    private fun updateListInMemory() {
        val audioToUpdate = selectedAudioForAction ?: return
        if (lastAction == ActionType.DELETE) {
            allAudiosCache = allAudiosCache.filter { it.id != audioToUpdate.id }
            _audioList.value = _audioList.value.filter { it.id != audioToUpdate.id }
        } else if (lastAction == ActionType.RENAME) {
            val newName = lastRenameNewName ?: return
            val mapper = { it: AudioEntity -> if (it.id == audioToUpdate.id) it.copy(title = newName) else it }
            allAudiosCache = allAudiosCache.map(mapper)
            _audioList.value = _audioList.value.map(mapper)
        }
    }

    // endregion

    fun shareAudio(context: Context, audio: AudioEntity) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, audio.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享音频到").apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "无法分享：找不到相关应用", Toast.LENGTH_SHORT).show()
        }
    }
}
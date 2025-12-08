package com.example.bilidownloader.ui.viewmodel

import android.app.Application
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
import com.example.bilidownloader.utils.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    // UI 观察的列表（可能是经过筛选的）
    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()

    // 【新增】原始完整列表缓存 (用于搜索时回退)
    private var allAudiosCache: List<AudioEntity> = emptyList()

    // 【新增】当前搜索词 (用于排序时保持筛选状态)
    private var currentQuery: String = ""

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    var scrollIndex: Int = 0
    var scrollOffset: Int = 0

    private var currentSortType = SortType.DATE

    enum class SortType { DATE, SIZE, DURATION }

    // --- 弹窗与操作状态 ---
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
                // 【修改】保存到缓存，并重置搜索
                allAudiosCache = list
                currentQuery = ""
                _audioList.value = sortList(list, currentSortType)
            } catch (e: Exception) {
                e.printStackTrace()
                _audioList.value = emptyList()
                allAudiosCache = emptyList() // 确保缓存也清空
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 【新增】搜索音频
     */
    fun searchAudio(query: String) {
        currentQuery = query
        val listToFilter = allAudiosCache

        val filteredList = if (query.isBlank()) {
            // 如果搜索词为空，恢复显示所有缓存数据
            listToFilter
        } else {
            // 否则，过滤缓存数据 (忽略大小写)
            listToFilter.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }
        // 对过滤后的结果应用当前排序
        _audioList.value = sortList(filteredList, currentSortType)
    }

    fun changeSortType(type: SortType) {
        currentSortType = type
        // 【修改】排序时，对当前显示的列表（可能是已筛选的）进行排序
        _audioList.value = sortList(_audioList.value, type)
    }

    private fun sortList(list: List<AudioEntity>, type: SortType): List<AudioEntity> {
        return when (type) {
            SortType.DATE -> list.sortedByDescending { it.dateAdded }
            SortType.SIZE -> list.sortedByDescending { it.size }
            SortType.DURATION -> list.sortedByDescending { it.duration }
        }
    }

    fun permissionRequestHandled() {
        pendingPermissionIntent = null
    }

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
                // 清理现场
                selectedAudioForAction = null
                lastAction = null
                lastRenameNewName = null
            }
            is StorageHelper.StorageResult.RequiresPermission -> {
                pendingPermissionIntent = result.intentSender
            }
            is StorageHelper.StorageResult.Error -> {
                Toast.makeText(getApplication(), "操作失败", Toast.LENGTH_SHORT).show()
                // 清理现场
                selectedAudioForAction = null
                lastAction = null
                lastRenameNewName = null
            }
        }
    }

    fun onPermissionGranted() {
        val audio = selectedAudioForAction ?: return

        viewModelScope.launch {
            if (lastAction == ActionType.DELETE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    kotlinx.coroutines.delay(200)
                    val exists = StorageHelper.isFileExisting(getApplication(), audio.uri)
                    if (!exists) {
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

    /**
     * 【修改】更新内存数据
     * 必须同时更新 _audioList (UI显示用) 和 allAudiosCache (搜索缓存用)
     */
    private fun updateListInMemory() {
        val audioToUpdate = selectedAudioForAction ?: return

        if (lastAction == ActionType.DELETE) {
            // 1. 更新缓存：从总列表移除
            allAudiosCache = allAudiosCache.filter { it.id != audioToUpdate.id }
            // 2. 更新UI：从当前显示列表移除
            _audioList.value = _audioList.value.filter { it.id != audioToUpdate.id }
        } else if (lastAction == ActionType.RENAME) {
            val newName = lastRenameNewName ?: return

            // 1. 更新缓存
            allAudiosCache = allAudiosCache.map {
                if (it.id == audioToUpdate.id) it.copy(title = newName) else it
            }
            // 2. 更新UI
            _audioList.value = _audioList.value.map {
                if (it.id == audioToUpdate.id) it.copy(title = newName) else it
            }
        }
    }
}
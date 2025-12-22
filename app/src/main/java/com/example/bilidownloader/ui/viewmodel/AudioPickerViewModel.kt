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

class AudioPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()

    private var allAudiosCache: List<AudioEntity> = emptyList()
    private var currentQuery: String = ""

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 【新增】正序/倒序状态 (默认倒序 false，因为通常想看最新的)
    private val _isAscending = MutableStateFlow(false)
    val isAscending = _isAscending.asStateFlow()

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
                allAudiosCache = list
                currentQuery = ""
                // 初始加载应用当前的排序规则
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

    fun searchAudio(query: String) {
        currentQuery = query
        val listToFilter = allAudiosCache

        val filteredList = if (query.isBlank()) {
            listToFilter
        } else {
            listToFilter.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }
        _audioList.value = sortList(filteredList, currentSortType, _isAscending.value)
    }

    fun changeSortType(type: SortType) {
        currentSortType = type
        _audioList.value = sortList(_audioList.value, type, _isAscending.value)
    }

    // 【新增】切换正序/倒序
    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
        _audioList.value = sortList(_audioList.value, currentSortType, _isAscending.value)
    }

    // 【修改】排序逻辑，增加 isAscending 参数
    private fun sortList(list: List<AudioEntity>, type: SortType, ascending: Boolean): List<AudioEntity> {
        return when (type) {
            SortType.DATE -> if (ascending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            SortType.SIZE -> if (ascending) list.sortedBy { it.size } else list.sortedByDescending { it.size }
            SortType.DURATION -> if (ascending) list.sortedBy { it.duration } else list.sortedByDescending { it.duration }
        }
    }

    // ... (以下代码保持不变：permissionRequestHandled, deleteSelectedAudio, renameSelectedAudio, handleStorageResult, onPermissionGranted, updateListInMemory, shareAudio) ...

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
                selectedAudioForAction = null
                lastAction = null
                lastRenameNewName = null
            }
            is StorageHelper.StorageResult.RequiresPermission -> {
                pendingPermissionIntent = result.intentSender
            }
            is StorageHelper.StorageResult.Error -> {
                Toast.makeText(getApplication(), "操作失败", Toast.LENGTH_SHORT).show()
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

    private fun updateListInMemory() {
        val audioToUpdate = selectedAudioForAction ?: return

        if (lastAction == ActionType.DELETE) {
            allAudiosCache = allAudiosCache.filter { it.id != audioToUpdate.id }
            _audioList.value = _audioList.value.filter { it.id != audioToUpdate.id }
        } else if (lastAction == ActionType.RENAME) {
            val newName = lastRenameNewName ?: return
            allAudiosCache = allAudiosCache.map {
                if (it.id == audioToUpdate.id) it.copy(title = newName) else it
            }
            _audioList.value = _audioList.value.map {
                if (it.id == audioToUpdate.id) it.copy(title = newName) else it
            }
        }
    }

    fun shareAudio(context: Context, audio: AudioEntity) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, audio.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "分享音频到")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "无法分享：找不到相关应用", Toast.LENGTH_SHORT).show()
        }
    }
}
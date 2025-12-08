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
    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()
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

    // --- 未修改的方法 ---
    fun loadAudios() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getAllAudio()
                _audioList.value = sortList(list, currentSortType)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeSortType(type: SortType) {
        currentSortType = type
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
    // --- ---

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

    /**
     * 【关键修改】当用户在系统弹窗点了“允许”后的回调
     */
    fun onPermissionGranted() {
        val audio = selectedAudioForAction ?: return

        viewModelScope.launch {
            if (lastAction == ActionType.DELETE) {
                // ★ 针对 Android 11+ (R) 的特殊处理
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ 系统在用户点击“允许”时，已经自动删除了文件
                    // 所以我们不需要(也不能)再次调用 delete，否则会报错或返回失败

                    // 我们只需验证文件是否真的没了
                    // 稍作延迟以确保系统有时间完成删除操作
                    kotlinx.coroutines.delay(200)
                    val exists = StorageHelper.isFileExisting(getApplication(), audio.uri)

                    if (!exists) {
                        // 文件没了，说明系统删除成功 -> 更新 UI
                        handleStorageResult(StorageHelper.StorageResult.Success)
                    } else {
                        // 文件还在，说明出问题了，按失败处理
                        handleStorageResult(StorageHelper.StorageResult.Error)
                    }
                } else {
                    // Android 10 (Q) 及以下，需要 APP 自己再动手删一次
                    deleteSelectedAudio()
                }
            }
            else if (lastAction == ActionType.RENAME) {
                // 重命名操作，无论哪个版本，获得权限后都需要 App 自己去执行 update
                lastRenameNewName?.let { renameSelectedAudio(it) }
            }
        }
    }

    private fun updateListInMemory() {
        val audio = selectedAudioForAction ?: return
        if (lastAction == ActionType.DELETE) {
            _audioList.value = _audioList.value.filter { it.id != audio.id }
        } else if (lastAction == ActionType.RENAME) {
            val newName = lastRenameNewName ?: return
            _audioList.value = _audioList.value.map {
                if (it.id == audio.id) it.copy(title = newName) else it
            }
        }
    }
}
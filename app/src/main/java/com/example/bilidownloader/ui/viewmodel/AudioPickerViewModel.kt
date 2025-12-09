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
import com.example.bilidownloader.utils.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    // UI 观察的列表（可能是经过筛选的）
    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()

    // 原始完整列表缓存 (用于搜索时回退)
    private var allAudiosCache: List<AudioEntity> = emptyList()

    // 当前搜索词 (用于排序时保持筛选状态)
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
                allAudiosCache = list
                currentQuery = ""
                _audioList.value = sortList(list, currentSortType)
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
        _audioList.value = sortList(filteredList, currentSortType)
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

    /**
     * 【新增】分享音频
     * @param context 用来启动 Activity
     * @param audio 要分享的音频对象
     */
    fun shareAudio(context: Context, audio: AudioEntity) {
        try {
            // 1. 创建分享意图
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                // 设置数据类型为音频
                type = "audio/*"
                // 放入文件的 Uri (MediaStore Uri)
                putExtra(Intent.EXTRA_STREAM, audio.uri)
                // ★关键：授予接收方(如QQ)读取这个 Uri 的临时权限
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // 2. 创建系统选择器 (标题显示“分享音频到...”)
            // 这样用户就可以选择 QQ、微信、保存到云盘等
            val chooser = Intent.createChooser(shareIntent, "分享音频到")

            // 3. 启动
            // 如果 context 不是 Activity (比如 ApplicationContext)，需要加这个 flag
            // 但从 UI 传进来的通常是 Activity Context，加了也不影响
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
package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.model.AudioEntity
import com.example.bilidownloader.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    // 1. 数据源：音频列表
    private val _audioList = MutableStateFlow<List<AudioEntity>>(emptyList())
    val audioList = _audioList.asStateFlow()

    // 2. 状态：是否正在扫描中 (用来显示转圈圈)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 3. 当前的排序方式 (默认按时间)
    private var currentSortType = SortType.DATE

    // 定义排序类型枚举
    enum class SortType {
        DATE,       // 时间 (最新)
        SIZE,       // 大小 (最大)
        DURATION    // 时长 (最长)
    }

    /**
     * 开始加载音频
     * 这一步需要权限，所以在 UI 层申请完权限后调用这个方法
     */
    fun loadAudios() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getAllAudio()
                // 拿到数据后，先排个序再显示
                _audioList.value = sortList(list, currentSortType)
            } catch (e: Exception) {
                e.printStackTrace()
                _audioList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换排序方式
     */
    fun changeSortType(type: SortType) {
        currentSortType = type
        // 重新排序当前列表
        _audioList.value = sortList(_audioList.value, type)
    }

    // 排序逻辑实现
    private fun sortList(list: List<AudioEntity>, type: SortType): List<AudioEntity> {
        return when (type) {
            SortType.DATE -> list.sortedByDescending { it.dateAdded } // 时间倒序
            SortType.SIZE -> list.sortedByDescending { it.size }      // 大小倒序
            SortType.DURATION -> list.sortedByDescending { it.duration } // 时长倒序
        }
    }
}
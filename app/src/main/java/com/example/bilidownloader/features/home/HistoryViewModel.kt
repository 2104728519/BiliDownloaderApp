package com.example.bilidownloader.features.home

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.model.CloudHistoryItem
import com.example.bilidownloader.core.model.HistoryCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 历史记录的标签页类型枚举.
 */
enum class HistoryTab {
    Local, // 本地数据库记录
    Cloud  // B站账号云端记录
}

/**
 * 处理本地和云端播放历史的 ViewModel.
 */
class HistoryViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val homeRepository: HomeRepository
) : AndroidViewModel(application) {

    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _historyTab = MutableStateFlow(HistoryTab.Local)
    val historyTab = _historyTab.asStateFlow()

    private val _cloudHistoryList = MutableStateFlow<List<CloudHistoryItem>>(emptyList())
    val cloudHistoryList = _cloudHistoryList.asStateFlow()

    private val _isCloudHistoryLoading = MutableStateFlow(false)
    val isCloudHistoryLoading = _isCloudHistoryLoading.asStateFlow()

    private val _cloudHistoryError = MutableStateFlow<String?>(null)
    val cloudHistoryError = _cloudHistoryError.asStateFlow()

    private var nextCloudCursor: HistoryCursor? = null
    private var hasMoreCloudHistory = true

    fun selectHistoryTab(tab: HistoryTab, isUserLoggedIn: Boolean) {
        if (_historyTab.value == tab) return
        _historyTab.value = tab
        if (tab == HistoryTab.Cloud && _cloudHistoryList.value.isEmpty() && isUserLoggedIn) {
            refreshCloudHistory()
        }
    }

    fun refreshCloudHistory() {
        if (_isCloudHistoryLoading.value) return
        viewModelScope.launch {
            _isCloudHistoryLoading.value = true
            _cloudHistoryError.value = null
            nextCloudCursor = null
            hasMoreCloudHistory = true
            when (val result = homeRepository.fetchCloudHistory(null)) {
                is Resource.Success -> {
                    val (list, cursor) = result.data!!
                    _cloudHistoryList.value = list
                    nextCloudCursor = cursor
                    if (cursor == null) hasMoreCloudHistory = false
                }

                is Resource.Error -> {
                    _cloudHistoryError.value = result.message
                    _cloudHistoryList.value = emptyList()
                }

                else -> {}
            }
            _isCloudHistoryLoading.value = false
        }
    }

    fun loadMoreCloudHistory() {
        if (_isCloudHistoryLoading.value || !hasMoreCloudHistory) return
        viewModelScope.launch {
            _isCloudHistoryLoading.value = true
            when (val result = homeRepository.fetchCloudHistory(nextCloudCursor)) {
                is Resource.Success -> {
                    val (newList, cursor) = result.data!!
                    _cloudHistoryList.update { it + newList }
                    nextCloudCursor = cursor
                    if (cursor == null) hasMoreCloudHistory = false
                }

                is Resource.Error -> showToast("加载更多失败: ${result.message}")
                else -> {}
            }
            _isCloudHistoryLoading.value = false
        }
    }

    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteList(list)
        }
    }

    fun clearCloudHistory() {
        _cloudHistoryList.value = emptyList()
        nextCloudCursor = null
        hasMoreCloudHistory = true
        _cloudHistoryError.value = null
    }

    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}

package com.example.bilidownloader.features.home

import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.model.CloudHistoryItem
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.HistoryCursor
import com.example.bilidownloader.core.model.VideoDetail
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.StorageHelper
import com.example.bilidownloader.features.login.AuthRepository
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
 * 首页 ViewModel.
 */
class HomeViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val downloadRepository: DownloadRepository,
    private val subtitleRepository: SubtitleRepository
) : AndroidViewModel(application) {

    // ========================================================================
    // 状态流 (StateFlow)
    // ========================================================================

    private val _state = MutableStateFlow<HomeState>(HomeState.Idle)
    val state = _state.asStateFlow()

    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userList = authRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    // ========================================================================
    // 云端历史记录状态
    // ========================================================================

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

    // ========================================================================
    // 内部临时变量
    // ========================================================================
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null

    init {
        // 1. 监听全局下载状态 (Service -> Session -> ViewModel)
        viewModelScope.launch {
            DownloadSession.downloadState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _state.value = HomeState.Processing(
                            info = resource.data ?: "下载中...",
                            progress = resource.progress
                        )
                    }
                    is Resource.Success -> {
                        _state.value = HomeState.Success(resource.data!!)
                    }
                    is Resource.Error -> {
                        handleDownloadError(resource.message)
                    }
                }
            }
        }

        // 2. 初始化时恢复登录会话
        restoreSession()

        // 3. [核心修复] 监听当前用户变化，重置并按需刷新云端历史
        viewModelScope.launch {
            currentUser.collect { newUser ->
                // 当用户发生变化时 (登录、切换、登出)，必须重置云端历史记录的状态
                _cloudHistoryList.value = emptyList()
                nextCloudCursor = null
                hasMoreCloudHistory = true
                _cloudHistoryError.value = null

                // 如果用户切换到了一个有效账号，并且当前正停留在“账号记录”Tab，则刷新
                if (_historyTab.value == HistoryTab.Cloud && newUser != null) {
                    refreshCloudHistory()
                }
            }
        }
    }

    private fun handleDownloadError(msg: String?) {
        when (msg) {
            "PAUSED" -> {
                val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
                _state.value = HomeState.Processing("已暂停", currentP)
            }
            "CANCELED" -> _state.value = HomeState.Idle
            else -> _state.value = HomeState.Error(msg ?: "失败")
        }
    }

    // ========================================================================
    // 1. 账号管理 (Session & Cookie)
    // ========================================================================

    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeUser = authRepository.getCurrentUser()
            if (activeUser != null) {
                CookieManager.saveCookies(getApplication(), listOf(activeUser.sessData))
                _currentUser.value = activeUser
            } else {
                CookieManager.clearCookies(getApplication())
                _currentUser.value = null
            }
        }
    }

    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            val localCookie = CookieManager.getCookie(getApplication())
            if (!localCookie.isNullOrEmpty()) addOrUpdateAccount(localCookie)
        }
    }

    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawCookie = if (cookieInput.contains("=")) cookieInput else "SESSDATA=$cookieInput"
                val cookieMap = CookieManager.parseCookieStringToMap(rawCookie)
                val inputSess = cookieMap["SESSDATA"]
                if (inputSess.isNullOrEmpty()) {
                    showToast("无效的 Cookie (缺少 SESSDATA)")
                    return@launch
                }
                CookieManager.saveCookies(getApplication(), listOf(rawCookie))
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data
                if (userData != null && userData.isLogin) {
                    val inputCsrf = cookieMap["bili_jct"] ?: ""
                    val finalCsrf = if (inputCsrf.isNotEmpty()) inputCsrf else CookieManager.getCookieValue(getApplication(), "bili_jct") ?: ""
                    val newUser = UserEntity(
                        mid = userData.mid,
                        name = userData.uname,
                        face = userData.face,
                        sessData = rawCookie,
                        biliJct = finalCsrf,
                        isLogin = true
                    )
                    authRepository.clearAllLoginStatus()
                    authRepository.insertUser(newUser)
                    _currentUser.value = newUser
                    showToast("登录成功")
                } else {
                    showToast("验证失败：Cookie 可能已过期")
                    restoreSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("登录异常: ${e.message}")
                restoreSession()
            }
        }
    }

    fun switchAccount(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            authRepository.setLoginStatus(user.mid)
            CookieManager.saveCookies(getApplication(), listOf(user.sessData))
            _currentUser.value = user
        }
    }

    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
        }
    }

    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.deleteUser(user)
            if (currentUser.value?.mid == user.mid) quitToGuestMode()
        }
    }

    // ========================================================================
    // 2. 云端历史记录动作
    // ========================================================================

    fun selectHistoryTab(tab: HistoryTab) {
        if (_historyTab.value == tab) return
        _historyTab.value = tab
        if (tab == HistoryTab.Cloud && _cloudHistoryList.value.isEmpty() && currentUser.value != null) {
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

    // ========================================================================
    // 3. 视频解析与下载
    // ========================================================================

    fun analyzeInput(input: String) {
        viewModelScope.launch {
            _state.value = HomeState.Analyzing
            when (val resource = homeRepository.analyzeVideo(input)) {
                is Resource.Success -> {
                    val result = resource.data!!
                    currentDetail = result.detail
                    currentBvid = result.detail.bvid
                    currentCid = result.detail.pages[0].cid
                    savedVideoOption = result.videoFormats.firstOrNull()
                    savedAudioOption = result.audioFormats.firstOrNull()
                    _state.value = HomeState.ChoiceSelect(
                        detail = result.detail,
                        videoFormats = result.videoFormats,
                        audioFormats = result.audioFormats,
                        selectedVideo = savedVideoOption,
                        selectedAudio = savedAudioOption
                    )
                }
                is Resource.Error -> _state.value =
                    HomeState.Error(resource.message ?: "未知解析错误")
                else -> {}
            }
        }
    }

    fun startDownload(audioOnly: Boolean) {
        val vOpt = savedVideoOption
        val aOpt = savedAudioOption
        if ((!audioOnly && vOpt == null) || aOpt == null) {
            _state.value = HomeState.Error("下载参数丢失，请重新解析")
            return
        }
        isLastDownloadAudioOnly = audioOnly
        val context = getApplication<Application>()
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_BVID, currentBvid)
            putExtra(DownloadService.EXTRA_CID, currentCid)
            putExtra(DownloadService.EXTRA_AUDIO_ONLY, audioOnly)
            putExtra("vid", vOpt?.id)
            putExtra("vcodec", vOpt?.codecs)
            putExtra("aid", aOpt.id)
            putExtra("acodec", aOpt.codecs)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
        _state.value = HomeState.Processing("下载准备中...", currentP)
    }

    fun pauseDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE }
        getApplication<Application>().startService(intent)
    }
    fun resumeDownload() = startDownload(isLastDownloadAudioOnly)
    fun cancelDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_CANCEL }
        getApplication<Application>().startService(intent)
        _state.value = HomeState.Idle
    }

    // ========================================================================
    // 4. 字幕与转写
    // ========================================================================

    fun fetchSubtitle() {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return
        _state.value = currentState.copy(isSubtitleLoading = true)
        viewModelScope.launch {
            val result = subtitleRepository.getSubtitleWithSign(
                currentBvid,
                currentCid,
                currentDetail?.owner?.mid
            )
            val safeState = _state.value
            if (safeState !is HomeState.ChoiceSelect) return@launch
            when (result) {
                is Resource.Success -> {
                    val data = result.data
                    val initialContent = formatSubtitleText(data, 0, safeState.isTimestampEnabled)
                    _state.value = safeState.copy(
                        isSubtitleLoading = false,
                        subtitleData = data,
                        selectedSubtitleIndex = 0,
                        subtitleContent = initialContent
                    )
                }

                is Resource.Error -> _state.value = safeState.copy(
                    isSubtitleLoading = false,
                    subtitleData = null,
                    subtitleContent = "ERROR:${result.message}"
                )
                else -> {}
            }
        }
    }

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            downloadRepository.downloadAudioToCache(currentBvid, currentCid).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.value =
                        HomeState.Processing(resource.data ?: "准备音频中...", resource.progress)

                    is Resource.Success -> {
                        onReady(resource.data!!); reset()
                    }

                    is Resource.Error -> _state.value =
                        HomeState.Error(resource.message ?: "音频提取失败")
                }
            }
        }
    }

    fun exportSubtitle(content: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val title = currentDetail?.title ?: "Unknown_Video"
            val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val fileName = "${safeTitle}_Subtitle.txt"
            val success = StorageHelper.saveTextToDownloads(context, content, fileName)
            withContext(Dispatchers.Main) {
                if (success) Toast.makeText(
                    context,
                    "已保存至 Downloads/BiliDownloader/Transcription",
                    Toast.LENGTH_LONG
                ).show()
                else Toast.makeText(context, "保存失败，请检查文件权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleTimestamp(enabled: Boolean) {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return
        val newContent = formatSubtitleText(
            currentState.subtitleData,
            currentState.selectedSubtitleIndex,
            enabled
        )
        _state.value = currentState.copy(isTimestampEnabled = enabled, subtitleContent = newContent)
    }

    fun updateSubtitleContent(content: String) {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) _state.value =
            currentState.copy(subtitleContent = content)
    }
    fun consumeSubtitleError() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect && currentState.subtitleContent.startsWith("ERROR:")) _state.value =
            currentState.copy(subtitleContent = "")
    }
    fun clearSubtitleState() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) _state.value =
            currentState.copy(subtitleData = null, subtitleContent = "", isSubtitleLoading = false)
    }

    // ========================================================================
    // 5. 其他 UI 辅助方法
    // ========================================================================

    fun reset() { _state.value = HomeState.Idle }
    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) { historyRepository.deleteList(list) }
    }
    fun updateSelectedVideo(option: FormatOption) {
        savedVideoOption = option
        val cur = _state.value
        if (cur is HomeState.ChoiceSelect) _state.value = cur.copy(selectedVideo = option)
    }
    fun updateSelectedAudio(option: FormatOption) {
        savedAudioOption = option
        val cur = _state.value
        if (cur is HomeState.ChoiceSelect) _state.value = cur.copy(selectedAudio = option)
    }

    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSubtitleText(data: ConclusionData?, index: Int, showTimestamp: Boolean): String {
        if (data == null) return ""
        val sb = StringBuilder()
        if (!data.modelResult?.summary.isNullOrEmpty()) sb.append("【AI 摘要】\n${data.modelResult?.summary}\n\n")
        if (!data.modelResult?.outline.isNullOrEmpty()) {
            sb.append("【视频大纲】\n")
            data.modelResult?.outline?.forEach { item ->
                if (showTimestamp) sb.append("${formatTime(item.timestamp)} ")
                sb.append("${item.title}\n")
            }
            sb.append("\n")
        }
        val subtitles = data.modelResult?.subtitle
        if (!subtitles.isNullOrEmpty() && index < subtitles.size) {
            sb.append("【字幕内容】\n")
            subtitles[index].partSubtitle?.forEach { item ->
                if (showTimestamp) sb.append("[${formatTime(item.startTimestamp)}] ")
                sb.append("${item.content}\n")
            }
        }
        return sb.toString()
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
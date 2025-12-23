package com.example.bilidownloader.features.home

import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.VideoDetail
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.features.login.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,       // 替换 AnalyzeVideoUseCase
    private val downloadRepository: DownloadRepository, // 替换 DownloadVideoUseCase
    private val subtitleRepository: SubtitleRepository
) : AndroidViewModel(application) {

    // ========================================================================
    // 状态流定义
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

    // 临时状态 (用于存储解析结果，供下载使用)
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null

    init {
        // 监听全局下载状态
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
                        if (resource.message == "PAUSED") {
                            val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
                            _state.value = HomeState.Processing("已暂停", currentP)
                        } else if (resource.message == "CANCELED") {
                            _state.value = HomeState.Idle
                        } else {
                            _state.value = HomeState.Error(resource.message ?: "失败")
                        }
                    }
                }
            }
        }
        restoreSession()
    }

    // ========================================================================
    // 1. 账号管理 (委托给 AuthRepository)
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
                // 1. 简单预处理
                val rawCookie = if (cookieInput.contains("=")) cookieInput else "SESSDATA=$cookieInput"
                val cookieMap = CookieManager.parseCookieStringToMap(rawCookie)
                val inputSess = cookieMap["SESSDATA"]
                val inputCsrf = cookieMap["bili_jct"] ?: ""

                if (inputSess.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "无效的 Cookie (缺少 SESSDATA)", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. 存入 Manager 并验证
                CookieManager.saveCookies(getApplication(), listOf(rawCookie))
                val response = com.example.bilidownloader.core.network.NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data

                if (userData != null && userData.isLogin) {
                    // 3. 确定 CSRF 并入库
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "登录成功", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "验证失败：Cookie 可能已过期", Toast.LENGTH_SHORT).show()
                    }
                    restoreSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "登录异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
    // 2. 视频解析 (调用 HomeRepository)
    // ========================================================================
    fun analyzeInput(input: String) {
        viewModelScope.launch {
            _state.value = HomeState.Analyzing

            // 直接调用 Repository
            when (val resource = homeRepository.analyzeVideo(input)) {
                is Resource.Success -> {
                    val result = resource.data!!
                    currentDetail = result.detail
                    currentBvid = result.detail.bvid
                    currentCid = result.detail.pages[0].cid

                    // 默认选中第一个选项
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
                is Resource.Error -> {
                    _state.value = HomeState.Error(resource.message ?: "未知解析错误")
                }
                else -> {}
            }
        }
    }

    // ========================================================================
    // 3. 下载控制 (启动 Service)
    // ========================================================================
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

            // 传递简化后的参数 (ID 和 Codec)
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

        // 立即更新 UI 状态为处理中
        val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
        _state.value = HomeState.Processing("下载中...", currentP)
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
    // 4. 字幕与转写 (调用 SubtitleRepository / DownloadRepository)
    // ========================================================================

    fun fetchSubtitle() {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return

        _state.value = currentState.copy(isSubtitleLoading = true)

        viewModelScope.launch {
            // 直接调用 Repository
            val result = subtitleRepository.getSubtitleWithSign(
                bvid = currentBvid,
                cid = currentCid,
                upMid = currentDetail?.owner?.mid
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
                is Resource.Error -> {
                    _state.value = safeState.copy(
                        isSubtitleLoading = false,
                        subtitleData = null,
                        subtitleContent = "ERROR:${result.message}"
                    )
                }
                else -> {}
            }
        }
    }

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            // 直接调用 Repository 提取音频
            downloadRepository.downloadAudioToCache(currentBvid, currentCid).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _state.value = HomeState.Processing(resource.data ?: "准备中...", resource.progress)
                    }
                    is Resource.Success -> {
                        onReady(resource.data!!)
                        reset()
                    }
                    is Resource.Error -> {
                        _state.value = HomeState.Error(resource.message ?: "提取失败")
                    }
                }
            }
        }
    }

    // ========================================================================
    // 5. 辅助方法
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

    fun clearSubtitleState() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) {
            _state.value = currentState.copy(
                subtitleData = null,
                subtitleContent = "",
                isSubtitleLoading = false
            )
        }
    }

    fun consumeSubtitleError() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect && currentState.subtitleContent.startsWith("ERROR:")) {
            _state.value = currentState.copy(subtitleContent = "")
        }
    }

    fun toggleTimestamp(enabled: Boolean) {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return
        val newContent = formatSubtitleText(currentState.subtitleData, currentState.selectedSubtitleIndex, enabled)
        _state.value = currentState.copy(isTimestampEnabled = enabled, subtitleContent = newContent)
    }

    fun updateSubtitleContent(content: String) {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) {
            _state.value = currentState.copy(subtitleContent = content)
        }
    }

    private fun formatSubtitleText(data: ConclusionData?, index: Int, showTimestamp: Boolean): String {
        if (data == null) return ""
        val sb = StringBuilder()
        if (!data.modelResult?.summary.isNullOrEmpty()) {
            sb.append("【AI 摘要】\n${data.modelResult?.summary}\n\n")
        }
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
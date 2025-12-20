package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.data.repository.DownloadSession
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.data.repository.UserRepository
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.DownloadVideoUseCase
import com.example.bilidownloader.domain.GetSubtitleUseCase
import com.example.bilidownloader.domain.PrepareTranscribeUseCase
import com.example.bilidownloader.service.DownloadService
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val userRepository: UserRepository,
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val downloadVideoUseCase: DownloadVideoUseCase,
    private val prepareTranscribeUseCase: PrepareTranscribeUseCase,
    private val getSubtitleUseCase: GetSubtitleUseCase
) : AndroidViewModel(application) {

    // ========================================================================
    // 状态流定义
    // ========================================================================
    private val _state = MutableStateFlow<MainState>(MainState.Idle)
    val state = _state.asStateFlow()

    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userList = userRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null

    init {
        viewModelScope.launch {
            DownloadSession.downloadState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _state.value = MainState.Processing(
                            info = resource.data ?: "下载中...",
                            progress = resource.progress
                        )
                    }
                    is Resource.Success -> {
                        _state.value = MainState.Success(resource.data!!)
                    }
                    is Resource.Error -> {
                        if (resource.message == "PAUSED") {
                            val currentP = (_state.value as? MainState.Processing)?.progress ?: 0f
                            _state.value = MainState.Processing("已暂停", currentP)
                        } else if (resource.message == "CANCELED") {
                            _state.value = MainState.Idle
                        } else {
                            _state.value = MainState.Error(resource.message ?: "失败")
                        }
                    }
                }
            }
        }
        restoreSession()
    }

    // ========================================================================
    // 1. 账号管理
    // ========================================================================
    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeUser = userRepository.getCurrentUser()
            if (activeUser != null) {
                CookieManager.saveSessData(getApplication(), activeUser.sessData)
                _currentUser.value = activeUser
                _isUserLoggedIn.value = true
            } else {
                CookieManager.clearCookies(getApplication())
                _currentUser.value = null
                _isUserLoggedIn.value = false
            }
        }
    }

    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            val localCookie = CookieManager.getSessDataValue(getApplication())
            if (localCookie.isNotEmpty()) addOrUpdateAccount(localCookie)
        }
    }

    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawCookie = if (cookieInput.contains("=")) cookieInput else "SESSDATA=$cookieInput"
                CookieManager.saveSessData(getApplication(), rawCookie)
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data
                if (userData != null && userData.isLogin) {
                    val csrf = CookieManager.getCookieValue(getApplication(), "bili_jct") ?: ""
                    val newUser = UserEntity(
                        mid = userData.mid, name = userData.uname, face = userData.face,
                        sessData = rawCookie, biliJct = csrf, isLogin = true
                    )
                    userRepository.clearAllLoginStatus()
                    userRepository.insertUser(newUser)
                    _currentUser.value = newUser
                    _isUserLoggedIn.value = true
                    withContext(Dispatchers.Main) { Toast.makeText(getApplication(), "登录成功", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                restoreSession()
            }
        }
    }

    fun switchAccount(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.clearAllLoginStatus()
            userRepository.setLoginStatus(user.mid)
            CookieManager.saveSessData(getApplication(), user.sessData)
            _currentUser.value = user
            _isUserLoggedIn.value = true
        }
    }

    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
            _isUserLoggedIn.value = false
        }
    }

    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.deleteUser(user)
            if (currentUser.value?.mid == user.mid) quitToGuestMode()
        }
    }

    // ========================================================================
    // 2. 解析视频
    // ========================================================================
    fun analyzeInput(input: String) {
        viewModelScope.launch {
            analyzeVideoUseCase(input).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.value = MainState.Analyzing
                    is Resource.Success -> {
                        val result = resource.data!!
                        currentDetail = result.detail
                        currentBvid = result.detail.bvid
                        currentCid = result.detail.pages[0].cid
                        savedVideoOption = result.videoFormats.firstOrNull()
                        savedAudioOption = result.audioFormats.firstOrNull()
                        _state.value = MainState.ChoiceSelect(
                            detail = result.detail,
                            videoFormats = result.videoFormats,
                            audioFormats = result.audioFormats,
                            selectedVideo = savedVideoOption,
                            selectedAudio = savedAudioOption
                        )
                    }
                    is Resource.Error -> _state.value = MainState.Error(resource.message ?: "未知解析错误")
                }
            }
        }
    }

    // ========================================================================
    // 3. 下载控制
    // ========================================================================
    fun startDownload(audioOnly: Boolean) {
        val vOpt = savedVideoOption
        val aOpt = savedAudioOption
        if ((!audioOnly && vOpt == null) || aOpt == null) {
            _state.value = MainState.Error("下载参数丢失，请重新解析")
            return
        }
        isLastDownloadAudioOnly = audioOnly
        val context = getApplication<Application>()
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_BVID, currentBvid)
            putExtra(DownloadService.EXTRA_CID, currentCid)
            putExtra(DownloadService.EXTRA_AUDIO_ONLY, audioOnly)
            val gson = Gson()
            putExtra(DownloadService.EXTRA_AUDIO_OPT, gson.toJson(aOpt))
            if (vOpt != null) putExtra(DownloadService.EXTRA_VIDEO_OPT, gson.toJson(vOpt))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        val currentP = (_state.value as? MainState.Processing)?.progress ?: 0f
        _state.value = MainState.Processing("下载中...", currentP)
    }

    fun pauseDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE }
        getApplication<Application>().startService(intent)
    }

    fun resumeDownload() = startDownload(isLastDownloadAudioOnly)

    fun cancelDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_CANCEL }
        getApplication<Application>().startService(intent)
        _state.value = MainState.Idle
    }

    // ========================================================================
    // 4. 字幕与辅助功能
    // ========================================================================

    fun fetchSubtitle() {
        val currentState = _state.value
        if (currentState !is MainState.ChoiceSelect) return

        _state.value = currentState.copy(isSubtitleLoading = true)

        viewModelScope.launch {
            val result = getSubtitleUseCase(
                bvid = currentBvid,
                cid = currentCid,
                upMid = currentDetail?.owner?.mid
            )

            val safeState = _state.value
            if (safeState !is MainState.ChoiceSelect) return@launch

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
                    // 失败时不设置 subtitleData，显示 ERROR 前缀以便 UI 弹出 Toast
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

    /**
     * 【新增】重置字幕状态
     * 用于用户想从“预览模式”返回到“未获取状态”（例如想改用阿里云备用方案）
     */
    fun clearSubtitleState() {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
            _state.value = currentState.copy(
                subtitleData = null,
                subtitleContent = "",
                isSubtitleLoading = false
            )
        }
    }

    fun consumeSubtitleError() {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect && currentState.subtitleContent.startsWith("ERROR:")) {
            _state.value = currentState.copy(subtitleContent = "")
        }
    }

    fun toggleTimestamp(enabled: Boolean) {
        val currentState = _state.value
        if (currentState !is MainState.ChoiceSelect) return
        val newContent = formatSubtitleText(currentState.subtitleData, currentState.selectedSubtitleIndex, enabled)
        _state.value = currentState.copy(isTimestampEnabled = enabled, subtitleContent = newContent)
    }

    fun updateSubtitleContent(content: String) {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
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

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val params = PrepareTranscribeUseCase.Params(currentBvid, currentCid)
            prepareTranscribeUseCase(params).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _state.value = MainState.Processing(resource.message ?: "准备中...", resource.progress)
                    }
                    is Resource.Success -> {
                        onReady(resource.data!!)
                        reset()
                    }
                    is Resource.Error -> _state.value = MainState.Error(resource.message ?: "提取失败")
                }
            }
        }
    }

    fun reset() { _state.value = MainState.Idle }

    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) { historyRepository.deleteList(list) }
    }

    fun updateSelectedVideo(option: FormatOption) {
        savedVideoOption = option
        val cur = _state.value
        if (cur is MainState.ChoiceSelect) _state.value = cur.copy(selectedVideo = option)
    }

    fun updateSelectedAudio(option: FormatOption) {
        savedAudioOption = option
        val cur = _state.value
        if (cur is MainState.ChoiceSelect) _state.value = cur.copy(selectedAudio = option)
    }
}
package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.data.repository.UserRepository
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.DownloadVideoUseCase
import com.example.bilidownloader.domain.PrepareTranscribeUseCase
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
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
    private val prepareTranscribeUseCase: PrepareTranscribeUseCase
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

    // 缓存当前选中的视频上下文
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    init {
        restoreSession()
    }

    // ========================================================================
    // 1. 账号管理逻辑
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
            if (localCookie.isNotEmpty()) {
                addOrUpdateAccount(localCookie)
            }
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
    // 2. 核心业务：解析视频
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

                        _state.value = MainState.ChoiceSelect(
                            detail = result.detail,
                            videoFormats = result.videoFormats,
                            audioFormats = result.audioFormats,
                            selectedVideo = result.videoFormats.firstOrNull(),
                            selectedAudio = result.audioFormats.firstOrNull()
                        )
                    }
                    is Resource.Error -> _state.value = MainState.Error(resource.message ?: "未知解析错误")
                }
            }
        }
    }

    // ========================================================================
    // 3. 核心业务：下载 (已修复进度与动态文本逻辑)
    // ========================================================================

    fun startDownload(audioOnly: Boolean) {
        val currentState = _state.value as? MainState.ChoiceSelect ?: return
        val vOpt = currentState.selectedVideo
        val aOpt = currentState.selectedAudio

        if (!audioOnly && vOpt == null) {
            _state.value = MainState.Error("请选择画质")
            return
        }
        if (aOpt == null) {
            _state.value = MainState.Error("请选择音质")
            return
        }

        viewModelScope.launch {
            val params = DownloadVideoUseCase.Params(
                bvid = currentBvid,
                cid = currentCid,
                videoOption = vOpt,
                audioOption = aOpt,
                audioOnly = audioOnly
            )

            downloadVideoUseCase(params).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // 【核心修复】同步更新进度和描述文字
                        val message = resource.message ?: "处理中..."
                        val progressValue = if (resource.progress >= 0f) resource.progress else 0f

                        _state.value = MainState.Processing(
                            info = message,
                            progress = progressValue
                        )
                    }
                    is Resource.Success -> {
                        _state.value = MainState.Success(resource.data ?: "任务完成")
                    }
                    is Resource.Error -> {
                        _state.value = MainState.Error(resource.message ?: "下载失败")
                    }
                }
            }
        }
    }

    // ========================================================================
    // 4. 辅助业务：为 AI 转写准备音频
    // ========================================================================

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val params = PrepareTranscribeUseCase.Params(currentBvid, currentCid)

            prepareTranscribeUseCase(params).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        val message = resource.message ?: "准备中..."
                        val progressValue = if (resource.progress >= 0f) resource.progress else 0f
                        _state.value = MainState.Processing(
                            info = message,
                            progress = progressValue
                        )
                    }
                    is Resource.Success -> {
                        onReady(resource.data!!)
                        reset()
                    }
                    is Resource.Error -> {
                        _state.value = MainState.Error(resource.message ?: "提取失败")
                    }
                }
            }
        }
    }

    // ========================================================================
    // 5. 其他 UI 操作
    // ========================================================================

    fun reset() {
        _state.value = MainState.Idle
    }

    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteList(list)
        }
    }

    fun updateSelectedVideo(option: FormatOption) {
        val cur = _state.value
        if (cur is MainState.ChoiceSelect) _state.value = cur.copy(selectedVideo = option)
    }

    fun updateSelectedAudio(option: FormatOption) {
        val cur = _state.value
        if (cur is MainState.ChoiceSelect) _state.value = cur.copy(selectedAudio = option)
    }
}
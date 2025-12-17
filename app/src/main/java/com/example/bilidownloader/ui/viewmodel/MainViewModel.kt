package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    // 必要的临时变量
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    // 广播接收器，用于监听 Service 的反馈
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val status = it.getStringExtra(DownloadService.BROADCAST_STATUS)
                val msg = it.getStringExtra(DownloadService.BROADCAST_MESSAGE)
                val progress = it.getFloatExtra(DownloadService.BROADCAST_PROGRESS, 0f)

                when (status) {
                    "loading" -> {
                        _state.value = MainState.Processing(
                            info = msg ?: "后台下载中...",
                            progress = progress
                        )
                    }
                    "success" -> {
                        _state.value = MainState.Success(msg ?: "下载完成")
                    }
                    "error" -> {
                        _state.value = MainState.Error(msg ?: "下载失败")
                    }
                }
            }
        }
    }

    init {
        // 1. 注册进度监听广播
        LocalBroadcastManager.getInstance(application).registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadService.ACTION_PROGRESS_UPDATE)
        )
        // 2. 恢复登录 Session
        restoreSession()
    }

    override fun onCleared() {
        super.onCleared()
        // 必须注销广播，防止 Context 泄露
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(downloadReceiver)
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
    // 3. 核心业务：下载 (重构为启动 Service)
    // ========================================================================

    fun startDownload(audioOnly: Boolean) {
        val currentState = _state.value as? MainState.ChoiceSelect ?: return
        val vOpt = currentState.selectedVideo
        val aOpt = currentState.selectedAudio

        // 校验选择
        if (!audioOnly && vOpt == null) {
            _state.value = MainState.Error("请选择画质")
            return
        }
        if (aOpt == null) {
            _state.value = MainState.Error("请选择音质")
            return
        }

        val context = getApplication<Application>()
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_BVID, currentBvid)
            putExtra(DownloadService.EXTRA_CID, currentCid)
            putExtra(DownloadService.EXTRA_AUDIO_ONLY, audioOnly)

            val gson = Gson()
            putExtra(DownloadService.EXTRA_AUDIO_OPT, gson.toJson(aOpt))
            if (vOpt != null) {
                putExtra(DownloadService.EXTRA_VIDEO_OPT, gson.toJson(vOpt))
            }
        }

        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // 立即更新 UI 状态，提示用户后台已接管
        _state.value = MainState.Processing("正在启动后台服务...", 0f)
    }

    // ========================================================================
    // 4. 辅助业务：转写准备 (保持原样，此操作较轻量)
    // ========================================================================

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val params = PrepareTranscribeUseCase.Params(currentBvid, currentCid)

            prepareTranscribeUseCase(params).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        val msg = resource.message ?: resource.data
                        val progressValue = resource.progress

                        _state.value = MainState.Processing(
                            info = msg ?: "准备中...",
                            progress = if (progressValue >= 0f) progressValue else 0f
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
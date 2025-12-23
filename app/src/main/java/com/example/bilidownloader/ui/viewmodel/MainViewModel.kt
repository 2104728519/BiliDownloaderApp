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
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.VideoDetail
import com.example.bilidownloader.data.repository.DownloadSession
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.features.login.AuthRepository
import com.example.bilidownloader.domain.usecase.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.usecase.DownloadVideoUseCase
import com.example.bilidownloader.domain.usecase.GetSubtitleUseCase
import com.example.bilidownloader.domain.usecase.PrepareTranscribeUseCase
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

/**
 * 主页 ViewModel.
 *
 * 职责：
 * 1. **状态流转**：维护 [MainState]，控制 UI 从 Idle -> Analyzing -> Choice -> Processing 的变化。
 * 2. **账号管理**：处理登录 Cookie 的解析、持久化和 CSRF 捕获。
 * 3. **服务通信**：启动 [DownloadService] 并监听 [DownloadSession] 广播的进度。
 */
class MainViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val authRepository: AuthRepository,
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

    // 将数据库 Flow 转换为 StateFlow，配置 5秒 超时以支持配置变更（旋转屏幕）时的状态保持
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

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null

    init {
        // 监听下载服务广播的状态
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
            val activeUser = authRepository.getCurrentUser()
            if (activeUser != null) {
                CookieManager.saveCookies(getApplication(), listOf(activeUser.sessData))
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
            val localCookie = CookieManager.getCookie(getApplication())
            if (!localCookie.isNullOrEmpty()) addOrUpdateAccount(localCookie)
        }
    }

    /**
     * 【深度优化版】添加或更新账号
     * 强行提取 bili_jct (CSRF) 并存入数据库，确保自动化评论功能可用
     */
    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 拟人化处理：如果用户只粘贴了 SESSDATA 的值，我们手动给它补全键名
                val rawCookie = if (cookieInput.contains("=")) {
                    cookieInput
                } else {
                    "SESSDATA=$cookieInput"
                }

                // 2. 解析出关键字段用于逻辑校验
                val cookieMap = CookieManager.parseCookieStringToMap(rawCookie)
                val inputSess = cookieMap["SESSDATA"]
                val inputCsrf = cookieMap["bili_jct"] ?: "" // 尝试从用户输入中直接提取

                if (inputSess.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "无效的 Cookie (缺少 SESSDATA)", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3. 预存入 Cookie 管理器 (Retrofit 拦截器会读取此处的 Cookie 发起 getSelfInfo)
                CookieManager.saveCookies(getApplication(), listOf(rawCookie))

                // 4. 发起网络验证，检查该 Cookie 是否真的有效
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data

                if (userData != null && userData.isLogin) {
                    // 5. 【关键】确定最终的 CSRF (bili_jct)
                    // 规则：优先用输入的，如果没有，尝试从刚才 saveCookies 后的合并池里回读
                    val finalCsrf = if (inputCsrf.isNotEmpty()) {
                        inputCsrf
                    } else {
                        CookieManager.getCookieValue(getApplication(), "bili_jct") ?: ""
                    }

                    val newUser = UserEntity(
                        mid = userData.mid,
                        name = userData.uname,
                        face = userData.face,
                        sessData = rawCookie, // 存储完整原始串，以便下次恢复环境
                        biliJct = finalCsrf,  // 显式存入 CSRF 字段，供 API 调用使用
                        isLogin = true
                    )

                    // 6. 持久化到数据库
                    authRepository.clearAllLoginStatus()
                    authRepository.insertUser(newUser)

                    _currentUser.value = newUser
                    _isUserLoggedIn.value = true

                    withContext(Dispatchers.Main) {
                        val msg = if (finalCsrf.isNotEmpty()) "登录成功！(已捕获 CSRF)" else "登录成功 (注意：未捕获 CSRF，部分功能受限)"
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "验证失败：Cookie 可能已过期", Toast.LENGTH_SHORT).show()
                    }
                    restoreSession() // 验证失败，恢复到上一个有效账号或游客状态
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
            _isUserLoggedIn.value = true
        }
    }

    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
            _isUserLoggedIn.value = false
        }
    }

    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.deleteUser(user)
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
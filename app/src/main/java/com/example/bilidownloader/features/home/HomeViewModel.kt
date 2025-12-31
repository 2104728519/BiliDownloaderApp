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
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.VideoDetail
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.StorageHelper
import com.example.bilidownloader.features.login.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页 ViewModel.
 *
 * 负责协调首页的所有业务逻辑，包括：
 * 1. 视频链接解析 (HomeRepository).
 * 2. 账号 Session 管理 (AuthRepository).
 * 3. 历史记录管理 (HistoryRepository).
 * 4. 启动与控制下载服务 (DownloadService).
 * 5. 获取 AI 摘要与字幕 (SubtitleRepository).
 *
 * @param application Android 应用上下文.
 * @param historyRepository 历史记录数据源.
 * @param authRepository 用户认证数据源.
 * @param homeRepository 视频解析数据源.
 * @param downloadRepository 下载相关数据源.
 * @param subtitleRepository 字幕解析数据源.
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

    // UI 主状态
    private val _state = MutableStateFlow<HomeState>(HomeState.Idle)
    val state = _state.asStateFlow()

    // 历史记录列表 (热流)
    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 用户账号列表 (热流)
    val userList = authRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 当前激活的用户
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    // ========================================================================
    // 内部临时变量 (用于下载参数传递)
    // ========================================================================
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null

    init {
        // 监听全局下载状态 (Service -> Session -> ViewModel)
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
        // 初始化时恢复登录会话
        restoreSession()
    }

    private fun handleDownloadError(msg: String?) {
        when (msg) {
            "PAUSED" -> {
                val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
                _state.value = HomeState.Processing("已暂停", currentP)
            }

            "CANCELED" -> {
                _state.value = HomeState.Idle
            }

            else -> {
                _state.value = HomeState.Error(msg ?: "失败")
            }
        }
    }

    // ========================================================================
    // 1. 账号管理 (Session & Cookie)
    // ========================================================================

    /** 恢复上次的会话状态 */
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

    /** 同步 CookieManager 中的 Cookie 到当前用户数据库 (防止外部浏览器修改后不同步) */
    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            val localCookie = CookieManager.getCookie(getApplication())
            if (!localCookie.isNullOrEmpty()) addOrUpdateAccount(localCookie)
        }
    }

    /** 添加或更新账号 (通过 Cookie 字符串) */
    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 简单格式处理
                val rawCookie = if (cookieInput.contains("=")) cookieInput else "SESSDATA=$cookieInput"
                val cookieMap = CookieManager.parseCookieStringToMap(rawCookie)
                val inputSess = cookieMap["SESSDATA"]
                val inputCsrf = cookieMap["bili_jct"] ?: ""

                if (inputSess.isNullOrEmpty()) {
                    showToast("无效的 Cookie (缺少 SESSDATA)")
                    return@launch
                }

                // 2. 存入 Manager 并联网验证
                CookieManager.saveCookies(getApplication(), listOf(rawCookie))
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data

                if (userData != null && userData.isLogin) {
                    // 3. 获取或补全 CSRF 并入库
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

    /** 切换当前账号 */
    fun switchAccount(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            authRepository.setLoginStatus(user.mid)
            CookieManager.saveCookies(getApplication(), listOf(user.sessData))
            _currentUser.value = user
        }
    }

    /** 退出至游客模式 */
    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
        }
    }

    /** 注销并删除账号 */
    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.deleteUser(user)
            if (currentUser.value?.mid == user.mid) quitToGuestMode()
        }
    }

    // ========================================================================
    // 2. 视频解析逻辑
    // ========================================================================

    /** 解析输入的链接或 BV 号 */
    fun analyzeInput(input: String) {
        viewModelScope.launch {
            _state.value = HomeState.Analyzing

            when (val resource = homeRepository.analyzeVideo(input)) {
                is Resource.Success -> {
                    val result = resource.data!!
                    // 缓存当前视频信息
                    currentDetail = result.detail
                    currentBvid = result.detail.bvid
                    currentCid = result.detail.pages[0].cid

                    // 默认选中第一个画质/音质选项
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
    // 3. 下载控制逻辑 (Service Interaction)
    // ========================================================================

    /** 启动下载任务 */
    fun startDownload(audioOnly: Boolean) {
        val vOpt = savedVideoOption
        val aOpt = savedAudioOption
        // 校验参数
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

        // 乐观更新 UI 为“准备中”
        val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
        _state.value = HomeState.Processing("下载准备中...", currentP)
    }

    /** 暂停下载 */
    fun pauseDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE }
        getApplication<Application>().startService(intent)
    }

    /** 继续下载 */
    fun resumeDownload() = startDownload(isLastDownloadAudioOnly)

    /** 取消下载 */
    fun cancelDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_CANCEL }
        getApplication<Application>().startService(intent)
        _state.value = HomeState.Idle
    }

    // ========================================================================
    // 4. 字幕与转写逻辑
    // ========================================================================

    /** 从 B 站 API 获取 AI 摘要或字幕 */
    fun fetchSubtitle() {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return

        _state.value = currentState.copy(isSubtitleLoading = true)

        viewModelScope.launch {
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

    /** 准备转写：先下载音频提取到 Cache，成功后回调路径 */
    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            downloadRepository.downloadAudioToCache(currentBvid, currentCid).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _state.value = HomeState.Processing(
                            resource.data ?: "准备音频中...",
                            resource.progress
                        )
                    }
                    is Resource.Success -> {
                        onReady(resource.data!!)
                        reset() // 准备完成后重置首页状态
                    }
                    is Resource.Error -> {
                        _state.value = HomeState.Error(resource.message ?: "音频提取失败")
                    }
                }
            }
        }
    }

    /**
     * [新增] 导出当前字幕内容为 TXT 文件到 Downloads 目录.
     */
    fun exportSubtitle(content: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()

            // 1. 生成安全的文件名 (使用视频标题)
            val title = currentDetail?.title ?: "Unknown_Video"
            // 替换非法字符 (\ / : * ? " < > |) 为下划线
            val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val fileName = "${safeTitle}_Subtitle.txt"

            // 2. 调用 StorageHelper 保存
            val success = StorageHelper.saveTextToDownloads(context, content, fileName)

            // 3. 反馈结果
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(
                        context,
                        "已保存至 Downloads/BiliDownloader/Transcription",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "保存失败，请检查文件权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 切换时间轴显示状态 */
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

    /** 更新字幕编辑框内容 */
    fun updateSubtitleContent(content: String) {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) {
            _state.value = currentState.copy(subtitleContent = content)
        }
    }

    /** 清除字幕错误信息 (UI 消费) */
    fun consumeSubtitleError() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect && currentState.subtitleContent.startsWith("ERROR:")) {
            _state.value = currentState.copy(subtitleContent = "")
        }
    }

    /** 重置字幕状态 */
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

    // ========================================================================
    // 私有工具方法
    // ========================================================================

    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** 格式化字幕数据为纯文本 */
    private fun formatSubtitleText(data: ConclusionData?, index: Int, showTimestamp: Boolean): String {
        if (data == null) return ""
        val sb = StringBuilder()

        // 添加 AI 摘要
        if (!data.modelResult?.summary.isNullOrEmpty()) {
            sb.append("【AI 摘要】\n${data.modelResult?.summary}\n\n")
        }

        // 添加大纲
        if (!data.modelResult?.outline.isNullOrEmpty()) {
            sb.append("【视频大纲】\n")
            data.modelResult?.outline?.forEach { item ->
                if (showTimestamp) sb.append("${formatTime(item.timestamp)} ")
                sb.append("${item.title}\n")
            }
            sb.append("\n")
        }

        // 添加具体字幕内容
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
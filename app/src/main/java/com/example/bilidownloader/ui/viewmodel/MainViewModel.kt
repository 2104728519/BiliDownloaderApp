package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.data.repository.UserRepository
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.utils.BiliSigner
import com.example.bilidownloader.utils.FFmpegHelper
import com.example.bilidownloader.utils.LinkUtils
import com.example.bilidownloader.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.TreeMap
import java.util.regex.Pattern

/**
 * 主页面的 ViewModel
 *
 * 职责：
 * 1. 管理 UI 状态 (MainState)
 * 2. 处理视频解析、下载、转写等业务逻辑
 * 3. 协调账号管理和 Cookie 同步
 *
 * 重构说明 (Phase 3)：
 * - 不再直接持有 Database 或 Dao，而是通过构造函数注入 Repository。
 * - 实现了与数据层的解耦。
 */
class MainViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val userRepository: UserRepository,      // 【新增】注入 UserRepository
    private val downloadRepository: DownloadRepository // 【新增】注入 DownloadRepository
) : AndroidViewModel(application) {

    // ========================================================================
    // 状态流定义
    // ========================================================================
    private val _state = MutableStateFlow<MainState>(MainState.Idle)
    val state = _state.asStateFlow()

    // 【移除】不再需要在内部创建 Database 和 Dao
    // private val database = AppDatabase.getDatabase(application)
    // private val repository = DownloadRepository()

    // 用于解析短链接的客户端
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    // 历史记录流 (直接从注入的 Repository 获取)
    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 用户账号列表流 (从 UserRepository 获取)
    val userList = userRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 当前登录的用户状态
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    // 兼容旧代码的简单的登录状态布尔值
    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    // 当前解析上下文缓存
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    init {
        restoreSession()
    }

    // ========================================================================
    // 1. 账号管理逻辑
    // ========================================================================

    /**
     * APP 启动时恢复会话
     */
    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            // 【修改】使用 userRepository
            val activeUser = userRepository.getCurrentUser()
            if (activeUser != null) {
                // 将数据库的 Cookie 同步到 Retrofit/WebView
                CookieManager.saveSessData(getApplication(), activeUser.sessData)
                _currentUser.value = activeUser
                _isUserLoggedIn.value = true
            } else {
                // 游客模式
                CookieManager.clearCookies(getApplication())
                _currentUser.value = null
                _isUserLoggedIn.value = false
            }
        }
    }

    /**
     * 同步 Web/短信登录后的 Cookie 到数据库
     * 在 WebViewActivity 返回或短信验证码通过后调用
     */
    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            val localCookie = CookieManager.getSessDataValue(getApplication())
            if (localCookie.isNotEmpty()) {
                addOrUpdateAccount(localCookie)
            }
        }
    }

    /**
     * 添加或更新账号
     */
    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawCookie = formatCookie(cookieInput)
                if (rawCookie.isEmpty()) return@launch

                // 临时设置 Cookie 以请求 API
                CookieManager.saveSessData(getApplication(), rawCookie)

                // 调用 API 获取用户信息
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data

                if (userData != null && userData.isLogin) {
                    val csrf = CookieManager.getCookieValue(getApplication(), "bili_jct") ?: ""

                    val newUser = UserEntity(
                        mid = userData.mid,
                        name = userData.uname,
                        face = userData.face,
                        sessData = rawCookie,
                        biliJct = csrf,
                        isLogin = true
                    )

                    // 【修改】使用 userRepository
                    userRepository.clearAllLoginStatus()
                    userRepository.insertUser(newUser)

                    _currentUser.value = newUser
                    _isUserLoggedIn.value = true

                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "已登录: ${userData.uname}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("Cookie 无效或已过期")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                restoreSession() // 失败则恢复旧状态
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "登录失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 切换账号
     */
    fun switchAccount(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // 【修改】使用 userRepository
            userRepository.clearAllLoginStatus()
            userRepository.setLoginStatus(user.mid)

            CookieManager.saveSessData(getApplication(), user.sessData)
            _currentUser.value = user
            _isUserLoggedIn.value = true

            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "已切换到: ${user.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 退出当前账号（转为游客）
     */
    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            // 【修改】使用 userRepository
            userRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
            _isUserLoggedIn.value = false
        }
    }

    /**
     * 注销并删除账号
     */
    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // 尝试服务端注销
            if (user.biliJct.isNotEmpty()) {
                try {
                    val currentSess = CookieManager.getSessDataValue(getApplication())
                    // 临时切环境注销
                    CookieManager.saveSessData(getApplication(), user.sessData)
                    NetworkModule.biliService.logout(user.biliJct).execute()
                    // 恢复环境
                    if (currentSess.isNotEmpty()) CookieManager.saveSessData(getApplication(), currentSess)
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 【修改】使用 userRepository
            userRepository.deleteUser(user)
            if (currentUser.value?.mid == user.mid) {
                quitToGuestMode()
            }
        }
    }

    // 兼容 UI 层的注销调用
    fun logout() {
        currentUser.value?.let { logoutAndRemove(it) } ?: quitToGuestMode()
    }

    // 兼容 UI 层的保存调用
    fun saveCookie(cookie: String) = addOrUpdateAccount(cookie)

    private fun formatCookie(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("SESSDATA=", true) || trimmed.contains("=")) trimmed else "SESSDATA=$trimmed"
    }

    fun getCurrentCookieValue(): String = CookieManager.getSessDataValue(getApplication())


    // ========================================================================
    // 2. 核心业务：解析视频 (包含 AV1 过滤与 Hi-Res 解析)
    // ========================================================================

    fun analyzeInput(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Analyzing
            try {
                // --- 1. 获取 BV 号 ---
                var bvid = LinkUtils.extractBvid(input)
                if (bvid == null) {
                    val url = findUrl(input)
                    if (url != null && url.contains("b23.tv")) {
                        val realUrl = resolveShortLink(url)
                        bvid = LinkUtils.extractBvid(realUrl)
                    }
                }
                if (bvid == null) {
                    _state.value = MainState.Error("没找到 BV 号，请检查链接")
                    return@launch
                }

                // --- 2. 获取基本信息 ---
                val response = NetworkModule.biliService.getVideoView(bvid).execute()
                val detail = response.body()?.data ?: throw Exception("无法获取视频信息")
                currentDetail = detail
                currentBvid = detail.bvid
                currentCid = detail.pages[0].cid

                // 写入历史 (使用注入的 Repository)
                historyRepository.insert(HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                ))

                // --- 3. WBI 签名 ---
                val navResp = NetworkModule.biliService.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                // --- 4. 构建参数 (fnval=4048 开启 Dash/Dolby/Hi-Res) ---
                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", "127")
                    put("fnval", "4048")
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)
                val queryMap = signedQuery.split("&").associate {
                    val p = it.split("=")
                    URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
                }

                // --- 5. 获取流媒体信息 ---
                val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
                val playData = playResp.body()?.data ?: throw Exception("无法获取播放列表: ${playResp.errorBody()?.string()}")

                val videoOpts = mutableListOf<FormatOption>()
                val audioOpts = mutableListOf<FormatOption>()

                if (playData.dash != null) {
                    val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                        playData.timelength / 1000L
                    } else {
                        180L
                    }

                    // --- 解析视频流 (过滤 AV1) ---
                    playData.dash.video.forEach { media ->
                        // 跳过 AV1 编码
                        if (media.codecs?.startsWith("av01") == true) return@forEach

                        val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                        val desc = if (qIndex >= 0 && qIndex < (playData.accept_description?.size ?: 0)) {
                            playData.accept_description?.get(qIndex) ?: "未知画质"
                        } else "画质 ${media.id}"

                        val codecSimple = when {
                            media.codecs?.startsWith("avc") == true -> "AVC"
                            media.codecs?.startsWith("hev") == true -> "HEVC"
                            else -> "MP4"
                        }

                        val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                        videoOpts.add(FormatOption(
                            id = media.id,
                            label = "$desc ($codecSimple) - 约 ${formatSize(estimatedSize)}",
                            description = desc,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        ))
                    }

                    // --- 解析常规音频 (AAC) ---
                    playData.dash.audio?.forEach { media ->
                        val idMap = mapOf(30280 to "192K", 30232 to "132K", 30216 to "64K")
                        val name = idMap[media.id] ?: "普通音质 ${media.id}"
                        val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                        audioOpts.add(FormatOption(
                            id = media.id,
                            label = "$name (AAC) - 约 ${formatSize(estimatedSize)}",
                            description = name,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        ))
                    }

                    // --- 解析杜比全景声 (Dolby) ---
                    playData.dash.dolby?.audio?.forEach { media ->
                        val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                        audioOpts.add(FormatOption(
                            id = media.id,
                            label = "杜比全景声 (Dolby) - 约 ${formatSize(estimatedSize)}",
                            description = "杜比全景声",
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        ))
                    }

                    // --- 解析 Hi-Res 无损 (FLAC) ---
                    val flacMedia = playData.dash.flac?.audio
                    if (flacMedia != null) {
                        val estimatedSize = (flacMedia.bandwidth * durationInSeconds / 8)
                        audioOpts.add(FormatOption(
                            id = flacMedia.id,
                            label = "无损 Hi-Res (FLAC) - 约 ${formatSize(estimatedSize)}",
                            description = "无损 Hi-Res",
                            codecs = "flac",
                            bandwidth = flacMedia.bandwidth,
                            estimatedSize = estimatedSize
                        ))
                    }
                }

                val finalVideoOpts = videoOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }
                val finalAudioOpts = audioOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }

                _state.value = MainState.ChoiceSelect(
                    detail = detail,
                    videoFormats = finalVideoOpts,
                    audioFormats = finalAudioOpts,
                    selectedVideo = finalVideoOpts.firstOrNull(),
                    selectedAudio = finalAudioOpts.firstOrNull()
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("解析错误: ${e.message}")
            }
        }
    }

    // ========================================================================
    // 3. 核心业务：下载 (含 FLAC 处理和 URL 匹配)
    // ========================================================================

    fun startDownload(audioOnly: Boolean) {
        val currentState = _state.value as? MainState.ChoiceSelect ?: return
        val vOpt = currentState.selectedVideo
        val aOpt = currentState.selectedAudio

        if (!audioOnly && vOpt == null) {
            _state.value = MainState.Error("请先选择视频画质")
            return
        }
        if (aOpt == null) {
            _state.value = MainState.Error("请先选择音频音质")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("准备下载...", 0f)

            try {
                // 1. 刷新 WBI Key
                val navResp = NetworkModule.biliService.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                // 2. 刷新下载链接 (防过期)
                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", vOpt?.id ?: aOpt.id)
                    put("fnval", "4048")
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)
                val queryMap = signedQuery.split("&").associate {
                    val p = it.split("=")
                    URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
                }

                val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
                val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址")

                // 3. 匹配视频流 (Video)
                val videoUrl = if (!audioOnly) {
                    dash.video.find { it.id == vOpt!!.id && it.codecs == vOpt.codecs }?.baseUrl
                        ?: dash.video.find { it.id == vOpt!!.id }?.baseUrl
                        ?: throw Exception("未找到选中的视频流")
                } else null

                // 4. 【核心】匹配音频流 (FLAC / Dolby / AAC)
                var foundAudioUrl: String? = null
                var isFlac = false

                if (aOpt.codecs == "flac") {
                    val flacMedia = dash.flac?.audio
                    if (flacMedia != null && flacMedia.id == aOpt.id) {
                        foundAudioUrl = flacMedia.baseUrl
                        isFlac = true
                    }
                }
                if (foundAudioUrl == null) {
                    foundAudioUrl = dash.dolby?.audio?.find { it.id == aOpt.id }?.baseUrl
                }
                if (foundAudioUrl == null) {
                    foundAudioUrl = dash.audio?.find { it.id == aOpt.id }?.baseUrl
                }
                if (foundAudioUrl == null) {
                    foundAudioUrl = dash.audio?.firstOrNull()?.baseUrl
                        ?: throw Exception("未找到音频流")
                }

                val cacheDir = getApplication<Application>().cacheDir
                val audioFile = File(cacheDir, "temp_audio.m4s")

                if (audioOnly) {
                    // --- 仅音频下载 ---
                    val suffix = if (isFlac) ".flac" else ".mp3"
                    val outAudio = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}$suffix")

                    _state.value = MainState.Processing("下载音频中...", 0.0f)
                    // 【修改】使用 downloadRepository
                    downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                        _state.value = MainState.Processing("下载音频中...", p * 0.8f)
                    }

                    _state.value = MainState.Processing("处理音频...", 0.9f)
                    val success = if (isFlac) {
                        FFmpegHelper.remuxToFlac(audioFile, outAudio)
                    } else {
                        FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                    }

                    if (!success) throw Exception("音频处理失败")
                    StorageHelper.saveAudioToMusic(getApplication(), outAudio, "Bili_${currentBvid}$suffix")
                    _state.value = MainState.Success("音频已保存到音乐目录")
                    outAudio.delete()

                } else {
                    // --- 视频+音频下载 ---
                    val videoFile = File(cacheDir, "temp_video.m4s")
                    val outMp4 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp4")

                    _state.value = MainState.Processing("下载视频流...", 0f)
                    // 【修改】使用 downloadRepository
                    downloadRepository.downloadFile(videoUrl!!, videoFile).collect { p ->
                        _state.value = MainState.Processing("下载视频流...", p * 0.45f)
                    }

                    _state.value = MainState.Processing("下载音频流...", 0.45f)
                    downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                        _state.value = MainState.Processing("下载音频流...", 0.45f + p * 0.45f)
                    }

                    _state.value = MainState.Processing("合并中...", 0.9f)
                    val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)

                    if (!success) throw Exception("合并失败")
                    StorageHelper.saveVideoToGallery(getApplication(), outMp4, "Bili_${currentBvid}.mp4")
                    _state.value = MainState.Success("视频已保存到相册")

                    videoFile.delete()
                    outMp4.delete()
                }

                audioFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("下载失败: ${e.message}")
            }
        }
    }

    // ========================================================================
    // 辅助方法
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
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
            _state.value = currentState.copy(selectedVideo = option)
        }
    }

    fun updateSelectedAudio(option: FormatOption) {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
            _state.value = currentState.copy(selectedAudio = option)
        }
    }

    private fun findUrl(text: String): String? {
        val matcher = Pattern.compile("http[s]?://\\S+").matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun resolveShortLink(shortUrl: String): String {
        try {
            val request = Request.Builder().url(shortUrl).head().build()
            val response = redirectClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            return finalUrl
        } catch (e: Exception) {
            return shortUrl
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "N/A"
        val mb = bytes / 1024.0 / 1024.0
        return String.format("%.1fMB", mb)
    }

    // ========================================================================
    // 辅助业务：为 AI 转写准备音频
    // ========================================================================

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取音频流...", 0f)
            try {
                // 1. 获取密钥
                val navResp = NetworkModule.biliService.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                // 2. 签名参数
                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", "80")
                    put("fnval", "4048")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)

                val queryMap = mutableMapOf<String, String>()
                signedQuery.split("&").forEach {
                    val parts = it.split("=")
                    if (parts.size == 2) {
                        queryMap[URLDecoder.decode(parts[0], "UTF-8")] = URLDecoder.decode(parts[1], "UTF-8")
                    }
                }

                // 3. 获取地址
                val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
                val data = playResp.body()?.data

                // 优先找普通音频流，如果没有则尝试找 Durl (兼容旧视频)
                val audioUrl = data?.dash?.audio?.firstOrNull()?.baseUrl
                    ?: data?.durl?.firstOrNull()?.url
                    ?: throw Exception("未找到音频流")

                // 4. 下载到临时文件
                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "trans_temp_${System.currentTimeMillis()}.m4a")

                _state.value = MainState.Processing("正在提取音频...", 0.2f)

                // 【修改】使用 downloadRepository
                downloadRepository.downloadFile(audioUrl, tempFile).collect { p ->
                    _state.value = MainState.Processing("正在提取音频...", p)
                }

                // 5. 下载完成，恢复 UI 状态并回调
                withContext(Dispatchers.Main) {
                    currentDetail?.let {
                        val videoOpts = (state.value as? MainState.ChoiceSelect)?.videoFormats ?: emptyList()
                        val audioOpts = (state.value as? MainState.ChoiceSelect)?.audioFormats ?: emptyList()
                        _state.value = MainState.ChoiceSelect(
                            detail = it,
                            videoFormats = videoOpts,
                            audioFormats = audioOpts,
                            selectedVideo = videoOpts.firstOrNull(),
                            selectedAudio = audioOpts.firstOrNull()
                        )
                    }
                    onReady(tempFile.absolutePath)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.value = MainState.Error("提取音频失败: ${e.message}")
                }
            }
        }
    }
}
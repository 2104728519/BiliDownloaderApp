package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.utils.BiliSigner
import com.example.bilidownloader.utils.CookieManager
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MainState>(MainState.Idle)
    val state = _state.asStateFlow()

    // 兼容旧代码的登录状态流（由 currentUser 衍生）
    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private val repository = DownloadRepository()
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    // 数据库初始化
    private val database = AppDatabase.getDatabase(application)
    private val historyRepository = HistoryRepository(database.historyDao())
    private val userDao = database.userDao() // 用户表操作接口

    // 历史记录流
    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 用户账号列表流
    val userList = userDao.getAllUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 当前活跃用户
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    init {
        restoreSession()
    }

    // ========================================================================
    // 账号管理核心逻辑
    // ========================================================================

    /**
     * APP 启动时恢复会话
     * 检查数据库中是否有标记为 isLogin=true 的用户
     */
    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeUser = userDao.getCurrentUser()
            if (activeUser != null) {
                // 同步 Cookie 到网络层
                CookieManager.saveSessData(getApplication(), activeUser.sessData)
                _currentUser.value = activeUser
                _isUserLoggedIn.value = true
            } else {
                // 游客模式：确保清除 Cookie
                CookieManager.clearCookies(getApplication())
                _currentUser.value = null
                _isUserLoggedIn.value = false
            }
        }
    }

    /**
     * 【新增】同步方法：用于短信登录返回后，把 SharedPreferences 里的临时 Cookie
     * 升级为数据库里的正式用户。
     */
    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 获取当前本地存储的 Cookie (可能是短信登录刚写入的)
            val localCookie = CookieManager.getSessDataValue(getApplication())

            // 2. 如果本地有 Cookie
            if (localCookie.isNotEmpty()) {
                // 尝试将其解析并作为用户存入数据库
                // addOrUpdateAccount 内部会去调 API 验证，并更新数据库和状态
                addOrUpdateAccount(localCookie)
            }
        }
    }

    /**
     * 添加或更新账号
     * 包含了智能 Cookie 格式化逻辑和 API 验证
     */
    fun addOrUpdateAccount(cookieInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 智能格式化 Cookie (处理不带 SESSDATA= 的情况)
                val rawCookie = formatCookie(cookieInput)
                if (rawCookie.isEmpty()) return@launch

                // 2. 临时保存 Cookie 到管理器，以便发起 API 请求
                CookieManager.saveSessData(getApplication(), rawCookie)

                // 3. 调用 API 获取用户信息 (需要在 BiliApiService 中定义 getSelfInfo)
                val response = RetrofitClient.service.getSelfInfo().execute()
                val userData = response.body()?.data

                if (userData != null && userData.isLogin) {
                    // 4. 解析 CSRF Token (bili_jct)，用于后续退出登录
                    val csrf = CookieManager.getCookieValue(getApplication(), "bili_jct") ?: ""

                    // 5. 构建实体
                    val newUser = UserEntity(
                        mid = userData.mid,
                        name = userData.uname,
                        face = userData.face,
                        sessData = rawCookie,
                        biliJct = csrf,
                        isLogin = true // 设为当前活跃
                    )

                    // 6. 更新数据库：清除旧活跃状态 -> 插入新用户
                    userDao.clearAllLoginStatus()
                    userDao.insertUser(newUser)

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
                // 验证失败，回滚状态
                restoreSession()
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
            // 1. 更新数据库状态
            userDao.clearAllLoginStatus()
            userDao.setLoginStatus(user.mid)

            // 2. 更新网络层 Cookie
            CookieManager.saveSessData(getApplication(), user.sessData)
            _currentUser.value = user
            _isUserLoggedIn.value = true

            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "已切换到: ${user.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 退出当前账号（转为游客模式，不删除账号）
     */
    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            userDao.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
            _isUserLoggedIn.value = false
        }
    }

    /**
     * 彻底注销账号（服务端失效 + 从列表删除）
     */
    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 尝试发送注销请求
            if (user.biliJct.isNotEmpty()) {
                try {
                    // 临时切换 Cookie 以发送请求（如果删的不是当前账号，可能会失败，忽略即可）
                    val currentSess = CookieManager.getSessDataValue(getApplication())
                    CookieManager.saveSessData(getApplication(), user.sessData)
                    RetrofitClient.service.logout(user.biliJct).execute()
                    // 恢复之前的 Cookie
                    if (currentSess.isNotEmpty()) {
                        CookieManager.saveSessData(getApplication(), currentSess)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 从数据库删除
            userDao.deleteUser(user)

            // 3. 如果删除的是当前正在用的账号，切回游客
            if (currentUser.value?.mid == user.mid) {
                quitToGuestMode()
            }
        }
    }

    /**
     * 【修改】旧的 saveCookie 方法（兼容性）
     * 不再仅仅存储 Prefs，而是直接去触发“添加用户”的逻辑。
     */
    fun saveCookie(cookie: String) {
        // 直接转发给新逻辑，确保入库和状态更新
        addOrUpdateAccount(cookie)
    }

    // 兼容旧代码的登出（UI菜单点击退出时调用）
    fun logout() {
        // 如果有当前用户，调用完整的注销流程
        val current = _currentUser.value
        if (current != null) {
            logoutAndRemove(current)
        } else {
            // 否则执行基础清理
            quitToGuestMode()
        }
    }

    // 辅助工具：智能格式化 Cookie
    private fun formatCookie(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("SESSDATA=", ignoreCase = true) || trimmed.contains("=")) {
            trimmed
        } else {
            "SESSDATA=$trimmed"
        }
    }

    // 辅助工具：获取当前 Cookie 值
    fun getCurrentCookieValue(): String {
        return CookieManager.getSessDataValue(getApplication())
    }

    /**
     * 【修改】旧的 checkLoginStatus 升级
     * 负责将短信登录写入的临时 Cookie 升级为数据库用户
     */
    fun checkLoginStatus() {
        // 尝试将本地 Cookie 升级为数据库用户，这也会触发状态更新
        syncCookieToUserDB()
    }

    // ========================================================================
    // 业务逻辑 (解析、下载) - 保持不变
    // ========================================================================

    fun reset() {
        _state.value = MainState.Idle
    }

    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteList(list)
        }
    }

    fun analyzeInput(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Analyzing
            try {
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

                val response = RetrofitClient.service.getVideoView(bvid).execute()
                val detail = response.body()?.data ?: throw Exception("无法获取视频信息")

                currentDetail = detail
                currentBvid = detail.bvid
                currentCid = detail.pages[0].cid

                historyRepository.insert(HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                ))

                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

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

                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val playData = playResp.body()?.data ?: throw Exception("无法获取播放列表: ${playResp.errorBody()?.string()}")

                val videoOpts = mutableListOf<FormatOption>()
                val audioOpts = mutableListOf<FormatOption>()

                if (playData.dash != null) {
                    val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                        playData.timelength / 1000L
                    } else {
                        180L
                    }

                    playData.dash.video.forEach { media ->
                        val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                        val desc = if (qIndex >= 0 && qIndex < (playData.accept_description?.size ?: 0)) {
                            playData.accept_description?.get(qIndex) ?: "未知画质"
                        } else "未知画质 ${media.id}"

                        val codecSimple = when {
                            media.codecs?.startsWith("avc") == true -> "AVC"
                            media.codecs?.startsWith("hev") == true -> "HEVC"
                            media.codecs?.startsWith("av01") == true -> "AV1"
                            else -> "MP4"
                        }

                        val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                        val sizeText = formatSize(estimatedSize)

                        videoOpts.add(FormatOption(
                            id = media.id,
                            label = "$desc ($codecSimple) - 约 $sizeText",
                            description = desc,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        ))
                    }

                    playData.dash.audio?.forEach { media ->
                        val idMap = mapOf(30280 to "192K", 30232 to "132K", 30216 to "64K", 30250 to "杜比全景声", 30251 to "Hi-Res")
                        val name = idMap[media.id] ?: "音质 ${media.id}"
                        val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                        audioOpts.add(FormatOption(
                            id = media.id,
                            label = "$name - 约 ${formatSize(estimatedSize)}",
                            description = name,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
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

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "N/A"
        val mb = bytes / 1024.0 / 1024.0
        return String.format("%.1fMB", mb)
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
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

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

                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址")

                val videoUrl = if (!audioOnly) {
                    dash.video.find { it.id == vOpt!!.id && it.codecs == vOpt.codecs }?.baseUrl
                        ?: dash.video.find { it.id == vOpt!!.id }?.baseUrl
                        ?: throw Exception("未找到选中的视频流")
                } else null

                val audioUrl = dash.audio?.find { it.id == aOpt.id }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到音频流")

                val cacheDir = getApplication<Application>().cacheDir
                val audioFile = File(cacheDir, "temp_audio.m4s")

                if (audioOnly) {
                    val outMp3 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp3")
                    _state.value = MainState.Processing("下载音频中...", 0.0f)
                    repository.downloadFile(audioUrl, audioFile).collect { p ->
                        _state.value = MainState.Processing("下载音频中...", p * 0.8f)
                    }

                    _state.value = MainState.Processing("转码中...", 0.9f)
                    val success = FFmpegHelper.convertAudioToMp3(audioFile, outMp3)
                    if (!success) throw Exception("转码失败")

                    StorageHelper.saveAudioToMusic(getApplication(), outMp3, "Bili_${currentBvid}.mp3")
                    _state.value = MainState.Success("音频下载完成")
                    outMp3.delete()
                } else {
                    val videoFile = File(cacheDir, "temp_video.m4s")
                    val outMp4 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp4")

                    _state.value = MainState.Processing("下载视频流...", 0f)
                    repository.downloadFile(videoUrl!!, videoFile).collect { p ->
                        _state.value = MainState.Processing("下载视频流...", p * 0.45f)
                    }

                    _state.value = MainState.Processing("下载音频流...", 0.45f)
                    repository.downloadFile(audioUrl, audioFile).collect { p ->
                        _state.value = MainState.Processing("下载音频流...", 0.45f + p * 0.45f)
                    }

                    _state.value = MainState.Processing("合并中...", 0.9f)
                    val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                    if (!success) throw Exception("合并失败")

                    StorageHelper.saveVideoToGallery(getApplication(), outMp4, "Bili_${currentBvid}.mp4")
                    _state.value = MainState.Success("视频下载完成")

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

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取音频流...", 0f)
            try {
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

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
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val data = playResp.body()?.data

                val audioUrl = data?.dash?.audio?.firstOrNull()?.baseUrl
                    ?: data?.durl?.firstOrNull()?.url
                    ?: throw Exception("未找到音频流")

                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "trans_temp_${System.currentTimeMillis()}.m4a")

                _state.value = MainState.Processing("正在提取音频...", 0.2f)

                repository.downloadFile(audioUrl, tempFile).collect { p ->
                    _state.value = MainState.Processing("正在提取音频...", p)
                }

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
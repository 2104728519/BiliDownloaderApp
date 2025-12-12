package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.database.HistoryEntity
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

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private val repository = DownloadRepository()
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()
    private val database = AppDatabase.getDatabase(application)
    private val historyRepository = HistoryRepository(database.historyDao())

    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    // 【删除】旧的变量 selectedVideoOption 和 selectedAudioOption 已被移除
    // 现在状态由 _state 统一管理

    init {
        checkLoginStatus()
    }

    fun checkLoginStatus() {
        val sess = CookieManager.getCookieValue(getApplication(), "SESSDATA")
        _isUserLoggedIn.value = !sess.isNullOrEmpty()
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val csrf = CookieManager.getCookieValue(getApplication(), "bili_jct")
            if (!csrf.isNullOrEmpty()) {
                try {
                    RetrofitClient.service.logout(csrf).execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            CookieManager.clearCookies(getApplication())
            reset()
            checkLoginStatus()
        }
    }

    fun saveCookie(cookie: String) {
        CookieManager.saveSessData(getApplication(), cookie)
        checkLoginStatus()
    }

    fun getCurrentCookieValue(): String {
        return CookieManager.getSessDataValue(getApplication())
    }

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

                // 【修改】直接在构建状态时传入默认选项
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

    // 【修改】更新选项时，直接更新 StateFlow
    fun updateSelectedVideo(option: FormatOption) {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
            _state.value = currentState.copy(selectedVideo = option)
        }
    }

    // 【修改】更新选项时，直接更新 StateFlow
    fun updateSelectedAudio(option: FormatOption) {
        val currentState = _state.value
        if (currentState is MainState.ChoiceSelect) {
            _state.value = currentState.copy(selectedAudio = option)
        }
    }

    fun startDownload(audioOnly: Boolean) {
        // 【修改】从当前 State 中获取选项
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
                // (下载逻辑与之前保持一致，省略重复代码以节省篇幅，核心逻辑不变)
                // ... 获取密钥、签名 ...
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
                // ...
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
                // (省略重复的 API 调用代码，逻辑不变)
                // ...
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
                    // 【修改】恢复状态时，需要重新构建完整的 ChoiceSelect，包括选项
                    currentDetail?.let {
                        val videoOpts = (state.value as? MainState.ChoiceSelect)?.videoFormats ?: emptyList()
                        val audioOpts = (state.value as? MainState.ChoiceSelect)?.audioFormats ?: emptyList()
                        _state.value = MainState.ChoiceSelect(
                            detail = it,
                            videoFormats = videoOpts,
                            audioFormats = audioOpts,
                            selectedVideo = videoOpts.firstOrNull(), // 恢复默认或保持之前的选择
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
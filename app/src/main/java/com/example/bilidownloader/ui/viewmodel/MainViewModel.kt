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

    // 缓存当前选中的格式，UI 更改选择时更新这两个变量
    var selectedVideoOption: FormatOption? = null
    var selectedAudioOption: FormatOption? = null

    fun reset() {
        _state.value = MainState.Idle
        selectedVideoOption = null
        selectedAudioOption = null
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

                // 1. 获取基本信息
                val response = RetrofitClient.service.getVideoView(bvid).execute()
                val detail = response.body()?.data ?: throw Exception("无法获取视频信息")

                currentDetail = detail
                currentBvid = detail.bvid
                currentCid = detail.pages[0].cid // 默认P1

                // 2. 存历史记录
                historyRepository.insert(HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                ))

                // 3. 预获取 PlayUrl 以解析可用画质和音质
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", "120")
                    put("fnval", "4048")
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)
                val queryMap = signedQuery.split("&").associate {
                    val p = it.split("=")
                    URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
                }

                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val playData = playResp.body()?.data

                // 4. 解析格式列表
                val videoOpts = mutableListOf<FormatOption>()
                val audioOpts = mutableListOf<FormatOption>()

                if (playData?.dash != null) {
                    // 【修改 A】: 从 API 获取真实的视频时长（毫秒），并转换为秒。
                    // 如果 API 没有返回时长，则使用默认的 180 秒作为备用值。
                    val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                        playData.timelength / 1000L
                    } else {
                        180L // 备用值
                    }

                    // 4.1 视频处理
                    playData.dash.video.forEach { media ->
                        val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                        val desc = if (qIndex >= 0) playData.accept_description?.get(qIndex) ?: "未知画质" else "未知画质"

                        val codecSimple = when {
                            media.codecs?.startsWith("avc") == true -> "AVC"
                            media.codecs?.startsWith("hev") == true -> "HEVC"
                            media.codecs?.startsWith("av01") == true -> "AV1"
                            else -> "MP4"
                        }

                        // 【修改 B】: 使用真实的视频时长来计算估算大小
                        // 体积 (Bytes) = 码率 (bit/s) * 时长 (s) / 8
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

                    // 4.2 音频处理
                    playData.dash.audio?.forEach { media ->
                        val idMap = mapOf(30280 to "192K", 30232 to "132K", 30216 to "64K", 30250 to "杜比全景声", 30251 to "Hi-Res")
                        val name = idMap[media.id] ?: "音质 ${media.id}"

                        // 【修改 C】: 同样使用真实时长来计算音频的估算大小
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

                // 去重并排序 (码率高的在前面)
                val finalVideoOpts = videoOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }
                val finalAudioOpts = audioOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }

                // 默认选中第一个 (最高画质/音质)
                selectedVideoOption = finalVideoOpts.firstOrNull()
                selectedAudioOption = finalAudioOpts.firstOrNull()

                _state.value = MainState.ChoiceSelect(detail, finalVideoOpts, finalAudioOpts)

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

    /**
     * 开始下载
     * (此部分逻辑未改动)
     */
    fun startDownload(audioOnly: Boolean) {
        val vOpt = selectedVideoOption
        val aOpt = selectedAudioOption

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
                // 1. 获取密钥 (重复逻辑可提取，这里保留)
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                // 2. 签名参数 - 使用选中的 qn
                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", vOpt?.id?.toString() ?: "80")
                    put("fnval", "4048")
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)
                val queryMap = signedQuery.split("&").associate {
                    val p = it.split("=")
                    URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
                }

                // 3. 获取地址
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址")

                // 4. 从 DASH 列表中精确匹配我们选中的那个流
                val videoUrl = if (!audioOnly) {
                    dash.video.find { it.bandwidth == vOpt!!.bandwidth }?.baseUrl
                        ?: dash.video.firstOrNull { it.id == vOpt!!.id }?.baseUrl
                        ?: throw Exception("未找到选中的视频流")
                } else null

                val audioUrl = dash.audio?.find { it.bandwidth == aOpt.bandwidth }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到音频流")

                // 5. 后续下载逻辑
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

    // (此部分逻辑未改动)
    fun updateSelectedVideo(option: FormatOption) {
        selectedVideoOption = option
    }

    // (此部分逻辑未改动)
    fun updateSelectedAudio(option: FormatOption) {
        selectedAudioOption = option
    }

    /**
     * 为转写做准备
     * (此部分逻辑未改动)
     */
    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取音频流...", 0f)
            try {
                // 1. 获取密钥 & 签名
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
                // 2. 获取地址
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val data = playResp.body()?.data

                val audioUrl = data?.dash?.audio?.firstOrNull()?.baseUrl
                    ?: data?.durl?.firstOrNull()?.url
                    ?: throw Exception("未找到音频流")

                // 3. 下载
                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "trans_temp_${System.currentTimeMillis()}.m4a")

                _state.value = MainState.Processing("正在提取音频...", 0.2f)

                repository.downloadFile(audioUrl, tempFile).collect { p ->
                    _state.value = MainState.Processing("正在提取音频...", p)
                }

                // 4. 切换回主线程进行回调
                withContext(Dispatchers.Main) {
                    currentDetail?.let {
                        _state.value = MainState.ChoiceSelect(it,
                            (state.value as? MainState.ChoiceSelect)?.videoFormats ?: emptyList(),
                            (state.value as? MainState.ChoiceSelect)?.audioFormats ?: emptyList()
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
package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.data.repository.HistoryRepository
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.TreeMap
import java.util.regex.Pattern

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MainState>(MainState.Idle)
    val state = _state.asStateFlow()

    private val repository = DownloadRepository()
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    // 【新增 1】初始化数据库和历史管理员
    private val database = AppDatabase.getDatabase(application)
    private val historyRepository = HistoryRepository(database.historyDao())

    // 【新增 2】把数据库里的历史记录拿出来，转成 StateFlow 给 UI 用
    // stateIn 的作用是把 Flow 变成 StateFlow，这样 UI 就可以用 collectAsState 监听了
    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // 省电策略：UI 不显示时过5秒停止更新
        initialValue = emptyList() // 初始值是空列表
    )

    private var currentBvid: String = ""
    private var currentCid: Long = 0L

    fun reset() {
        _state.value = MainState.Idle
    }

    // 【新增 3】删除历史记录的方法
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
                val detail = response.body()?.data

                if (detail == null) {
                    _state.value = MainState.Error("无法获取视频信息")
                    return@launch
                }

                currentBvid = detail.bvid
                currentCid = detail.pages[0].cid

                // 【新增 4】解析成功后，立刻写入历史记录
                val historyItem = HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis() // 记录当前时间
                )
                historyRepository.insert(historyItem)

                _state.value = MainState.ChoiceSelect(detail)

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("网络错误: ${e.message}")
            }
        }
    }

    // --- 以下代码与之前完全一致，没有修改 ---

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

    fun startDownload(audioOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取加密密钥...", 0f)

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
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)

                val queryMap = mutableMapOf<String, String>()
                signedQuery.split("&").forEach {
                    val parts = it.split("=")
                    if (parts.size == 2) {
                        queryMap[java.net.URLDecoder.decode(parts[0], "UTF-8")] = java.net.URLDecoder.decode(parts[1], "UTF-8")
                    }
                }

                _state.value = MainState.Processing("正在获取播放地址...", 0.1f)
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取视频流")

                val videoUrl = dash.video.firstOrNull()?.baseUrl
                val audioUrl = dash.audio?.firstOrNull()?.baseUrl

                if (videoUrl == null && !audioOnly) throw Exception("未找到视频流")
                if (audioUrl == null) throw Exception("未找到音频流")

                val cacheDir = getApplication<Application>().cacheDir
                val audioFile = File(cacheDir, "temp_audio.m4s")

                if (audioOnly) {
                    val outMp3 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp3")
                    _state.value = MainState.Processing("正在下载音频...", 0.3f)
                    repository.downloadFile(audioUrl, audioFile).collect { p ->
                        _state.value = MainState.Processing("正在下载音频...", 0.3f + (p * 0.5f))
                    }
                    _state.value = MainState.Processing("正在转码为 MP3...", 0.9f)
                    val success = FFmpegHelper.convertAudioToMp3(audioFile, outMp3)
                    if (!success) throw Exception("MP3 转换失败")
                    _state.value = MainState.Processing("正在保存到音乐库...", 0.99f)
                    val saveSuccess = StorageHelper.saveAudioToMusic(getApplication(), outMp3, "Bili_${currentBvid}.mp3")
                    if (saveSuccess) _state.value = MainState.Success("音频导出成功！") else throw Exception("保存失败")
                    outMp3.delete()
                } else {
                    val videoFile = File(cacheDir, "temp_video.m4s")
                    val outMp4 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp4")
                    _state.value = MainState.Processing("正在下载音频...", 0.2f)
                    repository.downloadFile(audioUrl, audioFile).collect { p -> _state.value = MainState.Processing("正在下载音频...", 0.2f + (p * 0.2f)) }
                    _state.value = MainState.Processing("正在下载视频...", 0.4f)
                    repository.downloadFile(videoUrl!!, videoFile).collect { p -> _state.value = MainState.Processing("正在下载视频...", 0.4f + (p * 0.4f)) }
                    _state.value = MainState.Processing("正在合并中...", 0.9f)
                    val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                    if (!success) throw Exception("合并失败")
                    _state.value = MainState.Processing("正在保存到相册...", 0.99f)
                    val saveSuccess = StorageHelper.saveVideoToGallery(getApplication(), outMp4, "Bili_${currentBvid}.mp4")
                    if (saveSuccess) _state.value = MainState.Success("视频导出成功！") else throw Exception("保存相册失败")
                    videoFile.delete()
                    outMp4.delete()
                }
                audioFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("出错: ${e.message}")
            }
        }
    }
}
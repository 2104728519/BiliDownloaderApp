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
import kotlinx.coroutines.withContext // 记得导入这个
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.TreeMap
import java.util.regex.Pattern

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 1. 状态管理
    private val _state = MutableStateFlow<MainState>(MainState.Idle)
    val state = _state.asStateFlow()

    // 2. 仓库与工具
    private val repository = DownloadRepository()
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    // 3. 数据库与历史记录
    private val database = AppDatabase.getDatabase(application)
    private val historyRepository = HistoryRepository(database.historyDao())

    // 暴露给 UI 的历史列表 (StateFlow)
    val historyList = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 4. 当前处理的视频信息缓存
    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null

    /**
     * 重置状态到空闲
     */
    fun reset() {
        _state.value = MainState.Idle
    }

    /**
     * 删除选中的历史记录
     */
    fun deleteHistories(list: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteList(list)
        }
    }

    /**
     * 解析输入的链接或文字
     */
    fun analyzeInput(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Analyzing
            try {
                // 1. 尝试直接提取 BV 号
                var bvid = LinkUtils.extractBvid(input)

                // 2. 没找到？看看是不是短链接
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

                // 3. 调用 API 获取视频详情
                val response = RetrofitClient.service.getVideoView(bvid).execute()
                val detail = response.body()?.data

                if (detail == null) {
                    _state.value = MainState.Error("无法获取视频信息")
                    return@launch
                }

                // 4. 缓存信息
                currentDetail = detail
                currentBvid = detail.bvid
                currentCid = detail.pages[0].cid // 默认取 P1

                // 5. 写入历史记录数据库
                val historyItem = HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                )
                historyRepository.insert(historyItem)

                // 6. 切换到选择界面
                _state.value = MainState.ChoiceSelect(detail)

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("网络错误: ${e.message}")
            }
        }
    }

    // --- 辅助工具方法 ---

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
     * 开始下载 (视频/音频)
     */
    fun startDownload(audioOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取加密密钥...", 0f)

            try {
                // 1. 获取密钥
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                // 2. 签名参数
                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", "80") // 1080P
                    put("fnval", "4048") // DASH
                    put("fourk", "1")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)

                val queryMap = mutableMapOf<String, String>()
                signedQuery.split("&").forEach {
                    val parts = it.split("=")
                    if (parts.size == 2) {
                        queryMap[URLDecoder.decode(parts[0], "UTF-8")] = URLDecoder.decode(parts[1], "UTF-8")
                    }
                }

                // 3. 获取播放地址
                _state.value = MainState.Processing("正在获取播放地址...", 0.1f)
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址")

                val videoUrl = dash.video.firstOrNull()?.baseUrl
                val audioUrl = dash.audio?.firstOrNull()?.baseUrl

                if (videoUrl == null && !audioOnly) throw Exception("未找到视频流")
                if (audioUrl == null) throw Exception("未找到音频流")

                // 4. 准备文件
                val cacheDir = getApplication<Application>().cacheDir
                val audioFile = File(cacheDir, "temp_audio.m4s")

                if (audioOnly) {
                    // === 纯音频模式 ===
                    val outMp3 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp3")

                    _state.value = MainState.Processing("正在下载音频...", 0.3f)
                    repository.downloadFile(audioUrl!!, audioFile).collect { p ->
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
                    // === 视频模式 ===
                    val videoFile = File(cacheDir, "temp_video.m4s")
                    val outMp4 = File(cacheDir, "${currentBvid}_${System.currentTimeMillis()}.mp4")

                    _state.value = MainState.Processing("正在下载音频...", 0.2f)
                    repository.downloadFile(audioUrl!!, audioFile).collect { p -> _state.value = MainState.Processing("正在下载音频...", 0.2f + (p * 0.2f)) }

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

                // 公共清理
                audioFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = MainState.Error("出错: ${e.message}")
            }
        }
    }

    /**
     * 【新增】为转写做准备
     * 逻辑：获取链接 -> 下载音频临时文件 -> 回调文件路径
     */
    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MainState.Processing("正在获取音频流...", 0f)

            try {
                // 1. 获取密钥 & 签名 (这部分不用动)
                val navResp = RetrofitClient.service.getNavInfo().execute()
                val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
                val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
                val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

                val params = TreeMap<String, Any>().apply {
                    put("bvid", currentBvid)
                    put("cid", currentCid)
                    put("qn", "80")
                    put("fnval", "16")
                }
                val signedQuery = BiliSigner.signParams(params, mixinKey)

                val queryMap = mutableMapOf<String, String>()
                signedQuery.split("&").forEach {
                    val parts = it.split("=")
                    if (parts.size == 2) {
                        queryMap[URLDecoder.decode(parts[0], "UTF-8")] = URLDecoder.decode(parts[1], "UTF-8")
                    }
                }

                // 2. 获取地址 (这部分不用动)
                val playResp = RetrofitClient.service.getPlayUrl(queryMap).execute()
                val data = playResp.body()?.data

                val audioUrl = if (data?.dash != null) {
                    data.dash.audio?.firstOrNull()?.baseUrl
                } else if (data?.durl != null) {
                    data.durl.firstOrNull()?.url
                } else {
                    null
                }

                if (audioUrl == null) throw Exception("未找到音频流")

                // 3. 下载 (这部分不用动)
                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "trans_temp_${System.currentTimeMillis()}.m4a")

                _state.value = MainState.Processing("正在提取音频...", 0.2f)

                repository.downloadFile(audioUrl, tempFile).collect { p ->
                    _state.value = MainState.Processing("正在提取音频...", p)
                }

                // 4. 【关键修改】切换回主线程进行跳转！
                withContext(Dispatchers.Main) {
                    // 恢复空闲状态
                    currentDetail?.let {
                        _state.value = MainState.ChoiceSelect(it)
                    }
                    // 在主线程执行回调，这样 navController 就不会报错了
                    onReady(tempFile.absolutePath)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 错误处理也建议切回主线程更新状态
                withContext(Dispatchers.Main) {
                    _state.value = MainState.Error("提取音频失败: ${e.message}")
                }
            }
        }
    }
}
package com.example.bilidownloader.features.home

import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.model.*
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页主业务 ViewModel，负责视频解析、下载调度和字幕转写逻辑。
 */
class HomeViewModel(
    application: Application,
    private val homeRepository: HomeRepository,
    private val downloadRepository: DownloadRepository,
    private val subtitleRepository: SubtitleRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<HomeState>(HomeState.Idle)
    val state = _state.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0L
    private var currentDetail: VideoDetail? = null
    private var isLastDownloadAudioOnly: Boolean = false
    private var savedVideoOption: FormatOption? = null
    private var savedAudioOption: FormatOption? = null
    private var savedAudioExtension: String = "m4a"

    init {
        viewModelScope.launch {
            DownloadSession.downloadState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        val rawData = resource.data ?: ""
                        var info = rawData.substringBefore("|")
                        var detail: String? = null
                        var isMerging = false
                        var mergeProgress = 0f

                        if (rawData.contains("|DETAIL:")) {
                            detail = rawData.substringAfter("|DETAIL:")
                        } else if (rawData.contains("|MERGE:")) {
                            isMerging = true
                            mergeProgress = rawData.substringAfter("|MERGE:").toFloatOrNull() ?: 0f
                        } else if (rawData.contains("|CROP:")) {
                            isMerging = true
                            info = "正在进行自定义裁剪..."
                            mergeProgress = rawData.substringAfter("|CROP:").toFloatOrNull() ?: 0f
                        }

                        _state.value = HomeState.Processing(
                            info = if (info.isEmpty()) "处理中..." else info,
                            progress = resource.progress,
                            detail = detail,
                            isMerging = isMerging,
                            mergeProgress = mergeProgress
                        )
                    }

                    is Resource.Success -> _state.value = HomeState.Success(resource.data!!)
                    is Resource.Error -> handleDownloadError(resource.message)
                }
            }
        }
    }

    private fun handleDownloadError(msg: String?) {
        when (msg) {
            "PAUSED" -> {
                val currentP = (_state.value as? HomeState.Processing)?.progress ?: 0f
                _state.value = HomeState.Processing("已暂停", currentP)
            }
            "CANCELED" -> _state.value = HomeState.Idle
            else -> _state.value = HomeState.Error(msg ?: "失败")
        }
    }

    fun analyzeInput(input: String) {
        viewModelScope.launch {
            _state.value = HomeState.Analyzing
            when (val resource = homeRepository.analyzeVideo(input)) {
                is Resource.Success -> {
                    val result = resource.data!!
                    val firstPage = result.detail.pages.first()
                    currentDetail = result.detail
                    currentBvid = result.detail.bvid
                    currentCid = firstPage.cid
                    savedVideoOption = result.videoFormats.firstOrNull()
                    savedAudioOption = result.audioFormats.firstOrNull()
                    savedAudioExtension = "m4a"
                    _state.value = HomeState.ChoiceSelect(
                        detail = result.detail,
                        videoFormats = result.videoFormats,
                        audioFormats = result.audioFormats,
                        selectedVideo = savedVideoOption,
                        selectedAudio = savedAudioOption,
                        selectedPage = firstPage,
                        selectedAudioExtension = savedAudioExtension,
                        videoDurationSeconds = result.durationSeconds,
                        cropStart = 0f,
                        cropEnd = result.durationSeconds.toFloat()
                    )
                }

                is Resource.Error -> _state.value =
                    HomeState.Error(resource.message ?: "未知解析错误")
                else -> {}
            }
        }
    }

    fun updateSelectedPage(page: PageData) {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect || page.cid == currentCid) return

        viewModelScope.launch {
            currentCid = page.cid
            when (val result = homeRepository.fetchVideoFormats(currentBvid, page.cid)) {
                is Resource.Success -> {
                    val formats = result.data!!
                    savedVideoOption = formats.first.firstOrNull()
                    savedAudioOption = formats.second.firstOrNull()
                    _state.value = currentState.copy(
                        selectedPage = page,
                        videoFormats = formats.first,
                        audioFormats = formats.second,
                        selectedVideo = savedVideoOption,
                        selectedAudio = savedAudioOption,
                        videoDurationSeconds = formats.third,
                        cropStart = 0f,
                        cropEnd = formats.third.toFloat(),
                        subtitleData = null,
                        subtitleContent = "",
                        isSubtitleLoading = false
                    )
                }

                is Resource.Error -> showToast("切换分P失败: ${result.message}")
                else -> {}
            }
        }
    }

    fun toggleCrop(enabled: Boolean) {
        val cur = _state.value
        if (cur is HomeState.ChoiceSelect) _state.value = cur.copy(isCropEnabled = enabled)
    }

    fun updateCropRange(start: Float, end: Float) {
        val cur = _state.value
        if (cur is HomeState.ChoiceSelect) _state.value = cur.copy(cropStart = start, cropEnd = end)
    }

    fun startDownload(audioOnly: Boolean) {
        val currentState = _state.value as? HomeState.ChoiceSelect
        val vOpt = savedVideoOption
        val aOpt = savedAudioOption
        if (currentState == null || (!audioOnly && vOpt == null) || aOpt == null) {
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
            if (audioOnly) putExtra("audio_ext", savedAudioExtension)
            if (currentState.detail.pages.size > 1) putExtra(
                "p_title",
                currentState.selectedPage.part
            )

            // 裁剪参数
            if (currentState.isCropEnabled) {
                putExtra("is_crop", true)
                putExtra("crop_start", currentState.cropStart)
                putExtra("crop_end", currentState.cropEnd)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)

        _state.value = HomeState.Processing("下载准备中...", 0f)
    }

    fun pauseDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE }
        getApplication<Application>().startService(intent)
    }
    fun resumeDownload() = startDownload(isLastDownloadAudioOnly)
    fun cancelDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply { action = DownloadService.ACTION_CANCEL }
        getApplication<Application>().startService(intent)
        _state.value = HomeState.Idle
    }

    fun fetchSubtitle() {
        val currentState = _state.value
        if (currentState !is HomeState.ChoiceSelect) return
        _state.value = currentState.copy(isSubtitleLoading = true)
        viewModelScope.launch {
            val result = subtitleRepository.getSubtitleWithSign(
                currentBvid,
                currentCid,
                currentDetail?.owner?.mid
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

                is Resource.Error -> _state.value = safeState.copy(
                    isSubtitleLoading = false,
                    subtitleData = null,
                    subtitleContent = "ERROR:${result.message}"
                )
                else -> {}
            }
        }
    }

    fun prepareForTranscription(onReady: (String) -> Unit) {
        viewModelScope.launch {
            downloadRepository.downloadAudioToCache(currentBvid, currentCid).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.value =
                        HomeState.Processing(resource.data ?: "准备音频中...", resource.progress)

                    is Resource.Success -> {
                        onReady(resource.data!!); reset()
                    }

                    is Resource.Error -> _state.value =
                        HomeState.Error(resource.message ?: "音频提取失败")
                }
            }
        }
    }

    fun exportSubtitle(content: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val title = currentDetail?.title ?: "Unknown_Video"
            val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val fileName = "${safeTitle}_Subtitle.txt"
            val success = StorageHelper.saveTextToDownloads(context, content, fileName)
            withContext(Dispatchers.Main) {
                if (success) Toast.makeText(
                    context,
                    "已保存至 Downloads/BiliDownloader/Transcription",
                    Toast.LENGTH_LONG
                ).show()
                else Toast.makeText(context, "保存失败，请检查文件权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    fun updateSubtitleContent(content: String) {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) _state.value =
            currentState.copy(subtitleContent = content)
    }
    fun consumeSubtitleError() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect && currentState.subtitleContent.startsWith("ERROR:")) _state.value =
            currentState.copy(subtitleContent = "")
    }
    fun clearSubtitleState() {
        val currentState = _state.value
        if (currentState is HomeState.ChoiceSelect) _state.value =
            currentState.copy(subtitleData = null, subtitleContent = "", isSubtitleLoading = false)
    }

    fun reset() { _state.value = HomeState.Idle }

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
    fun updateAudioExtension(ext: String) {
        savedAudioExtension = ext
        val cur = _state.value
        if (cur is HomeState.ChoiceSelect) _state.value = cur.copy(selectedAudioExtension = ext)
    }

    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSubtitleText(data: ConclusionData?, index: Int, showTimestamp: Boolean): String {
        if (data == null) return ""
        val sb = StringBuilder()
        val subtitles = data.modelResult?.subtitle
        if (!subtitles.isNullOrEmpty() && index < subtitles.size) {
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

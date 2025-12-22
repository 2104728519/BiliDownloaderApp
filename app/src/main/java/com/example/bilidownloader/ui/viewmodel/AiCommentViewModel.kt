package com.example.bilidownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.util.RateLimitHelper
import com.example.bilidownloader.data.model.CandidateVideo
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.RecommendRepository
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.GetSubtitleUseCase
import com.example.bilidownloader.domain.model.AiModelConfig // 新增
import com.example.bilidownloader.domain.model.CommentStyle
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase
import com.example.bilidownloader.domain.usecase.GetRecommendedVideosUseCase
import com.example.bilidownloader.domain.usecase.PostCommentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class AiCommentUiState(
    val loadingState: AiCommentLoadingState = AiCommentLoadingState.Idle,
    val error: String? = null,
    val successMessage: String? = null,

    // 视频信息
    val videoTitle: String = "",
    val videoCover: String = "",
    val oid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val upMid: Long = 0L,

    // 字幕数据
    val subtitleData: ConclusionData? = null,
    val isSubtitleReady: Boolean = false,

    // 评论生成
    val generatedContent: String = "",
    val selectedStyle: CommentStyle? = null,

    // [新增] 当前选择的模型 (默认为智能托管)
    val currentModel: AiModelConfig = AiModelConfig.SMART_AUTO,

    // 自动化相关
    val recommendationList: List<CandidateVideo> = emptyList(),
    val isAutoRunning: Boolean = false,
    val autoLogs: List<String> = emptyList()
)

class AiCommentViewModel(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getSubtitleUseCase: GetSubtitleUseCase,
    private val generateCommentUseCase: GenerateCommentUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val getRecommendedVideosUseCase: GetRecommendedVideosUseCase,
    private val recommendRepository: RecommendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCommentUiState())
    val uiState = _uiState.asStateFlow()
    private var automationJob: Job? = null

    // [新增] 切换模型
    fun updateModel(model: AiModelConfig) {
        if (!_uiState.value.isAutoRunning) {
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    // ... (toggleAutomation, startAutomation, stopAutomation, appendLog 保持不变) ...
    // 为了节省篇幅，这里简写，请保持原有的自动化控制逻辑
    fun toggleAutomation(style: CommentStyle?) {
        if (_uiState.value.isAutoRunning) stopAutomation() else if (style != null) startAutomation(style)
    }
    private fun startAutomation(style: CommentStyle) {
        _uiState.update { it.copy(isAutoRunning = true, selectedStyle = style, autoLogs = listOf(">>> 启动自动化 | 风格: ${style.label} | 模型: ${_uiState.value.currentModel.name}")) }
        automationJob = viewModelScope.launch { startAutomationLoop(style) }
    }
    private fun stopAutomation() {
        automationJob?.cancel()
        automationJob = null
        _uiState.update { it.copy(isAutoRunning = false, loadingState = AiCommentLoadingState.Idle) }
        appendLog(">>> 自动化任务已停止")
    }
    private fun appendLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.update { it.copy(autoLogs = (it.autoLogs + "[$timestamp] $msg").takeLast(50)) }
    }

    // ========================================================================
    // 自动化循环 (startAutomationLoop)
    // ========================================================================
    private suspend fun startAutomationLoop(style: CommentStyle) {
        val localQueue = ArrayDeque<CandidateVideo>()

        while (currentCoroutineContext().isActive) {
            try {
                // 1. 获取列表
                if (localQueue.isEmpty()) {
                    appendLog("获取推荐列表...")
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingRecommendations) }
                    val result = getRecommendedVideosUseCase()
                    if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                        localQueue.addAll(result.data)
                        appendLog("获取到 ${result.data.size} 个视频")
                    } else {
                        appendLog("获取列表失败: ${result.message}，休息10秒...")
                        delay(10000)
                        continue
                    }
                }

                // 2. 取视频
                val currentVideo = localQueue.removeFirst()
                applyCandidate(currentVideo)
                appendLog("处理: ${currentVideo.info.title}")

                // 3. 获取字幕
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingSubtitle) }
                delay(Random.nextLong(1000, 3000))

                val subResult = getSubtitleUseCase(currentVideo.info.bvid, currentVideo.cid, currentVideo.info.owner?.mid)

                if (subResult is Resource.Error || subResult.data?.modelResult == null) {
                    appendLog("❌ 无字幕，跳过")
                    delay(2000)
                    continue
                }

                val validSubtitleData = subResult.data
                _uiState.update { it.copy(subtitleData = validSubtitleData, isSubtitleReady = true, loadingState = AiCommentLoadingState.Idle) }

                // 4. [修改] 估算 Token (仅用于日志显示，实际配额检查已移交 Repository)
                // 只有在智能模式下，RateLimitHelper 才起作用；如果是强制模型，这里只是个参考
                val summaryText = validSubtitleData.modelResult?.summary ?: ""
                val subtitleText = validSubtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle?.joinToString { it.content } ?: ""
                val estimatedTokens = RateLimitHelper.estimateTokens(summaryText + subtitleText)
                appendLog("内容长度: $estimatedTokens tokens")

                // 5. [修改] 生成评论 (传入当前选中的模型配置)
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment) }

                // >>> 这里传入 state.currentModel <<<
                val genResult = generateCommentUseCase(validSubtitleData, style, _uiState.value.currentModel)

                if (genResult is Resource.Error) {
                    appendLog("⚠️ 生成失败: ${genResult.message}")
                    delay(2000)
                    continue
                }

                val commentText = genResult.data!!
                _uiState.update { it.copy(generatedContent = commentText) }

                // 6. 模拟阅读 & 发送
                val readingTime = Random.nextLong(5000, 10000)
                appendLog("阅读中 (${readingTime/1000}s)...")
                delay(readingTime)

                _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
                val postResult = postCommentUseCase(currentVideo.info.id, commentText)

                if (postResult is Resource.Success) {
                    appendLog("✅ 发送成功")
                    recommendRepository.markVideoAsProcessed(currentVideo.info)
                    recommendRepository.reportProgress(currentVideo.info.id, currentVideo.cid)
                } else {
                    appendLog("❌ 发送失败: ${postResult.message}")
                }

                val cooldown = Random.nextLong(15000, 30000)
                appendLog("冷却 (${cooldown/1000}s)...")
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.AutoRunning) }
                delay(cooldown)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                appendLog("循环异常: ${e.message}")
                delay(5000)
            }
        }
    }

    // ... (fetchRecommendations, applyCandidate, analyzeVideo, fetchSubtitle, updateContent, sendComment, clearMessages) ...
    // 这些方法逻辑不变，唯一需要注意的是 generateComment (手动生成) 也要传 model

    // [修改] 手动生成评论
    fun generateComment(style: CommentStyle) {
        val state = _uiState.value
        if (state.subtitleData == null || (state.loadingState != AiCommentLoadingState.Idle && !state.isAutoRunning)) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment, selectedStyle = style, error = null) }

            // >>> 传入 state.currentModel <<<
            val result = generateCommentUseCase(state.subtitleData, style, state.currentModel)

            when (result) {
                is Resource.Success -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, generatedContent = result.data ?: "") }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                else -> {}
            }
        }
    }

    // (其余方法复用之前的即可，如果你需要完整文件请告诉我，这里省略以聚焦变更)
    fun fetchRecommendations() {
        if (_uiState.value.loadingState != AiCommentLoadingState.Idle && !_uiState.value.isAutoRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingRecommendations, error = null) }
            val result = getRecommendedVideosUseCase()
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, recommendationList = result.data ?: emptyList(), successMessage = "获取 ${result.data?.size ?: 0} 个推荐") }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                else -> {}
            }
        }
    }
    fun applyCandidate(candidate: CandidateVideo) {
        _uiState.update { it.copy(videoTitle = candidate.info.title, videoCover = candidate.info.pic, bvid = candidate.info.bvid, oid = candidate.info.id, cid = candidate.cid, upMid = candidate.info.owner?.mid ?: 0L, subtitleData = candidate.subtitleData, isSubtitleReady = candidate.subtitleData != null, error = null, generatedContent = "", selectedStyle = if (it.isAutoRunning) it.selectedStyle else null) }
        if (candidate.subtitleData == null && !_uiState.value.isAutoRunning) fetchSubtitle()
    }
    fun analyzeVideo(url: String) {
        if (url.isBlank() || (_uiState.value.loadingState != AiCommentLoadingState.Idle && !_uiState.value.isAutoRunning)) return
        viewModelScope.launch {
            analyzeVideoUseCase(url).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.AnalyzingVideo, error = null) }
                    is Resource.Success -> { val detail = resource.data!!.detail; _uiState.update { it.copy(videoTitle = detail.title, videoCover = detail.pic, bvid = detail.bvid, oid = detail.aid, cid = detail.pages.first().cid, upMid = detail.owner.mid) }; fetchSubtitle() }
                    is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = resource.message) }
                }
            }
        }
    }
    private fun fetchSubtitle() {
        val state = _uiState.value
        if (state.bvid.isEmpty() || state.cid == 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingSubtitle) }
            val result = getSubtitleUseCase(state.bvid, state.cid, state.upMid)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, subtitleData = result.data, isSubtitleReady = true, error = null) }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = "无字幕: ${result.message}", isSubtitleReady = false) }
                else -> {}
            }
        }
    }
    fun updateContent(text: String) { _uiState.update { it.copy(generatedContent = text) } }
    fun sendComment() {
        val state = _uiState.value
        if (state.oid == 0L || state.generatedContent.isBlank() || (state.loadingState != AiCommentLoadingState.Idle && !state.isAutoRunning)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
            val result = postCommentUseCase(state.oid, state.generatedContent)
            when (result) {
                is Resource.Success -> { launch { val currentItem = state.recommendationList.find { it.info.bvid == state.bvid }?.info; if (currentItem != null) recommendRepository.markVideoAsProcessed(currentItem); recommendRepository.reportProgress(state.oid, state.cid) }; _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, successMessage = "发送成功", generatedContent = "", recommendationList = it.recommendationList.filter { item -> item.info.bvid != it.bvid }) } }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                else -> {}
            }
        }
    }
    fun clearMessages() { _uiState.update { it.copy(error = null, successMessage = null) } }
}
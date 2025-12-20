package com.example.bilidownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.model.CandidateVideo
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.RecommendRepository
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.GetSubtitleUseCase
import com.example.bilidownloader.domain.model.CommentStyle
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase
import com.example.bilidownloader.domain.usecase.GetRecommendedVideosUseCase
import com.example.bilidownloader.domain.usecase.PostCommentUseCase
import com.example.bilidownloader.utils.RateLimitHelper
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

    // 推荐列表
    val recommendationList: List<CandidateVideo> = emptyList(),

    // 自动化相关
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

    // ========================================================================
    // 自动化核心逻辑 (拟人化优化版)
    // ========================================================================

    fun toggleAutomation(style: CommentStyle?) {
        if (_uiState.value.isAutoRunning) {
            stopAutomation()
        } else {
            if (style != null) {
                startAutomation(style)
            }
        }
    }

    private fun startAutomation(style: CommentStyle) {
        _uiState.update {
            it.copy(
                isAutoRunning = true,
                selectedStyle = style,
                autoLogs = listOf(">>> 自动化任务启动 - 风格: ${style.label}")
            )
        }
        automationJob = viewModelScope.launch {
            startAutomationLoop(style)
        }
    }

    private fun stopAutomation() {
        automationJob?.cancel()
        automationJob = null
        _uiState.update {
            it.copy(
                isAutoRunning = false,
                loadingState = AiCommentLoadingState.Idle
            )
        }
        appendLog(">>> 自动化任务已停止")
    }

    private suspend fun startAutomationLoop(style: CommentStyle) {
        val localQueue = ArrayDeque<CandidateVideo>()

        while (currentCoroutineContext().isActive) {
            try {
                // 1. 获取基础推荐列表 (不带字幕，降低风控)
                if (localQueue.isEmpty()) {
                    appendLog("正在获取推荐列表 (不含字幕)...")
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingRecommendations) }

                    val result = getRecommendedVideosUseCase()
                    if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                        localQueue.addAll(result.data)
                        appendLog("成功获取 ${result.data.size} 个待处理视频")
                    } else {
                        appendLog("获取列表失败: ${result.message}，10秒后重试...")
                        delay(10000)
                        continue
                    }
                }

                // 2. 取出视频并更新 UI
                val currentVideo = localQueue.removeFirst()
                applyCandidate(currentVideo)
                appendLog("准备检查内容: ${currentVideo.info.title}")

                // 3. [拟人化逻辑] 此时才请求字幕，模拟点击进入视频页
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingSubtitle) }

                // 模拟人类在页面停留的随机延迟 (1.5~4秒)
                delay(Random.nextLong(1500, 4000))

                val subResult = getSubtitleUseCase(
                    currentVideo.info.bvid,
                    currentVideo.cid,
                    currentVideo.info.owner?.mid ?: 0L
                )

                if (subResult is Resource.Error || subResult.data?.modelResult == null) {
                    appendLog("❌ 该视频无 AI 摘要，跳过...")
                    delay(2000)
                    continue
                }

                // 成功获取字幕
                val validSubtitleData = subResult.data
                _uiState.update {
                    it.copy(
                        subtitleData = validSubtitleData,
                        isSubtitleReady = true,
                        loadingState = AiCommentLoadingState.Idle
                    )
                }
                appendLog("✅ 字幕解析完成，开始生成评论")

                // 4. Token 检查
                val totalText = (validSubtitleData.modelResult?.summary ?: "") +
                        (validSubtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle?.joinToString { it.content } ?: "")
                val estimatedTokens = RateLimitHelper.estimateTokens(totalText)

                appendLog("消耗估算: $estimatedTokens，申请配额...")
                RateLimitHelper.waitForQuota(estimatedTokens)

                // 5. 生成评论
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment) }
                val genResult = generateCommentUseCase(validSubtitleData, style)

                if (genResult is Resource.Error) {
                    appendLog("AI 生成报错: ${genResult.message}")
                    delay(2000)
                    continue
                }

                val commentText = genResult.data!!
                _uiState.update { it.copy(generatedContent = commentText) }

                // 6. 模拟深度阅读时间 (5~12秒)
                val readingTime = Random.nextLong(5000, 12000)
                appendLog("模拟阅读中 (${readingTime/1000}s)...")
                delay(readingTime)

                // 7. 发送评论
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
                val postResult = postCommentUseCase(currentVideo.info.id, commentText)

                if (postResult is Resource.Success) {
                    appendLog("✅ 任务完成：评论已发送")
                    recommendRepository.markVideoAsProcessed(currentVideo.info)
                    recommendRepository.reportProgress(currentVideo.info.id, currentVideo.cid)
                } else {
                    appendLog("❌ 发送失败: ${postResult.message}")
                }

                // 8. 强制冷却，防止频率过高
                val cooldown = Random.nextLong(15000, 30000)
                appendLog("等待冷却 (${cooldown/1000}s)...")
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.AutoRunning) }
                delay(cooldown)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                appendLog("自动化异常: ${e.message}")
                delay(5000)
            }
        }
    }

    private fun appendLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "[$timestamp] $msg"
        _uiState.update {
            val updatedLogs = (it.autoLogs + newLog).takeLast(50)
            it.copy(autoLogs = updatedLogs)
        }
    }

    // ========================================================================
    // 基础/手动操作区域
    // ========================================================================

    fun applyCandidate(candidate: CandidateVideo) {
        _uiState.update {
            it.copy(
                videoTitle = candidate.info.title,
                videoCover = candidate.info.pic,
                bvid = candidate.info.bvid,
                oid = candidate.info.id,
                cid = candidate.cid,
                upMid = candidate.info.owner?.mid ?: 0L,

                // 手动模式下，如果传入的数据里已经有字幕则使用，否则重置等待请求
                subtitleData = candidate.subtitleData,
                isSubtitleReady = candidate.subtitleData != null,
                error = null,
                generatedContent = "",
                selectedStyle = if (it.isAutoRunning) it.selectedStyle else null
            )
        }

        // [新增逻辑] 如果是手动点击列表项，且当前没有字幕数据，且没有在自动化运行，则自动去抓取字幕
        if (candidate.subtitleData == null && !_uiState.value.isAutoRunning) {
            fetchSubtitle()
        }
    }

    fun fetchRecommendations() {
        if (_uiState.value.loadingState != AiCommentLoadingState.Idle && !_uiState.value.isAutoRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingRecommendations, error = null) }
            val result = getRecommendedVideosUseCase()
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle,
                            recommendationList = result.data ?: emptyList(),
                            successMessage = "获取 ${result.data?.size ?: 0} 个推荐"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                }
                else -> {}
            }
        }
    }

    fun analyzeVideo(url: String) {
        if (url.isBlank() || (_uiState.value.loadingState != AiCommentLoadingState.Idle && !_uiState.value.isAutoRunning)) return
        viewModelScope.launch {
            analyzeVideoUseCase(url).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.AnalyzingVideo, error = null) }
                    is Resource.Success -> {
                        val detail = resource.data!!.detail
                        _uiState.update {
                            it.copy(
                                videoTitle = detail.title,
                                videoCover = detail.pic,
                                bvid = detail.bvid,
                                oid = detail.aid,
                                cid = detail.pages.first().cid,
                                upMid = detail.owner.mid
                            )
                        }
                        fetchSubtitle()
                    }
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
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle,
                            subtitleData = result.data,
                            isSubtitleReady = true,
                            error = null
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle,
                            error = "无字幕: ${result.message}",
                            isSubtitleReady = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun generateComment(style: CommentStyle) {
        val state = _uiState.value
        if (state.subtitleData == null || (state.loadingState != AiCommentLoadingState.Idle && !state.isAutoRunning)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment, selectedStyle = style, error = null) }
            val result = generateCommentUseCase(state.subtitleData, style)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, generatedContent = result.data ?: "") }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                }
                else -> {}
            }
        }
    }

    fun updateContent(text: String) {
        _uiState.update { it.copy(generatedContent = text) }
    }

    fun sendComment() {
        val state = _uiState.value
        if (state.oid == 0L || state.generatedContent.isBlank() || (state.loadingState != AiCommentLoadingState.Idle && !state.isAutoRunning)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
            val result = postCommentUseCase(state.oid, state.generatedContent)
            when (result) {
                is Resource.Success -> {
                    launch {
                        val currentItem = state.recommendationList.find { it.info.bvid == state.bvid }?.info
                        if (currentItem != null) {
                            recommendRepository.markVideoAsProcessed(currentItem)
                        }
                        recommendRepository.reportProgress(state.oid, state.cid)
                    }
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle,
                            successMessage = "评论发送成功！",
                            generatedContent = "",
                            recommendationList = it.recommendationList.filter { item -> item.info.bvid != it.bvid }
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                }
                else -> {}
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
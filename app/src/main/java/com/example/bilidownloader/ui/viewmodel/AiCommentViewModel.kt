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
    // 自动化核心逻辑
    // ========================================================================

    // [修改 1] 参数改为可空类型 CommentStyle?，适配 UI 停止按钮无需传参的场景
    fun toggleAutomation(style: CommentStyle?) {
        if (_uiState.value.isAutoRunning) {
            // 停止时不需要风格参数
            stopAutomation()
        } else {
            // 开始时必须有风格
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
        appendLog(">>> 自动化任务已由用户手动停止")
    }

    private suspend fun startAutomationLoop(style: CommentStyle) {
        val localQueue = ArrayDeque<CandidateVideo>()

        while (currentCoroutineContext().isActive) {
            try {
                // 1. 检查队列
                if (localQueue.isEmpty()) {
                    appendLog("正在获取 B 站首页推荐及字幕...")
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingRecommendations) }

                    val result = getRecommendedVideosUseCase()
                    if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                        localQueue.addAll(result.data)
                        appendLog("成功获取 ${result.data.size} 个待处理视频")
                    } else {
                        appendLog("获取推荐失败或无合适视频: ${result.message}，等待 10秒后重试...")
                        delay(10000)
                        continue
                    }
                }

                // 2. 取出视频
                val currentVideo = localQueue.removeFirst()
                applyCandidate(currentVideo)
                appendLog("正在处理: ${currentVideo.info.title}")

                // 3. 估算 Token 并申请配额
                val summaryText = currentVideo.subtitleData.modelResult?.summary ?: ""
                val subtitleText = currentVideo.subtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle?.joinToString { it.content } ?: ""
                val totalText = summaryText + subtitleText

                val estimatedTokens = RateLimitHelper.estimateTokens(totalText)
                appendLog("预计消耗 Token: $estimatedTokens，正在检查配额...")

                RateLimitHelper.waitForQuota(estimatedTokens)

                // 4. 生成评论
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment) }
                val genResult = generateCommentUseCase(currentVideo.subtitleData, style)

                if (genResult is Resource.Error) {
                    appendLog("AI 生成失败: ${genResult.message}，跳过此视频")
                    delay(2000)
                    continue
                }

                val commentText = genResult.data!!
                _uiState.update { it.copy(generatedContent = commentText) }
                appendLog("AI 生成完毕，准备发送...")

                // 5. 模拟浏览延迟
                val readingTime = Random.nextLong(5000, 10000)
                delay(readingTime)

                // 6. 发送评论
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
                val postResult = postCommentUseCase(currentVideo.info.id, commentText)

                if (postResult is Resource.Success) {
                    appendLog("✅ 评论发送成功！")
                    recommendRepository.markVideoAsProcessed(currentVideo.info)
                    recommendRepository.reportProgress(currentVideo.info.id, currentVideo.cid)
                } else {
                    appendLog("❌ 发送失败: ${postResult.message}，跳过")
                }

                // 7. 冷却时间
                val cooldown = Random.nextLong(15000, 30000)
                appendLog("进入冷却时间 (${cooldown/1000}秒)...")
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.AutoRunning) }
                delay(cooldown)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                appendLog("自动化循环异常: ${e.message}，等待重试")
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
    // 基础操作区域
    // ========================================================================

    // [修改 2] 自动化运行时，保留 selectedStyle，避免 UI 闪烁或丢失配置
    fun applyCandidate(candidate: CandidateVideo) {
        _uiState.update {
            it.copy(
                videoTitle = candidate.info.title,
                videoCover = candidate.info.pic,
                bvid = candidate.info.bvid,
                oid = candidate.info.id,
                cid = candidate.cid,
                upMid = candidate.info.owner?.mid ?: 0L,
                subtitleData = candidate.subtitleData,
                isSubtitleReady = true,
                error = null,
                generatedContent = "",

                // [关键修复] 如果是自动化运行中，保持当前风格；否则(手动模式)清空风格
                selectedStyle = if (it.isAutoRunning) it.selectedStyle else null
            )
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
                            successMessage = "筛选出 ${result.data?.size ?: 0} 个视频"
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
                        it.copy(loadingState = AiCommentLoadingState.Idle, error = "无法获取字幕: ${result.message}", isSubtitleReady = false)
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
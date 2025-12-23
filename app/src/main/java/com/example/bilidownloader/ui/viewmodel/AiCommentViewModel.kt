package com.example.bilidownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.util.RateLimitHelper
import com.example.bilidownloader.data.model.CandidateVideo
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.RecommendRepository
import com.example.bilidownloader.data.repository.StyleRepository
import com.example.bilidownloader.domain.usecase.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.usecase.GetSubtitleUseCase
import com.example.bilidownloader.domain.model.AiModelConfig
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

/**
 * AI 评论助手 UI 状态.
 * 聚合了视频信息、字幕数据、生成结果以及自动化控制台日志.
 */
data class AiCommentUiState(
    val loadingState: AiCommentLoadingState = AiCommentLoadingState.Idle,
    val error: String? = null,
    val successMessage: String? = null,

    // 当前处理的视频信息
    val videoTitle: String = "",
    val videoCover: String = "",
    val oid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val upMid: Long = 0L,

    // 字幕与内容生成
    val subtitleData: ConclusionData? = null,
    val isSubtitleReady: Boolean = false,
    val generatedContent: String = "",
    val selectedStyle: CommentStyle? = null,

    // 配置与列表
    val availableStyles: List<CommentStyle> = emptyList(),
    val currentModel: AiModelConfig = AiModelConfig.SMART_AUTO,
    val recommendationList: List<CandidateVideo> = emptyList(),

    // 自动化状态
    val isAutoRunning: Boolean = false,
    val autoLogs: List<String> = emptyList()
)

/**
 * AI 评论助手核心 ViewModel.
 *
 * 职责：
 * 1. **单点操作**：协调视频解析 -> 字幕获取 -> AI 生成 -> 评论发送的全流程。
 * 2. **风格管理**：监听 [StyleRepository] 获取实时更新的评论风格列表。
 * 3. **自动化循环**：实现类似“按键精灵”的后台任务，自动从推荐流获取视频并生成评论。
 *    - 包含随机延迟 (Jitter) 以模拟人类行为，降低风控风险。
 *    - 包含本地去重逻辑，防止重复评论。
 */
class AiCommentViewModel(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getSubtitleUseCase: GetSubtitleUseCase,
    private val generateCommentUseCase: GenerateCommentUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val getRecommendedVideosUseCase: GetRecommendedVideosUseCase,
    private val recommendRepository: RecommendRepository,
    private val styleRepository: StyleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCommentUiState())
    val uiState = _uiState.asStateFlow()

    // 自动化任务 Job，用于手动停止循环
    private var automationJob: Job? = null

    init {
        // 启动时订阅风格仓库，确保 UI 总是显示最新的内置+自定义风格
        viewModelScope.launch {
            styleRepository.allStyles.collect { styles ->
                _uiState.update { it.copy(availableStyles = styles) }
            }
        }
    }

    // region Custom Style Management (风格管理)

    fun addCustomStyle(label: String, prompt: String) {
        viewModelScope.launch {
            styleRepository.addStyle(label, prompt)
            _uiState.update { it.copy(successMessage = "已添加风格: $label") }
        }
    }

    fun deleteCustomStyle(style: CommentStyle) {
        viewModelScope.launch {
            styleRepository.deleteStyle(style)
            if (_uiState.value.selectedStyle == style) {
                _uiState.update { it.copy(selectedStyle = null) }
            }
            _uiState.update { it.copy(successMessage = "已删除风格: ${style.label}") }
        }
    }

    // endregion

    fun updateModel(model: AiModelConfig) {
        if (!_uiState.value.isAutoRunning) {
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    // region Automation Logic (自动化逻辑)

    fun toggleAutomation(style: CommentStyle?) {
        if (_uiState.value.isAutoRunning) stopAutomation() else if (style != null) startAutomation(style)
    }

    private fun startAutomation(style: CommentStyle) {
        _uiState.update {
            it.copy(
                isAutoRunning = true,
                selectedStyle = style,
                autoLogs = listOf(">>> 启动自动化 | 风格: ${style.label} | 模型: ${_uiState.value.currentModel.name}")
            )
        }
        automationJob = viewModelScope.launch { startAutomationLoop(style) }
    }

    private fun stopAutomation() {
        automationJob?.cancel()
        automationJob = null
        _uiState.update { it.copy(isAutoRunning = false, loadingState = AiCommentLoadingState.Idle) }
        appendLog(">>> 自动化任务已停止")
    }

    /**
     * 自动化核心循环.
     * 流程：获取推荐 -> 提取单个视频 -> 检查字幕 -> 生成评论 -> 模拟阅读延迟 -> 发送评论 -> 冷却 -> 循环.
     */
    private suspend fun startAutomationLoop(style: CommentStyle) {
        val localQueue = ArrayDeque<CandidateVideo>()

        while (currentCoroutineContext().isActive) {
            try {
                // 1. 队列为空时，请求新的推荐流
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

                val currentVideo = localQueue.removeFirst()
                applyCandidate(currentVideo)
                appendLog("处理: ${currentVideo.info.title}")

                // 2. 模拟点击延迟
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingSubtitle) }
                delay(Random.nextLong(1000, 3000))

                // 3. 获取字幕 (Plan A/B)
                val subResult = getSubtitleUseCase(currentVideo.info.bvid, currentVideo.cid, currentVideo.info.owner?.mid)

                if (subResult is Resource.Error || subResult.data?.modelResult == null) {
                    appendLog("❌ 无字幕，跳过")
                    delay(2000)
                    continue
                }

                val validSubtitleData = subResult.data
                _uiState.update { it.copy(subtitleData = validSubtitleData, isSubtitleReady = true, loadingState = AiCommentLoadingState.Idle) }

                // 4. 生成评论 (含 Token 估算日志)
                val summaryText = validSubtitleData.modelResult?.summary ?: ""
                val subtitleText = validSubtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle?.joinToString { it.content } ?: ""
                val estimatedTokens = RateLimitHelper.estimateTokens(summaryText + subtitleText)
                appendLog("内容长度: $estimatedTokens tokens")

                _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment) }
                val genResult = generateCommentUseCase(validSubtitleData, style, _uiState.value.currentModel)

                if (genResult is Resource.Error) {
                    appendLog("⚠️ 生成失败: ${genResult.message}")
                    delay(2000)
                    continue
                }

                val commentText = genResult.data!!
                _uiState.update { it.copy(generatedContent = commentText) }

                // 5. 模拟阅读/观看时间 (防止秒回被封)
                val readingTime = Random.nextLong(5000, 10000)
                appendLog("阅读中 (${readingTime/1000}s)...")
                delay(readingTime)

                // 6. 发送评论
                _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) }
                val postResult = postCommentUseCase(currentVideo.info.id, commentText)

                if (postResult is Resource.Success) {
                    appendLog("✅ 发送成功")
                    // 标记为已处理，并上报进度以优化推荐算法
                    recommendRepository.markVideoAsProcessed(currentVideo.info)
                    recommendRepository.reportProgress(currentVideo.info.id, currentVideo.cid)
                } else {
                    appendLog("❌ 发送失败: ${postResult.message}")
                }

                // 7. 冷却时间 (长间隔)
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

    private fun appendLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.update { it.copy(autoLogs = (it.autoLogs + "[$timestamp] $msg").takeLast(50)) }
    }

    // endregion

    // region Manual Operations (手动操作)

    fun generateComment(style: CommentStyle) {
        val state = _uiState.value
        if (state.subtitleData == null || (state.loadingState != AiCommentLoadingState.Idle && !state.isAutoRunning)) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment, selectedStyle = style, error = null) }
            val result = generateCommentUseCase(state.subtitleData, style, state.currentModel)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, generatedContent = result.data ?: "") }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                else -> {}
            }
        }
    }

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
        _uiState.update {
            it.copy(
                videoTitle = candidate.info.title,
                videoCover = candidate.info.pic,
                bvid = candidate.info.bvid,
                oid = candidate.info.id,
                cid = candidate.cid,
                upMid = candidate.info.owner?.mid ?: 0L,
                subtitleData = candidate.subtitleData,
                isSubtitleReady = candidate.subtitleData != null,
                error = null,
                generatedContent = "",
                selectedStyle = if (it.isAutoRunning) it.selectedStyle else null
            )
        }
        if (candidate.subtitleData == null && !_uiState.value.isAutoRunning) fetchSubtitle()
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
                is Resource.Success -> {
                    launch {
                        val currentItem = state.recommendationList.find { it.info.bvid == state.bvid }?.info
                        if (currentItem != null) recommendRepository.markVideoAsProcessed(currentItem)
                        recommendRepository.reportProgress(state.oid, state.cid)
                    }
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle,
                            successMessage = "发送成功",
                            generatedContent = "",
                            recommendationList = it.recommendationList.filter { item -> item.info.bvid != it.bvid }
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) }
                else -> {}
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(error = null, successMessage = null) } }

    // endregion
}
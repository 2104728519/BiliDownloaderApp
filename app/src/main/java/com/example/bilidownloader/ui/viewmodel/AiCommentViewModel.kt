package com.example.bilidownloader.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.GetSubtitleUseCase
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase
import com.example.bilidownloader.domain.usecase.PostCommentUseCase
import com.example.bilidownloader.domain.model.CommentStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI 状态定义
data class AiCommentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // 步骤 1: 视频信息
    val videoTitle: String = "",
    val videoCover: String = "",
    val oid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val upMid: Long = 0L,

    // 步骤 2: 字幕数据
    val subtitleData: ConclusionData? = null,
    val isSubtitleReady: Boolean = false,

    // 步骤 3: 评论生成
    val generatedContent: String = "", // 编辑框的内容
    val selectedStyle: CommentStyle? = null
)

class AiCommentViewModel(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getSubtitleUseCase: GetSubtitleUseCase,
    private val generateCommentUseCase: GenerateCommentUseCase,
    private val postCommentUseCase: PostCommentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCommentUiState())
    val uiState = _uiState.asStateFlow()

    // 1. 解析视频链接
    fun analyzeVideo(url: String) {
        if (url.isBlank()) return

        viewModelScope.launch {
            analyzeVideoUseCase(url).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Resource.Success -> {
                        val detail = resource.data!!.detail
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                videoTitle = detail.title,
                                videoCover = detail.pic,
                                bvid = detail.bvid,
                                oid = detail.aid, // oid 就是 aid
                                cid = detail.pages.first().cid,
                                upMid = detail.owner.mid
                            )
                        }
                        // 解析成功后，自动开始获取字幕
                        fetchSubtitle()
                    }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = resource.message) }
                }
            }
        }
    }

    // 2. 获取字幕 (内部调用)
    private fun fetchSubtitle() {
        val state = _uiState.value
        if (state.bvid.isEmpty() || state.cid == 0L) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = getSubtitleUseCase(state.bvid, state.cid, state.upMid)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            subtitleData = result.data,
                            isSubtitleReady = true,
                            error = null
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "无法获取字幕: ${result.message} (无法生成评论)",
                            isSubtitleReady = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    // 3. 生成 AI 评论
    fun generateComment(style: CommentStyle) {
        val state = _uiState.value
        if (state.subtitleData == null) {
            _uiState.update { it.copy(error = "字幕数据未就绪") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedStyle = style, error = null) }

            val result = generateCommentUseCase(state.subtitleData, style)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generatedContent = result.data ?: ""
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    // 4. 更新编辑框内容 (用户手动修改)
    fun updateContent(text: String) {
        _uiState.update { it.copy(generatedContent = text) }
    }

    // 5. 发送评论
    fun sendComment() {
        val state = _uiState.value
        if (state.oid == 0L || state.generatedContent.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = postCommentUseCase(state.oid, state.generatedContent)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "评论发送成功！",
                            generatedContent = "" // 发送成功后清空
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    // 清除一次性消息
    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
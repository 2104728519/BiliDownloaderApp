package com.example.bilidownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.GetSubtitleUseCase
import com.example.bilidownloader.domain.model.CommentStyle
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase
import com.example.bilidownloader.domain.usecase.PostCommentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [修改 1] 使用新的 LoadingState 替换 isLoading
data class AiCommentUiState(
    val loadingState: AiCommentLoadingState = AiCommentLoadingState.Idle, // 替换 isLoading
    val error: String? = null,
    val successMessage: String? = null,
    val videoTitle: String = "",
    val videoCover: String = "",
    val oid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val upMid: Long = 0L,
    val subtitleData: ConclusionData? = null,
    val isSubtitleReady: Boolean = false,
    val generatedContent: String = "",
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

    fun analyzeVideo(url: String) {
        if (url.isBlank() || _uiState.value.loadingState != AiCommentLoadingState.Idle) return

        viewModelScope.launch {
            analyzeVideoUseCase(url).collect { resource ->
                when (resource) {
                    // [修改 2] 设置具体状态
                    is Resource.Loading -> _uiState.update { it.copy(loadingState = AiCommentLoadingState.AnalyzingVideo, error = null) }
                    is Resource.Success -> {
                        val detail = resource.data!!.detail
                        _uiState.update {
                            it.copy(
                                // 加载完成后，状态会由 fetchSubtitle接管
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
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.FetchingSubtitle) } // 设置具体状态

            val result = getSubtitleUseCase(state.bvid, state.cid, state.upMid)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle, // 恢复 Idle
                            subtitleData = result.data,
                            isSubtitleReady = true,
                            error = null
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle, // 恢复 Idle
                            error = "无法获取字幕: ${result.message} (无法生成评论)",
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
        if (state.subtitleData == null || state.loadingState != AiCommentLoadingState.Idle) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.GeneratingComment, selectedStyle = style, error = null) } // 设置具体状态

            val result = generateCommentUseCase(state.subtitleData, style)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle, // 恢复 Idle
                            generatedContent = result.data ?: ""
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) } // 恢复 Idle
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
        if (state.oid == 0L || state.generatedContent.isBlank() || state.loadingState != AiCommentLoadingState.Idle) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingState = AiCommentLoadingState.SendingComment) } // 设置具体状态

            val result = postCommentUseCase(state.oid, state.generatedContent)

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            loadingState = AiCommentLoadingState.Idle, // 恢复 Idle
                            successMessage = "评论发送成功！",
                            generatedContent = ""
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(loadingState = AiCommentLoadingState.Idle, error = result.message) } // 恢复 Idle
                }
                else -> {}
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
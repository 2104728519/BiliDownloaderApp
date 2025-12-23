package com.example.bilidownloader.ui.state

import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.VideoDetail

data class FormatOption(
    val id: Int,
    val label: String,
    val description: String,
    val codecs: String?,
    val bandwidth: Long,
    val estimatedSize: Long
)

/**
 * 主页 UI 状态机.
 */
sealed class MainState {
    object Idle : MainState()
    object Analyzing : MainState()

    /**
     * 选择模式.
     * 包含所有需要用户决策的数据：视频详情、可选画质、AI 字幕预览等.
     */
    data class ChoiceSelect(
        val detail: VideoDetail,
        val videoFormats: List<FormatOption>,
        val audioFormats: List<FormatOption>,
        val selectedVideo: FormatOption?,
        val selectedAudio: FormatOption?,

        // 字幕/摘要相关
        val isSubtitleLoading: Boolean = false,
        val subtitleData: ConclusionData? = null,
        val selectedSubtitleIndex: Int = 0,
        val isTimestampEnabled: Boolean = false,
        val subtitleContent: String = ""
    ) : MainState()

    data class Processing(val info: String, val progress: Float) : MainState()
    data class Success(val message: String) : MainState()
    data class Error(val errorMsg: String) : MainState()
}